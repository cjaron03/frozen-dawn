package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveArcherGameTest {
    private static long train(MaeveObservationGameTest.Scene s, MaeveObservationGameTest.TestPlayer p) {
        for (int x = -8; x <= 30; x++) for (int z = -8; z <= 18; z++) s.block(x, -1, z, Blocks.STONE.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var witness = s.architect(2, 4); long now = s.gameTime + 1;
        for (int i = 0; i < 5; i++) { s.clock(now + i * 610L); s.hit(witness, p, false, 1); }
        witness.discard(); s.clock(now + 3050); p.setPos(s.position(20, 4)); return now + 3050;
    }
    private static ArchitectEntity actor(MaeveObservationGameTest.Scene s, MaeveObservationGameTest.TestPlayer p) {
        var a = s.architect(4, 4); a.tickCount = 80; a.setOnGround(true); a.debugForceApproach(p);
        a.startDecisionRecording(1337L); a.decisionJournal().useExtendedLabBuffer(); return a;
    }
    private static void tick(MaeveObservationGameTest.Scene s, ArchitectEntity a, MaeveObservationGameTest.TestPlayer p, long now) {
        s.clock(now); if (now % 20 == 0) MaeveDirector.commitmentHints(a, p); s.level.tickNonPassenger(a);
    }
    private static long shots(ArchitectEntity a) { return a.decisionJournal().entries().stream().filter(e -> e.event().equals("MAEVE_ARCHER_SHOT")).count(); }
    private static void ready(GameTestHelper h, MaeveObservationGameTest.Scene s, ArchitectEntity a, MaeveObservationGameTest.TestPlayer p, long now) {
        for (int i = 0; i < 25; i++) tick(s, a, p, now + i);
        var d = MaeveDirector.positionDirective(a);
        h.assertTrue(d != null && d.keepAwayArcher() && a.getMainHandItem().is(Items.BOW), "Historical sword evidence must equip the archer: " + a.inspectDecisions());
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherFiniteQuiverAndEmptyFallback(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 240, 2, s -> {
            var p = s.player("archer_ammo", 8, 4); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            boolean cue = false;
            for (int i = 25; i <= 700; i++) {
                a.setPos(s.position(4, 4)); a.setDeltaMovement(Vec3.ZERO); tick(s, a, p, now + i);
                cue |= a.getArcherPose() == 2;
            }
            h.assertTrue(shots(a) == 16, "Quiver must fire exactly 16, without reload: " + shots(a));
            h.assertTrue(cue && MaeveDirector.positionDirective(a) == null && !a.getMainHandItem().is(Items.BOW), "Empty tell must yield to ordinary combat");
            var savedActor = new CompoundTag(); a.saveWithoutId(savedActor);
            h.assertTrue(savedActor.getList("TacticalIce", net.minecraft.nbt.Tag.TAG_LONG).isEmpty() && a.getOffhandItem().isEmpty(), "Archer must not spend pillars or a second shield bet");
            var arrows = s.level.getEntitiesOfClass(Arrow.class, a.getBoundingBox().inflate(32), x -> x.getOwner() == a);
            h.assertTrue(arrows.size() == 16 && arrows.stream().allMatch(x -> x.pickup == AbstractArrow.Pickup.DISALLOWED), "Every real generated arrow must forbid pickup");
            arrows.forEach(Arrow::discard);
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherRushSightAndSubjectIsolation(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 241, 2, s -> {
            var p = s.player("archer_rush", 8, 4); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            long before = shots(a); s.wall(true);
            for (int i = 25; i < 60; i++) { a.setPos(s.position(4, 4)); tick(s, a, p, now + i); }
            h.assertTrue(shots(a) == before && !a.isUsingItem(), "Occluded target cannot receive a shot or keep a draw");
            s.wall(false); p.setPos(a.position().add(2, 0, 0)); tick(s, a, p, now + 61);
            h.assertTrue(shots(a) == before && MaeveDirector.positionDirective(a) == null, "Rush inside three blocks must cancel point-blank fire");
            h.assertTrue(a.getOffhandItem().isEmpty(), "Rush cannot spend a second shield bet");
            var other = s.player("archer_untrained", 20, 8); a.discard(); var second = actor(s, other);
            for (int i = 62; i < 90; i++) tick(s, second, other, now + i);
            h.assertTrue(MaeveDirector.positionDirective(second) == null && !second.getMainHandItem().is(Items.BOW), "Another player cannot inherit this subject's bow");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherReloadAndErasureRemoveGeneratedEquipment(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 242, 2, s -> {
            var p = s.player("archer_reload", 8, 4); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            var saved = MaeveSavedData.get(s.server).save(new CompoundTag(), s.level.registryAccess());
            var entity = new CompoundTag(); a.saveWithoutId(entity); a.discard();
            s.storage(MaeveSavedData.load(saved, s.level.registryAccess())); a = actor(s, p); a.load(entity); a.tickCount = 80; a.debugForceApproach(p);
            h.assertTrue(!a.getMainHandItem().is(Items.BOW) && !a.isUsingItem(), "Reload must strip the generated bow and draw");
            for (int i = 25; i < 55; i++) tick(s, a, p, now + i);
            h.assertTrue(MaeveDirector.positionDirective(a) == null, "Reload must not refund the spent quiver");
            a.discard(); s.clock(now + 800); a = actor(s, p); ready(h, s, a, p, now + 800);
            PostMaeveWorldState.setForDebug(s.server, true);
            h.assertTrue(!a.getMainHandItem().is(Items.BOW) && a.getArcherPose() == 0 && !a.isUsingItem(), "Authoritative erasure clears equipment and draw immediately");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherBackpedalsOnSnowAndCannotOutrunRush(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 243, 2, s -> {
            var p = s.player("archer_snow", 8, 4); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            for (int x = -7; x <= 28; x++) for (int z = -7; z <= 17; z++) s.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
            a.setPos(s.position(4, 4).add(0, .25, 0)); p.setPos(s.position(11, 4).add(0, .25, 0));
            double start = a.getX();
            for (int i = 25; i < 45; i++) tick(s, a, p, now + i);
            h.assertTrue(a.getX() < start - .2, "Inside eight blocks the visible archer must backpedal on partial snow");
            h.assertTrue(a.getDeltaMovement().horizontalDistance() <= .121 && a.getY() >= s.origin.getY(), "Existing supported combat speed bounds kiting");
            for (int i = 45; i < 90 && MaeveDirector.positionDirective(a) != null; i++) {
                p.setPos(p.position().add(a.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(.28)));
                tick(s, a, p, now + i);
            }
            h.assertTrue(MaeveDirector.positionDirective(a) == null, "A sprinting approach must close the gap and force melee");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherRealProjectileUsesOrdinaryPunctureAndFinalDamage(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 244, 2, s -> {
            var p = MaeveReconnaissanceGameTest.damageablePlayer(s, "archer_puncture"); p.setPos(s.position(8, 4)); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            for (int i = 25; i < 70 && shots(a) == 0; i++) tick(s, a, p, now + i);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(com.frozendawn.init.ModItems.EVA_HELMET.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(com.frozendawn.init.ModItems.EVA_CHESTPLATE.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(com.frozendawn.init.ModItems.EVA_LEGGINGS.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(com.frozendawn.init.ModItems.EVA_BOOTS.get()));
            h.assertTrue(com.frozendawn.event.SuitIntegrityHandler.isVacuumExposure(p), "Fixture must use the real late-phase puncture gate");
            var chance = com.frozendawn.config.FrozenDawnConfig.SUIT_PUNCTURE_ARCHITECT_CHANCE;
            double oldChance = chance.get();
            try {
                // Deterministically exercise the configured event path, restoring production values below.
                chance.set(1D);
                var arrows = s.level.getEntitiesOfClass(Arrow.class, a.getBoundingBox().inflate(32), x -> x.getOwner() == a);
                h.assertTrue(!arrows.isEmpty(), "Executor must have spawned a real arrow");
                var arrow = arrows.getFirst();
                arrow.setPos(p.getX() - 1, p.getY() + .8, p.getZ()); arrow.setDeltaMovement(2.0, 0, 0);
                float health = p.getHealth(); arrow.tick(); arrow.tick();
                h.assertTrue(p.getHealth() < health, "Real projectile collision must damage the subject");
                var suit = p.getData(com.frozendawn.init.ModAttachments.SUIT_INTEGRITY);
                h.assertTrue(suit.punctures() == 1 && suit.graceTicks() > 0, "Architect arrow must use existing puncture chance and grace");
                p.invulnerableTime = 0;
                var second = new Arrow(s.level, a, new ItemStack(Items.ARROW), a.getMainHandItem());
                second.setBaseDamage(arrow.getBaseDamage());
                s.level.addFreshEntity(second); second.setPos(p.getX() - 1, p.getY() + .8, p.getZ()); second.setDeltaMovement(2.0, 0, 0);
                second.tick(); second.tick(); second.discard();
                h.assertTrue(suit.punctures() == 1, "Ordinary grace must prevent repeated punctures");
                MaeveDirector.releaseCommitment(a, "ARCHER_QUIVER_EMPTY");
                var row = MaeveSavedData.get(s.server).store().commitment(p.getUUID()).performance().save()
                        .getList("contexts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
                h.assertTrue(row.getString("pattern").equals(CounterVariantPolicy.ARCHER) && row.getInt("successes") == 1
                                && row.getList("results", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getFloat("dealt") > 0,
                        "Actual final projectile damage must score the archer variant");
                arrows.forEach(Arrow::discard);
            } finally { chance.set(oldChance); }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveArcherDamageAndRoleExclusions(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 245, 2, s -> {
            var p = s.player("archer_cleanup", 8, 4); long now = train(s, p); var a = actor(s, p); ready(h, s, a, p, now);
            s.hit(a, p, true, 1);
            h.assertTrue(!a.getMainHandItem().is(Items.BOW) && !a.isUsingItem() && MaeveDirector.positionDirective(a) == null,
                    "Effective hit must hand back to self-defense without a stuck draw");
            a.discard(); s.clock(now + 800); var master = actor(s, p);
            master.bindToHearthMasterArchitect(java.util.UUID.randomUUID(), s.origin, 0);
            for (int i = 0; i < 25; i++) tick(s, master, p, now + 800 + i);
            h.assertTrue(MaeveDirector.positionDirective(master) == null && !master.getMainHandItem().is(Items.BOW), "Master stays independent");
            master.discard();
        });
    }

}
