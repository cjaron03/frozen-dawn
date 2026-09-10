package com.frozendawn.debug.architect;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The same setup and assertions power manual lab runs and headless GameTests. */
public final class ArchitectLabRun {
    public enum Status { PREPARED, RUNNING, PASSED, FAILED, ABORTED }
    public final UUID id = UUID.randomUUID();
    public final ServerLevel level;
    public final ArchitectLabScenario scenario;
    public final ArchitectLabFrame frame;
    public final long seed;
    public final boolean liveTarget;
    public final ArchitectEntity architect;
    public final Villager target;
    private final ArchitectLabEncounter encounter;
    private final ArchitectLabLifecycle lifecycle;
    private java.util.List<BlockPos> generatedRoute = java.util.List.of();
    private final Map<BlockPos, BlockState> preserved = new LinkedHashMap<>();
    private Status status = Status.PREPARED;
    private String reason = "Prepared; recording has not started";
    private long startTick;
    private long lastCheckedTick = Long.MIN_VALUE;
    private long suppressionExpires = -1;
    private long completionTick;
    private String initialTerrainHash = "";
    private float startingTargetHealth;
    private float startingActorHealth;
    private final java.util.Set<String> appliedEvents = new java.util.LinkedHashSet<>();

    private ArchitectLabRun(ServerLevel level, ArchitectLabScenario scenario, ArchitectLabFrame frame, long seed, boolean liveTarget) {
        this.level = level; this.scenario = scenario; this.frame = frame; this.seed = seed; this.liveTarget = liveTarget;
        architect = ModEntities.ARCHITECT.get().create(level);
        target = EntityType.VILLAGER.create(level);
        if (architect == null || target == null) throw new IllegalStateException("Could not create lab actors");
        architect.decisionJournal().stop();
        architect.addTag("fd_lab");
        target.addTag("fd_lab");
        architect.setPersistenceRequired();
        target.setPersistenceRequired();
        architect.setPos(frame.position(scenario.actorStart));
        target.setPos(frame.position(scenario.targetStart));
        target.setSilent(true);
        target.setInvulnerable(true);
        encounter = scenario.multipleTargetsCase() ? new ArchitectLabEncounter(this) : null;
        lifecycle = scenario.lifecycleCase() ? new ArchitectLabLifecycle(this) : null;
        pauseActors();
        level.addFreshEntity(target);
        level.addFreshEntity(architect);
    }

    public static ArchitectLabRun prepare(ServerLevel level, ArchitectLabScenario scenario, ArchitectLabFrame frame,
            long seed, boolean liveTarget, boolean restoreTerrain) {
        if (restoreTerrain) {
            var template = level.getStructureManager().get(scenario.template())
                    .orElseThrow(() -> new IllegalStateException("Missing fixture " + scenario.template()));
            var settings = new StructurePlaceSettings().setRotation(frame.rotation()).setIgnoreEntities(true);
            if (!template.placeInWorld(level, frame.origin(), frame.origin(), settings, RandomSource.create(0), 3)) {
                throw new IllegalStateException("Could not place fixture " + scenario.template());
            }
        }
        if (scenario == ArchitectLabScenario.SEEDED_MAZE) ArchitectLabStress.carveMaze(level, frame, seed);
        var route = scenario.fieldCase() ? ArchitectLabObstacleField.place(level, frame, scenario, seed) : java.util.List.<BlockPos>of();
        ArchitectLabRun run = new ArchitectLabRun(level, scenario, frame, seed, liveTarget);
        run.generatedRoute = route;
        return run;
    }

