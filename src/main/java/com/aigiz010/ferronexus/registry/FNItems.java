package com.aigiz010.ferronexus.registry;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FNItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ferronexus.MOD_ID);

    // Базовые материалы
    public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerSimpleItem("tin_ingot");
    public static final DeferredItem<Item> BRONZE_INGOT = ITEMS.registerSimpleItem("bronze_ingot");
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");

    // Энергия
    public static final DeferredItem<BlockItem> LV_CABLE = ITEMS.registerSimpleBlockItem(FNBlocks.LV_CABLE);
    public static final DeferredItem<BlockItem> MV_CABLE = ITEMS.registerSimpleBlockItem(FNBlocks.MV_CABLE);
    public static final DeferredItem<BlockItem> NEXUS_BUS = ITEMS.registerSimpleBlockItem(FNBlocks.NEXUS_BUS);
    public static final DeferredItem<BlockItem> COAL_GENERATOR = ITEMS.registerSimpleBlockItem(FNBlocks.COAL_GENERATOR);
    public static final DeferredItem<BlockItem> ENERGY_CELL = ITEMS.registerSimpleBlockItem(FNBlocks.ENERGY_CELL);

    // Инструменты
    public static final DeferredItem<ImprinterItem> IMPRINTER = ITEMS.registerItem("imprinter", ImprinterItem::new);

    private FNItems() {}
}
