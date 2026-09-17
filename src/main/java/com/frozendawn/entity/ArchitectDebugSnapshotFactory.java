package com.frozendawn.entity;

import com.frozendawn.debug.architect.ArchitectDebugSnapshot;
import com.frozendawn.debug.architect.ArchitectDebugSnapshot.*;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectCombatState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Copies actual server state; never calls a planner, creates a path, or changes a controller. */
final class ArchitectDebugSnapshotFactory {
    private ArchitectDebugSnapshotFactory() { }

    static ArchitectDebugSnapshot capture(ArchitectEntity actor, ArchitectApproachState approach,
            ArchitectCombatState combat, ArchitectBlockBreaker breaker, DStarLitePathfinder.NextStep step,
            ArchitectEntity.MeleeDebugObservation meleeObservation, Lab lab, boolean frozen) {
        var journal = actor.decisionJournal();
        var trace = journal.visual();
        long tick = actor.level().getGameTime();
        LivingEntity target = actor.getTarget();
        var search = approach.dstar.debugState();
        String searchStatus = !search.initialized() ? "UNINITIALIZED" : search.aborted() ? "ABORTED"
                : search.complete() ? "COMPLETE" : "SEARCHING";
        var move = actor.getMoveControl();
        Point steering = move.hasWanted() ? new Point(move.getWantedX(), move.getWantedY(), move.getWantedZ()) : null;
        int corridorFirst = Math.max(0, Math.min(approach.committedWalkCorridorIndex - 8,
                approach.committedWalkCorridor.size() - ArchitectDebugSnapshot.MAX_PATH_NODES));
        Planner planner = new Planner(searchStatus, search.cells(), search.queueEntries(), point(search.goal()),
                step == null ? null : point(step.pos()), step == null ? "NONE" : step.type().name(),
                point(approach.committedWalkWaypoint),
                approach.committedWalkCorridor.stream().skip(corridorFirst).limit(ArchitectDebugSnapshot.MAX_PATH_NODES).map(ArchitectDebugSnapshotFactory::point).toList(),
                corridorFirst, approach.committedWalkCorridorIndex, approach.committedWalkCorridor.size(),
                point(approach.committedWalkTargetSnapshot), steering);
        boolean sight = target != null && actor.hasLineOfSight(target);
        // The real commit check may create a path. Observe its last result instead of rerunning it.
        boolean commitKnown = target != null && meleeObservation != null && meleeObservation.target().equals(target.getUUID())
                && tick - meleeObservation.gameTick() <= 1;
        boolean canCommit = commitKnown && meleeObservation.allowed();
        double distance = target == null ? -1 : actor.distanceTo(target);
        String blocked = target == null ? "NO_TARGET" : actor.getBrainAction() != ArchitectEntity.ACTION_ATTACK_MELEE ? "NOT_MELEE"
                : distance >= ArchitectEntity.MELEE_ATTACK_RANGE ? "OUT_OF_RANGE" : combat.backoffTicks > 0 ? "BACKOFF"
                : !sight ? "NO_LINE_OF_SIGHT" : actor.attackAnim != 0 ? "SWING_IN_PROGRESS"
                : !commitKnown ? "COMMIT_NOT_SAMPLED" : !canCommit ? "COMMIT_REJECTED" : "ELIGIBLE";
        Combat attack = new Combat(distance, target == null ? -1 : actor.horizontalDistanceTo(target),
                target == null ? -1 : actor.verticalDistanceTo(target), ArchitectEntity.MELEE_ATTACK_RANGE,
                sight, canCommit, commitKnown, meleeObservation == null ? -1 : meleeObservation.gameTick(),
                blocked.equals("ELIGIBLE"), blocked, combat.backoffTicks);
        var choice = breaker.getChoice();
        Mining mining = new Mining(point(breaker.getTarget()), choice == null ? "NONE" : choice.reason().name(),
                breaker.debugProgressTicks(), breaker.debugRequiredTicks());
        Recovery recovery = new Recovery(ArchitectEntity.actionName(actor.getBrainAction()), approach.approachNoProgressTicks,
                approach.walkStuckTicks, approach.committedWalkNoProgressTicks, approach.unstickReinitAttempts,
                approach.blockedUnstickBreakCandidates.size(), trace.capturedBreaks(), trace.capturedPlacements());
        List<Marker> markers = new ArrayList<>();
        if (breaker.getTarget() != null) markers.add(new Marker(point(breaker.getTarget()), "MINING", mining.reason()));
        if (approach.scaffoldTarget != null) markers.add(new Marker(point(approach.scaffoldTarget), "SCAFFOLD", "queued placement"));
        if (step != null) markers.add(new Marker(point(step.pos()), "STEP", step.type().name()));
        if (actor.mainSupportingBlockPos.isPresent()) markers.add(new Marker(point(actor.mainSupportingBlockPos.get()), "SUPPORT", "Minecraft support"));
        for (var block : approach.blockedUnstickBreakCandidates) {
            if (markers.size() >= 12) break;
            markers.add(new Marker(point(block), "EXCLUDED", "failed break candidate"));
        }
        for (var marker : trace.candidates(tick)) {
            if (markers.size() == ArchitectDebugSnapshot.MAX_MARKERS) break;
            markers.add(marker);
        }
        List<Shape> shapes = collisionShapes(actor, markers);
        return new ArchitectDebugSnapshot(1, tick, trace.elapsed(tick), trace.runId(), actor.level().dimension().location().toString(),
                frozen, lab, body(actor), target == null ? null : body(target), planner, attack, mining, recovery,
                shapes, shapes.size() == ArchitectDebugSnapshot.MAX_SHAPES, markers,
                trace.trail(tick, point(actor.position()), target == null ? null : point(target.position())), trace.recentEvents());
    }

