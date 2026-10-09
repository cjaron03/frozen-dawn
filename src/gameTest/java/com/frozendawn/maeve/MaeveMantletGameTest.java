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
        return prepare(scene, snow, false);
    }
    static Fight prepare(MaeveObservationGameTest.Scene scene, int snow, boolean damageable) {
        for (int x = -3; x <= 22; x++) for (int z = -3; z <= 14; z++) {
            scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 4; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
            if (snow > 0) scene.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, snow));
        }
        double surface = snow == 0 ? 0 : (snow - 1) / 8.0;
        var player = damageable ? MaeveReconnaissanceGameTest.damageablePlayer(scene, "mantlet") : scene.player("mantlet", 18, 6);
        player.setPos(scene.position(18, 6).add(0, surface, 0));
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
            Arrow lodged = null;
            for (int t = 0; t < 395; t++) {
                tick(scene, fight, t);
                if (lodged != null && !lodged.isRemoved()) scene.level.tickNonPassenger(lodged);
                if (!shot && actor.decisionJournal().entries().stream().anyMatch(e -> e.event().equals("MANTLET_SCREEN"))) {
                    float hp = actor.getHealth(); var arrow = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
                    arrow.setPos(fight.player().getEyePosition());
                    arrow.setDeltaMovement(actor.position().add(0, 1.25, 0).subtract(arrow.position()).normalize().scale(2));
                    scene.level.addFreshEntity(arrow); scene.entities.add(arrow);
                    for (int i = 0; i < 15; i++) scene.level.tickNonPassenger(arrow);
                    helper.assertTrue(actor.getHealth() == hp && arrow.saveWithoutId(new CompoundTag()).getBoolean("inGround")
                            && arrow.saveWithoutId(new CompoundTag()).getCompound("inBlockState").getString("Name").equals("minecraft:packed_ice"), "An actual arrow must collide with the built front: hp=" + hp + " -> " + actor.getHealth() + " actor=" + actor.position() + " arrow=" + arrow.saveWithoutId(new CompoundTag()) + " blocks=" + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
                    lodged = arrow; shot = true;
                }
            }
            helper.assertTrue(actor.getHealth() == actor.getMaxHealth(),
                    "Retiring its own screen must not release its stopped arrow into the advancing builder: "
                            + actor.decisionJournal().entries().stream().filter(e -> e.event().equals("DAMAGE")).toList());
            var blocks = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_BLOCK")).toList();
            var screens = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_SCREEN")).toList();
            helper.assertTrue(shot && blocks.size() == 20 && screens.size() == 5, "Expected five real four-block screens: " + blocks + " screens=" + screens + " actor=" + actor.position() + " policy=" + MaeveDirector.commitmentSnapshot(scene.server, fight.player().getUUID()) + " journal=" + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
            for (int i = 1; i < blocks.size(); i++) helper.assertTrue(blocks.get(i).tick() - blocks.get(i - 1).tick() >= 10, "Placement cadence stays bounded");
            helper.assertTrue(actor.getX() - initial.x >= 7.7, "Builder physically advances behind successive screens");
            helper.assertTrue(actor.saveWithoutId(new CompoundTag()).getList("MantletIce", Tag.TAG_LONG).size() == 20, "Retirement does not refund the separate mantlet pool");
            helper.assertTrue(MaeveDirector.positionDirective(actor).advancingCover(), "Still the original 20-second bet");
            helper.assertTrue(lodged.isRemoved(), "A lodged arrow must leave projectile form before its supporting ice retires");
            var pickups = scene.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, actor.getBoundingBox().inflate(15));
            scene.entities.addAll(pickups);
            helper.assertTrue(pickups.size() == 1 && pickups.getFirst().getItem().is(Items.ARROW), "Retiring ice preserves the recoverable arrow item");
            System.out.println("MACS_MANTLET_CHECK screens=" + screens.size() + " blocks=" + blocks.size() + " advance=" + (actor.getX() - initial.x));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletRetiresLodgedArrowsWithoutSelfDamage(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 152, scene -> {
            var fight = prepare(scene, 2); var actor = fight.actor();
            var arrows = new java.util.ArrayList<Arrow>();
            for (int t = 0; t < 260; t++) {
                tick(scene, fight, t);
                for (var arrow : arrows) if (!arrow.isRemoved()) scene.level.tickNonPassenger(arrow);
                if (t == 60) {
                    for (double height : new double[]{.15, .4, .7, 1, 1.3, 1.6}) {
                        var arrow = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
                        arrow.getRandom().setSeed(1337L);
                        arrow.setPos(fight.player().getEyePosition());
                        arrow.setDeltaMovement(actor.position().add(0, height, 0).subtract(arrow.position()).normalize().scale(3));
                        scene.level.addFreshEntity(arrow); scene.entities.add(arrow); arrows.add(arrow);
                        for (int i = 0; i < 10; i++) scene.level.tickNonPassenger(arrow);
                        helper.assertTrue(arrow.saveWithoutId(new CompoundTag()).getBoolean("inGround"), "Fixture arrow must lodge at height " + height);
                    }
                }
            }
            scene.entities.addAll(scene.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, actor.getBoundingBox().inflate(15)));
            helper.assertTrue(actor.getHealth() == actor.getMaxHealth(), "Stopped arrows must not damage the advancing builder: "
                    + actor.decisionJournal().entries().stream().filter(e -> e.event().equals("DAMAGE")).toList());
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletBudgetIsSeparatePersistentAndBounded(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 153, scene -> {
            var fight = prepare(scene, 2); var actor = fight.actor();
            CompoundTag seeded = actor.saveWithoutId(new CompoundTag());
            var ordinary = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 12; i++) {
                var p = scene.origin.offset(i, 0, 12);
                scene.block(i, 0, 12, Blocks.PACKED_ICE.defaultBlockState());
                ordinary.add(net.minecraft.nbt.LongTag.valueOf(p.asLong()));
            }
            seeded.put("TacticalIce", ordinary); actor.readAdditionalSaveData(seeded);
            for (int t = 0; t < 395; t++) tick(scene, fight, t);
            var saved = actor.saveWithoutId(new CompoundTag());
            helper.assertTrue(saved.getList("MantletIce", Tag.TAG_LONG).size() == 20, "A full ordinary pool cannot consume the mantlet allowance");
            helper.assertTrue(saved.getList("TacticalIce", Tag.TAG_LONG).equals(ordinary), "Mantlet never spends or evicts ordinary cover");
            for (int i = 0; i < 12; i++) helper.assertTrue(scene.level.getBlockState(scene.origin.offset(i, 0, 12)).is(Blocks.PACKED_ICE), "Ordinary cover stays intact");
            var reloaded = scene.architect(2, 10); reloaded.readAdditionalSaveData(saved);
            helper.assertTrue(reloaded.saveWithoutId(new CompoundTag()).getList("MantletIce", Tag.TAG_LONG).size() == 20, "Reload preserves spent allowance and cleanup ownership");
            reloaded.hurt(scene.level.damageSources().genericKill(), Float.MAX_VALUE);
            for (int t = 0; t < 31; t++) scene.level.tickNonPassenger(reloaded);
            for (var value : saved.getList("MantletIce", Tag.TAG_LONG))
                helper.assertTrue(!scene.level.getBlockState(net.minecraft.core.BlockPos.of(((net.minecraft.nbt.LongTag) value).getAsLong())).is(Blocks.PACKED_ICE), "Death cleans up retained mantlet blocks");
            var legacy = scene.architect(2, 11); var legacyTag = seeded.copy(); legacyTag.remove("MantletIce");
            legacy.readAdditionalSaveData(legacyTag);
            helper.assertTrue(legacy.saveWithoutId(new CompoundTag()).getList("MantletIce", Tag.TAG_LONG).isEmpty(), "Legacy entities start without mantlet allowance spent");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletRetirementPreservesFreshArrowCounterplay(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 154, scene -> {
            var fight = prepare(scene, 2); var actor = fight.actor();
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            var cover = MaeveDirector.positionDirective(actor).cover();
            var lodged = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
            lodged.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
            lodged.setPos(fight.player().getEyePosition());
            lodged.setDeltaMovement(actor.position().add(0, 1.25, 0).subtract(lodged.position()).normalize().scale(2));
            scene.level.addFreshEntity(lodged); scene.entities.add(lodged);
            for (int t = 0; t < 15; t++) scene.level.tickNonPassenger(lodged);
            helper.assertTrue(lodged.saveWithoutId(new CompoundTag()).getBoolean("inGround"), "Control arrow must be lodged");
            // A projectile crossing the same query box must not be treated as embedded.
            var flying = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
            flying.setPos(Vec3.atCenterOf(cover.above()));
            scene.level.addFreshEntity(flying); scene.entities.add(flying);
            for (int t = 60; t < 150; t++) {
                tick(scene, fight, t);
                if (!lodged.isRemoved()) scene.level.tickNonPassenger(lodged);
            }
            helper.assertTrue(lodged.isRemoved() && !flying.isRemoved(), "Retirement processes only the stopped arrow");
            var drops = scene.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, actor.getBoundingBox().inflate(15));
            scene.entities.addAll(drops);
            helper.assertTrue(drops.isEmpty(), "An Infinity/creative-only arrow must not become a survival pickup");
            flying.discard();
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Retirement preserves the original commitment");
            var flank = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
            flank.setPos(actor.position().add(0, 1.2, -3));
            flank.setDeltaMovement(0, 0, 1);
            scene.level.addFreshEntity(flank); scene.entities.add(flank);
            for (int t = 0; t < 5 && !flank.isRemoved(); t++) scene.level.tickNonPassenger(flank);
            helper.assertTrue(actor.getHealth() < actor.getMaxHealth() && MaeveDirector.positionDirective(actor) == null,
                    "A fresh arrow through the flank still damages and ends the spent mantlet");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletArrowQueryOverflowLeavesOldScreenIntact(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 155, scene -> {
            var fight = prepare(scene, 2); var actor = fight.actor();
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            var cover = MaeveDirector.positionDirective(actor).cover();
            var arrows = new java.util.ArrayList<Arrow>();
            for (int i = 0; i < 33; i++) {
                var arrow = new Arrow(scene.level, fight.player(), new ItemStack(Items.ARROW), null);
                arrow.setPos(Vec3.atCenterOf(cover)); scene.level.addFreshEntity(arrow); scene.entities.add(arrow); arrows.add(arrow);
            }
            for (int t = 60; t < 180 && MaeveDirector.positionDirective(actor) != null; t++) tick(scene, fight, t);
            helper.assertTrue(actor.decisionJournal().entries().stream().anyMatch(e -> e.event().equals("MANTLET_RELEASED") && e.detail().contains("ARROW_LIMIT")),
                    "Overfull query stops safely at the configured bound: " + actor.decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
            helper.assertTrue(scene.level.getBlockState(cover).is(Blocks.PACKED_ICE) && arrows.stream().noneMatch(Arrow::isRemoved),
                    "Overflow must not dismantle the old screen or partially process arrows");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletUsesVisibleFrontAndKeepsItWhenPlayerFlanks(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 156, scene -> {
            var original = prepare(scene, 2); var actor = original.actor(); var player = original.player();
            // Last fight ended near this actor's new spawn, while the new fight starts at range.
            scene.clock(original.start() + 610);
            player.setPos(scene.position(3, 1));
            var witness = scene.architect(2, 1); scene.hit(witness, player, true, 1); witness.discard();
            player.setPos(scene.position(18, 6).add(0, .125, 0));
            var fight = new Fight(actor, player, original.start() + 1220);
            Vec3 start = actor.position();
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            var directive = MaeveDirector.positionDirective(actor);
            helper.assertTrue(directive != null && directive.advancingCover(), "A nearby historical firing point cannot reject the currently visible ranged subject");
            helper.assertTrue(directive.evidence().position().equals(scene.origin.offset(3, 0, 1)), "Selection must still retain its actual historical evidence");
            helper.assertTrue(directive.cover().getX() > start.x && actor.isHoldingRangedCover(), "The east-facing front uses the cover-ready hold cue");
            player.setPos(scene.position(1, 12).add(0, .125, 0));
            for (int t = 60; t < 395; t++) tick(scene, fight, t);
            helper.assertTrue(actor.getX() - start.x >= 7.7 && Math.abs(actor.getZ() - start.z) < 1,
                    "Flanking cannot turn the fixed screen corridor toward the new player position");
            helper.assertTrue(MaeveDirector.positionDirective(actor).cover().equals(directive.cover()), "The same original bet and front remain fixed");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletCannotOrientTowardAnOccludedSubject(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 157, scene -> {
            var fight = prepare(scene, 2); var actor = fight.actor();
            // Outside the entire construction corridor: only the subject's visibility changes.
            for (int y = 0; y <= 4; y++) for (int z = 0; z <= 14; z++) scene.block(15, y, z, Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!actor.hasLineOfSight(fight.player()), "Subject must actually be hidden behind the wall");
            for (int t = 0; t < 60; t++) tick(scene, fight, t);
            helper.assertTrue(actor.decisionJournal().entries().stream().noneMatch(e -> e.event().equals("MANTLET_STARTED")),
                    "Historical bow confidence cannot provide an unseen subject's current position");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletWorksOnEverySnowDepth(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 146, scene -> {
            for (int depth = 1; depth <= 8; depth++) {
                var fight = prepare(scene, depth); Vec3 start = fight.actor().position();
                for (int t = 0; t < 395; t++) tick(scene, fight, t);
                long screens = fight.actor().decisionJournal().entries().stream().filter(e -> e.event().equals("MANTLET_SCREEN")).count();
                helper.assertTrue(screens == 5 && fight.actor().getX() - start.x >= 7.7, "Snow depth " + depth + " must allow actual screened advance; screens=" + screens + " actor=" + fight.actor().position() + " events=" + fight.actor().decisionJournal().entries().stream().filter(e -> e.event().startsWith("MANTLET")).toList());
                fight.actor().discard(); fight.player().discard();
            }
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
