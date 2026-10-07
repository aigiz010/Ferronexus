package com.aigiz010.ferronexus.machine;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.SidedEnergyView;
import com.aigiz010.ferronexus.energy.VoltageTier;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Накопитель LV: принимает со всех сторон, отдаёт только через лицевую сторону. */
public class EnergyCellBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 100_000;

    private final SidedEnergyView inputView = new SidedEnergyView(energy, true, false);
    private final SidedEnergyView outputView = new SidedEnergyView(energy, false, true);

    public EnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(FNBlockEntities.ENERGY_CELL.get(), pos, state, VoltageTier.LV, CAPACITY, true,
                (int) VoltageTier.LV.maxRfPerTick());
    }

    private Direction front() { return getBlockState().getValue(MachineBlock.FACING); }

    @Override
    protected void tickMachine() {
        EnergyHelper.pushTo(level, worldPosition, front(), energy, (int) tier.maxRfPerTick());
    }

    @Override
    public IEnergyStorage getEnergy(Direction side) {
        return side == front() ? outputView : inputView;
    }
}
