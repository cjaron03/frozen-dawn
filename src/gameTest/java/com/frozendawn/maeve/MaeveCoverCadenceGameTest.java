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
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCoverCadenceGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void architectPeekFinishesAndPhysicallyLeavesTheCorner(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 95, scene -> {
            var actor = scene.architect(2, 4);
            var player = scene.player("peek_exit", 10, 4);
            scene.block(1, 0, 4, Blocks.PACKED_ICE.defaultBlockState());
            scene.block(2, 0, 3, Blocks.PACKED_ICE.defaultBlockState());
            actor.setHealth(actor.getMaxHealth() * .4F);
            var tag = actor.saveWithoutId(new CompoundTag());
            tag.putInt("HealCooldown", 1000);
            actor.load(tag);
            actor.tickCount = 80;
            actor.setOnGround(true);
            actor.debugForceApproach(player);
            actor.startDecisionRecording(UUID.randomUUID(), 1337L, Rotation.NONE);
            Vec3 corner = actor.position();
            int streak = 0, longest = 0;
            boolean sawPeek = false;
            double displacement = 0;
            for (int i = 0; i < 260; i++) {
                scene.clock(scene.gameTime + i + 1);
                scene.level.tickNonPassenger(actor);
                boolean peek = actor.getBrainAction() == ArchitectEntity.ACTION_PEEK;
                sawPeek |= peek;
                streak = peek ? streak + 1 : 0;
                longest = Math.max(longest, streak);
                if (sawPeek) displacement = Math.max(displacement, actor.position().subtract(corner).horizontalDistanceSqr());
            }
            helper.assertTrue(sawPeek, "The low-health corner fixture must select the real PEEK action");
            helper.assertTrue(longest <= 31, "PEEK must release after its look, instead of repeatedly winning: longest=" + longest);
            helper.assertTrue(displacement >= 4, "After peeking the actual actor must move at least two blocks");
            System.out.println("MACS_PEEK_CHECK longest=" + longest + " maxDisplacement=" + Math.sqrt(displacement));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveHistoricalRangedCoverRepeatsWithMovementAndSpacing(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 96, 2, scene -> {
            for (int x = -20; x <= 30; x++) for (int z = -20; z <= 30; z++) {
                scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
                for (int y = 0; y < 4; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
            }
            var witness = scene.architect(2, 10);
            var player = scene.player("cover_history", 14, 10);
            long start = scene.gameTime + 1;
            for (int i = 0; i < 4; i++) {
                scene.clock(start + i * 610L);
                scene.hit(witness, player, true, 1);
            }
            helper.assertTrue(MaeveDirector.utilityBias(witness, player).fortify() < .4F,
                    "The fourth hit cannot strengthen the encounter that supplied it");
            witness.discard();
            var actor = scene.architect(2, 10);
            long now = start + 4 * 610L;
            scene.clock(now);
            MaeveDirector.commitmentHints(actor, player);
            helper.assertTrue(Math.abs(MaeveDirector.utilityBias(actor, player).fortify() - .56F) < 1e-5,
                    "Four past witnessed encounters strengthen cover in the next encounter");
            var other = scene.player("cover_unseen", 14, 12);
            helper.assertTrue(MaeveDirector.utilityBias(actor, other).fortify() == 0, "Another player does not inherit the bow belief");
            other.discard();
            // Isolate ordinary construction after this encounter's sole positional bet.
            helper.assertTrue(MaeveDirector.chooseCommitment(actor, player, List.of(new MaeveDirector.PositionCandidate(
                    BeliefStore.RANGED, actor.blockPosition(), actor.blockPosition().east(2), 2))), "Fixture consumes the one position directive");
            MaeveDirector.releaseCommitment(actor, "LOCAL_SAFETY_RELEASE");
            actor.tickCount = 80;
            actor.setOnGround(true);
            actor.debugForceApproach(player);
            actor.startDecisionRecording(UUID.randomUUID(), 1337L, Rotation.NONE);
            actor.decisionJournal().useExtendedLabBuffer();
            boolean intercepted = false;
            for (int i = 0; i < 600; i++) {
                scene.clock(now + i);
                // A visible moving opponent exposes different sides; it never fires or equips a bow.
                double angle = i * Math.PI / 120;
                player.setPos(actor.position().add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12));
                // ServerLevel advances tickCount and posts NeoForge events as a live world does.
                scene.level.tickNonPassenger(actor);
                if (!intercepted && actor.decisionJournal().entries().stream().anyMatch(e ->
                        e.tick() == scene.server.overworld().getGameTime() && e.event().equals("FORTIFY_FINISHED") && e.detail().startsWith("blocks=2 "))) {
                    // Fire a real arrow after the authored wall exists. Tick the projectile
                    // through ordinary collision; neither participant moves during this probe.
                    float hp = actor.getHealth();
                    var arrow = new net.minecraft.world.entity.projectile.Arrow(scene.level, player,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
                    arrow.setPos(player.getEyePosition());
                    Vec3 shot = actor.position().add(0, 1.25, 0).subtract(arrow.position()).normalize().scale(2);
                    arrow.setDeltaMovement(shot); scene.level.addFreshEntity(arrow); scene.entities.add(arrow);
                    for (int flight = 0; flight < 12; flight++) scene.level.tickNonPassenger(arrow);
                    helper.assertTrue(actor.getHealth() == hp && arrow.getDeltaMovement().lengthSqr() < .01,
                            "The actual prebuilt wall must stop an actual incoming arrow");
                    arrow.discard(); intercepted = true;
                }
            }
            helper.assertTrue(intercepted, "This fixture must exercise real arrow interception");
            var attempts = actor.decisionJournal().entries().stream().filter(e -> e.event().equals("FORTIFY_FINISHED")).toList();
            long walls = attempts.stream().filter(e -> e.detail().startsWith("blocks=2 ")).count();
            helper.assertTrue(walls >= 2, "Strong historical confidence must produce multiple real two-block walls: " + attempts
                    + " final=" + actor.position() + " action=" + actor.getBrainAction());
            for (int i = 1; i < attempts.size(); i++) {
                helper.assertTrue(attempts.get(i).tick() - attempts.get(i - 1).tick() >= 120,
                        "Construction attempts stay at least six seconds apart");
                helper.assertTrue(attempts.get(i).pos().distSqr(attempts.get(i - 1).pos()) >= 1,
                        "The Architect must move between construction attempts");
            }
            var saved = actor.saveWithoutId(new CompoundTag());
            var ice = saved.getList("TacticalIce", Tag.TAG_LONG);
            helper.assertTrue(ice.size() <= 12, "Repeated cover stays within the twelve-block ordinary tactical pool");
            System.out.println("MACS_COVER_CHECK walls=" + walls + " attempts=" + attempts.size() + " retainedIce=" + ice.size());
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Repeated local walls cannot issue another positional commitment");
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(MaeveDirector.utilityBias(actor, player).fortify() == 0, "Erasure immediately removes the learned cover bias");
        });
    }
}
