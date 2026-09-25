package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native damage, shield mitigation, entity AI and persistence exercise the complete counter. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveSwordGuardGameTest {
    private static MaeveDirector.BeliefSnapshot sword(MaeveObservationGameTest.Scene scene,
                                                       MaeveObservationGameTest.TestPlayer player) {
        return scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.SWORD)).findFirst().orElseThrow();
    }

    private static long train(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 10000, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var witness = scene.architect(2, 4);
        long now = scene.gameTime + 1;
        for (int i = 0; i < 4; i++) {
            scene.clock(now + 610L * i);
            scene.hit(witness, player, false, 1);
        }
        witness.discard();
        scene.clock(now + 2440);
        return now + 2440;
    }

    private static ArchitectEntity actor(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player) {
        var actor = scene.architect(4, 4);
        actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(0, -.08, 0); actor.debugForceApproach(player);
        actor.setYRot(-90); actor.setYHeadRot(-90); actor.setYBodyRot(-90);
        actor.startDecisionRecording(1337L); actor.decisionJournal().useExtendedLabBuffer();
        return actor;
    }

    private static void ticks(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long first, int count) {
        for (int i = 0; i < count; i++) { scene.clock(first + i); actor.tick(); }
    }

    private static long awaitGuard(GameTestHelper helper, MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long first) {
        for (int i = 0; i < 60; i++) {
            scene.clock(first + i); actor.tick();
            var directive = MaeveDirector.positionDirective(actor);
            if (directive != null && directive.arrivedAt() >= 0) return directive.arrivedAt();
        }
        helper.assertTrue(false, "Guard must be admitted by the coarse planner: " + actor.decisionJournal().entries());
        return first;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordEvidenceRequiresRealVisibleAttacks(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 110, scene -> {
            var player = scene.player("sword_witness", 8, 4);
            var observer = scene.architect(2, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            MaeveDirector.observePresence(observer, player);
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> b.pattern().equals(BeliefStore.SWORD)),
                    "Holding a sword is not evidence");
            scene.wall(true); scene.hit(observer, player, false, 1); scene.wall(false);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Hidden damage supplies no weapon knowledge");
            Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == observer) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(cancel);
            try { scene.hit(observer, player, false, 1); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Canceled damage cannot teach sword preference");
            scene.hit(observer, player, false, 1); scene.hit(scene.architect(3, 4), player, false, 1);
            helper.assertTrue(sword(scene, player).confidence() == .2 && sword(scene, player).evidence() == 1,
                    "Two witnesses share the one supporting encounter contribution");
            helper.assertTrue(sword(scene, player).provenance().getFirst().action().contains("weapon=minecraft:iron_sword"),
                    "Evidence names the actual attacking weapon");
            var other = scene.player("sword_other", 8, 7);
            scene.hit(observer, other, true, 1);
            helper.assertTrue(scene.beliefs(other).stream().noneMatch(b -> b.pattern().equals(BeliefStore.SWORD)),
                    "A different archer's negative-only history cannot manufacture sword knowledge");
            scene.hit(observer, player, true, 1);
            helper.assertTrue(sword(scene, player).confidence() == 0 && sword(scene, player).contradictions() == 1,
                    "A witnessed projectile contradicts the positive sword belief");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            scene.hit(observer, player, false, 1);
            helper.assertTrue(sword(scene, player).contradictions() == 1, "Non-sword melee shares the encounter contradiction limit");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(sword(scene, player).evidence() == 1 && sword(scene, player).contradictions() == 1,
                    "Sword identity and deduplication survive the existing versioned save");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardUsesLaggedHistoryAndRealFrontalBlocking(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 111, scene -> {
            var player = scene.player("sword_block", 8, 4);
            long now = train(scene, player);
            var actor = actor(scene, player);
            now = awaitGuard(helper, scene, actor, now);
            var directive = MaeveDirector.positionDirective(actor);
            helper.assertTrue(directive != null && directive.pattern().equals(BeliefStore.SWORD)
                            && directive.confidence() == .8 && directive.evidence().time() < now,
                    "Prior ordinary attacks naturally choose the sword counter: " + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(!actor.isBlocking(), "Vanilla shield raise startup must remain vulnerable");
            ticks(scene, actor, now + 1, 12);
            helper.assertTrue(actor.isBlocking() && actor.getOffhandItem().is(Items.SHIELD), "Actual offhand item reaches native blocking state");
            float health = actor.getHealth();
            var motion = actor.getDeltaMovement();
            scene.hit(actor, player, false, 6); scene.hit(actor, player, false, 6);
            helper.assertTrue(actor.getHealth() == health && actor.getOffhandItem().getDamageValue() == 14,
                    "Frontal sword hits are prevented and consume vanilla shield durability");
            helper.assertTrue(actor.getDeltaMovement().distanceToSqr(motion) < 1.0E-12,
                    "Fully blocked sword hits must add no horizontal or vertical knockback: before=" + motion
                            + " after=" + actor.getDeltaMovement());
            helper.assertTrue(sword(scene, player).evidence() == 5 && sword(scene, player).confidence() == 1,
                    "Blocked sword contacts are visible evidence but remain deduplicated");
            ticks(scene, actor, now + 13, 2);
            var position = actor.position();
            ticks(scene, actor, now + 15, 19);
            helper.assertTrue(actor.position().subtract(position).horizontalDistanceSqr() < .01 && actor.isBlocking(),
                    "Raised guard pays its pursuit cost: moved=" + actor.position().subtract(position).horizontalDistanceSqr()
                            + " blocking=" + actor.isBlocking() + " journal=" + actor.decisionJournal().entries());
            ticks(scene, actor, now + 34, 2);
            helper.assertTrue(!actor.isUsingItem(), "The exposed window really lowers the shield");
            player.setPos(scene.position(10, 4));
            ticks(scene, actor, now + 36, 20);
            helper.assertTrue(actor.position().distanceToSqr(position) > .05,
                    "The exposed window permits ordinary pursuit");
            player.setPos(actor.position().add(3, 0, 0));
            for (int i = 56; i < 125 && !actor.isBlocking(); i++) ticks(scene, actor, now + i, 1);
            helper.assertTrue(actor.isBlocking(), "Close combat returns to a raised guard after bounded attack openings");
            scene.clock(directive.arrivedAt() + 400); actor.tick();
            helper.assertTrue(actor.getOffhandItem().is(Items.SHIELD) && MaeveDirector.positionDirective(actor) != null,
                    "The same shield remains available beyond the former twenty-second limit");
            MaeveDirector.releaseCommitment(actor, "TEST_COMPLETE");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(s -> s.contains("blocked=12.0")),
                    "Prevented damage remains separate in the diagnostic result");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardBracesBlockedAttacksButNotExternalForces(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 118, scene -> {
            var player = scene.player("sword_bracing", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            helper.assertTrue(actor.isBlocking(), "Bracing begins with an actual raised MACS shield");
            player.setPos(actor.position().add(2, 0, 0));
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).setBaseValue(100);
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(7);
            player.setSprinting(true);
            helper.assertTrue(player.getAttackStrengthScale(.5F) > .9F, "The real sprint attack is fully charged");
            actor.setOnGround(true); actor.setDeltaMovement(.03, -.08, .02);
            var motion = actor.getDeltaMovement(); float health = actor.getHealth();
            actor.invulnerableTime = 0; player.attack(actor);
            helper.assertTrue(actor.getHealth() == health && actor.getOffhandItem().getDamageValue() > 0
                            && actor.getDeltaMovement().distanceToSqr(motion) < 1.0E-12,
                    "A real blocked sprint sword attack must not add knockback or erase preexisting motion");
            scene.hit(actor, player, true, 6);
            helper.assertTrue(actor.getHealth() == health && actor.getDeltaMovement().distanceToSqr(motion) < 1.0E-12,
                    "A frontal blocked projectile must not add vertical or horizontal knockback");
            actor.knockback(.5, 1, 0);
            helper.assertTrue(actor.getDeltaMovement().distanceToSqr(motion) > .01,
                    "A raised shield must not suppress external forces outside the blocked damage transaction");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardRearDamageStaggersAndAxeDisableSpendsBet(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 112, scene -> {
            var player = scene.player("sword_counterplay", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            now = awaitGuard(helper, scene, actor, now); ticks(scene, actor, now + 1, 12);
            helper.assertTrue(actor.isBlocking(), "Counterplay begins against an actual raised shield");
            player.setPos(actor.position().add(-2, 0, 0));
            float facing = actor.getYRot(); scene.clock(now + 13); actor.tick();
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(actor.getYRot() - facing)) <= 10.01F,
                    "A sudden flank cannot induce a perfect instant shield turn");
            actor.setOnGround(true); actor.setDeltaMovement(0, 0, 0);
            float health = actor.getHealth(); scene.hit(actor, player, false, 6);
            var commitment = MaeveDirector.positionDirective(actor);
            helper.assertTrue(actor.getHealth() < health && actor.getOffhandItem().is(Items.SHIELD)
                            && !actor.isUsingItem() && commitment != null,
                    "Rear damage bypasses the shield and staggers the same commitment");
            helper.assertTrue(actor.getDeltaMovement().horizontalDistanceSqr() > 0 && actor.getDeltaMovement().y > 0,
                    "Rear damage must still apply ordinary horizontal and vertical knockback");
            player.setPos(scene.position(8, 4));
            ticks(scene, actor, now + 14, 18);
            helper.assertTrue(!actor.isUsingItem(), "Damage must leave at least a full second without a guard");
            for (int i = 32; i < 110 && !actor.isBlocking(); i++) ticks(scene, actor, now + i, 1);
            helper.assertTrue(actor.isBlocking() && MaeveDirector.positionDirective(actor).encounter().equals(commitment.encounter())
                            && MaeveDirector.positionDirective(actor).arrivedAt() == commitment.arrivedAt(),
                    "After recovery, the same shield can guard again without replacing the bet or its arrival");
            scene.clock(commitment.arrivedAt() + 400); actor.tick();
            helper.assertTrue(actor.getOffhandItem().is(Items.SHIELD) && MaeveDirector.positionDirective(actor) != null,
                    "A staggered stance lasts for the same encounter beyond the former deadline");
            actor.discard(); player.setPos(scene.position(8, 4)); scene.clock(now + 1100);
            actor = actor(scene, player); long axeStart = awaitGuard(helper, scene, actor, now + 1100); ticks(scene, actor, axeStart + 1, 12);
            helper.assertTrue(actor.isBlocking(), "A fresh later encounter can choose the guard again");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            var motion = actor.getDeltaMovement();
            health = actor.getHealth(); scene.hit(actor, player, false, 6);
            helper.assertTrue(actor.getHealth() == health && !actor.isUsingItem() && actor.getOffhandItem().isEmpty(),
                    "The initial frontal axe strike blocks but disables and removes the generated shield");
            helper.assertTrue(actor.getDeltaMovement().distanceToSqr(motion) < 1.0E-12,
                    "The disabling axe contact was still blocked and must not shove the defender");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("SHIELD_DISABLED"),
                    "Axe counterplay is explicit in the commitment outcome");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(s -> s.contains("result=FAILURE") && s.contains("blocked=6.0")),
                    "A disabling blow cannot be scored as a guard success");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardBreakReloadErasureAndEquipmentCleanup(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 113, scene -> {
            var player = scene.player("sword_cleanup", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            actor.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            ticks(scene, actor, now, 25);
            helper.assertTrue(actor.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && MaeveDirector.positionDirective(actor) == null,
                    "Maeve cannot replace unrelated offhand equipment");
            actor.clearMaevePositioning();
            helper.assertTrue(actor.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "Cleanup preserves unrelated equipment");
            actor.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ticks(scene, actor, now + 25, 30);
            helper.assertTrue(actor.getOffhandItem().is(Items.SHIELD), "Still unused encounter can now admit the guard");
            var savedData = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            var savedActor = new CompoundTag(); actor.saveWithoutId(savedActor);
            actor.discard(); scene.storage(MaeveSavedData.load(savedData, scene.level.registryAccess()));
            actor = scene.architect(2, 4); actor.load(savedActor); actor.tickCount = 80; actor.debugForceApproach(player);
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem(), "Entity load releases generated equipment and use pose");
            ticks(scene, actor, now + 55, 25);
            helper.assertTrue(actor.getOffhandItem().isEmpty() && MaeveDirector.positionDirective(actor) == null,
                    "Reload cannot refund the saved encounter bet");
            actor.discard(); scene.clock(now + 800); actor = actor(scene, player); long breakStart = awaitGuard(helper, scene, actor, now + 800); ticks(scene, actor, breakStart + 1, 12);
            helper.assertTrue(actor.isBlocking(), "Later encounter equips after the reload");
            actor.getOffhandItem().setDamageValue(actor.getOffhandItem().getMaxDamage() - 1);
            var motion = actor.getDeltaMovement();
            scene.hit(actor, player, false, 6);
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem()
                            && MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("SHIELD_BROKEN"),
                    "Durability exhaustion ends the commitment and item use immediately");
            helper.assertTrue(actor.getDeltaMovement().distanceToSqr(motion) < 1.0E-12,
                    "A fully blocked hit must not gain knockback when it breaks the shield");
            actor.discard(); scene.clock(now + 1600); actor = actor(scene, player); long eraseStart = awaitGuard(helper, scene, actor, now + 1600); ticks(scene, actor, eraseStart + 1, 12);
            helper.assertTrue(actor.isBlocking(), "One failed counter alone does not permanently suppress the guard");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem() && scene.beliefs(player).isEmpty(),
                    "Erasure immediately clears tactical memory, generated equipment and pose");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Debug reversal cannot recover sword history");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardRejectsSameEncounterAndClearsOnTargetLoss(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 114, scene -> {
            var player = scene.player("sword_lag", 8, 4);
            long now = train(scene, player);
            scene.clock(now - 610 + 1);
            var actor = actor(scene, player);
            ticks(scene, actor, now - 610 + 1, 30);
            helper.assertTrue(sword(scene, player).confidence() == .8 && actor.getOffhandItem().isEmpty(),
                    "Current .8 confidence cannot replace the fourth encounter's frozen .6 history");
            actor.discard(); scene.clock(now + 100); actor = actor(scene, player);
            long guardStart = awaitGuard(helper, scene, actor, now + 100); ticks(scene, actor, guardStart + 1, 12);
            helper.assertTrue(actor.isBlocking(), "A later encounter admits the historical sword prediction");
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            scene.clock(guardStart + 13); actor.tick();
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem(),
                    "Losing the eligible local target clears the guard immediately");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardCanceledHitsDeathAndMastersStayIsolated(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 115, scene -> {
            var player = scene.player("sword_death", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == actor) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(cancel);
            try { scene.hit(actor, player, false, 6); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(sword(scene, player).evidence() == 4 && actor.getOffhandItem().getDamageValue() == 0,
                    "Canceled shield contact neither teaches Maeve nor consumes durability");
            scene.hit(actor, player, false, 6);
            actor.setDropChance(EquipmentSlot.OFFHAND, 1);
            actor.setHealth(1); player.setPos(actor.position().add(-2, 0, 0));
            scene.hit(actor, player, false, 4);
            helper.assertTrue(!actor.isAlive() && actor.getOffhandItem().isEmpty() && !actor.isUsingItem(),
                    "Fatal rear hit removes the generated equipment before guaranteed equipment drops");
            helper.assertTrue(scene.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    actor.getBoundingBox().inflate(4), item -> item.getItem().is(Items.SHIELD)).isEmpty(),
                    "The guard cannot farm a shield as loot");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream()
                            .anyMatch(line -> line.contains("result=FAILURE") && line.contains("blocked=6.0")),
                    "Death is a failed counter even if prior prevented damage outweighs the fatal hit");
            var master = scene.architect(3, 3);
            master.bindToHearthMasterArchitect(java.util.UUID.randomUUID(), scene.origin, 0);
            var other = scene.player("sword_master", 8, 4);
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            scene.hit(master, other, false, 1);
            helper.assertTrue(scene.beliefs(other).isEmpty() && MaeveDirector.commitmentHints(master, player).isEmpty(),
                    "Masters remain independent guardians without sword reports or Maeve guard directives");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardUsesLocalSightAndAxeEndsExposedGuard(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 119, scene -> {
            var player = scene.player("sword_distance", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            long start = -1;
            // Hold the fixture at six blocks until the coarse planner admits it;
            // otherwise normal pursuit can enter guard range before admission.
            for (int i = 0; i < 60; i++) {
                actor.setPos(scene.position(2, 4)); ticks(scene, actor, now + i, 1);
                var directive = MaeveDirector.positionDirective(actor);
                if (directive != null && directive.arrivedAt() >= 0) { start = directive.arrivedAt(); break; }
            }
            helper.assertTrue(start >= 0 && actor.getOffhandItem().is(Items.SHIELD) && !actor.isUsingItem(),
                    "Historical belief equips the shield before confirmation, but distant targets do not trigger a guard: distance="
                            + actor.distanceTo(player));
            actor.setPos(scene.position(4, 4));
            scene.wall(true); ticks(scene, actor, start + 10, 1);
            helper.assertTrue(!actor.isUsingItem(), "Nearby hidden players cannot trigger a guard through the wall");
            scene.wall(false); player.setPos(actor.position().add(3, 0, 0));
            ticks(scene, actor, start + 20, 12);
            helper.assertTrue(actor.isBlocking(), "Visible approach starts a guard after local polling and native startup");
            player.setPos(actor.position().add(8, 0, 0)); ticks(scene, actor, start + 32, 1);
            helper.assertTrue(!actor.isUsingItem() && actor.getOffhandItem().is(Items.SHIELD),
                    "Withdrawal lowers the shield while retaining the same equipped commitment");
            player.setPos(actor.position().add(2, 0, 0));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            float health = actor.getHealth(); scene.hit(actor, player, false, 6);
            helper.assertTrue(actor.getHealth() < health && actor.getOffhandItem().isEmpty()
                            && MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("SHIELD_DISABLED"),
                    "An axe hit during an exposed window ends the guard instead of entering ordinary stagger recovery");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardResumesAfterRealRetreatAndHealing(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 120, scene -> {
            var player = scene.player("sword_recovery", 8, 4);
            long now = train(scene, player); var actor = actor(scene, player);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            scene.hit(actor, player, false, 6);
            var shield = actor.getOffhandItem();
            var directive = MaeveDirector.positionDirective(actor);
            var attention = MaeveDirector.attentionSnapshot(scene.server).slots().stream()
                    .filter(s -> s.kind().equals("ACTIVE_COMMITMENT")).findFirst().orElseThrow();
            scene.clock(start + 450); actor.tick();
            helper.assertTrue(actor.getOffhandItem() == shield && MaeveDirector.positionDirective(actor) != null,
                    "The original shield survives the old 400-tick deadline");
            actor.setHealth(actor.getMaxHealth() * .25F);
            player.setPos(actor.position().add(-2, 0, 0));
            float beforeDamage = actor.getHealth();
            scene.hit(actor, player, false, 1);
            float received = beforeDamage - actor.getHealth();
            helper.assertTrue(actor.getOffhandItem() == shield && !actor.isUsingItem(),
                    "A hit into low health suspends protection without discarding the shield");
            boolean drank = false, damagedDuringDrink = false, resumed = false;
            long last = start + 450;
            for (int i = 1; i <= 300; i++) {
                last = start + 450 + i;
                // Keep the compact fixture on its floor. The real retreat timeout,
                // cover placement, potion duration and healing all execute normally.
                actor.setPos(scene.position(4, 4)); actor.setDeltaMovement(0, 0, 0);
                // Circle around its real retreat cover once it starts drinking.
                player.setPos(scene.position(drank ? 1 : 4, drank ? 4 : 7));
                scene.clock(last);
                if (i % 10 == 0) MaeveDirector.observePresence(actor, player);
                actor.tick();
                helper.assertTrue(actor.getOffhandItem() == shield,
                        "Recovery must retain the same shield instance and its durability");
                if (actor.getMainHandItem().is(Items.POTION)) {
                    drank = true;
                    helper.assertTrue(!actor.isUsingItem() && !actor.isBlocking(), "Healing cannot block attacks");
                    if (!damagedDuringDrink) {
                        player.setPos(scene.position(1, 4));
                        float health = actor.getHealth();
                        scene.hit(actor, player, false, 1);
                        received += health - actor.getHealth();
                        helper.assertTrue(actor.getHealth() < health && actor.getOffhandItem() == shield,
                                "A real hit during drinking remains effective without deleting the stance");
                        damagedDuringDrink = true;
                    }
                }
                if (actor.getBrainAction() == ArchitectEntity.ACTION_RETREAT)
                    helper.assertTrue(!actor.isUsingItem(), "Retreat must remain exposed");
                if (drank && actor.getHealth() >= actor.getMaxHealth() * .7F && actor.isBlocking()) {
                    resumed = true; break;
                }
            }
            helper.assertTrue(drank && damagedDuringDrink && resumed,
                    "Real retreat and potion healing must return to guarding: drank=" + drank
                            + " hitDuringDrink=" + damagedDuringDrink + " resumed=" + resumed
                            + " health=" + actor.getHealth() + " visible=" + actor.hasLineOfSight(player));
            var returned = MaeveDirector.positionDirective(actor);
            helper.assertTrue(returned != null && returned.encounter().equals(directive.encounter())
                            && returned.arrivedAt() == directive.arrivedAt() && shield.getDamageValue() == 7,
                    "Healing preserves the encounter, original arrival and spent durability");
            var retained = MaeveDirector.attentionSnapshot(scene.server).slots().stream()
                    .filter(s -> s.kind().equals("ACTIVE_COMMITMENT")).findFirst().orElseThrow();
            helper.assertTrue(retained.subject().equals(attention.subject()) && retained.admittedAt() == attention.admittedAt(),
                    "Recovery cannot obtain a fresh focus slot or minimum dwell");
            // End through the real contact timeout, not a new bet or an artificial success.
            MaeveDirector.observePresence(actor, player);
            player.setPos(scene.position(8, 4));
            scene.wall(true);
            scene.clock(last + 599);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "A contact gap shorter than 600 ticks retains the stance");
            scene.clock(last + 600);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "600 ticks without contact ends the encounter stance");
            actor.tick();
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem(), "Encounter expiry removes equipment and use pose");
            var result = MaeveSavedData.get(scene.server).store().commitment(player.getUUID()).performance().save()
                    .getList("contexts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                    .getList("results", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(result.getString("outcome").equals("UNKNOWN") && result.getFloat("blocked") == 6
                            && Math.abs(result.getFloat("received") - received) < .0001 && received > 1,
                    "The single interrupted result retains blocks and damage from before and during healing");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardWitnessedVictoryCompletesItsSingleResult(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 121, scene -> {
            var player = MaeveReconnaissanceGameTest.damageablePlayer(scene, "sword_victory");
            player.setPos(scene.position(8, 4));
            long now = train(scene, player); var actor = actor(scene, player);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            scene.hit(actor, player, false, 6);
            // Finish during a real exposed interval, after the guard has already blocked.
            ticks(scene, actor, start + 13, 25);
            helper.assertTrue(!actor.isUsingItem(), "A lethal local attack must use an exposed interval");
            player.removeAllEffects(); player.setHealth(1); player.invulnerableTime = 0;
            player.hurt(scene.level.damageSources().mobAttack(actor), 6);
            helper.assertTrue(!player.isAlive() && MaeveDirector.positionDirective(actor) == null,
                    "The actual final outgoing damage event completes the encounter stance: health=" + player.getHealth()
                            + " outcome=" + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome());
            actor.tick();
            helper.assertTrue(actor.getOffhandItem().isEmpty() && !actor.isUsingItem(), "Victory clears the generated shield and pose");
            var state = MaeveDirector.commitmentSnapshot(scene.server, player.getUUID());
            helper.assertTrue(state.issued() && state.outcome().equals("SUBJECT_DEFEATED"), "Victory cannot refund the bet");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream()
                            .anyMatch(line -> line.contains("result=SUCCESS") && line.contains("blocked=6.0")),
                    "The counter can earn a positive witnessed trade without a 400-tick timeout");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardOtherPlayerCannotOwnTheCounterResult(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 117, scene -> {
            var subject = scene.player("sword_subject", 8, 4);
            long now = train(scene, subject); var actor = actor(scene, subject);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            scene.hit(actor, subject, false, 6);
            var other = scene.player("sword_interference", 8, 4);
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            scene.hit(actor, other, false, 6);
            helper.assertTrue(sword(scene, subject).evidence() == 5 && sword(scene, other).evidence() == 1,
                    "A second player's real blocked contact belongs only to their own sword profile");
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            scene.hit(actor, other, false, 6);
            helper.assertTrue(actor.getOffhandItem().isEmpty(), "Another player's axe can physically disable the shield");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, subject.getUUID()).stream()
                            .anyMatch(line -> line.contains("result=UNKNOWN") && line.contains("blocked=6.0")),
                    "Interference cannot become success or failure against the intended subject");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardExposedOtherPlayerAxeIsUnknown(GameTestHelper helper) {
        unrelatedGuardDamage(helper, 122, 0, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardExposedMobAxeIsUnknown(GameTestHelper helper) {
        unrelatedGuardDamage(helper, 123, 1, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardOtherPlayerDeathIsUnknown(GameTestHelper helper) {
        unrelatedGuardDamage(helper, 124, 0, true);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardMobDeathIsUnknown(GameTestHelper helper) {
        unrelatedGuardDamage(helper, 125, 1, true);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveSwordGuardEnvironmentalDeathIsUnknown(GameTestHelper helper) {
        unrelatedGuardDamage(helper, 126, 2, true);
    }

    private static void unrelatedGuardDamage(GameTestHelper helper, int lane, int kind, boolean lethal) {
        MaeveObservationGameTest.withScene(helper, lane, scene -> {
            var subject = scene.player("guard_subject_" + lane, 8, 4);
            long now = train(scene, subject); var actor = actor(scene, subject);
            long start = awaitGuard(helper, scene, actor, now); ticks(scene, actor, start + 1, 12);
            scene.hit(actor, subject, false, 6);
            ticks(scene, actor, start + 13, 25);
            helper.assertTrue(!actor.isUsingItem() && !actor.getOffhandItem().isEmpty(), "Interference must land during a real exposed guard window");
            net.minecraft.world.damagesource.DamageSource source;
            if (kind == 0) {
                var other = scene.player("guard_intruder_" + lane, 8, 4);
                other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                source = scene.level.damageSources().playerAttack(other);
            } else if (kind == 1) {
                var mob = net.minecraft.world.entity.EntityType.VINDICATOR.create(scene.level);
                mob.setPos(scene.position(8, 4)); mob.setNoAi(true);
                mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                scene.level.addFreshEntity(mob); scene.entities.add(mob);
                source = scene.level.damageSources().mobAttack(mob);
            } else source = scene.level.damageSources().inWall();
            if (lethal) actor.setHealth(1);
            actor.invulnerableTime = 0;
            helper.assertTrue(actor.hurt(source, 6), "Native damage must reach the exposed actor");
            helper.assertTrue(actor.isAlive() != lethal && actor.getOffhandItem().isEmpty() && !actor.isUsingItem(),
                    "The physical disable or death still clears the shield");
            var state = MaeveSavedData.get(scene.server).store().commitment(subject.getUUID());
            var context = state.performance().save().getList("contexts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            var results = context.getList("results", net.minecraft.nbt.Tag.TAG_COMPOUND);
            helper.assertTrue(results.size() == 1 && results.getCompound(0).getString("outcome").equals("UNKNOWN")
                            && results.getCompound(0).getFloat("blocked") == 6 && context.getInt("failures") == 0,
                    "Unrelated damage cannot penalize the subject: " + context);
            helper.assertTrue(state.issued() && state.active(scene.server.overworld().getGameTime()) == null,
                    "An unknown result cannot refund the spent encounter");
        });
    }

}
