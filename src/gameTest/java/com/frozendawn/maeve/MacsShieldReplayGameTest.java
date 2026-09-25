package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MacsShieldReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 80)
    public static void macsSwordGuardFunctionsAndAdvancementLoad(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals("macs_guard") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(!functions.isEmpty(), "The generated shield functions must be in the actual test resources");
        var calls = java.util.regex.Pattern.compile("(?:^| )function (macs_guard:[a-z0-9_/]+)");
        functions.forEach((file, resource) -> {
            var id = ResourceLocation.fromNamespaceAndPath("macs_guard",
                    file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function loaded: " + id);
            try (var reader = resource.openAsReader()) {
                var lines = reader.lines().toList();
                CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), lines);
                for (String line : lines) {
                    var matcher = calls.matcher(line);
                    while (matcher.find()) helper.assertTrue(server.getFunctions().get(ResourceLocation.parse(matcher.group(1))).isPresent(),
                            "Referenced function exists: " + matcher.group(1));
                    // Brigadier parses fill coordinates but checks its volume only at execution.
                    if (line.startsWith("fill ")) {
                        String[] fields = line.split(" ");
                        long volume = 1;
                        for (int axis = 1; axis <= 3; axis++) volume *= 1L + Math.abs(
                                Integer.parseInt(fields[axis]) - Integer.parseInt(fields[axis + 3]));
                        helper.assertTrue(volume <= 32768, "Shield arena fill fits the vanilla command limit: " + line);
                    }
                }
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.assertTrue(server.getAdvancements().get(ResourceLocation.parse("macs_guard:sword_hit")) != null,
                "The actual player-hurt advancement must load, including its sword and tagged actor predicates");
        helper.assertTrue(server.getAdvancements().get(ResourceLocation.parse("macs_guard:accept_kill")) != null,
                "Acceptance must load its native player-kill trigger");
        helper.succeed();
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void macsSwordPracticeActualHitAdvancesOnce(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 116, scene -> {
            // NeoForge deliberately refuses all advancement awards for FakePlayer. Use a real
            // server player with the fixture's inert packet transport so the reward path is native.
            var transport = new MaeveObservationGameTest.TestPlayer(scene.level, "guard_transport");
            var player = new net.minecraft.server.level.ServerPlayer(scene.server, scene.level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "guard_practice"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            player.connection = transport.connection;
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(scene.position(8, 4)); scene.level.addNewPlayer(player); scene.entities.add(player);
            var actor = scene.architect(6, 4);
            actor.addTag("macs_guard_actor"); actor.addTag("macs_guard_training");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            var commands = scene.server.getCommands();
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            commands.performPrefixedCommand(source, "function macs_guard:load");
            commands.performPrefixedCommand(source, "scoreboard players set #trained mg 0");
            commands.performPrefixedCommand(source, "scoreboard players set #stage mg 1");
            var advancement = scene.server.getAdvancements().get(ResourceLocation.parse("macs_guard:sword_hit"));
            helper.assertTrue(advancement != null, "Practice trigger exists");
            player.attack(actor);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "A real sword attack satisfies the native advancement");
            var score = scene.server.getScoreboard(); var objective = score.getObjective("mg");
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 1
                            && score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#stage"), objective).get() == 2,
                    "The actual reward advances exactly one round and opens the empty encounter gap");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs().stream().anyMatch(b -> b.pattern().equals(BeliefStore.SWORD) && b.confidence() == .2),
                    "Progress corresponds to actual Maeve evidence before training cleanup");
            helper.assertTrue(!actor.isAlive() || actor.isRemoved(), "Only the completed training actor is removed");
            commands.performPrefixedCommand(source, "function macs_guard:hit");
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 1,
                    "Repeated reward dispatch cannot skip practice rounds");
            // Refresh encounters use the same real advancement without corrupting the completed 4/4 counter.
            commands.performPrefixedCommand(source, "scoreboard players set #trained mg 4");
            commands.performPrefixedCommand(source, "scoreboard players set #refresh mg 1");
            commands.performPrefixedCommand(source, "scoreboard players set #stage mg 1");
            commands.performPrefixedCommand(source, "advancement revoke @s only macs_guard:sword_hit");
            player.setPos(scene.position(8, 4));
            var refresh = scene.architect(6, 4);
            refresh.addTag("macs_guard_actor"); refresh.addTag("macs_guard_training");
            player.attack(refresh);
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 4
                            && score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#stage"), objective).get() == 2,
                    "An actual warmup hit opens the gap and preserves completed training");
            score.removeObjective(objective); score.removeObjective(score.getObjective("mg_deaths"));
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void macsGuardAcceptanceDistinguishesKillAndCleanup(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 136, scene -> {
            var transport = new MaeveObservationGameTest.TestPlayer(scene.level, "guard_result_io");
            var player = new net.minecraft.server.level.ServerPlayer(scene.server, scene.level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "guard_result"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            player.connection = transport.connection;
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(scene.position(8, 4)); scene.level.addNewPlayer(player); scene.entities.add(player);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            java.util.function.Consumer<String> run = command -> scene.server.getCommands().performPrefixedCommand(source, command);
            run.accept("function macs_guard:accept/load");
            var scores = scene.server.getScoreboard(); var objective = scores.getObjective("mga");
            java.util.function.ToIntFunction<String> value = name -> scores.getOrCreatePlayerScore(
                    net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            try {
                run.accept("scoreboard players set #stage mga 1");
                var actor = scene.architect(6, 4); actor.addTag("macs_guard_accept_actor");
                var position = player.position();
                run.accept("function macs_guard:accept/finish");
                helper.assertTrue(actor.isAlive() && !actor.isNoAi() && position.equals(player.position()) && value.applyAsInt("#stage") == 1,
                        "Finish cannot end a running fight or turn a cleanup into a win");
                actor.setHealth(.1F);
                player.attack(actor);
                var advancement = scene.server.getAdvancements().get(ResourceLocation.parse("macs_guard:accept_kill"));
                helper.assertTrue(!actor.isAlive() && player.getAdvancements().getOrStartProgress(advancement).isDone()
                                && value.applyAsInt("#outcome") == 1 && value.applyAsInt("#stage") == 4,
                        "An actual fatal player attack records victory through the native advancement before cleanup");
                helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs().stream()
                                .anyMatch(b -> b.pattern().equals(BeliefStore.SWORD) && b.evidence() == 1),
                        "The final attack still publishes real witnessed sword evidence");
                actor.discard();

                run.accept("advancement revoke @s only macs_guard:accept_kill");
                run.accept("scoreboard players set #stage mga 1");
                run.accept("scoreboard players set #outcome mga 0");
                var aborted = scene.architect(6, 4); aborted.addTag("macs_guard_accept_actor");
                player.attack(aborted); // Give vanilla kill attribution a recent player to credit.
                helper.assertTrue(aborted.isAlive(), "The cleanup fixture starts alive after a real hit");
                run.accept("function macs_guard:accept/abort");
                helper.assertTrue(!aborted.isAlive() && value.applyAsInt("#outcome") == 4 && value.applyAsInt("#stage") == 4,
                        "Emergency cleanup stays aborted even if vanilla credits the recent attacker for /kill");
                run.accept("function macs_guard:accept/victory");
                helper.assertTrue(value.applyAsInt("#outcome") == 4,
                        "A delayed or repeated advancement cannot rewrite the recorded abort");
            } finally {
                scores.removeObjective(objective); scores.removeObjective(scores.getObjective("mga_deaths"));
            }
        });
    }
}
