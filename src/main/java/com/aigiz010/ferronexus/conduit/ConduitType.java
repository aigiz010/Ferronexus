package com.aigiz010.ferronexus.conduit;

import com.aigiz010.ferronexus.registry.FNItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * Типы проводов и труб. В одном блоке может стоять до {@link #MAX_PER_BLOCK}
 * РАЗНЫХ типов; один и тот же тип дважды в блок не ставится.
 * Каждый тип занимает свою постоянную ячейку 4x4 внутри блока,
 * поэтому одинаковые провода в соседних блоках всегда совпадают.
 */
public enum ConduitType {
    LV_CABLE("lv_cable", ConduitKind.ENERGY, 32),
    MV_CABLE("mv_cable", ConduitKind.ENERGY, 128),
    HV_CABLE("hv_cable", ConduitKind.ENERGY, 512),
    EV_CABLE("ev_cable", ConduitKind.ENERGY, 2048),
    ITEM_PIPE("item_pipe", ConduitKind.ITEM, 8),
    FAST_ITEM_PIPE("fast_item_pipe", ConduitKind.ITEM, 32),
    FLUID_PIPE("fluid_pipe", ConduitKind.FLUID, 250),
    PRESSURE_FLUID_PIPE("pressure_fluid_pipe", ConduitKind.FLUID, 1000),
    GAS_PIPE("gas_pipe", ConduitKind.GAS, 500),
    REDSTONE_CONDUIT("redstone_conduit", ConduitKind.REDSTONE, 15);

    public static final int MAX_PER_BLOCK = 16;

    private final String id;
    private final ConduitKind kind;
    private final int rate;

    ConduitType(String id, ConduitKind kind, int rate) {
        this.id = id;
        this.kind = kind;
        this.rate = rate;
    }

    public String id() { return id; }

    public ConduitKind kind() { return kind; }

    /** Энергия: RF/t. Предметы: штук за операцию. Жидкость/газ: mB за операцию. */
    public int rate() { return rate; }

    public int bit() { return 1 << ordinal(); }

    /** Ячейка 0..15 в сетке 4x4. */
    public int slot() { return ordinal(); }

    public Item item() { return FNItems.CONDUITS.get(this).get(); }

    public Component displayName() { return Component.translatable("item.ferronexus." + id); }

    public FaceMode defaultMode() {
        return kind == ConduitKind.ENERGY || kind == ConduitKind.REDSTONE ? FaceMode.BOTH : FaceMode.OUTPUT;
    }

    public static ConduitType byOrdinal(int i) {
        ConduitType[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
