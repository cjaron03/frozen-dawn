package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded, transient dispatch. Packets freeze historical knowledge at admission. */
final class MissionPlanner {
    static final long MIN_EVIDENCE_AGE = 600, TIMEOUT = 360, RETRY_DELAY = 1200;
    private record Mission(MaeveDirector.MissionPacket packet, String report, long sampledAt) { }
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private final AttentionCoordinator attention;
    private final Map<UUID, Mission> active = new LinkedHashMap<>();
    private final Map<UUID, Long> retry = new LinkedHashMap<>();
    private final ArrayDeque<MaeveDirector.MissionSnapshot> history = new ArrayDeque<>();

    MissionPlanner(MinecraftServer server, MaeveSavedData data, AttentionCoordinator attention) {
        this.server = server; this.data = data; this.attention = attention;
    }
    private long now() { return server.overworld().getGameTime(); }

    MaeveDirector.MissionPacket packet(ArchitectEntity actor) {
        var mission = active.get(actor.getUUID());
        return actor.isMasterArchitectVisual() || mission == null ? null : mission.packet();
    }

    boolean request(ArchitectEntity actor, ServerPlayer player) {
        var store = data.store(); long now = now();
        if (store == null || !CommitmentCoordinator.eligible(actor, player) || actor.isMaeveDisengaging()
                || active.containsKey(actor.getUUID()) || active.size() >= 5
                || now < retry.getOrDefault(actor.getUUID(), 0L) || CommitmentCoordinator.directive(data, actor) != null
                || active.values().stream().anyMatch(m -> m.packet().player().equals(player.getUUID()))) return false;
        var world = store.world(player.getUUID()); if (world == null) return false;
        String dimension = actor.level().dimension().location().toString();
        var target = world.snapshot(now).stream().filter(p -> p.label().equals("ACCESS_POINT") && p.dimension().equals(dimension)
                        && p.inside() != null && !p.provenance().isEmpty() && now - p.observedAt() >= MIN_EVIDENCE_AGE
                        && p.confidence() > .05 && p.confidence() < .75 && actor.blockPosition().distSqr(p.position()) <= 24 * 24)
                .min(Comparator.comparingDouble(MaeveDirector.WorldPointSnapshot::confidence)
                        .thenComparingLong(MaeveDirector.WorldPointSnapshot::observedAt)
                        .thenComparing(p -> p.position().asLong())).orElse(null);
        if (target == null) return false;
        var hints = CommitmentCoordinator.hints(data, actor, player);
        var focus = attention.snapshot();
        boolean threatened = actor.distanceToSqr(player) < 7 * 7 || actor.getLastHurtByMob() != null
                && actor.tickCount - actor.getLastHurtByMobTimestamp() < 100;
        var options = StrategySelector.options(target.confidence(), Math.sqrt(actor.blockPosition().distSqr(target.position())),
                actor.getHealth() / actor.getMaxHealth(), threatened,
                hints.stream().anyMatch(h -> h.confidence() >= .75 && store.commitment(player.getUUID()).ineligible(h.pattern(), now).equals("ELIGIBLE")),
                focus.slots().size(), focus.capacity());
        if (!StrategySelector.best(options).action().equals("SURVEY_ACCESS")) return false;
        var packet = new MaeveDirector.MissionPacket(UUID.randomUUID(), player.getUUID(), actor.getUUID(),
                store.commitment(player.getUUID()).encounter(), dimension, "VERIFY_WITNESSED_ACCESS",
                new MaeveDirector.AccessHint(target.position(), target.inside(), target.state(), target.confidence(), target.provenance().getLast()),
                world.dangers(dimension, actor.blockPosition(), now).stream().limit(4).toList(),
                List.of("PRIMARY: inspect the remembered crossing", "SECONDARY: observe a visible response",
                        "COMBAT: release for local self-defense on damage", "EXTRACTION: preferred; no prolonged engagement"),
                options.stream().map(StrategySelector.Score::describe).toList(), now, now + TIMEOUT);
        if (!attention.reconnaissance(actor, player)) return false;
        active.put(actor.getUUID(), new Mission(packet, "UNREPORTED", -1));
        actor.recordDecision("MAEVE_RECON_ASSIGNED", null, "mission=" + packet.id() + " access=" + target.position()
                + " confidence=" + target.confidence() + " source=" + packet.access().source());
        return true;
    }

