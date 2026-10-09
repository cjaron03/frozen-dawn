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
public final class MacsArcherReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 80)
    public static void macsArcherFunctionsAndAdvancementLoad(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals("macs_archer") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(!functions.isEmpty(), "The generated archer functions must be in the actual test resources");
        var calls = java.util.regex.Pattern.compile("(?:^| )function (macs_archer:[a-z0-9_/]+)");
        functions.forEach((file, resource) -> {
            var id = ResourceLocation.fromNamespaceAndPath("macs_archer",
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
                        helper.assertTrue(volume <= 32768, "Archer arena fill fits the vanilla command limit: " + line);
                    }
                }
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.assertTrue(server.getAdvancements().get(ResourceLocation.parse("macs_archer:sword_hit")) != null,
                "The actual player-hurt advancement must load, including its sword and tagged actor predicates");
        helper.succeed();
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void macsArcherPracticeActualHitAdvancesOnce(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 246, scene -> {
            // NeoForge deliberately refuses all advancement awards for FakePlayer. Use a real
            // server player with the fixture's inert packet transport so the reward path is native.
            var transport = new MaeveObservationGameTest.TestPlayer(scene.level, "archer_transport");
            var player = new net.minecraft.server.level.ServerPlayer(scene.server, scene.level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "archer_practice"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            player.connection = transport.connection;
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(scene.position(8, 4)); scene.level.addNewPlayer(player); scene.entities.add(player);
            var actor = scene.architect(6, 4);
            actor.addTag("macs_archer_actor"); actor.addTag("macs_archer_training");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            var commands = scene.server.getCommands();
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            commands.performPrefixedCommand(source, "function macs_archer:load");
            commands.performPrefixedCommand(source, "scoreboard players set #trained ma 0");
            commands.performPrefixedCommand(source, "scoreboard players set #stage ma 1");
            var advancement = scene.server.getAdvancements().get(ResourceLocation.parse("macs_archer:sword_hit"));
            helper.assertTrue(advancement != null, "Practice trigger exists");
            player.attack(actor);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                    "A real sword attack satisfies the native advancement");
            var score = scene.server.getScoreboard(); var objective = score.getObjective("ma");
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 1
                            && score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#stage"), objective).get() == 2,
                    "The actual reward advances exactly one round and opens the empty encounter gap");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs().stream().anyMatch(b -> b.pattern().equals(BeliefStore.SWORD) && b.confidence() == .2),
                    "Progress corresponds to actual Maeve evidence before training cleanup");
            helper.assertTrue(!actor.isAlive() || actor.isRemoved(), "Only the completed training actor is removed");
            commands.performPrefixedCommand(source, "function macs_archer:hit");
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 1,
                    "Repeated reward dispatch cannot skip practice rounds");
            // Refresh encounters use the same real advancement without corrupting the completed 5/5 counter.
            commands.performPrefixedCommand(source, "scoreboard players set #trained ma 5");
            commands.performPrefixedCommand(source, "scoreboard players set #refresh ma 1");
            commands.performPrefixedCommand(source, "scoreboard players set #stage ma 1");
            commands.performPrefixedCommand(source, "advancement revoke @s only macs_archer:sword_hit");
            player.setPos(scene.position(8, 4));
            var refresh = scene.architect(6, 4);
            refresh.addTag("macs_archer_actor"); refresh.addTag("macs_archer_training");
            player.attack(refresh);
            helper.assertTrue(score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#trained"), objective).get() == 5
                            && score.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#stage"), objective).get() == 2,
                    "An actual warmup hit opens the gap and preserves completed training");
            score.removeObjective(objective); score.removeObjective(score.getObjective("ma_deaths"));
        });
    }

}
