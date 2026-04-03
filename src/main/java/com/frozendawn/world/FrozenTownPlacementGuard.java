package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Shared exclusion checks for placements that should avoid Frozen Town footprints.
 */
public final class FrozenTownPlacementGuard {

    private static final ResourceKey<Structure> FROZEN_TOWN = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_town")
    );
    public static final int DEFAULT_EDGE_PADDING = 32;
    private static final int TOWN_SEARCH_CHUNK_RADIUS = 10;

    private FrozenTownPlacementGuard() {
    }

    public static boolean overlapsFrozenTownExclusion(ServerLevel level, BlockPos center, int footprintRadius) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return false;
        }

        int exclusion = footprintRadius + DEFAULT_EDGE_PADDING;
        ChunkPos chunkPos = new ChunkPos(center);
        Set<StructureStart> starts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int dx = -TOWN_SEARCH_CHUNK_RADIUS; dx <= TOWN_SEARCH_CHUNK_RADIUS; dx++) {
            for (int dz = -TOWN_SEARCH_CHUNK_RADIUS; dz <= TOWN_SEARCH_CHUNK_RADIUS; dz++) {
                starts.addAll(level.structureManager().startsForStructure(
                        new ChunkPos(chunkPos.x + dx, chunkPos.z + dz),
                        candidate -> candidate == structure
                ));
            }
        }

        for (StructureStart start : starts) {
            if (start == null || !start.isValid()) {
                continue;
            }
            var bounds = start.getBoundingBox();
            if (center.getX() >= bounds.minX() - exclusion
                    && center.getX() <= bounds.maxX() + exclusion
                    && center.getZ() >= bounds.minZ() - exclusion
                    && center.getZ() <= bounds.maxZ() + exclusion) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static Structure getFrozenTownStructure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(FROZEN_TOWN.location());
    }
}
