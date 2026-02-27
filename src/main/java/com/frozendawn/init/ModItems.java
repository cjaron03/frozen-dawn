package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.AcheroniteCompassItem;
import com.frozendawn.item.AcheronitePickaxeItem;
import com.frozendawn.item.AcheroniteShardItem;
import com.frozendawn.item.AcheroniteShovelItem;
import com.frozendawn.item.AcheroniteSwordItem;
import com.frozendawn.item.FrozenAtmosphereShardItem;
import com.frozendawn.item.OrsaDocumentItem;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.item.OrsaMultiToolItem;
import com.frozendawn.item.ThermalContainerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrozenDawn.MOD_ID);

    // --- Block Items ---
    public static final DeferredItem<BlockItem> DEAD_GRASS_BLOCK = ITEMS.registerSimpleBlockItem("dead_grass_block", ModBlocks.DEAD_GRASS_BLOCK);
    public static final DeferredItem<BlockItem> FROZEN_DIRT = ITEMS.registerSimpleBlockItem("frozen_dirt", ModBlocks.FROZEN_DIRT);
    public static final DeferredItem<BlockItem> FROZEN_SAND = ITEMS.registerSimpleBlockItem("frozen_sand", ModBlocks.FROZEN_SAND);
    public static final DeferredItem<BlockItem> DEAD_LOG = ITEMS.registerSimpleBlockItem("dead_log", ModBlocks.DEAD_LOG);
    public static final DeferredItem<BlockItem> FROZEN_LOG = ITEMS.registerSimpleBlockItem("frozen_log", ModBlocks.FROZEN_LOG);
    public static final DeferredItem<BlockItem> DEAD_LEAVES = ITEMS.registerSimpleBlockItem("dead_leaves", ModBlocks.DEAD_LEAVES);
    public static final DeferredItem<BlockItem> FROZEN_LEAVES = ITEMS.registerSimpleBlockItem("frozen_leaves", ModBlocks.FROZEN_LEAVES);
    public static final DeferredItem<BlockItem> FROZEN_OBSIDIAN = ITEMS.registerSimpleBlockItem("frozen_obsidian", ModBlocks.FROZEN_OBSIDIAN);
    public static final DeferredItem<BlockItem> THERMAL_HEATER = ITEMS.registerSimpleBlockItem("thermal_heater", ModBlocks.THERMAL_HEATER);
    public static final DeferredItem<BlockItem> IRON_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("iron_thermal_heater", ModBlocks.IRON_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> GOLD_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("gold_thermal_heater", ModBlocks.GOLD_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> DIAMOND_THERMAL_HEATER = ITEMS.registerSimpleBlockItem("diamond_thermal_heater", ModBlocks.DIAMOND_THERMAL_HEATER);
    public static final DeferredItem<BlockItem> INSULATED_GLASS = ITEMS.registerSimpleBlockItem("insulated_glass", ModBlocks.INSULATED_GLASS);
    public static final DeferredItem<BlockItem> FROZEN_COAL_ORE = ITEMS.registerSimpleBlockItem("frozen_coal_ore", ModBlocks.FROZEN_COAL_ORE);
    public static final DeferredItem<BlockItem> GEOTHERMAL_CORE = ITEMS.registerSimpleBlockItem("geothermal_core", ModBlocks.GEOTHERMAL_CORE);

    // --- Items ---
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");
    public static final DeferredItem<Item> THERMAL_CORE = ITEMS.registerSimpleItem("thermal_core");
    public static final DeferredItem<Item> FROZEN_HEART = ITEMS.registerSimpleItem("frozen_heart");
    public static final DeferredItem<OrsaDocumentItem> ORSA_DOCUMENT = ITEMS.register("orsa_document",
            () -> new OrsaDocumentItem(new Item.Properties().stacksTo(1)));
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
                        output.accept(DEAD_LOG.get());
                        output.accept(FROZEN_LOG.get());
                        output.accept(DEAD_LEAVES.get());
                        output.accept(FROZEN_LEAVES.get());
                        output.accept(FROZEN_OBSIDIAN.get());
                        output.accept(FROZEN_COAL_ORE.get());
                        // Player agency
                        output.accept(THERMAL_HEATER.get());
                        output.accept(IRON_THERMAL_HEATER.get());
                        output.accept(GOLD_THERMAL_HEATER.get());
                        output.accept(DIAMOND_THERMAL_HEATER.get());
                        output.accept(INSULATED_GLASS.get());
                        output.accept(GEOTHERMAL_CORE.get());
                        // Items
                        output.accept(ICE_SHARD.get());
                        output.accept(THERMAL_CORE.get());
                        output.accept(FROZEN_HEART.get());
                        output.accept(THERMAL_CONTAINER.get());
                        output.accept(ORSA_MULTITOOL.get());
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
                        // Win Condition
                        output.accept(ACHERONITE_COMPASS.get());
                        output.accept(TRANSPONDER_SCHEMATIC.get());
                        output.accept(TRANSPONDER.get());
                    }).build());
}
