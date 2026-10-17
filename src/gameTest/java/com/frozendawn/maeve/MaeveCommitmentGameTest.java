package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real event hooks and entity physics, with the game clock restored in one server callback. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCommitmentGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveRangedPillarImmediatelyPursuesAndAttacksWithinSameBet(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 158, scene -> {
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 10; z++)
                scene.block(x, -1, z, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var actor = scene.architect(4, 4);
            var player = MaeveReconnaissanceGameTest.damageablePlayer(scene, "pillar_combat");
            player.setPos(scene.position(14, 4));
            long start = scene.gameTime + 1;
            for (int i = 0; i < 4; i++) {
                scene.clock(start + i * 610L); scene.hit(actor, player, true, 1);
            }
            long now = start + 4 * 610L;
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            actor.startDecisionRecording(1337L); actor.decisionJournal().useExtendedLabBuffer();
            // A spawned actor first settles under real entity physics before placement.
            for (int i = 0; i < 40; i++) { scene.clock(now + i); scene.level.tickNonPassenger(actor); }
            var initial = MaeveDirector.positionDirective(actor);
            helper.assertTrue(initial != null && !initial.advancingCover() && initial.arrivedAt() >= 0,
                    "Real historical bow damage selects and builds the initial pillar: "
                            + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(scene.level.getBlockState(initial.cover()).is(net.minecraft.world.level.block.Blocks.PACKED_ICE),
                    "The physical pillar makes this commitment visible");
            double moved = 0;
            for (int i = 40; i < 160; i++) {
                if (i == 100) {
                    helper.assertTrue(moved > 4, "Ordinary pursuit must physically leave the built pillar before the player closes");
                    helper.assertTrue(actor.blockPosition().distSqr(initial.position()) > 9,
                            "The combat trade must occur beyond the former three-block scoring radius");
                    // The player flanks into melee without hitting the Architect.
                    // It must attack now rather than waiting for damage or a hold timeout.
                    player.setPos(actor.position().add(-1.5, 0, 0));
                }
                scene.clock(now + i); scene.level.tickNonPassenger(actor);
                helper.assertTrue(!actor.isHoldingMaevePosition(), "Pillar combat never pins the executor to its old position");
                moved = Math.max(moved, actor.position().distanceToSqr(
                        net.minecraft.world.phys.Vec3.atBottomCenterOf(initial.position())));
            }
            var current = MaeveDirector.positionDirective(actor);
            helper.assertTrue(moved > 4 && player.getHealth() < player.getMaxHealth(),
                    "Before the old 400-tick wait, the real actor must navigate its pillar and land a melee hit: moved="
                            + Math.sqrt(moved) + " hp=" + player.getHealth() + " journal=" + actor.decisionJournal().entries());
            helper.assertTrue(current != null && current.encounter().equals(initial.encounter()) && current.cover().equals(initial.cover()),
                    "Movement and attacks retain the original bet and outcome window");
            helper.assertTrue(!MaeveDirector.chooseCommitment(actor, player, List.of(new MaeveDirector.PositionCandidate(
                            BeliefStore.SWORD, actor.blockPosition(), null, 1.5))) && actor.getOffhandItem().isEmpty(),
                    "Closing to melee cannot buy an immediate replacement shield commitment");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("COVER_COMBAT"),
                    "Diagnostics describe active cover combat rather than a held position");
            helper.assertTrue(actor.blockPosition().distSqr(initial.position()) > 9,
                    "The incoming hit must also occur away from the original pillar position");
            float dealt = player.getMaxHealth() - player.getHealth();
            player.setPos(actor.position().add(-1.5, 0, 0));
            float healthBefore = actor.getHealth();
            scene.clock(now + 160);
            helper.assertTrue(scene.hit(actor, player, false, 3), "The player lands a real visible counterattack after pursuit");
            float received = healthBefore - actor.getHealth();
            helper.assertTrue(received > 0 && MaeveDirector.positionDirective(actor) == null,
                    "Final incoming damage ends the original bet after recording the trade");
            var policy = MaeveSavedData.get(scene.server).store().commitment(player.getUUID());
            var context = policy.performance().save().getList("contexts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            var result = context.getList("results", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(Math.abs(result.getFloat("dealt") - dealt) < .0001f
                            && Math.abs(result.getFloat("received") - received) < .0001f,
                    "Pillar results must retain the whole witnessed trade after pursuit: expected dealt=" + dealt
                            + " received=" + received + " actual=" + result);
            helper.assertTrue(result.getString("outcome").equals(dealt > received ? "SUCCESS" : "FAILURE"),
                    "The actual mobile combat trade determines the pillar outcome rather than UNKNOWN");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveRecoveryCommitmentLeavesAnAlreadyReachedApproachPoint(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 8, scene -> {
            var observer = scene.architect(2, 4);
            var player = scene.player("maeve_visible_bet", 8, 4);
            scene.roof(true);
            long start = scene.gameTime + 1;
            for (int i = 0; i < 4; i++) {
                scene.clock(start + i * 610L);
                player.finish(scene.potion());
            }
            long now = start + 4 * 610L;
            scene.clock(now);
            // Live replay: the ordinary approach had already put the actor within
            // 0.26 blocks of the old recovery point before the coarse planner ran.
            observer.setPos(scene.position(5, 4).add(-0.25, 0, 0));
            observer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            observer.setOnGround(true);
            observer.tickCount = 80;
            observer.debugForceApproach(player);
            var initial = observer.position();
            for (int i = 0; i < 70; i++) { scene.clock(now + i); observer.tick(); }
            var directive = MaeveDirector.positionDirective(observer);
            helper.assertTrue(directive != null && directive.arrivedAt() > directive.startedAt(),
                    "An already reached approach point must not count as visible positioning");
            helper.assertTrue(directive.recoveryCost() >= 2 && directive.recoveryCost() <= 6,
                    "The issued recovery bet requires a bounded physical move");
            helper.assertTrue(Math.abs(observer.getZ() - initial.z) > 1.3,
                    "The actual actor must leave the ordinary approach line before holding");
            player.setPos(scene.position(8, 7));
            player.finish(scene.potion());
            scene.clock(now + 71);
            observer.tick();
            helper.assertTrue(MaeveDirector.positionDirective(observer).contradictedAt() == now + 69,
                    "The subsequent real open-sky recovery breaks the held prediction");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveCommitmentMovesHoldsWrongAndCoolsDown(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 5, scene -> {
            var observer = scene.architect(2, 4);
            var player = scene.player("maeve_commit", 8, 4);
            scene.roof(true);
            long start = scene.gameTime + 1;
            for (int i = 0; i < 5; i++) {
                scene.clock(start + i * 610L);
                player.finish(scene.potion());
            }
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 5, "Training uses five real completed-item encounters");
            long now = start + 5 * 610L;
            scene.clock(now);
            observer.tickCount = 80;
            observer.setOnGround(true);
            observer.debugForceApproach(player);
            var initial = observer.position();
            // Actual entity ticks include travel and collision resolution, not a mocked arrival.
            for (int i = 0; i < 70; i++) { scene.clock(now + i); observer.tick(); }
            var held = MaeveDirector.positionDirective(observer);
            helper.assertTrue(held != null && held.arrivedAt() >= 0, "Architect must physically reach the committed point");
            helper.assertTrue(observer.position().distanceTo(initial) > 2,
                    "Commitment must produce visible positioning before any recovery in the new encounter");
            helper.assertTrue(observer.blockPosition().distSqr(held.position()) <= 1, "Actor reached its issued position");
            assertGuardFacesEvidence(helper, observer, held.evidence().position());
            var anchor = held.position();
            UUID encounter = held.encounter();
            player.setPos(scene.position(8, 7));
            helper.assertTrue(scene.level.canSeeSky(player.blockPosition()), "Contradiction fixture must expose the sky");
            scene.clock(now + 70);
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).getFirst().contradictions() == 1, "Ordinary open-sky consumption breaks the prediction");
            for (int i = 71; i < 150; i++) { scene.clock(now + i); observer.tick(); }
            var wrong = MaeveDirector.positionDirective(observer);
            helper.assertTrue(wrong != null && wrong.encounter().equals(encounter)
                            && wrong.contradictedAt() >= 0 && observer.blockPosition().distSqr(anchor) <= 1,
                    "Wrong commitment remains physically held rather than chasing the player's new position");
            assertGuardFacesEvidence(helper, observer, held.evidence().position());
            helper.assertTrue(MaeveDirector.explain(scene.server, player.getUUID(), BeliefStore.RECOVERY).stream()
                    .anyMatch(line -> line.contains("CONTRADICTED_HOLD")), "Diagnostics explain the held wrong prediction");
            // A supporting observation leaves current confidence above the threshold;
            // this makes the next encounter's refusal prove cooldown, not low confidence.
            player.setPos(scene.position(8, 4));
            player.finish(scene.potion());
            helper.assertTrue(scene.beliefs(player).getFirst().confidence() >= 0.75, "Cooldown test must retain high confidence");
            long end = held.arrivedAt() + CommitmentPolicy.HOLD_TICKS;
            for (long tick = now + 150; tick < end; tick++) { scene.clock(tick); observer.tick(); }
            helper.assertTrue(observer.blockPosition().distSqr(anchor) <= 1, "The full hold remains visible for roughly twenty seconds");
            scene.clock(end);
            helper.assertTrue(MaeveDirector.positionDirective(observer) == null, "Commitment ends at its bounded deadline");
            observer.tick();
            helper.assertTrue(!observer.isHoldingMaevePosition(), "The guard cue clears when normal pursuit resumes");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            scene.clock(end + 610);
            var hints = MaeveDirector.commitmentHints(observer, player);
            helper.assertTrue(!hints.isEmpty(), "Prior high-confidence recovery remains available for inspection");
            var option = new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY, anchor, null, 1);
            helper.assertTrue(!MaeveDirector.chooseCommitment(observer, player, List.of(option)), "Next encounter must honor the persisted contradiction cooldown");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).blocked().contains(BeliefStore.RECOVERY),
                    "Cooldown is explained explicitly");
            scene.clock(end + 1220);
            helper.assertTrue(MaeveDirector.chooseCommitment(observer, player, List.of(option)), "One later encounter can commit again");
            observer.setPos(initial);
            observer.setOnGround(true);
            observer.tick();
            helper.assertTrue(observer.getDeltaMovement().horizontalDistanceSqr() > 0, "The renewed directive drives real local movement");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(observer.getDeltaMovement().horizontalDistanceSqr() == 0, "Erasure stops active local positioning immediately");
            helper.assertTrue(MaeveDirector.positionDirective(observer) == null && MaeveSavedData.get(scene.server).store() == null,
                    "Erasure releases both policy and local directive state");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(MaeveDirector.commitmentHints(observer, player).isEmpty(), "Debug reversal cannot recover learned positioning");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveRangedCommitmentDamageInterruptsWithoutRefund(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 7, scene -> {
            var observer = scene.architect(4, 4);
            var player = scene.player("maeve_knockback", 8, 4);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 1200, 4));
            long start = scene.gameTime + 1;
            for (int i = 0; i < 4; i++) {
                scene.clock(start + i * 610L);
                scene.hit(observer, player, true, 1);
            }
            long now = start + 4 * 610L;
            scene.clock(now);
            observer.tickCount = 80;
            observer.setOnGround(true);
            observer.debugForceApproach(player);
            observer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            observer.startDecisionRecording(1337L);
            observer.decisionJournal().useExtendedLabBuffer();
            for (int i = 0; i < 40; i++) { scene.clock(now + i); observer.tick(); }
            var held = MaeveDirector.positionDirective(observer);
            helper.assertTrue(held != null && held.cover() != null && held.arrivedAt() >= 0,
                    "Ranged history must produce real cover before another shot: "
                            + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(scene.level.getBlockState(held.cover()).is(net.minecraft.world.level.block.Blocks.PACKED_ICE),
                    "The visible cover is built through the existing tactical ice path");
            player.setPos(observer.position().add(0, 0, 1.5));
            scene.hit(observer, player, false, 0);
            helper.assertTrue(MaeveDirector.positionDirective(observer) != null && !observer.isHoldingMaevePosition(),
                    "An ineffective hit cannot release the commitment");
            scene.clock(now + 40);
            helper.assertTrue(scene.hit(observer, player, false, 1), "The player actually lands a visible melee contradiction");
            helper.assertTrue(MaeveDirector.positionDirective(observer) == null && !observer.isHoldingMaevePosition(),
                    "Effective damage immediately releases the held position and pose");
            var ended = MaeveDirector.commitmentSnapshot(scene.server, player.getUUID());
            helper.assertTrue(ended.issued() && ended.outcome().equals("LOCAL_DEFENSE")
                            && ended.blockNext().contains(BeliefStore.RANGED),
                    "Damage preserves the spent bet and witnessed melee contradiction");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream()
                            .anyMatch(line -> line.contains("result=FAILURE") && line.contains("HOLD_RECEIVED_FINAL_DAMAGE")),
                    "The final hit is charged before the hold is released");
            player.setPos(scene.position(8, 7));
            double displacement = 0;
            for (int i = 41; i < 110; i++) {
                scene.clock(now + i); scene.level.tickNonPassenger(observer);
                displacement = Math.max(displacement, observer.position().distanceToSqr(
                        net.minecraft.world.phys.Vec3.atBottomCenterOf(held.position())));
                helper.assertTrue(MaeveDirector.positionDirective(observer) == null && !observer.hasReconnaissanceEyes(),
                        "Self-defense cannot restart a hold or convert into a scout");
                if (i == 41) helper.assertTrue(observer.getBrainAction() == ArchitectEntity.ACTION_APPROACH
                                || observer.getBrainAction() == ArchitectEntity.ACTION_ATTACK_MELEE,
                        "The first tick after a hit must enter local pursuit or melee");
            }
            helper.assertTrue(displacement > 1,
                    "The local executor physically leaves the interrupted hold: " + observer.decisionJournal().entries());
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveCommitmentRequiresSightAndIsolatesEncounterOwners(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 6, scene -> {
            var first = scene.architect(2, 4);
            var second = scene.architect(2, 7);
            var player = scene.player("maeve_owner", 8, 4);
            var other = scene.player("maeve_other", 8, 7);
            long start = scene.gameTime + 1;
            for (int i = 0; i < 4; i++) {
                scene.clock(start + i * 610L);
                scene.hit(first, player, true, 1);
                scene.hit(second, other, true, 1);
            }
            scene.clock(start + 4 * 610L);
            scene.wall(true);
            helper.assertTrue(MaeveDirector.commitmentHints(first, player).isEmpty(), "An omniscient target pointer cannot initiate commitment through a wall");
            scene.wall(false);
            helper.assertTrue(MaeveDirector.commitmentHints(first, player).size() == 1, "Actual sight supplies a valid encounter boundary");
            var a = new MaeveDirector.PositionCandidate(BeliefStore.RANGED, first.blockPosition(), first.blockPosition().east(2), 0);
            var b = new MaeveDirector.PositionCandidate(BeliefStore.RANGED, second.blockPosition(), second.blockPosition().east(2), 0);
            helper.assertTrue(MaeveDirector.chooseCommitment(first, player, List.of(a)), "First observer claims the encounter's only bet");
            helper.assertTrue(!MaeveDirector.chooseCommitment(second, player, List.of(b)), "Another observer cannot stack a commitment in that encounter");
            helper.assertTrue(!MaeveDirector.chooseCommitment(first, other, List.of(a)), "One Architect cannot execute two players' bets simultaneously");
            helper.assertTrue(MaeveDirector.chooseCommitment(second, other, List.of(b)), "A second player's encounter remains independent");
            var master = scene.architect(3, 3);
            master.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
            helper.assertTrue(MaeveDirector.commitmentHints(master, player).isEmpty(), "The Master retains its dedicated local combat controller");
            first.discard();
            helper.assertTrue(MaeveDirector.positionDirective(first) == null, "Removing an owner releases execution");
            helper.assertTrue(!MaeveDirector.chooseCommitment(master, player, List.of(a)), "An owner death does not buy a new bet or override boss roles");
            second.tickCount = 80;
            second.setOnGround(true);
            second.debugForceApproach(other);
            second.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            long holdingAt = scene.server.overworld().getGameTime();
            for (int i = 0; i < 40; i++) { scene.clock(holdingAt + i); second.tick(); }
            helper.assertTrue(MaeveDirector.positionDirective(second) != null && !second.isHoldingMaevePosition(),
                    "The other owner continues cover combat within its independent bet");
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(MaeveDirector.positionDirective(second) == null, "Authoritative erasure terminates the other player's active bet too");
            helper.assertTrue(!second.isHoldingMaevePosition(), "Erasure clears the guard cue before another entity tick");
            helper.assertTrue(!MaeveSavedData.get(scene.server).save(new CompoundTag(), null).contains("beliefs"), "No frozen basis or cooldown survives erasure");
        });
    }

    private static void assertGuardFacesEvidence(GameTestHelper helper,
            com.frozendawn.entity.ArchitectEntity observer, net.minecraft.core.BlockPos evidence) {
        helper.assertTrue(observer.isHoldingMaevePosition(), "Physical arrival must publish the guard cue to clients");
        var direction = evidence.getCenter().subtract(observer.position()).multiply(1, 0, 1).normalize();
        var body = net.minecraft.world.phys.Vec3.directionFromRotation(0, observer.yBodyRot);
        var head = net.minecraft.world.phys.Vec3.directionFromRotation(0, observer.getYHeadRot());
        helper.assertTrue(body.dot(direction) > 0.99 && head.dot(direction) > 0.99,
                "The whole body and head must watch the inherited event, even after the player moves elsewhere");
    }
}
