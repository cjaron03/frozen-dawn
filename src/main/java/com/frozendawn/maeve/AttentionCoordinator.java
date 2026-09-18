package com.frozendawn.maeve;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded references to loaded executors; attention stores no additional player knowledge. */
final class AttentionCoordinator {
    private record Executor(UUID actor, UUID player, String dimension, BlockPos observed, long lastSeen) { }
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private final AttentionManager manager = new AttentionManager();
    private final Map<AttentionManager.Key, Map<UUID, Executor>> executors = new LinkedHashMap<>();
    private final Map<UUID, Executor> departing = new LinkedHashMap<>();
    private MissionPlanner missions;

    AttentionCoordinator(MinecraftServer server, MaeveSavedData data) { this.server = server; this.data = data; }
    void bind(MissionPlanner missions) { this.missions = missions; }
    private long now() { return server.overworld().getGameTime(); }
    private void resize() { manager.resize(AttentionManager.capacity(ApocalypseState.get(server).getPresetName()), now(), this::evict); }

    void observe(ArchitectEntity actor, ServerPlayer player) {
        if (data.store() == null || !ObservationCollector.canObserve(actor, player, false)) return;
        resize();
        if (CommitmentCoordinator.eligible(actor, player)
                && !actor.isMaeveDisengaging() && actor.getCurrentAction() == ArchitectEntity.ACTION_OBSERVE
                && (missions == null || missions.packet(actor) == null)
                && CommitmentCoordinator.directive(data, actor) == null) {
            var key = new AttentionManager.Key(AttentionManager.Kind.PASSIVE_TRACKING, player.getUUID());
            for (var previous : new ArrayList<>(executors.keySet())) {
                if (previous.kind() != AttentionManager.Kind.PASSIVE_TRACKING || previous.equals(key)) continue;
                var members = executors.get(previous); members.remove(actor.getUUID());
                if (members.isEmpty()) { executors.remove(previous); manager.release(previous, now()); }
            }
            admit(key, actor, player);
        }
    }

    private boolean admit(AttentionManager.Key key, ArchitectEntity actor, ServerPlayer player) {
        var members = executors.get(key);
        if (members != null && !members.containsKey(actor.getUUID()) && members.size() >= 8) return false;
        if (!manager.request(key, now(), this::evict).admitted()) return false;
        executors.computeIfAbsent(key, unused -> new LinkedHashMap<>()).put(actor.getUUID(),
                new Executor(actor.getUUID(), player.getUUID(), actor.level().dimension().location().toString(), player.blockPosition().immutable(), now()));
        return true;
    }

    boolean commitment(ArchitectEntity actor, ServerPlayer player) {
        if (!CommitmentCoordinator.eligible(actor, player)) return false;
        resize();
        var tracking = new AttentionManager.Key(AttentionManager.Kind.PASSIVE_TRACKING, player.getUUID());
        var commitment = new AttentionManager.Key(AttentionManager.Kind.ACTIVE_COMMITMENT, actor.getUUID());
        var members = executors.get(tracking);
        // A sole tracker taking a position is still one activity. Shared tracking keeps its other executors.
        if (members != null && members.size() == 1 && members.containsKey(actor.getUUID()) && manager.replace(tracking, commitment, now())) {
            executors.remove(tracking); executors.put(commitment, members); return true;
        }
        if (!admit(commitment, actor, player)) return false;
        if (members != null) members.remove(actor.getUUID());
        return true;
    }

    void releaseCommitment(ArchitectEntity actor) {
        var key = new AttentionManager.Key(AttentionManager.Kind.ACTIVE_COMMITMENT, actor.getUUID());
        executors.remove(key); manager.release(key, now());
    }

    boolean reconnaissance(ArchitectEntity actor, ServerPlayer player) {
        resize();
        if (actor.isMaeveDisengaging()) return false;
        var tracking = new AttentionManager.Key(AttentionManager.Kind.PASSIVE_TRACKING, player.getUUID());
        var mission = new AttentionManager.Key(AttentionManager.Kind.RECONNAISSANCE, actor.getUUID());
        var members = executors.get(tracking);
        if (members != null && members.size() == 1 && members.containsKey(actor.getUUID()) && manager.replace(tracking, mission, now())) {
            executors.remove(tracking); executors.put(mission, members); return true;
        }
        if (!admit(mission, actor, player)) return false;
        if (members != null) members.remove(actor.getUUID());
        return true;
    }

    void releaseReconnaissance(ArchitectEntity actor) { releaseReconnaissance(actor.getUUID()); }
    void releaseReconnaissance(UUID actor) {
        var key = new AttentionManager.Key(AttentionManager.Kind.RECONNAISSANCE, actor);
        executors.remove(key); manager.release(key, now());
    }

