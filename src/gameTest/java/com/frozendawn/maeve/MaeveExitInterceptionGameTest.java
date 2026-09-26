package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveExitInterceptionGameTest {
    private static final String EAST = "RETREAT_BEARING_E";
    private static long rounded(long now) { return (now / 10 + 1) * 10; }

    private static void sample(MaeveObservationGameTest.Scene scene, ArchitectEntity actor,
                               MaeveObservationGameTest.TestPlayer player, long time, int x, int z) {
        MaeveWorldModelGameTest.sample(scene, actor, player, time, x, z);
    }

    private static void select(GameTestHelper helper, MaeveObservationGameTest.Scene scene, ArchitectEntity actor,
                               MaeveObservationGameTest.TestPlayer player, long now, boolean alternative) {
        scene.clock(now); player.setPos(scene.position(3, 5)); actor.setTarget(player);
        actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player); actor.setDeltaMovement(Vec3.ZERO);
        var candidates = MaeveDirector.spatialCandidates(actor, player, MaeveDirector.commitmentHints(actor, player));
        helper.assertTrue(MaeveDirector.chooseCommitment(actor, player, candidates), "Expected a historical access choice: "
                + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
        helper.assertTrue((ExitPrediction.parse(MaeveDirector.positionDirective(actor).pattern()) != null) == alternative,
                "Ordinary and conditional choices must use their own gates");
    }

    private static long arrive(GameTestHelper helper, MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long now) {
        for (int i = 0; i < 180; i++) {
            scene.clock(now + i); actor.tick();
            var directive = MaeveDirector.positionDirective(actor);
            if (directive != null && directive.arrivedAt() >= 0) return rounded(now + i);
        }
        helper.fail("The actual executor did not reach the observed exit"); return now;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void exitLearningWitnessesFiveFailuresThenGuardsAlternativeAndLosesToOriginal(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 81, scene -> {
            MaeveWorldModelGameTest.shelter(scene);
            var actor = scene.architect(10, 5); var witness = scene.architect(10, 3);
            var player = scene.player("exit_causal", 3, 5);
            long now = MaeveWorldModelGameTest.train(scene, actor, player);
            String key = null;
            for (int round = 0; round < 5; round++) {
                actor.setPos(scene.position(10, 5)); select(helper, scene, actor, player, now, false);
                now = arrive(helper, scene, actor, now);
                sample(scene, actor, player, now, 4, 3); sample(scene, witness, player, now, 4, 3);
                sample(scene, actor, player, now + 10, 4, 2); sample(scene, witness, player, now + 10, 4, 2);
                var conditional = scene.beliefs(player).stream().filter(b -> ExitPrediction.parse(b.pattern()) != null).findFirst().orElseThrow();
                key = conditional.pattern();
                helper.assertTrue(conditional.evidence() == round + 1, "Real duplicate witnesses must share one contribution");
                helper.assertTrue(MaeveDirector.positionDirective(actor).position().equals(scene.origin.offset(6, 0, 5)),
                        "The ordinary watch remains at the wrong exit after witnessed escape");
                MaeveDirector.releaseCommitment(actor, "TEST_END");
                if (round < 4) {
                    // Ordinary crossings rebuild the primary habit during real subsequent encounters.
                    // This also consumes its normal contradiction cooldown; no NBT belief injection.
                    actor.setPos(scene.position(10, 5));
                    for (int refresh = 1; refresh <= 2; refresh++) {
                        sample(scene, actor, player, now + refresh * 640, 4, 5);
                        sample(scene, actor, player, now + refresh * 640 + 10, 6, 5);
                    }
                    now += 1920;
                } else now += 650;
            }
            var learned = key;
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(learned) && b.confidence() >= .90),
                    "Five real causal episodes reach the production threshold");
            actor.setPos(scene.position(10, 5)); select(helper, scene, actor, player, now, true);
            now = arrive(helper, scene, actor, now);
            helper.assertTrue(MaeveDirector.positionDirective(actor).position().equals(scene.origin.offset(4, 0, 2)),
                    "The same executor physically reaches remembered alternative B");
            sample(scene, actor, player, now, 4, 5); sample(scene, actor, player, now + 10, 6, 5);
            helper.assertTrue(MaeveDirector.positionDirective(actor).position().equals(scene.origin.offset(4, 0, 2)),
                    "Returning to A leaves the Architect committed at B");
            helper.assertTrue(scene.beliefs(player).stream().filter(b -> ExitPrediction.parse(b.pattern()) != null).count() == 1,
                    "The second-level watch cannot train a recursive pair");
            var result = scene.beliefs(player).stream().filter(b -> b.pattern().equals(learned)).findFirst().orElseThrow();
            helper.assertTrue(result.contradictions() == 1 && Math.abs(result.confidence() - .65) < .001,
                    "Witnessed counterplay lowers the conditional confidence");
            helper.assertTrue(result.provenance().stream().anyMatch(e -> e.action().contains("FAILED_PRIMARY_INTERCEPTION")),
                    "Dump evidence retains the interception-to-crossing cause");
            scene.hit(actor, player, true, 1);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Effective damage releases the alternative watch to local defense");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).issued(), "Defense cannot purchase another bet");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void exitLearningRejectsHiddenInwardUnarrivedAndReloadedEpisodes(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 82, scene -> {
            MaeveWorldModelGameTest.shelter(scene);
            var actor = scene.architect(10, 5); var player = scene.player("exit_controls", 3, 5);
            long now = MaeveWorldModelGameTest.train(scene, actor, player);
            select(helper, scene, actor, player, now, false);
            sample(scene, actor, player, now + 10, 4, 3); sample(scene, actor, player, now + 20, 4, 2);
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> ExitPrediction.parse(b.pattern()) != null), "An order without arrival is not an interception");
            now = arrive(helper, scene, actor, now + 30);
            sample(scene, actor, player, now, 4, 2); sample(scene, actor, player, now + 10, 4, 3);
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> ExitPrediction.parse(b.pattern()) != null), "Inward crossing is not an escape");
            scene.wall(true); sample(scene, actor, player, now + 20, 4, 2); scene.wall(false);
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> ExitPrediction.parse(b.pattern()) != null), "Hidden escape is unknown");
            sample(scene, actor, player, now + 30, 4, 3);
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            sample(scene, actor, player, now + 40, 4, 2);
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> ExitPrediction.parse(b.pattern()) != null), "Reload cannot restore the causal window or stitch movement");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).issued(), "Reload preserves the spent bet");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void exitReplayFunctionsAndBothWitnessedCrossingsAreValid(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 83, scene -> {
            for (String name : List.of("setup", "build", "training", "training_go", "training_done", "gap", "ready", "next",
                    "interception", "primary_ready", "switch_done", "alternative_ready", "finish", "timeout", "hidden", "status", "tick"))
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("macs_exit:" + name)).isPresent(),
                        "The real Minecraft parser must load replay function " + name);
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int x = 0; x <= 30; x++) for (int z = 0; z <= 24; z++) scene.block(x, -1, z, stone);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) scene.block(x, 4, z, stone);
            for (int x = 23; x <= 25; x++) for (int y = 0; y <= 3; y++) for (int z = 7; z <= 9; z++) scene.block(x, y, z, bedrock);
            scene.block(24, 0, 8, air); scene.block(24, 1, 8, air); scene.block(23, 1, 8, air);
            for (var post : List.of(new net.minecraft.core.BlockPos(14, 0, 5), new net.minecraft.core.BlockPos(14, 0, 11),
                    new net.minecraft.core.BlockPos(5, 0, 2), new net.minecraft.core.BlockPos(11, 0, 2)))
                for (int y = 0; y <= 3; y++) scene.block(post.getX(), y, post.getZ(), stone);
            scene.settleLight();
            var actor = scene.architect(24, 8); var player = scene.player("exit_replay", 8, 8);
            long now = rounded(scene.gameTime);
            sample(scene, actor, player, now, 8, 8); sample(scene, actor, player, now + 10, 13, 8);
            sample(scene, actor, player, now + 20, 15, 8);
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(EAST) && b.evidence() == 1),
                    "The actual booth geometry witnesses the east crossing");
            actor.setPos(scene.position(15, 8));
            sample(scene, actor, player, now + 30, 8, 3); sample(scene, actor, player, now + 40, 8, 1);
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals("RETREAT_BEARING_N") && b.evidence() == 1),
                    "The waiting east Architect can really see both sides of the north crossing");
            helper.assertTrue(scene.beliefs(player).stream().noneMatch(b -> ExitPrediction.parse(b.pattern()) != null),
                    "Two ordinary crossings alone must not teach a conditional response");
        });
    }
}
