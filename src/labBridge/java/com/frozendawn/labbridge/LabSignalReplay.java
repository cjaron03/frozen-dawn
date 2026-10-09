package com.frozendawn.labbridge;

import com.frozendawn.maeve.LabSignalScene;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded, world-scoped masked visual replay. Excluded from release builds. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabSignalReplay {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static final BlockPos ORIGIN = new BlockPos(12000, 101, 12000);
    private LabSignalReplay() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("signal_access").executes(c -> allowedWorld(c.getSource()) ? 1 : 0))
                .then(Commands.literal("signal_load").executes(c -> loadArena(c.getSource())))
                .then(Commands.literal("signal_prepare").executes(c -> prepare(c.getSource())))
                .then(Commands.literal("signal_next").executes(c -> next(c.getSource())))
                .then(Commands.literal("signal_abort").executes(c -> abort(c.getSource()))));
    }
    private static boolean allowedWorld(CommandSourceStack source) {
        return !FMLEnvironment.production && Boolean.getBoolean("frozendawn.labBridge")
                && source.getServer().getWorldData().getLevelName().equals("MACS Signal Check")
                && source.getServer().getPlayerList().getPlayerCount() == 1
                && source.getEntity() instanceof ServerPlayer;
    }
    private static ServerPlayer operator(CommandSourceStack source) {
        if (!allowedWorld(source) || !(source.getEntity() instanceof ServerPlayer player)
                || !player.isCreative() || !player.getTags().contains("macs_signal_player")
                || player.serverLevel() != source.getServer().overworld()) return null;
        return player;
    }
    private static int score(MinecraftServer server, String holder) {
        var board = server.getScoreboard(); var objective = board.getObjective("msignal");
        if (objective == null) return -1;
        var value = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
        return value == null ? -1 : value.value();
    }
    private static void score(MinecraftServer server, String holder, int value) {
        var board = server.getScoreboard(); var objective = board.getObjective("msignal");
        if (objective != null) board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(value);
    }
    private static int loadArena(CommandSourceStack source) {
        if (operator(source) == null || score(source.getServer(), "#stage") != 0) return 0;
        // Explicit QA construction only: bounded 5x6 terrain columns before native /fill.
        // No chunks are force-retained and production observation/dispatch paths are untouched.
        var level = source.getServer().overworld();
        for (int x = 11968 >> 4; x <= 12032 >> 4; x++)
            for (int z = 11964 >> 4; z <= 12032 >> 4; z++) level.getChunk(x, z);
        return 1;
    }
    private static int prepare(CommandSourceStack source) {
        var player = operator(source); var server = source.getServer();
        if (player == null || score(server, "#stage") != 0 || SESSIONS.containsKey(server)) return 0;
        try {
            var path = server.getWorldPath(LevelResource.ROOT).resolve("lab-signal-order.json");
            var values = JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("order");
            if (values.size() != 4) return 0;
            boolean[] order = new boolean[4]; int count = 0;
            for (int i = 0; i < 4; i++) { order[i] = values.get(i).getAsBoolean(); if (order[i]) count++; }
            if (count != 2) return 0;
            SESSIONS.put(server, new Session(player.getUUID(), order));
            score(server, "#stage", 1); score(server, "#round", 0); score(server, "#timer", 0);
            player.sendSystemMessage(Component.literal("View ready. Return here after each description; no diagnostic commands needed."));
            return 1;
        } catch (Exception error) {
            source.sendFailure(Component.literal("View preparation failed; preserve the world and check its replay files.")); return 0;
        }
    }
    private static int next(CommandSourceStack source) {
        var player = operator(source); var server = source.getServer(); var session = SESSIONS.get(server);
        if (player == null || session == null || !session.player.equals(player.getUUID())
                || (score(server, "#stage") != 1 && score(server, "#stage") != 3)
                || session.round >= session.order.length || session.scene != null) return 0;
        var manager = server.tickRateManager();
        if (manager.isSprinting() || manager.isSteppingForward()) return 0;
        player.getAbilities().flying = true; player.onUpdateAbilities();
        player.teleportTo(server.overworld(), 12000.5, 104, 12018.5, 180, 10);
        session.round++; session.started = server.overworld().getGameTime(); session.lastTick = session.started;
        score(server, "#round", session.round); score(server, "#timer", 0); score(server, "#stage", 2);
        manager.setFrozen(true);
        player.sendSystemMessage(Component.literal("View " + session.round + " ready. Run /tick unfreeze when ready, then stay here and look around. The view stops automatically."));
        record(server, session, "armed"); return 1;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); var session = SESSIONS.get(server);
        if (session == null || score(server, "#stage") != 2) return;
        var player = server.getPlayerList().getPlayer(session.player);
        if (player == null || !player.isCreative() || player.serverLevel() != server.overworld()
                || player.distanceToSqr(12000.5, 104, 12018.5) > 36) { finish(server, session, true); return; }
        long now = server.overworld().getGameTime();
        if (now == session.lastTick || server.tickRateManager().isFrozen()) return;
        if (server.tickRateManager().isSprinting() || server.tickRateManager().isSteppingForward()) {
            finish(server, session, true); return;
        }
        session.lastTick = now; long age = now - session.started;
        score(server, "#timer", (int) age);
        if (age >= 40 && age < 640) {
            if (session.scene == null) session.scene = new LabSignalScene(server.overworld(), ORIGIN, session.order[session.round - 1]);
            else session.scene.tick();
        }
        if (age >= 680) finish(server, session, false);
    }
    private static int abort(CommandSourceStack source) {
        var session = SESSIONS.get(source.getServer());
        if (operator(source) == null || session == null) return 0;
        finish(source.getServer(), session, true); return 1;
    }
    private static void finish(MinecraftServer server, Session session, boolean invalid) {
        if (session.scene != null) { session.scene.close(); session.scene = null; }
        server.tickRateManager().stopSprinting(); server.tickRateManager().stopStepping(); server.tickRateManager().setFrozen(true);
        score(server, "#stage", invalid ? 5 : session.round == 4 ? 4 : 3);
        record(server, session, invalid ? "inconclusive" : "view_complete");
        var player = server.getPlayerList().getPlayer(session.player);
        if (player != null) player.sendSystemMessage(Component.literal(invalid
                ? "View interrupted. Tell the operator; do not restart it."
                : "View paused. Describe what you saw and what you expect to happen next, before opening diagnostics."));
    }
    private static void record(MinecraftServer server, Session session, String state) {
        try {
            String row = "{\"round\":" + session.round + ",\"state\":\"" + state + "\",\"gameTime\":"
                    + server.overworld().getGameTime() + "}\n";
            Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("lab-signal-progress.jsonl"), row,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new IllegalStateException("Could not preserve visual replay progress", error); }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var session = SESSIONS.remove(event.getServer());
        if (session != null && session.scene != null) session.scene.close();
    }
    private static final class Session {
        final UUID player; final boolean[] order;
        int round; long started, lastTick; LabSignalScene scene;
        Session(UUID player, boolean[] order) { this.player = player; this.order = order; }
    }
}
