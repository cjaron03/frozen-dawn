package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeBlockEntity;
import com.frozendawn.block.AlarmBeaconBlockEntity;
import com.frozendawn.block.CampRadioBlockEntity;
import com.frozendawn.block.EmergencyLightBlockEntity;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.MonitoringStationTerminalBlockEntity;
import com.frozendawn.block.OrsaFlagBlockEntity;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.TowerAntennaConsoleBlockEntity;
import com.frozendawn.block.TransponderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransponderBlockEntity>> TRANSPONDER =
            BLOCK_ENTITIES.register("transponder",
                    () -> BlockEntityType.Builder.of(TransponderBlockEntity::new,
                            ModBlocks.TRANSPONDER.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampRadioBlockEntity>> CAMP_RADIO =
            BLOCK_ENTITIES.register("camp_radio",
                    () -> BlockEntityType.Builder.of(CampRadioBlockEntity::new,
                            ModBlocks.CAMP_RADIO.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TowerAntennaConsoleBlockEntity>> TOWER_ANTENNA_CONSOLE =
            BLOCK_ENTITIES.register("tower_antenna_console",
                    () -> BlockEntityType.Builder.of(TowerAntennaConsoleBlockEntity::new,
                            ModBlocks.TOWER_ANTENNA_CONSOLE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitoringStationTerminalBlockEntity>> MONITORING_STATION_TERMINAL =
            BLOCK_ENTITIES.register("monitoring_station_terminal",
                    () -> BlockEntityType.Builder.of(MonitoringStationTerminalBlockEntity::new,
                            ModBlocks.MONITORING_STATION_TERMINAL.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrsaFlagBlockEntity>> ORSA_FLAG =
            BLOCK_ENTITIES.register("orsa_flag",
                    () -> BlockEntityType.Builder.of(OrsaFlagBlockEntity::new,
                            ModBlocks.ORSA_FLAG.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBeaconBlockEntity>> ALARM_BEACON =
            BLOCK_ENTITIES.register("alarm_beacon",
                    () -> BlockEntityType.Builder.of(AlarmBeaconBlockEntity::new,
                            ModBlocks.ALARM_BEACON.get(),
                            ModBlocks.WALL_ALARM_BEACON.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EmergencyLightBlockEntity>> EMERGENCY_LIGHT =
            BLOCK_ENTITIES.register("emergency_light",
                    () -> BlockEntityType.Builder.of(EmergencyLightBlockEntity::new,
                            ModBlocks.EMERGENCY_LIGHT.get(),
                            ModBlocks.WALL_EMERGENCY_LIGHT.get(),
                            ModBlocks.STREET_LIGHT.get()
                    ).build(null));

    @SubscribeEvent
    public static void onBlockEntityValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SKULL, ModBlocks.ARCHITECT_MASK.get(), ModBlocks.ARCHITECT_WALL_MASK.get());
        event.modify(BlockEntityType.BARREL, ModBlocks.ORSA_SUPPLY_CRATE.get());
    }
}
