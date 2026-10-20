package com.frozendawn.data;

import com.frozendawn.phase.PhaseOneWeatherPolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;

/** Remaining simulation ticks are saved: logging out neither restarts nor advances a weather window. */
public final class PhaseOneWeatherState extends SavedData {
    public static final String NAME = "frozendawn_phase_one_weather";
    public static final Factory<PhaseOneWeatherState> FACTORY = new Factory<>(
            PhaseOneWeatherState::new, PhaseOneWeatherState::load, DataFixTypes.LEVEL);
    private boolean active;
    private boolean raining;
    private boolean thundering;
    private int remainingTicks;
    private long sequence;
    private long lastTick = Long.MIN_VALUE;

    public static PhaseOneWeatherState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static PhaseOneWeatherState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new PhaseOneWeatherState();
        state.active = tag.getBoolean("active");
        state.raining = tag.getBoolean("raining");
        state.thundering = state.raining && tag.getBoolean("thundering");
        state.remainingTicks = Math.clamp(tag.getInt("remainingTicks"), 0, 14_400);
        state.sequence = Math.max(0, tag.getLong("sequence"));
        state.lastTick = tag.contains("lastTick") ? tag.getLong("lastTick") : Long.MIN_VALUE;
        return state;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("active", active);
        tag.putBoolean("raining", raining);
        tag.putBoolean("thundering", thundering);
        tag.putInt("remainingTicks", remainingTicks);
        tag.putLong("sequence", sequence);
        tag.putLong("lastTick", lastTick);
        return tag;
    }

    public void tick(ServerLevel level, float progress) {
        long now = level.getGameTime();
        if (lastTick == now) return;
        lastTick = now;
        if (!level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) return;
        if (!active || level.getLevelData().isRaining() != raining || level.getLevelData().isThundering() != thundering) {
            // Adopt sleep/commands and incoming weather on entry, then use the phase 1 duration.
            begin(level, progress, level.getLevelData().isRaining(), level.getLevelData().isRaining() && level.getLevelData().isThundering());
        } else if (--remainingTicks <= 0) {
            boolean nextRain = !raining;
            begin(level, progress, nextRain, nextRain && PhaseOneWeatherPolicy.thunder(
                    PhaseOneWeatherPolicy.roll(level.getSeed(), sequence, 0x5448554e444552L)));
        } else {
            setDirty();
        }
    }

    private void begin(ServerLevel level, float progress, boolean rain, boolean thunder) {
        active = true;
        raining = rain;
        thundering = rain && thunder;
        remainingTicks = PhaseOneWeatherPolicy.durationTicks(progress, rain,
                PhaseOneWeatherPolicy.roll(level.getSeed(), sequence++, 0x57454154484552L));
        // A short timer guard lets this saved schedule switch the weather before vanilla rerolls it.
        level.setWeatherParameters(rain ? 0 : remainingTicks + 20, rain ? remainingTicks + 20 : 0,
                raining, thundering);
        setDirty();
    }

    public void leave() {
        if (active) { active = false; lastTick = Long.MIN_VALUE; setDirty(); }
    }

    public boolean active() { return active; }
    public int remainingTicks() { return remainingTicks; }
    public long sequence() { return sequence; }
}
