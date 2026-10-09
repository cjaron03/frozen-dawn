package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.data.RoomAirState;
import java.util.*;
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
    public enum Seal { SEALED, OPEN, UNKNOWN }
    public record Geometry(Seal seal, Set<BlockPos> cells, Set<BlockPos> walls) {}
    private static final Map<ServerLevel, Index> LEVELS = new WeakHashMap<>();
    private static final class Room {
        Geometry geometry; final BlockPos origin; long refillStart = -1, lastUsed, checked;
        BlockPos changed;
        com.frozendawn.network.RoomRecoveryPayload.Stage recoveryNotice;
        Room(BlockPos origin, Geometry geometry, long tick) {
            this.origin = origin.immutable(); this.geometry = geometry; lastUsed = checked = tick;
        }
    }
    private record Negative(long tick, Seal seal) {}
    private static final class Index {
        final Set<Room> rooms = new LinkedHashSet<>();
        final Map<BlockPos, Room> cells = new HashMap<>();
        final Map<BlockPos, Set<Room>> walls = new HashMap<>();
        final Map<BlockPos, Negative> negative = new HashMap<>();
        final ArrayDeque<Room> work = new ArrayDeque<>();
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
        if (!level.isLoaded(origin) || !isPassage(level, origin, level.getBlockState(origin)))
            return new Geometry(Seal.UNKNOWN, Set.of(), Set.of());
        var queue = new ArrayDeque<BlockPos>(); var cells = new HashSet<BlockPos>(); var walls = new HashSet<BlockPos>();
        queue.add(origin.immutable()); cells.add(origin.immutable());
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            // Motion height includes full glass roofs, unlike canSeeSky().
            if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()))
                return new Geometry(Seal.OPEN, Set.copyOf(cells), Set.copyOf(walls));
            for (var direction : Direction.values()) {
                var next = pos.relative(direction);
                if (cells.contains(next) || walls.contains(next)) continue;
                if (!level.isLoaded(next)) return new Geometry(Seal.UNKNOWN, Set.copyOf(cells), Set.copyOf(walls));
                var state = level.getBlockState(next);
                if (!isPassage(level, next, state)) { walls.add(next.immutable()); continue; }
                if (Math.abs(next.getX() - origin.getX()) > 48 || Math.abs(next.getZ() - origin.getZ()) > 48
                        || Math.abs(next.getY() - origin.getY()) > 24 || cells.size() >= MAX_CELLS)
                    return new Geometry(Seal.UNKNOWN, Set.copyOf(cells), Set.copyOf(walls));
                cells.add(next.immutable()); queue.addLast(next.immutable());
            }
        }
        return new Geometry(Seal.SEALED, Set.copyOf(cells), Set.copyOf(walls));
    }

    public static boolean hasAir(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || level.dimension() != Level.OVERWORLD) return false;
        Room room = roomAt(serverLevel, pos);
        if (room == null) return false;
        if (!CombustionAtmosphere.isVacuum(level)) return true;
        return recover(serverLevel, room);
    }

    public static boolean hasOxygenSupport(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        Room room = roomAt(serverLevel, pos);
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

    private static Room roomAt(ServerLevel level, BlockPos pos) {
        Index index = LEVELS.computeIfAbsent(level, ignored -> new Index());
        var room = index.cells.get(pos);
        if (room != null && room.changed != null) room = refresh(level, index, room);
        if (room != null) { room.lastUsed = level.getGameTime(); return room; }
        var negative = index.negative.get(pos);
        if (negative != null && level.getGameTime() - negative.tick < 10) return null;
        Geometry geometry = inspect(level, pos);
        if (geometry.seal != Seal.SEALED) {
            if (index.negative.size() > 1024) index.negative.clear();
            index.negative.put(pos.immutable(), new Negative(level.getGameTime(), geometry.seal));
            if (geometry.seal == Seal.OPEN && CombustionAtmosphere.isVacuum(level))
                RoomAirState.get(level).evacuate(Set.of(pos.immutable()));
            return null;
        }
        if (index.rooms.size() >= MAX_ROOMS) remove(index, index.rooms.iterator().next());
        room = new Room(pos, geometry, level.getGameTime()); add(index, room); return room;
    }

    private static Room refresh(ServerLevel level, Index index, Room room) {
        var previous = room.geometry; var change = room.changed;
        Geometry current = inspect(level, room.origin);
        remove(index, room);
        if (current.seal == Seal.SEALED) {
            room.geometry = current; room.changed = null; room.checked = level.getGameTime(); add(index, room); return room;
        }
        if (current.seal == Seal.OPEN && CombustionAtmosphere.isVacuum(level)) {
            boolean pressurized = !RoomAirState.get(level).isDepleted(previous.cells);
            RoomAirState.get(level).evacuate(previous.cells);
            CombustionAtmosphere.reset();
            VacuumFlames.extinguishRoom(level, previous.cells, previous.walls);
            if (pressurized && change != null) AtmosphericBreach.start(level, previous.cells, change);
        }
        return null;
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
        if (before == after || isPassage(level, pos, before) == isPassage(level, pos, after)) return;
        var index = LEVELS.get(level); if (index == null) return;
        index.negative.clear();
        var impacted = new HashSet<Room>(index.walls.getOrDefault(pos, Set.of()));
        var inside = index.cells.get(pos); if (inside != null) impacted.add(inside);
        for (var room : impacted) if (room.changed == null) room.changed = pos.immutable();
        CombustionAtmosphere.reset();
    }

    public static void tickLevel(ServerLevel level) {
        var index = LEVELS.get(level); if (index == null) return;
        int count = Math.min(8, index.work.size());
        for (int i = 0; i < count; i++) {
            var room = index.work.removeFirst();
            if (!index.rooms.contains(room)) continue;
            if (level.getGameTime()-room.lastUsed > 600) { remove(index, room); continue; }
            if (room.changed != null || level.getGameTime()-room.checked >= 20) {
                room = refresh(level, index, room); // refresh queues retained rooms itself
                if (room == null) continue;
            } else index.work.addLast(room);
            if (CombustionAtmosphere.isVacuum(level)) recover(level, room);
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { tickLevel(event.getServer().overworld()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { reset(); }
    public static void reset() { LEVELS.clear(); }
}
