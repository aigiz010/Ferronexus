package com.aigiz010.ferronexus.conduit;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.FNEnergyStorage;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Блок, в котором стоят до 16 РАЗНЫХ проводов/труб. Каждый тип работает независимо:
 * соединяется только с таким же типом в соседних блоках и с подходящими машинами.
 */
public class ConduitBundleBlockEntity extends BlockEntity {
    public static final int PERIOD = 10;
    public static final int MAX_NETWORK = 1024;
    public static final int MAX_REDSTONE_NETWORK = 256;
    private static final int N = ConduitType.MAX_PER_BLOCK;

    private record Endpoint(BlockPos pos, Direction side) {}

    private int types;
    private final int[] conn = new int[N];   // стороны с активным соединением
    private final int[] plugs = new int[N];  // из них — разъём к машине/инвентарю
    private final int[] stubs = new int[N];  // отключённые стороны (показываем заглушку)
    private final FaceMode[][] modes = new FaceMode[N][6];
    private final FNEnergyStorage[] buffers = new FNEnergyStorage[N];
    private int signal;
    private long redstoneTick = -1;
    private boolean refresh = true;
    private int cursor;
    private VoxelShape shape;

    public ConduitBundleBlockEntity(BlockPos pos, BlockState state) {
        super(FNBlockEntities.CONDUIT_BUNDLE.get(), pos, state);
        for (ConduitType t : ConduitType.values()) resetModes(t);
    }

    // ---------- Состав ----------
    public int typesMask() { return types; }

    public boolean has(ConduitType t) { return (types & t.bit()) != 0; }

    public int count() { return Integer.bitCount(types); }

    public List<ConduitType> list() {
        List<ConduitType> out = new ArrayList<>();
        for (ConduitType t : ConduitType.values()) if (has(t)) out.add(t);
        return out;
    }

    private void resetModes(ConduitType t) {
        for (int d = 0; d < 6; d++) modes[t.ordinal()][d] = t.defaultMode();
    }

    /** Добавить тип. false — если такой уже есть или блок заполнен. */
    public boolean add(ConduitType t) {
        if (has(t) || count() >= ConduitType.MAX_PER_BLOCK) return false;
        types |= t.bit();
        resetModes(t);
        changedStructure();
        return true;
    }

    public boolean remove(ConduitType t) {
        if (!has(t)) return false;
        types &= ~t.bit();
        int i = t.ordinal();
        conn[i] = plugs[i] = stubs[i] = 0;
        buffers[i] = null;
        if (t.kind() == ConduitKind.REDSTONE) signal = 0;
        changedStructure();
        return true;
    }

    private void changedStructure() {
        refresh = true;
        shape = null;
        if (level != null && !level.isClientSide()) {
            refreshConnections(true);
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBundleBlockEntity o) o.markRefresh();
            }
            level.invalidateCapabilities(worldPosition);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void markRefresh() { refresh = true; }

    // ---------- Стороны ----------
    private static int bit(Direction d) { return 1 << d.ordinal(); }

    public boolean isConnected(ConduitType t, Direction d) { return (conn[t.ordinal()] & bit(d)) != 0; }

    public boolean isPlug(ConduitType t, Direction d) { return (plugs[t.ordinal()] & bit(d)) != 0; }

    public boolean isStub(ConduitType t, Direction d) { return (stubs[t.ordinal()] & bit(d)) != 0; }

    public FaceMode mode(ConduitType t, Direction d) { return modes[t.ordinal()][d.ordinal()]; }

    /** Сменить режим стороны. Связь с соседним блоком проводов — только вкл/выкл. */
    public FaceMode cycleMode(ConduitType t, Direction d) {
        FaceMode cur = mode(t, d);
        boolean link = level != null && level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBundleBlockEntity;
        FaceMode next;
        if (link) next = cur == FaceMode.DISABLED ? t.defaultMode() : FaceMode.DISABLED;
        else next = cur.next();
        modes[t.ordinal()][d.ordinal()] = next;
        changedStructure();
        return next;
    }

