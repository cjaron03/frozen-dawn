package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Replays the camp's fractional spawn surface and an access observed during a jump. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveReconTerrainGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconWalksFromDirtPathToFullFloor(GameTestHelper helper) {
        survey(helper, 39, true, false, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconGroundsAnElevatedCrossing(GameTestHelper helper) {
        survey(helper, 40, false, true, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconInspectsFromPartialSurface(GameTestHelper helper) {
        survey(helper, 42, false, false, true);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveObservedWalkKeepsTerrainSafetyBounds(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 41, 2, scene -> {
            // A single supported lane: unsafe terrain cannot be bypassed around its sides.
            for (int x = 18; x <= 26; x++) for (int z = 18; z <= 22; z++) for (int y = -2; y <= 3; y++)
                scene.block(x, y, z, Blocks.AIR.defaultBlockState());
            for (int x = 20; x <= 24; x++) scene.block(x, -1, 20, Blocks.STONE.defaultBlockState());
            scene.block(22, -1, 20, Blocks.STONE_SLAB.defaultBlockState());
            helper.assertTrue(crossLane(scene), "An actual half-slab dip remains a supported walking route");
            scene.block(22, -1, 20, Blocks.MAGMA_BLOCK.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "A magma floor is not a walking shortcut");
            scene.block(22, -1, 20, Blocks.AIR.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "An unsupported gap cannot become a scaffold route");
            scene.block(22, -1, 20, Blocks.STONE.defaultBlockState());
            scene.block(22, 0, 20, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "A full-block barrier cannot become a jump or excavation route");
            scene.block(22, 0, 20, Blocks.WATER.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "Walking does not enter water");
            scene.block(22, 0, 20, Blocks.FIRE.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "Walking does not enter fire");
            scene.block(22, 0, 20, Blocks.AIR.defaultBlockState());
            scene.block(22, 1, 20, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!crossLane(scene), "A low ceiling still blocks the body");
        });
    }

    private static boolean crossLane(MaeveObservationGameTest.Scene scene) {
        var current = scene.origin.offset(20, 0, 20); var goal = scene.origin.offset(24, 0, 20);
        var path = new DStarLitePathfinder(); path.configureObservedWalk(java.util.List.of());
        path.initialize(goal, current, scene.level);
        try {
            for (int i = 0; i < 100 && !current.equals(goal); i++) {
                path.updateStart(current);
                if (!path.computePartial(80, scene.level)) continue;
                var step = path.getNextStep(current, scene.level);
                if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) return false;
                current = step.pos();
            }
            return current.equals(goal);
        } finally { path.cleanup(); }
    }

    private static void survey(GameTestHelper helper, int lane, boolean pathStart, boolean elevated, boolean partialGoal) {
        MaeveObservationGameTest.withScene(helper, lane, 2, scene -> {
            for (int x = -5; x <= 35; x++) for (int z = -10; z <= 22; z++)
                scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            MaeveWorldModelGameTest.shelter(scene);
            var player = scene.player("recon_terrain_" + lane, 4, 5);
            var witness = scene.architect(10, 5);
            long now = (scene.gameTime / 20 + 1) * 20;
            MaeveWorldModelGameTest.sample(scene, witness, player, now, 4, 5);
            scene.clock(now + 10);
            player.setPos(scene.position(6, 5).add(0, elevated ? 1 : 0, 0));
            witness.setTarget(player);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(witness));
            var remembered = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream()
                    .filter(p -> p.label().equals("ACCESS_POINT")).findFirst().orElseThrow();
            helper.assertTrue(remembered.position().getY() == scene.origin.getY() + (elevated ? 1 : 0),
                    "Fixture must retain the actual witnessed crossing height");
            witness.discard(); player.setPos(scene.position(12, 12));
            if (pathStart) for (int x = 14; x <= 21; x++) for (int z = 4; z <= 6; z++)
                scene.block(x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
            if (partialGoal) for (int x = 8; x <= 10; x++) for (int z = 4; z <= 6; z++)
                scene.block(x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
            scene.clock(now + 650); MaeveDirector.tick(scene.server);
            var scout = scene.architect(18, 5);
            scout.setPos(scene.position(18, 5).add(0, pathStart ? -0.0625 : 0, 0));
            scout.tickCount = 80; scout.setOnGround(true); scout.startDecisionRecording(1337L);
            boolean inspected = false, walked = false, assigned = false;
            StringBuilder motion = new StringBuilder("tick\tx\ty\tz\tgrounded\tmission\n");
            float health = player.getHealth();
            for (int i = 0; i < 450; i++) {
                scene.clock(now + 650 + i); scout.tickCount++; scout.tick();
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(scout)); MaeveDirector.tick(scene.server);
                motion.append(i).append('\t').append(scout.getX() - scene.origin.getX()).append('\t')
                        .append(scout.getY() - scene.origin.getY()).append('\t').append(scout.getZ() - scene.origin.getZ())
                        .append('\t').append(scout.onGround()).append('\t')
                        .append(MaeveDirector.missionPacket(scout) != null).append('\n');
                if (MaeveDirector.missionPacket(scout) != null) {
                    assigned = true;
                    walked |= scout.getX() < scene.position(14, 5).x;
                    inspected |= scout.isHoldingMaevePosition();
                } else if (assigned) break;
            }
            var missions = MaeveDirector.missionSnapshots(scene.server, player.getUUID());
            try {
                var directory = java.nio.file.Path.of("..", "macs-terrain-traces");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve("survey-" + lane + ".tsv"), scout.decisionJournal().tsv());
                java.nio.file.Files.writeString(directory.resolve("motion-" + lane + ".tsv"), motion);
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            helper.assertTrue(walked && inspected && missions.stream().anyMatch(m ->
                            m.outcome().equals("SURVEY_COMPLETE") && m.report().equals("OPEN")),
                    "Scout must physically reach and inspect the crossing, path=" + pathStart + " elevated=" + elevated + " partialGoal=" + partialGoal
                            + " outcomes=" + missions.stream().map(m -> m.outcome() + "/" + m.report()).toList());
            helper.assertTrue(player.getHealth() == health, "Terrain repair must not turn the survey into combat");
            helper.assertTrue(missions.stream().allMatch(m -> m.packet().access().outside().equals(remembered.position())),
                    "Grounding an inspection position must not rewrite the witnessed record");
            helper.assertTrue(scout.decisionJournal().breaks() == 0,
                    "The scout must cross these surfaces without excavation");
        });
    }
}
