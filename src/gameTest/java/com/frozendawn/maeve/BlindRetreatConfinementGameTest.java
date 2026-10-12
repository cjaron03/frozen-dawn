package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.blind.BlindArchitectPolicy;
import com.frozendawn.gametest.GameTestTemplates;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical regression for the owner-observed bell view, including its confined escape space. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlindRetreatConfinementGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindBellRetreatUsesAvailableConfinedRoom(GameTestHelper helper) {
        exercise(helper, 921, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindBellRetreatRejectsRoofSeekingPartialPath(GameTestHelper helper) {
        exercise(helper, 922, true);
    }

    private static void exercise(GameTestHelper helper, int lane, boolean surroundingRock) {
        MaeveObservationGameTest.withScene(helper, lane, 2, scene -> {
            // A solid escape column triggers GroundPathNavigation's real upward target adjustment.
            // Saved cave: the escape column stays solid from Y=-44 through Y=25 (70 blocks).
            if (surroundingRock) for (int x = -3; x <= 0; x++) for (int z = 0; z <= 10; z++)
                for (int y = -2; y <= 69; y++) scene.block(x, y, z, Blocks.STONE.defaultBlockState());
            for (int x = 0; x <= 30; x++) for (int z = 0; z <= 10; z++) for (int y = -1; y <= 4; y++)
                scene.block(x, y, z, (x == 0 || x == 30 || z == 0 || z == 10 || y == 4)
                        ? Blocks.BEDROCK.defaultBlockState()
                        : (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            for (int y = 0; y <= 3; y++) for (int z = 1; z <= 9; z++) scene.block(10, y, z, Blocks.GLASS.defaultBlockState());
            for (int[] light : new int[][]{{5, 3}, {8, 3}, {5, 7}, {8, 7}, {12, 5}})
                scene.block(light[0], -1, light[1], Blocks.SEA_LANTERN.defaultBlockState());
            scene.block(8, 0, 5, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.WEST)
                    .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
            var player = scene.player("confined_bell_observer", 13, 5);
            player.setGameMode(GameType.CREATIVE);
            var actor = scene.architect(7, 5);
            actor.setBlindArchitect(true); actor.setOnGround(true); actor.setYRot(270); actor.tickCount = 1;
            scene.clock(4); actor.startDecisionRecording(1337L);
            Vec3 source = scene.origin.offset(8, 0, 5).getCenter();
            double initialDistance = -1, furthestFleeDistance = -1, closestFleeDistance = Double.MAX_VALUE;
            int fleeFrames = 0, movingFleeFrames = 0;
            JsonArray frames = new JsonArray();
            Vec3 idealGoal = null;
            for (int timer = 1; timer <= 320; timer++) {
                scene.clock(4 + timer);
                if (timer == 40) {
                    initialDistance = actor.position().distanceTo(source);
                    idealGoal = BlindArchitectPolicy.retreatGoal(actor.position(), source, actor.getLookAngle());
                    scene.block(8, 0, 4, Blocks.REDSTONE_BLOCK.defaultBlockState());
                }
                if (timer == 42) scene.block(8, 0, 4, Blocks.AIR.defaultBlockState());
                Vec3 before = actor.position();
                scene.level.tickNonPassenger(actor);
                String state = actor.blindController().diagnostics();
                var path = actor.getNavigation().getPath();
                JsonObject row = new JsonObject();
                row.addProperty("timer", timer); row.addProperty("health", actor.getHealth());
                row.addProperty("x", actor.getX() - scene.origin.getX());
                row.addProperty("y", actor.getY() - scene.origin.getY());
                row.addProperty("z", actor.getZ() - scene.origin.getZ());
                row.addProperty("state", state); row.addProperty("moved", actor.position().distanceTo(before));
                row.addProperty("pathPresent", path != null); row.addProperty("navigationDone", actor.getNavigation().isDone());
                if (path != null) { row.addProperty("pathCanReach", path.canReach()); row.addProperty("pathNodes", path.getNodeCount()); row.addProperty("pathTarget", path.getTarget().subtract(scene.origin).toShortString()); }
                frames.add(row);
                if (state.startsWith("blind=FLEE")) {
                    fleeFrames++;
                    if (actor.position().distanceTo(before) > .01) movingFleeFrames++;
                    furthestFleeDistance = Math.max(furthestFleeDistance, actor.position().distanceTo(source));
                    closestFleeDistance = Math.min(closestFleeDistance, actor.position().distanceTo(source));
                }
            }
            JsonObject evidence = new JsonObject();
            evidence.addProperty("fixture", "Owner room geometry and real redstone bell timing; solid escape column separately models underground target adjustment");
            evidence.addProperty("surroundingRock", surroundingRock);
            evidence.addProperty("initialBellDistance", initialDistance);
            evidence.addProperty("closestFleeDistance", closestFleeDistance);
            evidence.addProperty("furthestFleeDistance", furthestFleeDistance);
            evidence.addProperty("fleeFrames", fleeFrames); evidence.addProperty("movingFleeFrames", movingFleeFrames);
            evidence.addProperty("health", actor.getHealth());
            if (idealGoal != null) evidence.addProperty("idealGoalRelative", idealGoal.subtract(Vec3.atLowerCornerOf(scene.origin)).toString());
            evidence.add("frames", frames);
            try {
                Path directory = Path.of(System.getProperty("frozendawn.architect.reports"), surroundingRock ? "rock-bell-retreat" : "confined-bell-retreat");
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("movement.json"), new GsonBuilder().setPrettyPrinting().create().toJson(evidence) + "\n");
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            helper.assertTrue(actor.getHealth() == 38, "Actual supported redstone bell produces one noise-pain event");
            helper.assertTrue(fleeFrames == BlindArchitectPolicy.FLEE_TICKS, "Complete original retreat window remains active");
            helper.assertTrue(closestFleeDistance >= initialDistance - .1,
                    "Retreat never climbs onto the noise source: initial=" + initialDistance + " closest=" + closestFleeDistance);
            helper.assertTrue(furthestFleeDistance > initialDistance + 3 && movingFleeFrames > 0,
                    "Available floor permits physical retreat during FLEE, before later wandering: initial=" + initialDistance
                            + " furthest=" + furthestFleeDistance + " movingFrames=" + movingFleeFrames);
        });
    }
}