    /** Какой провод и какая сторона под точкой (локальные координаты 0..16). */
    public Pick pick(double x, double y, double z) {
        double[] h = ConduitShapes.housing(types);
        for (ConduitType t : list()) {
            for (Direction d : Direction.values()) {
                boolean hit = (isPlug(t, d) || isStub(t, d)) && ConduitShapes.contains(ConduitShapes.plug(t, d), x, y, z);
                hit = hit || isConnected(t, d) && ConduitShapes.contains(ConduitShapes.arm(t, d, h), x, y, z);
                if (hit) return new Pick(t, d);
            }
        }
        return null;
    }

    public record Pick(ConduitType type, Direction side) {}

    public VoxelShape shape() {
        if (shape == null) shape = ConduitShapes.build(this);
        return shape;
    }

    // ---------- Энергия ----------
    private FNEnergyStorage buffer(ConduitType t) {
        int i = t.ordinal();
        if (buffers[i] == null) buffers[i] = new FNEnergyStorage(t.rate() * 4, t.rate(), t.rate(), this::setChanged);
        return buffers[i];
    }

    /** Энергия стороны: буфер самого сильного включённого на этой стороне провода. */
    public EnergyHandler getEnergy(Direction side) {
        ConduitType[] v = ConduitType.values();
        for (int i = v.length - 1; i >= 0; i--) {
            ConduitType t = v[i];
            if (t.kind() != ConduitKind.ENERGY || !has(t)) continue;
            if (side != null && !mode(t, side).isInput()) continue;
            return buffer(t);
        }
        return null;
    }

    // ---------- Редстоун ----------
    public int signalTowards(Direction sideOfThisBlock) {
        for (ConduitType t : list()) {
            if (t.kind() != ConduitKind.REDSTONE) continue;
            if (isPlug(t, sideOfThisBlock) && mode(t, sideOfThisBlock).isOutput()) return signal;
        }
        return 0;
    }

    private static boolean redstoneTarget(BlockState s) {
        if (s.isAir()) return false;
        if (s.isSignalSource()) return true;
        var b = s.getBlock();
        return b instanceof RedstoneLampBlock || b instanceof PistonBaseBlock || b instanceof DoorBlock
                || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof DispenserBlock
                || b instanceof NoteBlock || b instanceof TntBlock || b instanceof PoweredRailBlock
                || b instanceof BellBlock || s.is(Blocks.REDSTONE_WIRE);
    }

    // ---------- Подключения ----------
    private boolean endpoint(ConduitType t, Direction dir) {
        BlockPos np = worldPosition.relative(dir);
        Direction side = dir.getOpposite();
        return switch (t.kind()) {
            case ENERGY -> level.getCapability(Capabilities.Energy.BLOCK, np, side) != null;
            case ITEM -> level.getCapability(Capabilities.Item.BLOCK, np, side) != null;
            case FLUID, GAS -> level.getCapability(Capabilities.Fluid.BLOCK, np, side) != null;
            case REDSTONE -> redstoneTarget(level.getBlockState(np));
        };
    }

