package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stateful encounter contracts. The runner changes the world, never resets the Architect's AI. */
final class ArchitectLabLifecycle {
    private final ArchitectLabRun run;
    private Villager active;
    private final List<Villager> roster = new ArrayList<>();
    private final List<Map<String, Object>> checkpoints = new ArrayList<>();
    private final List<BlockPos> placedPositions = new ArrayList<>();
    private long lastMelee, lastPlaces, lastBreakEnds, lastProgress, longestStall;
    private int stage, hits, downBreaks, upBreaks;
    private double bestDistance = Double.POSITIVE_INFINITY;
    private boolean stageHit, changed, repaired;
    private long changedAt = -1, placesAtDamage;
    private BlockPos removedScaffold;
    private float previousHealth;

    ArchitectLabLifecycle(ArchitectLabRun run) {
        this.run = run;
        active = run.target;
        roster.add(active);
    }

    void begin() {
        if (run.scenario == ArchitectLabScenario.TARGET_TURNOVER) run.architect.clearDebugTargetLock();
        previousHealth = active.getHealth();
        checkpoint("begin");
    }

    String tick() {
        long tick = run.elapsedTicks();
        if (!active.isAlive() || active.isRemoved()) {
            checkpoint("target_lost");
            return "Active lifecycle target died or disappeared unexpectedly; damage="
                    + (active.getLastDamageSource() == null ? "unknown" : active.getLastDamageSource().getMsgId());
        }
        var counts = run.architect.decisionJournal().eventCounts();
        long melee = counts.getOrDefault("MELEE_HIT", 0L), places = counts.getOrDefault("SCAFFOLD_PLACE", 0L);
        long ends = counts.getOrDefault("BREAK_END", 0L);
        if (places != lastPlaces || ends != lastBreakEnds) {
            boolean usefulChange = places > lastPlaces;
            long needPlaces = places - lastPlaces, needEnds = ends - lastBreakEnds;
            var entries = run.architect.decisionJournal().entries();
            for (int i = entries.size() - 1; i >= 0 && (needPlaces > 0 || needEnds > 0); i--) {
                var e = entries.get(i);
                if (e.event().equals("SCAFFOLD_PLACE") && needPlaces-- > 0 && e.choice() != null)
                    placedPositions.add(e.choice().pos());
                if (e.event().equals("BREAK_END") && needEnds-- > 0 && e.choice() != null && e.detail().equals("DESTROYED")) {
                    usefulChange = true;
                    if (e.choice().pos().getY() < e.pos().getY()) downBreaks++;
                    if (e.choice().pos().getY() > e.pos().getY()) upBreaks++;
                }
            }
            if (usefulChange) lastProgress = tick;
        }
        if (melee > lastMelee && active.getHealth() < previousHealth && active.getLastHurtByMob() == run.architect) {
            hits++;
            if (!stageHit) { stageHit = true; checkpoint("hit"); }
            // Keep the same real, damageable villager alive for the full observation period.
            active.setHealth(active.getMaxHealth());
        }
        previousHealth = active.getHealth();
        lastMelee = melee;
        lastPlaces = places;
        lastBreakEnds = ends;
        if (places > placementBudget()) return "Scaffold placement budget exceeded: " + places + " > " + placementBudget();
        Vec3 local = run.frame.local(run.architect.position());
        if ((run.scenario == ArchitectLabScenario.SCAFFOLD_GAP || run.scenario == ArchitectLabScenario.SCAFFOLD_DAMAGE
                || run.scenario == ArchitectLabScenario.MIXED_ESCAPE) && local.y < 5.75)
            return "Architect left the elevated crossing";

        if (!changed) {
            switch (run.scenario) {
                case SCAFFOLD_INTERRUPTION -> {
                    if (places > 0) { change("target_moved_during_scaffolding"); move(new Vec3(5.5, 1, 15.5), false); }
                }
                case SCAFFOLD_DAMAGE -> {
                    for (BlockPos pos : placedPositions) {
                        if (run.level.getBlockState(pos).isAir()) continue;
                        // Remove a constructed block ahead, never the current supporting block.
                        if (new AABB(pos).inflate(0.05, 1.0, 0.05).intersects(run.architect.getBoundingBox())) continue;
                        if (Vec3.atCenterOf(pos).distanceToSqr(active.position()) >= run.architect.distanceToSqr(active)) continue;
                        removedScaffold = pos;
                        placesAtDamage = places;
                        run.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        change("constructed_bridge_removed_ahead");
                        stageHit = false;
                        break;
                    }
                }
                case ROUTE_OPENS_MINING -> {
                    if (counts.getOrDefault("BREAK_START", 0L) > 0) {
                        for (int y = 1; y <= 2; y++) run.level.setBlockAndUpdate(run.frame.block(new BlockPos(9, y, 10)), Blocks.AIR.defaultBlockState());
                        change("route_opened_after_mining_started");
                    }
                }
                case ROUTE_CLOSES_TRAVEL -> {
                    if (local.x >= 6 && local.x < 9) {
                        for (int y = 1; y <= 2; y++) run.level.setBlockAndUpdate(run.frame.block(new BlockPos(10, y, 10)), Blocks.BEDROCK.defaultBlockState());
                        change("direct_route_closed_during_travel");
                    }
                }
                default -> { }
            }
        }
        if (removedScaffold != null && places > placesAtDamage && !run.level.getBlockState(removedScaffold).isAir()) repaired = true;
        double distance = run.architect.distanceToSqr(active);
        if (distance + 0.25 < bestDistance) { bestDistance = distance; lastProgress = tick; }
        if (!stageHit) {
            longestStall = Math.max(longestStall, tick - lastProgress);
            if (tick - lastProgress >= 600) return "No useful progress for 600 ticks in lifecycle stage " + stage;
        }
        int interval = run.scenario == ArchitectLabScenario.TARGET_TURNOVER || run.scenario == ArchitectLabScenario.LONG_PURSUIT ? 600 : 1200;
        if (run.scenario.minimumDuration() >= 3600 && tick >= (stage + 1L) * interval && tick < run.scenario.minimumDuration()) {
            if (!stageHit) return "Lifecycle stage " + stage + " missed its melee checkpoint";
            String mechanism = missingMechanism();
            if (mechanism != null) return mechanism;
            stage++;
            Vec3 destination = nextDestination(stage);
            if (run.scenario == ArchitectLabScenario.ROUTE_OPENS_MINING) {
                for (int y = 1; y <= 2; y++) run.level.setBlockAndUpdate(run.frame.block(new BlockPos(9, y, 10)), Blocks.STONE.defaultBlockState());
                changed = false;
            }
            if (run.scenario == ArchitectLabScenario.ROUTE_CLOSES_TRAVEL) {
                // Alternate which doorway is available after each successful crossing.
                int closeZ = stage % 2 == 1 ? 8 : 10;
                for (int z = 8; z <= 10; z++) for (int y = 1; y <= 2; y++)
                    run.level.setBlockAndUpdate(run.frame.block(new BlockPos(10, y, z)),
                            z == closeZ ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
                checkpoint("doorways_changed");
            }
            move(destination, run.scenario == ArchitectLabScenario.TARGET_TURNOVER);
        }
        return null;
    }

    private Vec3 nextDestination(int number) {
        return switch (run.scenario) {
            case TARGET_TURNOVER, LONG_PURSUIT -> switch (number % 4) {
                case 0 -> new Vec3(16.5, 1, 4.5);
                case 1 -> new Vec3(16.5, 3, 16.5);
                case 2 -> new Vec3(4.5, 3, 16.5);
                default -> new Vec3(4.5, 1, 4.5);
            };
            case SCAFFOLD_INTERRUPTION -> number % 2 == 0 ? new Vec3(5.5, 1, 15.5) : run.scenario.targetStart;
            case SCAFFOLD_DAMAGE -> number % 2 == 0 ? run.scenario.targetStart : run.scenario.actorStart;
            case MIXED_ESCAPE -> number % 2 == 0 ? run.scenario.targetStart : new Vec3(13.5, 6, 10.5);
            default -> number % 2 == 0 ? run.scenario.targetStart : run.scenario.actorStart;
        };
    }

    private void move(Vec3 pos, boolean replace) {
        if (replace) {
            active.discard();
            active = EntityType.VILLAGER.create(run.level);
            if (active == null) throw new IllegalStateException("Could not create lifecycle replacement target");
            active.addTag("fd_lab");
            active.setPersistenceRequired();
            active.setSilent(true);
            active.setNoAi(!run.liveTarget);
            active.setPos(run.frame.position(pos));
            run.level.addFreshEntity(active);
            roster.add(active);
        }
        active.getNavigation().stop();
        active.setPos(run.frame.position(pos));
        active.setDeltaMovement(Vec3.ZERO);
        active.resetFallDistance();
        active.setHealth(active.getMaxHealth());
        // Stages are separate pursuit observations. Healing alone leaves hundreds of
        // preceding melee hits' freezing damage ticking during the next traversal.
        active.setTicksFrozen(0);
        previousHealth = active.getHealth();
        stageHit = false;
        bestDistance = Double.POSITIVE_INFINITY;
        lastProgress = run.elapsedTicks();
        checkpoint(replace ? "target_replaced" : "target_moved");
    }

    private void change(String name) {
        changed = true;
        changedAt = run.elapsedTicks();
        checkpoint(name);
    }

    private String missingMechanism() {
        return switch (run.scenario) {
            case SCAFFOLD_ASCENT, SCAFFOLD_GAP -> lastPlaces == 0 ? "Required scaffolding never occurred" : null;
            case SCAFFOLD_INTERRUPTION, SCAFFOLD_DAMAGE, ROUTE_OPENS_MINING, ROUTE_CLOSES_TRAVEL ->
                    !changed ? "Required mid-action world/target change never occurred" : null;
            case DIG_DOWN_REQUIRED -> downBreaks == 0 ? "Required downward excavation never occurred" : null;
            case DIG_UP_REQUIRED -> upBreaks == 0 || lastPlaces == 0 ? "Required upward excavation and scaffolding never occurred" : null;
            case MIXED_ESCAPE -> run.architect.successfulBreakCount() == 0 || lastPlaces == 0 ? "Required dig/build sequence never occurred" : null;
            default -> null;
        };
    }

    boolean passed() {
        return run.elapsedTicks() >= run.scenario.minimumDuration() && stageHit && missingMechanism() == null;
    }

    private int placementBudget() {
        return switch (run.scenario) {
            case SCAFFOLD_ASCENT, SCAFFOLD_GAP, DIG_UP_REQUIRED -> 32;
            case SCAFFOLD_INTERRUPTION, SCAFFOLD_DAMAGE, MIXED_ESCAPE -> 96;
            default -> 0;
        };
    }

    private void checkpoint(String event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tick", run.elapsedTicks()); value.put("stage", stage); value.put("event", event);
        value.put("targetUuid", active.getUUID().toString());
        Vec3 local = run.frame.local(active.position());
        value.put("targetPosition", List.of(local.x, local.y, local.z));
        value.put("targetHealth", active.getHealth());
        value.put("targetFrozenTicks", active.getTicksFrozen());
        value.put("targetLastAttacker", active.getLastDamageSource() == null || active.getLastDamageSource().getEntity() == null
                ? "" : active.getLastDamageSource().getEntity().getUUID().toString());
        value.put("targetLastDamage", active.getLastDamageSource() == null ? "" : active.getLastDamageSource().getMsgId()); value.put("actorHealth", run.architect.getHealth());
        value.put("hits", hits); value.put("placed", lastPlaces); value.put("destroyed", run.architect.successfulBreakCount());
        checkpoints.add(value);
        run.architect.recordDecision("LIFECYCLE_EVENT", null, event + " stage=" + stage + " target=" + active.getUUID());
    }

    void pause() {
        for (Villager v : roster) { v.setNoAi(true); v.setNoGravity(true); v.getNavigation().stop(); v.setDeltaMovement(Vec3.ZERO); }
    }
    void dispose() { for (Villager v : roster) if (v != run.target) v.discard(); }
    Map<String, Object> context() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minimumDurationTicks", run.scenario.minimumDuration()); data.put("stallLimitTicks", 600);
        data.put("stage", stage); data.put("hits", hits); data.put("placed", lastPlaces);
        data.put("placementBudget", placementBudget()); data.put("longestStallTicks", longestStall);
        data.put("downwardBreaks", downBreaks); data.put("upwardBreaks", upBreaks);
        data.put("changeTick", changedAt); data.put("removedScaffoldRepaired", repaired);
        data.put("activeTargetUuid", active.getUUID().toString()); data.put("checkpoints", List.copyOf(checkpoints));
        data.put("missingMechanism", missingMechanism() == null ? "" : missingMechanism());
        return data;
    }
}
