package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveMantletBreachGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void brokenMantletResumesCombatWithoutBeingHit(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 147, scene -> {
            var fight = ready(helper, scene);
            var cell = MaeveDirector.positionDirective(fight.actor()).cover().above();
            Vec3 before = fight.actor().position();
            // Break actual owned ice, then wait on its open flank without hitting the builder.
            scene.level.destroyBlock(cell, false);
            fight.player().setPos(before.add(0, 0, -4));
            MaeveMantletGameTest.tick(scene, fight, 65);
            assertReleased(helper, scene, fight, "LOCAL_MANTLET_BREACHED");
            assertCombat(helper, scene, fight, before);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletMiningStartResumesCombatBeforeScreenBreaks(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 159, scene -> {
            var fight = ready(helper, scene); var actor = fight.actor();
            var cell = MaeveDirector.positionDirective(actor).cover().above();
            Vec3 before = actor.position();
            fight.player().setPos(Vec3.atBottomCenterOf(cell).add(2, -.75, 0));
            helper.assertTrue(!actor.hasLineOfSight(fight.player()), "The screen hides the miner, while its own attacked cell is visible");
            var belief = MaeveDirector.snapshot(scene.server, fight.player().getUUID());
            mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(scene.level.getBlockState(cell).is(Blocks.PACKED_ICE), "React at mining start, before the real block breaks");
            assertReleased(helper, scene, fight, "LOCAL_MANTLET_MINED");
            helper.assertTrue(actor.getTarget() == fight.player(), "Wall damage cannot assign a hidden miner as a new target");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, fight.player().getUUID()).equals(belief), "Mining is local execution, not fabricated belief evidence");
            assertCombat(helper, scene, fight, before);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletMiningRejectsHiddenUnrelatedAndDeniedActions(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 160, scene -> {
            var fight = ready(helper, scene); var actor = fight.actor();
            var directive = MaeveDirector.positionDirective(actor); var cell = directive.cover().above();
            // Server interaction path must reject out-of-reach and restricted attempts.
            mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Out-of-reach packets cannot release cover");
            fight.player().setPos(Vec3.atBottomCenterOf(cell).add(2, -.75, 0));
            fight.player().setGameMode(GameType.ADVENTURE);
            mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Adventure restrictions apply before the cue");
            fight.player().setGameMode(GameType.SURVIVAL);
            Consumer<PlayerInteractEvent.LeftClickBlock> cancel = event -> { if (event.getEntity() == fight.player()) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(cancel);
            try { mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Canceled mining cannot release cover");
            Consumer<PlayerInteractEvent.LeftClickBlock> deny = event -> { if (event.getEntity() == fight.player()) event.setUseItem(TriState.FALSE); };
            NeoForge.EVENT_BUS.addListener(deny);
            try { mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK); }
            finally { NeoForge.EVENT_BUS.unregister(deny); }
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Denied item use cannot release cover");
            mine(fight, cell, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
            mine(fight, cell, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Stopping/aborting mining is not a new start");
            BlockPos occluder = cell.west();
            scene.block(occluder.getX() - scene.origin.getX(), occluder.getY() - scene.origin.getY(), occluder.getZ() - scene.origin.getZ(), Blocks.STONE.defaultBlockState());
            mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "An occluded owned cell cannot supply the mining cue");
            scene.level.setBlock(occluder, Blocks.AIR.defaultBlockState(), 3);
            BlockPos unrelated = cell.east(2).south(2);
            scene.block(unrelated.getX() - scene.origin.getX(), unrelated.getY() - scene.origin.getY(), unrelated.getZ() - scene.origin.getZ(), Blocks.PACKED_ICE.defaultBlockState());
            mine(fight, unrelated, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Unowned ice cannot release this mantlet");
            mine(fight, cell, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            assertReleased(helper, scene, fight, "LOCAL_MANTLET_MINED");
        });
    }

    private static MaeveMantletGameTest.Fight ready(GameTestHelper helper, MaeveObservationGameTest.Scene scene) {
        var fight = MaeveMantletGameTest.prepare(scene, 3, true);
        for (int t = 0; t < 65; t++) MaeveMantletGameTest.tick(scene, fight, t);
        var directive = MaeveDirector.positionDirective(fight.actor());
        helper.assertTrue(directive != null && directive.advancingCover(), "Five real historical bow hits must select a real mantlet");
        helper.assertTrue(fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).count() == 4, "Fixture completed the first screen");
        return fight;
    }

    private static void mine(MaeveMantletGameTest.Fight fight, BlockPos pos, ServerboundPlayerActionPacket.Action action) {
        fight.player().gameMode.handleBlockBreakAction(pos, action, Direction.EAST, fight.player().level().getMaxBuildHeight(), 0);
    }

    private static void assertReleased(GameTestHelper helper, MaeveObservationGameTest.Scene scene, MaeveMantletGameTest.Fight fight, String reason) {
        helper.assertTrue(MaeveDirector.positionDirective(fight.actor()) == null && !fight.actor().isHoldingMaevePosition(), "Release execution immediately: " + reason);
        var snapshot = MaeveDirector.commitmentSnapshot(scene.server, fight.player().getUUID());
        helper.assertTrue(snapshot.issued() && snapshot.outcome().equals(reason), "The original bet stays spent: " + snapshot);
    }

    private static void assertCombat(GameTestHelper helper, MaeveObservationGameTest.Scene scene, MaeveMantletGameTest.Fight fight, Vec3 before) {
        for (int t = 66; t < 240 && fight.player().getHealth() == fight.player().getMaxHealth(); t++) MaeveMantletGameTest.tick(scene, fight, t);
        helper.assertTrue(fight.actor().position().distanceTo(before) > .5 && fight.player().getHealth() < fight.player().getMaxHealth(),
                "Physically pursue and land a real melee hit without first being hit: " + fight.actor().decisionJournal().entries());
        helper.assertTrue(fight.actor().getHealth() == fight.actor().getMaxHealth(), "No damage trigger released this hold");
        helper.assertTrue(MaeveDirector.positionDirective(fight.actor()) == null, "Cannot buy a replacement commitment");
        helper.assertTrue(fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).count() == 4, "No repairs or later screens after the break/mining cue");
    }
}
