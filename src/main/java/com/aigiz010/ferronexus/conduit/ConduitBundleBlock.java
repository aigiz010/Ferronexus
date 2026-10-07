package com.aigiz010.ferronexus.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Блок-носитель проводов и труб. Сам по себе не ставится — появляется,
 * когда ставишь любой провод/трубу.
 *  - ПКМ проводом по блоку: добавить этот тип (если его там ещё нет).
 *  - Shift + ПКМ проводом: вынуть этот тип обратно.
 *  - Shift + ПКМ пустой рукой по отрезку/разъёму: режим стороны.
 */
public class ConduitBundleBlock extends Block implements EntityBlock {
    private static final VoxelShape DEFAULT = Block.box(6, 6, 6, 10, 10, 10);

    public ConduitBundleBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be ? be.shape() : DEFAULT;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be) be.markRefresh();
        return state;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be ? be.signalTowards(direction.getOpposite()) : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be)) return InteractionResult.PASS;
        Vec3 v = hit.getLocation();
        ConduitBundleBlockEntity.Pick pick = be.pick((v.x - pos.getX()) * 16, (v.y - pos.getY()) * 16, (v.z - pos.getZ()) * 16);
        if (pick == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            FaceMode mode = be.cycleMode(pick.type(), pick.side());
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.ferronexus.conduit_face",
                        pick.type().displayName(),
                        Component.translatable("direction.ferronexus." + pick.side().getSerializedName()),
                        Component.translatable("facemode.ferronexus." + mode.id())), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity be) {
            for (ConduitType t : be.list()) Block.popResource(level, pos, new ItemStack(t.item()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBundleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof ConduitBundleBlockEntity b) b.serverTick();
        };
    }
}
