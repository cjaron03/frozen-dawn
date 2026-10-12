package com.frozendawn.data;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent membership, independent of the pressure cache and any single anchor. */
public final class RoomIdentityState extends SavedData {
    public static final String NAME = "frozendawn_room_identities";
    public record Membership(long id, Set<BlockPos> cells) {
        public Membership { cells = Set.copyOf(cells); }
    }
    private record Claim(long parent, int child, int overlap, long firstCell) {}
    private final Map<Long, Membership> rooms = new TreeMap<>();
    private final Map<BlockPos, Long> cells = new HashMap<>();
    private long nextId = 1;

    public static RoomIdentityState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoomIdentityState::new, RoomIdentityState::load), NAME);
    }
    public Membership at(BlockPos pos) {
        var id = cells.get(pos); return id == null ? null : rooms.get(id);
    }
    public Set<Membership> overlapping(Set<BlockPos> positions) {
        var ids = new TreeSet<Long>();
        for (var pos : positions) { var id = cells.get(pos); if (id != null) ids.add(id); }
        var result = new LinkedHashSet<Membership>();
        for (long id : ids) result.add(rooms.get(id));
        return result;
    }

    /** One parent can name only one child. Biggest overlap wins; ties use parent ID and child position. */
    public List<Membership> reconcile(Set<Membership> parents, List<Set<BlockPos>> volumes) {
        if (parents.size() == volumes.size()) {
            var unchanged = new ArrayList<Membership>();
            for (var volume : volumes) {
                var match = parents.stream().filter(parent -> parent.cells.equals(volume)).findFirst().orElse(null);
                if (match == null) break;
                unchanged.add(match);
            }
            if (unchanged.size() == volumes.size()) return List.copyOf(unchanged);
        }
        var claims = new ArrayList<Claim>();
        var seen = new HashSet<BlockPos>();
        for (int child = 0; child < volumes.size(); child++) {
            var volume = volumes.get(child);
            if (volume.isEmpty()) throw new IllegalArgumentException("Empty room membership");
            for (var pos : volume) if (!seen.add(pos)) throw new IllegalArgumentException("Overlapping room volumes");
            long first = volume.stream().mapToLong(BlockPos::asLong).min().orElseThrow();
            for (var parent : parents) {
                int overlap = 0;
                for (var pos : volume) if (parent.cells.contains(pos)) overlap++;
                if (overlap > 0) claims.add(new Claim(parent.id, child, overlap, first));
            }
        }
        claims.sort(Comparator.comparingInt(Claim::overlap).reversed()
                .thenComparingLong(Claim::parent).thenComparingLong(Claim::firstCell));
        long[] ids = new long[volumes.size()];
        var used = new HashSet<Long>();
        for (var claim : claims) if (ids[claim.child] == 0 && used.add(claim.parent)) ids[claim.child] = claim.parent;
        // Compare numeric IDs; hashing a Membership hashes all its cells.
        var parentIds = new HashSet<Long>();
        for (var parent : parents) parentIds.add(parent.id);
        // Validate before changing any saved membership.
        for (var pos : seen) {
            var existing = at(pos);
            if (existing != null && !parentIds.contains(existing.id)) throw new IllegalArgumentException("Missing overlapping parent");
        }
        for (var parent : parents) {
            rooms.remove(parent.id);
            for (var pos : parent.cells) cells.remove(pos, parent.id);
        }
        var result = new ArrayList<Membership>();
        for (int i = 0; i < volumes.size(); i++) {
            if (ids[i] == 0) ids[i] = nextId++;
            var record = new Membership(ids[i], volumes.get(i));
            rooms.put(record.id, record);
            for (var pos : record.cells) cells.put(pos, record.id);
            result.add(record);
        }
        setDirty();
        return List.copyOf(result);
    }
    public static RoomIdentityState load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RoomIdentityState();
        result.nextId = Math.max(1, tag.getLong("nextId"));
        for (var item : tag.getList("rooms", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)item; long id = entry.getLong("id");
            var positions = new HashSet<BlockPos>();
            for (long pos : entry.getLongArray("cells")) positions.add(BlockPos.of(pos));
            if (id <= 0 || id == Long.MAX_VALUE || positions.isEmpty() || result.rooms.containsKey(id)
                    || positions.stream().anyMatch(result.cells::containsKey)) continue;
            var record = new Membership(id, positions);
            result.rooms.put(id, record);
            for (var pos : positions) result.cells.put(pos, id);
            result.nextId = Math.max(result.nextId, id + 1);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var room : rooms.values()) {
            var item = new CompoundTag(); item.putLong("id", room.id);
            item.putLongArray("cells", room.cells.stream().mapToLong(BlockPos::asLong).sorted().toArray());
            list.add(item);
        }
        tag.putLong("nextId", nextId); tag.put("rooms", list);
        return tag;
    }
}
