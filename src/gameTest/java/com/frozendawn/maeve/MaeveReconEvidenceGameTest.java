package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mission admission and native observation events, without manufactured confidence. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveReconEvidenceGameTest {
    private static long history(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player) {
        MaeveWorldModelGameTest.shelter(scene);
        var witness = scene.architect(10, 5);
        long now = (scene.gameTime / 20 + 1) * 20;
        MaeveWorldModelGameTest.sample(scene, witness, player, now, 4, 5);
        MaeveWorldModelGameTest.sample(scene, witness, player, now + 10, 6, 5);
        witness.discard(); player.setPos(scene.position(12, 12));
        scene.clock(now + 650); MaeveDirector.tick(scene.server);
        return now + 650;
    }

    private static ArchitectEntity scout(GameTestHelper helper, MaeveObservationGameTest.Scene scene,
                                         MaeveObservationGameTest.TestPlayer player) {
        var actor = scene.architect(18, 5); actor.tickCount = 80; actor.setOnGround(true);
        helper.assertTrue(MaeveDirector.requestReconnaissance(actor, player), "Real historical uncertainty admits a scout");
        return actor;
    }

    private static MaeveDirector.BeliefSnapshot belief(MaeveObservationGameTest.Scene scene,
                                                       MaeveObservationGameTest.TestPlayer player, String pattern) {
        return scene.beliefs(player).stream().filter(b -> b.pattern().equals(pattern)).findFirst().orElseThrow();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveScoutFatalHitEarnsOneWeightedContribution(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 99, scene -> {
            var player = scene.player("scout_weight_fatal", 4, 5); history(scene, player);
            var actor = scout(helper, scene, player);
            var mission = MaeveDirector.missionPacket(actor);
            actor.setHealth(1);
            helper.assertTrue(scene.hit(actor, player, true, 100) && !actor.isAlive(), "Final damage is genuinely fatal");
            var result = belief(scene, player, BeliefStore.RANGED);
            helper.assertTrue(Math.abs(result.confidence() - .3) < 1e-9 && result.evidence() == 1,
                    "Fatal observation earns scout credit before death cleanup");
            var credit = result.provenance().getFirst();
            helper.assertTrue(credit.observer().equals(actor.getUUID()) && credit.confidenceWeight() == .3
                            && credit.action().contains("reconMission=" + mission.id()), "Credited scout and mission are explainable");
            helper.assertTrue(MaeveDirector.missionPacket(actor) == null, "Fatal hit still releases the mission");
            var ordinary = scene.architect(18, 6);
            scene.hit(ordinary, player, true, 1);
            result = belief(scene, player, BeliefStore.RANGED);
            helper.assertTrue(result.evidence() == 1 && Math.abs(result.confidence() - .3) < 1e-9
                            && result.provenance().getFirst().equals(credit)
                            && result.provenance().getLast().confidenceWeight() == 0,
                    "Later ordinary witnesses cannot add confidence or overwrite the scored scout event");
            scene.hit(ordinary, player, false, 1);
            result = belief(scene, player, BeliefStore.RANGED);
            helper.assertTrue(result.confidence() == 0 && result.contradictions() == 1
                            && result.provenance().getLast().confidenceWeight() == -.35, "Contradiction remains stronger than one scout report");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveScoutRecoveryRequiresSightAndPrefersMissionWitness(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 100, scene -> {
            var player = scene.player("scout_weight_recovery", 4, 5); history(scene, player);
            var actor = scout(helper, scene, player); player.setPos(scene.position(8, 4)); scene.roof(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, scene.potion());
            player.startUsingItem(InteractionHand.MAIN_HAND); player.stopUsingItem();
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> b.pattern().equals(BeliefStore.RECOVERY)),
                    "An active mission cannot turn interrupted consumption into evidence");
            for (int y = 0; y < 4; y++) for (int z = 0; z <= 12; z++) scene.block(15, y, z, Blocks.STONE.defaultBlockState());
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> b.pattern().equals(BeliefStore.RECOVERY)),
                    "An active scout cannot see recovery through a wall");
            for (int y = 0; y < 4; y++) for (int z = 0; z <= 12; z++) scene.block(15, y, z, Blocks.AIR.defaultBlockState());
            // This actor would win UUID ordering without the mission-aware priority.
            var ordinary = com.frozendawn.init.ModEntities.ARCHITECT.get().create(scene.level);
            ordinary.setUUID(new java.util.UUID(Long.MIN_VALUE, 0));
            ordinary.setPos(scene.position(10, 4)); scene.level.addFreshEntity(ordinary); scene.entities.add(ordinary);
            helper.assertTrue(actor.hasLineOfSight(player) && ordinary.hasLineOfSight(player), "Both real witnesses see the action");
            player.finish(scene.potion());
            var result = belief(scene, player, BeliefStore.RECOVERY);
            helper.assertTrue(result.evidence() == 1 && result.confidence() == .3
                            && result.provenance().getFirst().observer().equals(actor.getUUID())
                            && result.provenance().getFirst().confidenceWeight() == .3,
                    "One event prefers its mission witness without multiplying evidence for the crowd");
            scene.roof(false); player.finish(scene.potion());
            result = belief(scene, player, BeliefStore.RECOVERY);
            helper.assertTrue(result.confidence() == 0 && result.contradictions() == 1
                            && result.provenance().getLast().confidenceWeight() == -.35,
                    "An active scout's open-sky contradiction retains ordinary strength");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveScoutBonusExcludesOtherSubjectsAndDeparture(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 101, scene -> {
            var subject = scene.player("scout_weight_subject", 4, 5); long now = history(scene, subject);
            var actor = scout(helper, scene, subject);
            var other = scene.player("scout_weight_other", 8, 4); scene.roof(true);
            other.finish(scene.potion());
            var result = belief(scene, other, BeliefStore.RECOVERY);
            helper.assertTrue(result.confidence() == .2 && result.provenance().getFirst().confidenceWeight() == .2,
                    "Incidental observation of another player is ordinary evidence");
            helper.assertTrue(scene.beliefs(subject).stream().noneMatch(b -> b.pattern().equals(BeliefStore.RECOVERY)),
                    "Another player's recovery never teaches the mission subject's profile");
            MaeveDirector.finishMission(actor, "TEST_EXTRACTION", true);
            scene.clock(now + 1); actor.tick();
            helper.assertTrue(actor.hasReconnaissanceEyes() && MaeveDirector.missionPacket(actor) == null,
                    "Departure still looks purple but is no longer an active mission");
            scene.hit(actor, subject, true, 1);
            result = belief(scene, subject, BeliefStore.RANGED);
            helper.assertTrue(result.confidence() == .2 && result.provenance().getFirst().confidenceWeight() == .2
                            && !result.provenance().getFirst().action().contains("reconMission="),
                    "The appearance alone cannot grant a scout bonus after mission completion");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveScoutCrossingWeightKeepsHistoryLagAndDeadline(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 102, scene -> {
            var player = scene.player("scout_weight_crossing", 4, 5); long now = history(scene, player);
            var actor = scout(helper, scene, player);
            MaeveWorldModelGameTest.sample(scene, actor, player, now, 4, 5);
            MaeveWorldModelGameTest.sample(scene, actor, player, now + 10, 6, 5);
            var result = belief(scene, player, "RETREAT_BEARING_E");
            helper.assertTrue(result.evidence() == 2 && result.confidence() == .5
                            && result.provenance().getLast().confidenceWeight() == .3,
                    "A directly witnessed mission crossing gets the modest behavioral bonus");
            var hints = MaeveDirector.commitmentHints(actor, player);
            helper.assertTrue(hints.stream().anyMatch(h -> h.pattern().equals("RETREAT_BEARING_E") && h.confidence() == .2),
                    "The current mission's observation cannot rewrite the encounter's frozen history");
            scene.clock(now + MissionPlanner.TIMEOUT);
            // Deliberately omit lifecycle cleanup: an expired packet alone cannot authorize the bonus.
            scene.hit(actor, player, true, 1);
            result = belief(scene, player, BeliefStore.RANGED);
            helper.assertTrue(result.confidence() == .2, "Expired mission packets do not boost final damage");
        });
    }
}
