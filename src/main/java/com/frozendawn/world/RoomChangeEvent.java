package com.frozendawn.world;

import com.frozendawn.data.RoomIdentityState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/** Server-thread, non-cancellable notifications after the authoritative state is updated. */
public final class RoomChangeEvent extends Event {
    public enum Type { GEOMETRY, WALL_MATERIAL, AIR_STATE }
    public sealed interface Change permits GeometryChange, WallMaterialChange, AirStateChange {
        Type type();
    }
    /** Current rooms are complete sealed volumes. Incomplete inspection suspends, never discards, stored heat.
     * Prior saved memberships remain available even when no live geometry was cached (including reload).
     * Complete inspection with no current rooms means the affected volumes are open or removed. */
    public record GeometryChange(List<RoomAtmosphere.RoomView> previousRooms,
            List<RoomAtmosphere.RoomView> currentRooms, Set<RoomIdentityState.Membership> previousMemberships,
            boolean complete) implements Change {
        public GeometryChange {
            previousRooms = List.copyOf(previousRooms); currentRooms = List.copyOf(currentRooms);
            previousMemberships = Set.copyOf(previousMemberships);
        }
        @Override public Type type() { return Type.GEOMETRY; }
    }
    /** Both sides of a shared physical boundary are included, without changing their geometry or refill timer. */
    public record WallMaterialChange(BlockPos position, BlockState before, BlockState after,
            List<RoomAtmosphere.RoomView> rooms) implements Change {
        public WallMaterialChange {
            position = position.immutable(); rooms = List.copyOf(rooms);
            Objects.requireNonNull(before); Objects.requireNonNull(after);
        }
        @Override public Type type() { return Type.WALL_MATERIAL; }
    }
    /** Only cells whose persisted depletion bit actually changed. IDs may include dormant/uncached membership.
     * Never flood-fills or forces chunks just to publish an air transition. */
    public record AirStateChange(Set<BlockPos> cells, Set<Long> roomIds, boolean depleted) implements Change {
        public AirStateChange {
            cells = cells.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toUnmodifiableSet());
            roomIds = Set.copyOf(roomIds);
        }
        @Override public Type type() { return Type.AIR_STATE; }
    }
    private final ServerLevel level;
    private final Change change;
    public RoomChangeEvent(ServerLevel level, Change change) {
        this.level = Objects.requireNonNull(level); this.change = Objects.requireNonNull(change);
    }
    public ServerLevel level() { return level; }
    public Change change() { return change; }
    public Type type() { return change.type(); }
}
