package com.aigiz010.ferronexus.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class EnergyHelper {
    private EnergyHelper() {}

    public static IEnergyStorage neighbour(Level level, BlockPos pos, Direction dir) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
    }

    /** Отдать RF соседу. Возвращает, сколько принято. */
    public static int pushTo(Level level, BlockPos pos, Direction dir, FNEnergyStorage src, int max) {
        int offer = Math.min(max, src.getEnergyStored());
        if (offer <= 0) return 0;
        IEnergyStorage target = neighbour(level, pos, dir);
        if (target == null || !target.canReceive()) return 0;
        int accepted = target.receiveEnergy(offer, false);
        src.extractInternal(accepted);
        return accepted;
    }

    /**
     * Забрать энергию любых других систем у соседей и перевести её в RF.
     * Работает через зарегистрированные {@link ForeignEnergyAdapter}.
     */
    public static int pullForeign(Level level, BlockPos pos, FNEnergyStorage dst, int maxRf) {
        int remaining = Math.min(maxRf, dst.freeSpace());
        int total = 0;
        for (ForeignEnergyAdapter adapter : EnergyConversion.adapters()) {
            for (Direction dir : Direction.values()) {
                if (remaining <= 0) return total;
                BlockEntity be = level.getBlockEntity(pos.relative(dir));
                if (be == null) continue;
                Direction side = dir.getOpposite();
                if (!adapter.canExtract(be, side)) continue;
                String id = adapter.systemId();
                long wanted = EnergyConversion.fromRf(id, remaining);
                long available = adapter.extractNative(be, side, wanted, true);
                if (available <= 0) continue;
                long taken = adapter.extractNative(be, side, available, false);
                int rf = (int) Math.min(remaining, EnergyConversion.toRf(id, taken));
                dst.receiveInternal(rf);
                remaining -= rf;
                total += rf;
            }
        }
        return total;
    }
}
