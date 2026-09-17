package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectApproachRecovery;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectApproachRecoveryGameTest {

    @GameTest(template = "lab/low_ceiling", timeoutTicks = 230)
    public static void lowDepartureCeilingIsOpenedBeforeSteppingUp(GameTestHelper helper) {
        ArchitectLabGameTest.runScenario(helper, com.frozendawn.debug.architect.ArchitectLabScenario.LOW_CEILING, 1337, false);
    }

    @GameTest(template = "lab/unreachable_target", timeoutTicks = 310)
    public static void incompleteRouteStillAttemptsLocalEscape(GameTestHelper helper) {
        ArchitectLabGameTest.runScenario(helper, com.frozendawn.debug.architect.ArchitectLabScenario.UNREACHABLE_TARGET, 1337, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 40)
    public static void releasedBreakTargetsReportOutcomesInsteadOfSelections(GameTestHelper helper) {
        GameTestTemplates.placeFloor(helper);
        ArchitectEntity architect = helper.spawnWithNoFreeWill(ModEntities.ARCHITECT.get(), new BlockPos(3, 1, 3));
        ArchitectApproachState state = new ArchitectApproachState();
        ArchitectBlockBreaker breaker = new ArchitectBlockBreaker(architect, pos ->
                ArchitectApproachRecovery.finishBreakAttempt(state, pos,
                        ArchitectBlockEnvironment.isPathObstructingState(helper.getLevel(),
                                helper.getLevel().getBlockState(pos), pos)));
        BlockPos first = helper.absolutePos(new BlockPos(4, 1, 3));
        BlockPos second = helper.absolutePos(new BlockPos(3, 1, 4));
        helper.getLevel().setBlockAndUpdate(first, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(second, Blocks.DIRT.defaultBlockState());
        breaker.setTarget(first);
        helper.assertTrue(state.blockedUnstickBreakCandidates.isEmpty(), "selection counted as failure");
        breaker.setTarget(second);
        helper.assertTrue(state.blockedUnstickBreakCandidates.contains(first), "replaced obstruction was forgotten");
        breaker.clearTarget();
        helper.assertTrue(state.blockedUnstickBreakCandidates.contains(second), "cancelled obstruction was forgotten");
        helper.assertTrue(breaker.successfulBreakCount() == 0, "Cancelled attempts counted as excavation");
        ArchitectApproachRecovery.recordReinit(state);
        breaker.setTarget(second);
        for (int tick = 0; tick < 300 && breaker.hasTarget(); tick++) {
            breaker.tick();
        }
        helper.assertTrue(helper.getLevel().getBlockState(second).isAir(), "real dirt break did not finish");
        helper.assertTrue(state.blockedUnstickBreakCandidates.isEmpty(), "successful break counted as failure");
        helper.assertTrue(breaker.successfulBreakCount() == 1, "Successful destroy did not count exactly once");
        breaker.clearTarget();
        breaker.tick();
        breaker.setTarget(first);
        helper.getLevel().setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
        breaker.tick();
        helper.assertTrue(breaker.successfulBreakCount() == 1, "External removal/release counted as excavation");
        helper.succeed();
    }

    @GameTest(template = "lab/sealed_pocket", timeoutTicks = 860)
    public static void sealedPocketAbandonsAndSuppressesImmediateReacquisition(GameTestHelper helper) {
        ArchitectLabGameTest.runScenario(helper, com.frozendawn.debug.architect.ArchitectLabScenario.SEALED_POCKET, 1337, false);
    }
}
