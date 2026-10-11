package com.frozendawn.world;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.oxygen.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Observational first slice: loaded known rooms only; never starves, vents, refills or grants oxygen. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class RoomOxygenManager {
    public static final double CORE_CAPACITY=8, BLAST_PIT_CAPACITY=8;
    public record Reading(OxygenStatus.Mode mode,long room,long pool,double supply,double demand,double localDemand,int sources,int rooms) {
        public static Reading waiting(){return new Reading(OxygenStatus.Mode.WAITING,0,0,0,0,0,0,0);}
    }
    private record Topology(RoomAtmosphere.Geometry geometry,BlockPos anchor,Set<Long> chunks) {}
    private record Supply(String id,double capacity,Set<Long> rooms,boolean ready) {}
    private static final class State {
        final Set<BlockPos> gauges=new HashSet<>();final Set<BlockPos> exposed=new HashSet<>();
        final Map<Long,Topology> topology=new HashMap<>();
        final Map<UUID,OxygenAlarmPolicy> alarms=new HashMap<>();
        final Map<BlockPos,Long> cells=new HashMap<>();final Map<BlockPos,Set<Long>> intakes=new HashMap<>();
        List<Supply> supplies=List.of();List<OxygenPools.Burner> burners=List.of();long flameRevision=-1;
        Map<Long,Reading> readings=Map.of();long profileBuilds;boolean dirty=true;
    }
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    private RoomOxygenManager() {}
    public static void register(ServerLevel l,BlockPos p){LEVELS.computeIfAbsent(l,k->new State()).gauges.add(p.immutable());}
    public static void remove(ServerLevel l,BlockPos p){var s=LEVELS.get(l);if(s!=null){s.gauges.remove(p);s.exposed.remove(p);}}
    public static void reset(){LEVELS.clear();}
    /** A cleared geometry cache invalidates telemetry, while loaded gauges retain registration. */
    public static void atmosphereReset(){for(var s:LEVELS.values()){s.exposed.clear();s.readings=Map.of();s.topology.clear();s.cells.clear();s.intakes.clear();s.dirty=true;}}
    @SubscribeEvent public static void changed(RoomChangeEvent e){var s=LEVELS.get(e.level());if(s!=null){s.dirty=true;s.readings=Map.of();
        if(e.change() instanceof RoomChangeEvent.GeometryChange g&&g.complete())for(var p:s.gauges){
            if(g.currentRooms().stream().anyMatch(r->r.geometry().cells().contains(p)))s.exposed.remove(p);
            else if(g.previousRooms().stream().anyMatch(r->r.geometry().cells().contains(p))||g.previousMemberships().stream().anyMatch(r->r.cells().contains(p)))s.exposed.add(p);
        }
    }}
    public static double combustionDemand(BlockState state) {
        if(!VacuumFlames.isOrdinaryFlame(state))return 0;
        if(state.is(Blocks.TORCH)||state.is(Blocks.WALL_TORCH)||state.is(Blocks.LANTERN))return .25;
        if(state.getBlock() instanceof CandleBlock)return .25*state.getValue(CandleBlock.CANDLES);
        if(state.getBlock() instanceof CandleCakeBlock)return .25;
        if(state.is(Blocks.FURNACE)||state.is(Blocks.BLAST_FURNACE)||state.is(Blocks.SMOKER))return 3;
        return 1; // Ordinary campfire/fire, including the existing MiteAway flame.
    }
    public static double animalDemand(Animal animal) {
        var id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
        double rate=switch(id){case "chicken"->.07;case "sheep"->.9;case "pig"->1.3;case "cow","mooshroom"->5;
            case "rabbit"->mass(2);case "cat"->mass(4);case "fox"->mass(8);case "wolf"->mass(35);case "goat"->mass(45);
            case "horse","skeleton_horse","zombie_horse"->mass(450);case "donkey"->mass(200);case "mule"->mass(300);
            case "camel"->mass(600);case "llama","trader_llama"->mass(150);case "polar_bear"->mass(400);case "panda"->mass(100);
            case "ocelot"->mass(12);case "turtle"->mass(150);case "axolotl"->mass(.1);case "bee"->mass(.0002);
            case "parrot"->mass(1);case "frog"->mass(.2);case "armadillo"->mass(5);case "sniffer"->mass(1000);default->1;};
        if(id.equals("skeleton_horse")||id.equals("zombie_horse"))return 0;
        return rate*(animal.isBaby()?.5:1);
    }
    private static double mass(double kg){return Math.pow(kg/70,.75);}
    private static Set<Long> chunks(RoomAtmosphere.Geometry g){var chunks=new HashSet<Long>();for(var p:g.cells())chunks.add(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));for(var p:g.walls())chunks.add(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));return Set.copyOf(chunks);}
    private static boolean loaded(ServerLevel l,Topology t){return t.chunks.stream().allMatch(c->l.getChunkSource().getChunkNow(ChunkPos.getX(c),ChunkPos.getZ(c))!=null);}
    private static void topology(ServerLevel l,State s) {
        var rooms=RoomAtmosphere.cachedRoomViews(l);boolean changed=s.topology.size()!=rooms.size()||s.dirty;
        for(var r:rooms){var old=s.topology.get(r.id());if(old==null||old.geometry!=r.geometry())changed=true;}
        if(!changed)return;s.topology.clear();s.cells.clear();s.intakes.clear();
        for(var r:rooms){var g=r.geometry();var anchor=g.cells().stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
            s.topology.put(r.id(),new Topology(g,anchor,chunks(g)));
            for(var p:g.cells()){s.cells.put(p,r.id());s.intakes.computeIfAbsent(p,k->new HashSet<>()).add(r.id());}
            for(var p:g.walls())s.intakes.computeIfAbsent(p,k->new HashSet<>()).add(r.id());}
        s.profileBuilds++;s.flameRevision=-1;s.dirty=false;
    }
    private static List<Supply> supplies(ServerLevel l,State s,Set<Long> valid) {
        var result=new ArrayList<Supply>();int[] discoveryBudget={2};
        for(var p:GeothermalCoreRegistry.getCores(l).stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList())if(l.isLoaded(p)&&l.getBlockEntity(p) instanceof GeothermalCoreBlockEntity core){
            var rooms=served(s,p,valid);if(!rooms.isEmpty())result.add(new Supply("core:"+p.asLong(),CORE_CAPACITY,rooms,sourceReady(l,s,p,discoveryBudget)));}
        for(var p:BlastPitWarmZoneRegistry.getWarmZones(l))if(l.isLoaded(p)){var rooms=served(s,p,valid);if(!rooms.isEmpty())result.add(new Supply("blast_pit:"+p.asLong(),BLAST_PIT_CAPACITY,rooms,sourceReady(l,s,p,discoveryBudget)));}
        result.sort(Comparator.comparing(Supply::id));return List.copyOf(result);
    }
    /** Inspect at most two unknown loaded intakes per accounting update. Known/open cells are cached.
     * Only sources already touching a tracked room are considered; do not discover unrelated terrain. */
    private static boolean sourceReady(ServerLevel l,State s,BlockPos source,int[] budget) {
        boolean ready=true;
        for(var direction:Direction.values()) {
            var seed=source.relative(direction);
            if(!l.isLoaded(seed)){ready=false;continue;}
            if(!RoomAtmosphere.isPassage(l,seed,l.getBlockState(seed)))continue;
            var seal=RoomAtmosphere.cachedSealAt(l,seed);
            if(s.cells.containsKey(seed)){if(seal==RoomAtmosphere.Seal.UNKNOWN)ready=false;continue;}
            if(seal==RoomAtmosphere.Seal.UNKNOWN&&budget[0]>0){
                budget[0]--;RoomAtmosphere.discoverSealedSourceIntake(l,seed);
                // Terrain changes may invalidate open samples every update. Honor a probe that
                // resolved OPEN now; a newly discovered sealed room still waits for membership.
                seal=RoomAtmosphere.cachedSealAt(l,seed);
            }
            if(seal==RoomAtmosphere.Seal.UNKNOWN||seal==RoomAtmosphere.Seal.SEALED&&!s.cells.containsKey(seed))ready=false;
        }
        return ready;
    }
    private static Set<Long> served(State s,BlockPos p,Set<Long> valid){var result=new HashSet<>(s.intakes.getOrDefault(p,Set.of()));result.retainAll(valid);return Set.copyOf(result);}
    public static void tickLevel(ServerLevel l) {
        if(l.dimension()!=Level.OVERWORLD)return;var s=LEVELS.computeIfAbsent(l,k->new State());topology(l,s);
        var valid=new HashSet<Long>();for(var e:s.topology.entrySet())if(loaded(l,e.getValue())&&RoomAtmosphere.cachedSealAt(l,e.getValue().anchor)==RoomAtmosphere.Seal.SEALED)valid.add(e.getKey());
        var known=Set.copyOf(s.topology.keySet());
        var next=supplies(l,s,known);if(!next.equals(s.supplies)){s.supplies=next;s.flameRevision=-1;}
        var flames=VacuumFlames.snapshot(l);
        if(flames.revision()!=s.flameRevision){var burners=new ArrayList<OxygenPools.Burner>();
            for(var p:flames.positions())if(l.isLoaded(p)){double rate=combustionDemand(l.getBlockState(p));var rooms=served(s,p,known);
                if(rate>0&&!rooms.isEmpty())burners.add(new OxygenPools.Burner(p.asLong(),rate,rooms));}
            s.burners=List.copyOf(burners);s.flameRevision=flames.revision();}
        var breathing=new HashMap<Long,Double>();int checked=0;boolean entitiesComplete=true;
        for(var entity:l.getAllEntities()){if(++checked>16384){entitiesComplete=false;break;}if(!entity.isAlive())continue;
            var room=s.cells.get(entity.blockPosition());if(room==null||!valid.contains(room))continue;
            double rate=entity instanceof Player p?(!p.isCreative()&&!p.isSpectator()?1:0):entity instanceof Animal a?animalDemand(a):0;
            if(rate>0)breathing.merge(room,rate,Double::sum);}
        var budget=OxygenPools.calculate(known,s.supplies.stream().map(v->new OxygenPools.Source(v.id,v.capacity,v.rooms)).toList(),s.burners,breathing);
        var complete=new HashMap<Long,Boolean>();for(long id:known){var t=s.topology.get(id);complete.put(id,valid.contains(id)&&entitiesComplete&&Collections.disjoint(t.chunks,flames.pendingChunks()));}
        // Every room of a shared pool must be ready before any panel calls the pool safe.
        var poolComplete=new HashMap<Long,Boolean>();for(var e:budget.entrySet())poolComplete.merge(e.getValue().pool(),complete.get(e.getKey()),(a,b)->a&&b);
        for(var source:s.supplies)if(!source.ready)for(long room:source.rooms){var b=budget.get(room);if(b!=null)poolComplete.put(b.pool(),false);}
        var readings=new TreeMap<Long,Reading>();
        for(var e:budget.entrySet()){var b=e.getValue();boolean air=!CombustionAtmosphere.isVacuum(l)||!RoomAirState.get(l).isDepleted(s.topology.get(e.getKey()).geometry.cells());
            var mode=OxygenStatus.room(poolComplete.get(b.pool()),air,b.supply(),b.demand());readings.put(e.getKey(),new Reading(mode,e.getKey(),b.pool(),b.supply(),b.demand(),b.localDemand(),b.sources(),b.rooms()));}
        s.readings=Map.copyOf(readings);
        for(var p:List.copyOf(s.gauges))if(l.isLoaded(p)&&l.getBlockEntity(p) instanceof OxygenGaugeBlockEntity gauge)gauge.publish(readingAt(l,p));
        warnListeners(l,s);
    }
    /** One cue per nearby listener, even when several gauges report the same pool. */
    private static void warnListeners(ServerLevel l,State s) {
        var online=new HashSet<UUID>();
        for(var player:l.players()){
            var id=player.getUUID();online.add(id);if(player.isSpectator())continue;
            var mode=OxygenStatus.Mode.OPEN_AIR;int severity=0;
            for(var p:s.gauges)if(l.isLoaded(p)&&p.distSqr(player.blockPosition())<=256){
                var candidate=readingAt(l,p).mode();
                int rank=switch(candidate){case NO_AIR,NO_SEAL->3;case STRAINED->2;case WAITING->1;default->0;};
                if(rank>severity){severity=rank;mode=candidate;}
            }
            var cue=s.alarms.computeIfAbsent(id,k->new OxygenAlarmPolicy()).update(mode,
                    com.frozendawn.event.PlayerTickHandler.isSuffocationImminent(id),l.getGameTime());
            if(cue!=OxygenAlarmPolicy.Cue.NONE)player.playNotifySound(
                    cue==OxygenAlarmPolicy.Cue.CRITICAL?com.frozendawn.init.ModSounds.OXYGEN_GAUGE_CRITICAL.get():com.frozendawn.init.ModSounds.OXYGEN_GAUGE_STRAINED.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS,cue==OxygenAlarmPolicy.Cue.CRITICAL?.85f:.6f,1);
        }
        s.alarms.keySet().retainAll(online);
    }
    /** Cached diagnostic access only: does not inspect geometry or refill any room. */
    public static Reading readingAt(ServerLevel l,BlockPos p) {
        var s=LEVELS.get(l);var seal=RoomAtmosphere.cachedSealAt(l,p);
        if(s!=null&&s.exposed.contains(p)&&l.isLoaded(p))return new Reading(CombustionAtmosphere.isVacuum(l)?OxygenStatus.Mode.NO_SEAL:OxygenStatus.Mode.OPEN_AIR,0,0,0,0,0,0,0);
        if(seal==RoomAtmosphere.Seal.UNKNOWN)return Reading.waiting();
        if(seal==RoomAtmosphere.Seal.OPEN)return new Reading(CombustionAtmosphere.isVacuum(l)?OxygenStatus.Mode.NO_SEAL:OxygenStatus.Mode.OPEN_AIR,0,0,0,0,0,0,0);
        var id=s==null?null:s.cells.get(p);return id==null?Reading.waiting():s.readings.getOrDefault(id,Reading.waiting());
    }
    public static List<Reading> snapshots(ServerLevel l){var s=LEVELS.get(l);return s==null?List.of():s.readings.values().stream().sorted(Comparator.comparingLong(Reading::room)).toList();}
    public static long profileBuilds(ServerLevel l){var s=LEVELS.get(l);return s==null?0:s.profileBuilds;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(e.getServer().overworld().getGameTime()%20==0)tickLevel(e.getServer().overworld());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){reset();}
}