    String sample(ArchitectEntity actor) {
        var mission = active.get(actor.getUUID());
        if (mission == null || actor.isMasterArchitectVisual() || !actor.isAlive() || actor.isNoAi() || data.store() == null) return "UNSEEN";
        var packet = mission.packet(); long now = now();
        if (!packet.dimension().equals(actor.level().dimension().location().toString()) || now < packet.issuedAt() || now >= packet.expiresAt()) return "UNSEEN";
        if (actor.level().getPlayerByUUID(packet.player()) instanceof ServerPlayer player) {
            SpatialObservations.presence(data.store(), actor, player, now); data.setDirty();
        }
        if (!mission.report().equals("UNREPORTED")) return mission.report();
        if (mission.sampledAt() >= 0 && now - mission.sampledAt() < 20) return "UNSEEN";
        String report = MissionSensing.inspect(actor, packet.access());
        active.put(actor.getUUID(), new Mission(packet, report.equals("UNSEEN") ? "UNREPORTED" : report, now));
        if (!report.equals("UNSEEN")) {
            var world = data.store().world(packet.player());
            if (world == null) return "UNSEEN";
            world.survey(packet.access(), report, new ObservedEvidence(actor.getUUID(), packet.encounter(), packet.dimension(),
                    packet.access().outside(), now, "RECON_INSPECTED_ACCESS_" + report, true));
            data.setDirty();
            actor.recordDecision("MAEVE_RECON_REPORT", null, "mission=" + packet.id() + " report=" + report + " tick=" + now);
        }
        return report;
    }

    void finish(ArchitectEntity actor, String reason, boolean withdraw) {
        end(actor, reason, withdraw, true);
    }
    void evicted(ArchitectEntity actor) { end(actor, "ATTENTION_EVICTED", false, false); }
    private void end(ArchitectEntity actor, String reason, boolean withdraw, boolean releaseSlot) {
        var mission = active.remove(actor.getUUID()); if (mission == null) return;
        var packet = mission.packet();
        if (releaseSlot) attention.releaseReconnaissance(actor);
        remember(new MaeveDirector.MissionSnapshot(packet, reason, mission.report(), now()));
        if (retry.size() == 256 && !retry.containsKey(actor.getUUID())) retry.remove(retry.keySet().iterator().next());
        retry.put(actor.getUUID(), now() + RETRY_DELAY);
        actor.clearMaeveReconnaissance();
        actor.recordDecision("MAEVE_RECON_FINISHED", null, "mission=" + packet.id() + " reason=" + reason + " report=" + mission.report());
        if (withdraw && actor.isAlive() && !actor.isMasterArchitectVisual()) {
            attention.depart(actor, packet.player(), packet.access().outside(), "RECON_" + reason);
        }
    }

    void tick() {
        retry.entrySet().removeIf(e -> now() >= e.getValue());
        for (var mission : new ArrayList<>(active.values())) {
            var p = mission.packet(); var actor = actor(p);
            if (actor == null) {
                active.remove(p.observer()); attention.releaseReconnaissance(p.observer());
                remember(new MaeveDirector.MissionSnapshot(p, "OWNER_UNLOADED", mission.report(), now()));
            } else if (!actor.isAlive() || actor.isNoAi() || actor.isMasterArchitectVisual() || data.store() == null
                    || data.store().world(p.player()) == null) finish(actor, "OWNER_UNAVAILABLE", false);
            else if (now() < p.issuedAt() || now() >= p.expiresAt()) finish(actor, "TIMED_OUT", true);
        }
    }

    private ArchitectEntity actor(MaeveDirector.MissionPacket packet) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(packet.dimension())));
        return level != null && level.getEntity(packet.observer()) instanceof ArchitectEntity actor ? actor : null;
    }
    private void remember(MaeveDirector.MissionSnapshot snapshot) {
        if (history.size() == 16) history.removeFirst(); history.addLast(snapshot);
    }
    List<MaeveDirector.MissionSnapshot> snapshots(UUID player) {
        var result = new ArrayList<MaeveDirector.MissionSnapshot>();
        for (var mission : active.values()) result.add(new MaeveDirector.MissionSnapshot(mission.packet(), "ACTIVE", mission.report(), now()));
        result.addAll(history);
        return result.stream().filter(s -> player == null || s.packet().player().equals(player)).toList();
    }
    void clear() {
        for (var mission : active.values()) { var actor = actor(mission.packet()); if (actor != null) actor.clearMaeveReconnaissance(); }
        active.clear(); history.clear(); retry.clear();
    }
    static List<String> format(List<MaeveDirector.MissionSnapshot> snapshots) {
        var lines = new ArrayList<String>(); lines.add("MACS reconnaissance | retained missions=" + snapshots.size());
        for (var s : snapshots) {
            var p = s.packet();
            lines.add("  mission=" + p.id() + " observer=" + p.observer() + " player=" + p.player() + " " + s.outcome() + " report=" + s.report());
            lines.add("    packet=" + p.objective() + " dimension=" + p.dimension() + " issued=" + p.issuedAt() + " expires=" + p.expiresAt());
            lines.add("    inherited=" + p.access() + " dangers=" + p.dangers());
            for (String order : p.orders()) lines.add("    " + order);
            for (String option : p.alternatives()) lines.add("    " + option);
            lines.add("    boundary: one historical crossing; no current hidden player position, inventory, health or room classification");
        }
        return List.copyOf(lines);
    }
}
