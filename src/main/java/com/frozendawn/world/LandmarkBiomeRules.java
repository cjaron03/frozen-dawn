package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * Shared biome allowlist for large ORSA landmarks that need open, mostly flat terrain.
 */
public final class LandmarkBiomeRules {

    private static final Set<ResourceKey<Biome>> ALLOWED_BIOMES = Set.of(
            Biomes.PLAINS,
            Biomes.SUNFLOWER_PLAINS,
            Biomes.SNOWY_PLAINS,
            Biomes.DESERT,
            Biomes.SAVANNA,
            Biomes.MEADOW,
            Biomes.FOREST,
            Biomes.FLOWER_FOREST,
            Biomes.BIRCH_FOREST,
            Biomes.OLD_GROWTH_BIRCH_FOREST,
            Biomes.TAIGA,
            Biomes.SNOWY_TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA
    );

    private static final Set<ResourceKey<Biome>> HARD_FAIL_FOOTPRINT_BIOMES = Set.of(
            Biomes.RIVER,
            Biomes.FROZEN_RIVER,
            Biomes.OCEAN,
            Biomes.DEEP_OCEAN,
            Biomes.COLD_OCEAN,
            Biomes.DEEP_COLD_OCEAN,
            Biomes.FROZEN_OCEAN,
            Biomes.DEEP_FROZEN_OCEAN,
            Biomes.LUKEWARM_OCEAN,
            Biomes.DEEP_LUKEWARM_OCEAN,
            Biomes.WARM_OCEAN,
            Biomes.SWAMP,
            Biomes.MANGROVE_SWAMP,
            Biomes.JAGGED_PEAKS,
            Biomes.FROZEN_PEAKS,
            Biomes.STONY_PEAKS
    );

    private LandmarkBiomeRules() {
    }

    public static Holder<Biome> getLandmarkNoiseBiome(ServerLevel level, int x, int z) {
        int quartY = QuartPos.fromBlock(level.getSeaLevel());
        return level.getUncachedNoiseBiome(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z));
    }

    public static boolean isEligibleLandmarkBiome(ServerLevel level, BlockPos pos) {
        return isEligibleLandmarkBiome(getLandmarkNoiseBiome(level, pos.getX(), pos.getZ()));
    }

    public static boolean isEligibleLandmarkBiome(ServerLevel level, int x, int z) {
        return isEligibleLandmarkBiome(getLandmarkNoiseBiome(level, x, z));
    }

    public static boolean isEligibleLandmarkBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(ALLOWED_BIOMES::contains).orElse(false);
    }

    public static boolean isToleratedLandmarkFootprintBiome(ServerLevel level, int x, int z) {
        return isToleratedLandmarkFootprintBiome(getLandmarkNoiseBiome(level, x, z));
    }

    public static boolean isToleratedLandmarkFootprintBiome(ServerLevel level, BlockPos pos) {
        return isToleratedLandmarkFootprintBiome(getLandmarkNoiseBiome(level, pos.getX(), pos.getZ()));
    }

    public static boolean isToleratedLandmarkFootprintBiome(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> !HARD_FAIL_FOOTPRINT_BIOMES.contains(key))
                .orElse(false);
    }
}
