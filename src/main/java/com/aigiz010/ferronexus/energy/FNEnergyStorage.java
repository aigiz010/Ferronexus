package com.aigiz010.ferronexus.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** RF-буфер машины. Внутренние методы не ограничены скоростью входа/выхода. */
public class FNEnergyStorage extends SimpleEnergyHandler {
    private final Runnable onChanged;

    public FNEnergyStorage(int capacity, int maxInsert, int maxExtract, Runnable onChanged) {
        super(capacity, maxInsert, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        if (onChanged != null) onChanged.run();
    }

    public int getEnergyStored() { return energy; }

    public int getMaxEnergyStored() { return capacity; }

    public int freeSpace() { return Math.max(0, capacity - energy); }

    /** Вне транзакций: добавить энергию без ограничения скорости. */
    public int receiveInternal(int amount) {
        int r = Math.max(0, Math.min(freeSpace(), amount));
        if (r > 0) set(energy + r);
        return r;
    }

    /** Вне транзакций: забрать энергию без ограничения скорости. */
    public int extractInternal(int amount) {
        int r = Math.max(0, Math.min(energy, amount));
        if (r > 0) set(energy - r);
        return r;
    }

    public void setEnergy(int value) { set(Math.max(0, Math.min(capacity, value))); }
}
