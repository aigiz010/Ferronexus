package com.aigiz010.ferronexus.client;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.imprinter.ImprinterItem;
import com.aigiz010.ferronexus.network.FarUsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Дальний прицел Импринтера: если обычный прицел не достаёт до блока,
 * ищем блок в 3 раза дальше и отправляем нажатие на сервер сами.
 */
@EventBusSubscriber(modid = Ferronexus.MOD_ID, value = Dist.CLIENT)
public final class ImprinterFar {
    private ImprinterFar() {}

    /** Блок под прицелом: обычный, а если его нет — дальний. */
    public static BlockHitResult target(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) return b;
        if (mc.player == null) return null;
        HitResult h = mc.player.pick(FarUsePayload.FAR, 1.0f, false);
        return h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        boolean main = p.getMainHandItem().getItem() instanceof ImprinterItem;
        boolean off = !main && p.getOffhandItem().getItem() instanceof ImprinterItem;
        if (!main && !off) return;
        if (p.isShiftKeyDown()) return; // Shift+ПКМ — смена режима, пусть обрабатывает игра
        if (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) return; // близко — обычный клик
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return;
        while (mc.options.keyUse.consumeClick()) {
            HitResult h = p.pick(FarUsePayload.FAR, 1.0f, false);
            if (h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                ClientPacketDistributor.sendToServer(new FarUsePayload(b.getBlockPos(), b.getDirection(), off));
            }
        }
    }
}