    void tick() {
        if (data.store() == null) { clear(); return; }
        departing.values().removeIf(ref -> { var actor = actor(ref); return actor == null || !actor.isMaeveDisengaging(); });
        for (var entry : new ArrayList<>(executors.entrySet())) {
            var key = entry.getKey();
            entry.getValue().values().removeIf(ref -> {
                ArchitectEntity actor = actor(ref);
                if (actor == null || !actor.isAlive() || actor.isNoAi() || actor.isMasterArchitectVisual()) return true;
                return switch (key.kind()) {
                    case ACTIVE_COMMITMENT -> CommitmentCoordinator.directive(data, actor) == null;
                    case PASSIVE_TRACKING -> actor.getCurrentAction() != ArchitectEntity.ACTION_OBSERVE
                            || actor.isMaeveDisengaging() || now() - ref.lastSeen() > 100;
                    case RECONNAISSANCE -> missions == null || missions.packet(actor) == null;
                    default -> true; // Siege execution belongs to a later slice.
                };
            });
            if (entry.getValue().isEmpty()) { executors.remove(key); manager.release(key, now()); }
        }
        resize();
    }

    private void evict(AttentionManager.Key key) {
        var members = executors.remove(key);
        if (members == null) return;
        for (var ref : members.values()) {
            ArchitectEntity actor = actor(ref);
            if (actor == null || !actor.isAlive() || actor.isMasterArchitectVisual()) continue;
            if (key.kind() == AttentionManager.Kind.ACTIVE_COMMITMENT) CommitmentCoordinator.release(data, actor, "ATTENTION_EVICTED");
            if (key.kind() == AttentionManager.Kind.RECONNAISSANCE && missions != null) missions.evicted(actor);
            depart(actor, ref.player(), ref.observed(), key.kind().name());
        }
    }

    void depart(ArchitectEntity actor, UUID player, BlockPos observed, String reason) {
        actor.beginMaeveDisengagement(player, observed, reason);
        if (departing.size() >= 256 && !departing.containsKey(actor.getUUID())) {
            var oldest = departing.remove(departing.keySet().iterator().next());
            var previous = actor(oldest); if (previous != null) previous.clearMaeveAttention();
        }
        departing.put(actor.getUUID(), new Executor(actor.getUUID(), player,
                actor.level().dimension().location().toString(), observed.immutable(), now()));
    }

    private ArchitectEntity actor(Executor ref) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ref.dimension())));
        return level != null && level.getEntity(ref.actor()) instanceof ArchitectEntity actor ? actor : null;
    }

    void clear() {
        for (var members : executors.values()) for (var ref : members.values()) {
            var actor = actor(ref); if (actor != null) actor.clearMaeveAttention();
        }
        for (var ref : departing.values()) { var actor = actor(ref); if (actor != null) actor.clearMaeveAttention(); }
        departing.clear(); executors.clear(); manager.clear();
    }

    MaeveDirector.AttentionSnapshot snapshot() {
        return new MaeveDirector.AttentionSnapshot(AttentionManager.capacity(ApocalypseState.get(server).getPresetName()), manager.slots().stream().map(s ->
                new MaeveDirector.FocusSnapshot(s.key().kind().name(), s.key().subject(), s.admittedAt(),
                        Math.max(0, AttentionManager.MIN_DWELL - (now() - s.admittedAt())),
                        executors.getOrDefault(s.key(), Map.of()).keySet().stream().toList(),
                        executors.getOrDefault(s.key(), Map.of()).values().stream().map(ref ->
                                "observer=" + ref.actor() + " player=" + ref.player() + " dimension=" + ref.dimension()
                                        + " seen=" + ref.observed().toShortString() + " tick=" + ref.lastSeen()).toList())).toList(),
                manager.events().stream().map(e -> "tick=" + e.time() + " " + e.action() + " " + e.concern()
                        + (e.replacement() == null ? "" : " -> " + e.replacement())).toList());
    }

    static List<String> format(MaeveDirector.AttentionSnapshot view) {
        var lines = new ArrayList<String>();
        lines.add("ATTENTION " + view.slots().size() + "/" + view.capacity() + " shared focus slots | minimum dwell=100 ticks");
        for (var slot : view.slots()) {
            lines.add("  " + slot.kind() + " subject=" + slot.subject()
                    + " admitted=" + slot.admittedAt() + " dwellRemaining=" + slot.dwellRemaining() + " executors=" + slot.executors());
            for (String report : slot.reports()) lines.add("    " + report);
        }
        for (var event : view.events()) lines.add("  " + event);
        return List.copyOf(lines);
    }
}
