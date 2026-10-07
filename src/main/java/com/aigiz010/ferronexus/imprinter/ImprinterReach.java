package com.aigiz010.ferronexus.imprinter;

import com.aigiz010.ferronexus.Ferronexus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Пока Импринтер в любой руке — дальность работы с блоками в 3 раза больше.
 * Работает и на клиенте (прицел, предпросмотр), и на сервере (проверка дальности нажатия).
 */
@EventBusSubscriber(modid = Ferronexus.MOD_ID)
public final class ImprinterReach {
    private ImprinterReach() {}

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "imprinter_reach");

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        AttributeInstance range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (range == null) return;
        boolean holding = player.getMainHandItem().getItem() instanceof ImprinterItem
                || player.getOffhandItem().getItem() instanceof ImprinterItem;
        boolean has = range.hasModifier(ID);
        if (holding && !has) {
            range.addTransientModifier(new AttributeModifier(ID, ImprinterItem.REACH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!holding && has) {
            range.removeModifier(ID);
        }
    }
}
