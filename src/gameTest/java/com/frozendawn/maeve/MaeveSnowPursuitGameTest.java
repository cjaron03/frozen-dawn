package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveSnowPursuitGameTest {
    // Frozen camp x=2040..2057, z=2024..2032. Four-layer cell at (2051,101,2029).
    private static final int[][] SNOW = {
        {2, 1, 1, 0, 0, 0, 0, 0, 1, 2, 3, 5, 7, 9, 12, 13, 15, 16},
        {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 5, 7, 9, 11, 13, 14, 15},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 5, 7, 9, 11, 12, 13, 14},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 6, 8, 10, 11, 12, 12},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 6, 8, 9, 10, 10, 11},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 4, 6, 7, 8, 8, 9, 9},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 4, 5, 6, 6, 7, 7, 0},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 3, 4, 5, 5, 5, 5, 5},
        {0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 2, 3, 3, 3, 4, 4, 4, 4},
    };

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void snowPursuitEscapesRetainedElevatedGoal(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 135, 2, scene -> {
            for (int x = -12; x <= 30; x++) for (int z = -8; z <= 20; z++) {
                scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 5; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
            }
            for (int z = 0; z < SNOW.length; z++) for (int x = 0; x < SNOW[z].length; x++) {
                int depth = SNOW[z][x], full = depth / 8, layers = depth % 8;
                for (int y = 0; y < full; y++) scene.block(x, y, z, Blocks.SNOW_BLOCK.defaultBlockState());
                if (layers > 0) scene.block(x, full, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
            }
            var actor = scene.architect(11, 5);
            actor.setPos(scene.position(11, 5).add(0, .375, 0));
            var player = scene.player("snow_loop", -5, 5);
            actor.setInvulnerable(true); player.setInvulnerable(true);
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            actor.setDeltaMovement(Vec3.ZERO);
            actor.startDecisionRecording(1337L); actor.decisionJournal().useExtendedLabBuffer();
            var path = actor.getDStarPathfinder();
            path.setSurfaceY(scene.origin.getY());
            path.initialize(scene.origin.offset(2, 1, 5), actor.blockPosition(), scene.level);
            path.computePartial(20_000, scene.level);
            long now = scene.gameTime;
            int reached = -1;
            for (int i = 0; i < 480; i++) {
                scene.clock(now + i + 1); scene.level.tickNonPassenger(actor);
                if (actor.distanceTo(player) < 3) { reached = i; break; }
            }
            System.out.println("SNOW_PURSUIT reached=" + reached + " pos=" + actor.position()
                    + " journal=" + actor.decisionJournal().entries().stream()
                    .filter(e -> e.event().equals("LOCAL_RECOVERY") || e.event().equals("REINIT")).toList());
            helper.assertTrue(reached >= 0, "Snow pursuit must escape the stale elevated route and reach the player: " + actor.position());
        });
    }
}
