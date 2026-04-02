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
import com.frozendawn.item.ComfortItem;
import com.frozendawn.item.FrozenAtmosphereShardItem;
import com.frozendawn.item.FrozenMeatItem;
import com.frozendawn.item.MeteorologistJournalItem;
import com.frozendawn.item.SoulHarvestBladeItem;
import com.frozendawn.item.OrsaDocumentItem;
import com.frozendawn.item.OrsaIdBadgeItem;
import com.frozendawn.item.OrsaThermalVisorItem;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.item.OrsaMultiToolItem;
import com.frozendawn.item.MirroredFragmentItem;
import com.frozendawn.item.RemnantEmberItem;
import com.frozendawn.item.SurveyorLensItem;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.item.ThermalContainerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrozenDawn.MOD_ID);

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
    public static final DeferredItem<BlockItem> THERMAL_HEATER = ITEMS.registerSimpleBlockItem("thermal_heater", ModBlocks.THERMAL_HEATER);
    public static final DeferredItem<BlockItem> IRON_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("iron_thermal_heater", ModBlocks.IRON_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> GOLD_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("gold_thermal_heater", ModBlocks.GOLD_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> DIAMOND_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("diamond_thermal_heater", ModBlocks.DIAMOND_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> INSULATED_GLASS = ITEMS.registerSimpleBlockItem("insulated_glass", ModBlocks.INSULATED_GLASS);
    public static final DeferredItem<BlockItem> FROZEN_COAL_ORE = ITEMS.registerSimpleBlockItem("frozen_coal_ore", ModBlocks.FROZEN_COAL_ORE);
    public static final DeferredItem<BlockItem> GEOTHERMAL_CORE = ITEMS.registerSimpleBlockItem("geothermal_core", ModBlocks.GEOTHERMAL_CORE);
    public static final DeferredItem<BlockItem> ORSA_SUPPLY_CRATE = ITEMS.registerSimpleBlockItem("orsa_supply_crate", ModBlocks.ORSA_SUPPLY_CRATE);
    public static final DeferredItem<BlockItem> CAMP_RADIO = ITEMS.registerSimpleBlockItem("camp_radio", ModBlocks.CAMP_RADIO);
    public static final DeferredItem<BlockItem> ORSA_FLAG = ITEMS.registerSimpleBlockItem("orsa_flag", ModBlocks.ORSA_FLAG);
    public static final DeferredItem<BlockItem> ALARM_BEACON = ITEMS.registerSimpleBlockItem("alarm_beacon", ModBlocks.ALARM_BEACON);
    public static final DeferredItem<BlockItem> EMERGENCY_LIGHT = ITEMS.registerSimpleBlockItem("emergency_light", ModBlocks.EMERGENCY_LIGHT);
    public static final DeferredItem<BlockItem> WALL_EMERGENCY_LIGHT = ITEMS.registerSimpleBlockItem("wall_emergency_light", ModBlocks.WALL_EMERGENCY_LIGHT);
    public static final DeferredItem<BlockItem> STREET_LIGHT = ITEMS.registerSimpleBlockItem("street_light", ModBlocks.STREET_LIGHT);
    public static final DeferredItem<BlockItem> TOWER_ANTENNA_CONSOLE = ITEMS.registerSimpleBlockItem("tower_antenna_console", ModBlocks.TOWER_ANTENNA_CONSOLE);
    public static final DeferredItem<BlockItem> MONITORING_STATION_TERMINAL = ITEMS.registerSimpleBlockItem("monitoring_station_terminal", ModBlocks.MONITORING_STATION_TERMINAL);

    // --- Items ---
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");
    public static final DeferredItem<Item> THERMAL_CORE = ITEMS.registerSimpleItem("thermal_core");
    public static final DeferredItem<Item> FROZEN_HEART = ITEMS.registerSimpleItem("frozen_heart");
    public static final DeferredItem<OrsaDocumentItem> ORSA_DOCUMENT = ITEMS.register("orsa_document",
            () -> new OrsaDocumentItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MeteorologistJournalItem> METEOROLOGIST_JOURNAL = ITEMS.register("meteorologist_journal",
            () -> new MeteorologistJournalItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ThermalContainerItem> THERMAL_CONTAINER = ITEMS.register("thermal_container",
            () -> new ThermalContainerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<OrsaMultiToolItem> ORSA_MULTITOOL = ITEMS.register("orsa_multitool",
            () -> new OrsaMultiToolItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<O2TankItem> O2_TANK = ITEMS.register("o2_tank",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER1_MAX), O2TankItem.TIER1_MAX));
    public static final DeferredItem<O2TankItem> O2_TANK_MK2 = ITEMS.register("o2_tank_mk2",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER2_MAX), O2TankItem.TIER2_MAX));
    public static final DeferredItem<O2TankItem> O2_TANK_MK3 = ITEMS.register("o2_tank_mk3",
            () -> new O2TankItem(new Item.Properties().stacksTo(1)
                    .component(ModDataComponents.O2_LEVEL.get(), O2TankItem.TIER3_MAX), O2TankItem.TIER3_MAX));

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
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(4).saturationModifier(0.3f).build())));
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
    public static final DeferredItem<Item> TATTERED_CLOTHING_SCRAP = ITEMS.registerSimpleItem("tattered_clothing_scrap");
    public static final DeferredItem<OrsaIdBadgeItem> ORSA_ID_BADGE = ITEMS.register("orsa_id_badge",
            () -> new OrsaIdBadgeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // --- Mimic Mob Drops ---
    public static final DeferredItem<MirroredFragmentItem> MIRRORED_FRAGMENT = ITEMS.register("mirrored_fragment",
            () -> new MirroredFragmentItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // --- Architect Mob Drops ---
    public static final DeferredItem<ArchitectSoulItem> ARCHITECT_SOUL = ITEMS.register("architect_soul",
            () -> new ArchitectSoulItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
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
            () -> new ComfortItem(new Item.Properties().stacksTo(1), "tooltip.frozendawn.wilson", true));

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
    public static final DeferredItem<ArmorItem> LINED_EVA_CHESTPLATE = ITEMS.register("lined_eva_chestplate",
            () -> new ArmorItem(ModArmorMaterials.EVA, ArmorItem.Type.CHESTPLATE,
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
                        output.accept(DEAD_LOG.get());
                        output.accept(FROZEN_LOG.get());
                        output.accept(DEAD_LEAVES.get());
                        output.accept(FROZEN_LEAVES.get());
                        output.accept(ICICLE.get());
                        output.accept(FROZEN_OBSIDIAN.get());
                        output.accept(FROZEN_COAL_ORE.get());
                        // Player agency
                        output.accept(THERMAL_HEATER.get());
                        output.accept(IRON_THERMAL_HEATER.get());
                        output.accept(GOLD_THERMAL_HEATER.get());
                        output.accept(DIAMOND_THERMAL_HEATER.get());
                        output.accept(INSULATED_GLASS.get());
                        output.accept(GEOTHERMAL_CORE.get());
                        output.accept(ORSA_SUPPLY_CRATE.get());
                        output.accept(CAMP_RADIO.get());
                        output.accept(ORSA_FLAG.get());
                        output.accept(ALARM_BEACON.get());
                        output.accept(EMERGENCY_LIGHT.get());
                        output.accept(WALL_EMERGENCY_LIGHT.get());
                        output.accept(TOWER_ANTENNA_CONSOLE.get());
                        output.accept(MONITORING_STATION_TERMINAL.get());
                        // Items
                        output.accept(ICE_SHARD.get());
                        output.accept(THERMAL_CORE.get());
                        output.accept(FROZEN_HEART.get());
                        output.accept(METEOROLOGIST_JOURNAL.get());
                        output.accept(THERMAL_CONTAINER.get());
                        output.accept(ORSA_MULTITOOL.get());
                        ItemStack guideBook = StarterBooks.createGuideBook();
                        if (guideBook != null) {
                            output.accept(guideBook);
                        }
                        output.accept(O2_TANK.get());
                        output.accept(O2_TANK_MK2.get());
                        output.accept(O2_TANK_MK3.get());
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
                        output.accept(FROSTBITTEN_CORE.get());
                        output.accept(FROZEN_BREATH.get());
                        output.accept(REMNANT_EMBER.get());
                        output.accept(TATTERED_CLOTHING_SCRAP.get());
                        output.accept(ORSA_ID_BADGE.get());
                        output.accept(MIRRORED_FRAGMENT.get());
                        output.accept(ARCHITECT_SOUL.get());
                        output.accept(ARCHITECT_MASK.get());
                        output.accept(SURVEYOR_LENS.get());
                        output.accept(CALIBRATED_SURVEYOR_LENS.get());
                        output.accept(ORSA_THERMAL_VISOR.get());
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
}
