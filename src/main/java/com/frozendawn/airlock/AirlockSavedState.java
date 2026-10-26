package com.frozendawn.airlock;

import com.frozendawn.world.RoomSets;

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
        public AirlockCycle gas;
        public long lastTick = Long.MIN_VALUE;
        public boolean waitingForPower; // Rebuilt from actual paid ticks; never a saved electrical credit.
        Chamber(Set<BlockPos> cells,Set<BlockPos> doors,BlockPos base,BlockPos outside,AirlockCycle gas) {
            this.cells=RoomSets.freeze(cells);this.doors=RoomSets.freeze(doors);this.base=base.immutable();this.outside=outside.immutable();this.gas=gas;
            id=cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow();
        }
    }
    private final Map<Long,Chamber> chambers = new LinkedHashMap<>();
    private final Map<BlockPos,Chamber> owners = new HashMap<>();
    private final Map<BlockPos,Set<Chamber>> doorOwners = new HashMap<>();
    private final Set<Long> accounted = new HashSet<>();
    // Exact gas left in a separated pocket, or zero in displaced/consumed wall cells.
    private final Map<BlockPos,Integer> pocketGas=new HashMap<>();
    private boolean syncingGas;
    // Measured gas in ordinary cells that have shared air with a controlled chamber.
    private final Map<BlockPos,Integer> roomGas=new HashMap<>();
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
    /** Sealed topology edits conserve chamber plus pocket gas; the reserve is independent. */
    Chamber reshape(ServerLevel level,Chamber previous,Set<BlockPos> cells) {
        if(cells.stream().anyMatch(pos->owners.containsKey(pos)&&owners.get(pos)!=previous))return previous;
        var surviving=previous.cells.stream().filter(p->com.frozendawn.world.RoomAtmosphere.isPassage(level,p,level.getBlockState(p))).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        int retained=(int)surviving.stream().filter(cells::contains).count();
        int air=surviving.isEmpty()?previous.gas.air():(int)((long)previous.gas.air()*retained/surviving.size());
        int separated=previous.gas.air()-air;
        var pocket=surviving.stream().filter(p->!cells.contains(p)).toList();
        for(int i=0;i<pocket.size();i++) {
            int units=(int)((long)separated*(i+1)/pocket.size()-(long)separated*i/pocket.size());
            pocketGas.put(pocket.get(i),units);syncAir(level,Set.of(pocket.get(i)),units>0);
        }
        for(var pos:previous.cells)if(!surviving.contains(pos))pocketGas.put(pos,0);
        for(var pos:cells)if(!previous.cells.contains(pos)) {
            Integer measured=roomGas.remove(pos);if(measured==null)measured=pocketGas.remove(pos);
            boolean oldWall=Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d->previous.cells.contains(pos.relative(d)));
            int offered=measured!=null?measured:oldWall||RoomAirState.get(level).isDepleted(Set.of(pos))?0:AirlockCycle.UNITS_PER_CELL;
            air=Math.addExact(air,offered);
        }
        var gas=AirlockCycle.reconfigured(cells.size(),air,previous.gas.reserve());
        previous.gas.interrupt();
        var current=new Chamber(cells,previous.doors,previous.base,previous.outside,gas);
        current.panels.addAll(previous.panels);remove(previous);put(current);
        for(var pos:cells){accounted.add(pos.asLong());pocketGas.remove(pos);}
        for(var pos:current.panels)if(level.isLoaded(pos)&&level.getBlockEntity(pos) instanceof com.frozendawn.block.AirlockControllerBlockEntity panel)
            panel.assign(current.id);
        syncAir(level,cells,air>0);setDirty();return current;
    }
    /** A sealed connection redistributes existing gas; it never borrows the pump reserve. */
    public Map<Set<BlockPos>,Integer> reconcileRoomGas(ServerLevel level,
            Collection<com.frozendawn.data.RoomIdentityState.Membership> parents,List<Set<BlockPos>> volumes) {
        var oldCells=new HashSet<BlockPos>();for(var parent:parents)oldCells.addAll(parent.cells());
        boolean joined=volumes.stream().anyMatch(v->chambers.values().stream().anyMatch(c->AirlockManager.sharesBaseRoom(level,c,v)));
        if(!joined&&Collections.disjoint(oldCells,roomGas.keySet()))return Map.of();
        // Reinspection/reload of an unchanged membership must not remix gas or interrupt a pump.
        if((!joined||!Collections.disjoint(oldCells,roomGas.keySet()))&&parents.size()==volumes.size()
                &&volumes.stream().allMatch(v->parents.stream().anyMatch(p->p.cells().equals(v))))return Map.of();
        var amounts=new HashMap<BlockPos,Integer>();
        for(var parent:parents) {
            boolean empty=RoomAirState.get(level).isDepleted(parent.cells());
            for(var pos:parent.cells())amounts.put(pos,roomGas.getOrDefault(pos,empty?0:AirlockCycle.UNITS_PER_CELL));
        }
        // Controlled cells use their actual finite amount, not the ordinary nominal-air flag.
        for(var c:chambers.values()) {
            var previous=c.cells.stream().filter(oldCells::contains).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
            for(int i=0;i<previous.size();i++)amounts.put(previous.get(i),(int)((long)c.gas.air()*(i+1)/previous.size()-(long)c.gas.air()*i/previous.size()));
        }
        int[] totals=new int[volumes.size()];
        for(var parent:parents) {
            long gas=parent.cells().stream().mapToLong(p->amounts.getOrDefault(p,0)).sum();
            int[] overlaps=new int[volumes.size()];int retained=0;
            for(int i=0;i<volumes.size();i++){overlaps[i]=(int)parent.cells().stream().filter(volumes.get(i)::contains).count();retained+=overlaps[i];}
            int done=0;
            for(int i=0;i<volumes.size()&&retained>0;i++){totals[i]=Math.addExact(totals[i],(int)(gas*(done+overlaps[i])/retained-gas*done/retained));done+=overlaps[i];}
        }
        // A controlled chamber may not have been queried/cached before its door opens.
        for(var c:chambers.values())if(Collections.disjoint(oldCells,c.cells)) {
            int done=0;int count=volumes.stream().mapToInt(v->(int)c.cells.stream().filter(v::contains).count()).sum();
            for(int i=0;i<volumes.size()&&count>0;i++) {
                int n=(int)c.cells.stream().filter(volumes.get(i)::contains).count();
                totals[i]=Math.addExact(totals[i],(int)((long)c.gas.air()*(done+n)/count-(long)c.gas.air()*done/count));done+=n;
            }
        }
        // A removed wall contains no gas. Previously untracked sealed space retains its own air.
        for(int i=0;i<volumes.size();i++)for(var pos:volumes.get(i))if(!oldCells.contains(pos)&&at(pos)==null) {
            boolean wall=Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d->oldCells.contains(pos.relative(d)));
            if(!wall)totals[i]=Math.addExact(totals[i],roomGas.getOrDefault(pos,RoomAirState.get(level).isDepleted(Set.of(pos))?0:AirlockCycle.UNITS_PER_CELL));
        }
        roomGas.keySet().removeAll(oldCells);
        var result=new HashMap<Set<BlockPos>,Integer>();
        for(int i=0;i<volumes.size();i++){distributeRoomGas(volumes.get(i),totals[i]);result.put(volumes.get(i),totals[i]);}
        setDirty();return result;
    }
    /** Total measured room gas; a controlled chamber contributes exactly once. */
    public int roomGas(ServerLevel level,Set<BlockPos> cells) {
        var counted=new HashSet<Chamber>();int total=0;boolean empty=RoomAirState.get(level).isDepleted(cells);
        for(var pos:cells) {
            var c=at(pos);
            if(c!=null){if(counted.add(c))total=Math.addExact(total,c.gas.air());}
            else total=Math.addExact(total,roomGas.getOrDefault(pos,empty?0:AirlockCycle.UNITS_PER_CELL));
        }
        return total;
    }
    /** A deliberately opened manual valve shares existing gas; it never spends reserve or energy.
     * Both volumes must contain gas; callers first verify closed doors and sealed base geometry. */
    boolean equalizeWithBase(ServerLevel level,Chamber chamber,Set<BlockPos> baseCells) {
        if(baseCells.isEmpty()||!Collections.disjoint(baseCells,chamber.cells))return false;
        for(var other:chambers.values())if(other!=chamber&&other.gas.cycling()&&!Collections.disjoint(baseCells,other.cells))return false;
        int baseGas=roomGas(level,baseCells);
        if(baseGas==0||chamber.gas.air()==0)return false;
        long total=(long)baseGas+chamber.gas.air();
        if(total>Integer.MAX_VALUE)return false;
        var combined=new HashSet<BlockPos>(baseCells);combined.addAll(chamber.cells);
        // Deliberate manual operation ends an unfinished pump before redistributing its paid gas.
        chamber.gas.interrupt();chamber.waitingForPower=false;
        distributeRoomGas(combined,(int)total);
        syncAir(level,baseCells,roomGas(level,baseCells)>0);
        syncAir(level,chamber.cells,chamber.gas.air()>0);
        setDirty();return true;
    }
    public boolean hasRoomGas(Set<BlockPos> cells){return !Collections.disjoint(cells,roomGas.keySet());}
    public int roomAirRequest(ServerLevel level,Set<BlockPos> cells) {
        return Math.min(40,Math.max(0,cells.size()*AirlockCycle.UNITS_PER_CELL-roomGas(level,cells)));
    }
    public int receiveRoomAir(ServerLevel level,Set<BlockPos> cells,int offered) {
        int accepted=Math.clamp(offered,0,roomAirRequest(level,cells));if(accepted==0)return 0;
        distributeRoomGas(cells,Math.addExact(roomGas(level,cells),accepted));
        // A room with retained air stays breathable while topping up. An entirely evacuated
        // room still completes its normal recovery only after its finite deficit is supplied.
        setDirty();return accepted;
    }
    /** Sets a room's total gas, spread across its cells, and its breathability with it. */
    public void setRoomGas(ServerLevel level,Set<BlockPos> cells,int total) {
        if(cells.isEmpty())return;
        int clamped=Math.clamp(total,0,cells.size()*AirlockCycle.UNITS_PER_CELL);
        distributeRoomGas(cells,clamped);syncAir(level,cells,clamped>0);setDirty();
    }
    private void distributeRoomGas(Set<BlockPos> cells,int total) {
        var sorted=cells.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        var chamberGas=new HashMap<Chamber,Integer>();
        for(int i=0;i<sorted.size();i++) {
            var pos=sorted.get(i);int gas=(int)((long)total*(i+1)/sorted.size()-(long)total*i/sorted.size());
            var c=at(pos);
            if(c==null)roomGas.put(pos,gas);
            else {roomGas.remove(pos);chamberGas.merge(c,gas,Math::addExact);}
        }
        chamberGas.forEach((c,gas)->{if(gas!=c.gas.air())c.gas=AirlockCycle.reconfigured(c.cells.size(),gas,c.gas.reserve());});
    }
    /** Restore normal producer-supported room atmosphere without crediting nominal inventory.
     * The caller verifies a real source and the ordinary recovery delay first. */
    public boolean restoreProducedRoomAir(ServerLevel level,Set<BlockPos> cells) {
        if(hasRoomGas(cells)) {
            if(roomGas(level,cells)==0)return false;
            syncAir(level,cells,true);
        } else RoomAirState.get(level).refill(cells);
        return true;
    }
    private void syncAir(ServerLevel level,Set<BlockPos> cells,boolean present) {
        boolean previous=syncingGas;syncingGas=true;
        try{if(present)RoomAirState.get(level).refill(cells);else RoomAirState.get(level).evacuate(cells);}
        finally{syncingGas=previous;}
    }
    public void pocketAirChanged(Set<BlockPos> cells,boolean depleted) {
        if(syncingGas)return;
        boolean changed=false;
        for(var pos:cells)if(pocketGas.containsKey(pos)) {
            int units=depleted?0:AirlockCycle.UNITS_PER_CELL;
            if(pocketGas.get(pos)!=units){pocketGas.put(pos,units);changed=true;}
        }
        for(var pos:cells)if(roomGas.containsKey(pos)) {
            int units=depleted?0:AirlockCycle.UNITS_PER_CELL;
            if(roomGas.get(pos)!=units){roomGas.put(pos,units);changed=true;}
        }
        if(changed)setDirty();
    }
    public int measuredPocketGas(BlockPos pos){return pocketGas.getOrDefault(pos,0);}
    private void put(Chamber c) {chambers.put(c.id,c);for(var pos:c.cells)owners.put(pos,c);for(var pos:c.doors)doorOwners.computeIfAbsent(pos,k->new HashSet<>()).add(c);}
    private void remove(Chamber c) {chambers.remove(c.id,c);for(var pos:c.cells)owners.remove(pos,c);for(var pos:c.doors) {var set=doorOwners.get(pos);if(set!=null) {set.remove(c);if(set.isEmpty())doorOwners.remove(pos);}}}
    private static Set<BlockPos> positions(long[] data) {var set=new HashSet<BlockPos>();for(long n:data)set.add(BlockPos.of(n));return set;}
    private static long[] longs(Set<BlockPos> set) {return set.stream().mapToLong(BlockPos::asLong).sorted().toArray();}
    public static AirlockSavedState load(CompoundTag tag,HolderLookup.Provider registries) {
        var result=new AirlockSavedState();for(long n:tag.getLongArray("accounted"))result.accounted.add(n);
        for(var raw:tag.getList("pocketGas",Tag.TAG_COMPOUND)) {
            var t=(CompoundTag)raw;int units=t.getInt("gas");if(units>=0)result.pocketGas.put(BlockPos.of(t.getLong("pos")),units);
        }
        for(var raw:tag.getList("roomGas",Tag.TAG_COMPOUND)) {
            var t=(CompoundTag)raw;int units=t.getInt("gas");if(units>=0)result.roomGas.put(BlockPos.of(t.getLong("pos")),units);
        }
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
        var pockets=new ListTag();pocketGas.forEach((pos,units)->{var t=new CompoundTag();t.putLong("pos",pos.asLong());t.putInt("gas",units);pockets.add(t);});tag.put("pocketGas",pockets);
        var mixed=new ListTag();roomGas.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparingLong(BlockPos::asLong))).forEach(e->{
            var t=new CompoundTag();t.putLong("pos",e.getKey().asLong());t.putInt("gas",e.getValue());mixed.add(t);
        });tag.put("roomGas",mixed);
        tag.put("chambers",list);tag.putLongArray("accounted",accounted.stream().mapToLong(Long::longValue).sorted().toArray());return tag;
    }
}
