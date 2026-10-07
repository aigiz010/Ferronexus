package com.aigiz010.ferronexus.compat.jade;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlockEntity;
import com.aigiz010.ferronexus.conduit.ConduitType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Список установленных проводов и тот, на который смотрит игрок. */
public enum ConduitTooltip implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Ferronexus.MOD_ID, "conduits");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof ConduitBundleBlockEntity be)) return;
        BlockPos pos = accessor.getPosition();
        Vec3 hit = accessor.getHitResult().getLocation();
        ConduitBundleBlockEntity.Pick pick = be.pick((hit.x - pos.getX()) * 16, (hit.y - pos.getY()) * 16, (hit.z - pos.getZ()) * 16);

        tooltip.add(Component.translatable("jade.ferronexus.conduits", be.count(), ConduitType.MAX_PER_BLOCK)
                .withStyle(ChatFormatting.GRAY));
        for (ConduitType t : be.list()) {
            int links = 0, plugs = 0;
            for (Direction d : Direction.values()) {
                if (be.isConnected(t, d)) links++;
                if (be.isPlug(t, d)) plugs++;
            }
            boolean looked = pick != null && pick.type() == t;
            MutableComponent line = Component.literal(looked ? "▶ " : "• ")
                    .append(t.displayName())
                    .append(Component.translatable("jade.ferronexus.links", links, plugs).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(line.withStyle(looked ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
        if (pick != null) {
            tooltip.add(Component.translatable("jade.ferronexus.side",
                    Component.translatable("jade.ferronexus.dir." + pick.side().getSerializedName()),
                    be.mode(pick.type(), pick.side()).name()).withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public Identifier getUid() { return UID; }
}
