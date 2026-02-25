package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent world data tracking the apocalypse progression.
 * Saved with the overworld so it persists across server restarts.
 */
public class ApocalypseState extends SavedData {
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_apocalypse";

    private long apocalypseTicks;
    private boolean initialized;

    public ApocalypseState() {
        this.apocalypseTicks = 0;
        this.initialized = false;
    }

    /**
     * Get or create the ApocalypseState for this server.
     * Always stored in the overworld's data storage.
     */
    public static ApocalypseState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ApocalypseState::new, ApocalypseState::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static ApocalypseState load(CompoundTag tag, HolderLookup.Provider registries) {
        ApocalypseState state = new ApocalypseState();
        state.apocalypseTicks = tag.getLong("apocalypseTicks");
        state.initialized = tag.getBoolean("initialized");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("apocalypseTicks", apocalypseTicks);
        tag.putBoolean("initialized", initialized);
        return tag;
    }

    /**
     * Called once per server tick to sync apocalypse with world time.
     */
    public void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (!initialized) {
            initialized = true;
            // Apply starting day offset from config
            int startDay = FrozenDawnConfig.STARTING_DAY.get();
            if (startDay > 0 && overworld.getDayTime() < (long) startDay * 24000L) {
                overworld.setDayTime((long) startDay * 24000L);
                FrozenDawn.LOGGER.info("Apocalypse fast-forwarded to day {}", startDay);
            }
            setDirty();
        }

        if (!FrozenDawnConfig.PAUSE_PROGRESSION.get()) {
            // Sync with world time — sleeping advances the apocalypse
            apocalypseTicks = overworld.getDayTime();
        }
        // When paused, apocalypseTicks stays frozen at the last value
    }

    // --- Getters ---

    public long getApocalypseTicks() {
        return apocalypseTicks;
    }

    public int getCurrentDay() {
        return (int) (apocalypseTicks / 24000L);
    }

    public int getTotalDays() {
        return FrozenDawnConfig.TOTAL_DAYS.get();
    }

    public int getPhase() {
        return PhaseManager.getPhase(getCurrentDay(), getTotalDays());
    }

    public float getProgress() {
        return PhaseManager.getProgress(getCurrentDay(), getTotalDays());
    }

    public float getTemperatureOffset() {
        return PhaseManager.getTemperatureOffset(getCurrentDay(), getTotalDays());
    }

    public float getSunScale() {
        return PhaseManager.getSunScale(getCurrentDay(), getTotalDays());
    }

    public float getSunBrightness() {
        return PhaseManager.getSunBrightness(getCurrentDay(), getTotalDays());
    }

    public float getSkyLight() {
        return PhaseManager.getSkyLight(getCurrentDay(), getTotalDays());
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Set the apocalypse tick count directly (used by admin commands).
     * Also sets world time to keep everything in sync.
     */
    public void setApocalypseTicks(long ticks, MinecraftServer server) {
        this.apocalypseTicks = Math.max(0, ticks);
        server.overworld().setDayTime(this.apocalypseTicks);
        this.initialized = true;
        setDirty();
    }
}
