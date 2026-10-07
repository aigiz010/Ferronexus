package com.aigiz010.ferronexus.nexus;

import com.aigiz010.ferronexus.energy.EnergyHelper;
import com.aigiz010.ferronexus.energy.FNEnergyStorage;
import com.aigiz010.ferronexus.registry.FNBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Логика шины. Каждые {@link #PERIOD} тиков шина забирает предметы и жидкости
 * из соседей на сторонах «вход» и раздаёт их по кругу всем сторонам «выход»
 * во всей связанной сети. Энергия идёт как по проводу.
 */
public class NexusBusBlockEntity extends BlockEntity {
    public static final int ENERGY_RATE = 512;
    public static final int ITEMS_PER_OP = 16;
    public static final int FLUID_PER_OP = 500;
    public static final int PERIOD = 10;
    public static final int MAX_NETWORK = 1024;

    private record Endpoint(BlockPos pos, Direction side) {}

    private final FaceMode[] modes = new FaceMode[6];
    private final FNEnergyStorage buffer;
    private int cursor;

    public NexusBusBlockEntity(BlockPos pos, BlockState state) {
        super(FNBlockEntities.NEXUS_BUS.get(), pos, state);
        Arrays.fill(modes, FaceMode.OUTPUT);
        this.buffer = new FNEnergyStorage(ENERGY_RATE * 4, ENERGY_RATE, ENERGY_RATE, this::setChanged);
    }

    public EnergyHandler getEnergy(Direction side) {
        if (side != null && modes[side.ordinal()] == FaceMode.DISABLED) return null;
        return buffer;
    }

    public FaceMode mode(Direction side) { return modes[side.ordinal()]; }

    public FaceMode cycleMode(Direction side) {
        FaceMode next = modes[side.ordinal()].next();
        modes[side.ordinal()] = next;
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
        return next;
    }

    private boolean isBus(Direction dir) {
        return level.getBlockEntity(worldPosition.relative(dir)) instanceof NexusBusBlockEntity;
    }

    void serverTick() {
        if (level == null) return;
        energyTick();
        if ((level.getGameTime() + (worldPosition.asLong() & 7)) % PERIOD != 0) return;
        List<Direction> inputs = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (modes[dir.ordinal()].isInput() && !isBus(dir)) inputs.add(dir);
        }
        if (inputs.isEmpty()) return;
        List<Endpoint> outputs = collectOutputs();
        if (outputs.isEmpty()) return;
        for (Direction dir : inputs) {
            moveItems(dir, outputs);
            moveFluids(dir, outputs);
        }
        cursor++;
    }

    // ---------- Энергия ----------
    private void energyTick() {
        if (buffer.getEnergyStored() <= 0) return;
        int budget = ENERGY_RATE;
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            if (!modes[dir.ordinal()].isOutput() || isBus(dir)) continue;
            budget -= EnergyHelper.pushTo(level, worldPosition, dir, buffer, budget);
        }
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof NexusBusBlockEntity other)) continue;
            int diff = buffer.getEnergyStored() - other.buffer.getEnergyStored();
            if (diff <= 1) continue;
            int amount = Math.min(budget, Math.min(diff / 2, other.buffer.freeSpace()));
            if (amount <= 0) continue;
            other.buffer.receiveInternal(buffer.extractInternal(amount));
            budget -= amount;
        }
    }

    // ---------- Сеть ----------
    private List<Endpoint> collectOutputs() {
        List<Endpoint> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        seen.add(worldPosition);
        while (!queue.isEmpty() && seen.size() <= MAX_NETWORK) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof NexusBusBlockEntity bus)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (level.getBlockEntity(next) instanceof NexusBusBlockEntity) {
                    if (seen.add(next)) queue.add(next);
                } else if (bus.modes[dir.ordinal()].isOutput()) {
                    result.add(new Endpoint(next, dir.getOpposite()));
                }
            }
        }
        return result;
    }

    // ---------- Предметы ----------
    private void moveItems(Direction dir, List<Endpoint> outputs) {
        BlockPos srcPos = worldPosition.relative(dir);
        ResourceHandler<ItemResource> src = level.getCapability(Capabilities.Item.BLOCK, srcPos, dir.getOpposite());
        if (src == null) return;
        int budget = ITEMS_PER_OP;
        int n = outputs.size();
        for (int k = 0; k < n && budget > 0; k++) {
            Endpoint e = outputs.get(Math.floorMod(cursor + k, n));
            if (e.pos().equals(srcPos)) continue;
            ResourceHandler<ItemResource> dst = level.getCapability(Capabilities.Item.BLOCK, e.pos(), e.side());
            if (dst == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.moveStacking(src, dst, r -> true, budget, tx);
                tx.commit();
                budget -= moved;
            }
        }
    }

    // ---------- Жидкости ----------
    private void moveFluids(Direction dir, List<Endpoint> outputs) {
        BlockPos srcPos = worldPosition.relative(dir);
        ResourceHandler<FluidResource> src = level.getCapability(Capabilities.Fluid.BLOCK, srcPos, dir.getOpposite());
        if (src == null) return;
        int budget = FLUID_PER_OP;
        int n = outputs.size();
        for (int k = 0; k < n && budget > 0; k++) {
            Endpoint e = outputs.get(Math.floorMod(cursor + k, n));
            if (e.pos().equals(srcPos)) continue;
            ResourceHandler<FluidResource> dst = level.getCapability(Capabilities.Fluid.BLOCK, e.pos(), e.side());
            if (dst == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int moved = ResourceHandlerUtil.move(src, dst, r -> true, budget, tx);
                tx.commit();
                budget -= moved;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Energy", buffer.getEnergyStored());
        for (int i = 0; i < 6; i++) out.putInt("Mode" + i, modes[i].ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        buffer.setEnergy(in.getIntOr("Energy", 0));
        for (int i = 0; i < 6; i++) modes[i] = FaceMode.byOrdinal(in.getIntOr("Mode" + i, 0));
    }
}
