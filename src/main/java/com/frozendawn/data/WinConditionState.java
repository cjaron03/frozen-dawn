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
 * Persistent world data for the endgame progression.
 * Tracks the satellite/transponder chain plus the final-content narrative flags.
 */
public class WinConditionState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_win_condition";

    private BlockPos satellitePos;
    private boolean satellitePlaced;
    private boolean schematicUnlocked;
    private boolean conspiracyDiscovered;
    private boolean rocketBlueprintUnlocked;
    private boolean martianReplySent;
    private BlockPos rocketPadCenter;
    private boolean rocketAssembled;
    private int rocketFuelCellsLoaded;
    private boolean launchInProgress;
    private long launchSequenceStartTick;
    private boolean launchCompleted;
    private boolean endingTriggered;

    public WinConditionState() {
        this.satellitePos = null;
        this.satellitePlaced = false;
        this.schematicUnlocked = false;
        this.conspiracyDiscovered = false;
        this.rocketBlueprintUnlocked = false;
        this.martianReplySent = false;
        this.rocketPadCenter = null;
        this.rocketAssembled = false;
        this.rocketFuelCellsLoaded = 0;
        this.launchInProgress = false;
        this.launchSequenceStartTick = 0L;
        this.launchCompleted = false;
        this.endingTriggered = false;
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
        state.conspiracyDiscovered = tag.getBoolean("conspiracyDiscovered");
        state.rocketBlueprintUnlocked = tag.getBoolean("rocketBlueprintUnlocked");
        state.martianReplySent = tag.getBoolean("martianReplySent");
        if (tag.contains("rocketPadCenterX")) {
            state.rocketPadCenter = new BlockPos(
                    tag.getInt("rocketPadCenterX"),
                    tag.getInt("rocketPadCenterY"),
                    tag.getInt("rocketPadCenterZ")
            );
        }
        state.rocketAssembled = tag.getBoolean("rocketAssembled");
        state.rocketFuelCellsLoaded = tag.getInt("rocketFuelCellsLoaded");
        state.launchInProgress = tag.getBoolean("launchInProgress");
        state.launchSequenceStartTick = tag.getLong("launchSequenceStartTick");
        state.launchCompleted = tag.getBoolean("launchCompleted");
        state.endingTriggered = tag.getBoolean("endingTriggered");
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
        tag.putBoolean("conspiracyDiscovered", conspiracyDiscovered);
        tag.putBoolean("rocketBlueprintUnlocked", rocketBlueprintUnlocked);
        tag.putBoolean("martianReplySent", martianReplySent);
        if (rocketPadCenter != null) {
            tag.putInt("rocketPadCenterX", rocketPadCenter.getX());
            tag.putInt("rocketPadCenterY", rocketPadCenter.getY());
            tag.putInt("rocketPadCenterZ", rocketPadCenter.getZ());
        }
        tag.putBoolean("rocketAssembled", rocketAssembled);
        tag.putInt("rocketFuelCellsLoaded", rocketFuelCellsLoaded);
        tag.putBoolean("launchInProgress", launchInProgress);
        tag.putLong("launchSequenceStartTick", launchSequenceStartTick);
        tag.putBoolean("launchCompleted", launchCompleted);
        tag.putBoolean("endingTriggered", endingTriggered);
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

    public boolean isConspiracyDiscovered() {
        return conspiracyDiscovered;
    }

    public void setConspiracyDiscovered(boolean discovered) {
        this.conspiracyDiscovered = discovered;
        setDirty();
    }

    public boolean isRocketBlueprintUnlocked() {
        return rocketBlueprintUnlocked;
    }

    public void setRocketBlueprintUnlocked(boolean unlocked) {
        this.rocketBlueprintUnlocked = unlocked;
        setDirty();
    }

    public boolean isMartianReplySent() {
        return martianReplySent;
    }

    public void setMartianReplySent(boolean sent) {
        this.martianReplySent = sent;
        setDirty();
    }

    public BlockPos getRocketPadCenter() {
        return rocketPadCenter;
    }

    public void setRocketPadCenter(BlockPos rocketPadCenter) {
        this.rocketPadCenter = rocketPadCenter;
        setDirty();
    }

    public boolean isRocketAssembled() {
        return rocketAssembled;
    }

    public void setRocketAssembled(boolean rocketAssembled) {
        this.rocketAssembled = rocketAssembled;
        setDirty();
    }

    public int getRocketFuelCellsLoaded() {
        return rocketFuelCellsLoaded;
    }

    public void setRocketFuelCellsLoaded(int rocketFuelCellsLoaded) {
        this.rocketFuelCellsLoaded = rocketFuelCellsLoaded;
        setDirty();
    }

    public boolean isLaunchInProgress() {
        return launchInProgress;
    }

    public void setLaunchInProgress(boolean launchInProgress) {
        this.launchInProgress = launchInProgress;
        setDirty();
    }

    public long getLaunchSequenceStartTick() {
        return launchSequenceStartTick;
    }

    public void setLaunchSequenceStartTick(long launchSequenceStartTick) {
        this.launchSequenceStartTick = launchSequenceStartTick;
        setDirty();
    }

    public boolean isLaunchCompleted() {
        return launchCompleted;
    }

    public void setLaunchCompleted(boolean completed) {
        this.launchCompleted = completed;
        setDirty();
    }

    public void clearRocketAssembly() {
        this.rocketPadCenter = null;
        this.rocketAssembled = false;
        this.rocketFuelCellsLoaded = 0;
        this.launchInProgress = false;
        this.launchSequenceStartTick = 0L;
        setDirty();
    }

    public boolean isEndingTriggered() {
        return endingTriggered;
    }

    public void setEndingTriggered(boolean triggered) {
        this.endingTriggered = triggered;
        setDirty();
    }
}
