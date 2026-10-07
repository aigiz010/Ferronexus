package com.aigiz010.ferronexus.conduit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия блока с проводами (в пикселях 0..16).
 * У каждого провода свой тонкий узел. Сечение: сетка 4x4 в 3..13, провод 2x2 px.
 * Горизонтальные провода всегда на одной высоте y=v, поэтому повороты без ступенек:
 * ось Z: (x=u, y=v); ось X: (z=u, y=v); ось Y: (x=u, z=v).
 */
public final class ConduitShapes {
    public static final double ORIGIN = 3, CELL = 2.5, SIZE = 2, PLUG = 1.5;

    private ConduitShapes() {}

    private static double lo(int cell) { return ORIGIN + cell * CELL + (CELL - SIZE) / 2; }

    public static double u(ConduitType t) { return lo(t.slot() % 4); }

    public static double v(ConduitType t) { return lo(t.slot() / 4); }

    /** Только для совместимости (раньше был общий корпус). */
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

    private static int bit(Direction d) { return 1 << d.ordinal(); }

    private static final int Z_BITS = 1 << 2 | 1 << 3, X_BITS = 1 << 4 | 1 << 5, Y_BITS = 1 | 1 << 1;

    /** Ось, если провод идёт ровно насквозь (две противоположные стороны), иначе null. */
    public static Direction.Axis axisOf(int bits) {
        if (bits == (bit(Direction.NORTH) | bit(Direction.SOUTH))) return Direction.Axis.Z;
        if (bits == (bit(Direction.WEST) | bit(Direction.EAST))) return Direction.Axis.X;
        if (bits == (bit(Direction.DOWN) | bit(Direction.UP))) return Direction.Axis.Y;
        return null;
    }

    /** Диапазон узла по Z: охватывает сечения отводов по X и по Y. */
    public static double[] jointZ(ConduitType t, int bits) {
        double a = u(t), b = v(t), lo = 16, hi = 0;
        if ((bits & (X_BITS | Z_BITS)) != 0) { lo = Math.min(lo, a); hi = Math.max(hi, a + SIZE); }
        if ((bits & Y_BITS) != 0) { lo = Math.min(lo, b); hi = Math.max(hi, b + SIZE); }
        if (lo > hi) { lo = a; hi = a + SIZE; }
        return new double[] {lo, hi};
    }

    /** Узел провода (толщиной с сам провод). */
    public static double[] joint(ConduitType t, double[] jz) {
        double a = u(t), b = v(t);
        return new double[] {a, b, jz[0], a + SIZE, b + SIZE, jz[1]};
    }

    /** Отвод от узла до края блока: {x1,y1,z1,x2,y2,z2}. */
    public static double[] armTo(ConduitType t, Direction d, double[] jz) {
        double a = u(t), b = v(t), s = SIZE;
        return switch (d) {
            case NORTH -> new double[] {a, b, 0, a + s, b + s, jz[0]};
            case SOUTH -> new double[] {a, b, jz[1], a + s, b + s, 16};
            case WEST -> new double[] {0, b, a, a, b + s, a + s};
            case EAST -> new double[] {a + s, b, a, 16, b + s, a + s};
            case DOWN -> new double[] {a, 0, b, a + s, b, b + s};
            case UP -> new double[] {a, b + s, b, a + s, 16, b + s};
        };
    }

    /** Совместимость: отвод с полным узлом (h больше не используется). */
    public static double[] arm(ConduitType t, Direction d, double[] h) {
        double a = u(t), b = v(t);
        return armTo(t, d, new double[] {Math.min(a, b), Math.max(a, b) + SIZE});
    }

    /** Сплошной провод сквозь блок по оси. */
    public static double[] through(ConduitType t, Direction.Axis axis) {
        double a = u(t), b = v(t), s = SIZE;
        return switch (axis) {
            case X -> new double[] {0, b, a, 16, b + s, a + s};
            case Y -> new double[] {a, 0, b, a + s, 16, b + s};
            case Z -> new double[] {a, b, 0, a + s, b + s, 16};
        };
    }

    /** Все части одного провода (без разъёмов). bits — стороны, куда он идёт. */
    public static List<double[]> pieces(ConduitType t, int bits) {
        List<double[]> r = new ArrayList<>();
        Direction.Axis ax = axisOf(bits);
        if (ax != null) {
            r.add(through(t, ax));
            return r;
        }
        double[] jz = jointZ(t, bits);
        r.add(joint(t, jz));
        for (Direction d : Direction.values()) if ((bits & bit(d)) != 0) r.add(armTo(t, d, jz));
        return r;
    }

    /** Разъём у края блока (чуть шире провода). */
    public static double[] plug(ConduitType t, Direction d) {
        double a = u(t) - 0.5, b = v(t) - 0.5, s = SIZE + 1, p = PLUG;
        return switch (d) {
            case NORTH -> new double[] {a, b, 0, a + s, b + s, p};
            case SOUTH -> new double[] {a, b, 16 - p, a + s, b + s, 16};
            case WEST -> new double[] {0, b, a, p, b + s, a + s};
            case EAST -> new double[] {16 - p, b, a, 16, b + s, a + s};
            case DOWN -> new double[] {a, 0, b, a + s, p, b + s};
            case UP -> new double[] {a, 16 - p, b, a + s, 16, b + s};
        };
    }

    /** Стороны, куда идёт провод (соединения + разъёмы). */
    public static int bits(ConduitBundleBlockEntity be, ConduitType t) {
        int r = 0;
        for (Direction d : Direction.values())
            if (be.isConnected(t, d) || be.isPlug(t, d) || be.isStub(t, d)) r |= bit(d);
        return r;
    }

    /** Какой провод под точкой (локальные координаты 0..16), включая узлы. */
    public static ConduitType pickType(ConduitBundleBlockEntity be, double x, double y, double z) {
        int mask = be.typesMask();
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            int b = bits(be, t);
            for (double[] p : pieces(t, b)) if (contains(p, x, y, z)) return t;
            for (Direction d : Direction.values())
                if ((b & bit(d)) != 0 && (be.isPlug(t, d) || be.isStub(t, d)) && contains(plug(t, d), x, y, z)) return t;
        }
        return null;
    }

    public static boolean overlaps(double[] a, double[] b) {
        return a[0] < b[3] && b[0] < a[3] && a[1] < b[4] && b[1] < a[4] && a[2] < b[5] && b[2] < a[5];
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
        VoxelShape shape = Shapes.empty();
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            int b = bits(be, t);
            for (double[] p : pieces(t, b)) shape = Shapes.or(shape, box(p));
            for (Direction d : Direction.values())
                if (be.isPlug(t, d) || be.isStub(t, d)) shape = Shapes.or(shape, box(plug(t, d)));
        }
        return shape.isEmpty() ? Block.box(6, 6, 6, 10, 10, 10) : shape;
    }
}
