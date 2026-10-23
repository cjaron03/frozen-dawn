package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Same real historical attacks, followed by a fresh locally perceived encounter elsewhere. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCombatTravelGameTest {
    private static long train(MaeveObservationGameTest.Scene s, MaeveObservationGameTest.TestPlayer p,
                              boolean ranged, int encounters) {
        p.setPos(s.position(8, 4));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ranged ? Items.BOW : Items.IRON_SWORD));
        var witness = s.architect(2, 4);
        long start = s.server.overworld().getGameTime() + 1;
        for (int i = 0; i < encounters; i++) {
            s.clock(start + i * 610L);
            if (!s.hit(witness, p, ranged, 1)) throw new AssertionError("Real training hit must land");
        }
        witness.discard();
        long now = start + encounters * 610L;
        s.clock(now);
        return now;
    }

    private static void arena(MaeveObservationGameTest.Scene s) {
        for (int x = 48; x <= 80; x++) for (int z = -4; z <= 16; z++) {
            s.block(x, -1, z, Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 5; y++) s.block(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    private static ArchitectEntity executor(MaeveObservationGameTest.Scene s,
                                             MaeveObservationGameTest.TestPlayer p, int x) {
        var a = s.architect(x, 4);
        a.tickCount = 80; a.setOnGround(true); a.setDeltaMovement(Vec3.ZERO); a.debugForceApproach(p);
        a.startDecisionRecording(1337L); a.decisionJournal().useExtendedLabBuffer();
        return a;
    }

    private static void counter(GameTestHelper h, int lane, boolean ranged, int encounters) {
        MaeveObservationGameTest.withScene(h, lane, 5, s -> {
            arena(s);
            var p = s.player("travel_" + lane, 8, 4);
            long now = train(s, p, ranged, encounters);
            String pattern = ranged ? BeliefStore.RANGED : BeliefStore.SWORD;
            var before = s.beliefs(p).stream().filter(b -> b.pattern().equals(pattern)).findFirst().orElseThrow();
            p.setPos(s.position(!ranged ? (encounters == 4 ? 68 : 80) : 76, 4));
            var a = executor(s, p, 64);
            var hints = MaeveDirector.commitmentHints(a, p);
            System.out.println("COMBAT_TRAVEL lane=" + lane + " pattern=" + pattern + " confidence=" + before.confidence()
                    + " evidenceDistance=" + Math.sqrt(a.blockPosition().distSqr(before.provenance().getLast().position()))
                    + " currentDistance=" + a.distanceTo(p) + " eligible=" + CommitmentCoordinator.eligible(a, p)
                    + " hints=" + hints);
            h.assertTrue(CommitmentCoordinator.eligible(a, p), "New executor must actually see the local player");
            h.assertTrue(hints.stream().anyMatch(hint -> hint.pattern().equals(pattern)
                            && hint.confidence() == before.confidence()
                            && hint.evidence().position().equals(s.origin.offset(8, 0, 4))),
                    "Travel must preserve historical " + pattern + " without relocating its evidence");
            for (int i = 0; i < 65; i++) { s.clock(now + i); s.level.tickNonPassenger(a); }
            var d = MaeveDirector.positionDirective(a);
            h.assertTrue(d != null && d.pattern().equals(pattern), "A real counter must be selected after travel: " + a.inspectDecisions());
            h.assertTrue(d.evidence().position().equals(s.origin.offset(8, 0, 4)), "The original evidence remains inspectable");
            if (!ranged) {
                h.assertTrue(d.keepAwayArcher() == (encounters == 5), "Same historical threshold selects shield versus archer: " + d + " " + a.inspectDecisions());
                h.assertTrue(encounters == 5 ? a.getMainHandItem().is(Items.BOW) : a.getOffhandItem().is(Items.SHIELD),
                        "Selected sword counter must equip real equipment");
            } else {
                h.assertTrue(d.advancingCover() == (encounters == 5), "Same historical threshold selects pillar versus mantlet");
                h.assertTrue(d.cover() != null && s.level.getBlockState(d.cover()).is(Blocks.PACKED_ICE),
                        "Selected ranged counter must build actual local cover");
                System.out.println("COMBAT_TRAVEL_COVER lane=" + lane + " stand=" + d.position() + " cover=" + d.cover() + " evidence=" + d.evidence().position());
                h.assertTrue(encounters == 5 ? d.cover().getX() > d.position().getX()
                                : d.cover().getX() < d.position().getX(),
                        "Cover must screen the committed standing point: current east front for mantlet, historical west for pillar: " + d);
                if (encounters == 5) {
                    p.setPos(s.position(60, 12));
                    for (int i = 65; i < 160; i++) { s.clock(now + i); s.level.tickNonPassenger(a); }
                    var fixed = MaeveDirector.positionDirective(a);
                    h.assertTrue(fixed != null && fixed.cover().equals(d.cover()), "Flanking must not rotate the original mantlet bet");
                }
            }
            var after = s.beliefs(p).stream().filter(b -> b.pattern().equals(pattern)).findFirst().orElseThrow();
            h.assertTrue(after.confidence() == before.confidence() && after.evidence() == before.evidence()
                            && after.contradictions() == before.contradictions(), "Travel and counter selection supply no new behavioral evidence");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveSwordGuardCarriesAcrossTravel(GameTestHelper h) { counter(h, 172, false, 4); }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveArcherCarriesAcrossTravel(GameTestHelper h) { counter(h, 173, false, 5); }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maevePillarCarriesAcrossTravel(GameTestHelper h) { counter(h, 174, true, 4); }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveMantletCarriesAcrossTravel(GameTestHelper h) { counter(h, 175, true, 5); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveCombatTravelPreservesPerceptionAndThresholds(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 176, 5, s -> {
            arena(s);
            for (boolean ranged : new boolean[]{false, true}) {
                var p = s.player("travel_bounds_" + ranged, 8, 4);
                long now = train(s, p, ranged, 4);
                p.setPos(s.position(68, 4)); var a = executor(s, p, 64);
                for (int y = 0; y <= 4; y++) for (int z = 0; z <= 10; z++) s.block(66, y, z, Blocks.STONE.defaultBlockState());
                h.assertTrue(MaeveDirector.commitmentHints(a, p).isEmpty(), "Historical habits do not reveal an occluded player");
                for (int y = 0; y <= 4; y++) for (int z = 0; z <= 10; z++) s.block(66, y, z, Blocks.AIR.defaultBlockState());
                p.setPos(a.position().add(49, 0, 0));
                h.assertTrue(MaeveDirector.commitmentHints(a, p).isEmpty(), "Current sight range remains 48 blocks");
                p.setPos(s.position(68, 4)); p.setGameMode(GameType.SPECTATOR);
                h.assertTrue(MaeveDirector.commitmentHints(a, p).isEmpty(), "Spectators remain ineligible");
                p.setGameMode(GameType.CREATIVE);
                h.assertTrue(MaeveDirector.commitmentHints(a, p).isEmpty(), "Creative remains ineligible");
                p.setGameMode(GameType.SURVIVAL); a.setNoAi(true);
                h.assertTrue(MaeveDirector.commitmentHints(a, p).isEmpty(), "Inert actors cannot inherit tactical knowledge");
                a.setNoAi(false);
                var other = s.player("travel_other_" + ranged, 68, 5);
                h.assertTrue(MaeveDirector.commitmentHints(a, other).isEmpty(), "Another player's identity cannot inherit the trained preference");
                a.discard(); other.discard(); p.discard(); s.clock(now + 610);
                var weak = s.player("travel_weak_" + ranged, 8, 4);
                train(s, weak, ranged, 3); weak.setPos(s.position(68, 4)); var b = executor(s, weak, 64);
                h.assertTrue(!MaeveDirector.chooseCommitment(b, weak, List.of(new MaeveDirector.PositionCandidate(
                                ranged ? BeliefStore.RANGED : BeliefStore.SWORD, b.blockPosition(), null, 1.5))),
                        "Travel cannot promote 0.60 confidence above the counter threshold");
                b.discard(); weak.discard();
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveWeaponHintsCrossHistoricalDistanceBoundary(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 177, 5, s -> {
            arena(s);
            for (boolean ranged : new boolean[]{false, true}) {
                var p = s.player("travel_edge_" + ranged, 8, 4); train(s, p, ranged, 4);
                p.setPos(s.position(60, 4)); var a = executor(s, p, 56);
                String pattern = ranged ? BeliefStore.RANGED : BeliefStore.SWORD;
                h.assertTrue(MaeveDirector.commitmentHints(a, p).stream().anyMatch(v -> v.pattern().equals(pattern)),
                        "Nearby historical control is available at exactly 48 blocks");
                a.setPos(s.position(57, 4));
                h.assertTrue(MaeveDirector.commitmentHints(a, p).stream().anyMatch(v -> v.pattern().equals(pattern)),
                        "Weapon history must not disappear at 49 blocks from its evidence");
                a.discard(); p.discard();
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void maeveRecoveryLocationDoesNotTravelWithWeaponHabits(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 178, 5, s -> {
            arena(s); s.roof(true);
            var p = s.player("travel_recovery", 8, 4); var witness = s.architect(2, 4);
            long start = s.gameTime + 1;
            for (int i = 0; i < 4; i++) { s.clock(start + i * 610L); p.finish(s.potion()); }
            witness.discard(); s.clock(start + 2440);
            var local = executor(s, p, 4);
            h.assertTrue(MaeveDirector.commitmentHints(local, p).stream().anyMatch(v -> v.pattern().equals(BeliefStore.RECOVERY)),
                    "Real covered-use history remains available near its witnessed place");
            local.discard(); p.setPos(s.position(68, 4)); var remote = executor(s, p, 64);
            h.assertTrue(MaeveDirector.commitmentHints(remote, p).stream().noneMatch(v -> v.pattern().equals(BeliefStore.RECOVERY)),
                    "A recovery watch cannot silently move to an unrelated place");
        });
    }
}
