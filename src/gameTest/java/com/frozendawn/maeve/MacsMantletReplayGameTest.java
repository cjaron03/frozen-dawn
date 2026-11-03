package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MacsMantletReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void mantletReplayParsesAndRealArrowAdvancesPracticeOnce(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 149, scene -> {
            for (String name : new String[]{"load", "setup", "initialize", "build", "terrain", "kit", "practice", "practice_ready",
                    "hit", "accepted", "ready", "start", "start_ready", "snapshot", "remove_actor", "finish", "abort", "end", "again", "gap", "status", "tick"})
                helper.assertTrue(scene.server.getFunctions().get(ResourceLocation.parse("macs_mantlet:" + name)).isPresent(), "Replay function parses: " + name);
            var transport = new MaeveObservationGameTest.TestPlayer(scene.level, "mantlet_transport");
            var player = new net.minecraft.server.level.ServerPlayer(scene.server, scene.level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "mantlet_practice"), net.minecraft.server.level.ClientInformation.createDefault());
            player.connection = transport.connection;
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(scene.position(8, 4)); scene.level.addNewPlayer(player); scene.entities.add(player);
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            java.util.function.Consumer<String> run = s -> scene.server.getCommands().performPrefixedCommand(source, s);
            run.accept("function macs_mantlet:load"); run.accept("scoreboard players set #stage mm 1"); run.accept("scoreboard players set #trained mm 0");
            var actor = scene.architect(2, 4); actor.addTag("macs_mantlet_actor"); actor.addTag("macs_mantlet_training");
            var arrow = new net.minecraft.world.entity.projectile.Arrow(scene.level, player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.setPos(player.getEyePosition()); arrow.setDeltaMovement(actor.getEyePosition().subtract(arrow.position()).normalize().scale(2));
            scene.level.addFreshEntity(arrow); scene.entities.add(arrow);
            for (int t = 0; t < 6; t++) scene.level.tickNonPassenger(arrow);
            var board = scene.server.getScoreboard(); var objective = board.getObjective("mm");
            java.util.function.ToIntFunction<String> score = name -> board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            try {
                helper.assertTrue(score.applyAsInt("#trained") == 1 && score.applyAsInt("#stage") == 2, "A real arrow must dispatch exactly one practice reward");
                helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs().stream().anyMatch(b -> b.pattern().equals(BeliefStore.RANGED) && b.confidence() == .2), "Training progress must correspond to actual observed damage");
                run.accept("function macs_mantlet:hit");
                helper.assertTrue(score.applyAsInt("#trained") == 1, "Repeated reward dispatch cannot skip a practice encounter");
            } finally { board.removeObjective(objective); board.removeObjective(board.getObjective("mm_deaths")); }
        });
    }
}
