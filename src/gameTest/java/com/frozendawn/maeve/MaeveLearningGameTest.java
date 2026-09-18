package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveLearningGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveWithdrawalBoundsObserversAndKeepsPlayersSeparate(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 38, scene -> {
            floor(scene); var player = scene.player("learning_many", 6, 4); var other = scene.player("learning_isolated", 6, 7);
            var master = scene.architect(12, 4); master.bindToHearthMasterArchitect(java.util.UUID.randomUUID(), scene.origin, 0);
            var disabled = scene.architect(12, 4); disabled.setNoAi(true);
            long now = (scene.gameTime / 20 + 1) * 20; scene.clock(now);
            MaeveDirector.observeWithdrawal(master, player); MaeveDirector.observeWithdrawal(disabled, player);
            helper.assertTrue(MaeveDirector.knownPlayers(scene.server).isEmpty(), "Master guardians and disabled observers cannot start learning episodes");
            var actors = new java.util.ArrayList<ArchitectEntity>();
            for (int i = 0; i < 40; i++) { var actor = scene.architect(12, 4); actors.add(actor); MaeveDirector.observeWithdrawal(actor, player); }
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().filter(s -> s.contains("sampling age=")).count() == 32,
                    "At most 32 episodes can be active, regardless of local crowd size");
            for (int t = 10; t <= 100; t += 10) {
                for (var actor : actors) actor.setPos(scene.position(12, 4).add(t * .03, 0, 0));
                player.setPos(scene.position(6, 4).add(t * .03, 0, 0)); scene.clock(now + t); MaeveDirector.tick(scene.server);
            }
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(BeliefStore.PURSUIT) && b.evidence() == 1),
                    "Duplicate observers share one conditional contribution");
            helper.assertTrue(scene.beliefs(other).isEmpty(), "Another nearby player gains no inherited response");
            var actor = actors.getFirst(); actor.setPos(scene.position(12, 7)); scene.clock(now + 210);
            MaeveDirector.observeWithdrawal(actor, other);
            for (int t = 10; t <= 100; t += 10) {
                actor.setPos(scene.position(12, 7).add(t * .03, 0, 0)); other.setPos(scene.position(6, 7).add(t * .03, 0, 0));
                scene.clock(now + 210 + t); MaeveDirector.tick(scene.server);
            }
            helper.assertTrue(scene.beliefs(other).stream().anyMatch(b -> b.pattern().equals(BeliefStore.PURSUIT) && b.evidence() == 1),
                    "The same observer can learn a distinct response for a different player");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveScoutWithdrawalTeachesConditionalThroughNormalMission(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 37, 2, scene -> {
            for (int x = -10; x <= 40; x++) for (int z = -15; z <= 25; z++) scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            MaeveWorldModelGameTest.shelter(scene);
            var player = scene.player("learning_scout", 4, 5); var witness = scene.architect(10, 5);
            long now = (scene.gameTime / 20 + 1) * 20;
            MaeveWorldModelGameTest.sample(scene, witness, player, now, 4, 5);
            MaeveWorldModelGameTest.sample(scene, witness, player, now + 10, 6, 5);
            witness.discard(); player.setPos(scene.position(12, 12));
            scene.clock(now + 650); MaeveDirector.tick(scene.server);
            var scout = scene.architect(18, 5); scout.tickCount = 80; scout.setOnGround(true);
            for (int i = 0; i < 450; i++) {
                var previous = scout.position(); boolean leaving = scout.isMaeveDisengaging();
                tick(scene, scout, now + 650 + i);
                if (leaving) player.setPos(player.position().add(scout.position().subtract(previous)));
            }
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, player.getUUID()).stream().anyMatch(m -> m.report().equals("OPEN")),
                    "An actual mission must inspect the inherited crossing before withdrawing");
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(BeliefStore.PURSUIT) && b.evidence() == 1),
                    "Scout movement must teach a witnessed response: " + MaeveDirector.diagnostics(scene.server, player.getUUID()));
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveLearningReplayFunctionsParseAtClientPermission(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var resources = server.getResourceManager().listResources("function", id -> id.getNamespace().equals("macs_learning") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(resources.size() == 29, "The full integrated replay pack must be included");
        resources.forEach((file, resource) -> {
            String path = file.getPath().substring("function/".length()).replace(".mcfunction", "");
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("macs_learning", path);
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function exists: " + id);
            try (var reader = resource.openAsReader()) {
                net.minecraft.commands.functions.CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), reader.lines().toList());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }
    private static void floor(MaeveObservationGameTest.Scene scene) {
        for (int x = 0; x <= 40; x++) for (int z = 0; z <= 12; z++) scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
    }
    private static void tick(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long now) {
        scene.clock(now); actor.tickCount++; actor.tick(); MaeveDirector.tick(scene.server);
    }
    private static ArchitectEntity withdrawal(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player, long now, boolean follow) {
        player.setPos(scene.position(6, 4)); var actor = scene.architect(12, 4);
        actor.tickCount = 80; actor.setOnGround(true); scene.clock(now);
        actor.beginMaeveDisengagement(player.getUUID(), player.blockPosition(), "RECON_SURVEY_COMPLETE");
        for (int i = 1; i <= 110; i++) {
            if (follow) player.setPos(actor.position().add(-6, 0, 0));
            tick(scene, actor, now + i);
        }
        return actor;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveConditionalUsesRealWithdrawalAndLaggedPositioning(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 33, scene -> {
            floor(scene); var player = scene.player("learning_follower", 6, 4);
            long now = (scene.gameTime / 20 + 1) * 20;
            for (int i = 0; i < 4; i++) {
                var actor = withdrawal(scene, player, now + i * 800, true);
                int expected = i + 1;
                helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(BeliefStore.PURSUIT) && b.evidence() == expected), "Withdrawal must produce visible movement evidence");
                actor.discard();
            }
            var belief = scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.PURSUIT)).findFirst().orElseThrow();
            helper.assertTrue(belief.evidence() == 4 && belief.confidence() == .8, "Four real withdrawal responses reach threshold");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).hints().stream()
                    .filter(h -> h.pattern().equals(BeliefStore.PURSUIT)).allMatch(h -> h.confidence() < .75), "Fourth observation cannot unlock its own encounter");
            scene.clock(now + 3200); player.setPos(scene.position(6, 4)); var actor = scene.architect(12, 4);
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            Vec3 issuedFrom = null;
            for (int i = 0; i < 100; i++) {
                tick(scene, actor, now + 3200 + i);
                if (issuedFrom == null && MaeveDirector.positionDirective(actor) != null) issuedFrom = actor.position();
            }
            var directive = MaeveDirector.positionDirective(actor);
            helper.assertTrue(directive != null && directive.pattern().equals(BeliefStore.PURSUIT) && directive.arrivedAt() >= 0,
                    "A later encounter physically withdraws and holds on the inherited prediction");
            helper.assertTrue(issuedFrom != null && actor.getX() - issuedFrom.x > 2,
                    "Once issued, the counter physically steps away from the locally visible player");
            for (int i = 100; i <= 120; i++) tick(scene, actor, now + 3200 + i);
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(BeliefStore.PURSUIT) && b.contradictions() == 1),
                    "Staying still in continuous sight breaks the prediction");
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Broken prediction keeps its visible hold");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveConditionalOcclusionReloadAndErasureAreInconclusive(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 34, scene -> {
            floor(scene); var player = scene.player("learning_hidden", 8, 4); var actor = scene.architect(2, 4);
            long now = (scene.gameTime / 20 + 1) * 20; scene.clock(now);
            MaeveDirector.observeWithdrawal(actor, player); scene.wall(true);
            scene.clock(now + 10); MaeveDirector.tick(scene.server); scene.wall(false);
            for (int i = 20; i <= 120; i += 10) { scene.clock(now + i); MaeveDirector.tick(scene.server); }
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Occlusion cannot become a failed prediction");
            scene.clock(now + 300); MaeveDirector.observeWithdrawal(actor, player);
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            scene.clock(now + 310); MaeveDirector.tick(scene.server);
            helper.assertTrue(scene.beliefs(player).isEmpty(), "Reload cannot resume a partial movement episode");
            MaeveDirector.observeWithdrawal(actor, player); PostMaeveWorldState.markErased(scene.level);
            PostMaeveWorldState.setForDebug(scene.server, false); scene.clock(now + 500); MaeveDirector.tick(scene.server);
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).profiles() == 0, "Erasure clears pending episodes and stored profiles");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveCounterResultsUseFinalDamageAndPreservePlayerIsolation(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 35, scene -> {
            var player = scene.player("learning_trade", 8, 4); var other = scene.player("learning_other", 8, 7);
            var actor = scene.architect(2, 4); scene.roof(true);
            long start = (scene.gameTime / 20 + 1) * 20;
            for (int i = 0; i < 4; i++) { scene.clock(start + i * 610); player.finish(scene.potion()); }
            scene.clock(start + 2500);
            helper.assertTrue(MaeveDirector.chooseCommitment(actor, player, List.of(new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY,
                    actor.blockPosition(), null, 0))), "History issues a real counter");
            MaeveDirector.commitmentArrived(actor);
            scene.hit(scene.architect(3, 7), other, true, 2); // Another player's separate fight must remain isolated.
            actor.setHealth(1); scene.hit(actor, player, true, 100);
            helper.assertTrue(!actor.isAlive(), "The held executor must actually be killed by the subject");
            scene.clock(start + 2520); MaeveDirector.tick(scene.server);
            var policy = MaeveSavedData.get(scene.server).store().commitment(player.getUUID());
            var row = policy.performance().save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(row.getInt("uses") == 1 && row.getInt("failures") == 1, "Witnessed final damage records one failed hold");
            helper.assertTrue(MaeveSavedData.get(scene.server).store().commitment(other.getUUID()).performance().save()
                    .getList("contexts", Tag.TAG_COMPOUND).isEmpty(), "Other player inherits no strategy result");
            var backup = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(backup, scene.level.registryAccess()));
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(s -> s.contains("failure=1")), "Dump retains the witnessed outcome after reload");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveCounterHiddenDamageIsUnknownAndVisibleFatalHitSucceeds(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 36, scene -> {
            var player = new MaeveObservationGameTest.TestPlayer(scene.level, "learning_fatal") {
                @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
            };
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.setPos(scene.position(8, 4));
            scene.level.addNewPlayer(player); scene.entities.add(player);
            var actor = scene.architect(2, 4); scene.roof(true);
            long now = (scene.gameTime / 20 + 1) * 20;
            for (int i = 0; i < 4; i++) { scene.clock(now + i * 610); player.finish(scene.potion()); }
            scene.clock(now + 2500);
            var option = new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY, actor.blockPosition(), null, 0);
            helper.assertTrue(MaeveDirector.chooseCommitment(actor, player, List.of(option)), "Hold is issued");
            MaeveDirector.commitmentArrived(actor); scene.wall(true); scene.hit(actor, player, true, 2); scene.wall(false);
            MaeveDirector.releaseCommitment(actor, "TIME_COMPLETE");
            var policy = MaeveSavedData.get(scene.server).store().commitment(player.getUUID());
            var row = policy.performance().save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(row.getInt("unknown") == 1 && row.getInt("failures") == 0, "Hidden shooter cannot supply a attributed failure");
            scene.clock(now + 3200);
            helper.assertTrue(MaeveDirector.chooseCommitment(actor, player, List.of(option)), "A later hold can retry");
            MaeveDirector.commitmentArrived(actor); player.setHealth(1); player.invulnerableTime = 0;
            // FakePlayer does not tick away spawn protection. This vanilla bypass source still travels
            // through the real LivingDamageEvent.Post path and attributes the final hit to the executor.
            player.hurt(new net.minecraft.world.damagesource.DamageSource(scene.level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL), actor), 100);
            helper.assertTrue(!player.isAlive(), "The observed outgoing damage must actually be fatal");
            MaeveDirector.releaseCommitment(actor, "TIME_COMPLETE");
            row = policy.performance().save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
            helper.assertTrue(row.getInt("successes") == 1, "Final damage records the visible fatal hit before cleanup");
        });
    }
}
