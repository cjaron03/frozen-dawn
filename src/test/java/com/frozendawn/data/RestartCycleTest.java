package com.frozendawn.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restart behaviour for the persisted 2.0 subsystems.
 *
 * <p>Every check here runs <em>two</em> save/load cycles rather than one. A single cycle hides a
 * whole class of defect: a field that quietly falls back to its default on load still looks
 * correct immediately after loading, and only diverges once that defaulted value is written back
 * out. Comparing cycle two against cycle one is what makes that visible.
 *
 * <p>The one-shot checks matter for a different reason. A flag that round-trips correctly is
 * still a shipped bug if nothing consults it on the restart path, because the reward re-grants or
 * the structure gets built again. So those are asserted through the accessors the runtime
 * actually calls, not by reading the tag back.
 */
class RestartCycleTest {

    @Test
    void apocalypseCampaignPhaseSurvivesTwoRestarts() {
        assertStableAcrossRestarts(
                ApocalypseState::new,
                (state, tag) -> state.save(tag, null),
                tag -> ApocalypseState.load(tag, null),
                state -> {
                    state.setPresetName("harsh");
                    state.setDifficultyLocked(true);
                    state.recordFrozenBlocks(4096L);
                });
    }

    @Test
    void winConditionProgressSurvivesTwoRestarts() {
        assertStableAcrossRestarts(
                WinConditionState::new,
                (state, tag) -> state.save(tag, null),
                tag -> WinConditionState.load(tag, null),
                state -> {
                    state.setSatellitePlaced(true);
                    state.setSchematicUnlocked(true);
                    state.setConspiracyDiscovered(true);
                    state.setRocketBlueprintUnlocked(true);
                    state.setMartianReplySent(true);
                    state.setRocketPadCenter(new BlockPos(128, 71, -256));
                    state.setRocketAssembled(true);
                    state.setRocketFuelCellsLoaded(3);
                    state.setLaunchSequenceStartTick(9_001L);
                    state.setLaunchCompleted(true);
                });
    }

    @Test
    void structurePlanningSurvivesTwoRestarts() {
        assertStableAcrossRestarts(
                OrsaStructureState::new,
                (state, tag) -> state.save(tag, null),
                tag -> OrsaStructureState.load(tag, null),
                state -> {
                    state.markCampEvaluated(3, -7);
                    state.markCampBuilt(3, -7);
                    state.setBlastPitTargetPos(new BlockPos(-1945, 60, -246));
                    state.setBlastPitPos(new BlockPos(-1945, 60, -246));
                    state.setBlastPitPlaced(true);
                    state.addPlannedTower(1L, 0, new BlockPos(3912, 70, 0));
                    state.setTowerPlaced(1L, new BlockPos(3912, 70, 0));
                    state.setTowerArchitectTriggered(1L, true);
                });
    }

    @Test
    void catchUpBacklogSurvivesTwoRestarts() {
        assertStableAcrossRestarts(
                ChunkEpochState::new,
                (state, tag) -> state.save(tag, null),
                tag -> ChunkEpochState.load(tag, null),
                state -> {
                    // One chunk mid-transform and one finished: the backlog has to remember both,
                    // or a restart either redoes completed work or abandons pending work.
                    state.getOrCreate(0, 0).begin(4, 100, 6, 0.5F);
                    state.getOrCreate(0, 0).advance(1, 320, 4, 71, 1_000L);
                    state.getOrCreate(1, 0).complete(4, 100, 6, 1.0F, 2_000L);
                });
    }

    @Test
    void placedBlockOwnershipSurvivesTwoRestarts() {
        assertStableAcrossRestarts(
                PlayerPlacedBlockTracker::new,
                (state, tag) -> state.save(tag, null),
                tag -> PlayerPlacedBlockTracker.load(tag, null),
                state -> {
                    state.markPlaced(new BlockPos(10, 64, 10));
                    state.markPlaced(new BlockPos(-30, 12, 400));
                    state.markRemoved(new BlockPos(10, 64, 10));
                });
    }

