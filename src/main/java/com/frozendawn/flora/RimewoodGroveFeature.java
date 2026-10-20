package com.frozendawn.flora;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A small grove (for /place): two to four Rimewoods within a few blocks of each other, some carrying Frostcap. */
public final class RimewoodGroveFeature extends Feature<NoneFeatureConfiguration> {
    public RimewoodGroveFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return !ColdFloraSpawner.plantGrove(context.level(), context.origin(), context.random(), true).isEmpty();
    }
}
