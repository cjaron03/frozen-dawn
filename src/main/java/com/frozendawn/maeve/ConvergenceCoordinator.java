package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** History chooses a region. Each pawn executes locally; no player position enters the order. */
final class ConvergenceCoordinator {
    static final String DISPATCH = "macsConvergence", EVER_DISPATCHED = "macsConvergencePawn", DEATH_RECORDED = "macsPawnDeathRecorded";
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private final AttentionCoordinator attention;
    private final MissionPlanner missions;
    private String decision = "NO_HISTORY";
    ConvergenceCoordinator(MinecraftServer server, MaeveSavedData data, AttentionCoordinator attention, MissionPlanner missions) {
        this.server = server; this.data = data; this.attention = attention; this.missions = missions;
    }
    private long now() { return server.overworld().getGameTime(); }
    private boolean enabled() { return ConvergenceLifecycle.enabled(server, data); }
    static boolean ordinary(ArchitectEntity actor) {
        return !actor.isMasterArchitectVisual() && !actor.isHearthAssessor() && !actor.isHearthPopulationResident()
                && !AggregateReinforcementManager.isChild(actor);
    }
    static boolean assigned(ArchitectEntity actor) { return actor.getPersistentData().hasUUID(DISPATCH); }
    void observe(ArchitectEntity actor) {
        if (!enabled() || actor.isRemoved()) return;
        var memory = data.convergence();
        if (!ordinary(actor)) { if (memory.population.destroyed(actor.getUUID())) data.setDirty(); return; }
        if (actor.isAlive() && memory.population.register(actor.getUUID(), dimension(actor), actor.blockPosition())) data.setDirty();
        if (!actor.isAlive() || actor.isNoAi() || assigned(actor)) return;
        if (actor.getTarget() instanceof ServerPlayer player) localContact(actor, player);
    }
    void localContact(ArchitectEntity actor, ServerPlayer player) {
        if (!enabled() || assigned(actor) || !ordinary(actor) || !ObservationCollector.canObserve(actor, player, false)) return;
        var h = data.convergence().at(dimension(actor), actor.blockPosition());
        if (h != null) {
            h.contact(now()); data.setDirty();
            var group = data.convergence().active;
            if (group != null && group.hotspot.equals(h.id) && group.firstArrival < 0) finish("UNKNOWN", "LOCAL_CONTACT_RESUMED");
        }
    }
    void destroyed(ArchitectEntity actor) {
        if (data.convergence() != null && data.convergence().population.destroyed(actor.getUUID())) data.setDirty();
    }
    void death(ArchitectEntity actor) {
        if (!enabled() || !ordinary(actor) || actor.isNoAi() || actor.getPersistentData().getBoolean(DEATH_RECORDED)) return;
        actor.getPersistentData().putBoolean(DEATH_RECORDED, true);
        var memory = data.convergence();
        memory.population.destroyed(actor.getUUID());
        memory.death(actor.getUUID(), dimension(actor), actor.blockPosition(), now(), actor.getPersistentData().getBoolean(EVER_DISPATCHED));
        if (memory.active != null) {
            var h = memory.hotspots.get(memory.active.hotspot);
            if (h != null && h.encounter != null && memory.active.firstArrival < 0) finish("UNKNOWN", "LOCAL_CONTACT_RESUMED");
            else if (memory.active.allDead()) finish(memory.active.completedOutcome(), "ROSTER_DEAD");
        }
        data.setDirty();
    }
    void contact(ArchitectEntity actor, ServerPlayer player) {
        if (!enabled() || !ObservationCollector.canObserve(actor, player, false) || actor.distanceToSqr(player) > 2.8 * 2.8) return;
        var group = data.convergence().active;
        if (group != null) { group.contact(actor.getUUID(), player.getUUID(), player.blockPosition(), now()); data.setDirty(); }
    }
    MaeveDirector.PawnOrder order(ArchitectEntity actor) {
        if (!enabled() || !ordinary(actor)) return null;
        var g = data.convergence().active;
        if (g == null || !g.owns(actor.getUUID()) || g.dead.contains(actor.getUUID()) || !g.dimension.equals(dimension(actor))) return null;
        return new MaeveDirector.PawnOrder(g.id, g.destination, g.cloudAt + ConvergencePolicy.WARNING, g.cloudAt + ConvergencePolicy.TRAVEL_LIMIT);
    }
    void routeUnavailable(ArchitectEntity actor) {
        if (order(actor) != null) finish("UNKNOWN", "LOCAL_ROUTE_UNAVAILABLE");
    }
    boolean allowNaturalSpawn(ServerLevel level, BlockPos position) {
        if (!enabled()) return true;
        var memory = data.convergence();
        int players = (int) server.getPlayerList().getPlayers().stream().filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()).count();
        if (!memory.population.canSpawn(players)) return false;
        var h = memory.at(level.dimension().location().toString(), position);
        if (h != null) { h.advance(now()); if (h.avoid) return false; }
        var group = memory.active;
        // Diverted areas cannot immediately replace the pawns sent elsewhere.
        return group == null || !group.dimension.equals(level.dimension().location().toString())
                || group.donors.values().stream().noneMatch(p -> p.distSqr(position) <= 96 * 96);
    }
    void tick() {
        if (!enabled()) { clear("LIFECYCLE_DISABLED"); return; }
        var memory = data.convergence();
        for (var h : memory.hotspots.values()) h.advance(now());
        if (!memory.hotspots.isEmpty()) data.setDirty();
        if (memory.active != null) { tickGroup(); return; }
        avoidRegions();
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) { decision = "ARCHITECT_SPAWNING_DISABLED"; return; }
        for (Hotspot h : memory.hotspots.values().stream().sorted(Comparator.comparingDouble((Hotspot p) -> p.weight(now())).reversed().thenComparing(p -> p.id)).toList()) {
            if (!h.eligibility(now()).equals("ELIGIBLE")) continue;
            var options = ConvergencePolicy.scores(h.avoid, h.wipes, h.weight(now()));
            if (!StrategySelector.best(options).action().equals("CONVERGE")) continue;
            List<ArchitectEntity> donors = donors(h);
            if (donors.size() < ConvergencePolicy.groupSize(h.weight(now()))) { decision = "WAITING_FOR_EXISTING_PAWNS hotspot=" + h.id; continue; }
            var positions = new LinkedHashMap<UUID, BlockPos>(); donors.forEach(a -> positions.put(a.getUUID(), a.blockPosition()));
            var group = new ConvergenceGroup(UUID.randomUUID(), h, positions, now());
            if (!attention.siege(group, donors, () -> finish("UNKNOWN", "ATTENTION_EVICTED"))) { decision = "WAITING_FOR_SIEGE_SLOT hotspot=" + h.id; continue; }
            memory.active = group;
            for (var actor : donors) {
                actor.getPersistentData().putBoolean(EVER_DISPATCHED, true); actor.getPersistentData().putUUID(DISPATCH, group.id);
                actor.startPawnConvergence(); actor.recordDecision("MAEVE_PAWN_DISPATCH", null, "dispatch=" + group.id + " hotspot=" + h.id + " destination=" + h.anchor);
            }
            ConvergencePresentation.begin(server, memory, group); decision = "CONVERGE hotspot=" + h.id + " dispatch=" + group.id;
            FrozenDawn.LOGGER.info("[MACS Pawn Convergence] {} weight={} roster={}", decision, h.weight(now()), positions.keySet());
            data.setDirty(); break;
        }
    }
    private List<ArchitectEntity> donors(Hotspot h) {
        var level = level(h.dimension); if (level == null || !level.hasChunkAt(h.anchor)) return List.of();
        var actors = new ArrayList<ArchitectEntity>();
        for (var entry : data.convergence().population.pawns.entrySet()) {
            if (!entry.getValue().dimension().equals(h.dimension)) continue;
            var actor = actor(h.dimension, entry.getKey());
            if (actor == null || !ordinary(actor) || !actor.isAlive() || actor.isNoAi() || assigned(actor) || actor.isMaeveDisengaging()
                    || !actor.canBeginMaeveReconnaissance() || actor.getHealth() < actor.getMaxHealth() * .6
                    || actor.isInWaterOrBubble() || actor.isOnFire() || actor.isTowerEncounter()
                    || missions.packet(actor) != null || CommitmentCoordinator.directive(data, actor) != null) continue;
            double distance = h.anchor.distSqr(actor.blockPosition());
            if (distance < 32 * 32 || distance > 128 * 128) continue;
            if (!loadedBetween(level, actor.blockPosition(), h.anchor)) continue;
            actors.add(actor);
        }
        actors.sort(Comparator.comparingDouble((ArchitectEntity a) -> a.blockPosition().distSqr(h.anchor)).thenComparing(ArchitectEntity::getUUID));
        return actors.stream().limit(ConvergencePolicy.groupSize(h.weight(now()))).toList();
    }
    private void tickGroup() {
        var memory = data.convergence(); var g = memory.active; g.evaluatedAt = now();
        var h = memory.hotspots.get(g.hotspot);
        if (h == null) { finish("UNKNOWN", "HOTSPOT_UNAVAILABLE"); return; }
        if (h.encounter != null && g.firstArrival < 0) { finish("UNKNOWN", "LOCAL_CONTACT_RESUMED"); return; }
        for (UUID id : g.donors.keySet()) {
            if (g.dead.contains(id)) continue;
            var pawn = actor(g.dimension, id);
            if (pawn == null || pawn.isRemoved() || !ordinary(pawn) || pawn.isNoAi()) { finish("UNKNOWN", "PAWN_UNLOADED_OR_UNAVAILABLE"); return; }
            if (!pawn.isAlive()) { finish("UNKNOWN", "UNCONFIRMED_DEATH"); return; }
            if (!g.warningComplete(now()) && pawn.blockPosition().distSqr(g.destination) <= ConvergencePolicy.RADIUS * ConvergencePolicy.RADIUS) {
                finish("UNKNOWN", "WARNING_REGION_ENTERED_EARLY"); return;
            }
            if (g.firstArrival < 0 && g.warningComplete(now()) && pawn.blockPosition().distSqr(g.destination) <= ConvergencePolicy.RADIUS * ConvergencePolicy.RADIUS) {
                g.firstArrival = now(); pawn.recordDecision("MAEVE_PAWN_FIRST_ARRIVAL", null, "warningTicks=" + (now() - g.cloudAt));
            }
        }
        if (g.firstArrival < 0) {
            ConvergencePresentation.refreshMessage(server, memory, g, now());
            ConvergencePresentation.cloud(level(g.dimension), g, now());
        }
        long limit = g.firstArrival < 0 ? g.cloudAt + ConvergencePolicy.TRAVEL_LIMIT : g.firstArrival + ConvergencePolicy.ENGAGEMENT_LIMIT;
        if (now() >= limit) finish(g.contactAt >= 0 ? "SUCCESS" : "UNKNOWN", g.firstArrival < 0 ? "TRAVEL_TIMEOUT" : "ENGAGEMENT_ENDED");
        data.setDirty();
    }
    private void avoidRegions() {
        for (var entry : data.convergence().population.pawns.entrySet()) {
            var a = actor(entry.getValue().dimension(), entry.getKey());
            if (a == null || !a.isAlive() || !ordinary(a) || a.isNoAi() || a.isMaeveDisengaging() || !a.canBeginMaeveReconnaissance()
                    || missions.packet(a) != null || CommitmentCoordinator.directive(data, a) != null) continue;
            var h = data.convergence().at(dimension(a), a.blockPosition());
            if (h != null && h.avoid) {
                UUID player = a.getTarget() instanceof ServerPlayer p ? p.getUUID() : a.getUUID();
                attention.depart(a, player, h.anchor, "PAWN_AVOID");
                a.recordDecision("MAEVE_PAWN_AVOID", null, "hotspot=" + h.id + " wipes=" + h.wipes + " weight=" + h.weight(now()));
            }
        }
    }
    private void finish(String outcome, String reason) {
        var memory = data.convergence(); if (memory == null || memory.active == null) return;
        var g = memory.active; memory.finish(outcome, reason, now()); attention.releaseSiege(g.id);
        for (UUID id : g.donors.keySet()) {
            var pawn = actor(g.dimension, id); if (pawn == null) continue;
            pawn.getPersistentData().remove(DISPATCH); pawn.clearPawnConvergence();
            if (pawn.isAlive() && ordinary(pawn)) {
                UUID player = pawn.getTarget() instanceof ServerPlayer p ? p.getUUID() : pawn.getUUID();
                attention.depart(pawn, player, g.destination, "PAWN_CONVERGENCE_" + reason);
            }
        }
        decision = outcome + ":" + reason; data.setDirty();
        FrozenDawn.LOGGER.info("[MACS Pawn Convergence] dispatch={} outcome={} reason={} dead={} reached={}", g.id, outcome, reason, g.dead.size(), g.contactAt);
    }
    void clear(String reason) { finish("UNKNOWN", reason); }
    List<BlockPos> avoided(ArchitectEntity actor) {
        if (!enabled() || !ordinary(actor)) return List.of();
        return data.convergence().hotspots.values().stream().filter(h -> h.avoid && h.weight(now()) >= ConvergencePolicy.ACTIVATION_WEIGHT
                && h.dimension.equals(dimension(actor)) && h.anchor.distSqr(actor.blockPosition()) <= 48 * 48).map(h -> h.anchor).toList();
    }
    List<String> diagnostics() { return ConvergenceDiagnostics.format(data.convergence(), now(), decision); }
    private ArchitectEntity actor(String dim, UUID id) { var l = level(dim); return l != null && l.getEntity(id) instanceof ArchitectEntity a ? a : null; }
    private ServerLevel level(String dim) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim))); }
    private static String dimension(ArchitectEntity a) { return a.level().dimension().location().toString(); }
    private static boolean loadedBetween(ServerLevel l, BlockPos a, BlockPos b) {
        for (int x = Math.min(a.getX(), b.getX()) >> 4; x <= Math.max(a.getX(), b.getX()) >> 4; x++)
            for (int z = Math.min(a.getZ(), b.getZ()) >> 4; z <= Math.max(a.getZ(), b.getZ()) >> 4; z++) if (!l.hasChunk(x, z)) return false;
        return true;
    }
}
