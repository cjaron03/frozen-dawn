package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Lossy event locations. No block-change listener, structure parser or stored UNKNOWN label. */
final class WorldModel {
    static final int MAX_POINTS = 64, MAX_CELLS = 128, MAX_SHELTERS = 4, MAX_COVERED = 16;
    static final List<String> BEARINGS = List.of("RETREAT_BEARING_N", "RETREAT_BEARING_E", "RETREAT_BEARING_S", "RETREAT_BEARING_W");
    private final Map<String, Point> points = new LinkedHashMap<>();
    private final Map<String, Long> cells = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<BlockPos, Long>> shelters = new LinkedHashMap<>();
    // A reload must never join positions observed in different sessions into a crossing.
    private final Map<UUID, Presence> presence = new LinkedHashMap<>();

    record Presence(String dimension, BlockPos position, boolean covered, long time) { }

    Presence sample(UUID observer, String dimension, BlockPos pos, boolean covered, long now) {
        Presence previous = presence.remove(observer);
        presence.put(observer, new Presence(dimension, pos.immutable(), covered, now));
        trimOldestInsertion(presence, BeliefStore.MAX_CONTACTS);
        seen(dimension, pos, now);
        if (covered) {
            var locations = shelters.computeIfAbsent(dimension, k -> new LinkedHashMap<>());
            if (!locations.isEmpty() && center(dimension).distSqr(pos) > 32 * 32) locations.clear();
            locations.remove(pos); locations.put(pos.immutable(), now);
            trimOldestInsertion(locations, MAX_COVERED);
            trimOldestInsertion(shelters, MAX_SHELTERS);
        }
        return previous;
    }

    void forget(UUID observer) { presence.remove(observer); }
    boolean recent(UUID observer, long now) {
        Presence sample = presence.get(observer);
        return sample != null && now >= sample.time() && now - sample.time() <= 200;
    }

