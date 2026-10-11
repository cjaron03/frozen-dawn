package com.frozendawn.world;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.data.RoomOxygenState;
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
/** Loaded shared budgets and combustion shedding. Oxygen scarcity never vents or refills room gas. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class RoomOxygenManager {
    public static final double CORE_CAPACITY=8, BLAST_PIT_CAPACITY=8;
    public record Reading(OxygenStatus.Mode mode,long room,long pool,double supply,double demand,double localDemand,int sources,int rooms) {
        public static Reading waiting(){return new Reading(OxygenStatus.Mode.WAITING,0,0,0,0,0,0,0);}
    }
    private record Topology(RoomAtmosphere.Geometry geometry,BlockPos anchor,Set<Long> chunks) {}
    private record Supply(String id,double capacity,Set<Long> rooms,boolean ready) {}
    /** Value geometry and physical source membership, never transient cache object identity. */
    private record PoolProfile(Map<Long,RoomAtmosphere.Geometry> rooms,List<OxygenPools.Source> sources) {}
    private static final class State {
        final Set<BlockPos> gauges=new HashSet<>();final Set<BlockPos> exposed=new HashSet<>();
        final Set<BlockPos> starvedFurnaces=new HashSet<>();
        final FurnaceOxygenPolicy furnacePolicy=new FurnaceOxygenPolicy();
        final BreathingOxygenPolicy breathingPolicy=new BreathingOxygenPolicy();
        final Set<Long> oxygenDirtyPools=new HashSet<>(), unavailablePools=new HashSet<>();
        final Map<Long,PoolProfile> furnacePools=new HashMap<>(), furnaceRecoveryProfiles=new HashMap<>();
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
    public static void registerStarvedFurnace(ServerLevel l,BlockPos p){
        LEVELS.computeIfAbsent(l,k->new State()).starvedFurnaces.add(p.immutable());
        // Saved sleepers renew loaded geometry even without a nearby player or gauge.
        if(l.getGameTime()%20==0&&l.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)
            RoomAtmosphere.keepAlive(furnace);
    }
    public static void reset(){LEVELS.clear();}
    /** A cleared geometry cache invalidates telemetry, while loaded gauges retain registration. */
    public static void atmosphereReset(){for(var s:LEVELS.values()){s.exposed.clear();s.readings=Map.of();s.topology.clear();s.cells.clear();s.intakes.clear();s.dirty=true;s.furnacePolicy.reset();s.furnacePools.clear();s.furnaceRecoveryProfiles.clear();s.breathingPolicy.reset();s.oxygenDirtyPools.clear();s.unavailablePools.clear();}}
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
        for(var r:rooms){var old=s.topology.get(r.id());if(old==null||!old.geometry.equals(r.geometry()))changed=true;}
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
        var next=supplies(l,s,known);if(!next.equals(s.supplies)){
            s.supplies=next;s.flameRevision=-1;
        }
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
        budget=controlCombustion(l,s,budget,poolComplete);
        controlBreathing(l,s,budget,poolComplete,breathing);
        var readings=new TreeMap<Long,Reading>();
        for(var e:budget.entrySet()){var b=e.getValue();boolean air=!CombustionAtmosphere.isVacuum(l)||!RoomAirState.get(l).isDepleted(s.topology.get(e.getKey()).geometry.cells());
            var mode=OxygenStatus.room(poolComplete.get(b.pool()),air,b.supply(),b.demand());
            if(air&&poolComplete.get(b.pool())&&CombustionAtmosphere.isVacuum(l)&&s.unavailablePools.contains(b.pool()))mode=OxygenStatus.Mode.NO_OXYGEN;readings.put(e.getKey(),new Reading(mode,e.getKey(),b.pool(),b.supply(),b.demand(),b.localDemand(),b.sources(),b.rooms()));}
        s.readings=Map.copyOf(readings);
        for(var p:List.copyOf(s.gauges))if(l.isLoaded(p)&&l.getBlockEntity(p) instanceof OxygenGaugeBlockEntity gauge)gauge.publish(readingAt(l,p));
        warnListeners(l,s);
    }
    private static boolean vanillaFurnace(BlockState state) {
        return state.is(Blocks.FURNACE)||state.is(Blocks.BLAST_FURNACE)||state.is(Blocks.SMOKER);
    }
    private static Map<Long,OxygenPools.Budget> controlCombustion(ServerLevel l,State s,
            Map<Long,OxygenPools.Budget> initial,Map<Long,Boolean> complete) {
        var budget=new TreeMap<>(initial);
        var pools=new TreeSet<Long>();for(var b:budget.values())pools.add(b.pool());
        var profiles=new HashMap<Long,PoolProfile>();
        for(long pool:pools){
            var rooms=new TreeMap<Long,RoomAtmosphere.Geometry>();
            for(var e:budget.entrySet())if(e.getValue().pool()==pool)rooms.put(e.getKey(),s.topology.get(e.getKey()).geometry);
            var sources=s.supplies.stream().filter(v->!Collections.disjoint(v.rooms,rooms.keySet()))
                    .map(v->new OxygenPools.Source(v.id,v.capacity,v.rooms)).toList();
            var profile=new PoolProfile(Map.copyOf(rooms),sources);profiles.put(pool,profile);
            if(!profile.equals(s.furnacePools.put(pool,profile))){
                s.furnacePolicy.resetPool(pool);s.breathingPolicy.resetPool(pool);s.oxygenDirtyPools.add(pool);
            }
        }
        s.furnacePools.keySet().retainAll(pools);
        long now=l.getGameTime();boolean vacuum=CombustionAtmosphere.isVacuum(l);
        var poolAir=new HashMap<Long,Boolean>();
        for(var e:budget.entrySet())poolAir.merge(e.getValue().pool(),
                !RoomAirState.get(l).isDepleted(s.topology.get(e.getKey()).geometry.cells()),(a,b)->a&&b);
        for(long pool:pools){
            var b=budget.values().stream().filter(v->v.pool()==pool).findFirst().orElseThrow();
            boolean ready=complete.getOrDefault(pool,false)&&vacuum&&poolAir.getOrDefault(pool,false);
            if(!s.furnacePolicy.shouldShed(pool,now,ready,b.demand()>b.supply()+1e-8))continue;
            // Only the existing indexed burners are candidates; no room-cell scan or chunk load.
            var candidate=s.burners.stream().filter(v->{
                var room=v.rooms().stream().filter(budget::containsKey).min(Long::compare).orElse(null);
                return room!=null&&budget.get(room).pool()==pool&&l.isLoaded(BlockPos.of(v.id()))
                        &&VacuumFlames.isOrdinaryFlame(l.getBlockState(BlockPos.of(v.id())));
            }).sorted(Comparator.comparingDouble(OxygenPools.Burner::demand).reversed().thenComparingLong(OxygenPools.Burner::id)).findFirst();
            if(candidate.isPresent()){
                var burner=candidate.get();var pos=BlockPos.of(burner.id());
                var state=l.getBlockState(pos);
                if(vanillaFurnace(state)&&l.getBlockEntity(pos) instanceof OxygenControlledFurnace furnace){
                    furnace.frozendawn$starveForOxygen();s.starvedFurnaces.add(pos);
                    adjustBudget(budget,pool,burner.rooms(),-burner.demand());
                    FrozenDawn.LOGGER.info("[RoomO2] Furnace paused at {} | pool {} supply {} demand {} -> {}",pos,pool,b.supply(),b.demand(),b.demand()-burner.demand());
                } else if(!vanillaFurnace(state)&&VacuumFlames.extinguishForOxygen(l,pos)){
                    adjustBudget(budget,pool,burner.rooms(),-burner.demand());
                    FrozenDawn.LOGGER.info("[RoomO2] Flame snuffed {} at {} | pool {} supply {} demand {} -> {}",
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),pos,pool,b.supply(),b.demand(),b.demand()-burner.demand());
                }
            }
        }
        // Admission includes each restart immediately, so multiple sleepers cannot claim the same spare supply.
        for(var pos:s.starvedFurnaces.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()){
            if(!l.isLoaded(pos)||!vanillaFurnace(l.getBlockState(pos))
                    ||!(l.getBlockEntity(pos) instanceof OxygenControlledFurnace furnace)||!furnace.frozendawn$isOxygenStarved()){
                s.starvedFurnaces.remove(pos);s.furnacePolicy.canResume(pos.asLong(),now,false);continue;
            }
            var rooms=s.intakes.getOrDefault(pos,Set.of());
            var room=rooms.stream().filter(budget::containsKey).min(Long::compare).orElse(null);
            var b=room==null?null:budget.get(room);
            var profile=b==null?null:profiles.get(b.pool());
            if(!Objects.equals(profile,s.furnaceRecoveryProfiles.put(pos.asLong(),profile)))
                s.furnacePolicy.resetRecovery(pos.asLong());
            boolean supported=!vacuum || b!=null&&complete.getOrDefault(b.pool(),false)&&poolAir.getOrDefault(b.pool(),false)
                    &&b.demand()+3<=b.supply()+1e-8;
            if(!s.furnacePolicy.canResume(pos.asLong(),now,supported))continue;
            // Legacy air authority must also permit combustion; never restart into a breach/vacuum.
            if(!CombustionAtmosphere.canBurnAt(l,pos))continue;
            furnace.frozendawn$resumeForOxygen();
            s.starvedFurnaces.remove(pos);
            if(b!=null)adjustBudget(budget,b.pool(),rooms,3);
            FrozenDawn.LOGGER.info("[RoomO2] Furnace resumed at {} after stable O2 support",pos);
        }
        var sleepers=s.starvedFurnaces.stream().map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        s.furnacePolicy.retain(pools,sleepers);s.furnaceRecoveryProfiles.keySet().retainAll(sleepers);
        return budget;
    }
    /** Separate binary O2 latch. No pressure transitions, gas refills or thermal mutations. */
    private static void controlBreathing(ServerLevel l,State s,Map<Long,OxygenPools.Budget> budget,
            Map<Long,Boolean> complete,Map<Long,Double> breathing) {
        var saved=RoomOxygenState.get(l);var pools=new TreeSet<Long>();
        for(var b:budget.values())pools.add(b.pool());
        var rates=new HashMap<Long,Double>();
        for(var e:breathing.entrySet())if(budget.containsKey(e.getKey()))rates.merge(budget.get(e.getKey()).pool(),e.getValue(),Double::sum);
        for(long pool:pools){
            var profile=s.furnacePools.get(pool);boolean changed=s.oxygenDirtyPools.remove(pool);
            // Load persisted cell membership only when topology/source profile changes.
            if(changed){
                boolean unavailable=profile.rooms.values().stream().anyMatch(g->saved.isUnavailable(g.cells()));
                if(unavailable)s.unavailablePools.add(pool);else s.unavailablePools.remove(pool);
            }
            var b=budget.values().stream().filter(v->v.pool()==pool).findFirst().orElseThrow();
            boolean old=s.unavailablePools.contains(pool);
            boolean ready=CombustionAtmosphere.isVacuum(l)&&complete.getOrDefault(pool,false)
                    &&profile.rooms.values().stream().noneMatch(g->RoomAirState.get(l).isDepleted(g.cells()));
            boolean flames=s.burners.stream().anyMatch(v->{
                var room=v.rooms().stream().filter(budget::containsKey).min(Long::compare).orElse(null);
                return room!=null&&budget.get(room).pool()==pool&&l.isLoaded(BlockPos.of(v.id()))
                        &&VacuumFlames.isOrdinaryFlame(l.getBlockState(BlockPos.of(v.id())));
            });
            boolean unavailable=s.breathingPolicy.update(pool,l.getGameTime(),ready,
                    rates.getOrDefault(pool,0.0)>b.supply()+1e-8,flames,b.demand()<=b.supply()+1e-8,old);
            if(unavailable)s.unavailablePools.add(pool);else s.unavailablePools.remove(pool);
            if(ready&&(unavailable!=old||changed&&unavailable)){
                for(var g:profile.rooms.values())saved.setUnavailable(g.cells(),unavailable);
                CombustionAtmosphere.reset();
            }
            if(unavailable!=old)FrozenDawn.LOGGER.info("[RoomO2] Breathing O2 {} | pool {} supply {} breathing {} demand {} | gas retained",
                    unavailable?"unavailable":"restored",pool,b.supply(),rates.getOrDefault(pool,0.0),b.demand());
        }
        s.breathingPolicy.retain(pools);s.unavailablePools.retainAll(pools);s.oxygenDirtyPools.retainAll(pools);
    }
    /** Cached persisted authority; never discovers a room or changes gas. Early atmosphere bypasses scarcity. */
    public static boolean allowsBreathingAt(Level level,BlockPos pos) {
        return !CombustionAtmosphere.isVacuum(level)||!(level instanceof ServerLevel l)||!RoomOxygenState.get(l).unavailableAt(pos);
    }
    private static void adjustBudget(Map<Long,OxygenPools.Budget> budget,long pool,Set<Long> touched,double delta) {
        var owner=touched.stream().filter(budget::containsKey).min(Long::compare).orElseThrow();
        budget.replaceAll((room,b)->b.pool()!=pool?b:new OxygenPools.Budget(b.pool(),b.supply(),b.demand()+delta,
                b.localDemand()+(room.equals(owner)?delta:0),b.sources(),b.rooms()));
    }
    /** One cue per nearby listener, even when several gauges report the same pool. */
    private static void warnListeners(ServerLevel l,State s) {
        var online=new HashSet<UUID>();
        for(var player:l.players()){
            var id=player.getUUID();online.add(id);if(player.isSpectator())continue;
            var mode=OxygenStatus.Mode.OPEN_AIR;int severity=0;
            for(var p:s.gauges)if(l.isLoaded(p)&&p.distSqr(player.blockPosition())<=256){
                var candidate=readingAt(l,p).mode();
                int rank=switch(candidate){case NO_AIR,NO_SEAL,NO_OXYGEN->3;case STRAINED->2;case WAITING->1;default->0;};
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
