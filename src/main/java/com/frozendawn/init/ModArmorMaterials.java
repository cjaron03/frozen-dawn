package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public class ModArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, FrozenDawn.MOD_ID);

    /** Tier 1: Insulated Clothing — wool/leather, protects to Phase 3 (~-25C) */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INSULATED =
            ARMOR_MATERIALS.register("insulated", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.HELMET, 2);
                        map.put(ArmorItem.Type.CHESTPLATE, 4);
                        map.put(ArmorItem.Type.LEGGINGS, 3);
                        map.put(ArmorItem.Type.BOOTS, 1);
                    }),
                    12,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.WHITE_WOOL),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "insulated"))),
                    0.0F, 0.0F));

    /** Tier 2: Heavy Insulation — blaze-powered heating, protects to Phase 4 (~-45C) */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> REINFORCED =
            ARMOR_MATERIALS.register("reinforced", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.HELMET, 3);
                        map.put(ArmorItem.Type.CHESTPLATE, 5);
                        map.put(ArmorItem.Type.LEGGINGS, 4);
                        map.put(ArmorItem.Type.BOOTS, 2);
                    }),
                    10,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(Items.BLAZE_POWDER),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "reinforced"))),
                    0.5F, 0.0F));

    /** Tier 3: EVA Suit — extreme insulation + sealed air loop, protects to Phase 6 mid */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> EVA =
            ARMOR_MATERIALS.register("eva", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.HELMET, 3);
                        map.put(ArmorItem.Type.CHESTPLATE, 6);
                        map.put(ArmorItem.Type.LEGGINGS, 5);
                        map.put(ArmorItem.Type.BOOTS, 2);
                    }),
                    8,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(ModItems.ICE_SHARD),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "eva"))),
                    1.0F, 0.05F));

    /**
     * Returns the cold resistance bonus (°C) for a given armor material.
     * Full set = 4x this value.
     */
    public static float getColdResistancePerPiece(DeferredHolder<ArmorMaterial, ArmorMaterial> material) {
        if (material == INSULATED) return 6.25f;   // 25°C / 4
        if (material == REINFORCED) return 11.25f;  // 45°C / 4
        if (material == EVA) return 30.0f;           // 120°C / 4
        return 0f;
    }
}
