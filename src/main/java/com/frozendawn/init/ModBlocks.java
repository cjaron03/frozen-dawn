package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.block.AlarmBeaconBlock;
import com.frozendawn.block.AcheronForgeBlock;
import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.block.BloomCoreBlock;
import com.frozendawn.block.BloomTipBlock;
import com.frozendawn.block.EmergencyLightBlock;
import com.frozendawn.block.FuelProcessingSiloControllerBlock;
import com.frozendawn.block.FrozenAtmosphereBlock;
import com.frozendawn.block.GeothermalCoreBlock;
import com.frozendawn.block.HearthBoundaryMarkerBlock;
import com.frozendawn.block.IcicleBlock;
import com.frozendawn.block.CampRadioBlock;
import com.frozendawn.block.LaunchPadBlock;
import com.frozendawn.block.MonitoringStationTerminalBlock;
import com.frozendawn.block.MiteAwayBlock;
import com.frozendawn.block.RemnantMembraneBlock;
import com.frozendawn.block.RemnantPropBlock;
import com.frozendawn.block.RemnantSeamBlock;
import com.frozendawn.block.OrsaFlagBlock;
import com.frozendawn.block.OrsaSupplyCrateBlock;
import com.frozendawn.block.PhaseBarometerBlock;
import com.frozendawn.block.RocketEngineBlock;
import com.frozendawn.block.ScorchedGroundBlock;
import com.frozendawn.block.SealedLatticeBlock;
import com.frozendawn.block.StillpointCoreBlock;
import com.frozendawn.block.AggregateResidueBlock;
import com.frozendawn.block.StreetLightBlock;
import com.frozendawn.block.SulfurCrustBlock;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.block.ThermalVentPoolBlock;
import com.frozendawn.block.TownPASpeakerBlock;
import com.frozendawn.block.TowerAntennaConsoleBlock;
import com.frozendawn.block.TransponderBlock;
import com.frozendawn.block.VentLavaBlock;
import com.frozendawn.block.VolcanicAshBlock;
import com.frozendawn.block.WallAlarmBeaconBlock;
import com.frozendawn.block.WallEmergencyLightBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FrozenDawn.MOD_ID);

    private static int heaterLight(net.minecraft.world.level.block.state.BlockState state, int maxLight) {
        if (!state.getValue(ThermalHeaterBlock.LIT)) return 0;
        int glowStage = state.getValue(ThermalHeaterBlock.GLOW_STAGE);
        return Math.max(1, Math.round(maxLight * ((glowStage + 1) / 5.0f)));
    }

    // Grass Block -> Dead Grass Block (phase 2+). Drops dirt.
    public static final DeferredBlock<Block> DEAD_GRASS_BLOCK = BLOCKS.register("dead_grass_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.5F)
                    .sound(SoundType.GRAVEL)));

    // Dirt -> Frozen Dirt (phase 4+). Drops dirt + ice shard. 1.5x hardness.
    public static final DeferredBlock<Block> FROZEN_DIRT = BLOCKS.register("frozen_dirt",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.75F) // 1.5x dirt's 0.5
                    .sound(SoundType.STONE)));

    // Sand -> Frozen Sand / permafrost (phase 3+). Drops sand. NOT gravity-affected.
    public static final DeferredBlock<Block> FROZEN_SAND = BLOCKS.register("frozen_sand",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.75F)
                    .sound(SoundType.STONE)));

    // Exposed cobblestone ruins frost over in phase 4+. More brittle than normal cobble.
    public static final DeferredBlock<Block> FROZEN_COBBLESTONE = BLOCKS.register("frozen_cobblestone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(1.7F, 6.0F)
                    .sound(SoundType.STONE)));

    // Exposed masonry follows after cobble to give ruins a colder silhouette.
    public static final DeferredBlock<Block> FROZEN_STONE_BRICKS = BLOCKS.register("frozen_stone_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(1.8F, 6.0F)
                    .sound(SoundType.STONE)));

    // Exposed plank structures glaze over before they start to splinter apart.
    public static final DeferredBlock<Block> FROZEN_PLANKS = BLOCKS.register("frozen_planks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1.5F, 4.0F)
                    .sound(SoundType.WOOD)));

    // Logs -> Dead Log (phase 3+). Drops 2-4 sticks. Grey, cracked.
    public static final DeferredBlock<RotatedPillarBlock> DEAD_LOG = BLOCKS.register("dead_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)));

    // Dead Log -> Frozen Log (phase 4+). Drops 1-2 sticks + ice shard. Ice-encased.
    public static final DeferredBlock<RotatedPillarBlock> FROZEN_LOG = BLOCKS.register("frozen_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(2.5F)
                    .sound(SoundType.GLASS)));

    // Leaves -> Dead Leaves (phase 2+). Drops nothing (rare stick). Brown.
    public static final DeferredBlock<Block> DEAD_LEAVES = BLOCKS.register("dead_leaves",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.1F)
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY)));

    // Dead Leaves -> Frozen Leaves (phase 4+). Drops ice shard. Shatters like glass.
    public static final DeferredBlock<Block> FROZEN_LEAVES = BLOCKS.register("frozen_leaves",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.1F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY)));

    // Hanging ice spike that forms under frozen overhangs in phase 4+.
    public static final DeferredBlock<IcicleBlock> ICICLE = BLOCKS.register("icicle",
            () -> new IcicleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.15F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY)));

    // Obsidian -> Frozen Obsidian (phase 4+). Used by late-game cold-core recipes.
    public static final DeferredBlock<Block> FROZEN_OBSIDIAN = BLOCKS.register("frozen_obsidian",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)));

    public static final DeferredBlock<ScorchedGroundBlock> SCORCHED_GROUND = BLOCKS.register("scorched_ground",
            () -> new ScorchedGroundBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.8F)
                    .sound(SoundType.CALCITE)
                    .lightLevel(state -> 4)));

    public static final DeferredBlock<SulfurCrustBlock> SULFUR_CRUST = BLOCKS.register("sulfur_crust",
            () -> new SulfurCrustBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(1.2F)
                    .sound(SoundType.CALCITE)));

    public static final DeferredBlock<Block> SULFUR_ORE = BLOCKS.register("sulfur_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .requiresCorrectToolForDrops()
                    .strength(2.1F, 6.0F)
                    .sound(SoundType.CALCITE)));

    public static final DeferredBlock<Block> HYDROTHERMAL_ROCK = BLOCKS.register("hydrothermal_rock",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)
                    .strength(1.8F)
                    .sound(SoundType.TUFF)));

    public static final DeferredBlock<VolcanicAshBlock> VOLCANIC_ASH = BLOCKS.register("volcanic_ash",
            () -> new VolcanicAshBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.15F)
                    .sound(SoundType.SAND)
                    .replaceable()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
                    .isSuffocating((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)));

    public static final DeferredBlock<ThermalVentPoolBlock> THERMAL_VENT_POOL = BLOCKS.register("thermal_vent_pool",
            () -> new ThermalVentPoolBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> 2 + state.getValue(ThermalVentPoolBlock.HEAT_STAGE) * 2)
                    .noLootTable()));

    public static final DeferredBlock<VentLavaBlock> VENT_LAVA = BLOCKS.register("vent_lava",
            () -> new VentLavaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.EMPTY)
                    .replaceable()
                    .noCollission()
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()));

    public static final DeferredBlock<OrsaSupplyCrateBlock> ORSA_SUPPLY_CRATE = BLOCKS.register("orsa_supply_crate",
            () -> new OrsaSupplyCrateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.5F)
                    .sound(SoundType.NETHERITE_BLOCK)));

    public static final DeferredBlock<CampRadioBlock> CAMP_RADIO = BLOCKS.register("camp_radio",
            () -> new CampRadioBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.5F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .lightLevel(state -> 3)));

    public static final DeferredBlock<OrsaFlagBlock> ORSA_FLAG = BLOCKS.register("orsa_flag",
            () -> new OrsaFlagBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .lightLevel(state -> 2)));

    public static final DeferredBlock<AlarmBeaconBlock> ALARM_BEACON = BLOCKS.register("alarm_beacon",
            () -> new AlarmBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 0)));

    public static final DeferredBlock<WallAlarmBeaconBlock> WALL_ALARM_BEACON = BLOCKS.register("wall_alarm_beacon",
            () -> new WallAlarmBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 0)));

    public static final DeferredBlock<EmergencyLightBlock> EMERGENCY_LIGHT = BLOCKS.register("emergency_light",
            () -> new EmergencyLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(EmergencyLightBlock::lightLevelForStage)));

    public static final DeferredBlock<WallEmergencyLightBlock> WALL_EMERGENCY_LIGHT = BLOCKS.register("wall_emergency_light",
            () -> new WallEmergencyLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(EmergencyLightBlock::lightLevelForStage)));

    public static final DeferredBlock<StreetLightBlock> STREET_LIGHT = BLOCKS.register("street_light",
            () -> new StreetLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(EmergencyLightBlock::lightLevelForStage)));

    public static final DeferredBlock<TownPASpeakerBlock> TOWN_PA_SPEAKER = BLOCKS.register("town_pa_speaker",
            () -> new TownPASpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(1.2F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<TowerAntennaConsoleBlock> TOWER_ANTENNA_CONSOLE = BLOCKS.register("tower_antenna_console",
            () -> new TowerAntennaConsoleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(state -> 6)));

    public static final DeferredBlock<MonitoringStationTerminalBlock> MONITORING_STATION_TERMINAL = BLOCKS.register("monitoring_station_terminal",
            () -> new MonitoringStationTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(state -> 4)));

    public static final DeferredBlock<PhaseBarometerBlock> PHASE_BAROMETER = BLOCKS.register("phase_barometer",
            () -> new PhaseBarometerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    // --- Player Agency blocks ---

    // Thermal Heater: right-click fuel, radius 7, +35C when lit
    public static final DeferredBlock<ThermalHeaterBlock> THERMAL_HEATER = BLOCKS.register("thermal_heater",
            () -> new ThermalHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> heaterLight(state, 13))));

    // Iron Thermal Heater: +50C, radius 9, 1.5x fuel consumption
    public static final DeferredBlock<ThermalHeaterBlock> IRON_THERMAL_HEATER = BLOCKS.register("iron_thermal_heater",
            () -> new ThermalHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> heaterLight(state, 13)), 1.5f));

    // Gold Thermal Heater: +65C, radius 11, 2x fuel consumption
    public static final DeferredBlock<ThermalHeaterBlock> GOLD_THERMAL_HEATER = BLOCKS.register("gold_thermal_heater",
            () -> new ThermalHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> heaterLight(state, 14)), 2.0f));

    // Diamond Thermal Heater: +80C, radius 14, 3x fuel consumption
    public static final DeferredBlock<ThermalHeaterBlock> DIAMOND_THERMAL_HEATER = BLOCKS.register("diamond_thermal_heater",
            () -> new ThermalHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> heaterLight(state, 15)), 3.0f));

    public static final DeferredBlock<MiteAwayBlock> MITEAWAY = BLOCKS.register("miteaway",
            () -> new MiteAwayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.6F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(MiteAwayBlock.LIT) ? 4 : 0)));

    // Insulated Glass: transparent, counts as shelter (roof check)
    public static final DeferredBlock<HalfTransparentBlock> INSULATED_GLASS = BLOCKS.register("insulated_glass",
            () -> new HalfTransparentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    // Frozen Coal Ore: coal ore that freezes in configurable phase, Y >= 0 only
    public static final DeferredBlock<Block> FROZEN_COAL_ORE = BLOCKS.register("frozen_coal_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F)
                    .sound(SoundType.STONE)));

    // Geothermal Core: endgame block, upgradeable warm zone + O2 production, light level 15
    public static final DeferredBlock<GeothermalCoreBlock> GEOTHERMAL_CORE = BLOCKS.register("geothermal_core",
            () -> new GeothermalCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)));

    public static final DeferredBlock<FuelProcessingSiloControllerBlock> FUEL_PROCESSING_SILO_CONTROLLER = BLOCKS.register("fuel_processing_silo_controller",
            () -> new FuelProcessingSiloControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(FuelProcessingSiloControllerBlock.LIT) ? 7 : 0)));

    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.register("launch_pad",
            () -> new LaunchPadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 9.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    public static final DeferredBlock<RocketEngineBlock> ROCKET_ENGINE = BLOCKS.register("rocket_engine",
            () -> new RocketEngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.5F, 12.0F)
                    .sound(SoundType.NETHERITE_BLOCK)));

    public static final DeferredBlock<Block> ROCKET_FIN = BLOCKS.register("rocket_fin",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 10.0F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> ROCKET_HULL = BLOCKS.register("rocket_hull",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 10.0F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> ROCKET_NOSE_CONE = BLOCKS.register("rocket_nose_cone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(4.8F, 10.0F)
                    .sound(SoundType.METAL)));

    // --- Acheronite ---

    // Acheronite Crystal: 4 growth stages, forms on frozen substrates in phase 5+
    public static final DeferredBlock<AcheroniteCrystalBlock> ACHERONITE_CRYSTAL = BLOCKS.register("acheronite_crystal",
            () -> new AcheroniteCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1.5F, 1.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .noOcclusion()
                    .lightLevel(state -> {
                        if (state.getValue(AcheroniteCrystalBlock.DARK)) {
                            return 0;
                        }
                        int base = switch (state.getValue(AcheroniteCrystalBlock.AGE)) {
                            case 0 -> 3;
                            case 1 -> 5;
                            case 2 -> 7;
                            default -> 10;
                        };
                        return state.getValue(AcheroniteCrystalBlock.BURIED) ? Math.min(12, base + 2) : base;
                    })
                    .pushReaction(PushReaction.DESTROY)));

    // Acheron Forge: processes shards into refined acheronite, requires Y<0 + heat
    public static final DeferredBlock<AcheronForgeBlock> ACHERON_FORGE = BLOCKS.register("acheron_forge",
            () -> new AcheronForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)));

    // Frozen Atmosphere: thin surface deposit, forms in phase 6 late, sublimates above -150C
    public static final DeferredBlock<FrozenAtmosphereBlock> FROZEN_ATMOSPHERE = BLOCKS.register("frozen_atmosphere",
            () -> new FrozenAtmosphereBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.3F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(FrozenAtmosphereBlock.DARK) ? 0 : 2)
                    .pushReaction(PushReaction.DESTROY)));

    // Architect Mask: placeable Architect head trophy
    public static final DeferredBlock<SkullBlock> ARCHITECT_MASK = BLOCKS.register("architect_mask",
            () -> new SkullBlock(
                    ModSkullTypes.ARCHITECT,
                    BlockBehaviour.Properties.of()
                            .instrument(NoteBlockInstrument.CUSTOM_HEAD)
                            .strength(1.0F)
                            .pushReaction(PushReaction.DESTROY)
            ));
    public static final DeferredBlock<WallSkullBlock> ARCHITECT_WALL_MASK = BLOCKS.register("architect_wall_mask",
            () -> new WallSkullBlock(
                    ModSkullTypes.ARCHITECT,
                    BlockBehaviour.Properties.of()
                            .strength(1.0F)
                            .dropsLike(ARCHITECT_MASK.get())
                            .pushReaction(PushReaction.DESTROY)
            ));

    // --- Win Condition ---

    // ORSA Transponder: endgame broadcast block, must be below Y=0 near geothermal core
    public static final DeferredBlock<TransponderBlock> TRANSPONDER = BLOCKS.register("transponder",
            () -> new TransponderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> switch (state.getValue(TransponderBlock.STATE)) {
                        case 1 -> 10; // BROADCASTING
                        case 2 -> 15; // COMPLETE
                        case 3 -> 7;  // PAUSED
                        default -> 0; // IDLE
                    })));

    // --- Frost Ward Torch ---

    // Frost Ward Torch: light 15, snowflake particles, Hollows flee at 16-block radius
    public static final DeferredBlock<TorchBlock> FROST_WARD_TORCH = BLOCKS.register("frost_ward_torch",
            () -> new TorchBlock(ParticleTypes.SNOWFLAKE, BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<WallTorchBlock> FROST_WARD_WALL_TORCH = BLOCKS.register("frost_ward_wall_torch",
            () -> new WallTorchBlock(ParticleTypes.SNOWFLAKE, BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)
                    .lootFrom(FROST_WARD_TORCH)));

    // Acheronite Block: decorative, passive warmth aura, counts as shelter
    public static final DeferredBlock<Block> ACHERONITE_BLOCK = BLOCKS.register("acheronite_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 5)));

    // Legacy migration marker retained so experimental Hearth saves can reconcile it away.
    public static final DeferredBlock<HearthBoundaryMarkerBlock> HEARTH_BOUNDARY_MARKER =
            BLOCKS.register("hearth_boundary_marker",
                    () -> new HearthBoundaryMarkerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .strength(3.0F, 8.0F)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 7)
                            .noCollission()
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .pushReaction(PushReaction.BLOCK)));

    // --- Post-Maeve Bloom ---
    public static final DeferredBlock<BloomMassBlock> BLOOM_MASS = BLOCKS.register("bloom_mass",
            () -> new BloomMassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .requiresCorrectToolForDrops()
                    .strength(2.2F, 4.0F)
                    .sound(SoundType.CALCITE)));

    public static final DeferredBlock<SnowLayerBlock> BLOOM_CRUST = BLOCKS.register("bloom_crust",
            () -> new SnowLayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(0.25F)
                    .sound(SoundType.MOSS)
                    .replaceable()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<BloomTipBlock> BLOOM_TIP = BLOCKS.register("bloom_tip",
            () -> new BloomTipBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(0.4F)
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<BloomCoreBlock> BLOOM_CORE = BLOCKS.register("bloom_core",
            () -> new BloomCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .requiresCorrectToolForDrops()
                    .strength(3.4F, 7.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 6)));

    // --- Aggregate scar and temporary encounter material ---
    public static final DeferredBlock<AggregateResidueBlock> AGGREGATE_RESIDUE = BLOCKS.register(
            "aggregate_residue", () -> new AggregateResidueBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(0.8F, 3.0F)
                    .sound(SoundType.BONE_BLOCK).noLootTable()));
    public static final DeferredBlock<Block> AGGREGATE_MASS = BLOCKS.register(
            "aggregate_mass", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops()
                    .strength(2.8F, 8.0F).sound(SoundType.BONE_BLOCK).noLootTable()));
    public static final DeferredBlock<RotatedPillarBlock> AGGREGATE_RIB = BLOCKS.register(
            "aggregate_rib", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops()
                    .strength(3.2F, 9.0F).sound(SoundType.BONE_BLOCK).noLootTable()));
    public static final DeferredBlock<Block> AGGREGATE_TEMPORARY_MASS = BLOCKS.register(
            "aggregate_temporary_mass", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(1.8F, 5.0F)
                    .sound(SoundType.BONE_BLOCK).noLootTable()));
    public static final DeferredBlock<StillpointCoreBlock> INERT_CONVERGENCE_CORE =
            BLOCKS.register("inert_convergence_core", () -> new StillpointCoreBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                            .requiresCorrectToolForDrops().strength(5.0F, 1_200.0F)
                            .lightLevel(state -> 5).sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<Block> INERT_ACHERONITE = BLOCKS.register("inert_acheronite",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<SealedLatticeBlock> SEALED_LATTICE =
            BLOCKS.register("sealed_lattice",
                    () -> new SealedLatticeBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .requiresCorrectToolForDrops()
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.METAL)));

    public static final DeferredBlock<RemnantMembraneBlock> REMNANT_MEMBRANE =
            BLOCKS.register("remnant_membrane", () -> new RemnantMembraneBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                            .strength(3.0F, 8.0F).sound(SoundType.SCULK)
                            .noLootTable().noOcclusion()));
    public static final DeferredBlock<RemnantSeamBlock> REMNANT_SEAM =
            BLOCKS.register("remnant_seam", () -> new RemnantSeamBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                            .strength(2.0F, 6.0F).sound(SoundType.WOOD)
                            .lightLevel(state -> 4).noLootTable()));
    public static final DeferredBlock<RemnantPropBlock> REMNANT_PROP =
            BLOCKS.register("remnant_prop", () -> new RemnantPropBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                            .strength(2.5F, 6.0F).sound(SoundType.METAL)
                            .lightLevel(state -> state.getValue(RemnantPropBlock.LIT) ? 5 : 0)
                            .noLootTable()));
}
