package com.frozendawn.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.network.ApocalypseDataPayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/**
 * Applies Frozen Dawn difficulty presets and keeps the shared world state in sync.
 */
public final class DifficultyPresetManager {

    private DifficultyPresetManager() {}

    public static boolean applyPreset(MinecraftServer server, ConfigPresets preset, boolean lockSelection, boolean allowOverride) {
        ApocalypseState state = ApocalypseState.get(server);
        if (state.isDifficultyLocked() && !allowOverride) {
            return false;
        }

        preset.apply();
        state.setPresetName(preset.name());
        if (lockSelection) {
            state.setDifficultyLocked(true);
        }

        persistConfigOverrides();
        syncToClients(server, state);
        return true;
    }

    public static void syncToClients(MinecraftServer server, ApocalypseState state) {
        WinConditionState winState = WinConditionState.get(server);
        PacketDistributor.sendToAllPlayers(new ApocalypseDataPayload(
                state.getPhase(),
                state.getProgress(),
                state.getTemperatureOffset(),
                state.getSunScale(),
                state.getSunBrightness(),
                state.getSkyLight(),
                winState.isSchematicUnlocked()
        ));
    }

    public static void persistConfigOverrides() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("frozendawn-common.toml");
        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().build()) {
            config.load();
            config.set("general.totalDays", FrozenDawnConfig.TOTAL_DAYS.get());
            config.set("general.pauseProgression", FrozenDawnConfig.PAUSE_PROGRESSION.get());
            config.set("temperature.basePhase5Temp", FrozenDawnConfig.BASE_PHASE5_TEMP.get());
            config.set("temperature.geothermalStrength", FrozenDawnConfig.GEOTHERMAL_STRENGTH.get());
            config.set("temperature.heatSourceMultiplier", FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get());
            config.set("gameplay.snowAccumulationRate", FrozenDawnConfig.SNOW_ACCUMULATION_RATE.get());
            config.set("gameplay.broadcastTicks", FrozenDawnConfig.BROADCAST_TICKS.get());
            config.set("gameplay.sanitySpeedMultiplier", FrozenDawnConfig.SANITY_SPEED_MULTIPLIER.get());
            config.set("gameplay.mobSpawnMultiplier", FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get());
            config.save();
        } catch (Exception e) {
            FrozenDawn.LOGGER.error("Failed to persist Frozen Dawn config overrides to {}", configPath, e);
        }
    }
}
