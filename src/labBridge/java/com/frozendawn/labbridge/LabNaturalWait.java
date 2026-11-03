package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Lab-only acceleration of an empty waiting interval. Never changes a spawn roll or actor. */
public final class LabNaturalWait {
    private final ServerLevel level;
    private final UUID participant;
    private final Vec3 home;
    private final float health;
    private final long armedAt;
    private final int limit;
    private final Predicate<JsonObject> evidence;
    private long sprintAt, lastTick = Long.MIN_VALUE;
    private int readyTicks;
    private String phase = "ARMED", reason = "";
    private UUID candidate;
    private long arrivalHoldTime;
    private int arrivalHoldServerTick;

    public LabNaturalWait(ServerLevel level, ServerPlayer player, int limit, Predicate<JsonObject> evidence) {
        if (limit < 1 || limit > 24000) throw new IllegalArgumentException("Waiting limit must be 1..24000 ticks");
        this.level = level; this.participant = player.getUUID(); this.home = player.position();
        this.health = player.getHealth(); this.armedAt = level.getGameTime();
        this.limit = limit; this.evidence = evidence;
    }
    public String phase() { return phase; }
    public String reason() { return reason; }
    public boolean held() { return phase.equals("HELD"); }
    public boolean playing() { return phase.equals("PLAYING"); }
    public boolean arrivalConfirmed() { return held() && reason.equals("BLIND_ARRIVAL"); }
    public void tick(ServerPlayer player, boolean contextValid) {
        if (!phase.equals("ARMED") && !phase.equals("WAITING")) return;
        long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        if (!contextValid || player == null || !participant.equals(player.getUUID()) || !player.isAlive()
                || player.getHealth() < health || player.position().distanceToSqr(home) > .25 * .25) {
            hold("PLAYER_OR_CONTEXT_CHANGED"); return;
        }
        var manager = level.getServer().tickRateManager();
        if (phase.equals("ARMED")) {
            if (manager.isFrozen() || manager.isSprinting() || manager.isSteppingForward()) {
                hold("UNEXPECTED_CLOCK"); return;
            }
            readyTicks = player.onGround() ? readyTicks + 1 : 0;
            if (now - armedAt >= 200 && readyTicks < 40) { hold("GROUND_NOT_READY"); return; }
            if (readyTicks >= 40) {
                sprintAt = now; phase = "WAITING";
                // requestGameToSprint restores its previous frozen state at exhaustion.
                // Start frozen so exhaustion cannot silently resume ordinary AI.
                manager.setFrozen(true); manager.requestGameToSprint(limit);
                if (!record("SPRINT_STARTED")) hold("EVIDENCE_FAILED");
            }
        } else if (now - sprintAt >= limit || !manager.isSprinting()) {
            hold("NO_BLIND_ARRIVAL");
        }
    }
    /** Called on the joining tick, before insertion and before this actor's first AI tick. */
    public void joining(ArchitectEntity actor) {
        if (actor.level() != level) return;
        boolean sameTickBlind = held() && reason.equals("OTHER_ARCHITECT_ARRIVAL") && actor.isBlindArchitect()
                && level.getGameTime() == arrivalHoldTime && level.getServer().getTickCount() == arrivalHoldServerTick;
        if (!phase.equals("ARMED") && !phase.equals("WAITING") && !sameTickBlind) return;
        arrivalHoldTime = level.getGameTime(); arrivalHoldServerTick = level.getServer().getTickCount();
        candidate = actor.getUUID();
        hold(actor.isBlindArchitect() ? "ARRIVAL_PENDING_INSERTION" : "OTHER_ARCHITECT_ARRIVAL");
    }
    /** A join event alone is not proof that entity insertion succeeded. */
    public void confirmInsertion() {
        if (!held() || !reason.equals("ARRIVAL_PENDING_INSERTION")) return;
        reason = level.getEntity(candidate) instanceof ArchitectEntity actor && actor.isBlindArchitect()
                ? "BLIND_ARRIVAL" : "ARRIVAL_NOT_LOADED";
        if (!record(reason)) hold("EVIDENCE_FAILED");
    }
    public void hold(String why) {
        if (playing()) return;
        var manager = level.getServer().tickRateManager();
        // On the last scheduled tick remainingSprintTicks is already zero, so
        // stopSprinting returns false although isSprinting is still true.
        if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
        manager.stopStepping(); manager.setFrozen(true);
        phase = "HELD"; reason = why; record(why);
    }
    public boolean play(ServerPlayer player) {
        var manager = level.getServer().tickRateManager();
        if (!arrivalConfirmed() || player == null || !participant.equals(player.getUUID())
                || !player.isAlive() || !manager.isFrozen() || manager.isSprinting()
                || manager.isSteppingForward() || manager.tickrate() != 20.0f) return false;
        phase = "PLAYING";
        if (!record("NORMAL_PLAY_RELEASE")) {
            phase = "HELD"; reason = "EVIDENCE_FAILED"; manager.setFrozen(true); return false;
        }
        manager.setFrozen(false); return true;
    }
    private boolean record(String event) {
        var row = new JsonObject(); var manager = level.getServer().tickRateManager();
        row.addProperty("event", event); row.addProperty("phase", phase); row.addProperty("reason", reason);
        row.addProperty("gameTime", level.getGameTime()); row.addProperty("armedAt", armedAt);
        row.addProperty("sprintAt", sprintAt); row.addProperty("limit", limit);
        row.addProperty("participant", participant.toString()); row.addProperty("candidate", candidate == null ? "" : candidate.toString());
        row.addProperty("frozen", manager.isFrozen()); row.addProperty("sprinting", manager.isSprinting());
        row.addProperty("stepping", manager.isSteppingForward()); row.addProperty("tickRate", manager.tickrate());
        if (candidate != null && level.getEntity(candidate) instanceof ArchitectEntity actor) {
            row.addProperty("actorNbt", actor.saveWithoutId(new CompoundTag()).toString());
            row.addProperty("actorTick", actor.tickCount);
        }
        return evidence.test(row);
    }
}