    void seen(String dimension, BlockPos pos, long now) {
        cells.put(cell(dimension, pos), now);
        if (cells.size() > MAX_CELLS) cells.remove(cells.entrySet().stream()
                .min(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey())).orElseThrow().getKey());
    }

    boolean known(String dimension, BlockPos pos) { return cells.containsKey(cell(dimension, pos)); }
    private static String cell(String dimension, BlockPos pos) { return dimension + "/" + (pos.getX() >> 4) + "/" + (pos.getZ() >> 4); }
    boolean empty() { return points.isEmpty() && cells.isEmpty(); }

    BlockPos center(String dimension) {
        var locations = shelters.get(dimension);
        if (locations == null || locations.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        for (BlockPos p : locations.keySet()) { x += p.getX() + .5; y += p.getY(); z += p.getZ() + .5; }
        return BlockPos.containing(x / locations.size(), y / locations.size(), z / locations.size());
    }

    static String bearing(BlockPos center, BlockPos point) {
        if (center == null) return null;
        int x = point.getX() - center.getX(), z = point.getZ() - center.getZ();
        if (x == 0 && z == 0) return null;
        return "RETREAT_BEARING_" + (Math.abs(x) >= Math.abs(z) ? x >= 0 ? "E" : "W" : z >= 0 ? "S" : "N");
    }

    void access(String dimension, BlockPos outside, BlockPos inside, ObservedEvidence evidence) {
        seen(dimension, outside, evidence.time()); seen(dimension, inside, evidence.time());
        Point point = point("ACCESS_POINT", dimension, outside);
        point.inside = inside.immutable();
        point.observe("OPEN", evidence, .2);
    }

    void event(String label, String dimension, BlockPos pos, ObservedEvidence evidence) {
        if (!List.of("DANGER_ZONE", "HEAT_SOURCE").contains(label)) throw new IllegalArgumentException(label);
        seen(dimension, pos, evidence.time());
        point(label, dimension, pos).observe("OBSERVED", evidence, .2);
    }

    boolean obstructed(String dimension, BlockPos outside, ObservedEvidence evidence) {
        Point point = points.get(key("ACCESS_POINT", dimension, outside));
        if (point == null || point.state.equals("BLOCKED")) return false;
        point.previousConfidence = Math.max(0, point.confidence(evidence.time()) - .65);
        point.contradictions = BeliefPolicy.increment(point.contradictions);
        point.observe("BLOCKED", evidence, .9);
        return true;
    }

    void survey(MaeveDirector.AccessHint hint, String state, ObservedEvidence evidence) {
        Point point = points.get(key("ACCESS_POINT", evidence.dimension(), hint.outside()));
        if (point == null || !List.of("OPEN", "BLOCKED").contains(state)) return;
        if (!point.state.equals(state)) {
            point.previousConfidence = point.confidence(evidence.time());
            point.contradictions = BeliefPolicy.increment(point.contradictions);
        }
        point.inside = hint.inside();
        point.observe(state, evidence, .9);
        seen(evidence.dimension(), hint.outside(), evidence.time());
    }

    MaeveDirector.SpatialTarget resolve(String dimension, String pattern, BlockPos observer, long now) {
        BlockPos centroid = center(dimension);
        if (centroid == null) return null;
        return points.values().stream().filter(p -> p.label.equals("ACCESS_POINT") && p.dimension.equals(dimension)
                        && p.state.equals("OPEN") && p.inside != null && p.confidence(now) > 0.05
                        && p.position.distSqr(centroid) <= 32 * 32 && p.position.distSqr(observer) <= 24 * 24
                        && pattern.equals(bearing(centroid, p.position)))
                .min(Comparator.comparingDouble((Point p) -> p.position.distSqr(observer)).thenComparing(p -> p.key))
                .map(p -> new MaeveDirector.SpatialTarget(p.inside, p.position)).orElse(null);
    }

    List<BlockPos> dangers(String dimension, BlockPos origin, long now) {
        return points.values().stream().filter(p -> p.label.equals("DANGER_ZONE") && p.dimension.equals(dimension)
                        && p.confidence(now) >= .2 && p.position.distSqr(origin) <= 32 * 32)
                .sorted(Comparator.comparingDouble((Point p) -> p.position.distSqr(origin)).thenComparing(p -> p.key))
                .limit(8).map(p -> p.position).toList();
    }

    List<MaeveDirector.WorldPointSnapshot> snapshot(long now) {
        return points.values().stream().sorted(Comparator.comparing(p -> p.key)).map(p -> p.snapshot(now)).toList();
    }

    List<String> unknown() {
        List<String> result = new ArrayList<>();
        for (String dimension : shelters.keySet().stream().sorted().toList()) {
            BlockPos center = center(dimension);
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos probe = center.relative(direction, 16);
                if (!known(dimension, probe)) result.add(dimension + " " + direction.getName().toUpperCase(java.util.Locale.ROOT)
                        + " cell=" + (probe.getX() >> 4) + "," + (probe.getZ() >> 4));
            }
        }
        return List.copyOf(result);
    }

    private Point point(String label, String dimension, BlockPos pos) {
        String key = key(label, dimension, pos);
        Point point = points.get(key);
        if (point == null) {
            if (points.size() == MAX_POINTS) points.remove(points.values().stream()
                    .min(Comparator.comparingLong((Point p) -> p.time).thenComparing(p -> p.key)).orElseThrow().key);
            point = new Point(label, dimension, pos); points.put(key, point);
        }
        return point;
    }

    private static String key(String label, String dimension, BlockPos pos) { return label + "/" + dimension + "/" + pos.asLong(); }
    private static <K, V> void trimOldestInsertion(Map<K, V> map, int cap) { if (map.size() > cap) map.remove(map.keySet().iterator().next()); }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag(); ListTag entries = new ListTag();
        points.values().forEach(p -> entries.add(p.save())); tag.put("points", entries);
        ListTag areas = new ListTag();
        shelters.forEach((dimension, positions) -> positions.forEach((pos, time) -> {
            CompoundTag value = new CompoundTag(); value.putString("dimension", dimension);
            value.putLong("position", pos.asLong()); value.putLong("time", time); areas.add(value);
        })); tag.put("covered", areas);
        ListTag observed = new ListTag(); cells.forEach((cell, time) -> {
            CompoundTag value = new CompoundTag(); value.putString("cell", cell); value.putLong("time", time); observed.add(value);
        }); tag.put("observed", observed); return tag;
    }

    static WorldModel load(CompoundTag tag) {
        WorldModel model = new WorldModel();
        for (Tag raw : tag.getList("points", Tag.TAG_COMPOUND)) {
            if (model.points.size() == MAX_POINTS) break;
            Point point = Point.load((CompoundTag) raw);
            if (point != null) model.points.put(point.key, point);
        }
        int retained = 0;
        for (Tag raw : tag.getList("covered", Tag.TAG_COMPOUND)) {
            if (retained++ == MAX_SHELTERS * MAX_COVERED) break;
            CompoundTag p = (CompoundTag) raw; String dim = p.getString("dimension");
            if (ResourceLocation.tryParse(dim) == null) continue;
            if (!model.shelters.containsKey(dim) && model.shelters.size() == MAX_SHELTERS) continue;
            var positions = model.shelters.computeIfAbsent(dim, k -> new LinkedHashMap<>());
            if (positions.size() < MAX_COVERED) positions.put(BlockPos.of(p.getLong("position")), Math.max(0, p.getLong("time")));
        }
        for (Tag raw : tag.getList("observed", Tag.TAG_COMPOUND)) {
            if (model.cells.size() == MAX_CELLS) break;
            CompoundTag value = (CompoundTag) raw; String cell = value.getString("cell");
            if (cell.length() <= 160) model.cells.put(cell, Math.max(0, value.getLong("time")));
        }
        return model;
    }

    private static final class Point {
        final String label, dimension, key;
        final BlockPos position;
        BlockPos inside;
        String state = "UNVERIFIED";
        double confidence, previousConfidence;
        int evidence, contradictions;
        long time;
        UUID encounter;
        final List<ObservedEvidence> provenance = new ArrayList<>();
        Point(String label, String dimension, BlockPos pos) {
            this.label = label; this.dimension = dimension; position = pos.immutable(); key = key(label, dimension, pos);
        }
        double confidence(long now) { return BeliefPolicy.decay(confidence, time, time, now); }
        void observe(String observedState, ObservedEvidence event, double weight) {
            boolean replacement = !state.equals(observedState);
            if (replacement) { confidence = 0; evidence = 0; encounter = null; }
            if (!event.encounter().equals(encounter)) {
                confidence = BeliefPolicy.clamp(confidence(event.time()) + weight);
                evidence = BeliefPolicy.increment(evidence);
            } else confidence = confidence(event.time());
            state = observedState; encounter = event.encounter(); time = event.time();
            provenance.removeIf(p -> p.encounter().equals(encounter) && p.action().equals(event.action()));
            provenance.add(event); if (provenance.size() > 8) provenance.removeFirst();
        }
        MaeveDirector.WorldPointSnapshot snapshot(long now) {
            return new MaeveDirector.WorldPointSnapshot(label, dimension, position, inside, state, confidence(now),
                    previousConfidence, evidence, contradictions, time, provenance.stream().map(ObservedEvidence::snapshot).toList());
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putString("label", label); tag.putString("dimension", dimension);
            tag.putLong("position", position.asLong()); if (inside != null) tag.putLong("inside", inside.asLong());
            tag.putString("state", state); tag.putDouble("confidence", confidence); tag.putDouble("previousConfidence", previousConfidence);
            tag.putInt("evidence", evidence); tag.putInt("contradictions", contradictions); tag.putLong("time", time);
            if (encounter != null) tag.putUUID("encounter", encounter);
            ListTag events = new ListTag(); provenance.forEach(p -> events.add(p.save())); tag.put("provenance", events); return tag;
        }
        static Point load(CompoundTag tag) {
            String label = tag.getString("label"), dim = tag.getString("dimension");
            if (!List.of("ACCESS_POINT", "DANGER_ZONE", "HEAT_SOURCE").contains(label) || ResourceLocation.tryParse(dim) == null) return null;
            Point p = new Point(label, dim, BlockPos.of(tag.getLong("position")));
            if (tag.contains("inside", Tag.TAG_LONG)) p.inside = BlockPos.of(tag.getLong("inside"));
            p.state = tag.getString("state"); p.confidence = BeliefPolicy.clamp(tag.getDouble("confidence"));
            p.previousConfidence = BeliefPolicy.clamp(tag.getDouble("previousConfidence"));
            p.evidence = Math.max(0, tag.getInt("evidence")); p.contradictions = Math.max(0, tag.getInt("contradictions"));
            p.time = Math.max(0, tag.getLong("time")); if (tag.hasUUID("encounter")) p.encounter = tag.getUUID("encounter");
            for (Tag raw : tag.getList("provenance", Tag.TAG_COMPOUND)) {
                if (p.provenance.size() == 8) break;
                ObservedEvidence event = ObservedEvidence.load((CompoundTag) raw); if (event != null) p.provenance.add(event);
            }
            if (p.provenance.isEmpty() || p.encounter == null || p.evidence == 0) return null;
            if (label.equals("ACCESS_POINT")) {
                if (p.inside == null || !List.of("OPEN", "BLOCKED").contains(p.state)
                        || p.inside.distSqr(p.position) > 16) return null;
            } else if (!p.state.equals("OBSERVED")) return null;
            return p;
        }
    }
}
