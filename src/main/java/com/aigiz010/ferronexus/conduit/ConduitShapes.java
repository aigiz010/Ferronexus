package com.aigiz010.ferronexus.conduit;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия блока с проводами (в пикселях 0..16).
 * Каждый тип занимает ячейку сетки 4x4 в сечении 3..13, сам провод 2x2 px.
 * Ось Z: (x=u, y=v); ось X: (y=u, z=v); ось Y: (x=u, z=v).
 */
public final class ConduitShapes {
    public static final double ORIGIN = 3, CELL = 2.5, SIZE = 2, PLUG = 1.5;

    private ConduitShapes() {}

    private static double lo(int cell) { return ORIGIN + cell * CELL + (CELL - SIZE) / 2; }

    public static double u(ConduitType t) { return lo(t.slot() % 4); }

    public static double v(ConduitType t) { return lo(t.slot() / 4); }

    /** Корпус (общая коробка в центре) для набора типов: {min, max}. */
    public static double[] housing(int mask) {
        double min = 16, max = 0;
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            min = Math.min(min, Math.min(u(t), v(t)));
            max = Math.max(max, Math.max(u(t), v(t)) + SIZE);
        }
        if (min > max) return new double[] {6, 10};
        return new double[] {min - 0.5, max + 0.5};
    }

    /** Отрезок провода от корпуса до края блока: {x1,y1,z1,x2,y2,z2}. */
    public static double[] arm(ConduitType t, Direction d, double[] h) {
        double a = u(t), b = v(t), s = SIZE;
        return switch (d) {
            case NORTH -> new double[] {a, b, 0, a + s, b + s, h[0]};
            case SOUTH -> new double[] {a, b, h[1], a + s, b + s, 16};
            case WEST -> new double[] {0, a, b, h[0], a + s, b + s};
            case EAST -> new double[] {h[1], a, b, 16, a + s, b + s};
            case DOWN -> new double[] {a, 0, b, a + s, h[0], b + s};
            case UP -> new double[] {a, h[1], b, a + s, 16, b + s};
        };
    }

    /** Разъём у края блока (чуть шире провода). */
    public static double[] plug(ConduitType t, Direction d) {
        double a = u(t) - 0.5, b = v(t) - 0.5, s = SIZE + 1, p = PLUG;
        return switch (d) {
            case NORTH -> new double[] {a, b, 0, a + s, b + s, p};
            case SOUTH -> new double[] {a, b, 16 - p, a + s, b + s, 16};
            case WEST -> new double[] {0, a, b, p, a + s, b + s};
            case EAST -> new double[] {16 - p, a, b, 16, a + s, b + s};
            case DOWN -> new double[] {a, 0, b, a + s, p, b + s};
            case UP -> new double[] {a, 16 - p, b, a + s, 16, b + s};
        };
    }

    /** Направления (соединения + разъёмы) для каждого типа битами Direction.ordinal. */
    public static int[] dirs(ConduitBundleBlockEntity be) {
        int[] r = new int[ConduitType.values().length];
        for (ConduitType t : ConduitType.values())
            for (Direction d : Direction.values())
                if (be.isConnected(t, d) || be.isPlug(t, d) || be.isStub(t, d)) r[t.ordinal()] |= 1 << d.ordinal();
        return r;
    }

    /** Если все провода идут прямо по одной оси (без развилок и поворотов) — эта ось, иначе null. */
    public static Direction.Axis straightAxis(int mask, int[] dirs) {
        Direction.Axis axis = null;
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            Direction.Axis found = null;
            for (Direction.Axis ax : Direction.Axis.values()) {
                int pair = 0;
                for (Direction d : Direction.values()) if (d.getAxis() == ax) pair |= 1 << d.ordinal();
                if (dirs[t.ordinal()] == pair) found = ax;
            }
            if (found == null || (axis != null && axis != found)) return null;
            axis = found;
        }
        return axis;
    }

    /** Сплошной провод сквозь блок по оси. */
    public static double[] through(ConduitType t, Direction.Axis axis, double[] h) {
        double[] r = {16, 16, 16, 0, 0, 0};
        for (Direction d : Direction.values()) {
            if (d.getAxis() != axis) continue;
            double[] a = arm(t, d, h);
            for (int i = 0; i < 3; i++) { r[i] = Math.min(r[i], a[i]); r[i + 3] = Math.max(r[i + 3], a[i + 3]); }
        }
        return r;
    }

    public static VoxelShape box(double[] b) {
        return Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public static boolean contains(double[] b, double x, double y, double z) {
        double e = 0.15;
        return x >= b[0] - e && x <= b[3] + e && y >= b[1] - e && y <= b[4] + e && z >= b[2] - e && z <= b[5] + e;
    }

    public static VoxelShape build(ConduitBundleBlockEntity be) {
        int mask = be.typesMask();
        double[] h = housing(mask);
        Direction.Axis axis = straightAxis(mask, dirs(be));
        VoxelShape shape = axis != null ? Shapes.empty() : Block.box(h[0], h[0], h[0], h[1], h[1], h[1]);
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            if (axis != null) shape = Shapes.or(shape, box(through(t, axis, h)));
            for (Direction d : Direction.values()) {
                if (axis == null && be.isConnected(t, d)) shape = Shapes.or(shape, box(arm(t, d, h)));
                if (be.isPlug(t, d) || be.isStub(t, d)) shape = Shapes.or(shape, box(plug(t, d)));
            }
        }
        return shape.isEmpty() ? Block.box(h[0], h[0], h[0], h[1], h[1], h[1]) : shape;
    }
}
