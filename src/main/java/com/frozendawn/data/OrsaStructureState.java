package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent world data for ORSA landmark structures.
 * Keeps guaranteed landmark coordinates stable across restarts.
 */
public final class OrsaStructureState extends SavedData {

    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_orsa_structures";

    private BlockPos blastPitPos;
    private boolean blastPitPlaced;

    public OrsaStructureState() {
    }

    public static OrsaStructureState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OrsaStructureState::new, OrsaStructureState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static OrsaStructureState load(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
        OrsaStructureState state = new OrsaStructureState();
        if (tag.contains("blastPitX")) {
            state.blastPitPos = new BlockPos(
                    tag.getInt("blastPitX"),
                    tag.getInt("blastPitY"),
                    tag.getInt("blastPitZ")
            );
        }
        state.blastPitPlaced = tag.getBoolean("blastPitPlaced");
        return state;
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, HolderLookup.Provider registries) {
        if (blastPitPos != null) {
            tag.putInt("blastPitX", blastPitPos.getX());
            tag.putInt("blastPitY", blastPitPos.getY());
            tag.putInt("blastPitZ", blastPitPos.getZ());
        }
        tag.putBoolean("blastPitPlaced", blastPitPlaced);
        return tag;
    }

    /**
     * Pick the guaranteed Blast Pit target if it has not been chosen yet.
     * The target is 1000-3000 blocks from world spawn.
     */
    public void initBlastPitPosition(ServerLevel overworld) {
        if (blastPitPos != null) {
            if (!blastPitPlaced && !isExactPlains(overworld, new BlockPos(blastPitPos.getX(), overworld.getSeaLevel(), blastPitPos.getZ()))) {
                FrozenDawn.LOGGER.warn("Discarding saved non-plains Blast Pit target at ({}, {}, {}); rerolling",
                        blastPitPos.getX(), blastPitPos.getY(), blastPitPos.getZ());
                blastPitPos = null;
                setDirty();
            } else {
                return;
            }
        }
        blastPitPos = chooseBlastPitTarget(overworld);
        if (blastPitPos != null) {
            setDirty();
            FrozenDawn.LOGGER.info("Blast Pit target chosen at ({}, {}), distance {} from spawn",
                    blastPitPos.getX(), blastPitPos.getZ(),
                    (int) Math.sqrt(overworld.getSharedSpawnPos().distSqr(blastPitPos)));
        } else {
            FrozenDawn.LOGGER.warn("Failed to choose a plains-only Blast Pit target; will retry next tick");
        }
    }

    public void rerollBlastPitPosition(ServerLevel overworld) {
        this.blastPitPos = null;
        this.blastPitPlaced = false;
        initBlastPitPosition(overworld);
    }

    private BlockPos chooseBlastPitTarget(ServerLevel overworld) {
        RandomSource random = overworld.getRandom();
        BlockPos spawn = overworld.getSharedSpawnPos();

        for (int attempt = 0; attempt < 192; attempt++) {
            int distance = 1000 + random.nextInt(2001);
            double angle = random.nextDouble() * Math.PI * 2.0;
            int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos candidate = new BlockPos(x, overworld.getSeaLevel(), z);
            if (isExactPlains(overworld, candidate)) {
                return new BlockPos(x, 0, z);
            }
        }

        return null;
    }

    private boolean isExactPlains(ServerLevel overworld, BlockPos pos) {
        return overworld.getBiome(pos).unwrapKey().map(key -> key.equals(Biomes.PLAINS)).orElse(false);
    }

    /**
     * Resolve the stored Y value to the world surface once the chunk is loaded.
     */
    public boolean resolveBlastPitY(ServerLevel overworld) {
        if (blastPitPos == null || blastPitPlaced) {
            return false;
        }
        if (blastPitPos.getY() != 0) {
            return true;
        }
        if (!overworld.isLoaded(blastPitPos)) {
            return false;
        }

        int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, blastPitPos.getX(), blastPitPos.getZ());
        blastPitPos = new BlockPos(blastPitPos.getX(), surfaceY, blastPitPos.getZ());
        setDirty();
        return true;
    }

    public BlockPos getBlastPitPos() {
        return blastPitPos;
    }

    public boolean isBlastPitPlaced() {
        return blastPitPlaced;
    }

    public void setBlastPitPos(BlockPos blastPitPos) {
        this.blastPitPos = blastPitPos;
        setDirty();
    }

    public void setBlastPitPlaced(boolean blastPitPlaced) {
        this.blastPitPlaced = blastPitPlaced;
        setDirty();
    }
}
