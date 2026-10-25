package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * §9.4a: one ordinary Architect sent with thermal sight to where Maeve suspects a player lives, when she does not
 * yet know of heat there. Reconnaissance only: no counter-family slot, no focus slot, no ordinary evidence.
 * What it sees is uploaded at the moment it looks, so killing it afterwards undoes nothing; later raids read
 * the HEAT_SOURCE and shelter beliefs as memory (Lagged), never the live world.
 */
final class PyroCoordinator {
    static final String TAG = "macsPyro";
    static final String ADVANCEMENT = "covered_in_mud";
    private final MinecraftServer server;
    private final MaeveSavedData data;
    private String decision = "NO_PYRO";

    PyroCoordinator(MinecraftServer server, MaeveSavedData data) { this.server = server; this.data = data; }
    private long now() { return server.overworld().getGameTime(); }
    static boolean pyro(ArchitectEntity actor) { return actor.getPersistentData().getBoolean(TAG); }
    private boolean enabled() {
        return data.pyro() != null && data.store() != null && data.lifecycle().equals("ACTIVE")
                && !ConvergenceLifecycle.isArchitectExistencePermanentlyEnded(server);
    }

    boolean designate(ArchitectEntity actor, ServerPlayer subject) {
        String refused = refusal(actor, subject);
        if (refused != null) { decision = refused + " player=" + subject.getUUID(); return false; }
        var memory = data.pyro(); long now = now();
        if (memory.expire(now)) data.setDirty();
        String dimension = actor.level().dimension().location().toString();
        var world = data.store().world(subject.getUUID());
        var home = world == null ? null : world.center(dimension);
        boolean warm = home != null && world.snapshot(now).stream().anyMatch(p -> p.label().equals("HEAT_SOURCE") && p.dimension().equals(dimension)
                && p.confidence() >= PyroPolicy.KNOWN_HEAT && p.position().distSqr(home) <= PyroPolicy.HOME_RADIUS * PyroPolicy.HOME_RADIUS);
        String gate = PyroPolicy.gate(data.lifecycle(), memory.active != null, memory.lastEnded, now, home != null, warm);
        if (!gate.equals("ELIGIBLE")) { decision = gate + " player=" + subject.getUUID(); return false; }
        memory.begin(new PyroMemory.Claim(actor.getUUID(), subject.getUUID(), dimension, home, now));
        actor.getPersistentData().putBoolean(TAG, true);
        decision = "DESIGNATED pyro=" + actor.getUUID() + " player=" + subject.getUUID() + " home=" + home;
        FrozenDawn.LOGGER.info("[MACS Pyro] {}", decision); data.setDirty();
        return true;
    }

    /**
     * Test egg: skips the gate and cooldown, replaces any active claim (that Pyro departs) and, when Maeve
     * suspects no home yet, seeds one where the subject stands so the Pyro has somewhere to watch.
     */
    boolean designateForTest(ArchitectEntity actor, ServerPlayer subject) {
        if (!enabled() || pyro(actor) || ScribeCoordinator.scribe(actor) || subject.level() != actor.level()) return false;
        var memory = data.pyro(); long now = now();
        String dimension = actor.level().dimension().location().toString();
        var world = data.store().world(subject.getUUID());
        if (world == null || world.center(dimension) == null) {
            PyroLab.seedHome(subject, subject.blockPosition());
            world = data.store().world(subject.getUUID());
        }
        var home = world == null ? null : world.center(dimension);
        if (home == null) return false;
        if (memory.active != null) {
            FrozenDawn.LOGGER.info("[MACS Pyro] ENDED pyro={} reason=TEST_EGG_REPLACED", memory.active.pyro());
            memory.end(now);
        }
        memory.begin(new PyroMemory.Claim(actor.getUUID(), subject.getUUID(), dimension, home, now));
        actor.getPersistentData().putBoolean(TAG, true);
        decision = "DESIGNATED pyro=" + actor.getUUID() + " player=" + subject.getUUID() + " home=" + home + " route=TEST_EGG";
        FrozenDawn.LOGGER.info("[MACS Pyro] {}", decision); data.setDirty();
        return true;
    }

    /** Why this actor or subject cannot take part at all, before the §9.4a gate is asked; null when both can. */
    private String refusal(ArchitectEntity actor, ServerPlayer subject) {
        if (!enabled()) return "NOT_ACTIVE lifecycle=" + data.lifecycle();
        if (!ConvergenceCoordinator.ordinary(actor) || actor.isNoAi() || pyro(actor) || ScribeCoordinator.scribe(actor)
                || ConvergenceCoordinator.assigned(actor)) return "ACTOR_NOT_ORDINARY";
        if (subject.isCreative() || subject.isSpectator()) return "SUBJECT_NOT_SURVIVAL";
        if (subject.level() != actor.level()) return "SUBJECT_OTHER_DIMENSION";
        return null;
    }

