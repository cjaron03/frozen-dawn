package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Local conditional episodes and observed counter results; no inventory or player-health polling. */
final class LearningCoordinator {
    static final int MAX_WINDOWS = 32;
    private record Episode(UUID player, String dimension, WithdrawalWindow window) { }
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private final Map<UUID, Episode> windows = new LinkedHashMap<>();
    private final Map<UUID, Long> retries = new LinkedHashMap<>();
    private final java.util.List<String> events = new ArrayList<>();
    private long lastTick = Long.MIN_VALUE;

    LearningCoordinator(MinecraftServer server, MaeveSavedData data) { this.server = server; this.data = data; }

    void withdrawal(ArchitectEntity actor, ServerPlayer player) {
        if (data.store() == null || !CommitmentCoordinator.eligible(actor, player)
                || windows.containsKey(actor.getUUID()) || windows.size() >= MAX_WINDOWS
                || actor.distanceToSqr(player) < 1 || actor.distanceToSqr(player) > 16 * 16) return;
        long now = server.overworld().getGameTime();
        if (retries.getOrDefault(actor.getUUID(), -1L) > now) return;
        String dimension = actor.level().dimension().location().toString();
        data.store().observeContact(player.getUUID(), actor.getUUID(), dimension, now);
        windows.put(actor.getUUID(), new Episode(player.getUUID(), dimension, new WithdrawalWindow(actor.position(), player.position(), now)));
        retries.remove(actor.getUUID()); retries.put(actor.getUUID(), now + WithdrawalWindow.DURATION + 100);
        if (retries.size() > 256) retries.remove(retries.keySet().iterator().next());
        data.setDirty();
    }

    void tick() {
        long now = server.overworld().getGameTime();
        if (data.store() == null || now % 10 != 0 || now == lastTick) return;
        lastTick = now;
        for (var entry : new ArrayList<>(windows.entrySet())) {
            var episode = entry.getValue();
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(episode.dimension())));
            if (level == null || !(level.getEntity(entry.getKey()) instanceof ArchitectEntity actor)
                    || !(level.getEntity(episode.player()) instanceof ServerPlayer player)
                    || !CommitmentCoordinator.eligible(actor, player)) { windows.remove(entry.getKey()); event(episode.player(), entry.getKey(), "UNKNOWN: local sight or executor unavailable", now); continue; }
            var policy = data.store().commitmentFor(actor.getUUID(), now);
            var held = policy == null ? null : policy.active(now);
            boolean withdrawingHold = held != null && BeliefStore.PURSUIT.equals(held.pattern());
            if (!actor.isMaeveDisengaging() && !withdrawingHold && actor.getTarget() != null
                    && (actor.getBrainAction() == ArchitectEntity.ACTION_APPROACH || actor.getBrainAction() == ArchitectEntity.ACTION_ATTACK_MELEE)) {
                windows.remove(entry.getKey()); event(episode.player(), entry.getKey(), "UNKNOWN: withdrawal interrupted by pursuit", now); continue;
            }
            var result = episode.window().sample(actor.position(), player.position(), now, true);
            if (result == WithdrawalWindow.Result.WAITING) continue;
            windows.remove(entry.getKey());
            event(episode.player(), entry.getKey(), result.toString(), now);
            if (result == WithdrawalWindow.Result.UNKNOWN) continue;
            var window = episode.window();
            String action = (result == WithdrawalWindow.Result.FOLLOWED ? "WITHDRAWAL_FOLLOWED" : "WITHDRAWAL_NOT_FOLLOWED")
                    + " t0=" + window.started + " player0=" + net.minecraft.core.BlockPos.containing(window.playerStart).toShortString()
                    + " actor0=" + net.minecraft.core.BlockPos.containing(window.actorStart).toShortString()
                    + " actor1=" + actor.blockPosition().toShortString();
            data.store().record(player.getUUID(), actor.getUUID(), episode.dimension(), player.blockPosition(), now,
                    BeliefStore.PURSUIT, result == WithdrawalWindow.Result.FOLLOWED, action);
            actor.recordDecision("MAEVE_CONDITIONAL_OBSERVATION", null, action);
            data.setDirty();
        }
        if (now % 20 == 0) for (var state : data.store().commitments()) {
            var selected = state.active(now);
            if (selected == null) continue;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(selected.evidence().dimension())));
            if (level == null || !(level.getEntity(selected.observer()) instanceof ArchitectEntity actor)) state.finish("OBSERVER_UNAVAILABLE");
            else if (!actor.isAlive()) state.finish("OWNER_KILLED");
            else if (actor.isNoAi() || actor.isRemoved()) state.finish("OBSERVER_UNAVAILABLE");
        }
        data.setDirty();
    }

    void incoming(ArchitectEntity actor, net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (data.store() == null || !(damage > 0) || !Float.isFinite(damage)) return;
        long now = server.overworld().getGameTime();
        var state = data.store().commitmentFor(actor.getUUID(), now);
        if (state == null) return;
        var selected = state.active(now);
        if (source.getEntity() instanceof ServerPlayer player && selected.player().equals(player.getUUID())
                && ObservationCollector.canObserve(actor, player, true)) {
            damage(actor, player, damage, false); return;
        }
        // Hidden damage, hazards and another player's interference cannot be charged to this subject's counter response.
        state.performance().finish("UNOBSERVED_DAMAGE");
        data.setDirty();
    }

    void damage(ArchitectEntity actor, ServerPlayer player, float damage, boolean outgoing) {
        if (data.store() == null || !ObservationCollector.canObserve(actor, player, !outgoing, outgoing)) return;
        long now = server.overworld().getGameTime();
        var policy = data.store().commitment(player.getUUID());
        if (policy == null) return;
        var selected = policy.active(now);
        if (selected == null || selected.arrivedAt() < 0 || actor.blockPosition().distSqr(selected.position()) > 9) return;
        policy.performance().damage(actor.getUUID(), player.getUUID(), actor.level().dimension().location().toString(),
                actor.blockPosition(), now, damage, outgoing);
        data.setDirty();
    }
    private void event(UUID player, UUID actor, String outcome, long now) {
        events.add("CONDITIONAL WINDOW player=" + player + " observer=" + actor + " time=" + now + " outcome=" + outcome);
        if (events.size() > 8) events.removeFirst();
    }
    java.util.List<String> diagnostics(UUID player) {
        var lines = new ArrayList<String>();
        windows.forEach((actor, e) -> { if (player == null || e.player().equals(player))
            lines.add("CONDITIONAL WINDOW player=" + e.player() + " observer=" + actor + " sampling age="
                    + (server.overworld().getGameTime() - e.window().started) + "/" + WithdrawalWindow.DURATION); });
        events.stream().filter(e -> player == null || e.contains("player=" + player)).forEach(lines::add);
        return java.util.List.copyOf(lines);
    }
    void clear() { windows.clear(); retries.clear(); events.clear(); lastTick = Long.MIN_VALUE; }
}
