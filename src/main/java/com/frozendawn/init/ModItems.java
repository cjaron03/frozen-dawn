package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
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
    public static final DeferredItem<BlockItem> INSULATED_GLASS = ITEMS.registerSimpleBlockItem("insulated_glass", ModBlocks.INSULATED_GLASS);
    public static final DeferredItem<BlockItem> FROZEN_COAL_ORE = ITEMS.registerSimpleBlockItem("frozen_coal_ore", ModBlocks.FROZEN_COAL_ORE);
    public static final DeferredItem<BlockItem> GEOTHERMAL_CORE = ITEMS.registerSimpleBlockItem("geothermal_core", ModBlocks.GEOTHERMAL_CORE);

    // --- Items ---
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");
    public static final DeferredItem<Item> THERMAL_CORE = ITEMS.registerSimpleItem("thermal_core");
    public static final DeferredItem<Item> FROZEN_HEART = ITEMS.registerSimpleItem("frozen_heart");

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
                        output.accept(INSULATED_GLASS.get());
                        output.accept(GEOTHERMAL_CORE.get());
                        // Items
                        output.accept(ICE_SHARD.get());
                        output.accept(THERMAL_CORE.get());
                        output.accept(FROZEN_HEART.get());
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
                    }).build());
}
