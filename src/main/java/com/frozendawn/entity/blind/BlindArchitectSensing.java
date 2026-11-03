package com.frozendawn.entity.blind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Target-directed bounded propagation. All geometry is sampled synchronously at emission. */
public final class BlindArchitectSensing {
    private record Node(BlockPos position, int direction) { }
    private record Entry(Node node, double cost, double score) implements Comparable<Entry> {
        public int compareTo(Entry other) {
            int c = Double.compare(score, other.score);
            if (c == 0) c = Long.compare(node.position.asLong(), other.node.position.asLong());
            return c == 0 ? Integer.compare(node.direction, other.node.direction) : c;
        }
    }
    public record Result(double cost, List<Vec3> path, int visited, boolean exhausted) {
        public Result { path = List.copyOf(path); }
        public boolean reached() { return !path.isEmpty(); }
    }
    private BlindArchitectSensing() { }
    private static BlockPos grid(Vec3 p, int scale) { return BlockPos.containing(p.scale(scale)); }
    private static Vec3 point(BlockPos p, int scale) { return Vec3.atCenterOf(p).scale(1.0 / scale); }
    private static boolean loaded(ServerLevel level, Vec3 p) {
        BlockPos b = BlockPos.containing(p);
        return level.isInWorldBounds(b) && level.hasChunkAt(b);
    }
    public static boolean openEdge(ServerLevel level, Vec3 a, Vec3 b) {
        if (!loaded(level, a) || !loaded(level, b)) return false;
        // Edges are at most half a block. Inspect every crossed cell and the
        // cell below it: fences extend above their owning block.
        for (int x=(int)Math.floor(Math.min(a.x,b.x));x<=(int)Math.floor(Math.max(a.x,b.x));x++)
            for (int y=(int)Math.floor(Math.min(a.y,b.y))-1;y<=(int)Math.floor(Math.max(a.y,b.y));y++)
                for (int z=(int)Math.floor(Math.min(a.z,b.z));z<=(int)Math.floor(Math.max(a.z,b.z));z++) {
                    BlockPos cell=new BlockPos(x,y,z);
                    if (!level.isInWorldBounds(cell)||!level.hasChunkAt(cell)) return false;
                    var state=level.getBlockState(cell);
                    if (y>=(int)Math.floor(Math.min(a.y,b.y))&&!state.getFluidState().isEmpty()) return false;
                    var shape=state.getCollisionShape(level,cell);
                    if (!shape.isEmpty()&&shape.clip(a,b,cell)!=null) return false;
                }
        return true;
    }
    public static Result air(ServerLevel level, Vec3 from, Vec3 to, int range, int limit) {
        return search(level, from, to, range, limit);
    }
    private static Result search(ServerLevel level, Vec3 from, Vec3 to, double maximum, int limit) {
        int scale = 2;
        BlockPos start = grid(from, scale), goal = grid(to, scale);
        if (!loaded(level, from) || !loaded(level, to) || from.distanceTo(to) > maximum || limit <= 0)
            return new Result(0, List.of(), 0, limit <= 0);
        int cap=Math.min(limit,BlindArchitectPolicy.QUERY_LIMIT),visited=0;
        // A verified straight path avoids searching a three-dimensional volume
        // for ordinary open-air sound. Every short edge is charged to the same
        // query/actor/level cap; the frozen path is rechecked on delayed returns.
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)*4));
        var direct=new ArrayList<Vec3>();direct.add(from);
        boolean clear=true;
        for(int i=1;i<=steps;i++) {
            if(visited>=cap)return new Result(0,List.of(),visited,true);
            visited++;Vec3 next=from.lerp(to,i/(double)steps);
            if(!openEdge(level,direct.getLast(),next)) {clear=false;break;}
            direct.add(next);
        }
        if(clear)return new Result(from.distanceTo(to),direct,visited,false);
        if (!openEdge(level, from, point(start, scale)) || !openEdge(level, point(goal, scale), to))
            return new Result(0,List.of(),visited,false);
        var frontier = new PriorityQueue<Entry>();
        var costs = new HashMap<Node, Double>();
        var parents = new HashMap<Node, Node>();
        Node initial = new Node(start, -1);
        costs.put(initial, 0.0); frontier.add(new Entry(initial, 0, start.distManhattan(goal) / (double) scale));
        while (!frontier.isEmpty() && visited < cap) {
            Entry entry = frontier.remove(); Node current = entry.node;
            if (entry.cost > costs.getOrDefault(current, Double.POSITIVE_INFINITY)) continue;
            visited++;
            if (current.position.equals(goal)) {
                var path = new ArrayList<Vec3>(); Node at = current;
                while (at != null) { path.add(point(at.position, scale)); at = parents.get(at); }
                java.util.Collections.reverse(path);
                path.add(0, from); path.add(to);
                return new Result(entry.cost, path, visited, false);
            }
            for (Direction direction : Direction.values()) {
                BlockPos nextPosition = current.position.relative(direction);
                Vec3 nextPoint = point(nextPosition, scale);
                if (!loaded(level, nextPoint)) continue;
                double step = 1.0 / scale;
                if (!openEdge(level, point(current.position, scale), nextPoint)) continue;
                if (current.direction >= 0 && current.direction != direction.ordinal()) step += 2;
                double cost = entry.cost + step;
                if (cost > maximum || cost + nextPosition.distManhattan(goal) / (double) scale > maximum) continue;
                Node next = new Node(nextPosition, direction.ordinal());
                if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                costs.put(next, cost); parents.put(next, current);
                frontier.add(new Entry(next, cost, cost + 1.5 * nextPosition.distManhattan(goal) / (double) scale));
            }
        }
        return new Result(0, List.of(), visited, !frontier.isEmpty());
    }
    public static boolean stillOpen(ServerLevel level, Result result) {
        for (int i = 1; i < result.path.size(); i++)
            if (!openEdge(level, result.path.get(i - 1), result.path.get(i))) return false;
        return !result.path.isEmpty();
    }
    /** Coarse lattice, then a sampled connected air point; no random offsets into a wall. */
    public static BlockPos echoRegion(Result result, BlockPos feet, int width, double eyeHeight) {
        BlockPos coarse = quantize(feet, width);
        Vec3 wanted = Vec3.atCenterOf(coarse).add(0, eyeHeight - .5, 0);
        return result.path.stream().filter(p -> p.distanceToSqr(Vec3.atCenterOf(feet).add(0, eyeHeight - .5, 0)) <= width * width)
                .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(wanted)))
                .map(p -> BlockPos.containing(p.add(0, -eyeHeight, 0))).orElse(coarse);
    }
    public static BlockPos quantize(BlockPos p, int width) {
        return new BlockPos(Math.floorDiv(p.getX(), width) * width + width / 2,
                Math.floorDiv(p.getY(), width) * width + width / 2,
                Math.floorDiv(p.getZ(), width) * width + width / 2);
    }
}
