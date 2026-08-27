package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * Advances Hearth clocks from world time without resolving or loading their chunks.
 */
public final class HearthMaturationManager {
    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private HearthMaturationManager() {
    }

    public static void tick(ServerLevel overworld, ApocalypseState apocalypse) {
        long gameTime = overworld.getGameTime();
        if (gameTime % UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }

        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(overworld.getServer());
        if (!state.selectionComplete()) {
            return;
        }

        boolean active = HearthSelectionPolicy.isSelectionEligible(
                apocalypse.getApocalypseTicks(), apocalypse.getTotalDays());
        logTransitions(state.updateMaturation(gameTime, active));
    }

    public static ReturnedHearthSavedData.MaturationResult advanceForDebug(
            ServerLevel overworld, long ticks) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(overworld.getServer());
        ReturnedHearthSavedData.MaturationResult result = state.advanceMaturationForDebug(
                ticks, overworld.getGameTime());
        logTransitions(result);
        return result;
    }

    private static void logTransitions(ReturnedHearthSavedData.MaturationResult result) {
        for (ReturnedHearthSavedData.StageTransition transition : result.transitions()) {
            FrozenDawn.LOGGER.info("Returned {} Hearth {} matured from {} to {}",
                    transition.type().name().toLowerCase(Locale.ROOT),
                    transition.hearthId().toString().substring(0, 8),
                    transition.previousStage().name().toLowerCase(Locale.ROOT),
                    transition.currentStage().name().toLowerCase(Locale.ROOT));
        }
    }
}