    private void refreshConnections(boolean force) {
        boolean changed = force;
        for (ConduitType t : ConduitType.values()) {
            int i = t.ordinal();
            int c = 0, p = 0, s = 0;
            if (has(t)) {
                for (Direction d : Direction.values()) {
                    boolean possible, plug = false;
                    BlockEntity nb = level.getBlockEntity(worldPosition.relative(d));
                    if (nb instanceof ConduitBundleBlockEntity o) {
                        possible = o.has(t) && o.mode(t, d.getOpposite()) != FaceMode.DISABLED;
                    } else {
                        possible = endpoint(t, d);
                        plug = possible;
                    }
                    if (!possible) continue;
                    if (mode(t, d) == FaceMode.DISABLED) { s |= bit(d); continue; }
                    c |= bit(d);
                    if (plug) p |= bit(d);
                }
            }
            if (c != conn[i] || p != plugs[i] || s != stubs[i]) {
                conn[i] = c; plugs[i] = p; stubs[i] = s;
                changed = true;
            }
        }
        refresh = false;
        if (changed) {
            shape = null;
            setChanged();
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }

    // ---------- Тик ----------
    void serverTick() {
        if (level == null || types == 0) return;
        if (refresh || (level.getGameTime() + (worldPosition.asLong() & 15)) % 20 == 0) refreshConnections(false);
        boolean op = (level.getGameTime() + (worldPosition.asLong() & 7)) % PERIOD == 0;
        for (ConduitType t : list()) {
            switch (t.kind()) {
                case ENERGY -> energyTick(t);
                case ITEM -> { if (op) moveTick(t); }
                case FLUID, GAS -> { if (op) moveTick(t); }
                case REDSTONE -> { if (level.getGameTime() % 2 == 0) redstoneTick(t); }
            }
        }
        if (op) cursor++;
    }

    private void energyTick(ConduitType t) {
        FNEnergyStorage buf = buffer(t);
        if (buf.getEnergyStored() <= 0) return;
        int budget = t.rate();
        for (Direction d : Direction.values()) {
            if (budget <= 0) return;
            if (isPlug(t, d) && mode(t, d).isOutput()) budget -= EnergyHelper.pushTo(level, worldPosition, d, buf, budget);
        }
        for (Direction d : Direction.values()) {
            if (budget <= 0) return;
            if (!isConnected(t, d) || isPlug(t, d)) continue;
            if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBundleBlockEntity o) || !o.has(t)) continue;
            FNEnergyStorage ob = o.buffer(t);
            int diff = buf.getEnergyStored() - ob.getEnergyStored();
            if (diff <= 1) continue;
            int amount = Math.min(budget, Math.min(diff / 2, ob.freeSpace()));
            if (amount <= 0) continue;
            ob.receiveInternal(buf.extractInternal(amount));
            budget -= amount;
        }
    }

    /** Все выходы сети этого типа (обход по соединённым блокам). */
    private List<Endpoint> collectOutputs(ConduitType t) {
        List<Endpoint> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        seen.add(worldPosition);
        while (!queue.isEmpty() && seen.size() <= MAX_NETWORK) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity b) || !b.has(t)) continue;
            for (Direction d : Direction.values()) {
                if (!b.isConnected(t, d)) continue;
                BlockPos next = pos.relative(d);
                if (b.isPlug(t, d)) {
                    if (b.mode(t, d).isOutput()) result.add(new Endpoint(next, d.getOpposite()));
                } else if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    private static boolean isGas(FluidResource r) {
        return r.toStack(1).getFluid().getFluidType().isLighterThanAir();
    }

    private void moveTick(ConduitType t) {
        List<Endpoint> outputs = null;
        for (Direction d : Direction.values()) {
            if (!isPlug(t, d) || !mode(t, d).isInput()) continue;
            if (outputs == null) outputs = collectOutputs(t);
            if (outputs.isEmpty()) return;
            BlockPos srcPos = worldPosition.relative(d);
            if (t.kind() == ConduitKind.ITEM) moveItems(t, srcPos, d.getOpposite(), outputs);
            else moveFluids(t, srcPos, d.getOpposite(), outputs);
        }
    }

    private void moveItems(ConduitType t, BlockPos srcPos, Direction srcSide, List<Endpoint> outputs) {
        ResourceHandler<ItemResource> src = level.getCapability(Capabilities.Item.BLOCK, srcPos, srcSide);
        if (src == null) return;
        int budget = t.rate();
        int n = outputs.size();
        for (int k = 0; k < n && budget > 0; k++) {
            Endpoint e = outputs.get(Math.floorMod(cursor + k, n));
            if (e.pos().equals(srcPos)) continue;
            ResourceHandler<ItemResource> dst = level.getCapability(Capabilities.Item.BLOCK, e.pos(), e.side());
            if (dst == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.moveStacking(src, dst, (ItemResource r) -> true, budget, tx);
                tx.commit();
                budget -= moved;
            }
        }
    }

    private void moveFluids(ConduitType t, BlockPos srcPos, Direction srcSide, List<Endpoint> outputs) {
        ResourceHandler<FluidResource> src = level.getCapability(Capabilities.Fluid.BLOCK, srcPos, srcSide);
        if (src == null) return;
        boolean gas = t.kind() == ConduitKind.GAS;
        int budget = t.rate();
        int n = outputs.size();
        for (int k = 0; k < n && budget > 0; k++) {
            Endpoint e = outputs.get(Math.floorMod(cursor + k, n));
            if (e.pos().equals(srcPos)) continue;
            ResourceHandler<FluidResource> dst = level.getCapability(Capabilities.Fluid.BLOCK, e.pos(), e.side());
            if (dst == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.move(src, dst, (FluidResource r) -> gas == isGas(r), budget, tx);
                tx.commit();
                budget -= moved;
            }
        }
    }

    /** Сигнал сети = максимум со всех входов, без затухания. */
    private void redstoneTick(ConduitType t) {
        long now = level.getGameTime();
        if (redstoneTick == now) return;
        List<ConduitBundleBlockEntity> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        seen.add(worldPosition);
        int input = 0;
        while (!queue.isEmpty() && members.size() < MAX_REDSTONE_NETWORK) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity b) || !b.has(t)) continue;
            members.add(b);
            b.redstoneTick = now;
            for (Direction d : Direction.values()) {
                if (!b.isConnected(t, d)) continue;
                BlockPos next = pos.relative(d);
                if (b.isPlug(t, d)) {
                    if (!b.mode(t, d).isInput()) continue;
                    if (level.getBlockState(next).is(Blocks.REDSTONE_WIRE)) continue; // без петель через пыль
                    input = Math.max(input, level.getSignal(next, d));
                } else if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (ConduitBundleBlockEntity b : members) {
            if (b.signal != input) {
                b.signal = input;
                b.setChanged();
                level.updateNeighborsAt(b.worldPosition, b.getBlockState().getBlock());
            }
        }
    }

    // ---------- Сохранение и синхронизация ----------
    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Types", types);
        out.putInt("Signal", signal);
        for (ConduitType t : list()) {
            int i = t.ordinal();
            int m = 0;
            for (int d = 0; d < 6; d++) m |= modes[i][d].ordinal() << (d * 2);
            out.putInt("M" + i, m);
            out.putInt("C" + i, conn[i]);
            out.putInt("P" + i, plugs[i]);
            out.putInt("S" + i, stubs[i]);
            if (buffers[i] != null) out.putInt("E" + i, buffers[i].getEnergyStored());
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        types = in.getIntOr("Types", 0);
        signal = in.getIntOr("Signal", 0);
        for (ConduitType t : ConduitType.values()) {
            int i = t.ordinal();
            if (!has(t)) { conn[i] = plugs[i] = stubs[i] = 0; continue; }
            int m = in.getIntOr("M" + i, -1);
            if (m < 0) resetModes(t);
            else for (int d = 0; d < 6; d++) modes[i][d] = FaceMode.byOrdinal((m >> (d * 2)) & 3);
            conn[i] = in.getIntOr("C" + i, 0);
            plugs[i] = in.getIntOr("P" + i, 0);
            stubs[i] = in.getIntOr("S" + i, 0);
            int e = in.getIntOr("E" + i, 0);
            if (t.kind() == ConduitKind.ENERGY) buffer(t).setEnergy(e);
        }
        shape = null;
        refresh = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