    /** One server-thread transaction, while both actors are still paused. No AI can interleave. */
    public void begin() {
        if (status != Status.PREPARED) throw new IllegalStateException("Reset the lab before starting another run");
        for (BlockPos local : scenario.preservedBlocks()) {
            BlockPos world = frame.block(local);
            preserved.put(world, level.getBlockState(world));
        }
        StringBuilder terrain = new StringBuilder();
        for (int x = 0; x < 21; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 21; z++) {
            terrain.append(level.getBlockState(frame.block(new BlockPos(x, y, z)))).append('\n');
        }
        initialTerrainHash = ArchitectDebugReports.sha256(terrain.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        architect.tickCount = target.tickCount = 0;
        architect.debugForceApproach(target);
        // Restore exact initial conditions even if prepared ticks advanced during an edit.
        architect.setPos(frame.position(scenario.actorStart));
        architect.setDeltaMovement(Vec3.ZERO);
        target.setPos(frame.position(scenario.targetStart));
        target.setDeltaMovement(Vec3.ZERO);
        architect.getRandom().setSeed(seed);
        target.getRandom().setSeed(seed);
        architect.startDecisionRecording(id, seed, frame.rotation());
        if (scenario.expandedCase()) architect.decisionJournal().useExtendedLabBuffer();
        if (scenario.lifecycleCase()) architect.decisionJournal().useEnduranceLabBuffer();
        target.setNoAi(!liveTarget);
        target.setNoGravity(false);
        target.setInvulnerable(!liveTarget && !scenario.requiresMeleeHit());
        startingTargetHealth = target.getHealth();
        startingActorHealth = architect.getHealth();
        startTick = level.getGameTime();
        status = Status.RUNNING;
        reason = "Running";
        if (encounter != null) encounter.begin();
        if (lifecycle != null) lifecycle.begin();
        architect.recordDecision("RUN_START", null, "scenario=" + scenario.id + " target=" + (liveTarget ? "LIVE" : "STATIC"));
    }

    public Status status() { return status; }
    public String reason() { return reason; }
    public boolean done() { return status != Status.PREPARED && status != Status.RUNNING; }
    public long elapsedTicks() { return status == Status.PREPARED ? 0 : (done() ? completionTick : level.getGameTime()) - startTick; }

    /** Check invariants every tick, not only when the entity happens to reach its goal. */
    public void tick() {
        if (status != Status.RUNNING || lastCheckedTick == level.getGameTime()) return;
        lastCheckedTick = level.getGameTime();
        if (!architect.isAlive() || architect.isRemoved()) { finish(Status.FAILED, "Architect died or disappeared"); return; }
        for (var entry : preserved.entrySet()) {
            if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                finish(Status.FAILED, "Required support/terrain changed at " + frame.localBlock(entry.getKey()));
                return;
            }
        }
        if (architect.successfulBreakCount() > scenario.excavationBudget) {
            finish(Status.FAILED, "Excavation budget exceeded: " + architect.successfulBreakCount() + " > " + scenario.excavationBudget);
            return;
        }
        ArchitectLabStress.events(this, appliedEvents);
        if (encounter != null) {
            String failure = encounter.tick(appliedEvents);
            if (failure != null) { finish(Status.FAILED, failure); return; }
        }
        if (lifecycle != null) {
            String failure = lifecycle.tick();
            if (failure != null) { finish(Status.FAILED, failure); return; }
        }
        Vec3 local = frame.local(architect.position());
        if (scenario.stressCase() && encounter == null && lifecycle == null && !target.isAlive()) {
            finish(Status.FAILED, "Target died before the scenario completed"); return;
        }
        if (scenario.stressCase() && architect.getHealth() < startingActorHealth) {
            finish(Status.FAILED, "Architect took damage during the stress scenario"); return;
        }
        if (scenario.stressCase() && (architect.isInLava() || architect.isOnFire())) {
            finish(Status.FAILED, "Architect entered lava or caught fire"); return;
        }
        if (scenario == ArchitectLabScenario.NARROW_BRIDGE && local.y < 3.5) {
            finish(Status.FAILED, "Architect fell off the bridge"); return;
        }
        if (scenario == ArchitectLabScenario.FOOTING_SLAB_BRIDGE && local.y < 3.4) {
            finish(Status.FAILED, "Architect fell off the slab bridge"); return;
        }
        boolean reached = architect.distanceToSqr(target) <= 9.61 && architect.hasLineOfSight(target);
        boolean passed = lifecycle != null ? lifecycle.passed() : scenario.expandedCase() ? (encounter != null ? encounter.passed()
                : target.getHealth() < startingTargetHealth
                    && architect.decisionJournal().eventCounts().getOrDefault("MELEE_HIT", 0L) > 0
                    && ArchitectLabStress.eventsComplete(scenario, appliedEvents)) : switch (scenario) {
            case LOW_CEILING -> local.y >= scenario.actorStart.y + 0.9
                    && local.z >= scenario.actorStart.z + 1.3
                    && level.getBlockState(frame.block(new BlockPos(4, 3, 4))).isAir();
            case UNREACHABLE_TARGET -> local.x > 5.3
                    && level.getBlockState(frame.block(new BlockPos(5, 1, 4))).isAir()
                    && level.getBlockState(frame.block(new BlockPos(5, 2, 4))).isAir();
            case SEALED_POCKET -> checkSuppression();
            case CORRIDOR_SOAK -> elapsedTicks() >= 1000 && reached;
            case CORRIDOR_SHUTTLE -> elapsedTicks() >= 650 && reached && ArchitectLabStress.eventsComplete(scenario, appliedEvents);
            case PIT_SHALLOW, PIT_DIRECT_STEPS, PIT_SIDE_STEPS, PIT_CORNER_STEPS,
                    PIT_NARROW_STEPS, PIT_SLAB_RAMP, PIT_TUNNEL, PIT_TARGET_OFFSET ->
                    target.getHealth() < startingTargetHealth
                    && architect.decisionJournal().eventCounts().getOrDefault("MELEE_HIT", 0L) > 0;
            default -> (liveTarget ? target.getHealth() < startingTargetHealth : reached)
                    && ArchitectLabStress.eventsComplete(scenario, appliedEvents);
        };
        if (status != Status.RUNNING) return;
        if (passed) { finish(Status.PASSED, "All scenario invariants satisfied"); return; }
        if (elapsedTicks() >= scenario.timeout) finish(Status.FAILED, "Scenario deadline exceeded: " + scenario.timeout + " ticks");
    }

