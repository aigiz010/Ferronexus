package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlockEntity;
import com.aigiz010.ferronexus.conduit.ConduitShapes;
import com.aigiz010.ferronexus.conduit.ConduitType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Рисует тонкие провода и трубы. У каждого провода своя точка узла, поэтому они обходят друг друга.
 * Если два провода всё же пересекаются, общий кубик делится по диагонали: каждый виден прямоугольным треугольником.
 */
public class ConduitBundleRenderer implements BlockEntityRenderer<ConduitBundleBlockEntity, ConduitBundleRenderer.State> {
    private static final int NO_OVERLAY = 10 << 16;
    private static final Identifier PLUG = tex("plug");
    private static final Identifier[] TEX = new Identifier[ConduitType.values().length];
    private static final float[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    static {
        for (ConduitType t : ConduitType.values()) TEX[t.ordinal()] = tex(t.id());
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "textures/block/conduit/" + name + ".png");
    }

    public static class State extends BlockEntityRenderState {
        int mask;
        final int[] conn = new int[ConduitType.values().length];
        final int[] ends = new int[ConduitType.values().length];
    }

    private record Piece(ConduitType t, double[] b, Direction.Axis ax) {}

    private static final class Cube {
        final double[] c;
        final List<Piece> parts = new ArrayList<>();

        Cube(double[] c) { this.c = c; }
    }

    public ConduitBundleRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ConduitBundleBlockEntity be, State s, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(be, s, crumbling);
        s.mask = be.typesMask();
        for (ConduitType t : ConduitType.values()) {
            int c = 0, e = 0;
            for (Direction d : Direction.values()) {
                int b = 1 << d.ordinal();
                if (be.isConnected(t, d)) c |= b;
                if (be.isPlug(t, d) || be.isStub(t, d)) e |= b;
            }
            s.conn[t.ordinal()] = c;
            s.ends[t.ordinal()] = e;
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.mask == 0) return;
        final int light = s.lightCoords;

        // 1. Части всех проводов.
        final List<Piece> ps = new ArrayList<>();
        for (ConduitType t : ConduitType.values()) {
            if ((s.mask & t.bit()) == 0) continue;
            int bits = s.conn[t.ordinal()] | s.ends[t.ordinal()];
            Direction.Axis ax = ConduitShapes.axisOf(bits);
            if (ax != null) {
                ps.add(new Piece(t, ConduitShapes.through(t, ax), ax));
                continue;
            }
            ps.add(new Piece(t, ConduitShapes.joint(t), null));
            for (Direction d : Direction.values())
                if ((bits & (1 << d.ordinal())) != 0) ps.add(new Piece(t, ConduitShapes.armTo(t, d), d.getAxis()));
        }

        // 2. Неизбежные пересечения (только поперечные отрезки разных проводов).
        final List<Cube> cubes = new ArrayList<>();
        for (int i = 0; i < ps.size(); i++) {
            Piece a = ps.get(i);
            if (a.ax() == null) continue;
            for (int j = i + 1; j < ps.size(); j++) {
                Piece b = ps.get(j);
                if (b.ax() == null || b.t() == a.t() || b.ax() == a.ax()) continue;
                double[] c = ConduitShapes.intersect(a.b(), b.b());
                if (c == null) continue;
                Cube k = find(cubes, c);
                if (!k.parts.contains(a)) k.parts.add(a);
                if (!k.parts.contains(b)) k.parts.add(b);
            }
        }

