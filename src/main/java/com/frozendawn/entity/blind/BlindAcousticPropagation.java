package com.frozendawn.entity.blind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded direct-return model in one authored acoustic band. Both legs, reflector
 * area, emission direction and nearby static returns contribute to the measurement.
 * This is geometric acoustics, not a waveform simulation or a calibrated animal.
 * Bent/diffracted paths, transmission and coherent multipath are not modeled here.
 */
public final class BlindAcousticPropagation {
    public enum Outcome { LOCALIZED, DETECTED, BELOW_THRESHOLD, NO_DIRECT_RETURN, OUT_OF_RANGE,
        UNKNOWN_BUDGET, UNKNOWN_UNLOADED, UNKNOWN_GEOMETRY }
    public static final double SOUND_SPEED = 343, THRESHOLD = .025;
    public static final int PROCESSING_TICKS = 2;
    private static final double SOURCE_ENERGY = 65536, BODY_REFLECTION = .45, AIR_LOSS = .012;
    private static final double NOISE_FLOOR = .015, EPS = 1e-6;
    private static final int MAX_BOXES = 24, MAX_SHAPE_CELLS = 4096;

    /** Only this bounded measurement is suitable for behavior; it contains no true target position. */
    public record Measurement(BlockPos region, int uncertainty, double bearing, double elevation,
                              double range, double bearingHalfWidth, double rangeHalfWidth,
                              double confidence, double qualityCeiling) { }
    /** The frozen reflector point is retained solely to revalidate propagation, never to steer. */
    public record Result(Outcome outcome, long emitted, Vec3 emitter, Vec3 reflector,
                         double outwardLength, double returnLength, double arrivalMillis,
                         double directivity, double reflectedArea, double received, double clutter,
                         double signalToNoise, int work, Measurement measurement) {
        public boolean detected() { return outcome == Outcome.LOCALIZED || outcome == Outcome.DETECTED; }
        public boolean unknown() { return outcome.name().startsWith("UNKNOWN_"); }
        public int arrivalTicks() { return (int)Math.ceil(arrivalMillis / 50.0); }
        public long due() { return emitted + arrivalTicks() + PROCESSING_TICKS; }
    }
    public record Validation(boolean open, Outcome outcome, int work) { }
    private record Piece(AABB box, double reflection) { }
    private record Hit(Vec3 point, double reflection, double incidence) { }
    private static final class Incomplete extends RuntimeException {
        final Outcome outcome;
        Incomplete(Outcome outcome) { super(null, null, false, false); this.outcome = outcome; }
    }
    private BlindAcousticPropagation() { }

