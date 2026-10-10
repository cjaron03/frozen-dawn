package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.HeatVentBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.*;
import com.frozendawn.thermal.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Air and physical structure reservoirs, integrating loaded rooms once per second with explicit energy budgets. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class RoomThermalManager {
    // First-pass game units. Shared target control budgets actual heater energy and fractional fuel.
    public static final double BASE_HEATER_POWER=1800, FACE_LOSS_SCALE=.25, VACUUM_RADIATION_SCALE=.35;
    private static final int LAYERS=3,SUBSTEPS=20;
    private record Face(BlockPos outside,int boundaryY,double conductance,boolean ground,boolean interior) {}
    private record Profile(Set<BlockPos> structure,Set<BlockPos> watched,List<Face> faces,Set<BlockPos> vents) {}
    private static final class Binding {
        RoomThermalState.Room room; Profile profile; boolean dirty,geometryDirty,suspended;
        Binding(RoomThermalState.Room room,Profile profile){this.room=room;this.profile=profile;}
    }
    private static final class Runtime {
        final RoomThermalState state; final Map<Long,Binding> rooms=new TreeMap<>();
        final Map<Long,RoomAtmosphere.RoomView> pending=new TreeMap<>();
        final Map<BlockPos,Set<Long>> layers=new HashMap<>(),cells=new HashMap<>();
        final ArrayDeque<RoomChangeEvent.Change> changes=new ArrayDeque<>();
        final ArrayDeque<Long> saved=new ArrayDeque<>();
        Map<BlockPos,Integer> owners=Map.of(); boolean reconciling;
        long lastTick=-1,profileBuilds;
        Runtime(RoomThermalState state){this.state=state;saved.addAll(state.rooms.keySet());}
    }
    private static final Map<ServerLevel,Runtime> LEVELS=new WeakHashMap<>();
    private RoomThermalManager() {}
    private static Runtime runtime(ServerLevel level) {
        var state=RoomThermalState.get(level);var existing=LEVELS.get(level);
        if(existing==null||existing.state!=state){existing=new Runtime(state);LEVELS.put(level,existing);}return existing;
    }
    private static String blockId(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    private static boolean loaded(ServerLevel level,RoomThermalState.Room room) {
        for(var pos:room.cells)if(!level.isLoaded(pos))return false;
        for(var pos:room.structure)if(!level.isLoaded(pos))return false;
        return true;
    }
    private static boolean loaded(ServerLevel level,Binding binding) {
        if(!loaded(level,binding.room)||binding.profile==null)return false;
        for(var pos:binding.profile.watched)if(!level.isLoaded(pos))return false;
        return true;
    }
    private static boolean airPresent(ServerLevel level,Set<BlockPos> cells) {
        return !CombustionAtmosphere.isVacuum(level)||!RoomAirState.get(level).isDepleted(cells);
    }
    private static Profile profile(ServerLevel level,Set<RoomAtmosphere.BoundaryFace> faces,Set<BlockPos> knownCells) {
        var structure=new HashSet<BlockPos>();var watched=new HashSet<BlockPos>();var result=new ArrayList<Face>();var vents=new HashSet<BlockPos>();
        for(var face:faces) {
            var pos=face.wallCell();double resistance=0;
            if(level.isLoaded(pos)&&level.getBlockState(pos).getBlock() instanceof HeatVentBlock)vents.add(pos.immutable());
            for(int depth=0;depth<LAYERS;depth++) {
                if(!level.isLoaded(pos))return null;
                watched.add(pos.immutable());var state=level.getBlockState(pos);
                if(RoomAtmosphere.isPassage(level,pos,state))break;
                structure.add(pos.immutable());resistance+=1.0/RoomInsulation.conductance(level,pos,state);
                pos=pos.relative(face.outwardDirection());
            }
            if(!level.isLoaded(pos))return null;watched.add(pos.immutable());
            boolean interior=knownCells.contains(pos)&&RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos));
            boolean ground=!RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos));
            result.add(new Face(pos.immutable(),face.wallCell().getY(),resistance>0?FACE_LOSS_SCALE/resistance:0,ground,interior));
        }
        return new Profile(Set.copyOf(structure),Set.copyOf(watched),List.copyOf(result),Set.copyOf(vents));
    }
    private static Set<BlockPos> knownCells(Runtime rt,Collection<RoomAtmosphere.RoomView> current) {
        var cells=new HashSet<BlockPos>();for(var room:rt.state.rooms.values())if(room.sealed)cells.addAll(room.cells);
        for(var room:current)cells.addAll(room.geometry().cells());return cells;
    }
    private static double outside(ServerLevel level,Face face) {
        var apocalypse=ApocalypseState.get(level.getServer());
        return TemperatureManager.getBackgroundTemperature(face.boundaryY,apocalypse.getCurrentDay(),apocalypse.getTotalDays());
    }
    private static double outsideAverage(ServerLevel level,Profile profile) {
        return profile.faces.stream().filter(face->!face.interior).mapToDouble(face->outside(level,face)).average()
                .orElseGet(()->profile.faces.stream().mapToDouble(face->outside(level,face)).average().orElse(0));
    }
    private static double faceK(ServerLevel level,Face face) {
        return face.interior?0:face.conductance*(CombustionAtmosphere.isVacuum(level)&&!face.ground?VACUUM_RADIATION_SCALE:1);
    }
    // First-pass game conductance, not a real-world watt rating. Vacuum uses the existing radiator scale.
    public static final double HEAT_VENT_CONDUCTANCE=8.0;
    private record Cooling(BlockPos pos,double conductance,double outside) {}
    private static List<Cooling> cooling(ServerLevel level,Binding binding) {
        if(!binding.room.sealed)return List.of();
        var result=new ArrayList<Cooling>();
        for(var pos:binding.profile.vents) {
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof HeatVentBlock)||!HeatVentBlock.active(state))continue;
            var facing=state.getValue(HeatVentBlock.FACING);var inside=pos.relative(facing.getOpposite());var outlet=pos.relative(facing);
            if(!binding.room.cells.contains(inside)||!level.isLoaded(outlet)
                    ||!RoomAtmosphere.isPassage(level,outlet,level.getBlockState(outlet)))continue;
            // An uncovered outdoor face is required. Glass roofs also obstruct radiation.
            // This loaded-only heightmap check cannot dump heat into an undiscovered neighboring room.
            if(outlet.getY()<level.getHeight(Heightmap.Types.MOTION_BLOCKING,outlet.getX(),outlet.getZ()))continue;
            var a=ApocalypseState.get(level.getServer());
            double outside=TemperatureManager.getBackgroundTemperature(outlet.getY(),a.getCurrentDay(),a.getTotalDays());
            double k=HEAT_VENT_CONDUCTANCE*(CombustionAtmosphere.isVacuum(level)?VACUUM_RADIATION_SCALE:1);
            result.add(new Cooling(pos,k,outside));
        }
        return result;
    }
    /** 0 closed, 1 waiting/blocked, 2 rejecting heat. Loaded cached geometry only. */
    public static int ventIndicator(ServerLevel level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof HeatVentBlock)||!HeatVentBlock.active(state))return 0;
        var rt=LEVELS.get(level);if(rt==null)return 1;
        for(var binding:rt.rooms.values()) {
            if(binding.profile==null||binding.suspended||!loaded(level,binding)||!binding.profile.vents.contains(pos))continue;
            var room=binding.room;
            double temperature=room.airPresent?RoomHeatMath.temperature(room.airCapacity(),room.airEnergy)
                    :RoomHeatMath.temperature(capacity(rt.state,room,rt.owners),structureEnergy(rt.state,room,rt.owners));
            for(var outlet:cooling(level,binding))if(outlet.pos.equals(pos)&&temperature>outlet.outside+.1)return 2;
        }
        return 1;
    }
    private static void cool(RoomThermalState state,RoomThermalState.Room room,Map<BlockPos,Integer> owners,
            double capacity,List<Cooling> outlets,double dt) {
        for(var outlet:outlets) {
            double c=room.airPresent?room.airCapacity():capacity;
            double energy=room.airPresent?room.airEnergy:structureEnergy(state,room,owners);
            double temperature=RoomHeatMath.temperature(c,energy);
            // Passive rejection stops at the cold-side temperature; a warmer exterior cannot become a refrigerator.
            double q=Math.min(Math.max(0,energy),Math.max(0,RoomHeatMath.reservoirLoss(c,temperature,outlet.outside,outlet.conductance,dt)));
            if(room.airPresent){room.airEnergy-=q;state.ledger.environmentLoss+=q;}
            else state.ledger.environmentLoss-=addStructure(state,room,owners,-q);
        }
    }
    private static void index(Runtime rt) {
        rt.layers.clear();rt.cells.clear();rt.owners=owners(rt.state);
        for(var binding:rt.rooms.values()) {
            for(var pos:binding.room.cells)rt.cells.computeIfAbsent(pos,ignored->new HashSet<>()).add(binding.room.id);
            if(binding.profile!=null)for(var pos:binding.profile.watched)rt.layers.computeIfAbsent(pos,ignored->new HashSet<>()).add(binding.room.id);
        }
    }
    private static void material(ServerLevel level,Runtime rt,BlockPos pos,double initial) {
        var block=level.getBlockState(pos);var name=blockId(block);var old=rt.state.materials.get(pos);
        if(old!=null&&old.block.equals(name))return;
        if(old!=null)rt.state.ledger.materialOut+=old.energy;
        var fresh=new RoomThermalState.Material(name,RoomHeatMath.MATERIAL_CAPACITY,RoomHeatMath.energy(RoomHeatMath.MATERIAL_CAPACITY,initial));
        rt.state.materials.put(pos,fresh);rt.state.ledger.materialIn+=fresh.energy;
    }
    private static void releaseUnused(Runtime rt) {
        var used=new HashSet<BlockPos>();for(var room:rt.state.rooms.values())used.addAll(room.structure);
        for(var pos:List.copyOf(rt.state.materials.keySet()))if(!used.contains(pos)) {
            rt.state.ledger.materialOut+=rt.state.materials.remove(pos).energy;
        }
    }
    private static void bind(ServerLevel level,Runtime rt,RoomThermalState.Room room,Profile profile) {
        double initial=outsideAverage(level,profile);room.structure=profile.structure;
        for(var pos:profile.structure)material(level,rt,pos,initial);
        rt.rooms.put(room.id,new Binding(room,profile));rt.profileBuilds++;
    }
    /** Complete topology reconciliation consumes each old cell's gas energy at most once. Physical blocks never copy heat. */
    private static void reconcile(ServerLevel level,Runtime rt,RoomChangeEvent.GeometryChange change) {
        var parents=new LinkedHashSet<RoomThermalState.Room>();var affected=new HashSet<BlockPos>();
        for(var previous:change.previousMemberships())affected.addAll(previous.cells());
        for(var previous:change.previousRooms())affected.addAll(previous.geometry().cells());
        for(var current:change.currentRooms())affected.addAll(current.geometry().cells());
        for(var old:rt.state.rooms.values())if(!Collections.disjoint(old.cells,affected))parents.add(old);
        if(!change.complete()) {suspend(rt,parents,change.currentRooms());return;}
        // A neighboring volume's discovery/opening changes an existing face's exterior
        // classification even though no block in this room's pressure boundary changed.
        var neighbors=new HashSet<Long>();
        for(var pos:affected)neighbors.addAll(rt.layers.getOrDefault(pos,Set.of()));
        for(long id:neighbors){var binding=rt.rooms.get(id);if(binding!=null)binding.dirty=true;}
        // A thermal save can predate a pressure split. Resolve every surviving parent cell
        // before consuming it, even when pressure's first query only publishes one child.
        var siblings=new LinkedHashMap<Long,RoomAtmosphere.RoomView>();
        for(var view:change.currentRooms())siblings.put(view.id(),view);
        var covered=new HashSet<BlockPos>();for(var view:siblings.values())covered.addAll(view.geometry().cells());
        var examined=new HashSet<RoomThermalState.Room>();
        while(true) {
            var parent=parents.stream().filter(r->!examined.contains(r)).findFirst().orElse(null);
            if(parent==null)break;examined.add(parent);
            for(var cell:parent.cells) {
                if(!level.isLoaded(cell)){suspend(rt,parents,siblings.values());return;}
                if(covered.contains(cell)||!RoomAtmosphere.isPassage(level,cell,level.getBlockState(cell)))continue;
                var geometry=RoomAtmosphere.inspect(level,cell);
                if(geometry.seal()==RoomAtmosphere.Seal.UNKNOWN){suspend(rt,parents,siblings.values());return;}
                covered.addAll(geometry.cells());
                if(geometry.seal()==RoomAtmosphere.Seal.SEALED) {
                    var sibling=RoomAtmosphere.view(level,cell);
                    if(sibling==null){suspend(rt,parents,siblings.values());return;}
                    siblings.put(sibling.id(),sibling);covered.addAll(sibling.geometry().cells());
                    for(var old:rt.state.rooms.values())if(!Collections.disjoint(old.cells,sibling.geometry().cells()))parents.add(old);
                }
            }
        }
        change=new RoomChangeEvent.GeometryChange(change.previousRooms(),List.copyOf(siblings.values()),change.previousMemberships(),true);
        if(change.currentRooms().isEmpty()) {
            for(var parent:parents) {
                parent.sealed=false;dropAir(rt.state,parent);
                var p=profile(level,parent.faces,knownCells(rt,List.of()));
                if(p!=null)bind(level,rt,parent,p);else if(rt.rooms.containsKey(parent.id))rt.rooms.get(parent.id).suspended=true;
            }
            releaseUnused(rt);index(rt);rt.state.setDirty();return;
        }
        var profiles=new HashMap<Long,Profile>();var cells=knownCells(rt,change.currentRooms());
        for(var current:change.currentRooms()) {
            var p=profile(level,current.geometry().boundaryFaces(),cells);
            if(p==null) {
                suspend(rt,parents,change.currentRooms());return;
            }
            profiles.put(current.id(),p);
        }
        var oldCells=new HashMap<BlockPos,RoomThermalState.Room>();double oldAir=0,reusedAir=0;
        for(var parent:parents){oldAir+=parent.airEnergy;for(var cell:parent.cells)oldCells.put(cell,parent);}
        var next=new ArrayList<RoomThermalState.Room>();
        for(var current:change.currentRooms()) {
            var room=new RoomThermalState.Room(current.id(),current.geometry().cells(),current.geometry().boundaryFaces());
            room.airPresent=airPresent(level,room.cells);double initial=outsideAverage(level,profiles.get(room.id));
            if(room.airPresent)for(var cell:room.cells) {
                var parent=oldCells.get(cell);
                if(parent!=null&&parent.airPresent){double portion=parent.airEnergy/parent.cells.size();room.airEnergy+=portion;reusedAir+=portion;}
                else {double portion=RoomHeatMath.energy(RoomHeatMath.AIR_CAPACITY,initial);room.airEnergy+=portion;rt.state.ledger.airIn+=portion;}
            }
            next.add(room);
        }
        rt.state.ledger.airOut+=Math.max(0,oldAir-reusedAir);
        var parentCells=new HashSet<BlockPos>();for(var parent:parents)parentCells.addAll(parent.cells);
        rt.pending.values().removeIf(view->!Collections.disjoint(view.geometry().cells(),parentCells));
        for(var parent:parents){rt.state.rooms.remove(parent.id);rt.rooms.remove(parent.id);rt.pending.remove(parent.id);}
        for(var room:next){rt.state.rooms.put(room.id,room);bind(level,rt,room,profiles.get(room.id));rt.pending.remove(room.id);}
        releaseUnused(rt);index(rt);rt.state.setDirty();
    }
    private static void suspend(Runtime rt,Collection<RoomThermalState.Room> parents,Collection<RoomAtmosphere.RoomView> current) {
        for(var sibling:current)rt.pending.put(sibling.id(),sibling);
        for(var parent:parents){var binding=rt.rooms.get(parent.id);if(binding!=null){binding.suspended=true;binding.geometryDirty=true;}}
    }
    private static void dispatch(ServerLevel level,Runtime rt,RoomChangeEvent.Change change) {
        rt.changes.add(change);if(rt.reconciling)return;
        rt.reconciling=true;
        try {
            while(!rt.changes.isEmpty()) {
                var next=rt.changes.removeFirst();
                if(next instanceof RoomChangeEvent.GeometryChange geometry)reconcile(level,rt,geometry);
                else if(next instanceof RoomChangeEvent.WallMaterialChange material) {
                    for(var room:material.rooms()){var binding=rt.rooms.get(room.id());if(binding!=null)binding.dirty=true;}
                } else if(next instanceof RoomChangeEvent.AirStateChange air)airChanged(level,rt,air);
            }
        } finally {rt.reconciling=false;}
    }
    private static void dropAir(RoomThermalState state,RoomThermalState.Room room) {
        if(room.airEnergy>0)state.ledger.airOut+=room.airEnergy;room.airEnergy=0;room.airPresent=false;
    }
    private static void airChanged(ServerLevel level,Runtime rt,RoomChangeEvent.AirStateChange change) {
        for(var room:rt.state.rooms.values())if(!Collections.disjoint(room.cells,change.cells())) {
            if(!airPresent(level,room.cells)||!room.sealed)dropAir(rt.state,room);
            else if(!room.airPresent) {
                var binding=rt.rooms.get(room.id);if(binding==null||binding.suspended||binding.profile==null)continue;
                room.airPresent=true;room.airEnergy=RoomHeatMath.energy(room.airCapacity(),outsideAverage(level,binding.profile));
                rt.state.ledger.airIn+=room.airEnergy;
            }
        }
        rt.state.setDirty();
    }
    @SubscribeEvent public static void roomChanged(RoomChangeEvent event) {
        var level=event.level();if(level.dimension()!=Level.OVERWORLD)return;var rt=runtime(level);
        dispatch(level,rt,event.change());
    }
    /** Own layer index catches changes beyond the first pressure boundary. Cosmetic heater LEDs keep their heat. */
    public static void blockChanged(ServerLevel level,BlockPos pos,BlockState before,BlockState after) {
        var rt=LEVELS.get(level);if(rt==null||before==after)return;
        boolean geometry=RoomAtmosphere.isPassage(level,pos,before)!=RoomAtmosphere.isPassage(level,pos,after);
        if(!geometry&&before.getBlock()==after.getBlock())return;
        var ids=new HashSet<>(rt.layers.getOrDefault(pos,Set.of()));ids.addAll(rt.cells.getOrDefault(pos,Set.of()));
        for(long id:ids){var binding=rt.rooms.get(id);if(binding!=null){binding.dirty=true;if(geometry)binding.geometryDirty=true;}}
        var material=rt.state.materials.get(pos);
        if(material!=null&&(before.getBlock()!=after.getBlock()||RoomAtmosphere.isPassage(level,pos,after))) {
            rt.state.materials.remove(pos);rt.state.ledger.materialOut+=material.energy;rt.state.setDirty();
        }
    }
    private static Map<BlockPos,Integer> owners(RoomThermalState state) {
        var owners=new HashMap<BlockPos,Integer>();for(var room:state.rooms.values())for(var pos:room.structure)owners.merge(pos,1,Integer::sum);return owners;
    }
    private static double capacity(RoomThermalState state,RoomThermalState.Room room,Map<BlockPos,Integer> owners) {
        double result=0;for(var pos:room.structure){var material=state.materials.get(pos);if(material!=null)result+=material.capacity/owners.get(pos);}return result;
    }
    private static double structureEnergy(RoomThermalState state,RoomThermalState.Room room,Map<BlockPos,Integer> owners) {
        double result=0;for(var pos:room.structure){var material=state.materials.get(pos);if(material!=null)result+=material.energy/owners.get(pos);}return result;
    }
    /** Distribute into the owned capacity shares, preserving a single physical reservoir and preventing negative energy. */
    private static double addStructure(RoomThermalState state,RoomThermalState.Room room,Map<BlockPos,Integer> owners,double amount) {
        double capacity=capacity(state,room,owners);if(capacity<=0)return 0;
        if(amount<0)for(var pos:room.structure){var material=state.materials.get(pos);if(material==null)continue;
            double weight=material.capacity/owners.get(pos)/capacity;if(weight>0)amount=Math.max(amount,-material.energy/weight);}
        for(var pos:room.structure){var material=state.materials.get(pos);if(material!=null)material.energy+=amount*material.capacity/owners.get(pos)/capacity;}
        return amount;
    }
    private static boolean connects(RoomThermalState.Room room,BlockPos source) {
        return room.cells.contains(source)||room.faces.stream().anyMatch(face->face.wallCell().equals(source));
    }
    public static boolean managesHeater(ServerLevel level, BlockPos pos) {
        var rt = LEVELS.get(level);
        return rt != null && rt.rooms.values().stream().anyMatch(binding -> connects(binding.room, pos));
    }
    private static Map<ThermalHeaterBlockEntity, Double> heaterShares(ServerLevel level, Binding binding, List<Binding> loaded) {
        var shares = new LinkedHashMap<ThermalHeaterBlockEntity, Double>();
        for (var pos : HeaterRegistry.getHeaters(level)) if (level.isLoaded(pos) && connects(binding.room, pos)
                && level.getBlockEntity(pos) instanceof ThermalHeaterBlockEntity heater && heater.isLit()) {
            long count = loaded.stream().filter(other -> connects(other.room, pos)).count();
            double full = BASE_HEATER_POWER * Math.max(0, heater.getPublicHeatOutput() - heater.getFrostmiteHeatPenalty()) / 35.0
                    * FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get() / Math.max(1, count);
            shares.put(heater, full);
        }
        return shares;
    }
    private static double power(ServerLevel level, Binding binding, List<Binding> loaded) {
        return heaterShares(level,binding,loaded).entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * entry.getKey().getBurnFraction()).sum();
    }
    /** Rehydrate at most two saved records per second, without a player query or forced chunks. */
    private static void rebindSaved(ServerLevel level,Runtime rt) {
        for(int job=0,limit=Math.min(2,rt.saved.size());job<limit;job++) {
            long id=rt.saved.removeFirst();var saved=rt.state.rooms.get(id);
            if(saved==null||rt.rooms.containsKey(id))continue;
            if(!loaded(level,saved)){rt.saved.addLast(id);continue;}
            var anchor=saved.cells.stream().filter(pos->RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos))).findFirst().orElse(null);
            if(anchor==null) {
                dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),List.of(),
                        Set.of(new RoomIdentityState.Membership(saved.id,saved.cells)),true));continue;
            }
            var geometry=RoomAtmosphere.inspect(level,anchor);
            if(geometry.seal()==RoomAtmosphere.Seal.UNKNOWN){rt.saved.addLast(id);continue;}
            var view=RoomAtmosphere.view(level,anchor);
            if(view!=null)dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),List.of(view),
                    Set.of(new RoomIdentityState.Membership(saved.id,saved.cells)),true));
            else dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),List.of(),
                    Set.of(new RoomIdentityState.Membership(saved.id,saved.cells)),true));
            if(rt.state.rooms.containsKey(id)&&!rt.rooms.containsKey(id))rt.saved.addLast(id);
        }
    }
    public static void tickLevel(ServerLevel level) {
        if(level.dimension()!=Level.OVERWORLD)return;var rt=runtime(level);if(rt.lastTick==level.getGameTime())return;rt.lastTick=level.getGameTime();
        rebindSaved(level,rt);
        for(var pending:List.copyOf(rt.pending.values())) {
            var parents=RoomIdentityState.get(level).overlapping(pending.geometry().cells());
            // Pressure performs the full sibling/parent reconciliation, not an anchor-only heat claim.
            var anchor=pending.geometry().cells().iterator().next();RoomAtmosphere.view(level,anchor);
            var current=RoomAtmosphere.trackedRooms(level).stream().filter(room->!Collections.disjoint(room.geometry().cells(),
                    parents.stream().flatMap(parent->parent.cells().stream()).collect(java.util.stream.Collectors.toSet()))).toList();
            if(!current.isEmpty())dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),current,parents,true));
        }
        for(var binding:List.copyOf(rt.rooms.values()))if(binding.geometryDirty&&loaded(level,binding.room)) {
            var anchor=binding.room.cells.stream().filter(pos->RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos))).findFirst().orElse(null);
            if(anchor==null) {
                dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),List.of(),
                        Set.of(new RoomIdentityState.Membership(binding.room.id,binding.room.cells)),true));continue;
            }
            var view=RoomAtmosphere.view(level,anchor);
            if(rt.rooms.get(binding.room.id)!=binding)continue;
            boolean certain=view!=null&&view.geometry().cells().equals(binding.room.cells)
                    ||!binding.room.sealed&&RoomAtmosphere.inspect(level,anchor).seal()==RoomAtmosphere.Seal.OPEN;
            binding.geometryDirty=!certain;binding.suspended=!certain;
        }
        var known=knownCells(rt,List.of());boolean changed=false;
        for(var binding:List.copyOf(rt.rooms.values())) {
            if(!loaded(level,binding)){binding.suspended=true;binding.geometryDirty=true;continue;}
            if(binding.geometryDirty)continue;
            if(binding.dirty||binding.suspended) {
                var p=profile(level,binding.room.faces,known);if(p==null){binding.suspended=true;continue;}
                bind(level,rt,binding.room,p);changed=true;
            }
        }
        if(changed){releaseUnused(rt);index(rt);}
        ThermostatManager.refresh(level);
        var loaded=rt.rooms.values().stream().filter(binding->!binding.suspended&&loaded(level,binding)).toList();
        var owners=rt.owners;double dt=1.0/SUBSTEPS;
        var powers=new HashMap<Long,Double>();var losses=new HashMap<Long,double[]>();var coolers=new HashMap<Long,List<Cooling>>();
        var sourceShares = new HashMap<Long, Map<ThermalHeaterBlockEntity,Double>>();
        var suppliedByRoom = new HashMap<Long,Double>();
        for(var binding:loaded) {
            coolers.put(binding.room.id,cooling(level,binding));
            var shares = heaterShares(level,binding,loaded);
            sourceShares.put(binding.room.id,shares);
            powers.put(binding.room.id,shares.entrySet().stream().mapToDouble(entry -> entry.getValue() * entry.getKey().availableRoomFraction()).sum());
            double k=0,weighted=0;
            for(var face:binding.profile.faces){double coefficient=faceK(level,face);k+=coefficient;weighted+=coefficient*outside(level,face);}
            losses.put(binding.room.id,new double[]{k,k>0?weighted/k:0});
        }
        for(int step=0;step<SUBSTEPS;step++)for(var binding:loaded) {
            var room=binding.room;double capacity=capacity(rt.state,room,owners);if(capacity<=0)continue;
            double wallsBefore = RoomHeatMath.temperature(capacity,structureEnergy(rt.state,room,owners));
            double supplied = room.airPresent
                    ? HeaterControl.airGrant(powers.get(room.id)*dt,room.airCapacity(),capacity,
                            RoomHeatMath.temperature(room.airCapacity(),room.airEnergy),wallsBefore,room.faces.size()*4.0,dt,room.sealed?ThermostatManager.roomTarget(level,room.id):HeaterControl.DEFAULT_TARGET)
                    : HeaterControl.wallGrant(powers.get(room.id)*dt,capacity,wallsBefore,wallTarget(level,room,binding.profile));
            suppliedByRoom.merge(room.id,supplied,Double::sum);
            if(room.airPresent)room.airEnergy+=supplied;
            else supplied=addStructure(rt.state,room,owners,supplied*RoomHeatMath.VACUUM_HEATER_EFFICIENCY);
            rt.state.ledger.heater+=supplied;
            double structure=RoomHeatMath.temperature(capacity,structureEnergy(rt.state,room,owners));
            if(room.airPresent) {
                double air=RoomHeatMath.temperature(room.airCapacity(),room.airEnergy);
                double q=RoomHeatMath.exchange(room.airCapacity(),capacity,air,structure,room.faces.size()*4.0,dt);
                double transferred=addStructure(rt.state,room,owners,q);room.airEnergy-=transferred;
                structure=RoomHeatMath.temperature(capacity,structureEnergy(rt.state,room,owners));
            }
            var loss=losses.get(room.id);
            if(loss[0]>0){double q=RoomHeatMath.reservoirLoss(capacity,structure,loss[1],loss[0],dt);
                rt.state.ledger.environmentLoss-=addStructure(rt.state,room,owners,-q);}
            cool(rt.state,room,owners,capacity,coolers.get(room.id),dt);
        }
        // Shared boundary heaters are debited once, with their allocated fraction from every room.
        var fractions = new LinkedHashMap<ThermalHeaterBlockEntity,Double>();
        var readings = new HashMap<ThermalHeaterBlockEntity,Binding>();
        for (var binding : loaded) {
            double available = powers.get(binding.room.id);
            double demand = available > 0 ? suppliedByRoom.getOrDefault(binding.room.id,0.0)/available : 0;
            for (var entry : sourceShares.get(binding.room.id).entrySet()) {
                var heater = entry.getKey();
                double full = BASE_HEATER_POWER * Math.max(0,heater.getPublicHeatOutput()-heater.getFrostmiteHeatPenalty()) / 35.0
                        * FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get();
                double share = full > 0 ? entry.getValue()/full : 0;
                fractions.merge(heater,demand*heater.availableRoomFraction()*share,Double::sum);
                readings.putIfAbsent(heater,binding);
            }
        }
        for (var entry : fractions.entrySet()) {
            var binding = readings.get(entry.getKey());var room=binding.room;
            double temperature = room.airPresent ? RoomHeatMath.temperature(room.airCapacity(),room.airEnergy)
                    : RoomHeatMath.temperature(capacity(rt.state,room,owners),structureEnergy(rt.state,room,owners));
            entry.getKey().consumeRoomHeating(entry.getValue(),temperature,room.airPresent);
            var control=room.sealed?ThermostatManager.roomControl(level,room.id):null;
            if(control!=null){double base=room.airPresent?temperature:outsideAverage(level,binding.profile)+(temperature-outsideAverage(level,binding.profile))*RoomHeatMath.RADIANT_FEEL_WEIGHT;
                entry.getKey().thermostatStatus(control.terms().apply(base),control.panel().target(),room.airPresent);}

        }
        ThermostatManager.publish(level);
        if(!loaded.isEmpty())rt.state.setDirty();
    }
    private static double wallTarget(ServerLevel level,RoomThermalState.Room room,Profile profile) {
        var control=room.sealed?ThermostatManager.roomControl(level,room.id):null;
        if(control==null)return HeaterControl.DEFAULT_TARGET;
        double outside=outsideAverage(level,profile);
        return outside+(ThermostatManager.roomTarget(level,room.id)-outside)/RoomHeatMath.RADIANT_FEEL_WEIGHT;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level=event.getServer().overworld();if(level.getGameTime()%20==0)tickLevel(level);
    }
    public record Snapshot(long id,int cells,boolean sealed,boolean suspended,boolean airPresent,double airTemperature,
            double structureTemperature,double feltTemperature,double airCapacity,double structureCapacity,double conductance,
            double heaterPower,long profileBuilds,double totalEnergy,double ledgerNet,int coolingVents,double coolingConductance) {}
    private static Snapshot snapshot(ServerLevel level,Runtime rt,Binding binding,Map<BlockPos,Integer> owners) {
        var room=binding.room;double capacity=capacity(rt.state,room,owners);
        double structure=RoomHeatMath.temperature(capacity,structureEnergy(rt.state,room,owners));
        double air=RoomHeatMath.temperature(room.airCapacity(),room.airEnergy);
        double outside=outsideAverage(level,binding.profile);
        double felt=room.airPresent?air:outside+(structure-outside)*RoomHeatMath.RADIANT_FEEL_WEIGHT;
        var outlets=binding.suspended||!loaded(level,binding)?List.<Cooling>of():cooling(level,binding);
        return new Snapshot(room.id,room.cells.size(),room.sealed,binding.suspended,room.airPresent,air,structure,felt,
                room.airCapacity(),capacity,binding.profile.faces.stream().mapToDouble(face->faceK(level,face)).sum(),
                power(level,binding,rt.rooms.values().stream().filter(other->!other.suspended&&loaded(level,other)).toList()),
                rt.profileBuilds,rt.state.totalEnergy(),rt.state.ledger.net(),outlets.size(),outlets.stream().mapToDouble(Cooling::conductance).sum());
    }
    /** Non-querying diagnostics; never initialize a saved room, simulate time, or extend pressure activity. */
    public static List<Snapshot> snapshots(ServerLevel level) {
        var rt=LEVELS.get(level);if(rt==null)return List.of();var owners=rt.owners;
        return rt.rooms.values().stream().filter(binding->binding.profile!=null).map(binding->snapshot(level,rt,binding,owners)).toList();
    }
    public static OptionalDouble temperatureAt(ServerLevel level,BlockPos pos) {
        if(level.dimension()!=Level.OVERWORLD)return OptionalDouble.empty();
        var view=RoomAtmosphere.view(level,pos);var rt=runtime(level);
        if(view!=null&&!rt.rooms.containsKey(view.id()))dispatch(level,rt,new RoomChangeEvent.GeometryChange(List.of(),List.of(view),
                RoomIdentityState.get(level).overlapping(view.geometry().cells()),true));
        Binding binding=null;
        for(var candidate:rt.rooms.values())if(candidate.room.cells.contains(pos)){binding=candidate;break;}
        if(binding==null&&view==null) {
            // An opened saved room can retain hot material on reload; validate OPEN, never trust UNKNOWN.
            var saved=rt.state.rooms.values().stream().filter(room->room.cells.contains(pos)).findFirst().orElse(null);
            if(saved!=null&&loaded(level,saved)&&RoomAtmosphere.inspect(level,pos).seal()==RoomAtmosphere.Seal.OPEN) {
                saved.sealed=false;dropAir(rt.state,saved);var p=profile(level,saved.faces,knownCells(rt,List.of()));
                if(p!=null){bind(level,rt,saved,p);index(rt);rt.state.setDirty();binding=rt.rooms.get(saved.id);}
            }
        }
        if(binding==null||binding.suspended||!loaded(level,binding))return OptionalDouble.empty();
        if(view==null&&RoomAtmosphere.inspect(level,pos).seal()!=RoomAtmosphere.Seal.OPEN)return OptionalDouble.empty();
        return OptionalDouble.of(feltAt(level,rt,binding));
    }
    private static double feltAt(ServerLevel level,Runtime rt,Binding binding) {
        var room=binding.room;
        if(room.airPresent)return RoomHeatMath.temperature(room.airCapacity(),room.airEnergy);
        double capacity=capacity(rt.state,room,rt.owners),outside=outsideAverage(level,binding.profile);
        double structure=RoomHeatMath.temperature(capacity,structureEnergy(rt.state,room,rt.owners));
        return outside+(structure-outside)*RoomHeatMath.RADIANT_FEEL_WEIGHT;
    }
    /** Catch-up and mob probes never discover geometry, extend activity or initialize SavedData. */
    public static OptionalDouble cachedTemperatureAt(ServerLevel level,BlockPos pos) {
        var rt=LEVELS.get(level);if(rt==null)return OptionalDouble.empty();
        for(long id:rt.cells.getOrDefault(pos,Set.of())) {
            var binding=rt.rooms.get(id);
            if(binding!=null&&!binding.suspended&&!binding.geometryDirty&&binding.profile!=null&&loaded(level,binding))
                return OptionalDouble.of(feltAt(level,rt,binding));
        }
        return OptionalDouble.empty();
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){reset();ThermostatManager.reset();}
    public static void reset(){LEVELS.clear();}
}