        // 3. Отрисовка.
        for (ConduitType t : ConduitType.values()) {
            if ((s.mask & t.bit()) == 0) continue;
            out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEX[t.ordinal()]), (p, vc) -> {
                for (Piece pc : ps) {
                    if (pc.t() != t) continue;
                    if (pc.ax() == null) box(p, vc, pc.b(), light, false, null);
                    else drawSplit(p, vc, pc, cubes, light);
                }
            });
            final int e = s.ends[t.ordinal()];
            if (e != 0) {
                out.submitCustomGeometry(pose, RenderTypes.entityCutout(PLUG), (p, vc) -> {
                    for (Direction d : Direction.values())
                        if ((e & (1 << d.ordinal())) != 0) box(p, vc, ConduitShapes.plug(t, d), light, false, null);
                });
            }
        }
    }

    private static Cube find(List<Cube> cubes, double[] c) {
        for (Cube k : cubes) {
            if (Math.abs(k.c[0] - c[0]) < 0.01 && Math.abs(k.c[1] - c[1]) < 0.01 && Math.abs(k.c[2] - c[2]) < 0.01) return k;
        }
        Cube k = new Cube(c);
        cubes.add(k);
        return k;
    }

    /** Отрезок провода, разрезанный в местах пересечений. */
    private static void drawSplit(PoseStack.Pose p, VertexConsumer vc, Piece pc, List<Cube> cubes, int light) {
        final int a = pc.ax().ordinal();
        List<Cube> on = new ArrayList<>();
        for (Cube k : cubes) if (k.parts.contains(pc)) on.add(k);
        if (on.isEmpty()) {
            box(p, vc, pc.b(), light, true, pc.ax());
            return;
        }
        on.sort(Comparator.comparingDouble(k -> k.c[a]));
        double pos = pc.b()[a];
        for (Cube k : on) {
            if (k.c[a] > pos + 0.001) box(p, vc, seg(pc.b(), a, pos, k.c[a]), light, true, pc.ax());
            int idx = k.parts.indexOf(pc);
            if (idx == 0 || idx == 1) {
                Piece other = k.parts.get(idx == 0 ? 1 : 0);
                triangle(p, vc, k.c, light, pc.ax(), other.ax());
            }
            pos = Math.max(pos, k.c[a + 3]);
        }
        if (pos < pc.b()[a + 3] - 0.001) box(p, vc, seg(pc.b(), a, pos, pc.b()[a + 3]), light, true, pc.ax());
    }

    private static double[] seg(double[] b, int a, double from, double to) {
        double[] r = b.clone();
        r[a] = from;
        r[a + 3] = to;
        return r;
    }

    /**
     * Половина общего кубика: прямоугольный треугольник (призма). Убирается угол «своя ось — начало,
     * чужая ось — конец», поэтому половины двух проводов дополняют друг друга.
     */
    private static void triangle(PoseStack.Pose p, VertexConsumer vc, double[] c, int light,
                                 Direction.Axis mine, Direction.Axis other) {
        int al = mine.ordinal(), be = other.ordinal(), ga = 3 - al - be;
        float aLo = (float) c[al] / 16f, bHi = (float) c[be + 3] / 16f;
        int[] faces = ga == 0 ? new int[] {4, 5} : ga == 1 ? new int[] {0, 1} : new int[] {2, 3};
        for (int f : faces) {
            float[][] v = verts(c, f);
            for (int i = 0; i < 4; i++) {
                if (Math.abs(v[i][al] - aLo) < 1e-4 && Math.abs(v[i][be] - bHi) < 1e-4) v[i] = v[(i + 3) % 4].clone();
            }
            float[] n = NORMALS[f];
            face(p, vc, light, true, mine, n[0], n[1], n[2], v, c);
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

    /** Коробка в пикселях 0..16. stretch: u идёт вдоль оси провода, v — поперёк. */
    private static void box(PoseStack.Pose p, VertexConsumer vc, double[] b, int light, boolean stretch, Direction.Axis axis) {
        for (int f = 0; f < 6; f++) {
            float[] n = NORMALS[f];
            face(p, vc, light, stretch, axis, n[0], n[1], n[2], verts(b, f), b);
        }
    }

    private static void face(PoseStack.Pose p, VertexConsumer vc, int light, boolean stretch, Direction.Axis axis,
                             float nx, float ny, float nz, float[][] v, double[] b) {
        int na = nx != 0 ? 0 : ny != 0 ? 1 : 2;
        int ua, va;
        if (stretch && axis != null && axis.ordinal() != na) {
            ua = axis.ordinal();
            va = 3 - na - ua;
        } else {
            ua = na == 0 ? 2 : 0;
            va = na == 1 ? 2 : 1;
        }
        float uMin = (float) b[ua] / 16f, uLen = (float) (b[ua + 3] - b[ua]) / 16f;
        float vMin = (float) b[va] / 16f, vLen = (float) (b[va + 3] - b[va]) / 16f;
        boolean endCap = stretch && axis != null && axis.ordinal() == na;
        for (float[] q : v) {
            float u, w;
            if (stretch && !endCap) {
                u = q[ua];                                 // по длине, от края блока: текстура не рвётся
                w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen; // поперёк: вся высота текстуры
            } else {
                // Узел и торцы: узкая полоса текстуры провода, чтобы цвет совпадал с трубкой.
                u = 0.02f + (uLen <= 0 ? 0 : (q[ua] - uMin) / uLen) * 0.06f;
                w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen;
            }
            vc.addVertex(p, q[0], q[1], q[2])
                    .setColor(-1)
                    .setUv(u, w)
                    .setOverlay(NO_OVERLAY)
                    .setLight(light)
                    .setNormal(p, nx, ny, nz);
        }
    }
}
