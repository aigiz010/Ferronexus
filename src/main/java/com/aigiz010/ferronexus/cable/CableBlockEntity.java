package com.aigiz010.ferronexus.cable;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.FNEnergyStorage;
import com.aigiz010.ferronexus.energy.VoltageTier;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * Простая модель передачи: каждый отрезок провода хранит небольшой буфер,
 * сначала отдаёт энергию потребителям, затем выравнивает заряд с соседними проводами.
 * Скорость ограничена ярусом провода. В 0.5 заменится на общую сеть Nexus.
 */
public class CableBlockEntity extends BlockEntity {
    private final VoltageTier tier;
    private final FNEnergyStorage buffer;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(FNBlockEntities.CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof CableBlock c ? c.tier() : VoltageTier.LV;
        int rate = (int) tier.maxRfPerTick();
        this.buffer = new FNEnergyStorage(rate * 4, rate, rate, this::setChanged);
    }

    public EnergyHandler getEnergy(Direction side) { return buffer; }

    void serverTick() {
        if (level == null || buffer.getEnergyStored() <= 0) return;
        int budget = (int) tier.maxRfPerTick();
        // 1. Потребители (всё, что не провод).
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof CableBlockEntity) continue;
            budget -= EnergyHelper.pushTo(level, worldPosition, dir, buffer, budget);
        }
        // 2. Выравнивание с соседними проводами.
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof CableBlockEntity other)) continue;
            int diff = buffer.getEnergyStored() - other.buffer.getEnergyStored();
            if (diff <= 1) continue;
            int amount = Math.min(budget, Math.min(diff / 2, other.buffer.freeSpace()));
            amount = Math.min(amount, (int) other.tier.maxRfPerTick());
            if (amount <= 0) continue;
            other.buffer.receiveInternal(buffer.extractInternal(amount));
            budget -= amount;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Energy", buffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        buffer.setEnergy(in.getIntOr("Energy", 0));
    }
}
