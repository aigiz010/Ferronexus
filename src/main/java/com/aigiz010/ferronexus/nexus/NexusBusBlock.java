package com.aigiz010.ferronexus.nexus;

import com.aigiz010.ferronexus.cable.ConnectionType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Шина Nexus: один тонкий блок, по которому одновременно идут энергия (RF),
 * предметы и жидкости. Shift + ПКМ пустой рукой по разъёму меняет режим стороны.
 */
public class NexusBusBlock extends Block implements EntityBlock {
    public static final Map<Direction, EnumProperty<ConnectionType>> SIDES = new EnumMap<>(Direction.class);
    static {
        for (Direction dir : Direction.values()) {
            SIDES.put(dir, EnumProperty.create(dir.getSerializedName(), ConnectionType.class));
        }
    }

    private static final VoxelShape NODE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> PLUGS = new EnumMap<>(Direction.class);
    static {
        double a = 6, b = 10, n = 5, f = 11;
        ARMS.put(Direction.NORTH, Block.box(a, a, 0, b, b, n));
        ARMS.put(Direction.SOUTH, Block.box(a, a, f, b, b, 16));
        ARMS.put(Direction.WEST, Block.box(0, a, a, n, b, b));
        ARMS.put(Direction.EAST, Block.box(f, a, a, 16, b, b));
        ARMS.put(Direction.DOWN, Block.box(a, 0, a, b, n, b));
        ARMS.put(Direction.UP, Block.box(a, f, a, b, 16, b));
        PLUGS.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 2.5));
        PLUGS.put(Direction.SOUTH, Block.box(4, 4, 13.5, 12, 12, 16));
        PLUGS.put(Direction.WEST, Block.box(0, 4, 4, 2.5, 12, 12));
        PLUGS.put(Direction.EAST, Block.box(13.5, 4, 4, 16, 12, 12));
        PLUGS.put(Direction.DOWN, Block.box(4, 0, 4, 12, 2.5, 12));
        PLUGS.put(Direction.UP, Block.box(4, 13.5, 4, 12, 16, 12));
    }
    private final Map<BlockState, VoxelShape> shapeCache = new HashMap<>();

    public NexusBusBlock(Properties props) {
        super(props);
        BlockState def = stateDefinition.any();
        for (EnumProperty<ConnectionType> p : SIDES.values()) def = def.setValue(p, ConnectionType.NONE);
        registerDefaultState(def);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    /** Есть ли у блока хоть что-то, с чем умеет работать шина. */
    public static boolean hasEndpoint(Level level, BlockPos other, Direction side) {
        return level.getCapability(Capabilities.Energy.BLOCK, other, side) != null
                || level.getCapability(Capabilities.Item.BLOCK, other, side) != null
                || level.getCapability(Capabilities.Fluid.BLOCK, other, side) != null;
    }

    private static ConnectionType connection(LevelReader level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (level.getBlockState(other).getBlock() instanceof NexusBusBlock) return ConnectionType.CABLE;
        if (level instanceof Level real && hasEndpoint(real, other, dir.getOpposite())) return ConnectionType.PLUG;
        return ConnectionType.NONE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(SIDES.get(dir), connection(ctx.getLevel(), ctx.getClickedPos(), dir));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(SIDES.get(dir), connection(level, pos, dir));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeCache.computeIfAbsent(state, s -> {
            VoxelShape shape = NODE;
            for (Direction dir : Direction.values()) {
                ConnectionType type = s.getValue(SIDES.get(dir));
                if (type.connected()) shape = Shapes.or(shape, ARMS.get(dir));
                if (type == ConnectionType.PLUG) shape = Shapes.or(shape, PLUGS.get(dir));
            }
            return shape;
        });
    }

    /** Сторона, ближайшая к точке клика. */
    private static Direction clickedSide(BlockPos pos, BlockHitResult hit) {
        Vec3 v = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) return v.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return v.y > 0 ? Direction.UP : Direction.DOWN;
        return v.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NexusBusBlockEntity bus) {
            Direction side = clickedSide(pos, hit);
            FaceMode mode = bus.cycleMode(side);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.ferronexus.face",
                        Component.translatable("direction.ferronexus." + side.getSerializedName()),
                        Component.translatable("facemode.ferronexus." + mode.id())), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NexusBusBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof NexusBusBlockEntity bus) bus.serverTick();
        };
    }
}
