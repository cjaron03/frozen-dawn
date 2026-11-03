package com.frozendawn.entity.blind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frozen, loaded-only mechanical contact graph, in one authored low-impact band.
 * Box components touch over an area; air, diagonal points and disconnected pieces never conduct.
 * A detected result carries the energy of a verified conducting path, a conservative
 * lower bound on the strongest path. It omits elastic modes, phase and summed multipath energy.
 */
public final class BlindVibrationPropagation {
    public enum Kind { FOOTSTEP, LANDING, TOOL, MACHINE, BLOCK_IMPACT }
    public enum Outcome { DETECTED, NO_SOURCE_CONTACT, NO_RECEIVER_CONTACT, NO_PATH, BELOW_THRESHOLD,
        OUT_OF_RANGE, UNKNOWN_BUDGET, UNKNOWN_UNLOADED, UNKNOWN_GEOMETRY }
    public static final double THRESHOLD = .20;
    public static final int RANGE = 40;
    private static final double EPS = .0001, SPREADING_LOSS = .035;
    private static final double SEARCH_GUIDANCE = 2;
    private static final int MAX_BOXES = 24, MAX_SHAPE_CELLS = 4096;

    /** Private source information is used by physics only, never as a passive navigation target. */
    public record Impulse(Kind kind, Vec3 position, AABB footprint, boolean grounded,
                          BlockPos block, BlockState material, double strength, long emitted) {
        public Impulse { if (block != null) block = block.immutable(); }
    }
    public record Result(Outcome outcome, double received, double length, double loss, int work,
                         List<BlockPos> sourceContacts, List<BlockPos> receiverContacts, List<BlockPos> path) {
        public Result {
            sourceContacts = List.copyOf(sourceContacts); receiverContacts = List.copyOf(receiverContacts); path = List.copyOf(path);
        }
        public boolean detected() { return outcome == Outcome.DETECTED; }
        public boolean unknown() { return outcome.name().startsWith("UNKNOWN_"); }
    }
    private record Node(BlockPos block, int part) { }
    private record Piece(Node node, AABB box, BlindStructuralMaterials.Profile material) { }
    private record Contact(Piece piece, double coupling) { }
    private record Entry(Node node, double loss, double length, double score) implements Comparable<Entry> {
        public int compareTo(Entry other) {
            int c = Double.compare(score, other.score);
            if (c == 0) c = Long.compare(node.block.asLong(), other.node.block.asLong());
            return c == 0 ? Integer.compare(node.part, other.node.part) : c;
        }
    }
    private BlindVibrationPropagation() { }

    public static Impulse foot(Entity entity, Kind kind, double strength, long time) {
        if (kind != Kind.FOOTSTEP && kind != Kind.LANDING) throw new IllegalArgumentException("Foot impulse kind");
        // Posture modifies step generation, never an unrelated tool/machine event.
        double generated = strength * (kind == Kind.FOOTSTEP && entity.isShiftKeyDown() ? .25 : 1);
        return new Impulse(kind, entity.position(), entity.getBoundingBox(), entity.onGround(), null, null, generated, time);
    }

    public static Impulse block(Kind kind, BlockPos block, BlockState material, Vec3 approach, double strength, long time) {
        return new Impulse(kind, approach, null, false, block, material, strength, time);
    }

    public static Result receive(ServerLevel level, Impulse impulse, Entity receiver, int limit) {
        return receive(level, impulse, receiver.getBoundingBox(), receiver.onGround(), limit);
    }

    public static Result receive(ServerLevel level, Impulse impulse, AABB receiver, boolean grounded, int limit) {
        var context = new Query(level, impulse, receiver, Math.min(Math.max(0, limit), BlindArchitectPolicy.QUERY_LIMIT));
        if (impulse.emitted != level.getGameTime() || !Double.isFinite(impulse.strength) || impulse.strength <= 0)
            return context.result(Outcome.NO_SOURCE_CONTACT, 0, 0, 0, List.of());
        Vec3 source = impulse.block == null ? impulse.position : impulse.block.getCenter();
        if (source.distanceTo(receiver.getCenter()) > RANGE)
            return context.result(Outcome.OUT_OF_RANGE, 0, 0, 0, List.of());
        try {
            context.sources = impulse.footprint != null
                    ? context.support(impulse.footprint, impulse.grounded, true)
                    : context.struck();
            if (context.sources.isEmpty()) return context.absent(Outcome.NO_SOURCE_CONTACT);
            context.receivers = context.support(receiver, grounded, false);
            if (context.receivers.isEmpty()) return context.absent(Outcome.NO_RECEIVER_CONTACT);
            return context.search();
        } catch (Limit stop) {
            return context.result(Outcome.UNKNOWN_BUDGET, 0, 0, 0, List.of());
        }
    }

