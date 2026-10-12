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
    private volatile LabInputCapture input;
    private UUID inputPlayer, inputActor;
    private boolean inputView;
    private int inputViewRun;
    private UUID crouchPlayer, crouchActor, crouchCheck;
    private Path crouchDirectory;
    private int crouchSamples, crouchStable, crouchViewRun;
    private long crouchLastTick;
    private net.minecraft.world.phys.Vec3 crouchPosition;
    private String crouchReason;
    private final JsonArray crouchRows = new JsonArray();
    private volatile LabGroundCheck groundCheck;
    private UUID groundPlayer, groundActor;
    private int groundRun;

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
    public void close() {
        active = false;
        if (input != null) input.finish("WORLD_CLOSED");
        if (groundCheck != null && groundCheck.waiting()) groundCheck.finish("WORLD_CLOSED");
        if ("WAITING_FOR_CROUCH".equals(crouchReason)) finishCrouchCheck("WORLD_CLOSED");
    }
    private final LabClientClock clientClock = new LabClientClock();
    public void inputClock(JsonObject row,long rawClientTime) { clientClock.sample(row,rawClientTime); }
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
            case "input_start" -> {
                if (input != null && input.active()) throw new IllegalStateException("An input check is already recording");
                var participant = player(args);
                if (players.get().size() != 1 || !server.getWorldData().getLevelName().equals("Blind Architect Reusable Lab")
                        || LabBlindChannels.capturedForInput(participant.createCommandSourceStack()) != 1)
                    throw new IllegalStateException("Input check requires the preserved, held, completed inconclusive view24");
                input = new LabInputCapture(server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-input"),
                        session, participant.getUUID().toString(), snapshot(false));
                inputPlayer = participant.getUUID();
                inputActor = participant.getPersistentData().getUUID("BlindLabChannelActor");
                inputView = false;
                participant.sendSystemMessage(Component.literal("INPUT CHECK: unpause and hold LEFT SHIFT without moving for 12 seconds. The BA stays held. Pause at INPUT CHECK COMPLETE."));
                yield input.status();
            }
            case "input_dump" -> {
                if (groundCheck != null && (groundCheck.waiting() || input == null)) yield groundCheck.status();
                if (input == null) {
                    if (crouchCheck != null) yield crouchStatus();
                    throw new IllegalStateException("No input check exists in this world session");
                }
                var state = input.status();
                if (crouchCheck != null) state.add("crouchCheck", crouchStatus());
                if (groundCheck != null) state.add("groundCheck", groundCheck.status());
                yield state;
            }
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
                var participant = player(args);
                Integer replayRun = score("blind_vib", "#run");
                if (function.equals("blind_vibration_lab:start") && (Integer.valueOf(34).equals(replayRun) || Integer.valueOf(35).equals(replayRun) || Integer.valueOf(36).equals(replayRun) || Integer.valueOf(37).equals(replayRun) || Integer.valueOf(38).equals(replayRun) || Integer.valueOf(39).equals(replayRun) || Integer.valueOf(40).equals(replayRun) || Integer.valueOf(41).equals(replayRun) || Integer.valueOf(42).equals(replayRun) || Integer.valueOf(43).equals(replayRun) || Integer.valueOf(44).equals(replayRun) || Integer.valueOf(45).equals(replayRun) || Integer.valueOf(46).equals(replayRun) || Integer.valueOf(47).equals(replayRun) || Integer.valueOf(48).equals(replayRun) || Integer.valueOf(49).equals(replayRun) || Integer.valueOf(50).equals(replayRun) || Integer.valueOf(51).equals(replayRun) || Integer.valueOf(52).equals(replayRun) || Integer.valueOf(53).equals(replayRun))) {
                    if (groundCheck != null && (groundCheck.waiting() || groundRun==replayRun) || input != null && input.active()) throw new IllegalStateException("Ground check already armed; preserve this attempt");
                    var actor = viewActor(participant);
                    if (players.get().size()!=1 || actor==null || !participant.isCreative()) throw new IllegalStateException("Ground check requires a fresh held scene");
                    groundPlayer=participant.getUUID(); groundActor=actor; groundRun=replayRun;
                    participant.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); participant.setNoGravity(false);
                    participant.getAbilities().flying=false; participant.getAbilities().mayfly=false; participant.onUpdateAbilities();
                    var initial=snapshot(true); initial.addProperty("player",groundPlayer.toString()); initial.addProperty("actor",actor.toString());
                    initial.addProperty("viewRun",replayRun); initial.addProperty("x",participant.getX());
                    groundCheck=new LabGroundCheck(server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-ground"),initial);
                    participant.sendSystemMessage(Component.literal(replayRun>=43 ? "GROUND CHECK: stand still." : "GROUND CHECK: unpause and stand still. The actor and timer stay held until two continuous seconds grounded on both clocks. Wait for the action cue after VIEW STARTED."));
                    var reply=groundCheck.status(); reply.addProperty("accepted","WAITING_FOR_GROUND"); yield reply;
                }
                if (function.equals("blind_vibration_lab:start") && (Integer.valueOf(27).equals(replayRun) || Integer.valueOf(28).equals(replayRun))) {
                    if (crouchCheck != null || input != null && input.active())
                        throw new IllegalStateException("The crouch check was already armed; preserve this attempt");
                    var actor = viewActor(participant);
                    if (players.get().size() != 1 || actor == null)
                        throw new IllegalStateException("Crouch check requires the fresh, held, ready crouch view");
                    crouchPlayer = participant.getUUID(); crouchActor = actor; crouchCheck = UUID.randomUUID(); crouchViewRun = replayRun;
                    crouchPosition = participant.position(); crouchLastTick = participant.serverLevel().getGameTime() - 1;
                    crouchReason = "WAITING_FOR_CROUCH";
                    crouchDirectory = server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-ready/check-" + crouchCheck);
                    java.nio.file.Files.createDirectories(crouchDirectory);
                    java.nio.file.Files.writeString(crouchDirectory.resolve("start.json"), snapshot(true) + "\n", java.nio.file.StandardOpenOption.CREATE_NEW);
                    participant.sendSystemMessage(Component.literal("CROUCH CHECK: unpause, stay still and hold LEFT SHIFT. The timer and BA stay held until two continuous seconds crouched. Keep Shift after VIEW STARTED."));
                    var accepted = crouchStatus(); accepted.addProperty("accepted", "WAITING_FOR_CROUCH");
                    yield accepted;
                }
                if (function.equals("blind_vibration_lab:start") && (Integer.valueOf(25).equals(replayRun) || Integer.valueOf(26).equals(replayRun) || Integer.valueOf(29).equals(replayRun) || Integer.valueOf(30).equals(replayRun) || Integer.valueOf(31).equals(replayRun) || Integer.valueOf(32).equals(replayRun) || Integer.valueOf(33).equals(replayRun))) {
                    if (input != null && input.active()) throw new IllegalStateException("An input check is already recording");
                    var actor = LabBlindChannels.inputViewActor(participant.createCommandSourceStack().withPermission(2));
                    if (players.get().size() != 1 || actor == null)
                        throw new IllegalStateException("Input recording requires the fresh, held, ready input-recorded view");
                    yield beginRecordedView(participant, replayRun, actor);
                }
                yield command("function " + function, participant);
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
    /** Only immutable JSON crosses from the client thread; no client classes are loaded on a headless server. */
    public boolean inputActive(MinecraftServer expected) { return active && server == expected && (input != null && input.active() || groundCheck != null && groundCheck.waiting() || LabBlindUnload.active(server) || LabBlindStale.active(server) || LabBlindPersistence.active(server) || LabBlindLan.active(server) || LabBlindAttention.active(server) || LabBlindReconnect.active(server)); }
    /** Shared dispatch for the integrated-server subscriber and native fixtures. */
    public void serverTick(MinecraftServer expected) {
        if (active && server == expected) { clientClock.publish(server.overworld().getGameTime()); inputTick(); LabBlindUnload.tick(server); LabBlindStale.tick(server); LabBlindPersistence.tick(server); LabBlindLan.tick(server); LabBlindAttention.tick(server); LabBlindReconnect.tick(server); }
    }
    public void inputClient(JsonObject row, boolean keyEvent) {
        LabBlindUnload.client(server,row,keyEvent);
        LabBlindStale.client(server,row,keyEvent);
        LabBlindPersistence.client(server,row,keyEvent);
        LabBlindLan.hostClient(server,row,keyEvent);
        LabBlindAttention.hostClient(server,row,keyEvent);
        LabBlindReconnect.hostClient(server,row,keyEvent);
        if (active && groundCheck != null && groundCheck.waiting()) { groundCheck.client(row,keyEvent); return; }
        var capture = input;
        if (active && capture != null) capture.client(row, keyEvent);
    }
    public void inputTick() {
        if (!active || server.isPaused()) return;
        if ("WAITING_FOR_CROUCH".equals(crouchReason)) { tickCrouchCheck(); return; }
        if (groundCheck != null && groundCheck.waiting()) { tickGroundCheck(); return; }
        var capture = input;
        if (capture == null || !capture.active()) return;
        var roster = players.get();
        if (roster.size() != 1 || !roster.getFirst().getUUID().equals(inputPlayer)) { capture.finish("PARTICIPANT_CHANGED"); return; }
        var participant = roster.getFirst();
        var data = participant.getPersistentData();
        String actorKey = inputView && inputViewRun >= 36 ? "BlindLabDecoyActor" : "BlindLabChannelActor";
        Entity actor = data.hasUUID(actorKey) ? participant.serverLevel().getEntity(data.getUUID(actorKey)) : null;
        if (!(actor instanceof ArchitectEntity architect) || !architect.isAlive() || !architect.getUUID().equals(inputActor)) {
            capture.finish("ACTOR_CHANGED"); return;
        }
        if (inputView) {
            Integer stage = score("blind_vib", "#stage"), timer = score("blind_vib", "#timer");
            boolean running = Integer.valueOf(2).equals(stage), ended = Integer.valueOf(3).equals(stage) && Integer.valueOf(480).equals(timer);
            if (!Integer.valueOf(inputViewRun).equals(score("blind_vib", "#run")) || !Integer.valueOf(inputViewRun).equals(score("blind_vib", "#case"))
                    || !(running || ended) || architect.isNoAi() != ended
                    || participant.gameMode.getGameModeForPlayer() != (ended ? net.minecraft.world.level.GameType.CREATIVE : net.minecraft.world.level.GameType.SURVIVAL)) {
                capture.finish("VIEW_CHANGED"); return;
            }
            if (Integer.valueOf(0).equals(timer)) return;
            if (timer == null || timer != capture.status().get("serverFrames").getAsInt() + 1) {
                capture.finish("VIEW_FRAME_GAP"); return;
            }
            var row = snapshot(true); row.addProperty("viewRun", inputViewRun); row.addProperty("viewStage", stage); row.addProperty("viewTimer", timer);
            capture.server(row);
            return; // The replay's VIEW COMPLETE cue ends both recordings; no conflicting stationary cue.
        }
        if (!participant.isCreative() || !architect.isNoAi()) {
            capture.finish("ACTOR_NOT_HELD"); return;
        }
        if (capture.server(snapshot(false))) participant.sendSystemMessage(Component.literal("INPUT CHECK COMPLETE. Release Shift and pause. Describe whether your view moved or stayed still."));
    }
    private JsonObject beginRecordedView(ServerPlayer participant, int replayRun, UUID actor) throws java.io.IOException {
        LabFunctionPolicy.check(policy, "blind_vibration_lab:start", server.getWorldData().getLevelName(), this::score);
        var initial = snapshot(true);
        initial.addProperty("captureMode", replayRun == 53 ? "MELEE_OBSTRUCTION" : replayRun == 52 ? "MELEE_RANGE_REPEAT" : replayRun == 51 ? "MELEE_RANGE" : replayRun == 50 ? "MELEE_REACH" : replayRun == 49 ? "GRIEFING_DISABLED" : replayRun == 48 ? "PROTECTED_WALL" : replayRun == 47 ? "WALL_BREACH" : replayRun == 46 ? "HEAT_REACTION" : replayRun >= 44 ? "NOISE_REACTION" : replayRun >= 42 ? "DOOR_CONTACT" : replayRun == 41 ? "MACHINE_HEARING" : replayRun >= 39 ? "DOOR_HEARING" : replayRun >= 36 ? "PROJECTILE_DECOY" : replayRun >= 32 ? "STATIONARY_MINING" : replayRun == 31 ? "ECHO_HOVER" : replayRun >= 29 ? "STANDING_WALK" : "CROUCH_WALK"); initial.addProperty("viewRun", replayRun);
        initial.addProperty("actor", actor.toString());
        if(replayRun>=34 && groundCheck!=null) initial.add("groundCheck",groundCheck.status());
        input = new LabInputCapture(server.getWorldPath(LevelResource.ROOT).resolve("blind-lab-input"),
                session, participant.getUUID().toString(), initial, 480);
        inputPlayer = participant.getUUID(); inputActor = actor; inputView = true; inputViewRun = replayRun;
        participant.getPersistentData().putUUID("BlindLabInputProbe", UUID.fromString(input.status().get("probe").getAsString()));
        participant.getPersistentData().putUUID("BlindLabInputActor", actor);
        var reply = command("function blind_vibration_lab:start", participant);
        if (!reply.get("commandOutcome").getAsString().equals("SUCCESS")) input.finish("START_FAILED");
        reply.add("inputCapture", input.status()); return reply;
    }
    private UUID viewActor(ServerPlayer participant) {
        var source=participant.createCommandSourceStack().withPermission(2);
        return (Integer.valueOf(36).equals(score("blind_vib","#run")) || Integer.valueOf(37).equals(score("blind_vib","#run")) || Integer.valueOf(38).equals(score("blind_vib","#run")) || Integer.valueOf(39).equals(score("blind_vib","#run")) || Integer.valueOf(40).equals(score("blind_vib","#run")) || Integer.valueOf(41).equals(score("blind_vib","#run")) || Integer.valueOf(42).equals(score("blind_vib","#run")) || Integer.valueOf(43).equals(score("blind_vib","#run")) || Integer.valueOf(44).equals(score("blind_vib","#run")) || Integer.valueOf(45).equals(score("blind_vib","#run")) || Integer.valueOf(46).equals(score("blind_vib","#run")) || Integer.valueOf(47).equals(score("blind_vib","#run")) || Integer.valueOf(48).equals(score("blind_vib","#run")) || Integer.valueOf(49).equals(score("blind_vib","#run")) || Integer.valueOf(50).equals(score("blind_vib","#run")) || Integer.valueOf(51).equals(score("blind_vib","#run")) || Integer.valueOf(52).equals(score("blind_vib","#run")) || Integer.valueOf(53).equals(score("blind_vib","#run"))) ? LabBlindDecoy.inputViewActor(source) : LabBlindChannels.inputViewActor(source);
    }
    private void tickGroundCheck() {
        var roster=players.get();
        if(roster.size()!=1 || !roster.getFirst().getUUID().equals(groundPlayer)) { groundCheck.finish("PARTICIPANT_CHANGED"); return; }
        var participant=roster.getFirst();
        if(!Integer.valueOf(groundRun).equals(score("blind_vib","#run"))
                || !groundActor.equals(viewActor(participant))) {
            groundCheck.finish("VIEW_CHANGED"); participant.sendSystemMessage(Component.literal("GROUND CHECK invalid scene. Pause and preserve it.")); return;
        }
        var row=snapshot(true);row.addProperty("viewRun",groundRun);row.addProperty("viewStage",score("blind_vib","#stage"));row.addProperty("viewTimer",score("blind_vib","#timer"));
        if(groundCheck.server(row)) {
            participant.getPersistentData().putUUID("BlindLabGroundCheck",UUID.fromString(groundCheck.status().get("check").getAsString()));
            try {
                var started=beginRecordedView(participant,groundRun,groundActor);
                participant.sendSystemMessage(Component.literal(started.get("commandOutcome").getAsString().equals("SUCCESS")
                        ? (groundRun>=43 ? "VIEW STARTED: stay still. Watch center cues." : groundRun==42 ? "VIEW STARTED: aim at the wooden door and stay still. At OPEN NOW click once and release within two seconds; at CLOSE click once. Mouse turning allowed; watch until VIEW COMPLETE." : groundRun==41 ? "VIEW STARTED: stay still and watch. The heater will run automatically from MACHINE ON until MACHINE OFF; keep watching until VIEW COMPLETE." : groundRun>=39 ? "VIEW STARTED: stay still. At OPEN right click the door once with empty slot1; at CLOSE click the open door once. Watch until VIEW COMPLETE." : groundRun>=36 ? "VIEW STARTED: stay still. At THROW, aim at the ceiling light and right click once with slot1; watch until VIEW COMPLETE." : "VIEW STARTED: stay still. Mine only from WORK until STOP; then watch until VIEW COMPLETE.")
                        :"GROUND START failed. Pause and preserve this scene."));
            } catch(Exception failure) { participant.sendSystemMessage(Component.literal("GROUND START failed. Pause and preserve this scene.")); }
        } else if(!groundCheck.waiting()) participant.sendSystemMessage(Component.literal("GROUND CHECK ended without starting. Pause and preserve this scene."));
    }
    private JsonObject crouchStatus() {
        var out = new JsonObject(); out.addProperty("check", crouchCheck.toString());
        out.addProperty("session", session); out.addProperty("player", crouchPlayer.toString()); out.addProperty("actor", crouchActor.toString());
        out.addProperty("directory", crouchDirectory.toString()); out.addProperty("reason", crouchReason);
        out.addProperty("waiting", "WAITING_FOR_CROUCH".equals(crouchReason)); out.addProperty("viewRun", crouchViewRun);
        out.addProperty("stableTicks", crouchStable); out.addProperty("requiredStableTicks", 40);
        out.addProperty("sampledTicks", crouchSamples); out.addProperty("tickLimit", 2400); return out;
    }
    private boolean finishCrouchCheck(String reason) {
        crouchReason = reason;
        try {
            var content = new StringBuilder(); crouchRows.forEach(row -> content.append(row).append('\n'));
            java.nio.file.Files.writeString(crouchDirectory.resolve("server.jsonl"), content, java.nio.file.StandardOpenOption.CREATE_NEW);
            var summary = crouchStatus();
            summary.addProperty("serverSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(java.nio.file.Files.readAllBytes(crouchDirectory.resolve("server.jsonl")))));
            java.nio.file.Files.writeString(crouchDirectory.resolve("summary.json"), summary + "\n", java.nio.file.StandardOpenOption.CREATE_NEW);
            return true;
        } catch (Exception error) { crouchReason = "ARCHIVE_FAILED"; System.err.println("[LabCrouchCheck] " + error); return false; }
    }
    private void tickCrouchCheck() {
        var roster = players.get();
        if (roster.size() != 1 || !roster.getFirst().getUUID().equals(crouchPlayer)) { finishCrouchCheck("PARTICIPANT_CHANGED"); return; }
        var participant = roster.getFirst();
        if (!Integer.valueOf(crouchViewRun).equals(score("blind_vib", "#run"))
                || !crouchActor.equals(LabBlindChannels.inputViewActor(participant.createCommandSourceStack().withPermission(2)))) {
            finishCrouchCheck("VIEW_CHANGED"); return;
        }
        long tick = participant.serverLevel().getGameTime(); if (tick <= crouchLastTick) return; crouchLastTick = tick;
        boolean settled = participant.position().distanceToSqr(crouchPosition) <= .000001
                && participant.position().distanceToSqr(new net.minecraft.world.phys.Vec3(17.5,-44,5.5)) <= .04;
        boolean crouched = settled && participant.isShiftKeyDown() && participant.getPose() == net.minecraft.world.entity.Pose.CROUCHING
                && participant.onGround() && !participant.isSprinting() && !participant.getAbilities().flying;
        crouchPosition = participant.position(); crouchStable = crouched ? crouchStable + 1 : 0; crouchSamples++;
        var row = snapshot(true); row.addProperty("crouchSettled", settled); row.addProperty("crouchValid", crouched);
        crouchRows.add(row);
        if (crouchStable >= 40) {
            if (!finishCrouchCheck("CROUCH_CONFIRMED")) { participant.sendSystemMessage(Component.literal("CROUCH CHECK archive failed. The actor remains held; pause.")); return; }
            try {
                var started = beginRecordedView(participant, crouchViewRun, crouchActor);
                participant.sendSystemMessage(Component.literal(started.get("commandOutcome").getAsString().equals("SUCCESS")
                        ? "VIEW STARTED: keep LEFT SHIFT held. Stay still until WALK, then A/D until STOP. Keep Shift through VIEW COMPLETE."
                        : "VIEW START FAILED. Preserve this attempt and pause."));
            } catch (Exception error) { crouchReason = "START_FAILED"; System.err.println("[LabCrouchCheck] " + error); }
        } else if (crouchSamples >= 2400) {
            finishCrouchCheck("TIME_LIMIT"); participant.sendSystemMessage(Component.literal("CROUCH CHECK timed out. The actor remains held; pause."));
        }
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
        out.addProperty("sampledAtMillis", System.currentTimeMillis());
        if (crouchCheck != null) out.add("crouchCheck", crouchStatus());
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
        if (entity instanceof ServerPlayer player) {
            row.addProperty("pose", player.getPose().name()); row.addProperty("height", player.getBbHeight());
            row.addProperty("sneaking", player.isShiftKeyDown()); row.addProperty("sprinting", player.isSprinting());
            row.addProperty("onGround", player.onGround()); row.addProperty("flying", player.getAbilities().flying);
            row.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
            var motion = new JsonObject(); motion.addProperty("x", player.getDeltaMovement().x);
            motion.addProperty("y", player.getDeltaMovement().y); motion.addProperty("z", player.getDeltaMovement().z);
            row.add("velocity", motion);
        }
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
