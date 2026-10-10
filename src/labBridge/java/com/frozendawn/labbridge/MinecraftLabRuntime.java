package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;

/** All world reads and mutations run on the server thread, including validation immediately before execution. */
public final class MinecraftLabRuntime implements LabInbox.Endpoint {
    private final MinecraftServer server;
    private final Path policy;
    private final java.util.function.Supplier<java.util.List<ServerPlayer>> players;
    private final String session = UUID.randomUUID().toString();
    private final String world;
    private volatile boolean active = true;

    public MinecraftLabRuntime(MinecraftServer server, Path policy) {
        this(server, policy, () -> server.getPlayerList().getPlayers());
    }
    /** Native fixtures supply a fake online roster without editing Minecraft's immutable PlayerList view. */
    public MinecraftLabRuntime(MinecraftServer server, Path policy, java.util.function.Supplier<java.util.List<ServerPlayer>> players) {
        this.server = server;
        this.policy = policy;
        this.players = players;
        world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
    }
    public void close() { active = false; }
    public String session() { return session; }
    public String world() { return world; }

    public CompletableFuture<JsonObject> status() {
        return server.submit(() -> snapshot(false));
    }
    public CompletableFuture<JsonObject> execute(LabRequest request) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                if (!active || server.isStopped()) throw new IllegalStateException("World is closed");
                request.validate(session, world, System.currentTimeMillis());
                if (request.operation().equals("reload")) {
                    server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> {
                        if (error != null) result.completeExceptionally(error);
                        else { JsonObject reply = new JsonObject(); reply.addProperty("completed", "reload"); result.complete(reply); }
                    });
                } else result.complete(dispatch(request));
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }
    private JsonObject dispatch(LabRequest request) throws Exception {
        var args = request.args();
        return switch (request.operation()) {
            case "snapshot" -> snapshot(true);
            case "maeve_status" -> command("fd maeve status", null);
            case "maeve_dump" -> command("fd maeve dump " + player(args).getUUID(), null);
            case "architect_dump" -> {
                UUID id = UUID.fromString(args.get("entity").getAsString());
                Entity found = null;
                for (var level : server.getAllLevels()) { found = level.getEntity(id); if (found != null) break; }
                if (!(found instanceof ArchitectEntity)) throw new IllegalArgumentException("No loaded Architect with that UUID");
                yield command("fd architect dump " + id, null);
            }
            case "scores" -> {
                JsonObject values = new JsonObject();
                String objective = args.get("objective").getAsString();
                var holders = args.getAsJsonArray("holders");
                if (holders.size() > 64) throw new IllegalArgumentException("At most 64 score holders");
                for (var holder : holders) values.addProperty(holder.getAsString(), score(objective, holder.getAsString()));
                yield values;
            }
            case "function" -> {
                String function = args.get("name").getAsString();
                LabFunctionPolicy.check(policy, function, server.getWorldData().getLevelName(), this::score);
                if (server.tickRateManager().isSprinting()) throw new IllegalStateException("Wait for the current sprint to finish");
                yield command("function " + function, player(args));
            }
            case "freeze", "unfreeze", "step", "sprint" -> {
                var manager = server.tickRateManager();
                String operation = request.operation();
                if (operation.equals("freeze")) {
                    manager.stopSprinting(); manager.stopStepping(); manager.setFrozen(true);
                } else if (operation.equals("unfreeze")) {
                    if (manager.isSprinting() || manager.isSteppingForward()) throw new IllegalStateException("A tick operation is already active");
                    manager.setFrozen(false);
                } else {
                    int ticks = args.get("ticks").getAsInt();
                    if (ticks < 1 || ticks > 24000) throw new IllegalArgumentException("Ticks must be 1..24000");
                    if (!manager.isFrozen() || manager.isSprinting() || manager.isSteppingForward())
                        throw new IllegalStateException("Freeze first; a step or sprint must not already be active");
                    if (operation.equals("step")) manager.stepGameIfPaused(ticks);
                    else manager.requestGameToSprint(ticks);
                }
                JsonObject state = snapshot(false);
                state.addProperty("accepted", operation);
                state.addProperty("note", "Step/sprint acceptance is not completion. Menu/focus pause can suspend simulation.");
                yield state;
            }
            default -> throw new IllegalArgumentException("Operation is not allowed");
        };
    }
    private ServerPlayer player(JsonObject args) {
        String subject = args.has("player") ? args.get("player").getAsString() : "";
        var players = this.players.get();
        if (subject.isEmpty() && players.size() == 1) return players.getFirst();
        for (ServerPlayer player : players)
            if (player.getUUID().toString().equals(subject) || player.getGameProfile().getName().equals(subject)) return player;
        throw new IllegalArgumentException("Specify one online player by name or UUID");
    }
    private Integer score(String objective, String holder) {
        var board = server.getScoreboard(); var obj = board.getObjective(objective);
        if (obj == null) return null;
        var value = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), obj);
        return value == null ? null : value.value();
    }
    private JsonObject snapshot(boolean actors) {
        if (!active) throw new IllegalStateException("World is closed");
        JsonObject out = new JsonObject();
        out.addProperty("session", session); out.addProperty("world", world);
        out.addProperty("worldName", server.getWorldData().getLevelName());
        out.addProperty("gameTime", server.overworld().getGameTime());
        out.addProperty("paused", server.isPaused());
        out.addProperty("frozen", server.tickRateManager().isFrozen());
        out.addProperty("sprinting", server.tickRateManager().isSprinting());
        out.addProperty("stepping", server.tickRateManager().isSteppingForward());
        JsonArray players = new JsonArray();
        for (var player : this.players.get()) players.add(describe(player));
        out.add("players", players);
        if (actors) {
            JsonArray list = new JsonArray(); int scanned = 0;
            outer: for (var level : server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
                if (++scanned > 16384 || list.size() >= 128) { out.addProperty("truncated", true); break outer; }
                if (entity instanceof ArchitectEntity architect) {
                    var row = describe(entity); row.addProperty("noAI", architect.isNoAi());
                    row.addProperty("health", architect.getHealth()); list.add(row);
                }
            }
            out.add("architects", list);
            JsonArray rooms = new JsonArray();
            for (var level : server.getAllLevels()) for (var room : com.frozendawn.world.RoomAtmosphere.cachedRoomDiagnostics(level)) {
                JsonObject row = new JsonObject();
                row.addProperty("dimension", level.dimension().location().toString());
                row.addProperty("id", room.id()); row.addProperty("cells", room.geometry().cells().size());
                row.addProperty("faces", room.geometry().boundaryFaces().size());
                row.addProperty("uncertain", room.uncertain()); row.addProperty("active", room.active());
                row.addProperty("idleTicks", room.idleTicks()); row.addProperty("lastQueryAge", room.lastQueryAge());
                row.addProperty("lastQueryReason", room.lastQueryReason());
                if (room.lastQueryPos() != null) {
                    row.add("lastQueryPos", blockPos(room.lastQueryPos()));
                    row.addProperty("lastQueryBlock", level.isLoaded(room.lastQueryPos())
                            ? level.getBlockState(room.lastQueryPos()).toString() : "unloaded");
                }
                var cells = room.geometry().cells();
                row.add("min", blockPos(new net.minecraft.core.BlockPos(cells.stream().mapToInt(net.minecraft.core.BlockPos::getX).min().orElseThrow(),
                        cells.stream().mapToInt(net.minecraft.core.BlockPos::getY).min().orElseThrow(),
                        cells.stream().mapToInt(net.minecraft.core.BlockPos::getZ).min().orElseThrow())));
                row.add("max", blockPos(new net.minecraft.core.BlockPos(cells.stream().mapToInt(net.minecraft.core.BlockPos::getX).max().orElseThrow(),
                        cells.stream().mapToInt(net.minecraft.core.BlockPos::getY).max().orElseThrow(),
                        cells.stream().mapToInt(net.minecraft.core.BlockPos::getZ).max().orElseThrow())));
                JsonArray sources = new JsonArray(); room.sources().forEach(pos -> sources.add(blockPos(pos))); row.add("sources", sources);
                rooms.add(row);
            }
            out.add("roomCache", rooms);
            JsonArray heat=new JsonArray();
            for(var level:server.getAllLevels())for(var room:com.frozendawn.world.RoomThermalManager.snapshots(level)) {
                JsonObject row=new com.google.gson.Gson().toJsonTree(room).getAsJsonObject();
                row.addProperty("dimension",level.dimension().location().toString());heat.add(row);
            }
            out.add("roomThermal",heat);
        }
        return out;
    }
    private static JsonArray blockPos(net.minecraft.core.BlockPos pos) {
        JsonArray result = new JsonArray(); result.add(pos.getX()); result.add(pos.getY()); result.add(pos.getZ()); return result;
    }
    private static JsonObject describe(Entity entity) {
        JsonObject row = new JsonObject(); row.addProperty("uuid", entity.getUUID().toString());
        row.addProperty("name", entity.getName().getString()); row.addProperty("dimension", entity.level().dimension().location().toString());
        row.addProperty("x", entity.getX()); row.addProperty("y", entity.getY()); row.addProperty("z", entity.getZ());
        JsonArray tags = new JsonArray(); entity.getTags().stream().sorted().limit(64).forEach(tags::add); row.add("tags", tags);
        return row;
    }
    private JsonObject command(String command, ServerPlayer player) {
        Capture capture = new Capture();
        var source = server.createCommandSourceStack().withSource(capture).withPermission(2)
                .withCallback((success, value) -> { capture.success = success; capture.result = value; capture.called = true; });
        if (player != null) source = source.withEntity(player).withPosition(player.position()).withLevel(player.serverLevel());
        server.getCommands().performPrefixedCommand(source, command);
        JsonObject reply = new JsonObject(); reply.addProperty("command", command); reply.add("output", capture.lines);
        reply.addProperty("commandSuccess", capture.called && capture.success);
        reply.addProperty("commandResult", capture.result);
        reply.addProperty("commandOutcome", !capture.called || !capture.success ? "FAILED" : capture.result == 0 ? "ZERO_RESULT" : "SUCCESS");
        reply.addProperty("truncated", capture.truncated);
        reply.addProperty("note", "Function command success does not certify its gameplay outcome. Inspect stage and snapshot.");
        return reply;
    }
    private static final class Capture implements CommandSource {
        final JsonArray lines = new JsonArray(); int length; int result; boolean called, success, truncated;
        public void sendSystemMessage(Component message) {
            String text = message.getString();
            if (length + text.length() > 262144 || lines.size() >= 1024) { truncated = true; return; }
            lines.add(text); length += text.length();
        }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }
}