    private static final class Limit extends RuntimeException {
        private Limit() { super(null, null, false, false); }
    }

    private static final class Query {
        final ServerLevel level;
        final Impulse impulse;
        final AABB receiver;
        final int limit;
        int work;
        boolean unloaded, complex, attenuated;
        List<Contact> sources = List.of(), receivers = List.of();
        final Map<BlockPos, List<Piece>> geometry = new HashMap<>();

        Query(ServerLevel level, Impulse impulse, AABB receiver, int limit) {
            this.level = level; this.impulse = impulse; this.receiver = receiver; this.limit = limit;
        }
        void spend() { if (work >= limit) throw new Limit(); work++; }

        List<Piece> pieces(BlockPos position) {
            var cached = geometry.get(position);
            if (cached != null) return cached;
            spend();
            BlockPos block = position.immutable();
            if (!level.isInWorldBounds(block) || !level.hasChunkAt(block)) {
                unloaded = true; geometry.put(block, List.of()); return List.of();
            }
            // Destruction events carry their actual pre-destruction material. Only that root is frozen.
            var state = block.equals(impulse.block) && impulse.material != null ? impulse.material : level.getBlockState(block);
            var shape = state.getCollisionShape(level, block);
            if (shape.isEmpty()) { geometry.put(block, List.of()); return List.of(); }
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) cells *= Math.max(1, shape.getCoords(axis).size() - 1);
            if (cells > MAX_SHAPE_CELLS) { complex = true; geometry.put(block, List.of()); return List.of(); }
            var boxes = shape.toAabbs();
            if (boxes.size() > MAX_BOXES) { complex = true; geometry.put(block, List.of()); return List.of(); }
            var found = new ArrayList<Piece>();
            for (int i = 0; i < boxes.size(); i++) {
                spend();
                found.add(new Piece(new Node(block, i), boxes.get(i).move(block), BlindStructuralMaterials.of(state)));
            }
            geometry.put(block, List.copyOf(found));
            return geometry.get(block);
        }

        List<Contact> support(AABB body, boolean grounded, boolean source) {
            if (!grounded) return List.of();
            var contacts = new ArrayList<Contact>();
            // Fences may extend 1.5 blocks above their origin. Full footprint handles edges/multiple supports.
            for (int x = (int)Math.floor(body.minX + EPS); x <= (int)Math.floor(body.maxX - EPS); x++)
                for (int z = (int)Math.floor(body.minZ + EPS); z <= (int)Math.floor(body.maxZ - EPS); z++)
                    for (int y = (int)Math.floor(body.minY - 1.5 - EPS); y <= (int)Math.floor(body.minY + EPS); y++)
                        for (var piece : pieces(new BlockPos(x, y, z))) {
                            spend();
                            double area = overlap(body.minX, body.maxX, piece.box.minX, piece.box.maxX)
                                    * overlap(body.minZ, body.maxZ, piece.box.minZ, piece.box.maxZ);
                            if (Math.abs(piece.box.maxY - body.minY) <= EPS && area > EPS * EPS) {
                                double patch = Math.min(1, area / ((body.maxX - body.minX) * (body.maxZ - body.minZ)));
                                double coupling = source ? piece.material.footCushion() : piece.material.receiverCoupling();
                                contacts.add(new Contact(piece, coupling * Math.sqrt(patch)));
                            }
                        }
            return List.copyOf(contacts);
        }

        List<Contact> struck() {
            if (impulse.block == null) return List.of();
            Piece closest = null; double distance = Double.MAX_VALUE;
            for (var piece : pieces(impulse.block)) {
                spend();
                double d = distanceSquared(piece.box, impulse.position);
                if (d < distance) { distance = d; closest = piece; }
            }
            return closest == null ? List.of() : List.of(new Contact(closest, 1));
        }

