package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprintMode;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
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
 * Подсветка Импринтера (только пока он в руке):
 *  - рамка выделения между углами (или один угол, пока второй не выбран);
 *  - в режиме «Вставка» — мигающий прозрачный призрак структуры там, куда она встанет
 *    (красным — где место занято и блок не поставится);
 *  - в режиме «Применить настройки» — рамка области, куда лягут настройки.
 */
@EventBusSubscriber(modid = Ferronexus.MOD_ID, value = Dist.CLIENT)
public final class ImprinterPreview {
    private ImprinterPreview() {}

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int NO_OVERLAY = 10 << 16;
    private static final float E = 0.03f; // толщина рёбер рамки

    private record Ghost(int x, int y, int z, BlockState state) {}

    // Кэш разобранной схемы: пересобираем только когда данные предмета изменились.
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

        Vec3 cam = mc.gameRenderer.mainCamera().position();
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector out = event.getSubmitNodeCollector();
        RenderType type = RenderTypes.entityTranslucent(WHITE);

        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2 / 1.2)); // 0..1, период 1.2 с

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        // 1. Выделение (видно во всех режимах, ярче в режимах выделения/копирования).
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
            out.submitCustomGeometry(pose, type, (p, vc) -> {
                frame(p, vc, x0, y0, z0, x1, y1, z1, edge);
                cube(p, vc, x0 + 0.005f, y0 + 0.005f, z0 + 0.005f, x1 - 0.005f, y1 - 0.005f, z1 - 0.005f, fill, null, 0);
                frame(p, vc, fa.getX() - 0.01f, fa.getY() - 0.01f, fa.getZ() - 0.01f, fa.getX() + 1.01f, fa.getY() + 1.01f, fa.getZ() + 1.01f, cornerA);
                if (showB) frame(p, vc, fb.getX() - 0.01f, fb.getY() - 0.01f, fb.getZ() - 0.01f, fb.getX() + 1.01f, fb.getY() + 1.01f, fb.getZ() + 1.01f, cornerB);
            });
        }

        // 2. Предпросмотр вставки / применения настроек.
        if ((mode == ImprintMode.PASTE || mode == ImprintMode.APPLY_SETTINGS)
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            refreshCache(cd, tag, level);
            if (!cachedGhosts.isEmpty()) {
                final BlockPos origin = mode == ImprintMode.PASTE ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
                final int sx = Math.max(1, tag.getIntOr("SX", 1)), sy = Math.max(1, tag.getIntOr("SY", 1)), sz = Math.max(1, tag.getIntOr("SZ", 1));
                final boolean paste = mode == ImprintMode.PASTE;
                final int boxEdge = argb((int) (140 + 100 * pulse), 255, 255, 255);
                final List<Ghost> ghosts = cachedGhosts;
                final Set<Long> cells = cachedCells;
                out.submitCustomGeometry(pose, type, (p, vc) -> {
                    float ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
                    frame(p, vc, ox - 0.02f, oy - 0.02f, oz - 0.02f, ox + sx + 0.02f, oy + sy + 0.02f, oz + sz + 0.02f, boxEdge);
                    for (Ghost g : ghosts) {
                        BlockPos wp = origin.offset(g.x(), g.y(), g.z());
                        int rgb;
                        int alpha;
                        if (paste) {
                            boolean blocked = !level.getBlockState(wp).canBeReplaced();
                            rgb = blocked ? 0xFF3030 : (g.state().getMapColor(level, wp).col & 0xFFFFFF);
                            if (rgb == 0) rgb = 0xA0A0A0;
                            alpha = (int) (60 + 110 * pulse);
                        } else {
                            boolean match = level.getBlockState(wp).getBlock() == g.state().getBlock();
                            rgb = match ? 0x50FF70 : 0x808080;
                            alpha = (int) ((match ? 50 : 20) + (match ? 90 : 30) * pulse);
                        }
                        int c = (alpha << 24) | rgb;
                        float x = ox + g.x(), y = oy + g.y(), z = oz + g.z();
                        cube(p, vc, x + 0.01f, y + 0.01f, z + 0.01f, x + 0.99f, y + 0.99f, z + 0.99f, c, cells, BlockPos.asLong(g.x(), g.y(), g.z()));
                    }
                });
            }
        }

        pose.popPose();
    }

    private static void refreshCache(CustomData cd, CompoundTag tag, ClientLevel level) {
        if (cd == cachedData) return;
        cachedData = cd;
        ListTag list = tag.getListOrEmpty("Blocks");
        var blocks = level.holderLookup(Registries.BLOCK);
        List<Ghost> g = new ArrayList<>(list.size());
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            BlockState st = NbtUtils.readBlockState(blocks, e.getCompoundOrEmpty("S"));
            if (st.isAir()) continue;
            int x = e.getIntOr("X", 0), y = e.getIntOr("Y", 0), z = e.getIntOr("Z", 0);
            g.add(new Ghost(x, y, z, st));
            cells.add(BlockPos.asLong(x, y, z));
        }
        cachedGhosts = g;
        cachedCells = cells;
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Рамка из 12 тонких брусков. */
    private static void frame(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        float[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (float y : ys) for (float z : zs) cube(p, vc, x0 - E, y - E, z - E, x1 + E, y + E, z + E, c, null, 0);
        for (float x : xs) for (float z : zs) cube(p, vc, x - E, y0 - E, z - E, x + E, y1 + E, z + E, c, null, 0);
        for (float x : xs) for (float y : ys) cube(p, vc, x - E, y - E, z0 - E, x + E, y + E, z1 + E, c, null, 0);
    }

    private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /** Куб; если задан набор занятых ячеек, грани между соседними блоками схемы не рисуются. */
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
            float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, v[i][0], v[i][1], v[i][2])
                        .setColor(c)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(p, d[0], d[1], d[2]);
            }
        }
    }
}
