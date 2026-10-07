package com.aigiz010.ferronexus.registry;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.cable.CableBlockEntity;
import com.aigiz010.ferronexus.machine.CoalGeneratorBlockEntity;
import com.aigiz010.ferronexus.machine.EnergyCellBlockEntity;
import com.aigiz010.ferronexus.nexus.NexusBusBlockEntity;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FNBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ferronexus.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            TYPES.register("cable", () -> new BlockEntityType<>(CableBlockEntity::new,
                    Set.of(FNBlocks.LV_CABLE.get(), FNBlocks.MV_CABLE.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusBusBlockEntity>> NEXUS_BUS =
            TYPES.register("nexus_bus", () -> new BlockEntityType<>(NexusBusBlockEntity::new,
                    Set.of(FNBlocks.NEXUS_BUS.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR =
            TYPES.register("coal_generator", () -> new BlockEntityType<>(CoalGeneratorBlockEntity::new,
                    Set.of(FNBlocks.COAL_GENERATOR.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL =
            TYPES.register("energy_cell", () -> new BlockEntityType<>(EnergyCellBlockEntity::new,
                    Set.of(FNBlocks.ENERGY_CELL.get())));

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CABLE.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, NEXUS_BUS.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, COAL_GENERATOR.get(), (be, side) -> be.getEnergy(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ENERGY_CELL.get(), (be, side) -> be.getEnergy(side));
    }

    private FNBlockEntities() {}
}