        Result search() {
            Result direct=conductingCorridor();
            if(direct!=null)return direct;
            var frontier = new PriorityQueue<Entry>();
            var losses = new HashMap<Node, Double>();
            var parents = new HashMap<Node, Node>();
            var targets = new HashMap<Node, Contact>();
            for (var contact : receivers) targets.put(contact.piece.node, contact);
            for (var contact : sources) {
                double loss = -Math.log(contact.coupling);
                if (loss < losses.getOrDefault(contact.piece.node, Double.POSITIVE_INFINITY)) {
                    losses.put(contact.piece.node, loss);
                    frontier.add(new Entry(contact.piece.node, loss, 0, loss + SEARCH_GUIDANCE * heuristic(contact.piece)));
                }
            }
            double maxLoss = Math.log(impulse.strength / THRESHOLD);
            while (!frontier.isEmpty()) {
                Entry current = frontier.remove();
                if (current.loss > losses.getOrDefault(current.node, Double.POSITIVE_INFINITY)) continue;
                spend();
                if (current.loss > maxLoss) { attenuated = true; continue; }
                var contact = targets.get(current.node);
                if (contact != null) {
                    double received = impulse.strength * contact.coupling * Math.exp(-current.loss);
                    if (received >= THRESHOLD) {
                        // Reception is already established. Continuing to expand or
                        // queuing a terminal could exhaust the budget after finding it.
                        double totalLoss = current.loss - Math.log(contact.coupling);
                        var path = new ArrayList<BlockPos>(); Node at = current.node;
                        while (at != null) { path.add(at.block); at = parents.get(at); }
                        java.util.Collections.reverse(path);
                        return result(Outcome.DETECTED, received, current.length, totalLoss, path);
                    } else attenuated = true;
                }
                Piece piece = pieces(current.node.block).get(current.node.part);
                var neighbors = new ArrayList<Piece>(pieces(current.node.block));
                for (Direction direction : Direction.values()) {
                    BlockPos block = current.node.block.relative(direction);
                    if (block.getCenter().distanceTo(receiver.getCenter()) <= RANGE) neighbors.addAll(pieces(block));
                }
                for (var next : neighbors) {
                    if (next.node.equals(current.node)) continue;
                    spend();
                    double area = contactArea(piece.box, next.box);
                    if (area <= EPS * EPS) continue;
                    double length = Math.max(.001, piece.box.getCenter().distanceTo(next.box.getCenter()));
                    double transfer = piece.node.block.equals(next.node.block) ? 1
                            : BlindStructuralMaterials.interfaceTransfer(piece.material, next.material, coverage(piece.box, next.box, area));
                    double loss = current.loss + length * ((piece.material.damping() + next.material.damping()) * .5 + SPREADING_LOSS) - Math.log(transfer);
                    if (loss > maxLoss) { attenuated = true; continue; }
                    if (loss >= losses.getOrDefault(next.node, Double.POSITIVE_INFINITY)) continue;
                    losses.put(next.node, loss); parents.put(next.node, current.node);
                    // Guidance changes expansion order only. Loss/threshold/contact
                    // checks stay physical; an unfinished search still returns UNKNOWN.
                    frontier.add(new Entry(next.node, loss, current.length + length, loss + SEARCH_GUIDANCE * heuristic(next)));
                }
            }
            return absent(attenuated ? Outcome.BELOW_THRESHOLD : Outcome.NO_PATH);
        }
        /** Try a short conducting route before expanding the surrounding volume.
         * Every joint and attenuation term is identical to the general search.
         * Failure gives no negative conclusion; the remaining budget runs that search.
         */
        Result conductingCorridor() {
            Contact root=null,target=null;double best=Double.POSITIVE_INFINITY;
            for(var a:sources)for(var b:receivers) {
                double score=a.piece.box.getCenter().distanceTo(b.piece.box.getCenter())*.135-Math.log(a.coupling*b.coupling);
                if(score<best) {best=score;root=a;target=b;}
            }
            if(root==null||target==null)return null;
            final BlockPos targetBlock=target.piece.node.block;
            Piece current=root.piece;double loss=-Math.log(root.coupling),length=0;
            var path=new ArrayList<BlockPos>();path.add(current.node.block);
            int stop=Math.min(limit-16,work+Math.max(0,limit/2));
            while(work<stop) {
                spend();
                if(current.node.equals(target.piece.node)) {
                    double received=impulse.strength*target.coupling*Math.exp(-loss);
                    return received>=THRESHOLD?result(Outcome.DETECTED,received,length,loss-Math.log(target.coupling),path):null;
                }
                var directions=new ArrayList<Direction>();
                for(var direction:Direction.values())
                    if(current.node.block.relative(direction).distManhattan(target.piece.node.block)<current.node.block.distManhattan(target.piece.node.block))
                        directions.add(direction);
                directions.sort(java.util.Comparator.comparingDouble(d->
                        currentDistance(path.getLast().relative(d),targetBlock)));
                Piece chosen=null;double nextLoss=0,nextLength=0;
                for(var direction:directions) {
                    if(work>=stop) return null;
                    for(var next:pieces(current.node.block.relative(direction))) {
                        spend();double area=contactArea(current.box,next.box);
                        if(area<=EPS*EPS)continue;
                        double step=Math.max(.001,current.box.getCenter().distanceTo(next.box.getCenter()));
                        double transfer=BlindStructuralMaterials.interfaceTransfer(current.material,next.material,coverage(current.box,next.box,area));
                        double total=loss+step*((current.material.damping()+next.material.damping())*.5+SPREADING_LOSS)-Math.log(transfer);
                        if(total>Math.log(impulse.strength/THRESHOLD))continue;
                        if(chosen==null||total+heuristic(next)<nextLoss+heuristic(chosen)) {chosen=next;nextLoss=total;nextLength=step;}
                    }
                    if(chosen!=null)break;
                }
                if(chosen==null)return null;
                current=chosen;loss=nextLoss;length+=nextLength;path.add(current.node.block);
            }
            return null;
        }
        private static double currentDistance(BlockPos a,BlockPos b) { return a.distSqr(b); }
        double heuristic(Piece piece) {
            // A foot spanning several supports has unequal contact patches. Include
            // the unavoidable receiver loss so a tiny nearer patch does not attract
            // expansions away from a well-coupled support beside it.
            return receivers.stream().mapToDouble(c -> piece.box.getCenter().distanceTo(c.piece.box.getCenter())
                    * (BlindStructuralMaterials.MIN_DAMPING + SPREADING_LOSS) - Math.log(c.coupling)).min().orElse(0);
        }
        Result absent(Outcome fallback) {
            return result(complex ? Outcome.UNKNOWN_GEOMETRY : unloaded ? Outcome.UNKNOWN_UNLOADED : fallback, 0, 0, 0, List.of());
        }
        Result result(Outcome outcome, double strength, double length, double loss, List<BlockPos> path) {
            return new Result(outcome, strength, length, loss, work,
                    sources.stream().map(c -> c.piece.node.block).distinct().toList(),
                    receivers.stream().map(c -> c.piece.node.block).distinct().toList(), path);
        }
    }

    private static double overlap(double a, double b, double c, double d) { return Math.max(0, Math.min(b, d) - Math.max(a, c)); }
    private static double distanceSquared(AABB box, Vec3 p) {
        double x = Math.max(box.minX - p.x, Math.max(0, p.x - box.maxX));
        double y = Math.max(box.minY - p.y, Math.max(0, p.y - box.maxY));
        double z = Math.max(box.minZ - p.z, Math.max(0, p.z - box.maxZ));
        return x*x + y*y + z*z;
    }
    /** Positive face area (or overlapping occupied volume); edges/points do not create a joint. */
    static double contactArea(AABB a, AABB b) {
        double x = overlap(a.minX,a.maxX,b.minX,b.maxX), y = overlap(a.minY,a.maxY,b.minY,b.maxY), z = overlap(a.minZ,a.maxZ,b.minZ,b.maxZ);
        if (x > EPS && y > EPS && z > EPS) return Math.min(1, Math.max(x*y, Math.max(x*z,y*z)));
        if (Math.abs(a.maxX-b.minX)<=EPS || Math.abs(b.maxX-a.minX)<=EPS) return y*z;
        if (Math.abs(a.maxY-b.minY)<=EPS || Math.abs(b.maxY-a.minY)<=EPS) return x*z;
        if (Math.abs(a.maxZ-b.minZ)<=EPS || Math.abs(b.maxZ-a.minZ)<=EPS) return x*y;
        return 0;
    }
    private static double coverage(AABB a, AABB b, double area) {
        if (Math.abs(a.maxX-b.minX)<=EPS || Math.abs(b.maxX-a.minX)<=EPS)
            return area / Math.max(a.getYsize()*a.getZsize(),b.getYsize()*b.getZsize());
        if (Math.abs(a.maxY-b.minY)<=EPS || Math.abs(b.maxY-a.minY)<=EPS)
            return area / Math.max(a.getXsize()*a.getZsize(),b.getXsize()*b.getZsize());
        if (Math.abs(a.maxZ-b.minZ)<=EPS || Math.abs(b.maxZ-a.minZ)<=EPS)
            return area / Math.max(a.getXsize()*a.getYsize(),b.getXsize()*b.getYsize());
        return 1;
    }
}
