package com.aigiz010.ferronexus.network;

import com.aigiz010.ferronexus.Ferronexus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Ferronexus.MOD_ID)
public final class FNNetwork {
    private FNNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(RotateImprinterPayload.TYPE, RotateImprinterPayload.STREAM_CODEC, RotateImprinterPayload::handle);
    }
}
