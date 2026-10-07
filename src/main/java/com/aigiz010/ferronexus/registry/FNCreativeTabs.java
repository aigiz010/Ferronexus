package com.aigiz010.ferronexus.registry;

import com.aigiz010.ferronexus.Ferronexus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FNCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ferronexus.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ferronexus.main"))
                    .icon(() -> new ItemStack(FNItems.IMPRINTER.get()))
                    .displayItems((params, out) -> FNItems.ITEMS.getEntries().forEach(e -> out.accept(e.get())))
                    .build());

    private FNCreativeTabs() {}
}
