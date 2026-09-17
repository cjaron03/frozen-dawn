package com.frozendawn.debug.architect;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Multiple real villagers, ordinary target selection, and explicit scripted removals/moves. */
final class ArchitectLabEncounter {
    private final ArchitectLabRun run;
    private final List<Villager> villagers = new ArrayList<>();
    private final List<Vec3> starts = new ArrayList<>();
    private final Map<UUID, Float> previousHealth = new HashMap<>();
    private final Set<UUID> removed = new HashSet<>();
    private final List<Map<String, Object>> hits = new ArrayList<>();
    private long lastMeleeCount;
    private boolean eventApplied;

    ArchitectLabEncounter(ArchitectLabRun run) {
        this.run = run;
        villagers.add(run.target);
        starts.add(run.scenario.targetStart);
        List<Vec3> extras = switch (run.scenario) {
            case MULTI_CHOICE -> List.of(new Vec3(10.5, 1, 12.5), new Vec3(15.5, 1, 10.5));
            case MULTI_CROSSING -> List.of(new Vec3(14.5, 1, 13.5));
            default -> List.of(new Vec3(14.5, 1, 10.5));
        };
        for (Vec3 start : extras) {
            Villager villager = EntityType.VILLAGER.create(run.level);
            if (villager == null) throw new IllegalStateException("Could not create additional lab target");
            villager.addTag("fd_lab");
            villager.setPersistenceRequired();
            villager.setSilent(true);
            villager.setNoAi(true);
            villager.setNoGravity(true);
            villager.setInvulnerable(true);
            villager.setPos(run.frame.position(start));
            villagers.add(villager);
            starts.add(start);
            run.level.addFreshEntity(villager);
        }
    }

    void begin() {
        run.architect.clearDebugTargetLock();
        for (int i = 0; i < villagers.size(); i++) {
            Villager v = villagers.get(i);
            v.tickCount = 0;
            v.getRandom().setSeed(run.seed + i);
            v.setPos(run.frame.position(starts.get(i)));
            v.setDeltaMovement(Vec3.ZERO);
            v.setNoAi(!run.liveTarget);
            v.setNoGravity(false);
            v.setInvulnerable(false);
            previousHealth.put(v.getUUID(), v.getHealth());
        }
        run.architect.recordDecision("MULTI_TARGETS", null, "count=" + villagers.size() + " targetLock=false");
    }

    String tick(Set<String> applied) {
        long tick = run.elapsedTicks();
        if (!eventApplied && tick >= 60) {
            if (run.scenario == ArchitectLabScenario.MULTI_TARGET_REMOVED) {
                removed.add(run.target.getUUID());
                run.target.discard();
                applied.add("primary_removed");
                run.architect.recordDecision("SCENARIO_EVENT", null, "primary_removed uuid=" + run.target.getUUID());
            } else if (run.scenario == ArchitectLabScenario.MULTI_CROSSING) {
                move(villagers.get(0), new Vec3(6.5, 1, 14.5));
                move(villagers.get(1), new Vec3(8.5, 1, 6.5));
                applied.add("targets_crossed");
                run.architect.recordDecision("SCENARIO_EVENT", null, "targets_crossed");
            }
            eventApplied = true;
        }
        long meleeCount = run.architect.decisionJournal().eventCounts().getOrDefault("MELEE_HIT", 0L);
        for (int i = 0; i < villagers.size(); i++) {
            Villager v = villagers.get(i);
            if (removed.contains(v.getUUID())) continue;
            if (!v.isAlive() || v.isRemoved()) return "Encounter target " + i + " died or disappeared unexpectedly";
            float oldHealth = previousHealth.get(v.getUUID());
            if (meleeCount > lastMeleeCount && v.getHealth() < oldHealth && v.getLastHurtByMob() == run.architect) {
                hits.add(Map.of("role", i, "uuid", v.getUUID().toString(), "tick", tick,
                        "healthBefore", oldHealth, "healthAfter", v.getHealth()));
            }
            previousHealth.put(v.getUUID(), v.getHealth());
        }
        lastMeleeCount = meleeCount;
        return null;
    }

    private void move(Villager target, Vec3 position) {
        target.getNavigation().stop();
        target.setPos(run.frame.position(position));
        target.setDeltaMovement(Vec3.ZERO);
    }

    boolean passed() {
        return hits.stream().anyMatch(hit -> switch (run.scenario) {
            case MULTI_TARGET_REMOVED -> (int) hit.get("role") != 0 && (long) hit.get("tick") > 60;
            case MULTI_CROSSING -> (long) hit.get("tick") > 60;
            case MULTI_NEAR_ENCLOSED -> (int) hit.get("role") != 0;
            default -> true;
        });
    }

    void pause() {
        for (Villager v : villagers) {
            v.setNoAi(true);
            v.setNoGravity(true);
            v.getNavigation().stop();
            v.setDeltaMovement(Vec3.ZERO);
        }
    }

    void disposeExtras() { for (int i = 1; i < villagers.size(); i++) villagers.get(i).discard(); }

    Map<String, Object> context() {
        List<Map<String, Object>> roster = new ArrayList<>();
        for (int i = 0; i < villagers.size(); i++) {
            Villager v = villagers.get(i);
            Vec3 pos = run.frame.local(v.position());
            roster.add(Map.of("role", i, "uuid", v.getUUID().toString(), "start", List.of(starts.get(i).x, starts.get(i).y, starts.get(i).z),
                    "finalPosition", List.of(pos.x, pos.y, pos.z), "finalHealth", v.getHealth(), "scriptedRemoval", removed.contains(v.getUUID())));
        }
        return Map.of("targetLock", false, "targets", roster, "hits", List.copyOf(hits));
    }
}
