package com.frozendawn.maeve;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConvergenceMemoryTest {
    static final String DIM = "minecraft:overworld";
    static final BlockPos ORIGIN = new BlockPos(0, 60, 0);
    static UUID id(int n) { return new UUID(0, n); }
    static Hotspot history(ConvergenceMemory m) {
        for (int i = 0; i < 6; i++) m.death(id(i + 1), DIM, ORIGIN, i / 2 * 640L, false);
        var h = m.at(DIM, ORIGIN); h.advance(1880); return h;
    }
    static ConvergenceGroup group(Hotspot h, long now) {
        return new ConvergenceGroup(UUID.randomUUID(), h, Map.of(id(100), ORIGIN.offset(40, 0, 0), id(101), ORIGIN.offset(-40, 0, 0)), now);
    }
    static void result(Hotspot h, String outcome, long now) { h.outcome(group(h, now), outcome, "TEST", now); }

    @Test void lifetimeHistoryCannotActivateWithoutRecentWeight() {
        var m = new ConvergenceMemory(); var h = history(m);
        assertEquals(6, h.deaths); assertEquals(3, h.encounters); assertEquals("ELIGIBLE", h.eligibility(1880));
        double weight = h.weight(1880);
        assertEquals(weight / 2, h.weight(1880 + 24000), 1e-10);
        assertEquals("STALE_PRESSURE", h.eligibility(1880 + 24000));
        h = ConvergenceMemory.load(m.save()).at(DIM, ORIGIN);
        assertEquals(6, h.deaths); assertEquals(3, h.encounters);
        h.death(id(20), ORIGIN, 26000); h.death(id(21), ORIGIN, 26000); h.advance(26600);
        assertEquals(8, h.deaths); assertEquals(4, h.encounters); assertEquals("ELIGIBLE", h.eligibility(26600));
    }
    @Test void oneLargeFightAndReloadNeverManufactureExtraEncounters() {
        var m = new ConvergenceMemory();
        for (int i = 1; i <= 20; i++) m.death(id(i), DIM, ORIGIN, i * 50, false);
        var h = m.at(DIM, ORIGIN); var episode = h.encounter;
        assertEquals(0, h.encounters); assertEquals(16, h.evidence.size());
        m = ConvergenceMemory.load(m.save()); h = m.at(DIM, ORIGIN); assertEquals(episode, h.encounter);
        assertFalse(m.death(id(20), DIM, ORIGIN, 1100, false)); assertEquals(20, h.deaths);
        h.contact(1599); h.advance(2198); assertEquals(0, h.encounters);
        h.advance(2199); assertEquals(1, h.encounters); assertEquals("INSUFFICIENT_LIFETIME_HISTORY", h.eligibility(2199));
    }
    @Test void radiusHasFixedAnchorAndDimensionIdentity() {
        var m = new ConvergenceMemory(); m.death(id(1), DIM, ORIGIN, 1, false);
        m.death(id(2), DIM, ORIGIN.offset(24, 0, 0), 2, false);
        m.death(id(3), DIM, ORIGIN.offset(25, 0, 0), 3, false);
        m.death(id(4), "minecraft:the_nether", ORIGIN, 4, false);
        assertEquals(3, m.hotspots.size()); assertEquals(2, m.at(DIM, ORIGIN).deaths);
        assertEquals(ORIGIN, m.at(DIM, ORIGIN).anchor);
    }
    @Test void dispatchCasualtiesCannotChangeAnyHotspotEvenAfterInterruptionAndReload() {
        var m = new ConvergenceMemory(); var h = history(m); m.active = group(h, 2000);
        var before = h.save();
        m.death(id(100), DIM, ORIGIN.offset(200, 0, 0), 2100, true);
        assertEquals(before, h.save()); assertEquals(1, m.hotspots.size()); assertEquals(1, m.active.dead.size());
        assertFalse(m.death(id(100), DIM, ORIGIN, 2101, true));
        m = ConvergenceMemory.load(m.save()); assertNull(m.active);
        h = m.at(DIM, ORIGIN); assertEquals("UNKNOWN", h.results.getLast().getString("outcome"));
        var interrupted = h.save(); m.death(id(101), DIM, ORIGIN.offset(200, 0, 0), 2200, true);
        assertEquals(interrupted, h.save()); assertEquals(1, m.hotspots.size()); assertEquals(0, h.wipes);
    }
    @Test void wipeRequiresEveryMemberDeadWithoutPriorPlayerContact() {
        var h = history(new ConvergenceMemory()); var g = group(h, 2000);
        g.death(id(100)); assertEquals("UNKNOWN", g.completedOutcome());
        g.contact(id(101), id(2), ORIGIN, 2239); assertEquals(-1, g.contactAt);
        g.contact(id(999), id(2), ORIGIN, 2240); assertEquals(-1, g.contactAt);
        g.death(id(101)); assertEquals("WIPE", g.completedOutcome());
        g = group(h, 2000); g.death(id(100)); g.contact(id(101), id(2), ORIGIN, 2250); g.death(id(101));
        assertEquals("SUCCESS", g.completedOutcome()); assertEquals("SUCCESS", ConvergenceGroup.load(g.save()).completedOutcome());
    }
    @Test void unknownLeavesStreakAndSuccessResetsIt() {
        var h = history(new ConvergenceMemory()); result(h, "WIPE", 2000); result(h, "UNKNOWN", 2001); result(h, "WIPE", 2002);
        assertEquals(2, h.wipes); assertTrue(h.avoid);
        h = history(new ConvergenceMemory()); result(h, "WIPE", 2000); result(h, "SUCCESS", 2001); result(h, "WIPE", 2002);
        assertEquals(1, h.wipes); assertFalse(h.avoid);
    }
    @Test void strictDecayBoundaryRearmsAndOldPenaltiesDoNotLeakIntoNewCycle() {
        var h = history(new ConvergenceMemory()); result(h, "WIPE", 2000); result(h, "WIPE", 2001);
        h.weight = 6; h.weightedAt = 0; h.cooldownUntil = 1;
        h.advance(24000); assertTrue(h.avoid); assertEquals("AVOID_TWO_WIPES", h.eligibility(24000));
        h = Hotspot.load(h.save()); h.advance(24001); assertFalse(h.avoid); assertEquals(0, h.wipes); assertEquals(1, h.cycle);
        assertEquals(6, h.deaths); assertEquals(3, h.encounters); assertEquals(2, h.results.size());
        assertEquals("STALE_PRESSURE", h.eligibility(24001));
        h.death(id(99), ORIGIN, 24001); h.advance(24601);
        assertEquals("ELIGIBLE", h.eligibility(24601));
        assertEquals("CONVERGE", StrategySelector.best(ConvergencePolicy.scores(h.avoid, h.wipes, h.weight(24601))).action());
    }
    @Test void ongoingFreshDeathsKeepAvoidanceLatchedAndCooldownDoesNotClearIt() {
        var h = history(new ConvergenceMemory()); result(h, "WIPE", 2000); result(h, "WIPE", 2001);
        for (int i = 0; i < 40; i++) h.death(id(30 + i), ORIGIN, 3000 + i * 1000);
        h.advance(43000); assertTrue(h.avoid); assertEquals(2, h.wipes);
        assertEquals("AVOID", StrategySelector.best(ConvergencePolicy.scores(h.avoid, h.wipes, h.weight(43000))).action());
    }
    @Test void completedActivationConsumesExactCooldownDeferredHistoryDoesNot() {
        var h = history(new ConvergenceMemory()); assertEquals(0, h.cooldownUntil);
        result(h, "UNKNOWN", 2000);
        assertEquals("HOTSPOT_COOLDOWN", h.eligibility(13999)); assertEquals("ELIGIBLE", h.eligibility(14000));
    }
    @Test void notificationTaperAndCooldownSurviveReload() {
        var m = new ConvergenceMemory(); assertTrue(m.notice(id(1), 0)); assertFalse(m.notice(id(1), 11999));
        m = ConvergenceMemory.load(m.save()); assertTrue(m.notice(id(1), 12000)); assertFalse(m.notice(id(1), 24000));
        assertTrue(m.notice(id(2), 24000));
    }
    @Test void boundedHistoryEvictsOldestDeterministicallyAndProtectsActiveHotspot() {
        var m = new ConvergenceMemory(); var active = history(m); m.active = group(active, 2000);
        for (int i = 0; i < 50; i++) m.death(id(500 + i), DIM, ORIGIN.offset(100 + i * 50, 0, 0), 2100 + i, false);
        assertEquals(32, m.hotspots.size()); assertSame(active, m.hotspots.get(active.id));
        assertNull(m.at(DIM, ORIGIN.offset(100, 0, 0)));
        assertEquals(32, ConvergenceMemory.load(m.save()).hotspots.size());
    }
    @Test void populationClaimsSurviveReloadAndOnlyDestructionFreesCapacity() {
        var p = new PawnPopulation();
        for (int i = 1; i <= 3; i++) p.register(id(i), DIM, ORIGIN);
        assertFalse(p.canSpawn(1)); var copy = new PawnPopulation(); copy.load(p.save()); assertFalse(copy.canSpawn(1));
        copy.destroyed(id(1)); assertTrue(copy.canSpawn(1)); assertEquals(12, ConvergencePolicy.populationLimit(100));
    }
    @Test void dispatchRetainsDecisionEvidenceAfterLaterDeathsChangeTheRegion() {
        var m = new ConvergenceMemory(); var h = history(m); var g = group(h, 2000);
        var frozen = g.history.copy(); h.death(id(80), ORIGIN, 2100);
        assertEquals(frozen, g.history); assertEquals(frozen, ConvergenceGroup.load(g.save()).history);
        assertEquals(6, frozen.getInt("lifetimeDeaths")); assertEquals(3, frozen.getInt("completedEncounters"));
        assertEquals(6, frozen.getList("evidence", 10).size());
    }
    @Test void erasureClearsAndReleasesEveryConvergenceObjectAndLegacyLoadsEmpty() {
        var data = new MaeveSavedData(); data.synchronize(false, true); var m = data.convergence(); var h = history(m); m.active = group(h, 2000);
        data.store().contact(id(80), 1); m.notice(id(1), 2000); m.population.register(id(100), DIM, ORIGIN);
        var backup = data.save(new CompoundTag(), null);
        data.erase(); data.erase(); assertNull(data.convergence()); assertTrue(m.hotspots.isEmpty()); assertTrue(m.population.pawns.isEmpty()); assertNull(m.active);
        assertFalse(data.save(backup.copy(), null).contains("convergence"));
        assertEquals(1, MaeveSavedData.load(backup, null).convergence().hotspots.size());
        data.synchronize(false, true); assertTrue(data.convergence().hotspots.isEmpty());
        var legacy = new CompoundTag(); legacy.putInt("dataVersion", 7); legacy.putBoolean("activated", true);
        assertTrue(MaeveSavedData.load(legacy, null).convergence().hotspots.isEmpty());
    }
}
