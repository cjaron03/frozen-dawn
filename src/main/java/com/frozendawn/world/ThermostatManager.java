package com.frozendawn.world;
import com.frozendawn.block.*;
import com.frozendawn.data.ApocalypseState;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
/** Loaded-only room controllers. A local sensor includes Core warmth without adding a new heat source. */
public final class ThermostatManager {
    public static final int WAITING=0,SEALED=1,OPEN=2,OVERRIDDEN=3,NO_SEAL=4;
    public record Reading(int mode,double sensed,double base,int heaters,double duty) {
        public static Reading waiting(){return new Reading(WAITING,Double.NaN,Double.NaN,0,0);}
    }
    public record Control(ThermostatBlockEntity panel,TemperatureManager.LocalThermalTerms terms) {
        public double baseTarget(){return terms.baseTarget(panel.target());}
    }
    private record View(ThermostatBlockEntity panel,RoomAtmosphere.RoomView room,boolean open,int mode) {}
    private static final class State {
        final Set<BlockPos> positions=new HashSet<>();List<View> views=List.of();
        final Map<BlockPos,Integer> openTargets=new HashMap<>();
        final Map<BlockPos,ThermalHeaterBlockEntity> installedOpen=new HashMap<>();
        final Map<Long,Control> rooms=new HashMap<>();final Map<BlockPos,Double> openDuties=new HashMap<>();
    }
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    private static final Comparator<ThermostatBlockEntity> PRIORITY=Comparator.comparingLong(ThermostatBlockEntity::order).thenComparingLong(p->p.getBlockPos().asLong());
    private ThermostatManager(){}
    public static void register(ServerLevel l,BlockPos p){LEVELS.computeIfAbsent(l,k->new State()).positions.add(p.immutable());}
    public static void remove(ServerLevel l,BlockPos p){var s=LEVELS.get(l);if(s!=null){s.positions.remove(p);s.rooms.clear();s.openDuties.clear();s.openTargets.clear();s.installedOpen.clear();}}
    public static void reset(){LEVELS.clear();}
    public static Control roomControl(ServerLevel l,long id){var s=LEVELS.get(l);return s==null?null:s.rooms.get(id);}
    public static OptionalDouble openDuty(ServerLevel l,BlockPos p){var s=LEVELS.get(l);return s==null||!s.openDuties.containsKey(p)?OptionalDouble.empty():OptionalDouble.of(s.openDuties.get(p));}
    public static int openTarget(ServerLevel l,BlockPos p){var s=LEVELS.get(l);return s==null?20:s.openTargets.getOrDefault(p,20);}
    private static boolean valid(ServerLevel l,Control c){var p=c.panel().getBlockPos();return l.isLoaded(p)&&l.getBlockEntity(p)==c.panel()&&!c.panel().isRemoved();}
    public static double roomTarget(ServerLevel l,long id){var c=roomControl(l,id);return c!=null&&valid(l,c)?c.baseTarget():com.frozendawn.thermal.HeaterControl.DEFAULT_TARGET;}
    private static List<ThermalHeaterBlockEntity> connected(ServerLevel l,RoomAtmosphere.Geometry geometry) {
        var positions=new HashSet<>(geometry.cells());positions.addAll(geometry.walls());var heaters=new ArrayList<ThermalHeaterBlockEntity>();
        for(var p:positions)if(l.isLoaded(p)&&l.getBlockEntity(p) instanceof ThermalHeaterBlockEntity heater)heaters.add(heater);
        return heaters;
    }
    private static ThermostatBlockEntity openWinner(List<View> views,BlockPos heater){return views.stream().filter(v->v.open&&v.panel.getBlockPos().distSqr(heater)<=64).map(View::panel).max(PRIORITY).orElse(null);}
    public static void refresh(ServerLevel l) {
        var s=LEVELS.get(l);if(s==null)return;
        var views=new ArrayList<View>();s.rooms.clear();s.openDuties.clear();s.openTargets.clear();s.installedOpen.clear();
        var winners=new HashMap<Long,ThermostatBlockEntity>();
        var phase=ApocalypseState.get(l.getServer());
        for(var p:List.copyOf(s.positions)) {
            if(!l.isLoaded(p))continue;
            if(!(l.getBlockEntity(p) instanceof ThermostatBlockEntity panel)||panel.isRemoved()){s.positions.remove(p);continue;}
            var room=RoomAtmosphere.view(l,p);
            if(room!=null){panel.markSealed();winners.merge(room.id(),panel,(a,b)->PRIORITY.compare(a,b)>0?a:b);views.add(new View(panel,room,false,SEALED));}
            else {
                var seal=RoomAtmosphere.inspect(l,p).seal();
                boolean open=seal==RoomAtmosphere.Seal.OPEN&&!panel.wasSealed()&&phase.getPhase()<5;
                views.add(new View(panel,null,open,seal==RoomAtmosphere.Seal.UNKNOWN?WAITING:open?OPEN:NO_SEAL));
            }
        }
        for(var entry:winners.entrySet())s.rooms.put(entry.getKey(),new Control(entry.getValue(),TemperatureManager.thermostatTerms(l,entry.getValue().getBlockPos())));
        s.views=List.copyOf(views);
        var openHeaters=new HashMap<BlockPos,ThermalHeaterBlockEntity>();
        for(var view:views)if(view.open) {
            var center=view.panel.getBlockPos();
            for(int x=-8;x<=8;x++)for(int y=-8;y<=8;y++)for(int z=-8;z<=8;z++) {
                var pos=center.offset(x,y,z);if(center.distSqr(pos)>64||!l.isLoaded(pos)||RoomThermalManager.managesHeater(l,pos))continue;
                if(l.getBlockEntity(pos) instanceof ThermalHeaterBlockEntity heater)openHeaters.put(pos,heater);
            }
        }
        s.installedOpen.putAll(openHeaters);
        for(var view:views)if(view.open) {
            var panel=view.panel;var sensor=panel.getBlockPos();double full=0,otherHeat=0;
            for(var heater:openHeaters.values())if(openWinner(views,heater.getBlockPos())==panel&&heater.isLit())full+=TemperatureManager.fullHeaterWarmth(l,heater,sensor);
            for(var pos:HeaterRegistry.getHeaters(l))if(l.isLoaded(pos)&&l.getBlockEntity(pos) instanceof ThermalHeaterBlockEntity heater&&openWinner(views,pos)!=panel)
                otherHeat+=TemperatureManager.fullHeaterWarmth(l,heater,sensor)*heater.getBurnFraction();
            double baseline=TemperatureManager.getBackgroundTemperature(sensor.getY(),phase.getCurrentDay(),phase.getTotalDays())+TemperatureManager.getShelterModifier(l,sensor)+otherHeat;
            double target=TemperatureManager.thermostatTerms(l,sensor).baseTarget(panel.target());
            double duty=full>0?Math.clamp((target-baseline)/full,0,1):0;
            for(var heater:openHeaters.values())if(openWinner(views,heater.getBlockPos())==panel){s.openDuties.put(heater.getBlockPos(),duty);s.openTargets.put(heater.getBlockPos(),panel.target());}
        }
        publish(l);
    }
    public static void publish(ServerLevel l) {
        var s=LEVELS.get(l);if(s==null)return;var phase=ApocalypseState.get(l.getServer());
        for(var view:s.views) {
            var panel=view.panel;var p=panel.getBlockPos();if(!l.isLoaded(p)||l.getBlockEntity(p)!=panel)continue;
            int mode=view.mode;List<ThermalHeaterBlockEntity> heaters=new ArrayList<>();
            double base=Double.NaN,sensed=Double.NaN;
            if(view.room!=null){var value=RoomThermalManager.cachedTemperatureAt(l,p);if(value.isPresent())base=value.getAsDouble();else mode=WAITING;
                var winner=s.rooms.get(view.room.id());if(winner!=null&&winner.panel!=panel)mode=OVERRIDDEN;
                heaters=connected(l,view.room.geometry());
                if(Double.isFinite(base))sensed=TemperatureManager.thermostatTerms(l,p).apply(base);
            }else if(view.open){base=TemperatureManager.getBackgroundTemperature(p.getY(),phase.getCurrentDay(),phase.getTotalDays())+TemperatureManager.getShelterModifier(l,p);double heat=0;
                for(var pos:HeaterRegistry.getHeaters(l))if(l.isLoaded(pos)&&l.getBlockEntity(pos) instanceof ThermalHeaterBlockEntity heater){heat+=TemperatureManager.fullHeaterWarmth(l,heater,p)*heater.getBurnFraction();}
                for(var heater:s.installedOpen.values())if(l.isLoaded(heater.getBlockPos())&&l.getBlockEntity(heater.getBlockPos())==heater&&openWinner(s.views,heater.getBlockPos())==panel)heaters.add(heater);
                sensed=TemperatureManager.thermostatTerms(l,p).apply(base+heat);
                if(heaters.isEmpty()&&s.views.stream().anyMatch(other->other.open&&other.panel!=panel&&p.distSqr(other.panel.getBlockPos())<=256&&PRIORITY.compare(other.panel,panel)>0))mode=OVERRIDDEN;
            }
            double weight=0,power=0;for(var heater:heaters)if(heater.isLit()){double w=Math.max(0,heater.getPublicHeatOutput()-heater.getFrostmiteHeatPenalty());weight+=w;power+=w*heater.getBurnFraction();}
            panel.publish(new Reading(mode,sensed,base,heaters.size(),weight>0?power/weight:0));
        }
    }
}
