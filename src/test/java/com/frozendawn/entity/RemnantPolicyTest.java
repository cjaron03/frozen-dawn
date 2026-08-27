package com.frozendawn.entity;

import com.frozendawn.world.remnant.RemnantLureTemplate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemnantPolicyTest {
    @Test
    void regionKeysUseVanillaThirtyTwoChunkRegions() {
        assertEquals(RemnantPolicy.regionKey(new BlockPos(0, 64, 0)),
                RemnantPolicy.regionKey(new BlockPos(511, 90, 511)));
        assertFalse(RemnantPolicy.regionKey(new BlockPos(511, 64, 0))
                == RemnantPolicy.regionKey(new BlockPos(512, 64, 0)));
    }

    @Test
    void naturalPlacementRequiresReleaseCapacityAndCooldown() {
        assertTrue(RemnantPolicy.canNaturalPlace(true, true, false, 1, 400L, 300L));
        assertFalse(RemnantPolicy.canNaturalPlace(false, true, false, 1, 400L, 300L));
        assertFalse(RemnantPolicy.canNaturalPlace(true, false, false, 1, 400L, 300L));
        assertFalse(RemnantPolicy.canNaturalPlace(true, true, true, 1, 400L, 300L));
        assertFalse(RemnantPolicy.canNaturalPlace(true, true, false, 2, 400L, 300L));
        assertFalse(RemnantPolicy.canNaturalPlace(true, true, false, 1, 200L, 300L));
    }

    @Test
    void luresKeepTheLockedSevenHundredSixtyEightBlockSpacing() {
        BlockPos origin = BlockPos.ZERO;
        assertFalse(RemnantPolicy.hasSpacing(new BlockPos(767, 0, 0), List.of(origin)));
        assertTrue(RemnantPolicy.hasSpacing(new BlockPos(768, 0, 0), List.of(origin)));
    }

    @Test
    void allSixTemplatesStayInsideTheAuthoredBoundsUnderEveryRotation() {
        assertEquals(6, RemnantLureTemplate.Kind.values().length);
        for (RemnantLureTemplate.Kind kind : RemnantLureTemplate.Kind.values()) {
            RemnantLureTemplate template = RemnantLureTemplate.create(kind);
            for (int rotation = 0; rotation < 4; rotation++) {
                for (RemnantLureTemplate.Cell cell : template.cells()) {
                    BlockPos transformed = RemnantLureTemplate.rotate(cell.local(), rotation);
                    assertTrue(Math.abs(transformed.getX()) <= 5);
                    assertTrue(Math.abs(transformed.getZ()) <= 5);
                    assertTrue(transformed.getY() >= 0 && transformed.getY() <= 7);
                }
            }
            assertEquals(2, template.cells().stream()
                    .filter(cell -> cell.role() == RemnantLureTemplate.Role.SEAM).count());
            HashSet<BlockPos> tangible = new HashSet<>();
            for (RemnantLureTemplate.Cell cell : template.cells()) {
                if (cell.role() == RemnantLureTemplate.Role.ANCHOR
                        || cell.role() == RemnantLureTemplate.Role.TRIGGER) continue;
                assertTrue(tangible.add(cell.local()),
                        () -> kind + " overlaps tangible cells at " + cell.local());
            }
            int radius = template.radius();
            for (int y = 1; y < template.height() - 1; y++) {
                for (int edge = -radius; edge <= radius; edge++) {
                    assertShellCell(kind, tangible, new BlockPos(edge, y, radius));
                    if (!(edge == 0 && y <= 2)) {
                        assertShellCell(kind, tangible, new BlockPos(edge, y, -radius));
                    }
                    assertShellCell(kind, tangible, new BlockPos(radius, y, edge));
                    assertShellCell(kind, tangible, new BlockPos(-radius, y, edge));
                }
            }
        }
    }

    private static void assertShellCell(RemnantLureTemplate.Kind kind,
                                        HashSet<BlockPos> tangible, BlockPos position) {
        assertTrue(tangible.contains(position), () -> kind + " has a shell hole at " + position);
    }

    @Test
    void reloadNormalizationIsLimitedToUnsafeTravelAndGrabStates() {
        assertFalse(RemnantState.SEALING.isUnsafeAfterReload());
        assertTrue(RemnantState.EXPOSED.isUnsafeAfterReload());
        assertFalse(RemnantState.HUNTING.isUnsafeAfterReload());
        assertFalse(RemnantState.COLLAPSING.isUnsafeAfterReload());
    }

    @Test
    void deathStatePreservesEarlierPersistedOrdinalsAndLocksTheShelter() {
        assertEquals(8, RemnantState.COLLAPSING.ordinal());
        assertEquals(9, RemnantState.RESOLVED.ordinal());
        assertEquals(10, RemnantState.DYING.ordinal());
        assertTrue(RemnantState.DYING.locksShelter());
        assertFalse(RemnantState.COLLAPSING.locksShelter());
        assertEquals(48, RemnantPolicy.DEATH_PRESENTATION_TICKS);
    }

    @Test
    void learnedEvasionNeedsTwoMatchingHitsInsideItsWindow() {
        assertFalse(RemnantPolicy.canEvadeRepeatedAttack(false, true, 1, 80, 0));
        assertFalse(RemnantPolicy.canEvadeRepeatedAttack(false, false, 2, 80, 0));
        assertFalse(RemnantPolicy.canEvadeRepeatedAttack(false, true, 2, 0, 0));
        assertFalse(RemnantPolicy.canEvadeRepeatedAttack(false, true, 2, 80, 1));
        assertFalse(RemnantPolicy.canEvadeRepeatedAttack(true, true, 2, 80, 0));
        assertTrue(RemnantPolicy.canEvadeRepeatedAttack(false, true, 2, 80, 0));
    }

    @Test
    void slipCandidatesMoveFromWallMarkersTowardTheShelterInterior() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos eastWall = new BlockPos(3, 65, 0);
        List<BlockPos> candidates = RemnantPolicy.inwardSlipCandidates(eastWall, origin);
        assertEquals(9, candidates.size());
        assertTrue(candidates.stream().allMatch(pos -> pos.getX() < eastWall.getX()));
        assertTrue(candidates.contains(new BlockPos(2, 65, 0)));
    }

    @Test
    void wallSlipCanActuallyTriggerAcrossARealCabinInterior() {
        assertFalse(RemnantPolicy.canStartWallSlip(4.0D, 0));
        assertTrue(RemnantPolicy.canStartWallSlip(6.25D, 0));
        assertTrue(RemnantPolicy.canStartWallSlip(16.0D, 0));
        assertFalse(RemnantPolicy.canStartWallSlip(16.0D, 1));
    }

    @Test
    void wallLatchHealingIsBoundedPerUseAndPerEncounter() {
        float perTick = RemnantPolicy.wallLatchHealStep(40.0F, 84.0F, 0.0F, 0.0F);
        assertEquals(RemnantPolicy.WALL_LATCH_HEAL_PER_USE
                        / RemnantPolicy.WALL_LATCH_TICKS,
                perTick, 0.0001F);
        assertEquals(0.0F, RemnantPolicy.wallLatchHealStep(
                84.0F, 84.0F, 0.0F, 0.0F));
        assertEquals(0.0F, RemnantPolicy.wallLatchHealStep(
                40.0F, 84.0F, RemnantPolicy.WALL_LATCH_HEAL_PER_USE,
                RemnantPolicy.WALL_LATCH_HEAL_PER_USE));
        assertEquals(0.0F, RemnantPolicy.wallLatchHealStep(
                40.0F, 84.0F, 0.0F, RemnantPolicy.WALL_LATCH_HEAL_BUDGET));
    }

    @Test
    void wallRecoveryOnlyStartsWhenHealthIsActuallyMissing() {
        assertFalse(RemnantPolicy.canStartWallRecovery(84.0F, 84.0F, 0.0F));
        assertTrue(RemnantPolicy.canStartWallRecovery(83.0F, 84.0F, 0.0F));
        assertFalse(RemnantPolicy.canStartWallRecovery(
                40.0F, 84.0F, RemnantPolicy.WALL_LATCH_HEAL_BUDGET));
    }

    @Test
    void shelterProtectionEndsOnlyWhenCollapseBegins() {
        assertTrue(RemnantState.DORMANT.protectsShelterFromEnvironment());
        assertTrue(RemnantState.HUNTING.protectsShelterFromEnvironment());
        assertTrue(RemnantState.DYING.protectsShelterFromEnvironment());
        assertFalse(RemnantState.COLLAPSING.protectsShelterFromEnvironment());
        assertFalse(RemnantState.RESOLVED.protectsShelterFromEnvironment());
    }

    @Test
    void falseForgivenessIsTheRareRadioLine() {
        int[] counts = new int[4];
        for (int broadcast = 0; broadcast < 256; broadcast++) {
            counts[RemnantPolicy.radioLine(41L, broadcast)]++;
        }
        assertTrue(counts[3] >= 8 && counts[3] <= 24);
        assertTrue(counts[0] > 60);
        assertTrue(counts[1] > 60);
        assertTrue(counts[2] > 60);
    }

    @Test
    void falseRadioRepeatsOnAnIrregularBoundedDelay() {
        HashSet<Integer> delays = new HashSet<>();
        for (int broadcast = 0; broadcast < 32; broadcast++) {
            int delay = RemnantPolicy.radioRepeatDelay(41L, broadcast);
            assertTrue(delay >= RemnantPolicy.RADIO_REPEAT_DELAY_MIN);
            assertTrue(delay < RemnantPolicy.RADIO_REPEAT_DELAY_MIN
                    + RemnantPolicy.RADIO_REPEAT_DELAY_RANGE);
            delays.add(delay);
        }
        assertTrue(delays.size() > 20);
    }
}
