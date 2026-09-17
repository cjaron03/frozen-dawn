package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
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
    public static void maeveRangedCommitmentSurvivesOrdinaryKnockback(GameTestHelper helper) {
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
            for (int i = 0; i < 40; i++) { scene.clock(now + i); observer.tick(); }
            var held = MaeveDirector.positionDirective(observer);
            helper.assertTrue(held != null && held.cover() != null && held.arrivedAt() >= 0,
                    "Ranged history must produce a real held cover position before another shot: "
                            + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(scene.level.getBlockState(held.cover()).is(net.minecraft.world.level.block.Blocks.PACKED_ICE),
                    "The visible cover is built through the existing tactical ice path");
            player.setPos(scene.position(3, 4));
            scene.clock(now + 40);
            helper.assertTrue(scene.hit(observer, player, false, 1), "The player actually lands a visible melee contradiction");
            observer.knockback(0.4D, 1, 0);
            player.setPos(scene.position(8, 7));
            boolean airborne = false;
            for (int i = 41; i < 90; i++) {
                scene.clock(now + i);
                observer.tick();
                airborne |= !observer.onGround();
                helper.assertTrue(MaeveDirector.positionDirective(observer) != null,
                        "Normal knockback cannot erase the observable wrong commitment");
            }
            helper.assertTrue(airborne, "The regression must exercise actual airborne knockback");
            helper.assertTrue(observer.blockPosition().distSqr(held.position()) <= 1,
                    "After landing the Architect returns to its held position, not the player's new location");
            helper.assertTrue(MaeveDirector.positionDirective(observer).contradictedAt() == now + 40,
                    "The real melee event remains the reason this commitment is wrong");
            observer.setHealth(observer.getMaxHealth() * 0.2F);
            observer.tick();
            helper.assertTrue(MaeveDirector.positionDirective(observer) == null, "Critical local danger still releases the bet");
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
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(MaeveDirector.positionDirective(second) == null, "Authoritative erasure terminates the other player's active bet too");
            helper.assertTrue(!MaeveSavedData.get(scene.server).save(new CompoundTag(), null).contains("beliefs"), "No frozen basis or cooldown survives erasure");
        });
    }
}