    @Test
    void oneShotRewardsAndStructuresStayDoneAfterTwoRestarts() {
        WinConditionState original = new WinConditionState();
        original.setSatellitePlaced(true);
        original.setLaunchCompleted(true);

        WinConditionState restarted = restartTwice(
                original,
                (state, tag) -> state.save(tag, null),
                tag -> WinConditionState.load(tag, null));

        // Read through the accessors the runtime gates on. If either reset, the satellite gets
        // built a second time or the ending re-fires on every world load.
        assertTrue(restarted.isSatellitePlaced(),
                "satellite placement forgot itself across restarts and would be rebuilt");
        assertTrue(restarted.isLaunchCompleted(),
                "launch completion forgot itself across restarts and the ending would re-fire");
    }

    @Test
    void completedCatchUpIsNotQueuedAgainAfterTwoRestarts() {
        ChunkEpochState original = new ChunkEpochState();
        original.getOrCreate(5, 5).complete(4, 100, 6, 1.0F, 1_000L);

        ChunkEpochState restarted = restartTwice(
                original,
                (state, tag) -> state.save(tag, null),
                tag -> ChunkEpochState.load(tag, null));

        assertTrue(restarted.get(5, 5).complete(),
                "a finished chunk came back incomplete and would be transformed twice");
        assertEquals(1, restarted.recordCount(),
                "restarting duplicated chunk epoch records");
    }

    @Test
    void activeSuitDamageSurvivesTwoRestarts() {
        // Suit damage is a data attachment on the player, not SavedData, so it serialises through
        // a different contract and would be missed entirely by the SavedData checks above.
        assertAttachmentStableAcrossRestarts(SuitIntegrity::new, suit -> {
            suit.setPunctures(3);
            suit.setO2Ticks(1_200);
            suit.setGraceTicks(40);
            suit.setPatchTicks(80);
            suit.setTemporarySeals(2);
            suit.setTemporarySealTicks(600);
        });
    }

    @Test
    void activeCognitiveLoadSurvivesTwoRestarts() {
        assertAttachmentStableAcrossRestarts(CognitiveLoadState::new, mind -> {
            mind.setLoad(0.62F);
            mind.setTakeoverTicks(140);
            mind.setTerminalTakeover(true);
            mind.setBreakoutTicks(12.5F);
        });
    }

    /** Attachment flavour of the same double-cycle property. */
    private static <T extends INBTSerializable<CompoundTag>> void assertAttachmentStableAcrossRestarts(
            Supplier<T> factory, Consumer<T> populate) {
        T original = factory.get();
        populate.accept(original);

        CompoundTag firstCycle = original.serializeNBT(null);
        T reloaded = factory.get();
        reloaded.deserializeNBT(null, firstCycle);
        CompoundTag secondCycle = reloaded.serializeNBT(null);

        assertEquals(firstCycle, secondCycle,
                "attachment state changed between restart cycles");

        T reloadedAgain = factory.get();
        reloadedAgain.deserializeNBT(null, secondCycle);
        assertEquals(secondCycle, reloadedAgain.serializeNBT(null),
                "attachment state was still drifting on the third restart cycle");
    }

    /**
     * Saves, loads, saves again, and asserts the second cycle produced exactly what the first
     * did. Anything lossy shows up as a difference between the two tags.
     */
    private static <T> void assertStableAcrossRestarts(
            Supplier<T> factory,
            BiFunction<T, CompoundTag, CompoundTag> save,
            Function<CompoundTag, T> load,
            Consumer<T> populate) {
        T original = factory.get();
        populate.accept(original);

        CompoundTag firstCycle = save.apply(original, new CompoundTag());
        T reloaded = load.apply(firstCycle);
        CompoundTag secondCycle = save.apply(reloaded, new CompoundTag());

        assertEquals(firstCycle, secondCycle,
                "state changed between restart cycles; something is not round-tripping");

        // A third cycle costs nothing and catches state that oscillates rather than decays.
        T reloadedAgain = load.apply(secondCycle);
        assertEquals(secondCycle, save.apply(reloadedAgain, new CompoundTag()),
                "state was still drifting on the third restart cycle");
    }

    private static <T> T restartTwice(
            T original,
            BiFunction<T, CompoundTag, CompoundTag> save,
            Function<CompoundTag, T> load) {
        T once = load.apply(save.apply(original, new CompoundTag()));
        return load.apply(save.apply(once, new CompoundTag()));
    }
}
