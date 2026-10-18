package com.frozendawn.flora;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A small wild grove: two to four Rimewoods within a few blocks of each other, never a forest. */
public final class RimewoodGroveFeature extends Feature<NoneFeatureConfiguration> {
    public RimewoodGroveFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        var random = context.random();
        int want = 2 + random.nextInt(3), grown = 0;
        for (int attempt = 0; attempt < want * 4 && grown < want; attempt++) {
            int x = context.origin().getX() + random.nextInt(11) - 5, z = context.origin().getZ() + random.nextInt(11) - 5;
            var top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, new BlockPos(x, 0, z));
            // A snow layer on top is fine: the tree replaces it and stands on the ground beneath.
            if (level.getBlockState(top.below()).is(Blocks.SNOW)) top = top.below();
            if (RimewoodTreeFeature.grow(level, top, random)) grown++;
        }
        return grown > 0;
    }
}
