package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveMantletGameTest {
    record Fight(ArchitectEntity actor, MaeveObservationGameTest.TestPlayer player, long start) { }
    static Fight prepare(MaeveObservationGameTest.Scene scene, int snow) {
        for (int x = -3; x <= 22; x++) for (int z = -3; z <= 14; z++) {
            scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 4; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
            if (snow > 0) scene.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, snow));
        }
        double surface = snow == 0 ? 0 : (snow - 1) / 8.0;
        var player = scene.player("mantlet", 18, 6); player.setPos(player.position().add(0, surface, 0));
        var witness = scene.architect(2, 6); witness.setPos(witness.position().add(0, surface, 0));
        long time = scene.gameTime + 1;
        for (int i = 0; i < 5; i++) { scene.clock(time + i * 610); scene.hit(witness, player, true, 1); }
        witness.discard(); scene.clock(time + 3050);
        var actor = scene.architect(2, 6); actor.setPos(actor.position().add(0, surface, 0));
        actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
        actor.startDecisionRecording(UUID.randomUUID(), 1337L, Rotation.NONE); actor.decisionJournal().useExtendedLabBuffer();
        return new Fight(actor, player, time + 3050);
    }
    static void tick(MaeveObservationGameTest.Scene scene, Fight fight, int tick) {
        scene.clock(fight.start() + tick); scene.level.tickNonPassenger(fight.actor());
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletAdvancesScreensAndStopsActualArrows(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 145, scene -> {
            var fight = prepare(scene, 0); var actor = fight.actor(); Vec3 initial = actor.position();
            boolean shot = false;
            for (int t = 0; t < 320; t++) {
                tick(scene, fight, t);
                if (!shot && actor.decisionJournal().entries().stream().anyMatch(e -> e.event().equals("MANTLET_SCREEN"))) {
                    float hp = actor.getHealth(); var arrow = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
                    arrow.setPos(fight.player().getEyePosition());
                    arrow.setDeltaMovement(actor.position().add(0, 1.25, 0).subtract(arrow.position()).normalize().scale(2));
                    scene.level.addFreshEntity(arrow); scene.entities.add(arrow);
                    for (int i = 0; i < 15; i++) scene.level.tickNonPassenger(arrow);
                    helper.assertTrue(actor.getHealth() == hp && arrow.saveWithoutId(new CompoundTag()).getBoolean("inGround")
                            && arrow.saveWithoutId(new CompoundTag()).getCompound("inBlockState").getString("Name").equals("minecraft:packed_ice"), "An actual arrow must collide with the built front: hp=" + hp + " -> " + actor.getHealth() + " actor=" + actor.position() + " arrow=" + arrow.saveWithoutId(new CompoundTag()) + " blocks=" + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
                    arrow.discard(); shot = true;
                }
            }
            var blocks = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).toList();
            var screens = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_SCREEN")).toList();
            helper.assertTrue(shot && blocks.size() == 12 && screens.size() == 3, "Expected three real four-block screens: " + blocks + " screens=" + screens + " actor=" + actor.position() + " policy=" + MaeveDirector.commitmentSnapshot(scene.server, fight.player().getUUID()) + " journal=" + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
            for (int i = 1; i < blocks.size(); i++) helper.assertTrue(blocks.get(i).tick() - blocks.get(i - 1).tick() >= 10, "Placement cadence stays bounded");
            helper.assertTrue(actor.getX() - initial.x >= 3.7, "Builder physically advances behind successive screens");
            helper.assertTrue(actor.saveWithoutId(new CompoundTag()).getList("TacticalIce", Tag.TAG_LONG).size() == 12, "Retirement does not refund the shared pool");
            helper.assertTrue(MaeveDirector.positionDirective(actor).advancingCover(), "Still the original 20-second bet");
            System.out.println("MACS_MANTLET_CHECK screens=" + screens.size() + " blocks=" + blocks.size() + " advance=" + (actor.getX() - initial.x));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletWorksOnEverySnowDepth(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 146, scene -> {
            for (int depth = 1; depth <= 8; depth++) {
                var fight = prepare(scene, depth); Vec3 start = fight.actor().position();
                for (int t = 0; t < 300; t++) tick(scene, fight, t);
                long screens = fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_SCREEN")).count();
                helper.assertTrue(screens == 3 && fight.actor().getX() - start.x >= 3.7, "Snow depth " + depth + " must allow actual screened advance; screens=" + screens + " actor=" + fight.actor().position() + " events=" + fight.actor().decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
                fight.actor().discard(); fight.player().discard();
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void brokenMantletDoesNotRepairAndDamageReleasesSpentBet(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 147, scene -> {
            var fight = prepare(scene, 3); var actor = fight.actor();
            for (int t = 0; t < 65; t++) tick(scene, fight, t);
            var directive = MaeveDirector.positionDirective(actor);
            helper.assertTrue(directive != null && directive.advancingCover(), "Must select a real mantlet: " + MaeveDirector.commitmentSnapshot(scene.server, fight.player().getUUID()) + " journal=" + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
            scene.level.destroyBlock(directive.cover().above(), false);
            Vec3 stopped = actor.position();
            for (int t = 65; t < 200; t++) tick(scene, fight, t);
            helper.assertTrue(actor.decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).count() == 4,
                    "Breaking the first screen cannot trigger repairs or a second screen");
            helper.assertTrue(actor.position().distanceTo(stopped) < .4, "Broken cover stops the committed advance");
            fight.player().setPos(actor.position().add(0, 0, 2));
            scene.hit(actor, fight.player(), false, 1);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Actual flanking damage snaps it out of the hold");
            tick(scene, fight, 201);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Spent mantlet cannot instantly switch to pillars");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletReloadAndErasureStopConstruction(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 148, scene -> {
            var fight = prepare(scene, 0);
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            var store = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(store, scene.level.registryAccess()));
            tick(scene, fight, 60);
            helper.assertTrue(MaeveDirector.positionDirective(fight.actor()) == null && !fight.actor().isHoldingMaevePosition(), "Reload releases the executor and keeps the bet spent");
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(MaeveSavedData.get(scene.server).store() == null, "Erasure retains no variant results");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletLocalObstructionFallsBackAndSubjectChangeSpendsBet(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 150, scene -> {
            var blocked = prepare(scene, 0);
            // Block only a later screen. The ordinary first pillar remains physically legal.
            for (int y = 0; y <= 3; y++) scene.block(8, y, 7, Blocks.BEDROCK.defaultBlockState());
            for (int t = 0; t < 80; t++) tick(scene, blocked, t);
            var view = MaeveDirector.commitmentSnapshot(scene.server, blocked.player().getUUID());
            helper.assertTrue(view.selected() != null && !view.selected().advancingCover(),
                    "An obstructed future screen must leave the existing pillar variant available");
            helper.assertTrue(scene.level.getBlockState(scene.origin.offset(8, 0, 7)).is(Blocks.BEDROCK), "Mantlet preflight cannot alter existing terrain");
            blocked.actor().discard(); blocked.player().discard();

            var fight = prepare(scene, 0);
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            helper.assertTrue(MaeveDirector.positionDirective(fight.actor()).advancingCover(), "Second subject receives its own real mantlet");
            var other = scene.player("mantlet_other", 18, 10);
            fight.actor().debugForceApproach(other);
            tick(scene, fight, 60);
            helper.assertTrue(MaeveDirector.positionDirective(fight.actor()) == null, "Changing local targets releases the subject-specific plan");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, fight.player().getUUID()).issued(), "Subject change cannot refund the first subject's bet");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, other.getUUID()).beliefs().isEmpty(), "Other players inherit no ranged history");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletErasureImmediatelyStopsActiveBuild(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 151, scene -> {
            var fight = prepare(scene, 0);
            for (int t = 0; t < 50; t++) tick(scene, fight, t);
            helper.assertTrue(MaeveDirector.positionDirective(fight.actor()).advancingCover(), "Erasure fixture must have an active mantlet");
            long count = fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).count();
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(MaeveDirector.positionDirective(fight.actor()) == null && !fight.actor().isHoldingMaevePosition(), "Erasure immediately releases active execution");
            for (int t = 50; t < 200; t++) tick(scene, fight, t);
            helper.assertTrue(fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).count() == count,
                    "No pending screen may be placed after erasure");
        });
    }
}
