package com.aigiz010.ferronexus.machine;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.FNEnergyStorage;
import com.aigiz010.ferronexus.energy.VoltageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** Базовая машина: RF-буфер, ярус мощности, приём энергии других систем с конвертацией. */
public abstract class MachineBlockEntity extends BlockEntity {
    protected final VoltageTier tier;
    protected final FNEnergyStorage energy;
    private final boolean acceptsForeign;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                 VoltageTier tier, int capacity, boolean acceptsInput, int maxOutput) {
        super(type, pos, state);
        this.tier = tier;
        int in = acceptsInput ? (int) tier.maxRfPerTick() : 0;
        this.energy = new FNEnergyStorage(capacity, in, maxOutput, this::setChanged);
        this.acceptsForeign = acceptsInput;
    }

    public final void serverTick() {
        if (level == null) return;
        if (acceptsForeign && energy.freeSpace() > 0) {
            EnergyHelper.pullForeign(level, worldPosition, energy, (int) tier.maxRfPerTick());
        }
        tickMachine();
    }

    protected abstract void tickMachine();

    /** Что видят соседи с указанной стороны. */
    public abstract EnergyHandler getEnergy(Direction side);

    public FNEnergyStorage energy() { return energy; }

    public VoltageTier tier() { return tier; }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.setEnergy(in.getIntOr("Energy", 0));
    }
}
