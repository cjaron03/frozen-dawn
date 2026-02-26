package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeBlockEntity;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalHeaterBlockEntity>> THERMAL_HEATER =
            BLOCK_ENTITIES.register("thermal_heater",
                    () -> BlockEntityType.Builder.of(ThermalHeaterBlockEntity::new,
                            ModBlocks.THERMAL_HEATER.get(),
                            ModBlocks.IRON_THERMAL_HEATER.get(),
                            ModBlocks.GOLD_THERMAL_HEATER.get(),
                            ModBlocks.DIAMOND_THERMAL_HEATER.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeothermalCoreBlockEntity>> GEOTHERMAL_CORE =
            BLOCK_ENTITIES.register("geothermal_core",
                    () -> BlockEntityType.Builder.of(GeothermalCoreBlockEntity::new,
                            ModBlocks.GEOTHERMAL_CORE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcheronForgeBlockEntity>> ACHERON_FORGE =
            BLOCK_ENTITIES.register("acheron_forge",
                    () -> BlockEntityType.Builder.of(AcheronForgeBlockEntity::new,
                            ModBlocks.ACHERON_FORGE.get()
                    ).build(null));
}
