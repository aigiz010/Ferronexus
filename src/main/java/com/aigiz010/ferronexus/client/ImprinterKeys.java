package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprintMode;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import com.aigiz010.ferronexus.network.RotateImprinterPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Клавиша R — поворот вставки Импринтера + подсказки в описании предмета. */
@EventBusSubscriber(modid = Ferronexus.MOD_ID, value = Dist.CLIENT)
public final class ImprinterKeys {
    private ImprinterKeys() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "main"));
    public static final KeyMapping ROTATE = new KeyMapping(ImprinterItem.ROTATE_KEY, InputConstants.KEY_R, CATEGORY);

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        ROTATE.setKeyConflictContext(KeyConflictContext.IN_GAME);
        event.registerCategory(CATEGORY);
        event.register(ROTATE);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (ROTATE.consumeClick()) {
            Player p = mc.player;
            if (p == null) continue;
            if (p.getMainHandItem().getItem() instanceof ImprinterItem || p.getOffhandItem().getItem() instanceof ImprinterItem) {
                ClientPacketDistributor.sendToServer(RotateImprinterPayload.INSTANCE);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ImprinterItem)) return;
        List<Component> tip = event.getToolTip();
        ImprintMode mode = ImprinterItem.mode(stack);
        tip.add(Component.translatable("tooltip.ferronexus.imprinter.mode",
                Component.translatable("imprinter.ferronexus.mode." + mode.name().toLowerCase())).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.ferronexus.imprinter.rotation", ImprinterItem.rot(stack) * 90).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.ferronexus.imprinter.hint_mode").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("tooltip.ferronexus.imprinter.hint_rotate", Component.keybind(ImprinterItem.ROTATE_KEY)).withStyle(ChatFormatting.YELLOW));
    }
}
