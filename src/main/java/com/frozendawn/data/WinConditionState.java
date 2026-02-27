package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent world data for the win condition system.
 * Tracks crashed satellite location, schematic unlock, and placement state.
 */
public class WinConditionState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_win_condition";

    private BlockPos satellitePos;
    private boolean satellitePlaced;
    private boolean schematicUnlocked;

    public WinConditionState() {
        this.satellitePos = null;
        this.satellitePlaced = false;
        this.schematicUnlocked = false;
    }

    public static WinConditionState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WinConditionState::new, WinConditionState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static WinConditionState load(CompoundTag tag, HolderLookup.Provider registries) {
        WinConditionState state = new WinConditionState();
        if (tag.contains("satelliteX")) {
            state.satellitePos = new BlockPos(
                    tag.getInt("satelliteX"),
                    tag.getInt("satelliteY"),
                    tag.getInt("satelliteZ")
            );
        }
        state.satellitePlaced = tag.getBoolean("satellitePlaced");
        state.schematicUnlocked = tag.getBoolean("schematicUnlocked");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (satellitePos != null) {
            tag.putInt("satelliteX", satellitePos.getX());
            tag.putInt("satelliteY", satellitePos.getY());
            tag.putInt("satelliteZ", satellitePos.getZ());
        }
        tag.putBoolean("satellitePlaced", satellitePlaced);
        tag.putBoolean("schematicUnlocked", schematicUnlocked);
        return tag;
    }

    /**
     * Choose satellite coordinates if not yet determined.
     * Called on first world tick. Picks a random surface position 500-2000 blocks from spawn.
     * Y coordinate is set to 0 as a placeholder — actual Y is resolved when the chunk loads.
     */
    public void initSatellitePosition(ServerLevel overworld) {
        if (satellitePos != null) return;
        if (!FrozenDawnConfig.ENABLE_WIN_CONDITION.get()) return;

        RandomSource random = overworld.getRandom();
        BlockPos spawn = overworld.getSharedSpawnPos();

        // Random distance 500-2000 blocks from spawn
        int distance = 500 + random.nextInt(1501);
        // Random angle
        double angle = random.nextDouble() * 2 * Math.PI;
        int x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
        int z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);

        // Y is placeholder — resolved to surface height when chunk loads
        satellitePos = new BlockPos(x, 0, z);
        FrozenDawn.LOGGER.info("Satellite target chosen at ({}, {}), distance {} from spawn", x, z, distance);
        setDirty();
    }

    /**
     * Try to resolve the satellite's Y coordinate to the actual surface height.
     * Returns true if the chunk is loaded and Y was resolved.
     */
    public boolean resolveSatelliteY(ServerLevel overworld) {
        if (satellitePos == null || satellitePlaced) return false;
        if (satellitePos.getY() != 0) return true; // Already resolved

        BlockPos checkPos = satellitePos;
        if (!overworld.isLoaded(checkPos)) return false;

        int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, checkPos.getX(), checkPos.getZ());
        satellitePos = new BlockPos(checkPos.getX(), surfaceY, checkPos.getZ());
        setDirty();
        return true;
    }

    // --- Getters and setters ---

    public BlockPos getSatellitePos() {
        return satellitePos;
    }

    public boolean isSatellitePlaced() {
        return satellitePlaced;
    }

    public void setSatellitePlaced(boolean placed) {
        this.satellitePlaced = placed;
        setDirty();
    }

    public boolean isSchematicUnlocked() {
        return schematicUnlocked;
    }

    public void setSchematicUnlocked(boolean unlocked) {
        this.schematicUnlocked = unlocked;
        setDirty();
    }
}
