package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.StarterBooks;
import com.frozendawn.item.AcheroniteCompassItem;
import com.frozendawn.item.AcheronitePickaxeItem;
import com.frozendawn.item.AcheroniteShardItem;
import com.frozendawn.item.AcheroniteShovelItem;
import com.frozendawn.item.ArchitectMaskItem;
import com.frozendawn.item.AcheroniteSwordItem;
import com.frozendawn.item.ArchitectSoulItem;
import com.frozendawn.item.BoardPacketItem;
import com.frozendawn.item.BlackglassArchiveCardItem;
import com.frozendawn.item.BlizzardGogglesItem;
import com.frozendawn.item.ComfortItem;
import com.frozendawn.item.FrozenAtmosphereShardItem;
import com.frozendawn.item.FrozenMeatItem;
import com.frozendawn.item.EmergencyO2CartridgeItem;
import com.frozendawn.item.IceClawsItem;
import com.frozendawn.item.InertConvergenceCoreItem;
import com.frozendawn.item.LinedEvaChestplateItem;
import com.frozendawn.item.LastWitnessItem;
import com.frozendawn.item.MiteAwayBlockItem;
import com.frozendawn.item.MeteorologistJournalItem;
import com.frozendawn.item.MartianCommandPacketItem;
import com.frozendawn.item.SoulHarvestBladeItem;
import com.frozendawn.item.OrsaDocumentItem;
import com.frozendawn.item.OrsaIdBadgeItem;
import com.frozendawn.item.OrsaThermalVisorItem;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.item.O2EfficiencyModuleItem;
import com.frozendawn.item.OrsaTraumaKitItem;
import com.frozendawn.item.OrsaMultiToolItem;
import com.frozendawn.item.MirroredFragmentItem;
import com.frozendawn.item.RemnantEmberItem;
import com.frozendawn.item.SnowshoesItem;
import com.frozendawn.item.SurveyorLensItem;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.item.SuitPatchItem;
import com.frozendawn.item.ThermalContainerItem;
import com.frozendawn.item.RationWarmerItem;
import com.frozendawn.item.ThaevenCarrierItem;
import com.frozendawn.item.ThaevenTranslatorItem;
import com.frozendawn.lore.ThaevenRecordId;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrozenDawn.MOD_ID);

    // ORSA CFU-7 Hearthstone Reactor
    public static final DeferredItem<BlockItem> REACTOR_CASING = ITEMS.registerSimpleBlockItem("reactor_casing", ModBlocks.REACTOR_CASING);
    public static final DeferredItem<BlockItem> REACTOR_CORE = ITEMS.registerSimpleBlockItem("reactor_core", ModBlocks.REACTOR_CORE);
    public static final DeferredItem<BlockItem> CONTROL_ROD_DRIVE = ITEMS.registerSimpleBlockItem("control_rod_drive", ModBlocks.CONTROL_ROD_DRIVE);
    public static final DeferredItem<BlockItem> COOLANT_PORT = ITEMS.registerSimpleBlockItem("coolant_port", ModBlocks.COOLANT_PORT);
    public static final DeferredItem<BlockItem> REACTOR_CONTROLLER = ITEMS.registerSimpleBlockItem("reactor_controller", ModBlocks.REACTOR_CONTROLLER);
    public static final DeferredItem<BlockItem> REACTOR_RADIATOR = ITEMS.registerSimpleBlockItem("reactor_radiator", ModBlocks.REACTOR_RADIATOR);
    public static final DeferredItem<BlockItem> CORIUM = ITEMS.registerSimpleBlockItem("corium", ModBlocks.CORIUM);
    public static final DeferredItem<BlockItem> REACTOR_DEBRIS = ITEMS.registerSimpleBlockItem("reactor_debris", ModBlocks.REACTOR_DEBRIS);
    public static final DeferredItem<BlockItem> GALENA_ORE = ITEMS.registerSimpleBlockItem("galena_ore", ModBlocks.GALENA_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_GALENA_ORE = ITEMS.registerSimpleBlockItem("deepslate_galena_ore", ModBlocks.DEEPSLATE_GALENA_ORE);
    public static final DeferredItem<BlockItem> LEAD_BLOCK = ITEMS.registerSimpleBlockItem("lead_block", ModBlocks.LEAD_BLOCK);
    public static final DeferredItem<BlockItem> DECON_SHOWER = ITEMS.registerSimpleBlockItem("decon_shower", ModBlocks.DECON_SHOWER);
    public static final DeferredItem<Item> RAW_GALENA = ITEMS.registerSimpleItem("raw_galena");
    public static final DeferredItem<Item> LEAD_INGOT = ITEMS.registerSimpleItem("lead_ingot");
    public static final DeferredItem<com.frozendawn.item.LeadLiningModuleItem> LEAD_LINING_MODULE = ITEMS.register("lead_lining_module",
            () -> new com.frozendawn.item.LeadLiningModuleItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE = ITEMS.registerSimpleBlockItem("deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);
    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerSimpleItem("raw_uranium");
    public static final DeferredItem<Item> URANIUM_CONCENTRATE = ITEMS.registerSimpleItem("uranium_concentrate");
    public static final DeferredItem<Item> URANIUM_PELLET = ITEMS.registerSimpleItem("uranium_pellet");
    public static final DeferredItem<com.frozendawn.item.FuelRodItem> FUEL_ROD = ITEMS.register("fuel_rod",
            () -> new com.frozendawn.item.FuelRodItem(new Item.Properties().durability(com.frozendawn.power.ReactorPhysics.ROD_LIFE_SECONDS)));
    public static final DeferredItem<Item> SPENT_FUEL_ROD = ITEMS.register("spent_fuel_rod", () -> new Item(new Item.Properties().stacksTo(4)));
    public static final DeferredItem<com.frozendawn.item.ShieldedCaskItem> SHIELDED_CASK = ITEMS.register("shielded_cask",
            () -> new com.frozendawn.item.ShieldedCaskItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RADIOACTIVE_ASH = ITEMS.registerSimpleItem("radioactive_ash");
    public static final DeferredItem<com.frozendawn.item.RadAidItem> RAD_AID = ITEMS.register("rad_aid",
            () -> new com.frozendawn.item.RadAidItem(new Item.Properties().stacksTo(4)));
    public static final DeferredItem<com.frozendawn.item.GeigerCounterItem> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new com.frozendawn.item.GeigerCounterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> RESISTIVE_HEATER = ITEMS.registerSimpleBlockItem("resistive_heater", ModBlocks.RESISTIVE_HEATER);

    public static final DeferredItem<com.frozendawn.item.PixieItem> PIXIE = ITEMS.register("pixie",com.frozendawn.item.PixieItem::new);

    public static final DeferredItem<BlockItem> POWER_MONITOR = ITEMS.registerSimpleBlockItem("power_monitor",ModBlocks.POWER_MONITOR);

    public static final DeferredItem<BlockItem> PLUMB_LINE = ITEMS.registerSimpleBlockItem("plumb_line", ModBlocks.PLUMB_LINE);
    // Cold Flora: Rimewood wood family (no boats), Frostcap foods, and the Nether cutoff substitutes.
    public static final DeferredItem<BlockItem> RIMEWOOD_LOG = ITEMS.registerSimpleBlockItem("rimewood_log", ModBlocks.RIMEWOOD_LOG);
    public static final DeferredItem<BlockItem> RIMEWOOD_WOOD = ITEMS.registerSimpleBlockItem("rimewood_wood", ModBlocks.RIMEWOOD_WOOD);
    public static final DeferredItem<BlockItem> STRIPPED_RIMEWOOD_LOG = ITEMS.registerSimpleBlockItem("stripped_rimewood_log", ModBlocks.STRIPPED_RIMEWOOD_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_RIMEWOOD_WOOD = ITEMS.registerSimpleBlockItem("stripped_rimewood_wood", ModBlocks.STRIPPED_RIMEWOOD_WOOD);
    public static final DeferredItem<BlockItem> RIMEWOOD_PLANKS = ITEMS.registerSimpleBlockItem("rimewood_planks", ModBlocks.RIMEWOOD_PLANKS);
    public static final DeferredItem<BlockItem> RIMEWOOD_STAIRS = ITEMS.registerSimpleBlockItem("rimewood_stairs", ModBlocks.RIMEWOOD_STAIRS);
    public static final DeferredItem<BlockItem> RIMEWOOD_SLAB = ITEMS.registerSimpleBlockItem("rimewood_slab", ModBlocks.RIMEWOOD_SLAB);
    public static final DeferredItem<BlockItem> RIMEWOOD_FENCE = ITEMS.registerSimpleBlockItem("rimewood_fence", ModBlocks.RIMEWOOD_FENCE);
    public static final DeferredItem<BlockItem> RIMEWOOD_FENCE_GATE = ITEMS.registerSimpleBlockItem("rimewood_fence_gate", ModBlocks.RIMEWOOD_FENCE_GATE);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> RIMEWOOD_DOOR = ITEMS.register("rimewood_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.RIMEWOOD_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> RIMEWOOD_TRAPDOOR = ITEMS.registerSimpleBlockItem("rimewood_trapdoor", ModBlocks.RIMEWOOD_TRAPDOOR);
    public static final DeferredItem<BlockItem> RIMEWOOD_BUTTON = ITEMS.registerSimpleBlockItem("rimewood_button", ModBlocks.RIMEWOOD_BUTTON);
    public static final DeferredItem<BlockItem> RIMEWOOD_PRESSURE_PLATE = ITEMS.registerSimpleBlockItem("rimewood_pressure_plate", ModBlocks.RIMEWOOD_PRESSURE_PLATE);
    public static final DeferredItem<net.minecraft.world.item.SignItem> RIMEWOOD_SIGN = ITEMS.register("rimewood_sign",
            () -> new net.minecraft.world.item.SignItem(new Item.Properties().stacksTo(16), ModBlocks.RIMEWOOD_SIGN.get(), ModBlocks.RIMEWOOD_WALL_SIGN.get()));
    public static final DeferredItem<net.minecraft.world.item.HangingSignItem> RIMEWOOD_HANGING_SIGN = ITEMS.register("rimewood_hanging_sign",
            () -> new net.minecraft.world.item.HangingSignItem(ModBlocks.RIMEWOOD_HANGING_SIGN.get(), ModBlocks.RIMEWOOD_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BlockItem> RIMEWOOD_LEAVES = ITEMS.registerSimpleBlockItem("rimewood_leaves", ModBlocks.RIMEWOOD_LEAVES);
    public static final DeferredItem<BlockItem> RIMEWOOD_SAPLING = ITEMS.registerSimpleBlockItem("rimewood_sapling", ModBlocks.RIMEWOOD_SAPLING);
    /** Stripping a Rimewood log or wood with an axe gives one. */
    public static final DeferredItem<Item> RIMEWOOD_RESIN = ITEMS.registerSimpleItem("rimewood_resin");
    public static final DeferredItem<com.frozendawn.flora.FrostcapItem> FROZEN_FROSTCAP = ITEMS.register("frozen_frostcap",
            () -> new com.frozendawn.flora.FrostcapItem(ModBlocks.FROSTCAP.get(), new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build())));
    public static final DeferredItem<com.frozendawn.flora.FrostcapItem.Thawed> THAWED_FROSTCAP = ITEMS.register("thawed_frostcap",
            () -> new com.frozendawn.flora.FrostcapItem.Thawed(new Item.Properties()
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).build())));
    public static final DeferredItem<Item> FROSTCAP_MUSH = ITEMS.register("frostcap_mush",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(0).saturationModifier(0f).alwaysEdible()
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 300), 1f).build())));
    public static final DeferredItem<Item> FROSTCAP_STEW = ITEMS.register("frostcap_stew",
            () -> new Item(new Item.Properties().stacksTo(1).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(0.6f)
                    .usingConvertsTo(net.minecraft.world.item.Items.BOWL).build())));
    public static final DeferredItem<Item> DRIED_FROSTCAP = ITEMS.register("dried_frostcap",
            () -> new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.4f).build())));
    /** Nether cutoff substitutes: stand in for blaze powder and magma cream in Frozen Dawn recipes. */
    public static final DeferredItem<Item> SULFUR_IGNITER = ITEMS.registerSimpleItem("sulfur_igniter");
    public static final DeferredItem<Item> THERMAL_BINDER = ITEMS.registerSimpleItem("thermal_binder");

    /** Printed from a Plumb Line's last pulse; its data and tooltip live in PLUMB_LINE_SURVEY and the client overlay. */
    public static final DeferredItem<Item> PLUMB_LINE_SURVEY = ITEMS.registerSimpleItem("plumb_line_survey", new Item.Properties().stacksTo(1));
    public static final DeferredItem<BlockItem> UPLINK_CONSOLE = ITEMS.registerSimpleBlockItem("uplink_console", ModBlocks.UPLINK_CONSOLE);
    public static final DeferredItem<BlockItem> UPLINK_DISH = ITEMS.registerSimpleBlockItem("uplink_dish", ModBlocks.UPLINK_DISH);
    /** ORSA Uplink salvage parts: found at monitoring stations and ORSA sites, never crafted. */
    public static final DeferredItem<Item> SIGNAL_PROCESSOR = ITEMS.registerSimpleItem("signal_processor", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> FEED_HORN = ITEMS.registerSimpleItem("feed_horn", new Item.Properties().stacksTo(16));

    public static final DeferredItem<BlockItem> INDUCTION_FURNACE = ITEMS.registerSimpleBlockItem("induction_furnace", ModBlocks.INDUCTION_FURNACE);

    public static final DeferredItem<com.frozendawn.item.PowerCellItem> POWER_CELL = ITEMS.register("power_cell", () -> new com.frozendawn.item.PowerCellItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> BATTERY_BANK = ITEMS.registerSimpleBlockItem("battery_bank", ModBlocks.BATTERY_BANK);
    public static final DeferredItem<BlockItem> CELL_DOCK = ITEMS.registerSimpleBlockItem("cell_dock", ModBlocks.CELL_DOCK);

    public static final DeferredItem<com.frozendawn.item.ConduitBinderItem> CONDUIT_BINDER = ITEMS.register("conduit_binder",
            () -> new com.frozendawn.item.ConduitBinderItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> POWER_RELAY = ITEMS.registerSimpleBlockItem("power_relay", ModBlocks.POWER_RELAY);
    public static final DeferredItem<BlockItem> LARGE_POWER_RELAY = ITEMS.registerSimpleBlockItem("large_power_relay", ModBlocks.LARGE_POWER_RELAY);
    public static final DeferredItem<BlockItem> ORSA_EMERGENCY_LIGHT = ITEMS.registerSimpleBlockItem("orsa_emergency_light", ModBlocks.ORSA_EMERGENCY_LIGHT);
    public static final DeferredItem<com.frozendawn.item.SuitHeadlampItem> SUIT_HEADLAMP = ITEMS.register("suit_headlamp",com.frozendawn.item.SuitHeadlampItem::new);
    public static final DeferredItem<com.frozendawn.item.BatteryLanternItem> BATTERY_LANTERN = ITEMS.register("battery_lantern",com.frozendawn.item.BatteryLanternItem::new);
    public static final DeferredItem<BlockItem> WORK_LAMP = ITEMS.registerSimpleBlockItem("work_lamp", ModBlocks.WORK_LAMP);
    public static final DeferredItem<BlockItem> PANEL_LIGHT = ITEMS.registerSimpleBlockItem("panel_light", ModBlocks.PANEL_LIGHT);
    public static final DeferredItem<BlockItem> BULKHEAD_FEEDTHROUGH = ITEMS.registerSimpleBlockItem("bulkhead_feedthrough", ModBlocks.BULKHEAD_FEEDTHROUGH);

    // --- Block Items ---
    public static final DeferredItem<BlockItem> DEAD_GRASS_BLOCK = ITEMS.registerSimpleBlockItem("dead_grass_block", ModBlocks.DEAD_GRASS_BLOCK);
    public static final DeferredItem<BlockItem> FROZEN_DIRT = ITEMS.registerSimpleBlockItem("frozen_dirt", ModBlocks.FROZEN_DIRT);
    public static final DeferredItem<BlockItem> FROZEN_SAND = ITEMS.registerSimpleBlockItem("frozen_sand", ModBlocks.FROZEN_SAND);
    public static final DeferredItem<BlockItem> FROZEN_COBBLESTONE = ITEMS.registerSimpleBlockItem("frozen_cobblestone", ModBlocks.FROZEN_COBBLESTONE);
    public static final DeferredItem<BlockItem> FROZEN_STONE_BRICKS = ITEMS.registerSimpleBlockItem("frozen_stone_bricks", ModBlocks.FROZEN_STONE_BRICKS);
    public static final DeferredItem<BlockItem> FROZEN_PLANKS = ITEMS.registerSimpleBlockItem("frozen_planks", ModBlocks.FROZEN_PLANKS);
    public static final DeferredItem<BlockItem> DEAD_LOG = ITEMS.registerSimpleBlockItem("dead_log", ModBlocks.DEAD_LOG);
    public static final DeferredItem<BlockItem> FROZEN_LOG = ITEMS.registerSimpleBlockItem("frozen_log", ModBlocks.FROZEN_LOG);
    public static final DeferredItem<BlockItem> DEAD_LEAVES = ITEMS.registerSimpleBlockItem("dead_leaves", ModBlocks.DEAD_LEAVES);
    public static final DeferredItem<BlockItem> FROZEN_LEAVES = ITEMS.registerSimpleBlockItem("frozen_leaves", ModBlocks.FROZEN_LEAVES);
    public static final DeferredItem<BlockItem> ICICLE = ITEMS.registerSimpleBlockItem("icicle", ModBlocks.ICICLE);
    public static final DeferredItem<BlockItem> FROZEN_OBSIDIAN = ITEMS.registerSimpleBlockItem("frozen_obsidian", ModBlocks.FROZEN_OBSIDIAN);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> AIRLOCK_DOOR = ITEMS.register("airlock_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.AIRLOCK_DOOR.get(),new Item.Properties()));
    public static final DeferredItem<BlockItem> AIRLOCK_CONTROLLER = ITEMS.registerSimpleBlockItem("airlock_controller",ModBlocks.AIRLOCK_CONTROLLER);
    public static final DeferredItem<BlockItem> MANUAL_VENT_VALVE = ITEMS.registerSimpleBlockItem("manual_vent_valve",ModBlocks.MANUAL_VENT_VALVE);

    public static final DeferredItem<com.frozendawn.item.OxygenReservoirItem> OXYGEN_RESERVOIR=ITEMS.register("oxygen_reservoir",com.frozendawn.item.OxygenReservoirItem::new);
    public static final DeferredItem<BlockItem> OXYGEN_GAUGE=ITEMS.registerSimpleBlockItem("oxygen_gauge",ModBlocks.OXYGEN_GAUGE);
    public static final DeferredItem<BlockItem> HEAT_VENT=ITEMS.registerSimpleBlockItem("heat_vent",ModBlocks.HEAT_VENT);
    public static final DeferredItem<BlockItem> THERMOSTAT=ITEMS.registerSimpleBlockItem("thermostat",ModBlocks.THERMOSTAT);
    public static final DeferredItem<BlockItem> THERMAL_HEATER = ITEMS.registerSimpleBlockItem("thermal_heater", ModBlocks.THERMAL_HEATER);
    public static final DeferredItem<BlockItem> IRON_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("iron_thermal_heater", ModBlocks.IRON_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> GOLD_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("gold_thermal_heater", ModBlocks.GOLD_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> DIAMOND_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("diamond_thermal_heater", ModBlocks.DIAMOND_THERMAL_HEATER);
    public static final DeferredItem<MiteAwayBlockItem> MITEAWAY = ITEMS.register("miteaway",
            () -> new MiteAwayBlockItem(new Item.Properties()
                    .component(ModDataComponents.MITEAWAY_BURN_TICKS.get(), com.frozendawn.block.MiteAwayBlockEntity.MAX_BURN_TICKS)));
    public static final DeferredItem<BlockItem> INSULATED_GLASS = ITEMS.registerSimpleBlockItem("insulated_glass", ModBlocks.INSULATED_GLASS);
    public static final DeferredItem<BlockItem> FROZEN_COAL_ORE = ITEMS.registerSimpleBlockItem("frozen_coal_ore", ModBlocks.FROZEN_COAL_ORE);
    public static final DeferredItem<BlockItem> GEOTHERMAL_CORE = ITEMS.registerSimpleBlockItem("geothermal_core", ModBlocks.GEOTHERMAL_CORE);
    public static final DeferredItem<BlockItem> SCORCHED_GROUND = ITEMS.registerSimpleBlockItem("scorched_ground", ModBlocks.SCORCHED_GROUND);
    public static final DeferredItem<BlockItem> SULFUR_CRUST = ITEMS.registerSimpleBlockItem("sulfur_crust", ModBlocks.SULFUR_CRUST);
    public static final DeferredItem<BlockItem> SULFUR_ORE = ITEMS.registerSimpleBlockItem("sulfur_ore", ModBlocks.SULFUR_ORE);
    public static final DeferredItem<BlockItem> HYDROTHERMAL_ROCK = ITEMS.registerSimpleBlockItem("hydrothermal_rock", ModBlocks.HYDROTHERMAL_ROCK);
    public static final DeferredItem<BlockItem> VOLCANIC_ASH = ITEMS.registerSimpleBlockItem("volcanic_ash", ModBlocks.VOLCANIC_ASH);
    public static final DeferredItem<BlockItem> ORSA_SUPPLY_CRATE = ITEMS.registerSimpleBlockItem("orsa_supply_crate", ModBlocks.ORSA_SUPPLY_CRATE);
    public static final DeferredItem<BlockItem> CAMP_RADIO = ITEMS.registerSimpleBlockItem("camp_radio", ModBlocks.CAMP_RADIO);
    public static final DeferredItem<BlockItem> ORSA_FLAG = ITEMS.registerSimpleBlockItem("orsa_flag", ModBlocks.ORSA_FLAG);
    public static final DeferredItem<BlockItem> ALARM_BEACON = ITEMS.registerSimpleBlockItem("alarm_beacon", ModBlocks.ALARM_BEACON);
    public static final DeferredItem<BlockItem> EMERGENCY_LIGHT = ITEMS.registerSimpleBlockItem("emergency_light", ModBlocks.EMERGENCY_LIGHT);
    public static final DeferredItem<BlockItem> WALL_EMERGENCY_LIGHT = ITEMS.registerSimpleBlockItem("wall_emergency_light", ModBlocks.WALL_EMERGENCY_LIGHT);
    public static final DeferredItem<BlockItem> STREET_LIGHT = ITEMS.registerSimpleBlockItem("street_light", ModBlocks.STREET_LIGHT);
    public static final DeferredItem<BlockItem> TOWN_PA_SPEAKER = ITEMS.registerSimpleBlockItem("town_pa_speaker", ModBlocks.TOWN_PA_SPEAKER);
    public static final DeferredItem<BlockItem> TOWER_ANTENNA_CONSOLE = ITEMS.registerSimpleBlockItem("tower_antenna_console", ModBlocks.TOWER_ANTENNA_CONSOLE);
    public static final DeferredItem<BlockItem> MONITORING_STATION_TERMINAL = ITEMS.registerSimpleBlockItem("monitoring_station_terminal", ModBlocks.MONITORING_STATION_TERMINAL);
    public static final DeferredItem<BlockItem> PHASE_BAROMETER = ITEMS.registerSimpleBlockItem("phase_barometer", ModBlocks.PHASE_BAROMETER);
    public static final DeferredItem<BlockItem> FUEL_PROCESSING_SILO_CONTROLLER = ITEMS.registerSimpleBlockItem("fuel_processing_silo_controller", ModBlocks.FUEL_PROCESSING_SILO_CONTROLLER);
    public static final DeferredItem<BlockItem> LAUNCH_PAD = ITEMS.registerSimpleBlockItem("launch_pad", ModBlocks.LAUNCH_PAD);
    public static final DeferredItem<BlockItem> ROCKET_ENGINE = ITEMS.registerSimpleBlockItem("rocket_engine", ModBlocks.ROCKET_ENGINE);
    public static final DeferredItem<BlockItem> ROCKET_FIN = ITEMS.registerSimpleBlockItem("rocket_fin", ModBlocks.ROCKET_FIN);
    public static final DeferredItem<BlockItem> ROCKET_HULL = ITEMS.registerSimpleBlockItem("rocket_hull", ModBlocks.ROCKET_HULL);
    public static final DeferredItem<BlockItem> ROCKET_NOSE_CONE = ITEMS.registerSimpleBlockItem("rocket_nose_cone", ModBlocks.ROCKET_NOSE_CONE);
    public static final DeferredItem<BlockItem> BLOOM_MASS = ITEMS.registerSimpleBlockItem("bloom_mass", ModBlocks.BLOOM_MASS);
    public static final DeferredItem<BlockItem> BLOOM_CRUST = ITEMS.registerSimpleBlockItem("bloom_crust", ModBlocks.BLOOM_CRUST);
    public static final DeferredItem<BlockItem> BLOOM_TIP = ITEMS.registerSimpleBlockItem("bloom_tip", ModBlocks.BLOOM_TIP);
    public static final DeferredItem<BlockItem> INERT_ACHERONITE = ITEMS.registerSimpleBlockItem("inert_acheronite", ModBlocks.INERT_ACHERONITE);
    public static final DeferredItem<BlockItem> SEALED_LATTICE = ITEMS.registerSimpleBlockItem("sealed_lattice", ModBlocks.SEALED_LATTICE);
    public static final DeferredItem<InertConvergenceCoreItem> INERT_CONVERGENCE_CORE =
            ITEMS.register("inert_convergence_core", () -> new InertConvergenceCoreItem(
                    ModBlocks.INERT_CONVERGENCE_CORE.get(), new Item.Properties()
                    .durability(InertConvergenceCoreItem.MAX_USES)
                    .rarity(Rarity.EPIC).fireResistant()));
    // Remnant lure blocks intentionally have no item or creative-tab entry.

    // --- Items ---
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");
    public static final DeferredItem<Item> IMPURE_SULFUR = ITEMS.registerSimpleItem("impure_sulfur");
    public static final DeferredItem<Item> SULFUR = ITEMS.registerSimpleItem("sulfur");
    public static final DeferredItem<Item> SOLID_PROPELLANT = ITEMS.registerSimpleItem("solid_propellant");
    public static final DeferredItem<Item> LIQUID_OXIDIZER = ITEMS.registerSimpleItem("liquid_oxidizer");
    public static final DeferredItem<Item> ROCKET_FUEL_CELL = ITEMS.register("rocket_fuel_cell",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> THERMAL_CORE = ITEMS.registerSimpleItem("thermal_core");
    public static final DeferredItem<Item> SPENT_LATTICE = ITEMS.register("spent_lattice",
            () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> FROZEN_HEART = ITEMS.registerSimpleItem("frozen_heart");
    public static final DeferredItem<BoardPacketItem> BOARD_PACKET = ITEMS.register("board_packet",
            () -> new BoardPacketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<MartianCommandPacketItem> MARTIAN_COMMAND_PACKET = ITEMS.register("martian_command_packet",
            () -> new MartianCommandPacketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<OrsaDocumentItem> ORSA_DOCUMENT = ITEMS.register("orsa_document",
            () -> new OrsaDocumentItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MeteorologistJournalItem> METEOROLOGIST_JOURNAL = ITEMS.register("meteorologist_journal",
            () -> new MeteorologistJournalItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<RationWarmerItem> RATION_WARMER = ITEMS.register("ration_warmer",
            () -> new RationWarmerItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.RATION_WARMER_CHARGES.get(), 0)));

    public static final DeferredItem<ThermalContainerItem> THERMAL_CONTAINER = ITEMS.register("thermal_container",
            () -> new ThermalContainerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<OrsaMultiToolItem> ORSA_MULTITOOL = ITEMS.register("orsa_multitool",
            () -> new OrsaMultiToolItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ThaevenTranslatorItem> THAEVEN_TRANSLATOR = ITEMS.register(
            "thaeven_translator", () -> new ThaevenTranslatorItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.frozendawn.item.ScribeRecordItem> SCRIBE_RECORD = ITEMS.register(
            "scribe_record", () -> new com.frozendawn.item.ScribeRecordItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<ThaevenCarrierItem> HUMAN_CARRIER = ITEMS.register(
            "human_carrier", () -> new ThaevenCarrierItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    ThaevenRecordId.THE_PASSAGE));
    public static final DeferredItem<ThaevenCarrierItem> PATTERN_RESIDUE_RECORD = ITEMS.register(
            "pattern_residue_record", () -> new ThaevenCarrierItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    ThaevenRecordId.PATTERN_RESIDUE));
    public static final DeferredItem<ThaevenCarrierItem> ACCRETED_REMNANT = ITEMS.register(
            "accreted_remnant", () -> new ThaevenCarrierItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    ThaevenRecordId.THE_HEART_BENEATH));
    public static final DeferredItem<Item> RIMEBOUND_PATTERN_FRAGMENT = ITEMS.register(
            "rimebound_pattern_fragment", () -> new Item(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> RESONANT_PATTERN_FRAGMENT = ITEMS.register(
            "resonant_pattern_fragment", () -> new Item(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> REMNANT_PATTERN_FRAGMENT = ITEMS.register(
            "remnant_pattern_fragment", () -> new Item(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> FROSTWRITHE_PATTERN_FRAGMENT = ITEMS.register(
            "frostwrithe_pattern_fragment", () -> new Item(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<O2TankItem> O2_TANK = ITEMS.register("o2_tank",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER1_MAX), O2TankItem.TIER1_MAX));
    public static final DeferredItem<O2TankItem> O2_TANK_MK2 = ITEMS.register("o2_tank_mk2",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER2_MAX), O2TankItem.TIER2_MAX));
    public static final DeferredItem<O2TankItem> O2_TANK_MK3 = ITEMS.register("o2_tank_mk3",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER3_MAX), O2TankItem.TIER3_MAX));
    public static final DeferredItem<EmergencyO2CartridgeItem> EMERGENCY_O2_CARTRIDGE = ITEMS.register(
            "emergency_o2_cartridge",
            () -> new EmergencyO2CartridgeItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<O2EfficiencyModuleItem> O2_EFFICIENCY_MODULE = ITEMS.register(
            "o2_efficiency_module",
            () -> new O2EfficiencyModuleItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<OrsaTraumaKitItem> ORSA_TRAUMA_KIT = ITEMS.register(
            "orsa_trauma_kit",
            () -> new OrsaTraumaKitItem(
                    new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));
    public static final DeferredItem<BlackglassArchiveCardItem> BLACKGLASS_ARCHIVE_CARD =
            ITEMS.register("blackglass_archive_card",
                    () -> new BlackglassArchiveCardItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> SULFUR_LAVA_BUCKET = ITEMS.register("sulfur_lava_bucket",
            () -> new BucketItem(ModFluids.SOURCE_VENT_LAVA.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // --- Tier 1: Insulated Clothing (Phase 3) ---
    public static final DeferredItem<ArmorItem> INSULATED_HELMET = ITEMS.register("insulated_helmet",
            () -> new ArmorItem(ModArmorMaterials.INSULATED, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(8))));
    public static final DeferredItem<ArmorItem> INSULATED_CHESTPLATE = ITEMS.register("insulated_chestplate",
            () -> new ArmorItem(ModArmorMaterials.INSULATED, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(8))));
    public static final DeferredItem<ArmorItem> INSULATED_LEGGINGS = ITEMS.register("insulated_leggings",
            () -> new ArmorItem(ModArmorMaterials.INSULATED, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(8))));
    public static final DeferredItem<ArmorItem> INSULATED_BOOTS = ITEMS.register("insulated_boots",
            () -> new ArmorItem(ModArmorMaterials.INSULATED, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(8))));

    // --- Tier 2: Heavy Insulation (Phase 4) ---
    public static final DeferredItem<ArmorItem> REINFORCED_HELMET = ITEMS.register("reinforced_helmet",
            () -> new ArmorItem(ModArmorMaterials.REINFORCED, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))));
    public static final DeferredItem<ArmorItem> REINFORCED_CHESTPLATE = ITEMS.register("reinforced_chestplate",
            () -> new ArmorItem(ModArmorMaterials.REINFORCED, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));
    public static final DeferredItem<ArmorItem> REINFORCED_LEGGINGS = ITEMS.register("reinforced_leggings",
            () -> new ArmorItem(ModArmorMaterials.REINFORCED, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))));
    public static final DeferredItem<ArmorItem> REINFORCED_BOOTS = ITEMS.register("reinforced_boots",
            () -> new ArmorItem(ModArmorMaterials.REINFORCED, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));

    // --- Tier 3: EVA Suit (Phase 5-6) ---
    public static final DeferredItem<ArmorItem> EVA_HELMET = ITEMS.register("eva_helmet",
            () -> new ArmorItem(ModArmorMaterials.EVA, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(25))));
    public static final DeferredItem<ArmorItem> EVA_CHESTPLATE = ITEMS.register("eva_chestplate",
            () -> new ArmorItem(ModArmorMaterials.EVA, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));
    public static final DeferredItem<ArmorItem> EVA_LEGGINGS = ITEMS.register("eva_leggings",
            () -> new ArmorItem(ModArmorMaterials.EVA, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(25))));
    public static final DeferredItem<ArmorItem> EVA_BOOTS = ITEMS.register("eva_boots",
            () -> new ArmorItem(ModArmorMaterials.EVA, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(25))));

    // Emergency equipment is issued by the death-respawn handler; no recipes or creative entries.
    public static final DeferredItem<com.frozendawn.item.EmergencyEvaArmorItem> EMERGENCY_EVA_HELMET =
            ITEMS.register("emergency_eva_helmet", () -> new com.frozendawn.item.EmergencyEvaArmorItem(ArmorItem.Type.HELMET));
    public static final DeferredItem<com.frozendawn.item.EmergencyEvaArmorItem> EMERGENCY_EVA_CHESTPLATE =
            ITEMS.register("emergency_eva_chestplate", () -> new com.frozendawn.item.EmergencyEvaArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredItem<com.frozendawn.item.EmergencyEvaArmorItem> EMERGENCY_EVA_LEGGINGS =
            ITEMS.register("emergency_eva_leggings", () -> new com.frozendawn.item.EmergencyEvaArmorItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<com.frozendawn.item.EmergencyEvaArmorItem> EMERGENCY_EVA_BOOTS =
            ITEMS.register("emergency_eva_boots", () -> new com.frozendawn.item.EmergencyEvaArmorItem(ArmorItem.Type.BOOTS));

    // --- Win Condition ---
    public static final DeferredItem<AcheroniteCompassItem> ACHERONITE_COMPASS = ITEMS.register("acheronite_compass",
            () -> new AcheroniteCompassItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<OrsaDocumentItem> TRANSPONDER_SCHEMATIC = ITEMS.register("transponder_schematic",
            () -> new OrsaDocumentItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<OrsaDocumentItem> SATELLITE_LOG = ITEMS.register("satellite_log",
            () -> new OrsaDocumentItem(new Item.Properties().stacksTo(1)));

    // --- Frozen Atmosphere ---
    public static final DeferredItem<FrozenAtmosphereShardItem> FROZEN_ATMOSPHERE_SHARD = ITEMS.register(
            "frozen_atmosphere_shard",
            () -> new FrozenAtmosphereShardItem(new Item.Properties().stacksTo(64)));

    // --- Frostbitten Mob Drops ---
    public static final DeferredItem<FrozenMeatItem> FROZEN_MEAT = ITEMS.register("frozen_meat",
            () -> new FrozenMeatItem(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(4).saturationModifier(0.3f).build())));
    public static final DeferredItem<Item> HOT_WINGS = ITEMS.register("hot_wings",
            () -> new Item(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(8)
                            .saturationModifier(0.75f)
                            .build())));
    public static final DeferredItem<Item> FROSTBITTEN_CORE = ITEMS.register("frostbitten_core",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // --- Hollow Mob Drops ---
    public static final DeferredItem<Item> FROZEN_BREATH = ITEMS.register("frozen_breath",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16)));

    // --- Hollow Drop Crafts ---
    public static final DeferredItem<Item> CRYO_FUEL = ITEMS.register("cryo_fuel",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<StandingAndWallBlockItem> FROST_WARD_TORCH_ITEM = ITEMS.register("frost_ward_torch",
            () -> new StandingAndWallBlockItem(ModBlocks.FROST_WARD_TORCH.get(), ModBlocks.FROST_WARD_WALL_TORCH.get(),
                    new Item.Properties(), net.minecraft.core.Direction.DOWN));
    public static final DeferredItem<Item> THERMAL_CAPACITOR = ITEMS.register("thermal_capacitor",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).stacksTo(1)));

    // --- Returned Mob Drops ---
    public static final DeferredItem<RemnantEmberItem> REMNANT_EMBER = ITEMS.register("remnant_ember",
            () -> new RemnantEmberItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                    .component(ModDataComponents.WARMTH_REMAINING.get(), RemnantEmberItem.MAX_WARMTH)));
    public static final DeferredItem<SuitPatchItem> TATTERED_CLOTHING_SCRAP = ITEMS.register(
            "tattered_clothing_scrap",
            () -> new SuitPatchItem(new Item.Properties().stacksTo(32), false));
    public static final DeferredItem<SuitPatchItem> ORSA_SUIT_PATCH_KIT = ITEMS.register(
            "orsa_suit_patch_kit",
            () -> new SuitPatchItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), true));
    public static final DeferredItem<OrsaIdBadgeItem> ORSA_ID_BADGE = ITEMS.register("orsa_id_badge",
            () -> new OrsaIdBadgeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // --- Mimic Mob Drops ---
    public static final DeferredItem<MirroredFragmentItem> MIRRORED_FRAGMENT = ITEMS.register("mirrored_fragment",
            () -> new MirroredFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // --- Architect Mob Drops ---
    // Entity-only equipment: intentionally omitted from recipes and creative tabs.
    public static final DeferredItem<Item> MASTER_ARCHITECT_STAFF = ITEMS.register(
            "master_architect_staff",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<ArchitectSoulItem> ARCHITECT_SOUL = ITEMS.register("architect_soul",
            () -> new ArchitectSoulItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    // Future Maeve-erasure icon. Intentionally absent from creative tabs and recipes.
    public static final DeferredItem<Item> MEMORY_NODE_EMPTY = ITEMS.register(
            "memory_node_empty",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    // Hidden advancement icon for the first time The Last Witness denies a death.
    public static final DeferredItem<Item> REMEMBERED_KEEPSAKE = ITEMS.register(
            "remembered_keepsake",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    // Hidden advancement icon for surviving the Archivist's terminal notice.
    public static final DeferredItem<Item> MARKED_EYE = ITEMS.register(
            "marked_eye",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    // Unique Maeve-erasure relic. Intentionally absent from creative tabs and recipes.
    public static final DeferredItem<LastWitnessItem> THE_LAST_WITNESS = ITEMS.register(
            "the_last_witness",
            () -> new LastWitnessItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(LastWitnessItem.MAX_CHARGES)
                    .rarity(Rarity.EPIC)));
    public static final DeferredItem<ArchitectMaskItem> ARCHITECT_MASK = ITEMS.register("architect_mask",
            () -> new ArchitectMaskItem(
                    ModBlocks.ARCHITECT_MASK.get(),
                    ModBlocks.ARCHITECT_WALL_MASK.get(),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
            ));
    public static final DeferredItem<SurveyorLensItem> SURVEYOR_LENS = ITEMS.register("surveyor_lens",
            () -> new SurveyorLensItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    SurveyorLensScanner.LensProfile.STANDARD));
    public static final DeferredItem<SurveyorLensItem> CALIBRATED_SURVEYOR_LENS = ITEMS.register("calibrated_surveyor_lens",
            () -> new SurveyorLensItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SurveyorLensScanner.LensProfile.CALIBRATED));
    public static final DeferredItem<OrsaThermalVisorItem> ORSA_THERMAL_VISOR = ITEMS.register("orsa_thermal_visor",
            () -> new OrsaThermalVisorItem(ModArmorMaterials.THERMAL_VISOR, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(25)).rarity(Rarity.EPIC)));
    public static final DeferredItem<BlizzardGogglesItem> BLIZZARD_GOGGLES = ITEMS.register("blizzard_goggles",
            () -> new BlizzardGogglesItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SnowshoesItem> SNOWSHOES = ITEMS.register("snowshoes",
            () -> new SnowshoesItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<IceClawsItem> ICE_CLAWS = ITEMS.register("ice_claws",
            () -> new IceClawsItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // --- Spawn Eggs ---
    public static final DeferredItem<DeferredSpawnEggItem> FROSTBITTEN_SPAWN_EGG = ITEMS.register("frostbitten_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROSTBITTEN, 0x8FBCD4, 0x3B5998,
                    new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FROSTMITE_SPAWN_EGG = ITEMS.register("frostmite_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROSTMITE, 0xD9F5FF, 0x4FD8FF,
                    new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HOLLOW_SPAWN_EGG = ITEMS.register("hollow_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.HOLLOW, 0xCCDDEE, 0x667788,
                    new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RETURNED_SPAWN_EGG = ITEMS.register("returned_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RETURNED, 0x4A5568, 0x2D3748,
                    new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MIMIC_SPAWN_EGG = ITEMS.register("mimic_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MIMIC, 0x1A1A2E, 0x8B0000,
                    new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ARCHITECT_SPAWN_EGG = ITEMS.register("architect_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ARCHITECT, 0x4A5568, 0x5B7A9E,
                    new Item.Properties()));

    // --- Comfort Items ---
    public static final DeferredItem<ComfortItem> STUFFED_PENGUIN = ITEMS.register("stuffed_penguin",
            () -> new ComfortItem(new Item.Properties().stacksTo(1), "tooltip.frozendawn.stuffed_penguin"));
    public static final DeferredItem<ComfortItem> STUFFED_CAPYBARA = ITEMS.register("stuffed_capybara",
            () -> new ComfortItem(new Item.Properties().stacksTo(1), "tooltip.frozendawn.stuffed_capybara"));
    public static final DeferredItem<ComfortItem> STUFFED_HYRAX = ITEMS.register("stuffed_hyrax",
            () -> new ComfortItem(new Item.Properties().stacksTo(1), "tooltip.frozendawn.stuffed_hyrax"));
    public static final DeferredItem<ComfortItem> WILSON = ITEMS.register("wilson",
            () -> new ComfortItem(new Item.Properties().stacksTo(1), "tooltip.frozendawn.wilson"));

    // --- Acheronite Materials ---
    public static final DeferredItem<AcheroniteShardItem> ACHERONITE_SHARD = ITEMS.register("acheronite_shard",
            () -> new AcheroniteShardItem(new Item.Properties()));
    public static final DeferredItem<Item> REFINED_ACHERONITE = ITEMS.registerSimpleItem("refined_acheronite");

    // --- Acheronite Block Items ---
    public static final DeferredItem<BlockItem> ACHERON_FORGE = ITEMS.registerSimpleBlockItem("acheron_forge", ModBlocks.ACHERON_FORGE);
    public static final DeferredItem<BlockItem> ACHERONITE_BLOCK = ITEMS.registerSimpleBlockItem("acheronite_block", ModBlocks.ACHERONITE_BLOCK);
    public static final DeferredItem<BlockItem> TRANSPONDER = ITEMS.register("transponder",
            () -> new BlockItem(ModBlocks.TRANSPONDER.get(),
                    new Item.Properties().rarity(Rarity.EPIC)));

    // --- Acheronite Tools ---
    public static final DeferredItem<AcheroniteSwordItem> ACHERONITE_SWORD = ITEMS.register("acheronite_sword",
            () -> new AcheroniteSwordItem(ModToolTiers.ACHERONITE,
                    new Item.Properties().attributes(SwordItem.createAttributes(ModToolTiers.ACHERONITE, 3, -2.4F))));
    public static final DeferredItem<SoulHarvestBladeItem> SOUL_HARVEST_BLADE = ITEMS.register("soul_harvest_blade",
            () -> new SoulHarvestBladeItem(ModToolTiers.ACHERONITE,
                    new Item.Properties().rarity(Rarity.EPIC)
                            .attributes(SwordItem.createAttributes(ModToolTiers.ACHERONITE, 3, -2.4F))));
    public static final DeferredItem<AcheronitePickaxeItem> ACHERONITE_PICKAXE = ITEMS.register("acheronite_pickaxe",
            () -> new AcheronitePickaxeItem(ModToolTiers.ACHERONITE,
                    new Item.Properties().attributes(PickaxeItem.createAttributes(ModToolTiers.ACHERONITE, 1, -2.8F))));
    public static final DeferredItem<Item> ACHERONITE_AXE = ITEMS.register("acheronite_axe",
            () -> new AxeItem(ModToolTiers.ACHERONITE,
                    new Item.Properties().attributes(AxeItem.createAttributes(ModToolTiers.ACHERONITE, 5.0F, -3.0F))));
    public static final DeferredItem<AcheroniteShovelItem> ACHERONITE_SHOVEL = ITEMS.register("acheronite_shovel",
            () -> new AcheroniteShovelItem(ModToolTiers.ACHERONITE,
                    new Item.Properties().attributes(ShovelItem.createAttributes(ModToolTiers.ACHERONITE, 1.5F, -3.0F))));

    // --- Acheronite Armor ---
    public static final DeferredItem<ArmorItem> ACHERONITE_HELMET = ITEMS.register("acheronite_helmet",
            () -> new ArmorItem(ModArmorMaterials.ACHERONITE, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(500)));
    public static final DeferredItem<ArmorItem> ACHERONITE_CHESTPLATE = ITEMS.register("acheronite_chestplate",
            () -> new ArmorItem(ModArmorMaterials.ACHERONITE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(650)));
    public static final DeferredItem<ArmorItem> ACHERONITE_LEGGINGS = ITEMS.register("acheronite_leggings",
            () -> new ArmorItem(ModArmorMaterials.ACHERONITE, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(550)));
    public static final DeferredItem<ArmorItem> ACHERONITE_BOOTS = ITEMS.register("acheronite_boots",
            () -> new ArmorItem(ModArmorMaterials.ACHERONITE, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(500)));

    // --- Acheronite-Lined EVA Chestplate (bootstrap item) ---
    public static final DeferredItem<LinedEvaChestplateItem> LINED_EVA_CHESTPLATE = ITEMS.register("lined_eva_chestplate",
            () -> new LinedEvaChestplateItem(ModArmorMaterials.EVA, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));

    // --- Creative Tab ---
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FrozenDawn.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FROZEN_DAWN_TAB = CREATIVE_TABS.register("frozen_dawn_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + FrozenDawn.MOD_ID))
                    .withTabsBefore(CreativeModeTabs.NATURAL_BLOCKS)
                    .icon(() -> ICE_SHARD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Environment blocks
                        output.accept(DEAD_GRASS_BLOCK.get());
                        output.accept(FROZEN_DIRT.get());
                        output.accept(FROZEN_SAND.get());
                        output.accept(FROZEN_COBBLESTONE.get());
                        output.accept(FROZEN_STONE_BRICKS.get());
                        output.accept(FROZEN_PLANKS.get());
                        output.accept(RIMEWOOD_LOG.get());
                        output.accept(RIMEWOOD_WOOD.get());
                        output.accept(STRIPPED_RIMEWOOD_LOG.get());
                        output.accept(STRIPPED_RIMEWOOD_WOOD.get());
                        output.accept(RIMEWOOD_PLANKS.get());
                        output.accept(RIMEWOOD_STAIRS.get());
                        output.accept(RIMEWOOD_SLAB.get());
                        output.accept(RIMEWOOD_FENCE.get());
                        output.accept(RIMEWOOD_FENCE_GATE.get());
                        output.accept(RIMEWOOD_DOOR.get());
                        output.accept(RIMEWOOD_TRAPDOOR.get());
                        output.accept(RIMEWOOD_BUTTON.get());
                        output.accept(RIMEWOOD_PRESSURE_PLATE.get());
                        output.accept(RIMEWOOD_SIGN.get());
                        output.accept(RIMEWOOD_HANGING_SIGN.get());
                        output.accept(RIMEWOOD_LEAVES.get());
                        output.accept(RIMEWOOD_SAPLING.get());
                        output.accept(DEAD_LOG.get());
                        output.accept(FROZEN_LOG.get());
                        output.accept(DEAD_LEAVES.get());
                        output.accept(FROZEN_LEAVES.get());
                        output.accept(ICICLE.get());
                        output.accept(FROZEN_OBSIDIAN.get());
                        output.accept(FROZEN_COAL_ORE.get());
                        output.accept(SCORCHED_GROUND.get());
                        output.accept(SULFUR_CRUST.get());
                        output.accept(SULFUR_ORE.get());
                        output.accept(HYDROTHERMAL_ROCK.get());
                        output.accept(VOLCANIC_ASH.get());
                        output.accept(INERT_ACHERONITE.get());
                        output.accept(SEALED_LATTICE.get());
                        // Player agency
                        output.accept(AIRLOCK_DOOR.get());
                        output.accept(AIRLOCK_CONTROLLER.get());
                        output.accept(MANUAL_VENT_VALVE.get());
                        output.accept(THERMOSTAT.get());
                        output.accept(HEAT_VENT.get());
                        output.accept(RESISTIVE_HEATER.get());
                        output.accept(PIXIE.get());
                        output.accept(POWER_MONITOR.get());
                        output.accept(PLUMB_LINE.get());
                        output.accept(UPLINK_CONSOLE.get());
                        output.accept(UPLINK_DISH.get());
                        output.accept(SIGNAL_PROCESSOR.get());
                        output.accept(FEED_HORN.get());
                        output.accept(INDUCTION_FURNACE.get());
                        output.accept(POWER_CELL.get());
                        output.accept(BATTERY_BANK.get());
                        output.accept(CELL_DOCK.get());
                        output.accept(CONDUIT_BINDER.get());
                        output.accept(POWER_RELAY.get());
                        output.accept(LARGE_POWER_RELAY.get());
                        output.accept(BULKHEAD_FEEDTHROUGH.get());
                        output.accept(REACTOR_CONTROLLER.get());
                        output.accept(REACTOR_CASING.get());
                        output.accept(REACTOR_CORE.get());
                        output.accept(CONTROL_ROD_DRIVE.get());
                        output.accept(COOLANT_PORT.get());
                        output.accept(REACTOR_RADIATOR.get());
                        output.accept(DEEPSLATE_URANIUM_ORE.get());
                        output.accept(RAW_URANIUM.get());
                        output.accept(URANIUM_CONCENTRATE.get());
                        output.accept(URANIUM_PELLET.get());
                        output.accept(FUEL_ROD.get());
                        output.accept(SPENT_FUEL_ROD.get());
                        output.accept(SHIELDED_CASK.get());
                        output.accept(RADIOACTIVE_ASH.get());
                        output.accept(RAD_AID.get());
                        output.accept(GEIGER_COUNTER.get());
                        output.accept(CORIUM.get());
                        output.accept(REACTOR_DEBRIS.get());
                        output.accept(GALENA_ORE.get());
                        output.accept(DEEPSLATE_GALENA_ORE.get());
                        output.accept(RAW_GALENA.get());
                        output.accept(LEAD_INGOT.get());
                        output.accept(LEAD_BLOCK.get());
                        output.accept(LEAD_LINING_MODULE.get());
                        output.accept(DECON_SHOWER.get());
                        output.accept(PANEL_LIGHT.get());
                        output.accept(WORK_LAMP.get());
                        output.accept(BATTERY_LANTERN.get());
                        output.accept(SUIT_HEADLAMP.get());
                        output.accept(ORSA_EMERGENCY_LIGHT.get());
                        output.accept(OXYGEN_GAUGE.get());
                        output.accept(OXYGEN_RESERVOIR.get());
                        output.accept(THERMAL_HEATER.get());
                        output.accept(IRON_THERMAL_HEATER.get());
                        output.accept(GOLD_THERMAL_HEATER.get());
                        output.accept(DIAMOND_THERMAL_HEATER.get());
                        output.accept(MITEAWAY.get());
                        output.accept(INSULATED_GLASS.get());
                        output.accept(GEOTHERMAL_CORE.get());
                        output.accept(FUEL_PROCESSING_SILO_CONTROLLER.get());
                        output.accept(LAUNCH_PAD.get());
                        output.accept(ROCKET_ENGINE.get());
                        output.accept(ROCKET_FIN.get());
                        output.accept(ROCKET_HULL.get());
                        output.accept(ROCKET_NOSE_CONE.get());
                        output.accept(ORSA_SUPPLY_CRATE.get());
                        output.accept(CAMP_RADIO.get());
                        output.accept(ORSA_FLAG.get());
                        output.accept(ALARM_BEACON.get());
                        output.accept(EMERGENCY_LIGHT.get());
                        output.accept(WALL_EMERGENCY_LIGHT.get());
                        output.accept(TOWER_ANTENNA_CONSOLE.get());
                        output.accept(MONITORING_STATION_TERMINAL.get());
                        output.accept(PHASE_BAROMETER.get());
                        // Items
                        output.accept(ICE_SHARD.get());
                        output.accept(IMPURE_SULFUR.get());
                        output.accept(SULFUR.get());
                        output.accept(SULFUR_IGNITER.get());
                        output.accept(RIMEWOOD_RESIN.get());
                        output.accept(THERMAL_BINDER.get());
                        output.accept(SOLID_PROPELLANT.get());
                        output.accept(LIQUID_OXIDIZER.get());
                        output.accept(ROCKET_FUEL_CELL.get());
                        output.accept(THERMAL_CORE.get());
                        output.accept(SPENT_LATTICE.get());
                        output.accept(FROZEN_HEART.get());
                        output.accept(BOARD_PACKET.get());
                        output.accept(MARTIAN_COMMAND_PACKET.get());
                        output.accept(METEOROLOGIST_JOURNAL.get());
                        output.accept(THERMAL_CONTAINER.get());
                        output.accept(RATION_WARMER.get());
                        output.accept(ORSA_MULTITOOL.get());
                        output.accept(THAEVEN_TRANSLATOR.get());
                        ItemStack guideBook = StarterBooks.createGuideBook();
                        if (guideBook != null) {
                            output.accept(guideBook);
                        }
                        output.accept(O2_TANK.get());
                        output.accept(O2_TANK_MK2.get());
                        output.accept(O2_TANK_MK3.get());
                        output.accept(EMERGENCY_O2_CARTRIDGE.get());
                        output.accept(O2_EFFICIENCY_MODULE.get());
                        output.accept(ORSA_TRAUMA_KIT.get());
                        output.accept(BLACKGLASS_ARCHIVE_CARD.get());
                        output.accept(SULFUR_LAVA_BUCKET.get());
                        // Armor - Tier 1
                        output.accept(INSULATED_HELMET.get());
                        output.accept(INSULATED_CHESTPLATE.get());
                        output.accept(INSULATED_LEGGINGS.get());
                        output.accept(INSULATED_BOOTS.get());
                        // Armor - Tier 2
                        output.accept(REINFORCED_HELMET.get());
                        output.accept(REINFORCED_CHESTPLATE.get());
                        output.accept(REINFORCED_LEGGINGS.get());
                        output.accept(REINFORCED_BOOTS.get());
                        // Armor - Tier 3
                        output.accept(EVA_HELMET.get());
                        output.accept(EVA_CHESTPLATE.get());
                        output.accept(EVA_LEGGINGS.get());
                        output.accept(EVA_BOOTS.get());
                        // Acheronite
                        output.accept(ACHERONITE_SHARD.get());
                        output.accept(REFINED_ACHERONITE.get());
                        output.accept(ACHERON_FORGE.get());
                        output.accept(ACHERONITE_BLOCK.get());
                        output.accept(ACHERONITE_SWORD.get());
                        output.accept(SOUL_HARVEST_BLADE.get());
                        output.accept(ACHERONITE_PICKAXE.get());
                        output.accept(ACHERONITE_AXE.get());
                        output.accept(ACHERONITE_SHOVEL.get());
                        output.accept(ACHERONITE_HELMET.get());
                        output.accept(ACHERONITE_CHESTPLATE.get());
                        output.accept(ACHERONITE_LEGGINGS.get());
                        output.accept(ACHERONITE_BOOTS.get());
                        output.accept(LINED_EVA_CHESTPLATE.get());
                        // Frozen Atmosphere
                        output.accept(FROZEN_ATMOSPHERE_SHARD.get());
                        // Comfort Items
                        output.accept(STUFFED_PENGUIN.get());
                        output.accept(STUFFED_CAPYBARA.get());
                        output.accept(STUFFED_HYRAX.get());
                        output.accept(WILSON.get());
                        // Win Condition
                        output.accept(ACHERONITE_COMPASS.get());
                        output.accept(TRANSPONDER_SCHEMATIC.get());
                        output.accept(TRANSPONDER.get());
                        // Mob Drops
                        output.accept(FROZEN_MEAT.get());
                        output.accept(HOT_WINGS.get());
                        output.accept(FROZEN_FROSTCAP.get());
                        output.accept(THAWED_FROSTCAP.get());
                        output.accept(FROSTCAP_MUSH.get());
                        output.accept(FROSTCAP_STEW.get());
                        output.accept(DRIED_FROSTCAP.get());
                        output.accept(FROSTBITTEN_CORE.get());
                        output.accept(FROZEN_BREATH.get());
                        output.accept(REMNANT_EMBER.get());
                        output.accept(TATTERED_CLOTHING_SCRAP.get());
                        output.accept(ORSA_SUIT_PATCH_KIT.get());
                        output.accept(ORSA_ID_BADGE.get());
                        output.accept(MIRRORED_FRAGMENT.get());
                        output.accept(ARCHITECT_SOUL.get());
                        output.accept(ARCHITECT_MASK.get());
                        output.accept(SURVEYOR_LENS.get());
                        output.accept(CALIBRATED_SURVEYOR_LENS.get());
                        output.accept(ORSA_THERMAL_VISOR.get());
                        output.accept(BLIZZARD_GOGGLES.get());
                        output.accept(SNOWSHOES.get());
                        output.accept(ICE_CLAWS.get());
                        // Hollow Drop Crafts
                        output.accept(CRYO_FUEL.get());
                        output.accept(FROST_WARD_TORCH_ITEM.get());
                        output.accept(THERMAL_CAPACITOR.get());
                        // Spawn Eggs
                        output.accept(FROSTBITTEN_SPAWN_EGG.get());
                        output.accept(FROSTMITE_SPAWN_EGG.get());
                        output.accept(HOLLOW_SPAWN_EGG.get());
                        output.accept(RETURNED_SPAWN_EGG.get());
                        output.accept(MIMIC_SPAWN_EGG.get());
                        output.accept(ARCHITECT_SPAWN_EGG.get());
                    }).build());

    public static void addToVanillaCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SULFUR_LAVA_BUCKET.get());
        }
    }
}
