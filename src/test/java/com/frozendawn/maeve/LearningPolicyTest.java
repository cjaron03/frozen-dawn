package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LearningPolicyTest {
    static final UUID PLAYER = new UUID(0, 1), ACTOR = new UUID(0, 2);
    static final String DIM = "minecraft:overworld";

    private static MaeveDirector.PositionDirective directive(long now, String dimension) {
        var evidence = new MaeveDirector.EvidenceSnapshot(ACTOR, UUID.randomUUID(), dimension, BlockPos.ZERO, now - 1000, "OBSERVED", true);
        return new MaeveDirector.PositionDirective(PLAYER, ACTOR, UUID.randomUUID(), BeliefStore.PURSUIT, .8, evidence,
                new BlockPos(4, 1, 0), null, 3, now, -1, -1, -1, null, null);
    }
    private static void use(StrategyPerformance memory, long now, boolean success) {
        memory.start(directive(now, DIM)); memory.arrived(now + 10);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, now + 20, 4, success);
        memory.clock(now + 400); memory.finish("TIME_COMPLETE");
    }

    @Test void continuousWithdrawalDistinguishesPursuitFromWaiting() {
        for (boolean follow : List.of(true, false)) {
            var window = new WithdrawalWindow(new Vec3(6, 0, 0), Vec3.ZERO, 0);
            for (int t = 10; t <= 100; t += 10) {
                var result = window.sample(new Vec3(6 + t * .03, 0, 0), new Vec3(follow ? t * .03 : 0, 0, 0), t, true);
                assertEquals(t < 100 ? WithdrawalWindow.Result.WAITING : follow ? WithdrawalWindow.Result.FOLLOWED : WithdrawalWindow.Result.NOT_FOLLOWED, result);
            }
        }
    }
    @Test void OcclusionTeleportsMissingTicksAndStationaryActorAreInconclusive() {
        var start = new Vec3(6, 0, 0);
        assertEquals(WithdrawalWindow.Result.UNKNOWN, new WithdrawalWindow(start, Vec3.ZERO, 0).sample(start, Vec3.ZERO, 10, false));
        assertEquals(WithdrawalWindow.Result.UNKNOWN, new WithdrawalWindow(start, Vec3.ZERO, 0).sample(start, new Vec3(8, 0, 0), 10, true));
        assertEquals(WithdrawalWindow.Result.UNKNOWN, new WithdrawalWindow(start, Vec3.ZERO, 0).sample(start, Vec3.ZERO, 30, true));
        var still = new WithdrawalWindow(start, Vec3.ZERO, 0);
        for (int t = 10; t < 100; t += 10) still.sample(start, Vec3.ZERO, t, true);
        assertEquals(WithdrawalWindow.Result.UNKNOWN, still.sample(start, Vec3.ZERO, 100, true));
    }
    @Test void diagonalConvergenceCountsButLateralDepartureAndStandingStillDoNot() {
        for (int side : new int[]{-1, 1}) {
            var actor = new Vec3(714.5, 101, 705.5);
            var player = new Vec3(720.5, 101, 705.5 + side * 10);
            var window = new WithdrawalWindow(actor, player, 0);
            for (int t = 10; t <= 100; t += 10) {
                var result = window.sample(actor.add(t * .15, 0, 0), player.add(t * .026, 0, -side * t * .10), t, true);
                assertEquals(t < 100 ? WithdrawalWindow.Result.WAITING : WithdrawalWindow.Result.FOLLOWED, result,
                        "A player can follow by closing onto the scout's path from either side");
            }
        }
        var window = new WithdrawalWindow(new Vec3(6, 0, 0), Vec3.ZERO, 0);
        for (int t = 10; t <= 100; t += 10) {
            var result = window.sample(new Vec3(6 + t * .03, 0, 0), new Vec3(t * .03, 0, t * .06), t, true);
            assertEquals(t < 100 ? WithdrawalWindow.Result.WAITING : WithdrawalWindow.Result.NOT_FOLLOWED, result,
                    "Forward movement while diverging sideways is not following");
        }
    }
    @Test void twoBadTradesReduceLaterUtilityAndDeferTwoEncountersThenPermitRetry() {
        var memory = new StrategyPerformance(); memory.begin(1);
        use(memory, 10, false);
        assertEquals(1, memory.multiplier(BeliefStore.PURSUIT, DIM), "Results cannot change the encounter that supplied them");
        memory.begin(1000); assertTrue(memory.multiplier(BeliefStore.PURSUIT, DIM) < 1);
        use(memory, 1010, false);
        memory.begin(2000); assertTrue(memory.deferred(BeliefStore.PURSUIT, DIM));
        memory = StrategyPerformance.load(memory.save());
        assertTrue(memory.deferred(BeliefStore.PURSUIT, DIM), "Reload preserves frozen deferral");
        memory.begin(3000); assertTrue(memory.deferred(BeliefStore.PURSUIT, DIM));
        memory.begin(4000); assertFalse(memory.deferred(BeliefStore.PURSUIT, DIM));
        double before = memory.multiplier(BeliefStore.PURSUIT, DIM);
        use(memory, 4010, true); memory.begin(5000);
        assertTrue(memory.multiplier(BeliefStore.PURSUIT, DIM) > before);
    }
    @Test void noContactWrongActorWrongPlayerAndReloadCannotEarnSuccess() {
        var memory = new StrategyPerformance(); memory.begin(1); memory.start(directive(10, DIM));
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 11, 9, true); // before arrival
        memory.arrived(20);
        memory.damage(UUID.randomUUID(), PLAYER, DIM, BlockPos.ZERO, 21, 9, true);
        memory.damage(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, 22, 9, true);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 23, Float.NaN, true);
        memory.finish("TIME_COMPLETE");
        var row = memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(0, row.getInt("successes")); assertEquals(1, row.getInt("unknown"));
        memory.start(directive(1000, DIM)); memory.arrived(1010);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 1020, 9, true);
        var loaded = StrategyPerformance.load(memory.save());
        row = loaded.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(0, row.getInt("successes")); assertEquals(2, row.getInt("unknown"));
        assertEquals(loaded.save(), StrategyPerformance.load(loaded.save()).save());
    }
    @Test void resultMemoryIsBoundedAndStalePerformanceReturnsTowardNeutral() {
        var memory = new StrategyPerformance();
        for (int i = 0; i < 20; i++) { memory.begin(i * 1000L); use(memory, i * 1000L + 10, false); }
        var row = memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(20, row.getInt("uses")); assertEquals(8, row.getList("results", Tag.TAG_COMPOUND).size());
        memory.begin(20000); double recent = memory.multiplier(BeliefStore.PURSUIT, DIM);
        memory.begin(20000 + BeliefPolicy.STALE_AFTER + BeliefPolicy.HALF_LIFE * 10);
        assertTrue(memory.multiplier(BeliefStore.PURSUIT, DIM) > recent);
        for (int i = 0; i < 12; i++) { memory.start(directive(1000000 + i, "test:dimension_" + i)); memory.finish("TIME_COMPLETE"); }
        var contexts = memory.save().getList("contexts", Tag.TAG_COMPOUND);
        assertEquals(8, contexts.size()); assertEquals("test:dimension_4", contexts.getCompound(0).getString("dimension"));
    }
    @Test void observedExecutorDeathCannotBecomeSuccessFromAnEarlierGoodTrade() {
        var memory = new StrategyPerformance(); memory.begin(1); memory.start(directive(10, DIM)); memory.arrived(20);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 21, 12, true);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 22, 1, false);
        memory.finish("OWNER_KILLED");
        var row = memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(1, row.getInt("failures")); assertEquals(0, row.getInt("successes"));
    }
    @Test void failedCounterYieldsToAnotherSafeEligibleCounterWithoutChangingRecoveryOrdering() {
        var policy = new CommitmentPolicy();
        var ranged = new Belief(BeliefStore.RANGED); var pursuit = new Belief(BeliefStore.PURSUIT);
        for (int i = 0; i < 4; i++) for (var belief : List.of(ranged, pursuit))
            belief.record(new ObservedEvidence(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, i * 1000, "OBSERVED", true));
        for (int i = 0; i < 2; i++) { policy.begin(UUID.randomUUID(), List.of(ranged, pursuit), 5000 + i * 1000); use(policy.performance(), 5010 + i * 1000, false); }
        policy.begin(UUID.randomUUID(), List.of(ranged, pursuit), 8000);
        assertTrue(policy.choose(PLAYER, ACTOR, List.of(new MaeveDirector.PositionCandidate(BeliefStore.PURSUIT, BlockPos.ZERO, null, 0),
                new MaeveDirector.PositionCandidate(BeliefStore.RANGED, BlockPos.ZERO, null, 2)), 8000));
        assertEquals(BeliefStore.RANGED, policy.selected().pattern());
        assertTrue(policy.alternatives().stream().anyMatch(s -> s.contains("RECENT_COUNTER_FAILURES")));
    }
    @Test void versionThreeWorldsLoadEmptyPerformanceAndErasureRemovesResults() {
        var legacy = new CompoundTag(); legacy.putInt("dataVersion", 3); legacy.putBoolean("activated", true);
        var saved = MaeveSavedData.load(legacy, null); assertEquals(0, saved.store().size());
        saved.store().record(PLAYER, ACTOR, DIM, BlockPos.ZERO, 1, BeliefStore.PURSUIT, true, "WITHDRAWAL_FOLLOWED");
        use(saved.store().commitment(PLAYER).performance(), 10, false);
        saved.erase(); var erased = saved.save(new CompoundTag(), null);
        assertFalse(erased.contains("beliefs")); saved.synchronize(false, true); assertEquals(0, saved.store().size());
    }
}
