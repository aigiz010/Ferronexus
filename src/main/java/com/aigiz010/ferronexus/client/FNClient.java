package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Регистрация клиентских рендеров. */
@EventBusSubscriber(modid = Ferronexus.MOD_ID, value = Dist.CLIENT)
public final class FNClient {
    private FNClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FNBlockEntities.CONDUIT_BUNDLE.get(), ConduitBundleRenderer::new);
    }
}
