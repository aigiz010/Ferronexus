package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlock;
import com.aigiz010.ferronexus.conduit.ConduitShapes;
import com.aigiz010.ferronexus.conduit.ConduitType;
import com.aigiz010.ferronexus.imprinter.ImprintMode;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Подсветка Импринтера (только пока он в руке).
 *  - рамка выделения между углами;
 *  - «Вставка» — мигающий полупрозрачный призрак с настоящими текстурами блоков, проводов и труб
 *    (с учётом поворота на R); красным оттенком — где место занято;
 *  - «Применить настройки» — зелёным совпадающие блоки.
 */
@EventBusSubscriber(modid = Ferronexus.MOD_ID, value = Dist.CLIENT)
public final class ImprinterPreview {
    private ImprinterPreview() {}

    private static final float E = 0.03f; // толщина рёбер рамки
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int NO_OVERLAY = 10 << 16;
    private static final int MAX_TEXTURED = 4096;
    private static final int NT = ConduitType.values().length;
    private static final Identifier PLUG = tex("plug");
    private static final Identifier[] TEX = new Identifier[NT];
    private static final float[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    static {
        for (ConduitType t : ConduitType.values()) TEX[t.ordinal()] = tex(t.id());
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "textures/block/conduit/" + name + ".png");
    }

    /** Провода в блоке схемы: маска типов, стороны каждого типа и разъёмы (уже повёрнутые). */
    private record Conduits(int mask, int[] bits, int[] ends) {}

    /** quads — грани модели; conduits != null — блок проводов (рисуется своей геометрией). */
    private record Ghost(int x, int y, int z, BlockState state, List<BakedQuad> quads, Conduits conduits) {}

