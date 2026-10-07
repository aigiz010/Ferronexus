package com.aigiz010.ferronexus.registry;

import com.aigiz010.ferronexus.Ferronexus;
import com.aigiz010.ferronexus.conduit.ConduitBundleBlock;
import com.aigiz010.ferronexus.machine.CoalGeneratorBlock;
import com.aigiz010.ferronexus.machine.EnergyCellBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FNBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ferronexus.MOD_ID);

    /** Блок-носитель до 16 разных проводов/труб. Ставится предметами проводов. */
    public static final DeferredBlock<ConduitBundleBlock> CONDUIT_BUNDLE = BLOCKS.register("conduit_bundle",
            id -> new ConduitBundleBlock(cableProps().setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.register("coal_generator",
            id -> new CoalGeneratorBlock(machineProps()
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 13 : 0)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));
    public static final DeferredBlock<EnergyCellBlock> ENERGY_CELL = BLOCKS.register("energy_cell",
            id -> new EnergyCellBlock(machineProps().setId(ResourceKey.create(Registries.BLOCK, id))));

    private static BlockBehaviour.Properties cableProps() {
        return BlockBehaviour.Properties.of().strength(0.4f).sound(SoundType.WOOL).noOcclusion();
    }

    private static BlockBehaviour.Properties machineProps() {
        return BlockBehaviour.Properties.of().strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private FNBlocks() {}
}
