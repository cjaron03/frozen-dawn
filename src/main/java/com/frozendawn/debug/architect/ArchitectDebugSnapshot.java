package com.frozendawn.debug.architect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Locale;

/** Immutable server observations shared by the client overlay and on-disk evidence. */
public record ArchitectDebugSnapshot(
        int schema, long gameTick, long runTick, String runId, String dimension, boolean frozen,
        Lab lab, Body actor, Body target, Planner planner, Combat combat, Mining mining,
        Recovery recovery, List<Shape> collisionShapes, boolean shapesTruncated,
        List<Marker> markers, List<Trail> trails, List<Event> events) {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final int MAX_PATH_NODES = 64;
    public static final int MAX_SHAPES = 96;
    public static final int MAX_MARKERS = 24;

    public ArchitectDebugSnapshot {
        collisionShapes = List.copyOf(collisionShapes);
        markers = List.copyOf(markers);
        trails = List.copyOf(trails);
        events = List.copyOf(events);
    }

    public record Point(double x, double y, double z) { }
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) { }
    public record PathNode(int x, int y, int z, String type, float costMalus) { }
    public record Route(List<PathNode> nodes, Point destination, int nextNode, int firstNode,
                        int totalNodes, boolean reachable, boolean done, boolean stuck, float nodeRadius) {
        public Route { nodes = List.copyOf(nodes); }
        public boolean truncated() { return nodes.size() != totalNodes; }
        public static Route empty() { return new Route(List.of(), null, 0, 0, 0, false, true, false, 0.2f); }
    }
    public record Body(int entityId, String uuid, String name, Point position, Point velocity,
                       Box bounds, float health, float yaw, float headYaw, float bodyYaw,
                       boolean onGround, boolean horizontalCollision, Point support, Route navigation) { }
    public record Lab(String scenario, String status, String reason, long elapsedTicks, long durationTicks,
                      Point targetGoal, int waypoint, int lap, long hits, long actorStill, long targetStill) {
        public static Lab field() { return new Lab("field", "OBSERVING", "", 0, 0, null, -1, -1, 0, 0, 0); }
    }
    public record Planner(String search, int cells, int queueEntries, Point goal, Point step,
                          String stepType, Point waypoint, List<Point> corridor, int corridorFirst, int corridorIndex,
                          int corridorTotal, Point committedTarget, Point steeringGoal) {
        public Planner { corridor = List.copyOf(corridor); }
    }
    public record Combat(double distance, double horizontalDistance, double verticalDistance,
                         double attackRange, boolean lineOfSight, boolean canCommit, boolean commitKnown, long commitGameTick,
                         boolean hitEligible, String blockedBy, int backoffTicks) { }
    public record Mining(Point block, String reason, int progressTicks, int requiredTicks) { }
    public record Recovery(String action, int noProgressTicks, int walkStuckTicks, int committedStallTicks,
                           int reinits, int exclusions, long destroyedBlocks, long scaffoldPlacements) { }
    public record Shape(String block, Box bounds) { }
    public record Marker(Point block, String kind, String reason) { }
    public record Trail(long gameTick, Point actor, Point target) { }
    public record Event(long gameTick, long runTick, String kind, String detail, Point block, String reason) { }

    public String json() { return JSON.toJson(this); }

    public String logLine() {
        return String.format(Locale.ROOT,
                "run=%s tick=%d gameTick=%d entity=%d scenario=%s status=%s action=%s pos=%.3f,%.3f,%.3f "
                        + "target=%s distance=%.3f attack=%s planner=%s step=%s waypoint=%s nav=%d/%d "
                        + "mining=%s:%d/%d noProgress=%d walkStuck=%d reinits=%d",
                runId, runTick, gameTick, actor.entityId(), lab.scenario(), lab.status(), recovery.action(),
                actor.position().x(), actor.position().y(), actor.position().z(),
                target == null ? "none" : target.uuid(), combat.distance(), combat.blockedBy(), planner.search(),
                planner.stepType(), planner.waypoint(), actor.navigation().nextNode(), actor.navigation().totalNodes(),
                mining.reason(), mining.progressTicks(), mining.requiredTicks(),
                recovery.noProgressTicks(), recovery.walkStuckTicks(), recovery.reinits());
    }
}
