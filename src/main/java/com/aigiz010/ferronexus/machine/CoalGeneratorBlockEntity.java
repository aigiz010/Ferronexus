package com.aigiz010.ferronexus.machine;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.SidedEnergyView;
import com.aigiz010.ferronexus.energy.VoltageTier;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class CoalGeneratorBlockEntity extends MachineBlockEntity {
    public static final int RF_PER_TICK = 20;
    public static final int CAPACITY = 10_000;
    public static final int MAX_OUTPUT = 32;

    private final SidedEnergyView outputView = new SidedEnergyView(energy, false, true);
    private ItemStack fuel = ItemStack.EMPTY;
    private int burnTime;
    private int burnTotal;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(FNBlockEntities.COAL_GENERATOR.get(), pos, state, VoltageTier.LV, CAPACITY, false, MAX_OUTPUT);
    }

    @Override
    protected void tickMachine() {
        boolean wasLit = burnTime > 0;
        if (burnTime > 0) {
            burnTime--;
            energy.receiveInternal(RF_PER_TICK);
        } else if (!fuel.isEmpty() && energy.freeSpace() >= RF_PER_TICK) {
            int duration = level.fuelValues().burnDuration(fuel);
            if (duration > 0) {
                burnTime = burnTotal = duration;
                fuel.shrink(1);
                setChanged();
            }
        }
        int budget = MAX_OUTPUT;
        for (Direction dir : Direction.values()) {
            if (budget <= 0) break;
            budget -= EnergyHelper.pushTo(level, worldPosition, dir, energy, budget);
        }
        boolean lit = burnTime > 0;
        if (lit != wasLit) {
            BlockState state = getBlockState();
            if (state.hasProperty(CoalGeneratorBlock.LIT)) {
                level.setBlock(worldPosition, state.setValue(CoalGeneratorBlock.LIT, lit), 3);
            }
        }
    }

    /** Положить топливо из руки. Возвращает true, если что-то принято. */
    public boolean insertFuel(ItemStack held) {
        if (held.isEmpty() || level == null || level.fuelValues().burnDuration(held) <= 0) return false;
        if (fuel.isEmpty()) {
            fuel = held.split(held.getMaxStackSize());
        } else if (ItemStack.isSameItemSameComponents(fuel, held)) {
            int move = Math.min(held.getCount(), fuel.getMaxStackSize() - fuel.getCount());
            if (move <= 0) return false;
            fuel.grow(move);
            held.shrink(move);
        } else {
            return false;
        }
        setChanged();
        return true;
    }

    public ItemStack fuel() { return fuel; }

    public int burnProgressPercent() { return burnTotal == 0 ? 0 : burnTime * 100 / burnTotal; }

    @Override
    public IEnergyStorage getEnergy(Direction side) { return outputView; }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !fuel.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuel);
            fuel = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("BurnTime", burnTime);
        out.putInt("BurnTotal", burnTotal);
        if (!fuel.isEmpty()) out.store("Fuel", ItemStack.CODEC, fuel);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        burnTime = in.getIntOr("BurnTime", 0);
        burnTotal = in.getIntOr("BurnTotal", 0);
        fuel = in.read("Fuel", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