    private boolean checkSuppression() {
        if (suppressionExpires < 0) {
            if (architect.isApproachTargetSuppressed(target)) {
                if (architect.getBrainAction() != ArchitectEntity.ACTION_OBSERVE
                        || architect.decisionJournal().eventCounts().getOrDefault("ABANDON", 0L) == 0) {
                    finish(Status.FAILED, "Suppression began without observable abandonment");
                    return false;
                }
                int remaining = architect.approachRetryTicksRemaining();
                if (remaining < 198) { finish(Status.FAILED, "Retry cooldown shorter than 200 ticks"); return false; }
                suppressionExpires = level.getGameTime() + remaining;
            } else if (elapsedTicks() > 650) {
                finish(Status.FAILED, "Sealed approach was not abandoned within 650 ticks");
            }
            return false;
        }
        boolean suppressed = architect.isApproachTargetSuppressed(target);
        if (level.getGameTime() < suppressionExpires) {
            if (!suppressed || architect.getTarget() == target) finish(Status.FAILED, "Target reacquired during retry cooldown");
            return false;
        }
        if (suppressed) { finish(Status.FAILED, "Retry cooldown never expired"); return false; }
        return true;
    }

    public void finish(Status result, String detail) {
        if (done()) return;
        if (result == Status.RUNNING || result == Status.PREPARED) throw new IllegalArgumentException("Not a terminal result");
        status = result;
        reason = detail;
        completionTick = level.getGameTime();
        architect.recordDecision("RUN_END", null, result + ": " + detail);
        architect.decisionJournal().finish(completionTick, result.name(), detail);
        pauseActors();
    }

    private void pauseActors() {
        architect.setNoAi(true);
        architect.setNoGravity(true);
        architect.getNavigation().stop();
        architect.setDeltaMovement(Vec3.ZERO);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        if (encounter != null) encounter.pause();
        if (lifecycle != null) lifecycle.pause();
    }

    public void dispose() {
        architect.discardLabActor();
        target.discard();
        if (encounter != null) encounter.disposeExtras();
        if (lifecycle != null) lifecycle.dispose();
    }

    public Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scenario", scenario.id);
        context.put("fixtureSha256", scenario.fingerprint());
        context.put("initialTerrainSha256", initialTerrainHash);
        context.put("seed", seed);
        context.put("targetMode", liveTarget ? "LIVE" : "STATIC");
        context.put("targetDamageable", liveTarget || scenario.requiresMeleeHit());
        context.put("actorUuid", architect.getUUID().toString());
        context.put("targetUuid", target.getUUID().toString());
        context.put("dimension", level.dimension().location().toString());
        context.put("startPosition", java.util.List.of(scenario.actorStart.x, scenario.actorStart.y, scenario.actorStart.z));
        context.put("targetStartPosition", java.util.List.of(scenario.targetStart.x, scenario.targetStart.y, scenario.targetStart.z));
        context.put("deadlineTicks", scenario.timeout);
        context.put("excavationBudget", scenario.excavationBudget);
        context.put("elapsedTicks", elapsedTicks());
        context.put("scriptedEvents", java.util.List.copyOf(appliedEvents));
        context.put("actorInitialHealth", startingActorHealth);
        context.put("actorFinalHealth", architect.getHealth());
        context.put("targetInitialHealth", startingTargetHealth);
        context.put("targetFinalHealth", target.getHealth());
        context.put("actorFinalPosition", java.util.List.of(architect.getX(), architect.getY(), architect.getZ()));
        context.put("targetFinalPosition", java.util.List.of(target.getX(), target.getY(), target.getZ()));
        if (encounter != null) context.put("encounter", encounter.context());
        if (lifecycle != null) context.put("lifecycle", lifecycle.context());
        if (scenario.fieldCase()) {
            context.put("obstacleRecipeVersion", 1);
            context.put("reservedRoute", generatedRoute.stream().map(p -> java.util.List.of(p.getX(), p.getY(), p.getZ())).toList());
        }
        context.put("finalInspection", architect.inspectDecisions());
        return context;
    }
}
