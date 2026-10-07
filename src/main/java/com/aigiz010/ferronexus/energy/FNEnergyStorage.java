package com.aigiz010.ferronexus.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

/** RF-буфер машины. Внутренние методы не ограничены скоростью входа/выхода. */
public class FNEnergyStorage extends EnergyStorage {
    private final Runnable onChanged;

    public FNEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int r = super.receiveEnergy(amount, simulate);
        if (r > 0 && !simulate) onChanged.run();
        return r;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        int r = super.extractEnergy(amount, simulate);
        if (r > 0 && !simulate) onChanged.run();
        return r;
    }

    public int receiveInternal(int amount) {
        int r = Math.max(0, Math.min(capacity - energy, amount));
        if (r > 0) { energy += r; onChanged.run(); }
        return r;
    }

    public int extractInternal(int amount) {
        int r = Math.max(0, Math.min(energy, amount));
        if (r > 0) { energy -= r; onChanged.run(); }
        return r;
    }

    public int freeSpace() { return capacity - energy; }

    public void setEnergy(int value) { energy = Math.max(0, Math.min(capacity, value)); }
}
