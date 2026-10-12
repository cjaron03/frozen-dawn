package com.frozendawn.data;

import com.frozendawn.thermal.RoomHeatMath;
import com.frozendawn.world.RoomAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Full cell membership plus one heat reservoir per physical material block. No offline integration. */
public final class RoomThermalState extends SavedData {
    public static final String NAME = "frozendawn_room_heat";
    public static final class Room {
        public final long id;
        public final Set<BlockPos> cells;
        public final Set<RoomAtmosphere.BoundaryFace> faces;
        public Set<BlockPos> structure = Set.of();
        public double airEnergy;
        public boolean airPresent, sealed = true;
        public Room(long id, Set<BlockPos> cells, Set<RoomAtmosphere.BoundaryFace> faces) {
            this.id = id; this.cells = Set.copyOf(cells); this.faces = Set.copyOf(faces);
        }
        public double airCapacity() { return airPresent ? cells.size() * RoomHeatMath.AIR_CAPACITY : 0; }
    }
    public static final class Material {
        public final String block;
        public final double capacity;
        public double energy;
        public Material(String block, double capacity, double energy) {
            this.block = block; this.capacity = capacity; this.energy = energy;
        }
    }
    public static final class Ledger {
        public double heater, environmentLoss, airIn, airOut, materialIn, materialOut;
        public double net() { return heater - environmentLoss + airIn - airOut + materialIn - materialOut; }
    }
    public final Map<Long, Room> rooms = new TreeMap<>();
    public final Map<BlockPos, Material> materials = new HashMap<>();
    public final Ledger ledger = new Ledger();
    public static RoomThermalState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoomThermalState::new, RoomThermalState::load), NAME);
    }
    public double totalEnergy() {
        return rooms.values().stream().mapToDouble(room -> room.airEnergy).sum()
                + materials.values().stream().mapToDouble(material -> material.energy).sum();
    }
    private static Set<BlockPos> positions(long[] values) {
        var result = new HashSet<BlockPos>(); for (long value : values) result.add(BlockPos.of(value)); return Set.copyOf(result);
    }
    private static long[] positions(Set<BlockPos> values) { return values.stream().mapToLong(BlockPos::asLong).sorted().toArray(); }
    private static double finite(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
    public static RoomThermalState load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new RoomThermalState(); var claimed = new HashSet<BlockPos>();
        for (var item : tag.getList("rooms", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)item; long id = entry.getLong("id"); var cells = positions(entry.getLongArray("cells"));
            if (id <= 0 || cells.isEmpty() || result.rooms.containsKey(id) || !Collections.disjoint(claimed, cells)) continue;
            var faces = new HashSet<RoomAtmosphere.BoundaryFace>();
            for (var faceItem : entry.getList("faces", Tag.TAG_COMPOUND)) {
                var f = (CompoundTag)faceItem; int direction = f.getInt("direction"); var cell = BlockPos.of(f.getLong("cell"));
                if (direction >= 0 && direction < Direction.values().length && cells.contains(cell))
                    faces.add(new RoomAtmosphere.BoundaryFace(cell, Direction.values()[direction]));
            }
            var room = new Room(id,cells,faces); room.structure = positions(entry.getLongArray("structure"));
            room.airPresent = entry.getBoolean("airPresent"); room.sealed = entry.getBoolean("sealed");
            room.airEnergy = room.airPresent ? finite(entry.getDouble("airEnergy")) : 0;
            result.rooms.put(id,room); claimed.addAll(cells);
        }
        for (var item : tag.getList("materials", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag)item; double capacity = finite(entry.getDouble("capacity"));
            if (capacity > 0) result.materials.putIfAbsent(BlockPos.of(entry.getLong("pos")),
                    new Material(entry.getString("block"),capacity,finite(entry.getDouble("energy"))));
        }
        var ledger = tag.getCompound("ledger");
        result.ledger.heater=finite(ledger.getDouble("heater"));
        result.ledger.environmentLoss=Double.isFinite(ledger.getDouble("environmentLoss"))?ledger.getDouble("environmentLoss"):0;
        result.ledger.airIn=finite(ledger.getDouble("airIn")); result.ledger.airOut=finite(ledger.getDouble("airOut"));
        result.ledger.materialIn=finite(ledger.getDouble("materialIn")); result.ledger.materialOut=finite(ledger.getDouble("materialOut"));
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version",1); var roomsTag=new ListTag();
        for(var room:rooms.values()) {
            var entry=new CompoundTag();entry.putLong("id",room.id);entry.putLongArray("cells",positions(room.cells));
            entry.putLongArray("structure",positions(room.structure));entry.putDouble("airEnergy",room.airEnergy);
            entry.putBoolean("airPresent",room.airPresent);entry.putBoolean("sealed",room.sealed);
            var faces=new ListTag();for(var face:room.faces.stream().sorted(Comparator.comparingLong((RoomAtmosphere.BoundaryFace f)->f.airCell().asLong())
                    .thenComparingInt(f->f.outwardDirection().ordinal())).toList()) {
                var f=new CompoundTag();f.putLong("cell",face.airCell().asLong());f.putInt("direction",face.outwardDirection().ordinal());faces.add(f);
            }
            entry.put("faces",faces);roomsTag.add(entry);
        }
        tag.put("rooms",roomsTag);var materialsTag=new ListTag();
        for(var pos:materials.keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            var material=materials.get(pos);var entry=new CompoundTag();entry.putLong("pos",pos.asLong());entry.putString("block",material.block);
            entry.putDouble("capacity",material.capacity);entry.putDouble("energy",material.energy);materialsTag.add(entry);
        }
        tag.put("materials",materialsTag);var budget=new CompoundTag();budget.putDouble("heater",ledger.heater);
        budget.putDouble("environmentLoss",ledger.environmentLoss);budget.putDouble("airIn",ledger.airIn);budget.putDouble("airOut",ledger.airOut);
        budget.putDouble("materialIn",ledger.materialIn);budget.putDouble("materialOut",ledger.materialOut);tag.put("ledger",budget);return tag;
    }
}