    private static CustomData cachedData;
    private static List<Ghost> cachedGhosts = List.of();
    private static Set<Long> cachedCells = Set.of();

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ImprinterItem)) stack = player.getOffhandItem();
        if (!(stack.getItem() instanceof ImprinterItem)) return;

        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
        ImprintMode mode = ImprinterItem.mode(stack);
        int rot = ImprinterItem.rot(stack);

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector out = event.getSubmitNodeCollector();
        RenderType flat = RenderTypes.debugQuads();
        RenderType textured = RenderTypes.translucentMovingBlock();

        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2 / 1.2));

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        boolean hasA = tag.getBooleanOr("HasA", false), hasB = tag.getBooleanOr("HasB", false);
        if (hasA) {
            BlockPos a = BlockPos.of(tag.getLongOr("A", 0L));
            BlockPos b = hasB ? BlockPos.of(tag.getLongOr("B", 0L)) : a;
            boolean active = mode == ImprintMode.SELECT || mode == ImprintMode.COPY || mode == ImprintMode.COPY_CONTENTS;
            final float x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
            final float x1 = Math.max(a.getX(), b.getX()) + 1, y1 = Math.max(a.getY(), b.getY()) + 1, z1 = Math.max(a.getZ(), b.getZ()) + 1;
            final int edge = argb(active ? 230 : 120, 60, 200, 255);
            final int fill = argb(active ? 40 : 18, 60, 200, 255);
            final int cornerA = argb(200, 80, 255, 120), cornerB = argb(200, 255, 170, 60);
            final BlockPos fa = a, fb = b;
            final boolean showB = hasB;
            out.submitCustomGeometry(pose, flat, (p, vc) -> {
                frame(p, vc, x0, y0, z0, x1, y1, z1, edge);
                cube(p, vc, x0 + 0.005f, y0 + 0.005f, z0 + 0.005f, x1 - 0.005f, y1 - 0.005f, z1 - 0.005f, fill, null, 0);
                frame(p, vc, fa.getX() - 0.01f, fa.getY() - 0.01f, fa.getZ() - 0.01f, fa.getX() + 1.01f, fa.getY() + 1.01f, fa.getZ() + 1.01f, cornerA);
                if (showB) frame(p, vc, fb.getX() - 0.01f, fb.getY() - 0.01f, fb.getZ() - 0.01f, fb.getX() + 1.01f, fb.getY() + 1.01f, fb.getZ() + 1.01f, cornerB);
            });
        }

        if ((mode == ImprintMode.PASTE || mode == ImprintMode.APPLY_SETTINGS)
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            refreshCache(mc, cd, tag, level, rot);
            if (!cachedGhosts.isEmpty()) {
                final BlockPos origin = mode == ImprintMode.PASTE ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
                int rsx = Math.max(1, tag.getIntOr("SX", 1)), rsz = Math.max(1, tag.getIntOr("SZ", 1));
                final int sy = Math.max(1, tag.getIntOr("SY", 1));
                final int sx = (rot & 1) == 1 ? rsz : rsx, sz = (rot & 1) == 1 ? rsx : rsz;
                final boolean paste = mode == ImprintMode.PASTE;
                final int boxEdge = argb((int) (140 + 100 * pulse), 255, 255, 255);
                final List<Ghost> ghosts = cachedGhosts;
                final Set<Long> cells = cachedCells;
                final float ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
                final int alpha = (int) (90 + 120 * pulse); // ~35%..80%

                // рамка + блоки без модели (и всё в режиме «Применить настройки») — сплошным цветом
                out.submitCustomGeometry(pose, flat, (p, vc) -> {
                    frame(p, vc, ox - 0.02f, oy - 0.02f, oz - 0.02f, ox + sx + 0.02f, oy + sy + 0.02f, oz + sz + 0.02f, boxEdge);
                    int n = 0;
                    for (Ghost g : ghosts) {
                        if (paste && g.conduits() != null) continue;
                        boolean tex = paste && !g.quads().isEmpty() && n++ < MAX_TEXTURED;
                        if (tex) continue;
                        BlockPos wp = origin.offset(g.x(), g.y(), g.z());
                        int rgb, al;
                        if (paste) {
                            boolean blocked = !level.getBlockState(wp).canBeReplaced();
                            rgb = blocked ? 0xFF3030 : (g.state().getMapColor(level, wp).col & 0xFFFFFF);
                            if (rgb == 0) rgb = 0xA0A0A0;
                            al = (int) (60 + 110 * pulse);
                        } else {
                            boolean match = level.getBlockState(wp).getBlock() == g.state().getBlock();
                            rgb = match ? 0x50FF70 : 0x808080;
                            al = (int) ((match ? 50 : 20) + (match ? 90 : 30) * pulse);
                        }
                        float x = ox + g.x(), y = oy + g.y(), z = oz + g.z();
                        cube(p, vc, x + 0.01f, y + 0.01f, z + 0.01f, x + 0.99f, y + 0.99f, z + 0.99f, (al << 24) | rgb, cells, BlockPos.asLong(g.x(), g.y(), g.z()));
                    }
                });

                if (paste) {
                    // настоящие текстуры блоков, полупрозрачно и мигая
                    int n = 0;
                    for (Ghost g : ghosts) {
                        if (g.conduits() != null || g.quads().isEmpty()) continue;
                        if (n++ >= MAX_TEXTURED) break;
                        BlockPos wp = origin.offset(g.x(), g.y(), g.z());
                        boolean blocked = !level.getBlockState(wp).canBeReplaced();
                        int mapRgb = g.state().getMapColor(level, wp).col & 0xFFFFFF;
                        if (mapRgb == 0) mapRgb = 0xFFFFFF;
                        final int plain = (alpha << 24) | (blocked ? 0xFF6060 : 0xFFFFFF);
                        final int tinted = (alpha << 24) | (blocked ? 0xFF6060 : mapRgb);
                        final List<BakedQuad> quads = g.quads();
                        pose.pushPose();
                        pose.translate(ox + g.x() + 0.5f, oy + g.y() + 0.5f, oz + g.z() + 0.5f);
                        pose.scale(0.996f, 0.996f, 0.996f);
                        pose.translate(-0.5f, -0.5f, -0.5f);
                        out.submitCustomGeometry(pose, textured, (p, vc) -> {
                            QuadInstance inst = new QuadInstance();
                            inst.setLightCoords(FULL_BRIGHT);
                            inst.setOverlayCoords(NO_OVERLAY);
                            for (BakedQuad q : quads) {
                                inst.setColor(q.materialInfo().isTinted() ? tinted : plain);
                                vc.putBakedQuad(p, q, inst);
                            }
                        });
                        pose.popPose();
                    }

                    // провода и трубы: по одной отправке на каждый тип (своя текстура)
                    int anyMask = 0, anyEnds = 0;
                    for (Ghost g : ghosts) {
                        if (g.conduits() == null) continue;
                        anyMask |= g.conduits().mask();
                        for (int i = 0; i < NT; i++) if (g.conduits().ends()[i] != 0) anyEnds = 1;
                    }
                    for (ConduitType ct : ConduitType.values()) {
                        if ((anyMask & ct.bit()) == 0) continue;
                        final int ti = ct.ordinal();
                        out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEX[ti]), (p, vc) -> {
                            for (Ghost g : ghosts) {
                                Conduits c = g.conduits();
                                if (c == null || (c.mask() & ct.bit()) == 0) continue;
                                int col = ghostColor(level, origin, g, alpha);
                                float gx = ox + g.x(), gy = oy + g.y(), gz = oz + g.z();
                                int bits = c.bits()[ti];
                                Direction.Axis ax = ConduitShapes.axisOf(bits);
                                if (ax != null) {
                                    cbox(p, vc, ConduitShapes.through(ct, ax), gx, gy, gz, col, ax);
                                    continue;
                                }
                                cbox(p, vc, ConduitShapes.joint(ct), gx, gy, gz, col, null);
                                for (Direction d : Direction.values())
                                    if ((bits & (1 << d.ordinal())) != 0) cbox(p, vc, ConduitShapes.armTo(ct, d), gx, gy, gz, col, d.getAxis());
                            }
                        });
                    }
                    if (anyEnds != 0) {
                        out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(PLUG), (p, vc) -> {
                            for (Ghost g : ghosts) {
                                Conduits c = g.conduits();
                                if (c == null) continue;
                                int col = ghostColor(level, origin, g, alpha);
                                float gx = ox + g.x(), gy = oy + g.y(), gz = oz + g.z();
                                for (ConduitType ct : ConduitType.values()) {
                                    int e = c.ends()[ct.ordinal()];
                                    if ((c.mask() & ct.bit()) == 0 || e == 0) continue;
                                    for (Direction d : Direction.values())
                                        if ((e & (1 << d.ordinal())) != 0) cbox(p, vc, ConduitShapes.plug(ct, d), gx, gy, gz, col, null);
                                }
                            }
                        });
                    }
                }
            }
        }

        pose.popPose();
    }

    private static int ghostColor(ClientLevel level, BlockPos origin, Ghost g, int alpha) {
        boolean blocked = !level.getBlockState(origin.offset(g.x(), g.y(), g.z())).canBeReplaced();
        return (alpha << 24) | (blocked ? 0xFF6060 : 0xFFFFFF);
    }

    private static void refreshCache(Minecraft mc, CustomData cd, CompoundTag tag, ClientLevel level, int rot) {
        if (cd == cachedData) return;
        cachedData = cd;
        ListTag list = tag.getListOrEmpty("Blocks");
        int sx = Math.max(1, tag.getIntOr("SX", 1)), sz = Math.max(1, tag.getIntOr("SZ", 1));
        var blocks = level.holderLookup(Registries.BLOCK);
        var rotation = ImprinterItem.rotation(rot);
        List<int[]> pos = new ArrayList<>(list.size());
        List<BlockState> states = new ArrayList<>(list.size());
        List<CompoundTag> tags = new ArrayList<>(list.size());
        Set<Long> cells = new HashSet<>();
        Set<Long> solid = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            BlockState st = NbtUtils.readBlockState(blocks, e.getCompoundOrEmpty("S"));
            if (st.isAir()) continue;
            st = st.rotate(rotation);
            int[] xz = ImprinterItem.rotateXZ(e.getIntOr("X", 0), e.getIntOr("Z", 0), sx, sz, rot);
            int y = e.getIntOr("Y", 0);
            pos.add(new int[] {xz[0], y, xz[1]});
            states.add(st);
            tags.add(e.contains("T") ? e.getCompoundOrEmpty("T") : null);
            long key = BlockPos.asLong(xz[0], y, xz[1]);
            cells.add(key);
            if (st.canOcclude()) solid.add(key);
        }
        List<Ghost> g = new ArrayList<>(pos.size());
        RandomSource rand = RandomSource.create(42L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        for (int i = 0; i < pos.size(); i++) {
            int[] p = pos.get(i);
            BlockState st = states.get(i);
            if (st.getBlock() instanceof ConduitBundleBlock) {
                CompoundTag t = tags.get(i) == null ? new CompoundTag() : tags.get(i).copy();
                ImprinterItem.rotateConduitTag(t, rotation);
                int mask = t.getIntOr("Types", 0);
                int[] bits = new int[NT], ends = new int[NT];
                for (int k = 0; k < NT; k++) {
                    int c = t.getIntOr("C" + k, 0), pl = t.getIntOr("P" + k, 0), s = t.getIntOr("S" + k, 0);
                    bits[k] = c | pl | s;
                    ends[k] = pl | s;
                }
                g.add(new Ghost(p[0], p[1], p[2], st, List.of(), new Conduits(mask, bits, ends)));
                continue;
            }
            List<BakedQuad> quads = new ArrayList<>();
            try {
                BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(st);
                parts.clear();
                rand.setSeed(42L);
                model.collectParts(rand, parts);
                for (BlockStateModelPart part : parts) {
                    quads.addAll(part.getQuads(null));
                    for (Direction d : Direction.values()) {
                        if (solid.contains(BlockPos.asLong(p[0] + d.getStepX(), p[1] + d.getStepY(), p[2] + d.getStepZ()))) continue;
                        quads.addAll(part.getQuads(d));
                    }
                }
            } catch (Throwable ignored) {
                quads.clear();
            }
            g.add(new Ghost(p[0], p[1], p[2], st, quads, null));
        }
        cachedGhosts = g;
        cachedCells = cells;
    }

    // ---------- геометрия проводов (как в мире, но полупрозрачно) ----------
    /** Коробка b в пикселях 0..16 внутри блока с мировыми координатами (gx, gy, gz). */
    private static void cbox(PoseStack.Pose p, VertexConsumer vc, double[] b, float gx, float gy, float gz, int col, Direction.Axis axis) {
        for (int f = 0; f < 6; f++) {
            float[] n = NORMALS[f];
            float[][] v = verts(b, f);
            int na = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
            boolean stretch = axis != null && axis.ordinal() != na;
            int ua, va;
            if (stretch) {
                ua = axis.ordinal();
                va = 3 - na - ua;
            } else {
                ua = na == 0 ? 2 : 0;
                va = na == 1 ? 2 : 1;
            }
            float uMin = (float) b[ua] / 16f, uLen = (float) (b[ua + 3] - b[ua]) / 16f;
            float vMin = (float) b[va] / 16f, vLen = (float) (b[va + 3] - b[va]) / 16f;
            for (float[] q : v) {
                float u, w;
                if (stretch) {
                    u = q[ua];
                    w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen;
                } else {
                    u = 0.02f + (uLen <= 0 ? 0 : (q[ua] - uMin) / uLen) * 0.06f;
                    w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen;
                }
                vc.addVertex(p, gx + q[0], gy + q[1], gz + q[2])
                        .setColor(col)
                        .setUv(u, w)
                        .setOverlay(NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(p, n[0], n[1], n[2]);
            }
        }
    }

    private static float[][] verts(double[] b, int f) {
        float x0 = (float) b[0] / 16f, y0 = (float) b[1] / 16f, z0 = (float) b[2] / 16f;
        float x1 = (float) b[3] / 16f, y1 = (float) b[4] / 16f, z1 = (float) b[5] / 16f;
        return switch (f) {
            case 0 -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case 1 -> new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            case 2 -> new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            case 3 -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case 4 -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            default -> new float[][] {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
        };
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    private static void frame(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        float[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (float y : ys) for (float z : zs) cube(p, vc, x0 - E, y - E, z - E, x1 + E, y + E, z + E, c, null, 0);
        for (float x : xs) for (float z : zs) cube(p, vc, x - E, y0 - E, z - E, x + E, y1 + E, z + E, c, null, 0);
        for (float x : xs) for (float y : ys) cube(p, vc, x - E, y - E, z0 - E, x + E, y + E, z1 + E, c, null, 0);
    }

    private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static void cube(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                             int c, Set<Long> cells, long self) {
        BlockPos sp = cells == null ? null : BlockPos.of(self);
        for (int f = 0; f < 6; f++) {
            int[] d = DIRS[f];
            if (sp != null && cells.contains(BlockPos.asLong(sp.getX() + d[0], sp.getY() + d[1], sp.getZ() + d[2]))) continue;
            float[][] v = switch (f) {
                case 0 -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
                case 1 -> new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
                case 2 -> new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
                case 3 -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                case 4 -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
                default -> new float[][] {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            };
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, v[i][0], v[i][1], v[i][2]).setColor(c);
            }
        }
    }
}
