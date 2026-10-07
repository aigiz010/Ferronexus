package com.aigiz010.ferronexus;

import com.aigiz010.ferronexus.registry.FNBlockEntities;
import com.aigiz010.ferronexus.registry.FNBlocks;
import com.aigiz010.ferronexus.registry.FNCreativeTabs;
import com.aigiz010.ferronexus.registry.FNItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Ferronexus.MOD_ID)
public final class Ferronexus {
    public static final String MOD_ID = "ferronexus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ferronexus(IEventBus modBus, ModContainer container) {
        FNBlocks.BLOCKS.register(modBus);
        FNItems.ITEMS.register(modBus);
        FNBlockEntities.TYPES.register(modBus);
        FNCreativeTabs.TABS.register(modBus);
        modBus.addListener(FNBlockEntities::registerCapabilities);
        LOGGER.info("Ferronexus initialised");
    }
}
