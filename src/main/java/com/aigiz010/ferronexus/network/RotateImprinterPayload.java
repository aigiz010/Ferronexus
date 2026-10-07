package com.aigiz010.ferronexus.network;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: повернуть схему в Импринтере на 90° по часовой. */
public record RotateImprinterPayload() implements CustomPacketPayload {
    public static final RotateImprinterPayload INSTANCE = new RotateImprinterPayload();
    public static final CustomPacketPayload.Type<RotateImprinterPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "rotate_imprinter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RotateImprinterPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RotateImprinterPayload payload, IPayloadContext ctx) {
        Player player = ctx.player();
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ImprinterItem)) stack = player.getOffhandItem();
        if (stack.getItem() instanceof ImprinterItem) ImprinterItem.rotate(stack, player);
    }
}
