package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * §9.4b: Maeve's notes on one player, carried by one ordinary Architect. Reconnaissance and legibility only:
 * no counter-family slot, no focus slot, and the Scribe itself reports no evidence.
 * The watch target comes from the world model alone (Causal); death reads the store once and freezes it.
 */
final class ScribeCoordinator {
    static final String TAG = "macsScribe";
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private String decision = "NO_SCRIBE";

    ScribeCoordinator(MinecraftServer server, MaeveSavedData data) { this.server = server; this.data = data; }
    private long now() { return server.overworld().getGameTime(); }
    static boolean scribe(ArchitectEntity actor) { return actor.getPersistentData().getBoolean(TAG); }
    private boolean enabled() {
        return data.scribe() != null && data.store() != null && data.lifecycle().equals("ACTIVE")
                && !ConvergenceLifecycle.isArchitectExistencePermanentlyEnded(server);
    }

    boolean designate(ArchitectEntity actor, ServerPlayer subject) {
        if (!enabled() || !ConvergenceCoordinator.ordinary(actor) || actor.isNoAi() || scribe(actor) || ConvergenceCoordinator.assigned(actor)
                || subject.isCreative() || subject.isSpectator() || subject.level() != actor.level()) return false;
        var memory = data.scribe(); long now = now();
        if (memory.expire(now)) data.setDirty();
        String gate = ScribePolicy.gate(data.lifecycle(), data.store().snapshot(subject.getUUID(), now), memory.active != null, memory.lastEnded, now);
        if (!gate.equals("ELIGIBLE")) { decision = gate + " player=" + subject.getUUID(); return false; }
        String dimension = actor.level().dimension().location().toString();
        var world = data.store().world(subject.getUUID());
        BlockPos shelter = world == null ? null : world.center(dimension);
        var opening = world == null ? null : world.snapshot(now).stream()
                .filter(p -> p.label().equals("ACCESS_POINT") && p.dimension().equals(dimension) && p.state().equals("OPEN") && p.confidence() > .05)
                .filter(p -> shelter == null || p.position().distSqr(shelter) <= 32 * 32)
                .max(Comparator.comparingDouble(MaeveDirector.WorldPointSnapshot::confidence).thenComparing(p -> -p.position().asLong())).orElse(null);
        BlockPos watch = opening != null ? opening.position() : shelter;
        String label = opening != null ? "OPENING" : shelter != null ? "SHELTER" : "ROUTE";
        memory.active = new ScribeMemory.Claim(actor.getUUID(), subject.getUUID(), dimension, watch, label, now);
        actor.getPersistentData().putBoolean(TAG, true);
        decision = "DESIGNATED scribe=" + actor.getUUID() + " player=" + subject.getUUID() + " watch=" + label + "@" + watch;
        FrozenDawn.LOGGER.info("[MACS Scribe] {}", decision); data.setDirty();
        return true;
    }

    MaeveDirector.ScribeOrder order(ArchitectEntity actor) {
        if (!enabled()) return null;
        var memory = data.scribe();
        if (memory.expire(now())) { decision = "LIFETIME_EXPIRED"; data.setDirty(); }
        var claim = memory.active;
        return claim == null || !claim.scribe().equals(actor.getUUID()) ? null
                : new MaeveDirector.ScribeOrder(claim.subject(), claim.dimension(), claim.watch(), claim.watchLabel(), claim.expiresAt());
    }

    /** The store is read here once. The returned copy never consults it again. */
    MaeveDirector.ScribeNotes notes(ArchitectEntity actor) {
        var order = order(actor);
        if (order == null) return null;
        long now = now(); var store = data.store(); var world = store.world(order.subject());
        BlockPos center = world == null ? null : world.center(order.dimension());
        return new MaeveDirector.ScribeNotes(order.subject(), order.dimension(), center == null ? actor.blockPosition() : center,
                ScribeRecordWriter.notes(store.snapshot(order.subject(), now)),
                world == null ? List.of() : ScribeRecordWriter.marks(world.snapshot(now), order.dimension()));
    }

    void ended(ArchitectEntity actor, String reason) {
        var memory = data.scribe();
        if (memory == null || memory.active == null || !memory.active.scribe().equals(actor.getUUID())) return;
        memory.end(now()); decision = "ENDED scribe=" + actor.getUUID() + " reason=" + reason;
        FrozenDawn.LOGGER.info("[MACS Scribe] {}", decision); data.setDirty();
    }

    List<String> diagnostics() {
        var memory = data.scribe();
        if (memory == null) return List.of("SCRIBE: unavailable (" + data.lifecycle() + ")");
        long now = now(); var claim = memory.active;
        long cooldown = memory.lastEnded < 0 ? 0 : Math.max(0, memory.lastEnded + ScribePolicy.COOLDOWN - now);
        return List.of("SCRIBE: decision=" + decision + " cooldownRemaining=" + cooldown + " gate=" + ScribePolicy.GATE_BELIEFS + "x>=" + ScribePolicy.CONFIDENT,
                claim == null ? "SCRIBE active: none" : "SCRIBE active: " + claim.scribe() + " player=" + claim.subject() + " watch=" + claim.watchLabel()
                        + "@" + claim.watch() + " " + claim.dimension() + " expiresIn=" + Math.max(0, claim.expiresAt() - now));
    }
}
