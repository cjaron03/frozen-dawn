package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCampNavigationGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void retainedClimbingPlanYieldsToVisibleGroundTarget(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 106, 2, scene -> {
            terrain(scene);
            for (int z = -8; z <= 32; z++) for (int y = 0; y < 3; y++)
                scene.block(14, y, z, Blocks.BEDROCK.defaultBlockState());
            var actor = scene.architect(13, 12);
            var player = scene.player("ground_relocated", 18, 12);
            actor.setInvulnerable(true); player.setInvulnerable(true);
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            var path = actor.getDStarPathfinder();
            path.setSurfaceY(scene.origin.getY());
            path.initialize(player.blockPosition(), actor.blockPosition(), scene.level);
            helper.assertTrue(path.computePartial(20_000, scene.level), "Retained wall route must finish");
            var original = path.peekNextStep(actor.blockPosition(), scene.level);
            helper.assertTrue(original.type() == DStarLitePathfinder.StepType.SCAFFOLD_UP,
                    "Control route really requires construction: " + original);
            player.setPos(scene.position(3, 12));
            helper.assertTrue(!path.needsReinitialize(player.blockPosition())
                            && !path.isNearOutdatedGoal(actor.blockPosition(), player.blockPosition()),
                    "Player movement must remain inside the ordinary plan reuse limits");
            helper.assertTrue(actor.hasLineOfSight(player), "The relocated player must be visible on open ground");
            actor.startDecisionRecording(UUID.randomUUID(), 1337L, Rotation.NONE);
            actor.decisionJournal().useExtendedLabBuffer();
            for (int i = 0; i < 80; i++) {
                scene.clock(scene.gameTime + i + 1);
                scene.level.tickNonPassenger(actor);
            }
            var scaffolds = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("SCAFFOLD_PLACE")).toList();
            System.out.println("RETAINED_GROUND_REPLAY scaffolds=" + scaffolds + " pos=" + actor.blockPosition());
            helper.assertTrue(scaffolds.isEmpty(), "A retained climb must yield to the visible ground route: " + scaffolds);
            helper.assertTrue(actor.distanceTo(player) < 4, "The actor must pursue the relocated player");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void campWallGroundRouteNeedsNoScaffolds(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 104, 2, scene -> {
            terrain(scene);
            BlockPos start = scene.origin.offset(13, 0, 12);
            BlockPos goal = scene.origin.offset(2, 0, 16);
            var path = new DStarLitePathfinder();
            path.initialize(goal, start, scene.level);
            BlockPos current = start;
            for (int i = 0; i < 32 && !current.equals(goal); i++) {
                path.updateStart(current);
                helper.assertTrue(path.computePartial(10_000, scene.level), "Local ground search must finish");
                var step = path.getNextStep(current, scene.level);
                helper.assertTrue(step.type() == DStarLitePathfinder.StepType.WALK && step.pos().getY() == start.getY(),
                        "Open ground beside the cabin must stay walkable: " + current + " -> " + step);
                current = step.pos();
            }
            helper.assertTrue(current.equals(goal), "Ground route must reach the player");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void campMovingGroundTargetNeedsNoScaffolds(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 105, 2, scene -> {
            terrain(scene);
            var actor = scene.architect(15, 5);
            var player = scene.player("camp_ground", 5, 13);
            actor.setInvulnerable(true); player.setInvulnerable(true);
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            actor.startDecisionRecording(UUID.randomUUID(), 1337L, Rotation.NONE);
            actor.decisionJournal().useExtendedLabBuffer();
            for (int i = 0; i < 240; i++) {
                scene.clock(scene.gameTime + i + 1);
                // Replay the outward ground movement after the live actor left the west entrance.
                int x = Math.max(-4, 5 - i / 9), z = Math.min(26, 13 + i / 11);
                player.setPos(scene.position(x, z));
                scene.level.tickNonPassenger(actor);
            }
            var scaffolds = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("SCAFFOLD_PLACE")).toList();
            System.out.println("CAMP_GROUND_REPLAY scaffolds=" + scaffolds + " goal=" + actor.getDStarPathfinder().debugState().goal());
            helper.assertTrue(scaffolds.isEmpty(), "A player leaving the cabin across ground must not induce a raised bridge: " + scaffolds);
            helper.assertTrue(actor.position().distanceToSqr(scene.position(15, 5)) > 100,
                    "Avoiding scaffolds must still allow actual pursuit");
        });
    }

    private static void terrain(MaeveObservationGameTest.Scene scene) {
        for (int x = -12; x <= 36; x++) for (int z = -8; z <= 32; z++) {
            scene.block(x, -2, z, Blocks.STONE.defaultBlockState());
            scene.block(x, -1, z, Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y <= 5; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
        }
        // Translation of the camp's west wall, doorway and path-to-plank floor transition.
        for (int x = -10; x <= 36; x++) for (int z = 7; z <= 9; z++)
            scene.block(x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
        for (int x = 14; x <= 34; x++) for (int z = -2; z <= 18; z++)
            scene.block(x, -1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int z = -2; z <= 18; z++) for (int y = 0; y < 4; y++)
            if (z < 7 || z > 9 || y == 3) scene.block(14, y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
    }
}
