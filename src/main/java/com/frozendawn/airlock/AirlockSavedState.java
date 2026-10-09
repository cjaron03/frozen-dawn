package com.frozendawn.airlock;

import com.frozendawn.data.RoomAirState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One authority per exact chamber, independent of optional panel block entities. */
public final class AirlockSavedState extends SavedData {
    public static final class Chamber {
        public final long id;
        public final Set<BlockPos> cells, doors;
        public final BlockPos base, outside;
        public final Set<BlockPos> panels = new HashSet<>();
        public final AirlockCycle gas;
        public long lastTick = Long.MIN_VALUE;
        Chamber(Set<BlockPos> cells,Set<BlockPos> doors,BlockPos base,BlockPos outside,AirlockCycle gas) {
            this.cells=Set.copyOf(cells);this.doors=Set.copyOf(doors);this.base=base.immutable();this.outside=outside.immutable();this.gas=gas;
            id=cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow();
        }
    }
    private final Map<Long,Chamber> chambers = new LinkedHashMap<>();
    private final Map<BlockPos,Chamber> owners = new HashMap<>();
    private final Map<BlockPos,Set<Chamber>> doorOwners = new HashMap<>();
    private final Set<Long> accounted = new HashSet<>();
    public static AirlockSavedState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(AirlockSavedState::new,AirlockSavedState::load),"frozendawn_airlocks");
    }
    public Collection<Chamber> chambers() { return chambers.values(); }
    public Chamber at(BlockPos pos) { return owners.get(pos); }
    public Set<Chamber> atDoor(BlockPos pos) { return doorOwners.getOrDefault(pos,Set.of()); }
    public Chamber byId(long id) { return chambers.get(id); }
    public Chamber create(ServerLevel level,Set<BlockPos> cells,Set<BlockPos> doors,BlockPos base,BlockPos outside,boolean initiallyBreathable) {
        long id=cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow();
        var existing=chambers.get(id);
        if(existing!=null&&existing.cells.equals(cells)&&existing.doors.equals(doors))return existing;
        boolean alreadyAccounted=cells.stream().anyMatch(pos->accounted.contains(pos.asLong()));
        // Changing/overlapping layouts never credits old stored air into a new record.
        var overlaps=new HashSet<Chamber>();for(var pos:cells)if(owners.containsKey(pos))overlaps.add(owners.get(pos));
        for(var old:overlaps) {old.gas.vent();old.gas.discardReserve();remove(old);}
        for(var pos:cells)accounted.add(pos.asLong());
        var gas=new AirlockCycle(cells.size(),initiallyBreathable&&!alreadyAccounted?cells.size()*AirlockCycle.UNITS_PER_CELL:0);
        var chamber=new Chamber(cells,doors,base,outside,gas);put(chamber);
        if(!gas.breathable())RoomAirState.get(level).evacuate(cells);
        setDirty();return chamber;
    }
    private void put(Chamber c) {chambers.put(c.id,c);for(var pos:c.cells)owners.put(pos,c);for(var pos:c.doors)doorOwners.computeIfAbsent(pos,k->new HashSet<>()).add(c);}
    private void remove(Chamber c) {chambers.remove(c.id,c);for(var pos:c.cells)owners.remove(pos,c);for(var pos:c.doors) {var set=doorOwners.get(pos);if(set!=null) {set.remove(c);if(set.isEmpty())doorOwners.remove(pos);}}}
    private static Set<BlockPos> positions(long[] data) {var set=new HashSet<BlockPos>();for(long n:data)set.add(BlockPos.of(n));return set;}
    private static long[] longs(Set<BlockPos> set) {return set.stream().mapToLong(BlockPos::asLong).sorted().toArray();}
    public static AirlockSavedState load(CompoundTag tag,HolderLookup.Provider registries) {
        var result=new AirlockSavedState();for(long n:tag.getLongArray("accounted"))result.accounted.add(n);
        for(var raw:tag.getList("chambers",Tag.TAG_COMPOUND)) {
            var t=(CompoundTag)raw;var cells=positions(t.getLongArray("cells"));var doors=positions(t.getLongArray("doors"));
            if(cells.isEmpty()||cells.size()>32||doors.size()<2)continue;
            var c=new Chamber(cells,doors,BlockPos.of(t.getLong("base")),BlockPos.of(t.getLong("outside")),AirlockCycle.restore(cells.size(),t.getIntArray("gas")));
            c.panels.addAll(positions(t.getLongArray("panels")));result.put(c);
            for(var pos:cells)result.accounted.add(pos.asLong());
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var list=new ListTag();for(var c:chambers.values()) {
            var t=new CompoundTag();t.putLongArray("cells",longs(c.cells));t.putLongArray("doors",longs(c.doors));
            t.putLong("base",c.base.asLong());t.putLong("outside",c.outside.asLong());t.putLongArray("panels",longs(c.panels));t.putIntArray("gas",c.gas.snapshot());list.add(t);
        }
        tag.put("chambers",list);tag.putLongArray("accounted",accounted.stream().mapToLong(Long::longValue).sorted().toArray());return tag;
    }
}
