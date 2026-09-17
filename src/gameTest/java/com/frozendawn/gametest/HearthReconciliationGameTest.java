package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ChunkEpochState;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthReconciliationManager;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.HearthStructurePiece;
import com.frozendawn.homo.HearthStructurePlacement;
import com.frozendawn.homo.TraceHearthLayout;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Drives a real reconciliation pass against a live {@code ServerLevel}.
 *
 * <p>The JUnit suite covers the pieces of the degraded-placement fix in isolation — persistence,
 * the rewind, the policy gate — but the three-way fork at the end of
 * {@link HearthReconciliationManager} tick needs an actual world to exercise. This is the test
 * that would have caught the original bug: a structural cell is blocked, the scene must refuse to
 * report itself finished, and clearing the obstruction must heal it.
 *
 * <p>Uses the TRACE stage deliberately. Its footprint is radius 4 and its layout is a couple of
 * hundred pieces, so a pass completes in a handful of calls; the INTACT plan is 12,583 pieces and
 * would build a village in the test world.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class HearthReconciliationGameTest {

    /** Calls needed to walk a trace layout at the manager's per-call edit budget, with slack. */
    private static final int MAX_PASS_CALLS = 200;

    /** Blocks of clearance kept between the lowest planned cell and the world floor. */
    private static final int FLOOR_MARGIN = 2;

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void blockedHearthStaysUnfinishedThenHealsWhenCleared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ApocalypseState apocalypse = ApocalypseState.get(server);
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(server);

        // The manager keeps queue and failure state in statics; start from a known slate.
        HearthReconciliationManager.reset();

        ReturnedHearthSavedData.HearthRecord hearth =
                prepareTraceHearth(helper, data, level, apocalypse);

        BlockPos center = hearth.center();
        BlockPos blocked = firstStructuralCell(hearth, center);
        obstruct(server, level, blocked);

        runPass(level, apocalypse);

        helper.assertTrue(hearth.hasDegradedPlacements(),
                "a blocked structural cell was not recorded as degraded; stage=" + hearth.stage()
                        + " applied=" + hearth.structureStageApplied()
                        + " planVersion=" + hearth.structurePlanVersion()
                        + " cursor=" + hearth.structureCursor()
                        + " placed=" + hearth.structurePlaced()
                        + " surfaceResolved=" + hearth.surfaceResolved()
                        + " center=" + hearth.center()
                        + " blockedAt=" + blocked
                        + " | " + HearthReconciliationManager.statusLine());
        helper.assertFalse(hearth.structurePlaced(),
                "the scene reported itself finished while a structural cell was missing");
        helper.assertFalse(hearth.structureDegradedAccepted(),
                "the scene gave up on its first pass instead of scheduling a re-audit");

        clearObstruction(server, level, blocked);

        // queueAll also clears the re-audit backoff, which is what /fd hearth reconcile does.
        HearthReconciliationManager.queueAll(level);
        runPass(level, apocalypse);

        helper.assertFalse(hearth.hasDegradedPlacements(),
                "the degraded cell was not cleared after the obstruction was removed;"
                        + " blockedAt=" + blocked
                        + " nowHolds=" + level.getBlockState(blocked)
                        + " cursor=" + hearth.structureCursor()
                        + " attempts=" + hearth.structureReconcileAttempts()
                        + " degraded=" + hearth.structureDegradedCursors()
                        + " | " + HearthReconciliationManager.statusLine());
        helper.assertTrue(hearth.structurePlaced(),
                "the scene never completed even though nothing was blocking it any more");

        HearthReconciliationManager.reset();
        helper.succeed();
    }

    /** Selects a hearth, matures it to TRACE, and moves it onto the test platform. */
    private static ReturnedHearthSavedData.HearthRecord prepareTraceHearth(
            GameTestHelper helper,
            ReturnedHearthSavedData data,
            ServerLevel level,
            ApocalypseState apocalypse) {
        int middle = GameTestTemplates.EMPTY_LARGE_WIDTH / 2;
        BlockPos platform = helper.absolutePos(new BlockPos(middle, FLOOR_MARGIN, middle));
        long gameTime = level.getGameTime();
        data.applySelectionPlan(
                HearthSelectionPolicy.createPlan(level.getSeed(), platform), gameTime);
        data.advanceMaturationForDebug(HearthMaturationPolicy.TRACE_START_TICKS, gameTime);

        ReturnedHearthSavedData.HearthRecord hearth =
                data.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        helper.assertTrue(hearth != null, "no major hearth was selected for the test");
        helper.assertFalse(hearth.surfaceResolved(),
                "the test world already had a resolved hearth; the gate's world was not wiped");

        // Foundations dig below the center, and the gametest platform sits near the world floor.
        // Lift the scene until its lowest cell clears the build limit, or the pass stalls on
        // target-unloaded partway down the footprint.
        int lowestOffset = TraceHearthLayout.create(hearth.layoutSeed(), hearth.type()).stream()
                .mapToInt(placement -> placement.offset().getY())
                .min()
                .orElse(0);
        int minimumCenterY = level.getMinBuildHeight() - lowestOffset + FLOOR_MARGIN;
        BlockPos center = new BlockPos(
                platform.getX(), Math.max(platform.getY(), minimumCenterY), platform.getZ());

        // Selection places hearths hundreds of blocks away; drop it on the platform instead so
        // the pass runs inside loaded, predictable terrain.
        data.resolveSurface(hearth.id(), center);
        markFootprintCaughtUp(level, apocalypse, center);
        HearthReconciliationManager.queueAll(level);
        return hearth;
    }

    /**
     * Satisfies the chunk catch-up gate. Reconciliation refuses to touch a footprint the
     * apocalypse transform has not finished with, which never happens on its own in a test world.
     */
    private static void markFootprintCaughtUp(
            ServerLevel level, ApocalypseState apocalypse, BlockPos center) {
        ChunkEpochState epochs = ChunkEpochState.get(level.getServer());
        int radius = com.frozendawn.homo.HearthReconciliationPolicy.TRACE_FOOTPRINT_RADIUS;
        for (int chunkX = (center.getX() - radius) >> 4; chunkX <= (center.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (center.getZ() - radius) >> 4; chunkZ <= (center.getZ() + radius) >> 4; chunkZ++) {
                epochs.getOrCreate(chunkX, chunkZ).complete(
                        ChunkCatchUpManager.TRANSFORM_VERSION,
                        apocalypse.getTotalDays(),
                        apocalypse.getPhase(),
                        1.0F,
                        level.getGameTime());
            }
        }
    }

    /**
     * The first lower-ice cell in the layout — one of the two piece types the bug report named.
     *
     * <p>Not FOUNDATION_SUPPORT: the manager treats any solid block in a foundation cell as
     * already satisfying it, so putting cobblestone there fills the hole rather than blocking it.
     * Lower ice is the piece that actually refuses a player-placed obstruction, and it is what
     * the reported reproduction hit.
     *
     * <p>Deliberately not asking the production classifier which pieces are structural: that is
     * part of what this test exists to check, so the test must not depend on it.
     */
    private static BlockPos firstStructuralCell(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos center) {
        for (HearthStructurePlacement placement
                : TraceHearthLayout.create(hearth.layoutSeed(), hearth.type())) {
            if (placement.piece() == HearthStructurePiece.PACKED_ICE_LOWER) {
                return center.offset(placement.offset());
            }
        }
        throw new IllegalStateException("trace layout contains no lower-ice cells");
    }

    /**
     * A player-placed block is refused by canReplace as a permanent blocker, which makes this
     * deterministic — no waiting out the sixty-tick entity window.
     */
    private static void obstruct(MinecraftServer server, ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
        PlayerPlacedBlockTracker.get(server).markPlaced(pos);
    }

    private static void clearObstruction(MinecraftServer server, ServerLevel level, BlockPos pos) {
        PlayerPlacedBlockTracker.get(server).markRemoved(pos);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Runs the manager until it stops making progress. Each call gets its own edit and time
     * budget, so a whole pass fits inside one game tick rather than needing a delayed sequence.
     */
    private static void runPass(ServerLevel level, ApocalypseState apocalypse) {
        for (int call = 0; call < MAX_PASS_CALLS; call++) {
            HearthReconciliationManager.tick(level, apocalypse);
        }
    }
}