    MaeveDirector.PyroOrder order(ArchitectEntity actor) {
        if (!enabled()) return null;
        var memory = data.pyro();
        if (memory.expire(now())) { decision = "LIFETIME_EXPIRED"; data.setDirty(); }
        var claim = memory.active;
        return claim == null || !claim.pyro().equals(actor.getUUID()) ? null
                : new MaeveDirector.PyroOrder(claim.subject(), claim.dimension(), claim.home(), claim.expiresAt());
    }

    /**
     * One look from where the Pyro stands, uploaded at once. {@code last} closes the watch: a home read completely
     * with no heat seen anywhere earns the subject Covered in Mud. Only a look with the home in view is a read of it;
     * a Pyro stalled behind a hill sees nothing because it sees nothing at all.
     */
    MaeveDirector.PyroScan observe(ArchitectEntity actor, boolean last) {
        var order = order(actor);
        if (order == null || !(actor.level() instanceof ServerLevel level) || !order.dimension().equals(level.dimension().location().toString())) return null;
        var memory = data.pyro(); var claim = memory.active; long now = now();
        var world = data.store().world(order.subject());
        var scan = ThermalSight.scan(level, actor, order.home());
        if (world != null) for (var sighting : scan.sightings()) {
            var evidence = new ObservedEvidence(actor.getUUID(), claim.encounter(), order.dimension(), sighting.seen(), now, sighting.action(), true);
            world.event("HEAT_SOURCE", order.dimension(), sighting.seen(), evidence, sighting.weight());
            // Heat under a roof is someone's shelter.
            if (!level.canSeeSky(sighting.inside())) world.shelter(order.dimension(), sighting.inside(), now);
        }
        // Heat seen counts against a cold verdict from any look; only a look with the home in view counts toward one.
        if (scan.complete()) { if (scan.inView()) memory.scans++; memory.sightings += scan.sightings().size(); data.setDirty(); }
        boolean cold = last && memory.scans > 0 && memory.sightings == 0;
        decision = (cold ? "COLD" : "LOOKED") + " pyro=" + actor.getUUID() + " sources=" + scan.sources() + " seen=" + scan.sightings().size()
                + " complete=" + scan.complete() + " inView=" + scan.inView() + " visit=" + memory.sightings + "/" + memory.scans;
        FrozenDawn.LOGGER.info("[MACS Pyro] {} {}", decision, scan.sightings().stream()
                .map(s -> s.kind() + "@" + s.seen().toShortString()).toList());
        if (cold && (level.getPlayerByUUID(order.subject()) instanceof ServerPlayer here ? here
                : server.getPlayerList().getPlayer(order.subject())) instanceof ServerPlayer subject)
            com.frozendawn.event.WorldTickHandler.grantAdvancement(subject, ADVANCEMENT);
        return new MaeveDirector.PyroScan(scan.sources(), scan.sightings().size(), scan.complete(), scan.inView(), cold);
    }

    void ended(ArchitectEntity actor, String reason) {
        var memory = data.pyro();
        if (memory == null || memory.active == null || !memory.active.pyro().equals(actor.getUUID())) return;
        decision = "ENDED pyro=" + actor.getUUID() + " reason=" + reason + " visit=" + memory.sightings + "/" + memory.scans;
        memory.end(now());
        FrozenDawn.LOGGER.info("[MACS Pyro] {}", decision); data.setDirty();
    }

    /** Runtime diagnostics only; the claim itself lives in the erasable store. */
    void clear(String reason) { decision = reason; }

    List<String> diagnostics() {
        var memory = data.pyro();
        if (memory == null) return List.of("PYRO: unavailable (" + data.lifecycle() + ")");
        long now = now(); var claim = memory.active;
        long cooldown = memory.lastEnded < 0 ? 0 : Math.max(0, memory.lastEnded + PyroPolicy.COOLDOWN - now);
        return List.of("PYRO: decision=" + decision + " cooldownRemaining=" + cooldown + " sight=" + PyroPolicy.SIGHT,
                claim == null ? "PYRO active: none" : "PYRO active: " + claim.pyro() + " player=" + claim.subject() + " home=" + claim.home()
                        + " " + claim.dimension() + " seen=" + memory.sightings + "/" + memory.scans + " expiresIn=" + Math.max(0, claim.expiresAt() - now));
    }
}
