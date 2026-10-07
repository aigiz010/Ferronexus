package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlockEntity;
import com.aigiz010.ferronexus.conduit.ConduitShapes;
import com.aigiz010.ferronexus.conduit.ConduitType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Рисует тонкие провода и трубы внутри блока: корпус в центре (только на развилках),
 * отрезки к соседям и разъёмы к машинам.
 */
public class ConduitBundleRenderer implements BlockEntityRenderer<ConduitBundleBlockEntity, ConduitBundleRenderer.State> {
    private static final int NO_OVERLAY = 10 << 16;
    private static final Identifier HOUSING = tex("housing");
    private static final Identifier PLUG = tex("plug");
    private static final Identifier[] TEX = new Identifier[ConduitType.values().length];

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
        Direction.Axis axis;
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
        int[] dirs = new int[s.conn.length];
        for (int i = 0; i < dirs.length; i++) dirs[i] = s.conn[i] | s.ends[i];
        s.axis = ConduitShapes.straightAxis(s.mask, dirs);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.mask == 0) return;
        final int light = s.lightCoords;
        final double[] h = ConduitShapes.housing(s.mask);
        final Direction.Axis axis = s.axis;
        // Корпус нужен только на развилках, поворотах и концах линии.
        if (axis == null) {
            out.submitCustomGeometry(pose, RenderTypes.entityCutout(HOUSING), (p, vc) ->
                    box(p, vc, new double[] {h[0], h[0], h[0], h[1], h[1], h[1]}, light, false, null));
        }
        for (ConduitType t : ConduitType.values()) {
            if ((s.mask & t.bit()) == 0) continue;
            final int c = s.conn[t.ordinal()], e = s.ends[t.ordinal()];
            if (axis != null) {
                out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEX[t.ordinal()]), (p, vc) ->
                        box(p, vc, ConduitShapes.through(t, axis, h), light, true, axis));
            } else if (c != 0) {
                out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEX[t.ordinal()]), (p, vc) -> {
                    for (Direction d : Direction.values())
                        if ((c & (1 << d.ordinal())) != 0) box(p, vc, ConduitShapes.arm(t, d, h), light, true, d.getAxis());
                });
            }
            if (e != 0) {
                out.submitCustomGeometry(pose, RenderTypes.entityCutout(PLUG), (p, vc) -> {
                    for (Direction d : Direction.values())
                        if ((e & (1 << d.ordinal())) != 0) box(p, vc, ConduitShapes.plug(t, d), light, false, null);
                });
            }
        }
    }

    /** Коробка в пикселях 0..16. stretch: u идёт вдоль оси провода, v — поперёк. */
    private static void box(PoseStack.Pose p, VertexConsumer vc, double[] b, int light, boolean stretch, Direction.Axis axis) {
        float x0 = (float) b[0] / 16f, y0 = (float) b[1] / 16f, z0 = (float) b[2] / 16f;
        float x1 = (float) b[3] / 16f, y1 = (float) b[4] / 16f, z1 = (float) b[5] / 16f;
        // Каждая грань: 4 вершины + оси (ua, va) для текстурных координат.
        face(p, vc, light, stretch, axis, 0, -1, 0, new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, b);
        face(p, vc, light, stretch, axis, 0, 1, 0, new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, b);
        face(p, vc, light, stretch, axis, 0, 0, -1, new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, b);
        face(p, vc, light, stretch, axis, 0, 0, 1, new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, b);
        face(p, vc, light, stretch, axis, -1, 0, 0, new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, b);
        face(p, vc, light, stretch, axis, 1, 0, 0, new float[][] {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, b);
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
                u = q[ua] - uMin;                       // по длине: 1 блок = вся текстура
                w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen; // поперёк: вся высота текстуры
            } else if (endCap) {
                u = 0.02f + (uLen <= 0 ? 0 : (q[ua] - uMin) / uLen) * 0.06f;
                w = vLen <= 0 ? 0 : (q[va] - vMin) / vLen;
            } else {
                u = uLen <= 0 ? 0 : (q[ua] - uMin) / uLen;
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
