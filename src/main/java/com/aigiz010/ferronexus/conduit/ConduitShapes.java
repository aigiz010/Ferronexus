package com.aigiz010.ferronexus.conduit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия блока с проводами (в пикселях 0..16).
 * Общего корпуса нет: у каждого провода свой тонкий узел, в котором сходятся его отводы.
 * Каждый тип занимает ячейку сетки 4x4 в сечении 3..13, сам провод 2x2 px.
 * Ось Z: (x=u, y=v); ось X: (y=u, z=v); ось Y: (x=u, z=v).
 */
public final class ConduitShapes {
    public static final double ORIGIN = 3, CELL = 2.5, SIZE = 2, PLUG = 1.5;

    private ConduitShapes() {}

    private static double lo(int cell) { return ORIGIN + cell * CELL + (CELL - SIZE) / 2; }

    public static double u(ConduitType t) { return lo(t.slot() % 4); }

    public static double v(ConduitType t) { return lo(t.slot() / 4); }

    /** Габарит сечения для набора типов: {min, max}. Раньше здесь был корпус, сейчас только для совместимости. */
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

    /** Ось, если провод идёт ровно насквозь (две противоположные стороны), иначе null. */
    public static Direction.Axis axisOf(int bits) {
        if (bits == (bit(Direction.NORTH) | bit(Direction.SOUTH))) return Direction.Axis.Z;
        if (bits == (bit(Direction.WEST) | bit(Direction.EAST))) return Direction.Axis.X;
        if (bits == (bit(Direction.DOWN) | bit(Direction.UP))) return Direction.Axis.Y;
        return null;
    }

    /** Высота узла провода: охватывает сечения использованных горизонтальных отводов. */
    public static double[] jointY(ConduitType t, int bits) {
        double a = u(t), b = v(t), lo = 16, hi = 0;
        if ((bits & (bit(Direction.NORTH) | bit(Direction.SOUTH))) != 0) { lo = Math.min(lo, b); hi = Math.max(hi, b + SIZE); }
        if ((bits & (bit(Direction.WEST) | bit(Direction.EAST))) != 0) { lo = Math.min(lo, a); hi = Math.max(hi, a + SIZE); }
        if (lo > hi) { lo = b; hi = b + SIZE; }
        return new double[] {lo, hi};
    }

    /** Узел провода (толщиной с сам провод). */
    public static double[] joint(ConduitType t, double[] jy) {
        double a = u(t), b = v(t);
        return new double[] {a, jy[0], b, a + SIZE, jy[1], b + SIZE};
    }

    /** Отвод от узла до края блока: {x1,y1,z1,x2,y2,z2}. */
    public static double[] armTo(ConduitType t, Direction d, double[] jy) {
        double a = u(t), b = v(t), s = SIZE;
        return switch (d) {
            case NORTH -> new double[] {a, b, 0, a + s, b + s, b};
            case SOUTH -> new double[] {a, b, b + s, a + s, b + s, 16};
            case WEST -> new double[] {0, a, b, a, a + s, b + s};
            case EAST -> new double[] {a + s, a, b, 16, a + s, b + s};
            case DOWN -> new double[] {a, 0, b, a + s, jy[0], b + s};
            case UP -> new double[] {a, jy[1], b, a + s, 16, b + s};
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
            case X -> new double[] {0, a, b, 16, a + s, b + s};
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
        double[] jy = jointY(t, bits);
        r.add(joint(t, jy));
        for (Direction d : Direction.values()) if ((bits & bit(d)) != 0) r.add(armTo(t, d, jy));
        return r;
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
            int bits = 0, ends = 0;
            for (Direction d : Direction.values()) {
                if (be.isConnected(t, d)) bits |= bit(d);
                if (be.isPlug(t, d) || be.isStub(t, d)) { bits |= bit(d); ends |= bit(d); }
            }
            for (double[] p : pieces(t, bits)) shape = Shapes.or(shape, box(p));
            for (Direction d : Direction.values()) if ((ends & bit(d)) != 0) shape = Shapes.or(shape, box(plug(t, d)));
        }
        return shape.isEmpty() ? Block.box(6, 6, 6, 10, 10, 10) : shape;
    }
}
