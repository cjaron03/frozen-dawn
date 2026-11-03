package com.frozendawn.world;

import com.frozendawn.init.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One pegmatite pod deep in deepslate: an ellipsoid 3 to 5 blocks in each radius, about a fifth of it spodumene ore.
 * It replaces deepslate (and tuff) only, so spodumene is never found loose. */
public final class PegmatitePodFeature extends Feature<NoneFeatureConfiguration> {
    public static final float SPODUMENE_SHARE = 0.2f;

    public PegmatitePodFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return place(context.level(), context.origin(), context.random()) > 0;
    }

    /** Places a pod centred on {@code center} and returns how many blocks it placed. */
    public static int place(WorldGenLevel level, BlockPos center, RandomSource random) {
        int rx = 3 + random.nextInt(3), ry = 3 + random.nextInt(3), rz = 3 + random.nextInt(3), placed = 0;
        var pegmatite = ModBlocks.PEGMATITE.get().defaultBlockState();
        var ore = ModBlocks.DEEPSLATE_SPODUMENE_ORE.get().defaultBlockState();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = -rx; x <= rx; x++) for (int y = -ry; y <= ry; y++) for (int z = -rz; z <= rz; z++) {
            double d = (double) x * x / (rx * rx) + (double) y * y / (ry * ry) + (double) z * z / (rz * rz);
            if (d > 1) continue;
            pos.setWithOffset(center, x, y, z);
            if (!level.getBlockState(pos).is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) continue;
            level.setBlock(pos, random.nextFloat() < SPODUMENE_SHARE ? ore : pegmatite, 2);
            placed++;
        }
        return placed;
    }
}
