package com.aigiz010.ferronexus.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Вид на буфер для конкретной стороны: только вход, только выход или оба. */
public record SidedEnergyView(FNEnergyStorage storage, boolean input, boolean output) implements IEnergyStorage {
    @Override public int receiveEnergy(int amount, boolean simulate) { return input ? storage.receiveEnergy(amount, simulate) : 0; }
    @Override public int extractEnergy(int amount, boolean simulate) { return output ? storage.extractEnergy(amount, simulate) : 0; }
    @Override public int getEnergyStored() { return storage.getEnergyStored(); }
    @Override public int getMaxEnergyStored() { return storage.getMaxEnergyStored(); }
    @Override public boolean canReceive() { return input; }
    @Override public boolean canExtract() { return output; }
}