    /** Smooth authored power pattern, with a weak rear lobe rather than a visibility cone. */
    public static double directivity(Vec3 forward, Vec3 direction) {
        double cosine = Math.max(-1, Math.min(1, forward.normalize().dot(direction.normalize())));
        return .001875 + .998125 * Math.pow((1 + cosine) * .5, 3);
    }
    public static Vec3 direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }
    private static double energy(double outward, double returning, double gain, double area, double reflection) {
        // One inverse-square spreading term and air loss for each leg.
        return SOURCE_ENERGY * gain * area * reflection * Math.exp(-AIR_LOSS * (outward + returning))
                / ((1 + outward * outward) * (1 + returning * returning));
    }
    private static double quantize(double value, double width) { return Math.rint(value / width) * width; }

    /** The body is private scene geometry. No entity identity or feet coordinate enters the measurement. */
    public static Result echo(ServerLevel level, Vec3 emitter, float headYaw, float headPitch,
                              AABB body, long emitted, int limit) {
        var q = new Query(level, limit);
        Vec3 target = null;
        double distance = 0, gain = 0, area = 0, received = 0, clutter = 0;
        try {
            if (emitter.distanceTo(body.getCenter()) > BlindArchitectPolicy.ECHO_RANGE)
                return empty(Outcome.OUT_OF_RANGE, emitted, emitter, q.work);
            // A few exposed patches approximate the body surface. Never route around an obstruction.
            int exposed=0;
            for (double height : new double[]{.85, .50, .25}) {
                Vec3 center = new Vec3((body.minX + body.maxX) * .5,
                        body.minY + body.getYsize() * height, (body.minZ + body.maxZ) * .5);
                Vec3 patch = body.clip(emitter, center).orElse(center);
                if (q.ray(emitter, patch) == null && q.ray(patch, emitter) == null) {
                    exposed++; if(target==null)target=patch;
                }
            }
            if (target == null) return empty(Outcome.NO_DIRECT_RETURN, emitted, emitter, q.work);
            Vec3 delta = target.subtract(emitter), unit = delta.normalize();
            distance = delta.length();
            gain = directivity(direction(headYaw, headPitch), unit);
            // Project the frozen box onto the incident direction; clothing is not classified.
            area = exposed / 3.0 * Math.min(1.5, Math.abs(unit.x) * body.getYsize() * body.getZsize()
                    + Math.abs(unit.y) * body.getXsize() * body.getZsize()
                    + Math.abs(unit.z) * body.getXsize() * body.getYsize());
            received = energy(distance, distance, gain, area, BODY_REFLECTION);
            double yaw = Math.toDegrees(Math.atan2(-unit.x, unit.z));
            double pitch = Math.toDegrees(Math.asin(-unit.y));
            // A finite bearing/range resolution, independent of block/target coordinates.
            double bearingBin = 12, rangeBin = .75;
            double measuredYaw = quantize(yaw, bearingBin), measuredPitch = quantize(pitch, bearingBin);
            double measuredRange = quantize((2 * distance / SOUND_SPEED) * SOUND_SPEED / 2, rangeBin);
            // Sample static scene returns in the same angular and delay neighborhood. A
            // neighboring wall can mask a body; reachability alone cannot supply confidence.
            for (double[] offset : new double[][]{{0,0},{-6,0},{6,0},{0,-6},{0,6}}) {
                Vec3 ray = direction(measuredYaw + offset[0], measuredPitch + offset[1]);
                Hit hit = q.ray(emitter, emitter.add(ray.scale(Math.min(24, distance + 1.5))));
                if (hit == null) continue;
                double length = emitter.distanceTo(hit.point);
                if (Math.abs(length - distance) > 1.5) continue;
                // Reciprocal frozen static geometry has the same clear return leg.
                // The near-side point avoids treating the reflecting surface as an occluder.
                Vec3 outside = hit.point.subtract(ray.scale(EPS * 4));
                if (q.ray(outside, emitter) != null) continue;
                clutter = Math.max(clutter, energy(length, length,
                        directivity(direction(headYaw, headPitch), ray), 1.5,
                        hit.reflection * hit.incidence * hit.incidence));
            }
            double snr = received / (NOISE_FLOOR + clutter);
            double arrival = 1000 * (distance + distance) / SOUND_SPEED;
            if (received < THRESHOLD || snr < .75)
                return new Result(Outcome.BELOW_THRESHOLD, emitted, emitter, target, distance, distance,
                        arrival, gain, area, received, clutter, snr, q.work, null);
            boolean localized = received >= .10 && snr >= 3;
            if (!localized) { bearingBin = 24; rangeBin = 1.5; }
            measuredYaw = quantize(yaw, bearingBin); measuredPitch = quantize(pitch, bearingBin);
            measuredRange = quantize(distance, rangeBin);
            Vec3 estimate = emitter.add(direction(measuredYaw, measuredPitch).scale(measuredRange));
            // The estimated reflecting patch is mapped to a nominal body support region;
            // local terrain later resolves height. The sampled body's feet are never reused.
            BlockPos region = BlockPos.containing(estimate.add(0, -1.35, 0));
            int uncertainty = Math.max(1, (int)Math.ceil(rangeBin * .5
                    + measuredRange * Math.sin(Math.toRadians(bearingBin * .5))));
            double ceiling = localized ? .90 : .55;
            double confidence = localized ? .85 : .40;
            var measurement = new Measurement(region, uncertainty, measuredYaw, measuredPitch, measuredRange,
                    bearingBin * .5, rangeBin * .5, confidence, ceiling);
            return new Result(localized ? Outcome.LOCALIZED : Outcome.DETECTED, emitted, emitter, target,
                    distance, distance, arrival, gain, area, received, clutter, snr, q.work, measurement);
        } catch (Incomplete stop) {
            return new Result(stop.outcome, emitted, emitter, target, distance, distance,
                    1000 * (2 * distance) / SOUND_SPEED, gain, area, received, clutter, 0, q.work, null);
        }
    }

    /** Rechecks the frozen round trip and receiver displacement, without reading a live target. */
    public static Validation validate(ServerLevel level, Result result, Vec3 receiver, int limit) {
        var q = new Query(level, limit);
        // Ordinary walking during flight and processing must not erase a valid
        // echo. Bound ego movement by the authored locomotion allowance and the
        // frozen delay, then recheck both physical legs to the actual receiver.
        double motionAllowance=Math.min(2,.25*(result.arrivalTicks()+PROCESSING_TICKS));
        if (!result.detected() || result.reflector == null || receiver.distanceToSqr(result.emitter) > motionAllowance*motionAllowance)
            return new Validation(false, Outcome.NO_DIRECT_RETURN, 0);
        try {
            boolean open = q.ray(result.emitter, result.reflector) == null && q.ray(result.reflector, receiver) == null;
            return new Validation(open, open ? result.outcome : Outcome.NO_DIRECT_RETURN, q.work);
        } catch (Incomplete stop) { return new Validation(false, stop.outcome, q.work); }
    }
    private static Result empty(Outcome outcome, long emitted, Vec3 emitter, int work) {
        return new Result(outcome, emitted, emitter, null, 0,0,0,0,0,0,0,0,work,null);
    }

    private static final class Query {
        final ServerLevel level;
        final int limit;
        final Map<BlockPos,List<Piece>> geometry = new HashMap<>();
        int work;
        Query(ServerLevel level, int limit) { this.level = level; this.limit = Math.min(512, Math.max(0, limit)); }
        void spend() { if (work >= limit) throw new Incomplete(Outcome.UNKNOWN_BUDGET); work++; }
        List<Piece> pieces(BlockPos pos) {
            if (geometry.containsKey(pos)) return geometry.get(pos);
            spend();
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) throw new Incomplete(Outcome.UNKNOWN_UNLOADED);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) throw new Incomplete(Outcome.UNKNOWN_GEOMETRY);
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) { geometry.put(pos, List.of()); return List.of(); }
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) cells *= Math.max(1, shape.getCoords(axis).size() - 1);
            if (cells > MAX_SHAPE_CELLS) throw new Incomplete(Outcome.UNKNOWN_GEOMETRY);
            var boxes = shape.toAabbs();
            if (boxes.size() > MAX_BOXES) throw new Incomplete(Outcome.UNKNOWN_GEOMETRY);
            double reflection = state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) ? .08
                    : state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) ? .45 : .80;
            var found = new java.util.ArrayList<Piece>();
            for (AABB box : boxes) {
                spend();
                // The bounded neighbor stencil handles vanilla's 1.5-high fences.
                if (box.minX < -EPS || box.minY < -EPS || box.minZ < -EPS
                        || box.maxX > 1 + EPS || box.maxY > 1.5 + EPS || box.maxZ > 1 + EPS)
                    throw new Incomplete(Outcome.UNKNOWN_GEOMETRY);
                found.add(new Piece(box.move(pos), reflection));
            }
            geometry.put(pos, List.copyOf(found)); return geometry.get(pos);
        }
        Hit ray(Vec3 from, Vec3 to) {
            // Enumerating each short segment's full cell bounds also covers cells
            // crossed only near an edge; no point-sampling shortcut through slabs.
            int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) * 4));
            var visited = new HashSet<BlockPos>();
            Vec3 previous = from;
            Hit best = null; double nearest = Double.POSITIVE_INFINITY;
            for (int step = 1; step <= steps; step++) {
                Vec3 next = from.lerp(to, step / (double)steps);
                int minX=(int)Math.floor(Math.min(previous.x,next.x)), maxX=(int)Math.floor(Math.max(previous.x,next.x));
                int minY=(int)Math.floor(Math.min(previous.y,next.y))-1, maxY=(int)Math.floor(Math.max(previous.y,next.y));
                int minZ=(int)Math.floor(Math.min(previous.z,next.z)), maxZ=(int)Math.floor(Math.max(previous.z,next.z));
                for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
                    BlockPos cell = new BlockPos(x,y,z);
                    if (!visited.add(cell)) continue;
                    for (Piece piece : pieces(cell)) {
                        spend();
                        if (piece.box.contains(from)) return new Hit(from,piece.reflection,1);
                        var clipped = piece.box.clip(from,to);
                        if (clipped.isEmpty()) continue;
                        Vec3 point = clipped.get(); double distance = from.distanceToSqr(point);
                        if (distance < nearest) {
                            nearest = distance;
                            Vec3 d=to.subtract(from).normalize(); AABB b=piece.box;
                            double incidence = Math.abs(point.x-b.minX)<EPS || Math.abs(point.x-b.maxX)<EPS ? Math.abs(d.x)
                                    : Math.abs(point.y-b.minY)<EPS || Math.abs(point.y-b.maxY)<EPS ? Math.abs(d.y) : Math.abs(d.z);
                            best = new Hit(point,piece.reflection,incidence);
                        }
                    }
                }
                // All potentially closer cells were examined before this segment ends.
                if (best != null && nearest <= from.distanceToSqr(next) + EPS) return best;
                previous = next;
            }
            return best;
        }
    }
}
