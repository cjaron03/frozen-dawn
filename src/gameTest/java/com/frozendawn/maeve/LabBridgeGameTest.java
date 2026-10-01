package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.labbridge.LabRequest;
import com.frozendawn.labbridge.MinecraftLabRuntime;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabBridgeGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void labBridgeNativeCommandsHonorWorldAndStage(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 390, 3, s -> {
            var board = s.server.getScoreboard();
            var objective = board.addObjective("labbridge", ObjectiveCriteria.DUMMY, Component.literal("Lab"), ObjectiveCriteria.RenderType.INTEGER, false, null);
            var stage = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective); stage.set(2);
            var runs = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#runs"), objective);
            var player = s.player("LabOperator", 0, 0);
            boolean frozen = s.server.tickRateManager().isFrozen();
            MinecraftLabRuntime runtime = null;
            java.nio.file.Path policy = null;
            try {
                policy = Files.createTempFile("lab-bridge-native-", ".json");
                JsonObject root = new JsonObject(); root.addProperty("schema", 1);
                JsonObject rule = new JsonObject(); rule.addProperty("world", s.server.getWorldData().getLevelName());
                rule.add("scores", com.google.gson.JsonParser.parseString("[{\"objective\":\"labbridge\",\"holder\":\"#stage\",\"equals\":2}]").getAsJsonArray());
                JsonObject functions = new JsonObject(); functions.add("lab_bridge:probe", rule); functions.add("lab_bridge:refuse", rule); root.add("functions", functions);
                Files.writeString(policy, root.toString()); runtime = new MinecraftLabRuntime(s.server, policy, () -> java.util.List.of(player));
                var dumpArgs = new JsonObject(); dumpArgs.addProperty("player", player.getUUID().toString());
                var dump = run(runtime, "maeve_dump", dumpArgs);
                helper.assertTrue(dump.get("commandSuccess").getAsBoolean() && !dump.getAsJsonArray("output").isEmpty(), "Native Maeve dump must return captured diagnostics");
                var args = new JsonObject(); args.addProperty("name", "lab_bridge:probe"); args.addProperty("player", player.getUUID().toString());
                var result = run(runtime, "function", args);
                helper.assertTrue(result.get("commandSuccess").getAsBoolean() && runs.get() == 1, "Approved native function executes exactly once");
                stage.set(3);
                try { run(runtime, "function", args); throw new AssertionError("Wrong stage ran"); }
                catch (java.util.concurrent.CompletionException expected) { helper.assertTrue(runs.get() == 1, "Stage guard prevented mutation"); }
                stage.set(2); args.addProperty("name", "lab_bridge:refuse");
                var refused = run(runtime, "function", args);
                helper.assertTrue(refused.get("commandResult").getAsInt() == 0 && refused.get("commandOutcome").getAsString().equals("ZERO_RESULT"), "Native return zero stays visible");
                var request = request(runtime, "unfreeze", new JsonObject());
                var stale = new LabRequest(request.id(), "previous-session", request.world(), request.issuedAt(), request.expiresAt(), request.operation(), request.args());
                run(runtime, "freeze", new JsonObject());
                helper.assertTrue(run(runtime, "snapshot", new JsonObject()).get("frozen").getAsBoolean(), "Diagnostics work in a frozen simulation");
                try { runtime.execute(stale).join(); throw new AssertionError("Stale request ran"); }
                catch (java.util.concurrent.CompletionException expected) { helper.assertTrue(s.server.tickRateManager().isFrozen(), "Old session cannot unfreeze the world"); }
                runtime.close();
                try { run(runtime, "snapshot", new JsonObject()); throw new AssertionError("Closed runtime ran"); }
                catch (java.util.concurrent.CompletionException expected) { }
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            finally {
                if (runtime != null) runtime.close();
                s.server.tickRateManager().setFrozen(frozen);
                board.removeObjective(objective);
                if (policy != null) try { Files.delete(policy); } catch (java.io.IOException ignored) { }
            }
        });
    }
    private static LabRequest request(MinecraftLabRuntime runtime, String operation, JsonObject args) {
        long now = System.currentTimeMillis();
        return new LabRequest(UUID.randomUUID().toString(), runtime.session(), runtime.world(), now, now + 30000, operation, args);
    }
    private static JsonObject run(MinecraftLabRuntime runtime, String operation, JsonObject args) {
        return runtime.execute(request(runtime, operation, args)).join();
    }
}
