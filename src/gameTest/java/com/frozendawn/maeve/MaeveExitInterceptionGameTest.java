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
            var actor = scene.architect(10, 5); var witness = scene.architect(10, 3);
            var player = scene.player("exit_causal", 3, 5);
            learnAndBeatAlternative(helper, scene, actor, witness, player);
            scene.hit(actor, player, true, 1);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Effective damage releases the alternative watch to local defense");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).issued(), "Defense cannot purchase another bet");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void exitAlternativeExpiresIntoPursuitAndReloadPreservesDisproof(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 84, scene -> {
            var actor = scene.architect(10, 5); var witness = scene.architect(10, 3);
            var player = MaeveReconnaissanceGameTest.damageablePlayer(scene, "exit_expiry");
            long now = learnAndBeatAlternative(helper, scene, actor, witness, player);
            var held = MaeveDirector.positionDirective(actor);
            var before = scene.beliefs(player);
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            float healthBeforeHold = player.getHealth();
            helper.assertTrue(player.isAlive(), "The learning fixture must leave a living subject for recovery");
            long end = held.holdUntil();
            for (long tick = now + 11; tick < end; tick++) { scene.clock(tick); actor.tick(); }
            helper.assertTrue(actor.isHoldingMaevePosition() && actor.blockPosition().distSqr(held.position()) <= 1,
                    "The wrong alternative remains guarded until its real deadline");
            helper.assertTrue(player.getHealth() == healthBeforeHold, "No early chase or damage interrupted the hold: before="
                    + healthBeforeHold + " after=" + player.getHealth());
            scene.clock(end); actor.tick();
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null && !actor.isHoldingMaevePosition(),
                    "Natural expiry clears both the conditional directive and its guard pose");
            for (int tick = 1; tick <= 180 && player.getHealth() == healthBeforeHold; tick++) {
                scene.clock(end + tick); actor.tick();
                helper.assertTrue(!actor.hasReconnaissanceEyes() && MaeveDirector.positionDirective(actor) == null,
                        "Pursuit cannot restart a commitment or become a scout");
            }
            helper.assertTrue(player.getHealth() < healthBeforeHold,
                    "Ordinary pursuit must physically reach and attack without the player hitting first: " + actor.inspectDecisions());
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).issued(), "The same encounter bet stays spent");
            // Reload an actual mid-hold snapshot, not a policy that was already released.
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(MaeveSavedData.get(scene.server).store().snapshot(player.getUUID(), now + 10).equals(before),
                    "Reload preserves disproof and all causal provenance at the same observation time");
            actor.tick();
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null && !actor.isHoldingMaevePosition(),
                    "Reload cannot restore the unfinished conditional watch");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).issued(), "Reload cannot buy another bet");
        });
    }

    private static long learnAndBeatAlternative(GameTestHelper helper, MaeveObservationGameTest.Scene scene,
                                                ArchitectEntity actor, ArchitectEntity witness,
                                                MaeveObservationGameTest.TestPlayer player) {
        MaeveWorldModelGameTest.shelter(scene);
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
        witness.discard();
        return now;
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
            // GameTest servers allow more than the integrated server's function permission level.
            // Parse the shipped replay at level 2 so an operator-only command cannot hide a function.
            var functions = scene.server.getResourceManager().listResources("function", id ->
                    id.getNamespace().equals("macs_exit") && id.getPath().endsWith(".mcfunction"));
            helper.assertTrue(functions.size() == 28, "All exit replay functions must be present");
            functions.forEach((id, resource) -> {
                try (var reader = resource.openAsReader()) {
                    net.minecraft.commands.functions.CommandFunction.fromLines(id, scene.server.getCommands().getDispatcher(),
                            scene.server.createCommandSourceStack().withPermission(2), reader.lines().toList());
                } catch (java.io.IOException | IllegalArgumentException error) {
                    helper.fail("Replay function must parse at permission level 2: " + id + ": " + error.getMessage());
                }
            });
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var bedrock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int x = 0; x <= 30; x++) for (int z = 0; z <= 24; z++) scene.block(x, -1, z, stone);
            for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) scene.block(x, 4, z, stone);
            for (int x = 11; x <= 13; x++) for (int y = 0; y <= 2; y++) for (int z = 10; z <= 12; z++)
                scene.block(x, y, z, y == 1 ? air : bedrock);
            scene.block(12, 0, 11, air);
            for (var post : List.of(new net.minecraft.core.BlockPos(14, 0, 5), new net.minecraft.core.BlockPos(14, 0, 11),
                    new net.minecraft.core.BlockPos(5, 0, 2), new net.minecraft.core.BlockPos(11, 0, 2)))
                for (int y = 0; y <= 3; y++) scene.block(post.getX(), y, post.getZ(), stone);
            scene.settleLight();
            var player = scene.player("exit_replay", 8, 8);
            player.addTag("macs_exit");
            helper.assertTrue(!replayCounts(scene, player, 2, scene.position(14, 8).add(.35, 0, 0)),
                    "Touching GOLD with the player's bounding box while the feet remain covered is not completion");
            helper.assertTrue(replayCounts(scene, player, 2, scene.position(15, 8).add(.25, 0, 0)),
                    "The revised GOLD completion region requires an actual outward crossing");
            helper.assertTrue(!replayCounts(scene, player, 6, scene.position(8, 2).add(0, 0, -.35)),
                    "Touching GREEN while the feet remain covered is not completion");
            helper.assertTrue(replayCounts(scene, player, 6, scene.position(8, 1).add(0, 0, -.25)),
                    "The revised GREEN completion region requires an actual outward crossing");
            long now = rounded(scene.gameTime);
            for (int round = 0; round < 5; round++) {
                player.setPos(scene.position(8, 8));
                var witness = scene.architect(12, 11);
                witness.tickCount = 0; witness.debugForceApproach(player); witness.setOnGround(true);
                // Run the actual AI through spawn warmup and a walking crossing. Reassigning
                // its target before every sample would hide reconnaissance/extraction target loss.
                for (int tick = 0; tick <= 95; tick++) {
                    scene.clock(now + tick);
                    player.setPos(scene.position(8, 8).add(Math.clamp((tick - 40) * .2, 0, 8), 0, 0));
                    witness.tick();
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(witness));
                    helper.assertTrue(!witness.hasReconnaissanceEyes(), "Close practice witness must keep tracking instead of extracting");
                    helper.assertTrue(witness.blockPosition().equals(scene.origin.offset(12, 0, 11)), "The booth keeps its witness contained");
                }
                int count = round + 1;
                helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(EAST) && b.evidence() == count),
                        "Every real AI practice crossing must reach Maeve: round " + count + " " + scene.beliefs(player));
                witness.discard(); now += 740;
            }
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(EAST) && b.confidence() >= .75),
                    "Practice actually clears the production confidence gate");
            for (int x = 11; x <= 13; x++) for (int y = 0; y <= 2; y++) for (int z = 10; z <= 12; z++) scene.block(x, y, z, air);
            player.setPos(scene.position(8, 8));
            var actor = scene.architect(21, 8);
            actor.tickCount = 80; actor.debugForceApproach(player); actor.setOnGround(true);
            now = arrive(helper, scene, actor, now);
            // The actual executor stops within 0.6 blocks of the remembered point. At that
            // stance the old decorative posts occluded the north exit, unlike the previous
            // test's manually positioned witness at x=15.
            boolean hiddenEdge = false;
            for (int x = 7; x <= 9; x++) for (int z = 0; z <= 2; z++) {
                player.setPos(scene.position(x, z));
                hiddenEdge |= !actor.hasLineOfSight(player);
            }
            helper.assertTrue(hiddenEdge, "Original posts must reproduce an occluded part of the green crossing from "
                    + actor.position().subtract(scene.position(0, 0)));
            for (var post : List.of(new net.minecraft.core.BlockPos(14, 0, 5), new net.minecraft.core.BlockPos(14, 0, 11),
                    new net.minecraft.core.BlockPos(5, 0, 2), new net.minecraft.core.BlockPos(11, 0, 2)))
                for (int y = 0; y <= 3; y++) scene.block(post.getX(), y, post.getZ(), air);
            for (var post : List.of(new net.minecraft.core.BlockPos(2, 0, 2), new net.minecraft.core.BlockPos(14, 0, 2),
                    new net.minecraft.core.BlockPos(2, 0, 14), new net.minecraft.core.BlockPos(14, 0, 14)))
                for (int y = 0; y <= 3; y++) scene.block(post.getX(), y, post.getZ(), stone);
            scene.settleLight();
            for (int x = 7; x <= 9; x++) for (int z = 0; z <= 2; z++) {
                player.setPos(scene.position(x, z));
                helper.assertTrue(actor.hasLineOfSight(player), "Corner posts must expose the full green crossing at " + x + "," + z);
            }
            walkNorth(scene, actor, player, now);
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals("RETREAT_BEARING_N") && b.evidence() == 1),
                    "Corner posts allow the waiting east Architect to witness the same north crossing");
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> ExitPrediction.parse(b.pattern()) != null && b.evidence() == 1),
                    "The fixture must record one real conditional outcome, not just advance a movement counter");
        });
    }

    private static void walkNorth(MaeveObservationGameTest.Scene scene, ArchitectEntity actor,
                                  MaeveObservationGameTest.TestPlayer player, long start) {
        for (int tick = 0; tick <= 40; tick++) {
            scene.clock(start + tick); player.setPos(scene.position(8, 8).add(0, 0, -tick * .2));
            actor.tick();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(actor));
        }
    }

    private static boolean replayCounts(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player,
                                        int stage, Vec3 position) {
        var file = net.minecraft.resources.ResourceLocation.parse("macs_exit:function/tick.mcfunction");
        try (var reader = scene.server.getResourceManager().getResource(file).orElseThrow().openAsReader()) {
            String line = reader.lines().filter(s -> s.contains("#stage mx matches " + stage + " as @a[")).findFirst().orElseThrow();
            int start = line.indexOf("@a[");
            String[] fields = line.substring(start + 3, line.indexOf(']', start)).split(",");
            for (int i = 0; i < fields.length; i++) {
                int offset = fields[i].startsWith("x=") ? scene.origin.getX() - 3000
                        : fields[i].startsWith("y=") ? scene.origin.getY() - 101
                        : fields[i].startsWith("z=") ? scene.origin.getZ() - 3000 : 0;
                if (offset != 0) fields[i] = fields[i].substring(0, 2) + (Integer.parseInt(fields[i].substring(2)) + offset);
            }
            // Keep the generated bounds and tag predicate; @s selects this isolated test player.
            var selector = new net.minecraft.commands.arguments.selector.EntitySelectorParser(
                    new com.mojang.brigadier.StringReader("@s[" + String.join(",", fields) + "]"), true).parse();
            player.setPos(position);
            return !selector.findEntities(player.createCommandSourceStack().withPermission(2)).isEmpty();
        } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException(error);
        }
    }
}
