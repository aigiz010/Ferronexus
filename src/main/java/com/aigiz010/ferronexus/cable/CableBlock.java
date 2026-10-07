package com.aigiz010.ferronexus.cable;

import com.aigiz010.ferronexus.energy.VoltageTier;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Тонкий провод RF: узел в центре блока, трубки 3/16 к соседям
 * и плоский разъём там, где провод упирается в машину.
 */
public class CableBlock extends Block implements EntityBlock {
    public static final Map<Direction, EnumProperty<ConnectionType>> SIDES = new EnumMap<>(Direction.class);
    static {
        for (Direction dir : Direction.values()) {
            SIDES.put(dir, EnumProperty.create(dir.getSerializedName(), ConnectionType.class));
        }
    }

    private static final VoxelShape NODE = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> PLUGS = new EnumMap<>(Direction.class);
    static {
        double a = 6.5, b = 9.5, n = 5.5, f = 10.5;
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

    private final VoltageTier tier;

    public CableBlock(VoltageTier tier, Properties props) {
        super(props);
        this.tier = tier;
        BlockState def = stateDefinition.any();
        for (EnumProperty<ConnectionType> p : SIDES.values()) def = def.setValue(p, ConnectionType.NONE);
        registerDefaultState(def);
    }

    public VoltageTier tier() { return tier; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    private static ConnectionType connection(LevelReader level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        if (level.getBlockState(other).getBlock() instanceof CableBlock) return ConnectionType.CABLE;
        if (level instanceof Level real
                && real.getCapability(Capabilities.EnergyStorage.BLOCK, other, dir.getOpposite()) != null) {
            return ConnectionType.PLUG;
        }
        return ConnectionType.NONE;
    }

    public static boolean isConnected(BlockState state, Direction dir) {
        return state.getValue(SIDES.get(dir)).connected();
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof CableBlockEntity cable) cable.serverTick();
        };
    }
}
