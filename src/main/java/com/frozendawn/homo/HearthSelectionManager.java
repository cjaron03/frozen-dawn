package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Selects rare Hearth identities after late Phase 6 without loading their chunks.
 */
public final class HearthSelectionManager {
    private static final long SELECTION_CHECK_INTERVAL_TICKS = 20L;

    private HearthSelectionManager() {
    }

    public static void tick(ServerLevel overworld, ApocalypseState apocalypse) {
        if (overworld.getGameTime() % SELECTION_CHECK_INTERVAL_TICKS != 0L) {
            return;
        }

        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(overworld.getServer());
        if (state.selectionComplete()
                || !HearthSelectionPolicy.isSelectionEligible(
                        apocalypse.getApocalypseTicks(), apocalypse.getTotalDays())) {
            return;
        }

        state.transponderAnchor().ifPresent(anchor -> select(overworld, state, anchor));
    }

    public static SelectionResult forceSelect(ServerLevel overworld, BlockPos fallbackAnchor) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(overworld.getServer());
        if (state.selectionComplete()) {
            return new SelectionResult(false, state.transponderAnchor().orElse(fallbackAnchor), state.hearths());
        }

        state.rememberTransponderAnchor(fallbackAnchor);
        BlockPos anchor = state.transponderAnchor().orElse(fallbackAnchor);
        boolean selected = select(overworld, state, anchor);
        return new SelectionResult(selected, anchor, state.hearths());
    }

    private static boolean select(ServerLevel overworld, ReturnedHearthSavedData state, BlockPos anchor) {
        HearthSelectionPolicy.SelectionPlan plan = HearthSelectionPolicy.createPlan(overworld.getSeed(), anchor);
        if (!state.applySelectionPlan(plan, overworld.getGameTime())) {
            return false;
        }

        FrozenDawn.LOGGER.info("Returned Hearth sites selected around transponder ({}, {}, {}): major=({}, {}), minor={}",
                anchor.getX(), anchor.getY(), anchor.getZ(),
                plan.major().center().getX(), plan.major().center().getZ(),
                plan.minor()
                        .map(candidate -> "(" + candidate.center().getX() + ", " + candidate.center().getZ() + ")")
                        .orElse("none"));
        return true;
    }

    public record SelectionResult(boolean selected, BlockPos anchor,
                                  List<ReturnedHearthSavedData.HearthRecord> hearths) {
    }
}