    private static Body body(LivingEntity entity) {
        return new Body(entity.getId(), entity.getUUID().toString(), entity.getName().getString(), point(entity.position()),
                point(entity.getDeltaMovement()), box(entity.getBoundingBox()), entity.getHealth(), entity.getYRot(),
                entity.yHeadRot, entity.yBodyRot, entity.onGround(), entity.horizontalCollision,
                entity.mainSupportingBlockPos.map(ArchitectDebugSnapshotFactory::point).orElse(null),
                entity instanceof Mob mob ? route(mob) : Route.empty());
    }

    private static Route route(Mob mob) {
        var navigation = mob.getNavigation();
        var path = navigation.getPath();
        if (path == null) return new Route(List.of(), null, 0, 0, 0, false, true, navigation.isStuck(), navigation.getMaxDistanceToWaypoint());
        int total = path.getNodeCount();
        int first = Math.max(0, Math.min(path.getNextNodeIndex() - 8, total - ArchitectDebugSnapshot.MAX_PATH_NODES));
        List<PathNode> nodes = new ArrayList<>();
        for (int i = first; i < total && nodes.size() < ArchitectDebugSnapshot.MAX_PATH_NODES; i++) {
            var node = path.getNode(i);
            nodes.add(new PathNode(node.x, node.y, node.z, node.type.name(), Float.isFinite(node.costMalus) ? node.costMalus : Float.MAX_VALUE));
        }
        return new Route(nodes, point(path.getTarget()), path.getNextNodeIndex(), first, total,
                path.canReach(), path.isDone(), navigation.isStuck(), navigation.getMaxDistanceToWaypoint());
    }

    private static List<Shape> collisionShapes(ArchitectEntity actor, List<Marker> markers) {
        var blocks = new LinkedHashSet<BlockPos>();
        for (var marker : markers) {
            Point p = marker.block();
            if (actor.position().distanceToSqr(p.x(), p.y(), p.z()) < 64) blocks.add(BlockPos.containing(p.x(), p.y(), p.z()));
        }
        AABB area = actor.getBoundingBox().inflate(1.25, 0.75, 1.25);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ))) blocks.add(p.immutable());
        List<Shape> result = new ArrayList<>();
        var collisionContext = CollisionContext.of(actor);
        for (var block : blocks) {
            if (!actor.level().hasChunkAt(block)) continue;
            var state = actor.level().getBlockState(block);
            for (var shape : state.getCollisionShape(actor.level(), block, collisionContext).toAabbs()) {
                if (result.size() == ArchitectDebugSnapshot.MAX_SHAPES) return result;
                result.add(new Shape(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), box(shape.move(block))));
            }
        }
        return result;
    }

    static Point point(BlockPos pos) { return pos == null ? null : new Point(pos.getX(), pos.getY(), pos.getZ()); }
    static Point point(Vec3 pos) { return pos == null ? null : new Point(pos.x, pos.y, pos.z); }
    static Box box(AABB b) { return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
}
