package com.aigiz010.ferronexus.compat.jade;

import com.aigiz010.ferronexus.conduit.ConduitBundleBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Подсказки при наведении. Загружается только если подсказки установлены. */
@WailaPlugin
public class FNJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ConduitTooltip.INSTANCE, ConduitBundleBlock.class);
    }
}
