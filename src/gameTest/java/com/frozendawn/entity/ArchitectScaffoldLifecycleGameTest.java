package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectActionTransitionSupport;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectObservationMemory;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectScaffoldLifecycleGameTest {

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void scaffoldTargetLossCancelsPendingPlacement(GameTestHelper helper) {
        var fixture = pendingStep(helper);
        var controller = new ArchitectObservationController(fixture.actor, new ArchitectObservationMemory(),
                fixture.state, new ArchitectApproachController(fixture.actor, fixture.state, fixture.breaker), fixture.breaker);
        controller.enterRoamModeAfterTargetLoss();
        assertInterruptedStepCannotResume(helper, fixture);
    }

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void scaffoldActionExitCancelsPendingPlacement(GameTestHelper helper) {
        var fixture = pendingStep(helper);
        ArchitectActionTransitionSupport.onLeaveApproach(fixture.state);
        assertInterruptedStepCannotResume(helper, fixture);
    }

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void scaffoldDisplacementCannotPlaceOrTeleportRemotely(GameTestHelper helper) {
        var fixture = pendingStep(helper);
        Vec3 displaced = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(16, 2, 4)));
        fixture.actor.setPos(displaced);
        for (int i = 0; i < ArchitectEntity.SCAFFOLD_PLACE_TICKS; i++) {
            ArchitectApproachMovementSupport.tickPendingScaffold(fixture.actor, fixture.state, fixture.breaker);
        }
        // Also guard the resolver itself, before it places any remote block.
        ArchitectApproachMovementSupport.resolveScaffoldStep(fixture.actor, fixture.state, fixture.breaker, fixture.destination);
        helper.assertTrue(fixture.actor.position().distanceToSqr(displaced) < 1.0e-8, "Stale scaffold must not teleport a displaced actor");
        helper.assertTrue(helper.getLevel().getBlockState(fixture.destination.below()).isAir(), "Stale scaffold must not place ice remotely");
        helper.assertTrue(fixture.state.scaffoldTarget == null, "Displaced scaffold must be cancelled");
        helper.succeed();
    }

    @GameTest(template = "lab/scaffold_ascent", timeoutTicks = 40)
    public static void scaffoldValidPendingStepKeepsPacingAndOwnership(GameTestHelper helper) {
        var fixture = pendingStep(helper);
        Vec3 start = fixture.actor.position();
        fixture.state.scaffoldDelay = ArchitectEntity.SCAFFOLD_PLACE_TICKS;
        for (int i = 1; i < ArchitectEntity.SCAFFOLD_PLACE_TICKS; i++) {
            ArchitectApproachMovementSupport.tickPendingScaffold(fixture.actor, fixture.state, fixture.breaker);
            helper.assertTrue(fixture.actor.position().equals(start), "Valid scaffold must retain its placement delay");
            helper.assertTrue(helper.getLevel().getBlockState(fixture.destination.below()).isAir(), "Do not place early");
        }
        ArchitectApproachMovementSupport.tickPendingScaffold(fixture.actor, fixture.state, fixture.breaker);
        helper.assertTrue(fixture.actor.position().equals(Vec3.atBottomCenterOf(fixture.destination)), "Valid local scaffold must still lift one block");
        helper.assertTrue(fixture.actor.isOwnedScaffold(fixture.destination.below()), "New support must remain owned");
        helper.assertTrue(fixture.actor.getScaffoldIceCount() == 3, "Existing two supports must remain owned");
        helper.succeed();
    }

    private static void assertInterruptedStepCannotResume(GameTestHelper helper, PendingStep fixture) {
        helper.assertTrue(fixture.state.scaffoldTarget == null && fixture.state.scaffoldDelay == 0,
                "Interruption must retire the queued scaffold, not suspend it for reacquisition");
        Vec3 displaced = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(16, 2, 4)));
        fixture.actor.setPos(displaced);
        for (int i = 0; i < ArchitectEntity.SCAFFOLD_PLACE_TICKS; i++) {
            ArchitectApproachMovementSupport.tickPendingScaffold(fixture.actor, fixture.state, fixture.breaker);
        }
        helper.assertTrue(fixture.actor.position().equals(displaced), "Reacquisition must not resume an old position change");
        helper.assertTrue(helper.getLevel().getBlockState(fixture.destination.below()).isAir(), "No stale placement after interruption");
        helper.assertTrue(fixture.actor.getScaffoldIceCount() == 2, "Cancelling a pending step must preserve built scaffold ownership");
        helper.succeed();
    }

    private static PendingStep pendingStep(GameTestHelper helper) {
        var actor = ModEntities.ARCHITECT.get().create(helper.getLevel());
        BlockPos feet = helper.absolutePos(new BlockPos(12, 4, 10));
        actor.setPos(Vec3.atBottomCenterOf(feet));
        actor.setOnGround(true);
        helper.getLevel().setBlockAndUpdate(feet.below(3), Blocks.BEDROCK.defaultBlockState());
        helper.assertTrue(actor.placeScaffoldIce(feet.below(2)), "Place first owned support");
        helper.assertTrue(actor.placeScaffoldIce(feet.below()), "Place second owned support");
        var state = new ArchitectApproachState();
        state.scaffoldTarget = feet.above();
        state.scaffoldDelay = 8; // Live LAN checkpoint: 4 of the 12 waiting ticks had elapsed.
        return new PendingStep(actor, state, new ArchitectBlockBreaker(actor, pos -> {}), feet.above());
    }

    private record PendingStep(ArchitectEntity actor, ArchitectApproachState state,
            ArchitectBlockBreaker breaker, BlockPos destination) {}
}
