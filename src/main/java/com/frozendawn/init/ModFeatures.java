package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.flora.RimewoodGroveFeature;
import com.frozendawn.flora.RimewoodTreeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World-generation features; their configured and placed forms are data files under worldgen/. */
public final class ModFeatures {
    private ModFeatures() { }

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<Feature<?>, RimewoodTreeFeature> RIMEWOOD_TREE_FEATURE =
            FEATURES.register("rimewood_tree", () -> new RimewoodTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, RimewoodGroveFeature> RIMEWOOD_GROVE_FEATURE =
            FEATURES.register("rimewood_grove", () -> new RimewoodGroveFeature(NoneFeatureConfiguration.CODEC));

    /** The configured tree a sapling grows. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> RIMEWOOD_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "rimewood_tree"));
}
