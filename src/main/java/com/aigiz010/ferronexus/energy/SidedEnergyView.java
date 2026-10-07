package com.aigiz010.ferronexus.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Вид на буфер для конкретной стороны: только вход, только выход или оба. */
public record SidedEnergyView(FNEnergyStorage storage, boolean input, boolean output) implements EnergyHandler {
    @Override public int insert(int amount, TransactionContext tx) { return input ? storage.insert(amount, tx) : 0; }
    @Override public int extract(int amount, TransactionContext tx) { return output ? storage.extract(amount, tx) : 0; }
    @Override public long getAmountAsLong() { return storage.getAmountAsLong(); }
    @Override public long getCapacityAsLong() { return storage.getCapacityAsLong(); }
}
