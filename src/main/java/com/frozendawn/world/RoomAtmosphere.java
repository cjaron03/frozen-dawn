package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.data.RoomIdentityState;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded geometric seals and persistent breach/recovery history. No chunk loading. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RoomAtmosphere {
    public static final int REFILL_TICKS = 100;
    private static final int MAX_CELLS = 12_000, MAX_ROOMS = 256;
    private static final int ACTIVITY_LEASE_TICKS = 40, SOURCE_RETRY_TICKS = 200;
    public enum Seal { SEALED, OPEN, UNKNOWN }
    /** One oriented air-to-wall contact. Several faces may share a physical wall block. */
    public record BoundaryFace(BlockPos airCell, Direction outwardDirection) {
        public BoundaryFace {
            airCell = Objects.requireNonNull(airCell).immutable();
            Objects.requireNonNull(outwardDirection);
        }
        public BlockPos wallCell() { return airCell.relative(outwardDirection); }
    }
    /** Complete boundary faces are exposed only for SEALED geometry, never a partial flood fill. */
    public record Geometry(Seal seal, Set<BlockPos> cells, Set<BlockPos> walls, Set<BoundaryFace> boundaryFaces) {
        public Geometry {
            cells = Set.copyOf(cells); walls = Set.copyOf(walls); boundaryFaces = Set.copyOf(boundaryFaces);
        }
    }
    private static final Map<ServerLevel, Index> LEVELS = new WeakHashMap<>();
    private static final class Room {
        Geometry geometry; final long id; long refillStart = -1, lastUsed, checked;
        BlockPos changed; boolean uncertain; long activeUntil = -1;
        BlockPos lastQueryPos; long lastQueryTick = -1; String lastQueryReason = "none";
        com.frozendawn.network.RoomRecoveryPayload.Stage recoveryNotice;
        Room(long id, Geometry geometry, long tick) {
            this.id = id; this.geometry = geometry; lastUsed = checked = tick;
        }
    }
    public record RoomView(long id, Geometry geometry) {}
    private record Negative(long tick, Seal seal) {}
    private static final class Activity {
        final WeakReference<BlockEntity> source;
        long heartbeat, retryAt;
        Activity(BlockEntity source, long tick) { this.source = new WeakReference<>(source); heartbeat = tick; }
    }
    private static final class Index {
        final Set<Room> rooms = new LinkedHashSet<>();
        final Map<BlockPos, Room> cells = new HashMap<>();
        final Map<BlockPos, Set<Room>> walls = new HashMap<>();
        final Map<BlockPos, Negative> negative = new HashMap<>();
        final ArrayDeque<Room> work = new ArrayDeque<>();
        final Map<BlockPos, Activity> activity = new HashMap<>();
        final ArrayDeque<BlockPos> sourceWork = new ArrayDeque<>();
        long geometryChanges, materialChanges, airChanges; RoomChangeEvent.Type lastChange;
    }
    private RoomAtmosphere() {}

    public static boolean isPassage(Level level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
            return state.getValue(BlockStateProperties.OPEN);
        // A fence, gate, leaf canopy or stair is not an airtight wall.
        if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof WallBlock || state.is(net.minecraft.tags.BlockTags.LEAVES)) return true;
        return state.isAir() || !Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    public static Geometry inspect(ServerLevel level, BlockPos origin) {
        return inspect(level,origin,false,MAX_CELLS);
    }
    public static Geometry inspectAirlockPartition(ServerLevel level, BlockPos origin, int limit) {
        return inspect(level,origin,true,limit);
    }
    private static Geometry inspect(ServerLevel level, BlockPos origin, boolean partitionDoors, int limit) {
        if (!level.isLoaded(origin) || !isPassage(level, origin, level.getBlockState(origin)))
            return new Geometry(Seal.UNKNOWN, Set.of(), Set.of(), Set.of());
        var queue = new ArrayDeque<BlockPos>(); var cells = new HashSet<BlockPos>(); var walls = new HashSet<BlockPos>();
        var faces = new HashSet<BoundaryFace>();
        queue.add(origin.immutable()); cells.add(origin.immutable());
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            // Motion height includes full glass roofs, unlike canSeeSky().
            if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()))
                return new Geometry(Seal.OPEN, cells, walls, Set.of());
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (cells.contains(next)) continue;
                if (walls.contains(next)) { faces.add(new BoundaryFace(pos, direction)); continue; }
                if (!level.isLoaded(next)) return new Geometry(Seal.UNKNOWN, cells, walls, Set.of());
                var state = level.getBlockState(next);
                if ((partitionDoors && state.getBlock() instanceof com.frozendawn.block.AirlockDoorBlock)
                        || !isPassage(level, next, state)) {
                    walls.add(next.immutable()); faces.add(new BoundaryFace(pos, direction)); continue;
                }
                if (Math.abs(next.getX() - origin.getX()) > 48 || Math.abs(next.getZ() - origin.getZ()) > 48
                        || Math.abs(next.getY() - origin.getY()) > 24 || cells.size() >= limit)
                    return new Geometry(Seal.UNKNOWN, cells, walls, Set.of());
                cells.add(next.immutable()); queue.addLast(next.immutable());
            }
        }
        return new Geometry(Seal.SEALED, cells, walls, faces);
    }

    public static boolean hasAir(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || level.dimension() != Level.OVERWORLD) return false;
        Room room = roomAt(serverLevel, pos, "AIR_QUERY");
        if (room == null) return false;
        if (!CombustionAtmosphere.isVacuum(level)) return true;
        return recover(serverLevel, room);
    }

    public static boolean hasOxygenSupport(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        Room room = roomAt(serverLevel, pos, "OXYGEN_SUPPORT");
        return room != null && hasSupply(serverLevel, room.geometry, pos) && recover(serverLevel, room);
    }

    private static boolean hasSupply(ServerLevel level, Geometry geometry, BlockPos pos) {
        for (var corePos : GeothermalCoreRegistry.getCores(level)) {
            if (!level.isLoaded(corePos) || !(level.getBlockEntity(corePos) instanceof GeothermalCoreBlockEntity core)) continue;
            if (pos.distSqr(corePos) <= (long)core.getEffectiveO2Range() * core.getEffectiveO2Range()
                    && connectedSource(geometry, corePos)) return true;
        }
        for (var anchor : BlastPitWarmZoneRegistry.getWarmZones(level)) {
            if (level.isLoaded(anchor) && Math.abs(pos.getY()-anchor.getY()) <= 18
                    && Math.pow(pos.getX()-anchor.getX(), 2) + Math.pow(pos.getZ()-anchor.getZ(), 2) <= 18 * 18
                    && connectedSource(geometry, anchor)) return true;
        }
        return false;
    }

    private static boolean connectedSource(Geometry geometry, BlockPos pos) {
        if (geometry.cells.contains(pos)) return true;
        for (var direction : Direction.values()) if (geometry.cells.contains(pos.relative(direction))) return true;
        return false;
    }

    private static boolean recover(ServerLevel level, Room room) {
        // Controlled chambers cannot borrow the Core's automatic room refill.
        if (com.frozendawn.airlock.AirlockManager.blocksAutomaticAir(level,room.geometry.cells)) return false;
        var saved = RoomAirState.get(level);
        // Existing sealed saves retain their trapped air; exposed/breached cells do not.
        if (!saved.isDepleted(room.geometry.cells)) {
            room.refillStart = -1;
            if (room.recoveryNotice != null) announceRecovery(level, room,
                    com.frozendawn.network.RoomRecoveryPayload.Stage.AIR_RESTORED);
            return true;
        }
        boolean supply = false;
        for (var core : GeothermalCoreRegistry.getCores(level))
            if (connectedSource(room.geometry, core) && hasSupply(level, room.geometry, core)) { supply = true; break; }
        if (!supply) for (var anchor : BlastPitWarmZoneRegistry.getWarmZones(level))
            if (connectedSource(room.geometry, anchor) && hasSupply(level, room.geometry, anchor)) { supply = true; break; }
        if (!supply) {
            room.refillStart = -1;
            announceRecovery(level, room, com.frozendawn.network.RoomRecoveryPayload.Stage.WAITING_FOR_OXYGEN);
            return false;
        }
        announceRecovery(level, room, com.frozendawn.network.RoomRecoveryPayload.Stage.RESTORING_AIR);
        if (room.refillStart < 0) room.refillStart = level.getGameTime();
        if (level.getGameTime() - room.refillStart < REFILL_TICKS) return false;
        saved.refill(room.geometry.cells); room.refillStart = -1;
        announceRecovery(level, room, com.frozendawn.network.RoomRecoveryPayload.Stage.AIR_RESTORED);
        return true;
    }

    private static void announceRecovery(ServerLevel level, Room room,
            com.frozendawn.network.RoomRecoveryPayload.Stage stage) {
        if (room.recoveryNotice == stage) return;
        room.recoveryNotice = stage;
        for (var player : level.players()) {
            if (!player.isSpectator() && AtmosphericBreach.contains(room.geometry.cells, player))
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.frozendawn.network.RoomRecoveryPayload(stage));
        }
    }

    /** Canonical identity is per dimension and persists beyond cache eviction and server reload. */
    public static RoomView view(ServerLevel level, BlockPos pos) {
        var room = roomAt(level, pos, "OPERATOR_VIEW");
        return room == null ? null : new RoomView(room.id, room.geometry);
    }
    public static List<RoomView> trackedRooms(ServerLevel level) {
        var index = LEVELS.get(level);
        return index == null ? List.of() : index.rooms.stream().filter(room -> !room.uncertain)
                .map(room -> new RoomView(room.id, room.geometry)).toList();
    }

    private static Room roomAt(ServerLevel level, BlockPos pos, String reason) {
        Index index = LEVELS.computeIfAbsent(level, ignored -> new Index());
        var room = index.cells.get(pos);
        if (room != null && (room.changed != null || room.uncertain)) {
            if (!rebuild(level, index, room.geometry.cells)) return null;
            room = index.cells.get(pos); // A split may put the query in a different child.
        }
        if (room != null) { queried(level, room, pos, reason); return room; }
        var negative = index.negative.get(pos);
        if (negative != null && level.getGameTime() - negative.tick < 10) return null;
        if (!rebuild(level, index, Set.of(pos.immutable()))) return null;
        room = index.cells.get(pos);
        if (room != null) queried(level, room, pos, reason);
        return room;
    }

    private static void queried(ServerLevel level, Room room, BlockPos pos, String reason) {
        room.lastUsed = room.lastQueryTick = level.getGameTime();
        room.lastQueryPos = pos.immutable(); room.lastQueryReason = reason;
    }

    /** Rebuild every sibling before publishing. This prevents query order from choosing split identities. */
    private static boolean rebuild(ServerLevel level, Index index, Set<BlockPos> seeds) {
        var saved = RoomIdentityState.get(level);
        var parents = new LinkedHashSet<RoomIdentityState.Membership>();
        var pending = new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
        pending.addAll(seeds);
        for (var parent : saved.overlapping(seeds)) if (parents.add(parent)) pending.addAll(parent.cells());
        var covered = new HashSet<BlockPos>();
        var geometries = new ArrayList<Geometry>();
        var openParents = new HashSet<RoomIdentityState.Membership>();
        while (!pending.isEmpty()) {
            var start = pending.pollFirst();
            if (covered.contains(start)) continue;
            if (!level.isLoaded(start)) return defer(level, index, parents); // Keep history and retry; never load chunks or trust stale air.
            if (!isPassage(level, start, level.getBlockState(start))) { covered.add(start); continue; }
            var geometry = inspect(level, start);
            if (geometry.seal == Seal.UNKNOWN) return defer(level, index, parents);
            if (geometry.seal == Seal.SEALED) {
                geometries.add(geometry); covered.addAll(geometry.cells);
                for (var parent : saved.overlapping(geometry.cells))
                    if (parents.add(parent)) pending.addAll(parent.cells());
            } else {
                // OPEN inspection stops at its first sky exit. Cover the connected historical cells
                // separately so a breached room is not flood-filled once per cell, while sealed siblings survive.
                var historical = new HashSet<BlockPos>(seeds);
                for (var parent : parents) historical.addAll(parent.cells());
                var component = historicalComponent(level, start, historical);
                if (component == null) return defer(level, index, parents);
                covered.addAll(component); covered.add(start);
                for (var parent : parents) if (!Collections.disjoint(parent.cells(), component)) openParents.add(parent);
                if (index.negative.size() > 1024) index.negative.clear();
                index.negative.put(start, new Negative(level.getGameTime(), Seal.OPEN));
                if (parents.isEmpty() && CombustionAtmosphere.isVacuum(level))
                    RoomAirState.get(level).evacuate(Set.of(start));
            }
        }
        var previous = new HashMap<Long, Room>();
        var before = snapshots(index.rooms.stream().filter(room -> parents.stream().anyMatch(parent -> parent.id() == room.id)).toList());
        boolean wasUncertain = index.rooms.stream().anyMatch(room -> room.uncertain
                && parents.stream().anyMatch(parent -> parent.id() == room.id));
        for (var room : List.copyOf(index.rooms))
            if (parents.stream().anyMatch(parent -> parent.id() == room.id)) {
                previous.put(room.id, room); remove(index, room);
            }
        for (var parent : openParents) if (CombustionAtmosphere.isVacuum(level)) {
            var old = previous.get(parent.id());
            boolean pressurized = !RoomAirState.get(level).isDepleted(parent.cells());
            RoomAirState.get(level).evacuate(parent.cells());
            CombustionAtmosphere.reset();
            if (old != null) {
                VacuumFlames.extinguishRoom(level, parent.cells(), old.geometry.walls);
                if (pressurized && old.changed != null) AtmosphericBreach.start(level, parent.cells(), old.changed);
            }
        }
        // Keep dormant membership if the whole volume is open. Resealing can recover the same identity.
        if (geometries.isEmpty()) {
            if (!before.isEmpty()) publish(level, new RoomChangeEvent.GeometryChange(before, List.of(), parents, true));
            return true;
        }
        geometries.sort(Comparator.comparingLong(g -> g.cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow()));
        var records = saved.reconcile(parents, geometries.stream().map(Geometry::cells).toList());
        var current = new ArrayList<Room>();
        for (int i = 0; i < geometries.size(); i++) {
            var geometry = geometries.get(i); long id = records.get(i).id();
            var room = previous.get(id);
            if (room == null || !room.geometry.cells.equals(geometry.cells))
                room = new Room(id, geometry, level.getGameTime()); // Geometry edits restart refill; do not copy it to siblings.
            room.geometry = geometry; room.changed = null; room.uncertain = false; room.checked = level.getGameTime();
            if (index.rooms.size() >= MAX_ROOMS) {
                // Active infrastructure takes priority over the ordinary cache ceiling.
                index.rooms.stream().filter(candidate -> candidate.activeUntil < level.getGameTime())
                        .min(Comparator.comparingLong(candidate -> candidate.lastUsed)).ifPresent(candidate -> remove(index, candidate));
            }
            add(index, room); current.add(room);
        }
        var after = snapshots(current);
        if (wasUncertain || !before.equals(after))
            publish(level, new RoomChangeEvent.GeometryChange(before, after, parents, true));
        return true;
    }

    private static boolean defer(ServerLevel level, Index index, Set<RoomIdentityState.Membership> parents) {
        var affected = index.rooms.stream().filter(room -> parents.stream().anyMatch(parent -> parent.id() == room.id)).toList();
        boolean newlyUncertain = affected.stream().anyMatch(room -> !room.uncertain);
        var before = snapshots(affected);
        for (var room : affected) room.uncertain = true;
        if (newlyUncertain) publish(level, new RoomChangeEvent.GeometryChange(before, List.of(), parents, false));
        return false;
    }

    private static List<RoomView> snapshots(Collection<Room> rooms) {
        return rooms.stream().sorted(Comparator.comparingLong(room -> room.id))
                .map(room -> new RoomView(room.id, room.geometry)).toList();
    }
    /** Runtime operator diagnostics; counters reset with the pressure cache and are not persisted thermal state. */
    public record ChangeStats(long geometry, long material, long air, RoomChangeEvent.Type last) {}
    public static ChangeStats changeStats(ServerLevel level) {
        var index = LEVELS.get(level);
        return index == null ? new ChangeStats(0, 0, 0, null)
                : new ChangeStats(index.geometryChanges, index.materialChanges, index.airChanges, index.lastChange);
    }
    private static void publish(ServerLevel level, RoomChangeEvent.Change change) {
        var index = LEVELS.computeIfAbsent(level, ignored -> new Index());
        switch (change.type()) {
            case GEOMETRY -> index.geometryChanges++;
            case WALL_MATERIAL -> index.materialChanges++;
            case AIR_STATE -> index.airChanges++;
        }
        index.lastChange = change.type();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RoomChangeEvent(level, change));
    }
    /** Called by the persisted cell-air authority after mutation; no geometry query or chunk loading. */
    public static void airChanged(ServerLevel level, Set<BlockPos> cells, boolean depleted) {
        if (cells.isEmpty()) return;
        var ids = new HashSet<Long>();
        for (var membership : RoomIdentityState.get(level).overlapping(cells)) ids.add(membership.id());
        CombustionAtmosphere.reset();
        // Ordinary exterior vacuum cells have no room to notify. Initial discovery carries their state.
        if (!ids.isEmpty()) publish(level, new RoomChangeEvent.AirStateChange(cells, ids, depleted));
    }

    private static Set<BlockPos> historicalComponent(ServerLevel level, BlockPos start, Set<BlockPos> historical) {
        var queue = new ArrayDeque<BlockPos>(); var result = new HashSet<BlockPos>();
        queue.add(start); result.add(start);
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (!historical.contains(next) || result.contains(next)) continue;
                if (!level.isLoaded(next)) return null;
                if (isPassage(level, next, level.getBlockState(next))) { result.add(next); queue.addLast(next); }
            }
        }
        return result;
    }

    private static void add(Index index, Room room) {
        index.rooms.add(room); index.work.addLast(room);
        for (var cell : room.geometry.cells) index.cells.put(cell, room);
        for (var wall : room.geometry.walls) index.walls.computeIfAbsent(wall, ignored -> new HashSet<>()).add(room);
    }
    private static void remove(Index index, Room room) {
        index.rooms.remove(room); index.work.remove(room);
        for (var cell : room.geometry.cells) index.cells.remove(cell, room);
        for (var wall : room.geometry.walls) {
            var holders = index.walls.get(wall);
            if (holders != null) { holders.remove(room); if (holders.isEmpty()) index.walls.remove(wall); }
        }
    }

    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after) return;
        boolean beforePassage = isPassage(level, pos, before), afterPassage = isPassage(level, pos, after);
        var index = LEVELS.get(level); if (index == null) return;
        var impacted = new HashSet<Room>(index.walls.getOrDefault(pos, Set.of()));
        if (beforePassage != afterPassage) {
            index.negative.clear();
            for (var activity : index.activity.values()) activity.retryAt = 0;
            var inside = index.cells.get(pos); if (inside != null) impacted.add(inside);
            for (var room : impacted) if (room.changed == null) room.changed = pos.immutable();
            CombustionAtmosphere.reset();
        } else if (!beforePassage && before.getBlock() != after.getBlock() && !impacted.isEmpty()) {
            publish(level, new RoomChangeEvent.WallMaterialChange(pos, before, after, snapshots(impacted)));
        }
    }

    /**
     * Renewable loaded-block-entity lease. Lit heaters and future thermostats call every 20 ticks.
     * No permanent registration: removal, fuel exhaustion and chunk unload stop renewal automatically.
     * This keeps pressure geometry active; it never forces chunk tickets or supplies oxygen/heat.
     */
    public static void keepAlive(BlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level) || source.isRemoved()
                || !level.isLoaded(source.getBlockPos()) || level.getBlockEntity(source.getBlockPos()) != source) return;
        var index = LEVELS.computeIfAbsent(level, ignored -> new Index());
        var pos = source.getBlockPos().immutable();
        var activity = index.activity.get(pos);
        if (activity == null) {
            index.activity.put(pos, new Activity(source, level.getGameTime()));
            index.sourceWork.addLast(pos);
        } else if (activity.source.get() != source) {
            index.activity.put(pos, new Activity(source, level.getGameTime()));
        } else activity.heartbeat = level.getGameTime();
        renewAdjacent(level, index, pos);
    }

    private static boolean renewAdjacent(ServerLevel level, Index index, BlockPos pos) {
        var activity = index.activity.get(pos); if (activity == null) return false;
        var rooms = new HashSet<Room>(index.walls.getOrDefault(pos, Set.of()));
        var inside = index.cells.get(pos); if (inside != null) rooms.add(inside);
        for (var room : rooms) {
            // Polling must not extend an expired source's lease or overwrite a newer player query.
            room.lastUsed = Math.max(room.lastUsed, activity.heartbeat);
            room.activeUntil = Math.max(room.activeUntil, activity.heartbeat + ACTIVITY_LEASE_TICKS);
        }
        return !rooms.isEmpty();
    }

    private static void tickActivity(ServerLevel level, Index index) {
        // Bound autonomous discovery separately from the existing eight-room geometry work budget.
        int count = Math.min(2, index.sourceWork.size());
        for (int i = 0; i < count; i++) {
            var pos = index.sourceWork.removeFirst(); var activity = index.activity.get(pos);
            if (activity == null) continue;
            var source = activity.source.get();
            if (level.getGameTime() - activity.heartbeat > ACTIVITY_LEASE_TICKS || source == null || source.isRemoved()
                    || !level.isLoaded(pos) || level.getBlockEntity(pos) != source) {
                index.activity.remove(pos); continue;
            }
            index.sourceWork.addLast(pos);
            if (renewAdjacent(level, index, pos) || level.getGameTime() < activity.retryAt) continue;
            activity.retryAt = level.getGameTime() + SOURCE_RETRY_TICKS;
            // Full-block heaters sit on the room boundary; passable controller panels sit in its cells.
            var seeds = new ArrayList<BlockPos>(); seeds.add(pos);
            for (var direction : Direction.values()) seeds.add(pos.relative(direction));
            for (var seed : seeds) {
                if (level.isLoaded(seed) && isPassage(level, seed, level.getBlockState(seed))) roomAt(level, seed, "INFRASTRUCTURE_DISCOVERY");
            }
            renewAdjacent(level, index, pos);
        }
    }

    /** Loaded active room snapshots without extending their query/lease lifetime. */
    public static List<RoomView> activeRooms(ServerLevel level) {
        var index = LEVELS.get(level);
        return index == null ? List.of() : index.rooms.stream()
                .filter(room -> !room.uncertain && room.activeUntil >= level.getGameTime())
                .map(room -> new RoomView(room.id, room.geometry)).toList();
    }

    public record RoomDiagnostic(long id, Geometry geometry, boolean uncertain, boolean active,
            long idleTicks, long lastQueryAge, String lastQueryReason, BlockPos lastQueryPos,
            List<BlockPos> sources) {}
    /** Inspect existing records only: no flood fill, query renewal, chunk loading or cache creation. */
    public static List<RoomDiagnostic> cachedRoomDiagnostics(ServerLevel level) {
        var index = LEVELS.get(level); if (index == null) return List.of();
        long now = level.getGameTime();
        return index.rooms.stream().sorted(Comparator.comparingLong(room -> room.id)).map(room ->
                new RoomDiagnostic(room.id, room.geometry, room.uncertain,
                        !room.uncertain && room.activeUntil >= now, now - room.lastUsed,
                        room.lastQueryTick < 0 ? -1 : now - room.lastQueryTick,
                        room.lastQueryReason, room.lastQueryPos,
                        index.activity.entrySet().stream().filter(entry -> now-entry.getValue().heartbeat <= ACTIVITY_LEASE_TICKS
                                && (room.geometry.cells.contains(entry.getKey()) || room.geometry.walls.contains(entry.getKey())))
                                .map(Map.Entry::getKey).sorted(Comparator.comparingLong(BlockPos::asLong)).toList())).toList();
    }

    public record ActivityStats(int tracked, long active) {}
    /** Non-querying diagnostics: reading this does not discover or renew any room. */
    public static ActivityStats activityStats(ServerLevel level) {
        var index = LEVELS.get(level);
        return index == null ? new ActivityStats(0, 0) : new ActivityStats(index.rooms.size(),
                index.rooms.stream().filter(room -> !room.uncertain && room.activeUntil >= level.getGameTime()).count());
    }

    public static void tickLevel(ServerLevel level) {
        var index = LEVELS.get(level); if (index == null) return;
        tickActivity(level, index);
        int count = Math.min(8, index.work.size());
        for (int i = 0; i < count; i++) {
            if (index.work.isEmpty()) break;
            var room = index.work.removeFirst();
            if (!index.rooms.contains(room)) continue;
            if (level.getGameTime()-room.lastUsed > 600) { remove(index, room); continue; }
            if (room.changed != null || room.uncertain || level.getGameTime()-room.checked >= 20) {
                boolean rebuilt = rebuild(level, index, room.geometry.cells);
                if (!rebuilt) { index.work.addLast(room); continue; }
                long id = room.id;
                room = index.rooms.stream().filter(candidate -> candidate.id == id).findFirst().orElse(null);
                if (room == null) continue;
            } else index.work.addLast(room);
            if (CombustionAtmosphere.isVacuum(level)) recover(level, room);
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { for (var level : event.getServer().getAllLevels()) tickLevel(level); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { reset(); }
    public static void reset() { LEVELS.clear(); }
}
