package com.aigiz010.ferronexus.network;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: ПКМ Импринтером по дальнему блоку (дальше обычной досягаемости). */
public record FarUsePayload(BlockPos pos, Direction face, boolean offhand) implements CustomPacketPayload {
    /** В 3 раза дальше обычных 4.5 блока. */
    public static final double FAR = 13.5;

    public static final CustomPacketPayload.Type<FarUsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "imprinter_far_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarUsePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FarUsePayload::pos,
            Direction.STREAM_CODEC, FarUsePayload::face,
            ByteBufCodecs.BOOL, FarUsePayload::offhand,
            FarUsePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarUsePayload msg, IPayloadContext ctx) {
        Player player = ctx.player();
        if (player == null) return;
        InteractionHand hand = msg.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ImprinterItem)) return;
        BlockPos pos = msg.pos();
        if (!player.level().isLoaded(pos)) return;
        double max = FAR + 1.5;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > max * max) return;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), msg.face(), pos, false);
        stack.getItem().useOn(new UseOnContext(player, hand, hit));
    }
}
