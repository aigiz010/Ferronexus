package com.aigiz010.ferronexus.conduit;

import com.aigiz010.ferronexus.registry.FNBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Предмет провода/трубы.
 *  - ПКМ по блоку с проводами, где такого типа ещё нет — добавить в него.
 *  - Если такой тип там уже есть — ставится в соседний блок (протягиваешь линию).
 *  - Shift + ПКМ по блоку с проводами — вынуть этот тип.
 */
public class ConduitItem extends Item {
    private final ConduitType type;

    public ConduitItem(ConduitType type, Properties props) {
        super(props);
        this.type = type;
    }

    public ConduitType type() { return type; }

    private static void tell(Player player, Component msg) {
        if (player instanceof ServerPlayer sp) sp.sendSystemMessage(msg, true);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        boolean creative = player != null && player.getAbilities().instabuild;

        // Shift: вынуть свой тип из блока.
        if (player != null && player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be) {
            if (!be.has(type)) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                be.remove(type);
                if (!creative) {
                    ItemStack back = new ItemStack(this);
                    if (!player.getInventory().add(back)) player.drop(back, false);
                }
                if (be.count() == 0) level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }

        // Добавить в блок, по которому кликнули.
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be && !be.has(type)) {
            return addTo(level, be, player, stack, creative);
        }

        BlockPos target = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(ctx.getClickedFace());
        if (level.getBlockEntity(target) instanceof ConduitBundleBlockEntity be) {
            if (be.has(type)) {
                tell(player, Component.translatable("message.ferronexus.conduit_present", type.displayName()));
                return InteractionResult.FAIL;
            }
            return addTo(level, be, player, stack, creative);
        }
        if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlock(target, FNBlocks.CONDUIT_BUNDLE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(target) instanceof ConduitBundleBlockEntity be) be.add(type);
            if (!creative) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult addTo(Level level, ConduitBundleBlockEntity be, Player player, ItemStack stack, boolean creative) {
        if (be.count() >= ConduitType.MAX_PER_BLOCK) {
            tell(player, Component.translatable("message.ferronexus.conduit_full", ConduitType.MAX_PER_BLOCK));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (be.add(type) && !creative) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
