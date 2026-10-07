package com.aigiz010.ferronexus.conduit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Геометрия блока с проводами (в пикселях 0..16).
 * У каждого провода своя точка узла P = (px, py, pz) в сетке 4x4x4. Сетка подобрана как латинский квадрат:
 * pz = (cx + cy) % 4. Поэтому по каждой оси у всех проводов разные места, и провод идёт из узла к
 * любой стороне прямо — без вертикальных перемычек, а узлы разных проводов никогда не пересекаются.
 * Ось Z: (x=px, y=py); ось X: (y=py, z=pz); ось Y: (x=px, z=pz).
 */
public final class ConduitShapes {
    public static final double ORIGIN = 3, CELL = 2.5, SIZE = 2, PLUG = 1.5;

    private ConduitShapes() {}

    private static double lo(int cell) { return ORIGIN + cell * CELL + (CELL - SIZE) / 2; }

    private static int cx(ConduitType t) { return t.slot() % 4; }

    private static int cy(ConduitType t) { return t.slot() / 4; }

    public static double px(ConduitType t) { return lo(cx(t)); }

    public static double py(ConduitType t) { return lo(cy(t)); }

    public static double pz(ConduitType t) { return lo((cx(t) + cy(t)) % 4); }

    public static double u(ConduitType t) { return px(t); }

    public static double v(ConduitType t) { return py(t); }

    /** Только для совместимости (раньше был общий корпус). */
    public static double[] housing(int mask) { return new double[] {ORIGIN, 16 - ORIGIN}; }

    private static int bit(Direction d) { return 1 << d.ordinal(); }

    /** Ось, если провод идёт ровно насквозь (две противоположные стороны), иначе null. */
    public static Direction.Axis axisOf(int bits) {
        if (bits == (bit(Direction.NORTH) | bit(Direction.SOUTH))) return Direction.Axis.Z;
        if (bits == (bit(Direction.WEST) | bit(Direction.EAST))) return Direction.Axis.X;
        if (bits == (bit(Direction.DOWN) | bit(Direction.UP))) return Direction.Axis.Y;
        return null;
    }

    /** Узел провода — кубик 2x2x2 в точке P. */
    public static double[] joint(ConduitType t) {
        double x = px(t), y = py(t), z = pz(t);
        return new double[] {x, y, z, x + SIZE, y + SIZE, z + SIZE};
    }

    /** Отвод от узла до края блока: {x1,y1,z1,x2,y2,z2}. */
    public static double[] armTo(ConduitType t, Direction d) {
        double x = px(t), y = py(t), z = pz(t), s = SIZE;
        return switch (d) {
            case NORTH -> new double[] {x, y, 0, x + s, y + s, z};
            case SOUTH -> new double[] {x, y, z + s, x + s, y + s, 16};
            case WEST -> new double[] {0, y, z, x, y + s, z + s};
            case EAST -> new double[] {x + s, y, z, 16, y + s, z + s};
            case DOWN -> new double[] {x, 0, z, x + s, y, z + s};
            case UP -> new double[] {x, y + s, z, x + s, 16, z + s};
        };
    }

    /** Отвод вместе с узлом (для выбора провода прицелом). h не используется. */
    public static double[] arm(ConduitType t, Direction d, double[] h) {
        double[] a = armTo(t, d), j = joint(t);
        return new double[] {Math.min(a[0], j[0]), Math.min(a[1], j[1]), Math.min(a[2], j[2]),
                Math.max(a[3], j[3]), Math.max(a[4], j[4]), Math.max(a[5], j[5])};
    }

    /** Сплошной провод сквозь блок по оси. */
    public static double[] through(ConduitType t, Direction.Axis axis) {
        double x = px(t), y = py(t), z = pz(t), s = SIZE;
        return switch (axis) {
            case X -> new double[] {0, y, z, 16, y + s, z + s};
            case Y -> new double[] {x, 0, z, x + s, 16, z + s};
            case Z -> new double[] {x, y, 0, x + s, y + s, 16};
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
        r.add(joint(t));
        for (Direction d : Direction.values()) if ((bits & bit(d)) != 0) r.add(armTo(t, d));
        return r;
    }

    /** Разъём у края блока (вокруг сечения провода). */
    public static double[] plug(ConduitType t, Direction d) {
        double x = px(t) - 0.25, y = py(t) - 0.25, z = pz(t) - 0.25, s = SIZE + 0.5, p = PLUG;
        return switch (d) {
            case NORTH -> new double[] {x, y, 0, x + s, y + s, p};
            case SOUTH -> new double[] {x, y, 16 - p, x + s, y + s, 16};
            case WEST -> new double[] {0, y, z, p, y + s, z + s};
            case EAST -> new double[] {16 - p, y, z, 16, y + s, z + s};
            case DOWN -> new double[] {x, 0, z, x + s, p, z + s};
            case UP -> new double[] {x, 16 - p, z, x + s, 16, z + s};
        };
    }

    /** Стороны, куда идёт провод (соединения + разъёмы + заглушки). */
    public static int bits(ConduitBundleBlockEntity be, ConduitType t) {
        int r = 0;
        for (Direction d : Direction.values())
            if (be.isConnected(t, d) || be.isPlug(t, d) || be.isStub(t, d)) r |= bit(d);
        return r;
    }

    /** Пересечение двух коробок или null. */
    public static double[] intersect(double[] a, double[] b) {
        double[] r = {Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]),
                Math.min(a[3], b[3]), Math.min(a[4], b[4]), Math.min(a[5], b[5])};
        for (int i = 0; i < 3; i++) if (r[i + 3] - r[i] < 0.01) return null;
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
        VoxelShape shape = Shapes.empty();
        for (ConduitType t : ConduitType.values()) {
            if ((mask & t.bit()) == 0) continue;
            for (double[] p : pieces(t, bits(be, t))) shape = Shapes.or(shape, box(p));
            for (Direction d : Direction.values())
                if (be.isPlug(t, d) || be.isStub(t, d)) shape = Shapes.or(shape, box(plug(t, d)));
        }
        return shape.isEmpty() ? Block.box(6, 6, 6, 10, 10, 10) : shape;
    }
}
