package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.thermal.RoomHeatMath;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomThermalGameTest {
    @BeforeBatch(batch="room_heat")
    public static void report(ServerLevel level){GameTestReporting.installReporter(level);}
    @BeforeBatch(batch="room_heat_live")
    public static void reportLive(ServerLevel level){GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatMergeSplitConservesGasAndSharedMaterial(GameTestHelper h) {
        scene(h,s->{
            var first=RoomAtmosphere.view(s.l,s.c);hot(s,40,15);
            double oldAir=s.heat().rooms.get(first.id()).airEnergy;int count=first.geometry().cells().size();
            partition(s,true);RoomAtmosphere.view(s.l,s.c.west(2));
            h.assertTrue(s.heat().rooms.size()==2,"Both split children own thermal reservoirs");
            near(h,s.heat().rooms.values().stream().mapToDouble(r->r.airEnergy).sum(),oldAir*72/count,"Partition exports only its nine removed gas cells");
            double shared=RoomThermalManager.snapshots(s.l).stream().mapToDouble(r->r.structureCapacity()).sum();
            near(h,shared,s.heat().materials.values().stream().mapToDouble(m->m.capacity).sum(),"Shared physical walls have exactly one capacity across all rooms");
            budget(h,s);double remaining=s.heat().rooms.values().stream().mapToDouble(r->r.airEnergy).sum();double imported=s.heat().ledger.airIn;
            partition(s,false);RoomAtmosphere.view(s.l,s.c.east(2));
            h.assertTrue(s.heat().rooms.size()==1,"Merge retires both parents atomically");
            near(h,s.heat().rooms.values().iterator().next().airEnergy,remaining+s.heat().ledger.airIn-imported,"Merge imports only new partition cells; old gas retains energy");
            budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatOlderParentReloadClaimsEveryPressureSiblingOnce(GameTestHelper h) {
        scene(h,s->{
            var first=RoomAtmosphere.view(s.l,s.c);hot(s,55,20);
            double oldAir=s.heat().rooms.get(first.id()).airEnergy;int count=first.geometry().cells().size();
            var older=s.heat().save(new CompoundTag(),s.l.registryAccess());
            partition(s,true);RoomAtmosphere.view(s.l,s.c.west(2));
            h.assertTrue(RoomIdentityState.get(s.l).overlapping(Set.of(s.c.west(2),s.c.east(2))).size()==2,"Pressure save already contains both split memberships");
            RoomAtmosphere.reset();RoomThermalManager.reset();
            s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(older,s.l.registryAccess()));
            RoomAtmosphere.view(s.l,s.c.east(2));
            h.assertTrue(s.heat().rooms.size()==2,"First child query claims all surviving siblings from the older thermal parent");
            near(h,s.heat().rooms.values().stream().mapToDouble(r->r.airEnergy).sum(),oldAir*72/count,"Sibling gas shares survive partial-cache reload");
            budget(h,s);double total=s.heat().totalEnergy();
            RoomAtmosphere.view(s.l,s.c.west(2));RoomThermalManager.temperatureAt(s.l,s.c.east(2));
            near(h,s.heat().totalEnergy(),total,"Second sibling query cannot consume the saved parent twice");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatBreachRefillPreservesWallsAndAccountsIncomingAir(GameTestHelper h) {
        scene(h,s->{
            var room=RoomAtmosphere.view(s.l,s.c);hot(s,35,30);double air=s.heat().rooms.get(room.id()).airEnergy;
            var survivor=s.c.west(5);double wall=s.heat().materials.get(survivor).energy;
            double exported=s.heat().ledger.airOut;
            s.l.setBlock(s.c.east(5),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);
            var opened=s.heat().rooms.get(room.id());
            h.assertTrue(!opened.airPresent&&!opened.sealed,"A real geometry breach removes the air node");
            near(h,s.heat().ledger.airOut-exported,air,"Breach exports the complete gas energy exactly once");
            near(h,s.heat().materials.get(survivor).energy,wall,"Surviving hot walls retain heat through breach");budget(h,s);
            s.l.setBlock(s.c.east(5),Blocks.GLASS.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);
            h.assertTrue(!s.heat().rooms.get(room.id()).airPresent,"Reseal alone does not conjure oxygen or gas heat");
            double before=s.heat().totalEnergy(),incoming=s.heat().ledger.airIn;
            RoomAirState.get(s.l).refill(RoomAtmosphere.view(s.l,s.c).geometry().cells());
            h.assertTrue(s.heat().rooms.get(room.id()).airPresent,"Authoritative refill restores gas reservoir");
            near(h,s.heat().totalEnergy()-before,s.heat().ledger.airIn-incoming,"Cold incoming air energy is explicitly imported");
            double materialEnergy=s.heat().materials.values().stream().mapToDouble(m->m.energy).sum();
            double gas=s.heat().rooms.get(room.id()).airEnergy;RoomThermalManager.tickLevel(s.l);
            h.assertTrue(s.heat().rooms.get(room.id()).airEnergy>gas,"Retained walls warm incoming air");
            h.assertTrue(s.heat().materials.values().stream().mapToDouble(m->m.energy).sum()<materialEnergy,"Warming air debits wall heat");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatColdMaterialCannotBlockHotWallTransferAfterReload(GameTestHelper h) {
        scene(h,65-h.absolutePos(BlockPos.ZERO).getY(),s->{
            var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.IRON_THERMAL_HEATER.get().defaultBlockState(),3);
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,-64,191);
            var empty=s.heat().materials.get(pos);
            h.assertTrue(empty!=null,"Physical heater casing belongs to the room structure");
            s.heat().ledger.environmentLoss+=empty.energy;empty.energy=0;
            var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());
            RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
            double gas=s.heat().rooms.get(view.id()).airEnergy;
            double walls=s.heat().materials.values().stream().mapToDouble(m->m.energy).sum();
            advanceHeat(s);
            var first=RoomThermalManager.snapshots(s.l).stream().filter(r->r.id()==view.id()).findFirst().orElseThrow();
            h.assertTrue(first.airTemperature()>-63,"Hot walls start warming cold air after reload even with an empty casing: "+first.airTemperature());
            h.assertTrue(s.heat().rooms.get(view.id()).airEnergy>gas,"Air gains actual energy from the hot structure");
            h.assertTrue(s.heat().materials.values().stream().mapToDouble(m->m.energy).sum()<walls,"Wall energy pays for warming and exterior loss");
            h.assertTrue(first.heaterPower()==0&&first.passivePower()==0,"No fueled or passive source conceals the stored-wall transfer");
            for(int i=0;i<11;i++)advanceHeat(s);
            h.assertTrue(s.heat().materials.values().stream().allMatch(m->Double.isFinite(m.energy)&&m.energy>=0),"Repeated transfers never overdraw a cold physical block");
            h.assertTrue(RoomThermalManager.cachedMachineryTemperatureAt(s.l,s.c).orElseThrow()>1,"Machinery remains thawed on later real thermal seconds");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatOriginalCapacityColdStartAndQuickBreachRecovery(GameTestHelper h) {
        scene(h,65-h.absolutePos(BlockPos.ZERO).getY(),s->{
            smallRoom(s);ThermostatManager.reset();
            var phase=ApocalypseState.get(s.l.getServer());long lo=0,hi=phase.getTotalDays()*24000L;
            while(lo<hi){long mid=(lo+hi)/2;phase.setApocalypseTicks(mid,s.l.getServer());
                double outside=TemperatureManager.getBackgroundTemperature(s.c.getY(),phase.getCurrentDay(),phase.getTotalDays());
                if(outside>-150)lo=mid+1;else hi=mid;}
            phase.setApocalypseTicks(lo,s.l.getServer());CombustionAtmosphere.reset();
            var pos=s.c.west();s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            try {
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(40000);
            RoomAtmosphere.view(s.l,s.c);hot(s,-150,-150);
            int seconds=0;while(seconds<120&&RoomThermalManager.cachedMachineryTemperatureAt(s.l,s.c).orElseThrow()<19){advanceHeat(s);seconds++;}
            double temperature=RoomThermalManager.cachedMachineryTemperatureAt(s.l,s.c).orElseThrow();
            h.assertTrue(seconds>=50&&temperature>=19,"One basic heater warms the original-capacity cold room in about1-2min: "+seconds+"s, "+temperature+"C");budget(h,s);
            FrozenDawn.LOGGER.info("[VentCalibration]coldStart={}s at{}C with one base heater",seconds,temperature);
            hot(s,20,20);
            double walls=RoomThermalManager.snapshots(s.l).getFirst().structureTemperature();
            var retainedWall=s.c.west(2);double wallBefore=s.heat().materials.get(retainedWall).energy;
            var breach=s.c.east(2);s.l.setBlock(breach,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.inspect(s.l,s.c);advanceHeat(s);
            var material=s.heat().materials.get(retainedWall);
            h.assertTrue(RoomHeatMath.temperature(material.capacity,material.energy)>RoomHeatMath.temperature(material.capacity,wallBefore)-1,"Surviving walls barely cool during a quick breach");
            s.l.setBlock(breach,Blocks.WHITE_WOOL.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);
            // Invoke the authoritative refill transition after patching; controller timing
            // is independently covered by pressure recovery tests.
            RoomAirState.get(s.l).refill(RoomAtmosphere.view(s.l,s.c).geometry().cells());
            RoomThermalManager.temperatureAt(s.l,s.c);for(int i=0;i<5;i++)advanceHeat(s);
            var recovered=RoomThermalManager.snapshots(s.l).getFirst();
            h.assertTrue(recovered.structureTemperature()>walls-5,"Retained walls and the running heater recover after a quick patch: "+recovered.structureTemperature());
            h.assertTrue(recovered.airPresent()&&recovered.airTemperature()>=19,"Warm walls and the existing heater rapidly restore resealed incoming air: "+recovered.airTemperature());budget(h,s);
            FrozenDawn.LOGGER.info("[VentCalibration]coldStart={}s at{}C; reseal air={}C walls={}C",seconds,temperature,recovered.airTemperature(),recovered.structureTemperature());
            }finally{s.l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void thermostatVentHandlesPixieAndTwoFurnacesInDeepVacuum(GameTestHelper h) {
        scene(h,65-h.absolutePos(BlockPos.ZERO).getY(),s->{
            smallRoom(s);ThermostatManager.reset();
            var phase=ApocalypseState.get(s.l.getServer());phase.setApocalypseTicks(phase.getTotalDays()*24000L,s.l.getServer());CombustionAtmosphere.reset();
            var vent=s.c.east(2);s.l.setBlock(vent,ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST),3);
            s.l.setBlock(s.c.west(),ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
            var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(s.c.west());panel.onLoad();
            try {
                var geometry=RoomAtmosphere.view(s.l,s.c).geometry();hot(s,20,20);double before=s.heat().ledger.passive;
                for(int second=0;second<300;second++){RoomThermalManager.addMachineHeat(s.l,s.c,1000);advanceHeat(s);}
                var pixie=RoomThermalManager.snapshots(s.l).getFirst();
                h.assertTrue(panel.reading().coolingVents()>0&&panel.data().get(6)>0,"Thermostat menu reports actual cooling even when a substep closes at target");
                h.assertTrue(pixie.airTemperature()>=19.5&&pixie.airTemperature()<21,"One vent holds thermostat target with full PIXIE heat: "+pixie.airTemperature());
                for(int second=0;second<600;second++){
                    RoomThermalManager.addMachineHeat(s.l,s.c,1000+2*com.frozendawn.block.InductionFurnaceBlockEntity.WASTE_HEAT_WATTS);advanceHeat(s);
                    h.assertTrue(RoomThermalManager.snapshots(s.l).getFirst().airTemperature()<35,"One vent plus envelope loss handles full PIXIE and two furnaces in deep vacuum");
                }
                var busy=RoomThermalManager.snapshots(s.l).getFirst();
                near(h,s.heat().ledger.passive-before,1500000,"All machine waste heat is retained in accounting before real rejection");
                h.assertTrue(RoomAtmosphere.view(s.l,s.c).geometry()==geometry&&busy.airPresent(),"Automatic shutters retain the same sealed gas volume");budget(h,s);
                FrozenDawn.LOGGER.info("[VentCalibration]full PIXIE={}C; PIXIE+two furnaces={}C; exterior={}C",pixie.airTemperature(),busy.airTemperature(),TemperatureManager.getBackgroundTemperature(s.c.getY(),phase.getCurrentDay(),phase.getTotalDays()));
            }finally{s.l.setBlock(s.c.west(),Blocks.AIR.defaultBlockState(),3);ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void thermostatVentsStackAndCloseWithoutOvercoolingAcrossReload(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();
            // Insulated walls keep ordinary exterior loss below one basic heater's output.
            for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
                if(Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3)s.l.setBlock(s.c.offset(x,y,z),Blocks.WHITE_WOOL.defaultBlockState(),3);
            var panelPos=s.c.west(2);
            s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
            var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();
            var heaterPos=s.c.east(2);s.l.setBlock(heaterPos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(heaterPos);heater.onLoad();heater.addFuel(10000);
            var vents=List.of(s.c.east(5),s.c.east(5).above(),s.c.east(5).south());
            var state=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            try {
                s.l.setBlock(vents.getFirst(),state,3);RoomAtmosphere.view(s.l,s.c);hot(s,80,80);advanceHeat(s);
                var one=RoomThermalManager.snapshots(s.l).getFirst();h.assertTrue(one.coolingVents()==1,"Hot controlled room opens a manually closed vent");
                for(var vent:vents)s.l.setBlock(vent,state,3);
                RoomAtmosphere.view(s.l,s.c);hot(s,80,80);advanceHeat(s);
                var three=RoomThermalManager.snapshots(s.l).getFirst();
                h.assertTrue(three.coolingVents()==3,"Every exposed radiator participates");near(h,three.coolingConductance(),3*one.coolingConductance(),"Three independent outdoor faces give exactly triple conductance");
                var roof=vents.getLast().east().above(2);s.l.setBlock(roof,Blocks.GLASS.defaultBlockState(),3);hot(s,80,80);advanceHeat(s);
                h.assertTrue(RoomThermalManager.snapshots(s.l).getFirst().coolingVents()==2,"A transparent roof blocks only its own radiator");
                s.l.setBlock(roof,Blocks.AIR.defaultBlockState(),3);
                s.l.setBlock(vents.getLast(),state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true),3);
                hot(s,19.9,19.9);advanceHeat(s);
                for(var vent:vents)h.assertTrue(!RoomThermalManager.ventOpen(s.l,vent),"All automatic vents close below target");
                hot(s,20.4,20.4);advanceHeat(s);h.assertTrue(!RoomThermalManager.ventOpen(s.l,vents.getFirst()),"Closed control stays closed inside the hysteresis band");
                hot(s,30,30);advanceHeat(s);h.assertTrue(RoomThermalManager.ventOpen(s.l,vents.getFirst()),"Cooling starts above the upper threshold");
                var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
                hot(s,30,30);RoomThermalManager.temperatureAt(s.l,s.c);advanceHeat(s);
                h.assertTrue(RoomThermalManager.ventThermostatControlled(s.l,vents.getFirst())&&RoomThermalManager.ventOpen(s.l,vents.getFirst()),"Reload reconstructs same-room cooling without a saved open latch");
                for(int i=0;i<60;i++){advanceHeat(s);var snap=RoomThermalManager.snapshots(s.l).getFirst();h.assertTrue(snap.airTemperature()>=19.5,"Oversized cooling cannot drag air below target; only normal envelope loss remains: "+snap.airTemperature());}
                h.assertTrue(panel.data().getCount()==7,"Cooling status is synchronized with the simple thermostat menu");budget(h,s);
                s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),3);advanceHeat(s);
                h.assertTrue(!RoomThermalManager.ventThermostatControlled(s.l,vents.getFirst())&&!RoomThermalManager.ventOpen(s.l,vents.getFirst()),"Removing controller restores the original manual setting");
                h.assertTrue(RoomThermalManager.ventOpen(s.l,vents.getLast()),"Removing controller also restores a saved manual-open setting");
            }finally{s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),3);s.l.setBlock(heaterPos,Blocks.AIR.defaultBlockState(),3);ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatCapacityUpgradePreservesTemperaturesAndMigratesOnlyOnce(GameTestHelper h) {
        scene(h,s->{
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,-64,191);
            var cold=s.heat().materials.values().iterator().next();s.heat().ledger.environmentLoss+=cold.energy;cold.energy=0;
            var temperatures=new java.util.HashMap<BlockPos,Double>();
            s.heat().materials.forEach((pos,material)->temperatures.put(pos,RoomHeatMath.temperature(material.capacity,material.energy)));
            for(int version:new int[]{2,3}) {
                var legacy=s.heat().save(new CompoundTag(),s.l.registryAccess());legacy.putInt("version",version);
                double oldAir=version==3?1200.0/72.0:1,oldWall=version==3?18000.0/72.0:12,oldTotal=0;
                for(var item:legacy.getList("rooms",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    var room=(CompoundTag)item;double energy=room.getDouble("airEnergy")*oldAir/RoomHeatMath.AIR_CAPACITY;
                    room.putDouble("airEnergy",energy);oldTotal+=energy;
                }
                for(var item:legacy.getList("materials",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    var material=(CompoundTag)item;double energy=material.getDouble("energy")*oldWall/material.getDouble("capacity");
                    material.putDouble("capacity",oldWall);material.putDouble("energy",energy);oldTotal+=energy;
                }
                var oldLedger=new CompoundTag();oldLedger.putDouble("heater",123);oldLedger.putDouble("passive",456);
                oldLedger.putDouble("materialIn",oldTotal-579);legacy.put("ledger",oldLedger);
                var migrated=RoomThermalState.load(legacy,s.l.registryAccess());var room=migrated.rooms.get(view.id());
                near(h,RoomHeatMath.temperature(room.airCapacity(),room.airEnergy),-64,"Migration preserves gas temperature from version"+version);
                for(var entry:migrated.materials.entrySet())near(h,RoomHeatMath.temperature(entry.getValue().capacity,entry.getValue().energy),temperatures.get(entry.getKey()),"Migration preserves each hot or empty wall");
                near(h,migrated.ledger.heater,123,"Migration preserves historical fuel heat");near(h,migrated.ledger.passive,456,"Migration preserves historical machine heat");
                near(h,migrated.totalEnergy(),migrated.ledger.net(),"Signed calibration adjustment balances energy");
                h.assertTrue(room.cells.equals(view.geometry().cells())&&room.airPresent&&room.sealed&&migrated.isDirty(),"Migration retains geometry/air and schedules converted save");
                if(version==3)h.assertTrue(migrated.ledger.capacityAdjustment<0,"Reverting increased capacities uses an explicit negative rebase");
                var again=RoomThermalState.load(migrated.save(new CompoundTag(),s.l.registryAccess()),s.l.registryAccess());
                near(h,again.totalEnergy(),migrated.totalEnergy(),"Reload cannot convert capacity twice");near(h,again.ledger.capacityAdjustment,migrated.ledger.capacityAdjustment,"One-time adjustment persists");
                var depleted=legacy.copy();var entry=depleted.getList("rooms",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
                double exported=entry.getDouble("airEnergy");entry.putBoolean("airPresent",false);entry.putDouble("airEnergy",0);
                depleted.getCompound("ledger").putDouble("airOut",exported);var vacuum=RoomThermalState.load(depleted,s.l.registryAccess());
                var empty=vacuum.rooms.get(view.id());h.assertTrue(!empty.airPresent&&empty.airEnergy==0&&empty.airCapacity()==0,"Migration cannot refill depleted gas");
                near(h,vacuum.totalEnergy(),vacuum.ledger.net(),"Depleted conversion balances without fabricated air");
            }
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatOuterLayerChangesInsulationWithoutChangingAirGeometry(GameTestHelper h) {
        scene(h,s->{
            var outer=s.c.offset(6,0,0);s.l.setBlock(outer,Blocks.WHITE_WOOL.defaultBlockState(),3);
            var view=RoomAtmosphere.view(s.l,s.c);double gas=s.heat().rooms.get(view.id()).airEnergy;
            double oldK=RoomThermalManager.snapshots(s.l).getFirst().conductance();
            s.l.setBlock(outer,Blocks.GLASS.defaultBlockState(),3);RoomThermalManager.tickLevel(s.l);
            h.assertTrue(RoomAtmosphere.view(s.l,s.c).geometry()==view.geometry(),"Outer insulation edit reuses exact pressure geometry");
            h.assertTrue(RoomThermalManager.snapshots(s.l).getFirst().conductance()>oldK,"Wool-to-glass outer layer increases heat loss");
            near(h,s.heat().rooms.get(view.id()).airEnergy,gas,"Equal initial ambient temperatures prevent fabricated gas heat");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatUnknownSuspendsEnergyUntilGeometryRecovers(GameTestHelper h) {
        scene(h,s->{
            RoomAtmosphere.view(s.l,s.c);hot(s,35,30);
            for(int y=3;y<=35;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                s.l.setBlock(s.c.offset(x,y,z),(Math.abs(x)==1||Math.abs(z)==1||y==35?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(s.l,s.c)==null,"Oversized closed extension is UNKNOWN");
            double total=s.heat().totalEnergy();RoomThermalManager.tickLevel(s.l);
            near(h,s.heat().totalEnergy(),total,"UNKNOWN geometry suspends gas and surviving wall simulation");
            h.assertTrue(RoomThermalManager.snapshots(s.l).stream().allMatch(r->r.suspended()),"Uncertainty cannot resume from the old loaded membership");
            s.l.setBlock(s.c.above(3),Blocks.GLASS.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);
            h.assertTrue(RoomThermalManager.snapshots(s.l).stream().noneMatch(r->r.suspended()),"Known repaired geometry resumes simulation");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatReloadAndCachedCatchupDoNotDiscoverOrIntegrateTime(GameTestHelper h) {
        scene(h,s->{
            h.assertTrue(RoomThermalManager.cachedTemperatureAt(s.l,s.c).isEmpty(),"Cold cache lookup initializes no room");
            TemperatureManager.getLoadedTemperatureAt(s.l,s.c,45,50);TemperatureManager.getTemperatureAt(s.l,s.c,45,50,true);
            h.assertTrue(RoomAtmosphere.trackedRooms(s.l).isEmpty()&&s.heat().rooms.isEmpty(),"Mob and catch-up samples do not discover pressure or thermal rooms");
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,70,50);
            double energy=s.heat().totalEnergy();var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());
            RoomThermalManager.reset();RoomAtmosphere.reset();
            s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
            h.assertTrue(RoomThermalManager.snapshots(s.l).isEmpty(),"Loading SavedData does not integrate offline time");
            RoomAtmosphere.view(s.l,s.c);near(h,s.heat().totalEnergy(),energy,"Reload and rebind preserve gas and material energies");
            near(h,RoomThermalManager.temperatureAt(s.l,s.c).orElseThrow(),70,"Persisted air temperature survives reload");
            long age=RoomAtmosphere.cachedRoomDiagnostics(s.l).stream().filter(r->r.id()==view.id()).findFirst().orElseThrow().idleTicks();
            RoomThermalManager.cachedTemperatureAt(s.l,s.c);
            h.assertTrue(RoomAtmosphere.cachedRoomDiagnostics(s.l).stream().filter(r->r.id()==view.id()).findFirst().orElseThrow().idleTicks()==age,"Cached thermal lookup never renews pressure activity");
            var unloaded=new BlockPos(1234567,200,1234567);
            h.assertTrue(RoomThermalManager.cachedTemperatureAt(s.l,unloaded).isEmpty()&&!s.l.isLoaded(unloaded),"Unloaded probe does not force chunks");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatDepletedAirCyclesUseReducedHeaterAndExplicitImports(GameTestHelper h) {
        scene(h,s->{
            var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,40,30);
            double wall=s.heat().materials.values().stream().mapToDouble(m->m.energy).sum();
            for(int i=0;i<4;i++) {
                RoomAirState.get(s.l).evacuate(view.geometry().cells());
                var drained=RoomThermalManager.snapshots(s.l).getFirst();
                h.assertTrue(!drained.airPresent()&&drained.airCapacity()==0,"Airlock evacuation removes gas capacity while geometry remains sealed");
                h.assertTrue(drained.feltTemperature()<drained.structureTemperature(),"Vacuum felt temperature blends wall radiation with cold exterior");
                budget(h,s);RoomAirState.get(s.l).refill(view.geometry().cells());budget(h,s);
                near(h,s.heat().materials.values().stream().mapToDouble(m->m.energy).sum(),wall,"No unticked vent/refill cycle fabricates wall heat");
            }
            hot(s,0,0);RoomAirState.get(s.l).evacuate(view.geometry().cells());double heat=s.heat().ledger.heater;
            double power=RoomThermalManager.snapshots(s.l).getFirst().heaterPower();RoomThermalManager.tickLevel(s.l);
            near(h,s.heat().ledger.heater-heat,power*RoomHeatMath.VACUUM_HEATER_EFFICIENCY,"Depleted room heater delivers only reduced wall power");
            h.assertTrue(heater.isLit(),"Vacuum does not silently extinguish the sealed thermal heater");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatUnloadedParentShareSuspendsWholeReloadClaim(GameTestHelper h) {
        scene(h,s->{
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,50,35);
            var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());
            var entry=saved.getList("rooms",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            var unloaded=new BlockPos(1234567,200,1234567);long[] old=entry.getLongArray("cells");
            long[] cells=Arrays.copyOf(old,old.length+1);cells[old.length]=unloaded.asLong();entry.putLongArray("cells",cells);
            RoomThermalManager.reset();RoomAtmosphere.reset();
            s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
            double total=s.heat().totalEnergy(),gas=s.heat().rooms.get(view.id()).airEnergy;
            RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);
            near(h,s.heat().totalEnergy(),total,"Unloaded sibling membership preserves the complete parent energy");
            near(h,s.heat().rooms.get(view.id()).airEnergy,gas,"First loaded child cannot consume an unverified parent share");
            h.assertTrue(!s.l.isLoaded(unloaded)&&RoomThermalManager.cachedTemperatureAt(s.l,s.c).isEmpty(),"Suspended reload does not force the missing chunk or expose simulated heat");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatLoadedSavedRoomResumesWithoutPlayerEntry(GameTestHelper h) {
        scene(h,s->{
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,60,40);
            var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());
            double gas=s.heat().rooms.get(view.id()).airEnergy;
            RoomThermalManager.reset();RoomAtmosphere.reset();
            s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
            near(h,s.heat().rooms.get(view.id()).airEnergy,gas,"Deserialization itself performs no offline simulation");
            RoomThermalManager.tickLevel(s.l);
            h.assertTrue(RoomThermalManager.snapshots(s.l).size()==1,"Loaded saved room rebinds without a player entering or querying it");
            h.assertTrue(s.heat().rooms.get(view.id()).airEnergy<gas,"First loaded second resumes normal air/wall exchange");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatAutomaticRevalidationFindsSurvivingCellAfterCachedOriginFilled(GameTestHelper h) {
        scene(h,s->{
            var view=RoomAtmosphere.view(s.l,s.c);hot(s,50,30);
            var parent=s.heat().rooms.get(view.id());var first=parent.cells.iterator().next();
            double gas=parent.airEnergy;
            s.l.setBlock(first,Blocks.GLASS.defaultBlockState(),3);
            // No player/pressure query: the thermal tick must locate a surviving cell itself.
            RoomThermalManager.tickLevel(s.l);
            var snapshot=RoomThermalManager.snapshots(s.l).getFirst();
            h.assertTrue(!snapshot.suspended()&&snapshot.cells()==80,"Filling the cached origin cannot suspend a fully loaded surviving room");
            h.assertTrue(s.heat().rooms.get(view.id()).airEnergy<gas,"Filled gas cell is exported and remaining air resumes exchange");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatNeighborDiscoveryAndBreachRefreshSharedWallExposure(GameTestHelper h) {
        scene(h,s->{
            partition(s,true);var left=RoomAtmosphere.view(s.l,s.c.west(2));
            double initial=RoomThermalManager.snapshots(s.l).getFirst().conductance();
            RoomAtmosphere.view(s.l,s.c.east(2));RoomThermalManager.tickLevel(s.l);
            double shared=RoomThermalManager.snapshots(s.l).stream().filter(r->r.id()==left.id()).findFirst().orElseThrow().conductance();
            h.assertTrue(shared<initial,"Discovering the sealed neighbor removes fictitious exterior loss through their shared partition");
            s.l.setBlock(s.c.east(5),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c.east(2));
            // The next normal second processes this dirty profile; do not require a player query.
            // Rebinding via an airtight wall update uses the same material/profile path in this tick.
            var time=s.l.getGameTime();((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time+20);
            try {
                RoomThermalManager.tickLevel(s.l);
                double exposed=RoomThermalManager.snapshots(s.l).stream().filter(r->r.id()==left.id()).findFirst().orElseThrow().conductance();
                h.assertTrue(exposed>shared,"A breached neighbor exposes the still-sealed room's partition to vacuum radiation");budget(h,s);
            } finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat_live",timeoutTicks=220)
    public static void roomHeatActualHeaterWarmsAirAndWallsThenCoolsGradually(GameTestHelper h) {
        var s=new Scene(h);var pos=s.c.west(2);
        try {
            s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
            RoomAtmosphere.view(s.l,s.c);var initial=RoomThermalManager.snapshots(s.l).getFirst();
            h.runAfterDelay(100,()->{
                try {
                    var warm=RoomThermalManager.snapshots(s.l).getFirst();
                    h.assertTrue(warm.airTemperature()>initial.airTemperature()&&warm.structureTemperature()>initial.structureTemperature(),"Real subscribed ticking heats both gas and physical walls");budget(h,s);
                    heater.extinguish();
                    h.runAfterDelay(60,()->{
                        try {
                            var cool=RoomThermalManager.snapshots(s.l).getFirst();
                            h.assertTrue(cool.airTemperature()<warm.airTemperature()&&cool.airTemperature()>initial.airTemperature(),"Burnout cools gradually without dropping to the background immediately");
                            h.assertTrue(cool.heaterPower()==0,"Burned-out heater supplies no power");budget(h,s);h.succeed();
                        } finally {s.close();}
                    });
                } catch(RuntimeException|Error e){s.close();throw e;}
            });
        } catch(RuntimeException|Error e){s.close();throw e;}
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heaterControlSharedTargetCapsCombinedPowerAndDebitsExactFuel(GameTestHelper h) {
        scene(h,s->{
            var heaters=new ArrayList<ThermalHeaterBlockEntity>();
            for(var pos:List.of(s.c.west(2),s.c.east(2))) {
                s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);heaters.add(heater);
            }
            RoomAtmosphere.view(s.l,s.c);hot(s,19.9,19.9);
            double before=s.heat().ledger.heater;RoomThermalManager.tickLevel(s.l);
            var room=RoomThermalManager.snapshots(s.l).getFirst();
            h.assertTrue(room.airTemperature()<=20.000001,"Combined heating cannot overshoot the common20C target");
            double first=heaters.getFirst().getBurnFraction();
            h.assertTrue(first>0&&first<1,"Heaters reduce power near target");
            near(h,first,heaters.getLast().getBurnFraction(),"Matching heaters share exactly the same room demand");
            near(h,s.heat().ledger.heater-before,3600*first,"Only the actual heat grant enters the ledger");
            for(var heater:heaters) {
                var saved=heater.saveWithFullMetadata(s.l.registryAccess());
                double used=10000-saved.getInt("BurnTime")+saved.getDouble("FuelFraction");
                near(h,used,20*heater.getPublicPhaseConsumption()*first,"Actual fuel including the fractional remainder follows delivered power");
            }
            budget(h,s);s.l.setBlock(s.c.east(2),Blocks.AIR.defaultBlockState(),2);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heaterControlMixedTiersAndCapacitorsShareTargetAndPreserveFuelLedger(GameTestHelper h) {
        scene(h,s->{
            var blocks=List.of(ModBlocks.THERMAL_HEATER.get(),ModBlocks.IRON_THERMAL_HEATER.get(),
                    ModBlocks.GOLD_THERMAL_HEATER.get(),ModBlocks.DIAMOND_THERMAL_HEATER.get());
            int[] outputs={35,50,65,80};
            var heaters=new ArrayList<ThermalHeaterBlockEntity>();
            for(int i=0;i<8;i++) {
                var pos=s.c.offset(i-4,0,0);s.l.setBlock(pos,blocks.get(i%4).defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();
                if(i>=4)heater.installCapacitor();
                heater.addFuel(10000);heaters.add(heater);
                int expected=i>=4?(int)(outputs[i%4]*1.5f):outputs[i%4];
                near(h,heater.getPublicHeatOutput(),expected,"Each tier and installed capacitor has its own available output");
                var saved=heater.saveWithFullMetadata(s.l.registryAccess());
                var restored=new ThermalHeaterBlockEntity(pos,heater.getBlockState());
                restored.loadWithComponents(saved,s.l.registryAccess());
                near(h,restored.getPublicHeatOutput(),expected,"Capacitor-adjusted output survives reload");
            }
            RoomAtmosphere.view(s.l,s.c.offset(0,0,1));hot(s,20,19.5);
            double before=s.heat().ledger.heater;RoomThermalManager.tickLevel(s.l);
            var room=RoomThermalManager.snapshots(s.l).getFirst();
            h.assertTrue(room.airTemperature()<=20.000001,"Mixed tiers and upgrades cannot raise the shared target");
            double duty=heaters.getFirst().getBurnFraction(),delivered=0;
            h.assertTrue(duty>0&&duty<1,"Mixed heaters throttle at the common target");
            for(var heater:heaters) {
                near(h,heater.getBurnFraction(),duty,"Different power ratings share demand proportionally to available power");
                delivered+=RoomThermalManager.BASE_HEATER_POWER*heater.getPublicHeatOutput()/35.0*duty
                        *com.frozendawn.config.FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get();
                var saved=heater.saveWithFullMetadata(s.l.registryAccess());
                near(h,10000-saved.getInt("BurnTime")+saved.getDouble("FuelFraction"),
                        20*heater.getPublicPhaseConsumption()*duty,"Each tier pays its actual fractional burn");
                var menu=heater.getMenuData();for(int index=0;index<8;index++)menu.get(index);
                h.assertTrue(saved.equals(heater.saveWithFullMetadata(s.l.registryAccess())),"Status averaging cannot change stored fuel or capacitor state");
            }
            near(h,s.heat().ledger.heater-before,delivered,"Ledger counts every upgraded heater's actual heat once");budget(h,s);
            for(var heater:heaters)s.l.setBlock(heater.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heaterControlInsulationSavesFuelAtSameTemperature(GameTestHelper h) {
        final double[] demands=new double[2];
        for(int variant=0;variant<2;variant++) {
            var s=new Scene(h);try {
                if(variant==0)for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
                    if(Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3)s.l.setBlock(s.c.offset(x,y,z),Blocks.WHITE_WOOL.defaultBlockState(),2);
                var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
                RoomAtmosphere.view(s.l,s.c);hot(s,20,20);RoomThermalManager.tickLevel(s.l);
                demands[variant]=heater.getBurnFraction();
                h.assertTrue(RoomThermalManager.snapshots(s.l).getFirst().airTemperature()<=20.000001,"Holding target does not overheat");budget(h,s);
            }finally{s.close();}
        }
        h.assertTrue(demands[0]>0&&demands[0]<demands[1],"Wool consumes less fuel than glass at the same20C target");h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heaterControlRedstoneStopsHeatAndIndustrialDrawWithoutDiscardingFuel(GameTestHelper h) {
        scene(h,s->{
            var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
            RoomAtmosphere.view(s.l,s.c);hot(s,0,0);
            s.l.setBlock(pos.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);heater.serverTick();
            double before=s.heat().ledger.heater;RoomThermalManager.tickLevel(s.l);
            h.assertTrue(!heater.isLit()&&heater.hasFuel()&&heater.getBurnFraction()==0,"Redstone disables the burner while retaining fuel");
            h.assertTrue(!heater.consumeIndustrialFuel(10),"Disabled heater cannot secretly power industry");
            near(h,heater.saveWithFullMetadata(s.l.registryAccess()).getInt("BurnTime"),10000,"Disabled fuel remains unchanged");
            near(h,s.heat().ledger.heater,before,"Redstone injects no heat");
            s.l.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);heater.serverTick();
            var time=s.l.getGameTime();((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time+20);
            try {RoomThermalManager.tickLevel(s.l);h.assertTrue(heater.isLit()&&heater.getBurnFraction()>0,"Removing redstone resumes room heating");}
            finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
            budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heaterControlFractionalFuelSurvivesReloadAndTinyFuelCapsHeat(GameTestHelper h) {
        scene(h,s->{
            var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
            RoomAtmosphere.view(s.l,s.c);hot(s,20,20);RoomThermalManager.tickLevel(s.l);
            var saved=heater.saveWithFullMetadata(s.l.registryAccess());double fraction=saved.getDouble("FuelFraction");
            h.assertTrue(fraction>0&&fraction<1,"Throttled fuel retains a meaningful sub-tick remainder");
            var restored=new ThermalHeaterBlockEntity(pos,heater.getBlockState());restored.loadWithComponents(saved,s.l.registryAccess());
            near(h,restored.saveWithFullMetadata(s.l.registryAccess()).getDouble("FuelFraction"),fraction,"Reload preserves fractional fuel rather than granting free heat");
            heater.extinguish();heater.addFuel(1);hot(s,-200,-200);
            double before=s.heat().ledger.heater;var time=s.l.getGameTime();
            ((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time+20);
            try {RoomThermalManager.tickLevel(s.l);}finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
            h.assertTrue(!heater.hasFuel(),"Tiny remaining fuel is exhausted exactly");
            near(h,s.heat().ledger.heater-before,1800.0/(20*heater.getPublicPhaseConsumption()),"One fuel unit cannot fund a whole second of heating");budget(h,s);
        });
    }
    @BeforeBatch(batch="heater_control_live")
    public static void reportControl(ServerLevel level){GameTestReporting.installReporter(level);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="heater_control_live",timeoutTicks=200)
    public static void heaterControlRealTicksHoldTargetAndSaveFuelWithMultipleHeaters(GameTestHelper h) {
        var s=new Scene(h);var heaters=new ArrayList<ThermalHeaterBlockEntity>();
        try {
            for(var pos:List.of(s.c.west(2),s.c.east(2))) {
                s.l.setBlock(pos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);heaters.add(heater);
            }
            RoomAtmosphere.view(s.l,s.c);hot(s,19,19);
            h.runAfterDelay(100,()->{
                try {
                    var room=RoomThermalManager.snapshots(s.l).getFirst();
                    h.assertTrue(room.airTemperature()>19&&room.airTemperature()<=20.000001,"Real loaded ticking reaches and holds the common target");
                    for(var heater:heaters) {
                        var saved=heater.saveWithFullMetadata(s.l.registryAccess());
                        h.assertTrue(saved.getInt("BurnTime")>10000-100*heater.getPublicPhaseConsumption(),"Real tickers save fuel rather than applying full drain on top of room control");
                        h.assertTrue(heater.getBurnFraction()<.9,"Loaded heater visibly throttles after warm-up");
                    }
                    budget(h,s);h.succeed();
                }finally{s.l.setBlock(s.c.east(2),Blocks.AIR.defaultBlockState(),2);s.close();}
            });
        }catch(RuntimeException|Error e){s.l.setBlock(s.c.east(2),Blocks.AIR.defaultBlockState(),2);s.close();throw e;}
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void thermostatCoreAwareMixedHeatersReachSensedTargetWithoutDoubleHeat(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();var panelPos=s.c;var corePos=s.c.east(5);
            s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
            var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();panel.setTarget(30);
            s.l.setBlock(corePos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            com.frozendawn.world.GeothermalCoreRegistry.register(s.l,corePos);
            var heaters=new ArrayList<ThermalHeaterBlockEntity>();
            for(var pos:List.of(s.c.west(2),s.c.east(2))) {
                s.l.setBlock(pos,(pos.equals(s.c.west(2))?ModBlocks.THERMAL_HEATER.get():ModBlocks.DIAMOND_THERMAL_HEATER.get()).defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);heater.installCapacitor();heaters.add(heater);
            }
            RoomAtmosphere.view(s.l,panelPos);ThermostatManager.refresh(s.l);
            var terms=TemperatureManager.thermostatTerms(s.l,panelPos);h.assertTrue(terms.warmth()==0,"Connected Core contributes reservoir heat, not a second sensor bonus");
            // Finish dirty wall profiles before importing controlled thermal fixture energy.
            RoomThermalManager.tickLevel(s.l);
            double base=terms.baseTarget(30);
            var apocalypse=ApocalypseState.get(s.l.getServer());
            h.assertTrue(TemperatureManager.getBackgroundTemperature(panelPos.getY(),apocalypse.getCurrentDay(),apocalypse.getTotalDays())<base,"Heating fixture starts colder than the Core-adjusted gas target; passive heat cannot require cooling");
            hot(s,base-.1,base-.1);double before=s.heat().ledger.heater;
            var time=s.l.getGameTime();((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time+20);
            try {RoomThermalManager.tickLevel(s.l);}finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
            var value=RoomThermalManager.snapshots(s.l).getFirst();
            near(h,panel.reading().sensed(),value.airTemperature(),"Controller senses the actual mixed-source room temperature");
            h.assertTrue(value.airTemperature()>=base-1&&value.airTemperature()<=50,"Passive heat warms toward its supply temperature without an instant Core bonus");
            near(h,TemperatureManager.getTemperatureAt(s.l,panelPos,ApocalypseState.get(s.l.getServer()).getCurrentDay(),ApocalypseState.get(s.l.getServer()).getTotalDays()),value.airTemperature(),"Player environmental reading agrees at sensor");
            h.assertTrue(panel.reading().heaters()==2&&heaters.stream().allMatch(heater->heater.getBurnFraction()<1),"Mixed upgraded tiers link and throttle against passive heat");
            h.assertTrue(s.heat().ledger.passive>0,"Actual Core heat is accounted independently from fuel-burning heaters");budget(h,s);
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);player.setPos(panelPos.getX()+.5,panelPos.getY(),panelPos.getZ()+.5);
            var menu=new com.frozendawn.block.ThermostatMenu(1,player.getInventory(),panel);
            h.assertTrue(!menu.clickMenuButton(player,9),"Invalid network button cannot change target");
            h.assertTrue(menu.clickMenuButton(player,0)&&panel.target()==25,"Validated menu lowers target by five");
            player.setPos(panelPos.getX()+100,panelPos.getY(),panelPos.getZ());h.assertTrue(!menu.clickMenuButton(player,1)&&panel.target()==25,"Distant player cannot change controller");
            h.assertTrue(panel.comparator()==15,"30C sensed temperature maps to full comparator strength");
            for(var heater:heaters)s.l.setBlock(heater.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
            s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(corePos,Blocks.GLASS.defaultBlockState(),2);ThermostatManager.reset();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void thermostatPrioritySplitMergeRemovalBreachAndReloadKeepSettings(GameTestHelper h) {
        scene(h,s->{
            ThermostatManager.reset();var a=s.c.west(2);var b=s.c.east(2);
            s.l.setBlock(a,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);var left=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(a);left.onLoad();left.setTarget(10);
            s.l.setBlock(b,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);var right=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(b);right.onLoad();right.setTarget(30);
            ThermostatManager.refresh(s.l);h.assertTrue(right.order()>left.order()&&left.reading().mode()==ThermostatManager.OVERRIDDEN,"Same-tick latest placement wins shared volume");
            var saved=right.saveWithFullMetadata(s.l.registryAccess());var restored=new com.frozendawn.block.ThermostatBlockEntity(b,right.getBlockState());restored.loadWithComponents(saved,s.l.registryAccess());
            h.assertTrue(restored.target()==30&&restored.order()==right.order()&&restored.wasSealed(),"Target, priority and indoor binding survive reload");
            var order=com.frozendawn.data.ThermostatOrderState.get(s.l);var sequence=com.frozendawn.data.ThermostatOrderState.load(order.save(new CompoundTag(),s.l.registryAccess()),s.l.registryAccess());
            h.assertTrue(sequence.allocate()>right.order(),"Next placement remains newer after saved sequence reload");
            partition(s,true);ThermostatManager.refresh(s.l);
            h.assertTrue(left.reading().mode()==ThermostatManager.SEALED&&right.reading().mode()==ThermostatManager.SEALED,"Split volumes recover independent controllers");
            partition(s,false);ThermostatManager.refresh(s.l);h.assertTrue(left.reading().mode()==ThermostatManager.OVERRIDDEN,"Merge reselects newest controller");
            s.l.setBlock(b,Blocks.AIR.defaultBlockState(),3);ThermostatManager.refresh(s.l);h.assertTrue(left.reading().mode()==ThermostatManager.SEALED,"Removing winner restores earlier thermostat");
            long priorId=RoomAtmosphere.view(s.l,a).id();s.l.setBlock(s.c.west(5),Blocks.AIR.defaultBlockState(),3);ThermostatManager.refresh(s.l);h.assertTrue(left.reading().mode()==ThermostatManager.NO_SEAL,"Breached indoor thermostat reports No seal rather than controlling outdoors");
            h.assertTrue(ThermostatManager.roomControl(s.l,priorId)==null,"Breached room falls back to heater defaults");
            s.l.setBlock(s.c.west(5),Blocks.GLASS.defaultBlockState(),3);ThermostatManager.refresh(s.l);h.assertTrue(left.reading().mode()==ThermostatManager.SEALED&&left.target()==10,"Reseal restores preserved target");
            s.l.setBlock(a,Blocks.AIR.defaultBlockState(),2);ThermostatManager.reset();budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void thermostatOpenCampSharesDemandAndLateBreachDoesNotExtendControl(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();
            // Elevate this camp above the deep geothermal background, which already exceeds25C.
            ApocalypseState.get(s.l.getServer()).setApocalypseTicks(0,s.l.getServer());s.l.setBlock(s.c.east(5),Blocks.AIR.defaultBlockState(),3);
            s.l.setBlock(s.c,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(s.c);panel.onLoad();panel.setTarget(25);
            var pos=s.c.west(2);s.l.setBlock(pos,ModBlocks.IRON_THERMAL_HEATER.get().defaultBlockState(),3);var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(10000);
            ThermostatManager.refresh(s.l);heater.serverTick();ThermostatManager.publish(s.l);
            h.assertTrue(panel.reading().mode()==ThermostatManager.OPEN&&heater.getBurnFraction()>0&&heater.getBurnFraction()<1,"Early open camp controls a loaded heater in eight blocks");
            near(h,panel.reading().sensed(),25,"Open sensor target includes background and heater warmth once");
            var saved=heater.saveWithFullMetadata(s.l.registryAccess());h.assertTrue(saved.getInt("BurnTime")<10000||saved.getDouble("FuelFraction")>0,"Open demand consumes real fuel");
            heater.extinguish();ThermostatManager.refresh(s.l);h.assertTrue(panel.reading().heaters()==1,"Installed open-camp heater stays linked after fuel runs out");
            ApocalypseState.get(s.l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(s.l.getServer()).getTotalDays()*24000L*.90),s.l.getServer());ThermostatManager.refresh(s.l);
            h.assertTrue(panel.reading().mode()==ThermostatManager.NO_SEAL&&ThermostatManager.openDuty(s.l,pos).isEmpty(),"Late phase vacuum does not control through an unsealed camp");
            s.l.setBlock(s.c,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);ThermostatManager.reset();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentRejectsHeatWithoutChangingSealGasOrLedger(GameTestHelper h) {
        scene(h,104,s->{
            var vent=s.c.east(5);var closed=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            s.l.setBlock(vent,closed,3);var view=RoomAtmosphere.view(s.l,s.c);hot(s,40,40);
            var baseline=s.heat().save(new CompoundTag(),s.l.registryAccess());double airOut=s.heat().ledger.airOut;
            RoomThermalManager.tickLevel(s.l);var passive=RoomThermalManager.snapshots(s.l).getFirst();budget(h,s);
            RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(baseline,s.l.registryAccess()));
            s.l.setBlock(vent,closed.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true),3);
            h.assertTrue(RoomAtmosphere.view(s.l,s.c).geometry()==view.geometry(),"Shutter movement reuses the exact sealed pressure geometry");
            RoomThermalManager.temperatureAt(s.l,s.c);RoomThermalManager.tickLevel(s.l);var active=RoomThermalManager.snapshots(s.l).getFirst();
            h.assertTrue((passive.airTemperature()-active.airTemperature())*active.airCapacity()>100,"Exposed fins remove additional air heat");
            h.assertTrue(active.totalEnergy()<passive.totalEnergy()&&active.airPresent()&&active.sealed(),"Heat leaves while the room keeps its trapped gas");
            near(h,s.heat().ledger.airOut,airOut,"Opening fins exports no air energy");h.assertTrue(!RoomAirState.get(s.l).isDepleted(view.geometry().cells()),"Cooling never evacuates oxygen");budget(h,s);
            var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());double energy=s.heat().totalEnergy();RoomThermalManager.reset();
            s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));RoomThermalManager.temperatureAt(s.l,s.c);
            near(h,s.heat().totalEnergy(),energy,"Open exchanger rebind preserves cooled reservoirs across saved-data reconstruction");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentBlockedRoofWrongFacingAndWarmExteriorCannotCool(GameTestHelper h) {
        scene(h,104,s->{
            var vent=s.c.east(5);var state=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            s.l.setBlock(vent,state,3);RoomAtmosphere.view(s.l,s.c);hot(s,40,40);
            var seed=s.heat().save(new CompoundTag(),s.l.registryAccess());
            double closed=ventStep(s,seed,state);double open=ventStep(s,seed,state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true));
            h.assertTrue(open<closed,"Guard: exterior in this fixture is colder and exposed");
            near(h,ventStep(s,seed,state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true).setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.WEST)),closed,"Reversed radiator cannot cool through its indoor face");
            var roof=vent.east().above(2);s.l.setBlock(roof,Blocks.GLASS.defaultBlockState(),3);
            double roofClosed=ventStep(s,seed,state);near(h,ventStep(s,seed,state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true)),roofClosed,"Even a transparent roof obstructs the exposed radiator");
            s.l.setBlock(roof,Blocks.AIR.defaultBlockState(),3);s.l.setBlock(vent.east(),Blocks.STONE.defaultBlockState(),3);
            double blockedClosed=ventStep(s,seed,state);near(h,ventStep(s,seed,state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true)),blockedClosed,"A blocked exterior face disables heat rejection");
            s.l.setBlock(vent.east(),Blocks.AIR.defaultBlockState(),3);s.l.setBlock(vent,state,3);
            RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(seed,s.l.registryAccess()));RoomThermalManager.temperatureAt(s.l,s.c);
            hot(s,-273.14,-273.14);var cold=s.heat().save(new CompoundTag(),s.l.registryAccess());
            near(h,ventStep(s,cold,state.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true)),ventStep(s,cold,state),"A passive vent adds no cooling against a warmer exterior");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentRedstoneAndVacuumStructureCoolingKeepAirDepleted(GameTestHelper h) {
        scene(h,104,s->{
            var vent=s.c.east(5);var state=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            s.l.setBlock(vent,state,3);var view=RoomAtmosphere.view(s.l,s.c);hot(s,40,40);RoomAirState.get(s.l).evacuate(view.geometry().cells());
            var seed=s.heat().save(new CompoundTag(),s.l.registryAccess());double closed=ventStep(s,seed,state);
            s.l.setBlock(vent.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.POWERED),"Real neighboring redstone holds the shutter open");
            var poweredSeed=s.heat().save(new CompoundTag(),s.l.registryAccess());
            // Identical physical boundary, with only the shutter power changed.
            var powered=s.l.getBlockState(vent);double unpowered=ventStep(s,poweredSeed,powered.setValue(com.frozendawn.block.HeatVentBlock.POWERED,false));
            double active=ventStep(s,poweredSeed,powered);
            h.assertTrue(active<unpowered,"Without gas, the exposed exchanger removes real stored structural energy");
            var snap=RoomThermalManager.snapshots(s.l).getFirst();h.assertTrue(!snap.airPresent()&&RoomAirState.get(s.l).isDepleted(view.geometry().cells()),"Radiation never creates or refills gas");budget(h,s);
            s.l.setBlock(vent.above(),Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(!s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.POWERED),"Removing redstone restores the manual closed state");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentExtremeBackgroundNeverCreatesNegativeGasEnergy(GameTestHelper h) {
        scene(h,310,s->{
            ApocalypseState.get(s.l.getServer()).setApocalypseTicks(ApocalypseState.get(s.l.getServer()).getTotalDays()*24000L,s.l.getServer());
            var state=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST).setValue(com.frozendawn.block.HeatVentBlock.OPEN,true);
            s.l.setBlock(s.c.east(5),state,3);RoomAtmosphere.view(s.l,s.c);hot(s,RoomHeatMath.ABSOLUTE_ZERO,RoomHeatMath.ABSOLUTE_ZERO);
            h.assertTrue(TemperatureManager.getBackgroundTemperature(s.c.getY(),ApocalypseState.get(s.l.getServer()).getCurrentDay(),ApocalypseState.get(s.l.getServer()).getTotalDays())<RoomHeatMath.ABSOLUTE_ZERO,"Guard: legacy high-altitude background is below absolute zero");
            RoomThermalManager.tickLevel(s.l);h.assertTrue(s.heat().rooms.values().stream().allMatch(r->r.airEnergy>=0),"Radiator removal cannot make a negative gas reservoir");
            near(h,s.heat().totalEnergy(),0,"Zero-energy reservoirs cannot lose additional heat");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentGlowRequiresActualRejectionAndPreservesAirAndHeat(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();var vent=s.c.east(5);var panelPos=s.c.west(2);
            var closed=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            s.l.setBlock(vent,closed,3);var view=RoomAtmosphere.view(s.l,s.c);hot(s,40,40);advanceHeat(s);ventVisualTick(s,vent);
            h.assertTrue(!s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Closed exchanger cannot glow without heat rejection");
            s.l.setBlock(vent,closed.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true),3);advanceHeat(s);
            double energy=s.heat().totalEnergy(),airOut=s.heat().ledger.airOut;long profiles=RoomThermalManager.snapshots(s.l).getFirst().profileBuilds();ventVisualTick(s,vent);
            h.assertTrue(s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Actual manual heat rejection lights the outdoor fins");
            near(h,s.heat().totalEnergy(),energy,"Changing the glow state supplies or removes no heat");
            near(h,s.heat().ledger.airOut,airOut,"Glowing fins export no room gas");
            h.assertTrue(RoomAtmosphere.view(s.l,s.c).geometry()==view.geometry()&&RoomThermalManager.snapshots(s.l).getFirst().profileBuilds()==profiles,"Glow preserves exact seal geometry and physical material profile");
            var roof=vent.east().above(2);s.l.setBlock(roof,Blocks.GLASS.defaultBlockState(),3);advanceHeat(s);ventVisualTick(s,vent);
            h.assertTrue(!s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Open but blocked radiator has no glow");
            s.l.setBlock(roof,Blocks.AIR.defaultBlockState(),3);hot(s,-273.14,-273.14);advanceHeat(s);ventVisualTick(s,vent);
            h.assertTrue(!RoomThermalManager.ventRejectingHeat(s.l,vent),"Open shutter against a warmer exterior cannot falsely report heat rejection");
            s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
            var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();
            try {
                hot(s,19,19);advanceHeat(s);ventVisualTick(s,vent);
                h.assertTrue(!s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Thermostat closes cooling below target even with a manual-open setting");
                hot(s,40,40);advanceHeat(s);ventVisualTick(s,vent);
                h.assertTrue(s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Automatic actual cooling also lights the fins");
                RoomAirState.get(s.l).evacuate(view.geometry().cells());hot(s,80,80);advanceHeat(s);ventVisualTick(s,vent);
                h.assertTrue(s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING)&&!s.heat().rooms.get(view.id()).airPresent,"Real airless wall heat rejection glows without refilling gas");budget(h,s);
                var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));ventVisualTick(s,vent);
                h.assertTrue(!s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Saved glowing state clears until the new session actually rejects heat");
                advanceHeat(s);ventVisualTick(s,vent);
                h.assertTrue(s.l.getBlockState(vent).getValue(com.frozendawn.block.HeatVentBlock.RADIATING),"Loaded heat rejection reconstructs the glow on the next real thermal second");budget(h,s);
            }finally{s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),3);ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void heatVentIndicatorTracksRealOutletAndKeepsPressureGeometry(GameTestHelper h) {
        scene(h,104,s->{
            var vent=s.c.east(5);var closed=ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(com.frozendawn.block.HeatVentBlock.FACING,net.minecraft.core.Direction.EAST);
            s.l.setBlock(vent,closed,3);var view=RoomAtmosphere.view(s.l,s.c);hot(s,40,40);
            h.assertTrue(RoomThermalManager.ventIndicator(s.l,vent)==0,"Closed service light is dim");
            s.l.setBlock(vent,closed.setValue(com.frozendawn.block.HeatVentBlock.OPEN,true),3);
            h.assertTrue(RoomThermalManager.ventIndicator(s.l,vent)==2,"Open exposed radiator shows actual heat rejection");
            var roof=vent.east().above(2);s.l.setBlock(roof,Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(RoomThermalManager.ventIndicator(s.l,vent)==1,"Open but roof-blocked radiator is amber");
            s.l.setBlock(roof,Blocks.AIR.defaultBlockState(),3);s.l.setBlock(vent.east(),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(RoomThermalManager.ventIndicator(s.l,vent)==1,"Solid obstruction is amber");
            s.l.setBlock(vent.east(),Blocks.AIR.defaultBlockState(),3);
            var energy=s.heat().totalEnergy();var profile=RoomThermalManager.snapshots(s.l).getFirst().profileBuilds();
            s.l.setBlock(vent,s.l.getBlockState(vent).setValue(com.frozendawn.block.HeatVentBlock.INDICATOR,2),3);
            h.assertTrue(RoomAtmosphere.view(s.l,s.c).geometry()==view.geometry(),"Light changes preserve exact airtight geometry");
            near(h,s.heat().totalEnergy(),energy,"Cosmetic light cannot add thermal energy");
            h.assertTrue(RoomThermalManager.snapshots(s.l).getFirst().profileBuilds()==profile,"Cosmetic light reuses material profile");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void coreAwareDefaultHeaterCapsFeltHeatWithStackedCores(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();var corePositions=List.of(s.c.east(5),s.c.east(5).above(),s.c.east(5).above(2));
            try {
                for(var pos:corePositions){s.l.setBlock(pos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,pos);}
                var heaterPos=s.c.west(2);s.l.setBlock(heaterPos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(heaterPos);heater.onLoad();heater.addFuel(10000);
                RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);
                var probe=s.c.east(4);var a=ApocalypseState.get(s.l.getServer());
                double target=TemperatureManager.thermostatTerms(s.l,probe).baseTarget(20);
                near(h,target,20,"Built-in target uses shared room temperature without subtracting a Core aura");hot(s,target-.1,target-.1);
                advanceHeat(s);
                double felt=TemperatureManager.getTemperatureAt(s.l,probe,a.getCurrentDay(),a.getTotalDays());
                h.assertTrue(felt<=50.000001,"Stacked passive Cores cannot exceed their physical supply temperature: "+felt);
                h.assertTrue(heater.getBurnFraction()<1,"Core contribution throttles actual heater fuel");budget(h,s);
            } finally {for(var pos:corePositions){s.l.setBlock(pos,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,pos);}ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void coreHotspotLimitsDistantThermostatAndPartitionChanges(GameTestHelper h) {
        scene(h,104,s->{
            ThermostatManager.reset();var cores=List.of(s.c.east(5),s.c.east(5).above(),s.c.east(5).above(2));
            var panelPos=s.c.west(4);var heaterPos=s.c.west(2);var probe=s.c.east(4);
            try {
                for(var pos:cores){s.l.setBlock(pos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,pos);}
                s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
                var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();panel.setTarget(20);
                s.l.setBlock(heaterPos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
                var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(heaterPos);heater.onLoad();heater.addFuel(10000);
                // A right-room heater remains active when this volume splits.
                var rightPos=s.c.east(2);s.l.setBlock(rightPos,ModBlocks.DIAMOND_THERMAL_HEATER.get().defaultBlockState(),3);
                var right=(ThermalHeaterBlockEntity)s.l.getBlockEntity(rightPos);right.onLoad();right.addFuel(10000);
                RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);var a=ApocalypseState.get(s.l.getServer());
                h.assertTrue(TemperatureManager.thermostatTerms(s.l,panelPos).warmth()==0,"Distant panel misses Core aura in the old position-only control");
                double base=TemperatureManager.thermostatTerms(s.l,probe).baseTarget(20);hot(s,base-.1,base-.1);
                for(int cycle=0;cycle<3;cycle++) {
                    partition(s,true);RoomAtmosphere.view(s.l,s.c.west(3));RoomAtmosphere.view(s.l,s.c.east(3));advanceHeat(s);
                    h.assertTrue(TemperatureManager.getTemperatureAt(s.l,probe,a.getCurrentDay(),a.getTotalDays())<=50.000001,"Resealed Core side stays bounded by its real passive supply temperature");budget(h,s);
                    partition(s,false);RoomAtmosphere.view(s.l,s.c);advanceHeat(s);
                    h.assertTrue(TemperatureManager.getTemperatureAt(s.l,probe,a.getCurrentDay(),a.getTotalDays())<=50.000001,"Merged room cannot double-count Core heat as a local bonus");budget(h,s);
                }
                hot(s,20,20);double imports=s.heat().ledger.heater;advanceHeat(s);
                near(h,s.heat().ledger.heater,imports,"Already-hot Core room stops adding heater heat; it does not erase stored heat");
                budget(h,s);s.l.setBlock(rightPos,Blocks.AIR.defaultBlockState(),2);
            } finally {s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(s.c.east(2),Blocks.AIR.defaultBlockState(),2);for(var pos:cores){s.l.setBlock(pos,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,pos);}ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void coreBreachRefillDoesNotBankHotWalls(GameTestHelper h) {
        coreWallCycle(h,false);
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void coreSealedEvacuationRefillDoesNotBankHotWalls(GameTestHelper h) {
        coreWallCycle(h,true);
    }
    private static void coreWallCycle(GameTestHelper h,boolean evacuateOnly) {
        scene(h,104,s->{
            ThermostatManager.reset();var cores=List.of(s.c.east(5),s.c.east(5).above(),s.c.east(5).above(2));
            var panelPos=s.c.west(4);var heaterPos=s.c.west(2);var other=s.c.east(2);var probe=s.c.east(4);var breach=s.c.west(5);
            try {
                for(var pos:cores){s.l.setBlock(pos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,pos);}
                s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
                var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();panel.setTarget(20);
                for(var pos:List.of(heaterPos,other)) {
                    s.l.setBlock(pos,(pos.equals(heaterPos)?ModBlocks.THERMAL_HEATER.get():ModBlocks.DIAMOND_THERMAL_HEATER.get()).defaultBlockState(),3);
                    var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(pos);heater.onLoad();heater.addFuel(100000);
                }
                var view=RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);
                double base=TemperatureManager.thermostatTerms(s.l,probe).baseTarget(20);hot(s,base,base);
                var a=ApocalypseState.get(s.l.getServer());
                for(int cycle=0;cycle<3;cycle++) {
                    if(evacuateOnly)RoomAirState.get(s.l).evacuate(view.geometry().cells());
                    else {s.l.setBlock(breach,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);}
                    h.assertTrue(RoomThermalManager.snapshots(s.l).stream().noneMatch(r->r.airPresent()),"Evacuation or actual breach removes gas");
                    for(int i=0;i<10;i++)advanceHeat(s);
                    for(var snap:RoomThermalManager.snapshots(s.l))h.assertTrue(snap.structureTemperature()<=50+1e-6,
                        "Airless heat cannot bank walls above heater or passive source supply temperatures: "+snap.structureTemperature());
                    budget(h,s);
                    if(!evacuateOnly){s.l.setBlock(breach,Blocks.GLASS.defaultBlockState(),3);view=RoomAtmosphere.view(s.l,s.c);}
                    RoomAirState.get(s.l).refill(view.geometry().cells());
                    for(int i=0;i<10;i++)advanceHeat(s);
                    h.assertTrue(TemperatureManager.getTemperatureAt(s.l,probe,a.getCurrentDay(),a.getTotalDays())<=50.00001,
                        "Refill cannot create temperatures above actual source temperatures");budget(h,s);
                }
            } finally {s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(other,Blocks.AIR.defaultBlockState(),2);
                for(var pos:cores){s.l.setBlock(pos,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,pos);}ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void unknownRoomCannotRestoreLegacyManagedHeaterBonuses(GameTestHelper h) {
        scene(h,s->{
            var heaterPos=s.c.west(2);s.l.setBlock(heaterPos,ModBlocks.DIAMOND_THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)s.l.getBlockEntity(heaterPos);heater.onLoad();heater.addFuel(100000);
            RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);heater.consumeRoomHeating(1,20,true);
            for(int y=3;y<=35;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                s.l.setBlock(s.c.offset(x,y,z),(Math.abs(x)==1||Math.abs(z)==1||y==35?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(s.l,s.c)==null,"Oversized closed extension is genuinely UNKNOWN");
            h.assertTrue(RoomThermalManager.managesHeater(s.l,heaterPos),"Managed heater remains suspended with its room");
            var a=ApocalypseState.get(s.l.getServer());double with=TemperatureManager.getTemperatureAt(s.l,s.c,a.getCurrentDay(),a.getTotalDays());
            heater.extinguish();double without=TemperatureManager.getTemperatureAt(s.l,s.c,a.getCurrentDay(),a.getTotalDays());
            near(h,with,without,"Suspended room heater cannot reappear as an unthrottled legacy aura");budget(h,s);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomHeatSourcesRespectWallsAndLegacyOutdoorWarmth(GameTestHelper h) {
        scene(h,104,s->{
            var core=s.c.east(7);var inside=s.c.west(3);var outside=s.c.east(6);
            try {
                s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,core);
                RoomAtmosphere.view(s.l,s.c);hot(s,20,20);
                var a=ApocalypseState.get(s.l.getServer());
                near(h,TemperatureManager.getTemperatureAt(s.l,inside,a.getCurrentDay(),a.getTotalDays()),20,"Unconnected outside Core cannot add heat through a sealed wall");
                h.assertTrue(TemperatureManager.thermostatTerms(s.l,outside).warmth()>0,"Unmanaged outside Core retains its ordinary outdoor warmth");
                near(h,RoomHeatSources.connected(s.l,RoomAtmosphere.view(s.l,s.c).geometry().cells(),RoomAtmosphere.view(s.l,s.c).geometry().boundaryFaces()).size(),0,"Only in-volume or directly adjoining sources supply room heat");budget(h,s);
            }finally{s.l.setBlock(core,Blocks.AIR.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,core);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomConnectedHeatUsesOneTemperatureAcrossSourceEdges(GameTestHelper h) {
        scene(h,104,s->{
            var core=s.c.east(5);var fire=s.c.east(3);var panelPos=s.c.west(4);
            try {
                s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,core);
                s.l.setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);
                s.l.setBlock(panelPos,ModBlocks.THERMOSTAT.get().defaultBlockState(),3);
                var panel=(com.frozendawn.block.ThermostatBlockEntity)s.l.getBlockEntity(panelPos);panel.onLoad();
                var view=RoomAtmosphere.view(s.l,s.c);hot(s,20,20);ThermostatManager.refresh(s.l);ThermostatManager.publish(s.l);
                var apocalypse=ApocalypseState.get(s.l.getServer());
                for(var cell:view.geometry().cells())near(h,TemperatureManager.getTemperatureAt(s.l,cell,apocalypse.getCurrentDay(),apocalypse.getTotalDays()),20,
                    "Every sealed-room cell uses actual room temperature across Core and soul-campfire range edges");
                near(h,panel.reading().sensed(),20,"Thermostat agrees with the same room-wide player reading");budget(h,s);
            }finally{s.l.setBlock(fire,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(panelPos,Blocks.AIR.defaultBlockState(),2);
                s.l.setBlock(core,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,core);ThermostatManager.reset();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomPassiveSourcesImportRealHeatWithoutInstantAura(GameTestHelper h) {
        scene(h,104,s->{
            var core=s.c.east(5);var fire=s.c.east(3);
            try {
                s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,core);
                s.l.setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);
                RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);hot(s,-40,-40);
                double old=s.heat().totalEnergy(),loss=s.heat().ledger.environmentLoss;advanceHeat(s);
                double supplied=s.heat().totalEnergy()-old+s.heat().ledger.environmentLoss-loss;
                h.assertTrue(supplied>100,"Connected Core and fire actually supply conserved reservoir heat without a heater: "+supplied);
                budget(h,s);
                var a=ApocalypseState.get(s.l.getServer());var base=RoomThermalManager.cachedTemperatureAt(s.l,s.c).orElseThrow();
                near(h,TemperatureManager.getTemperatureAt(s.l,s.c,a.getCurrentDay(),a.getTotalDays()),base,"Imported passive heat is not added a second time as an aura");
            }finally{s.l.setBlock(fire,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(core,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,core);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomSharedCorePowerIsNotDuplicatedBySplitOrReload(GameTestHelper h) {
        scene(h,104,s->{
            var core=s.c.above();
            try {
                s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,core);
                RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);hot(s,-40,-40);
                double original=s.heat().ledger.passive;advanceHeat(s);double one=s.heat().ledger.passive-original;
                h.assertTrue(one>0,"One real Core delivers finite heat");
                partition(s,true);s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
                RoomAtmosphere.view(s.l,s.c.west(2));RoomAtmosphere.view(s.l,s.c.east(2));hot(s,-40,-40);
                double previous=s.heat().ledger.passive;advanceHeat(s);
                near(h,s.heat().ledger.passive-previous,one,"Two rooms sharing one boundary Core share one heat budget");budget(h,s);
                var saved=s.heat().save(new CompoundTag(),s.l.registryAccess());double energy=s.heat().totalEnergy(),passive=s.heat().ledger.passive;
                RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
                RoomThermalManager.temperatureAt(s.l,s.c.west(2));RoomThermalManager.temperatureAt(s.l,s.c.east(2));
                near(h,s.heat().totalEnergy(),energy,"Reload retains existing Core heat rather than importing it again");
                near(h,s.heat().ledger.passive,passive,"Reload persists the new passive-source ledger");budget(h,s);
                advanceHeat(s);near(h,s.heat().ledger.passive-passive,one,"Reloaded shared source still has one budget");budget(h,s);
            }finally{s.l.setBlock(core,Blocks.AIR.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,core);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomPassiveTransferHonorsUpgradesLitStateAndVacuum(GameTestHelper h) {
        scene(h,104,s->{
            var corePos=s.c.east(5);var fire=s.c.east(3);
            try {
                s.l.setBlock(corePos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
                var original=RoomHeatSources.at(s.l,corePos).orElseThrow();
                var core=(com.frozendawn.block.GeothermalCoreBlockEntity)s.l.getBlockEntity(corePos);
                var data=core.saveWithFullMetadata(s.l.registryAccess());data.putInt("TempLevel",10);
                core.loadWithComponents(data,s.l.registryAccess());var upgraded=RoomHeatSources.at(s.l,corePos).orElseThrow();
                near(h,upgraded.power(),original.power()*2,"Core temperature upgrade doubles available heat transfer");
                near(h,upgraded.temperature(),original.temperature()*2,"Core supply temperature respects upgrade and surface penalty");
                near(h,upgraded.grant(100,upgraded.temperature()+1,1,true),0,"Core cannot refrigerate a hotter room");
                near(h,upgraded.grant(1,upgraded.temperature()-.25,1,true),.25,"Finite source heat cannot overshoot supply temperature");
                near(h,upgraded.grant(10000,-200,1,false),upgraded.power()*.25,"Vacuum transfers only reduced power into walls");
                s.l.setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);
                h.assertTrue(RoomHeatSources.at(s.l,fire).isPresent(),"Lit soul campfire supplies heat");
                s.l.setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,false),3);
                h.assertTrue(RoomHeatSources.at(s.l,fire).isEmpty(),"Unlit campfire supplies no stored heat");
                s.l.setBlock(fire,Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true),3);
                h.assertTrue(RoomHeatSources.at(s.l,fire).isPresent(),"Lit furnace supplies room heat");
                s.l.setBlock(fire,Blocks.FURNACE.defaultBlockState(),3);
                h.assertTrue(RoomHeatSources.at(s.l,fire).isEmpty(),"Paused unlit furnace supplies no heat");
                var far=s.c.offset(100000,0,100000);h.assertTrue(!s.l.isLoaded(far),"Probe chunk starts unloaded");
                h.assertTrue(RoomHeatSources.at(s.l,far).isEmpty()&&!s.l.isLoaded(far),"Source scan never forces a chunk load");
            }finally{s.l.setBlock(fire,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(corePos,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,corePos);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void roomUnknownCannotRestoreCoreOrFireAuras(GameTestHelper h) {
        scene(h,s->{
            var core=s.c.east(5);var fire=s.c.east(2);
            try {
                s.l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,core);
                s.l.setBlock(fire,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.c);RoomThermalManager.tickLevel(s.l);
                for(int y=3;y<=35;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                    s.l.setBlock(s.c.offset(x,y,z),(Math.abs(x)==1||Math.abs(z)==1||y==35?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
                h.assertTrue(RoomAtmosphere.view(s.l,s.c)==null,"Extension makes room geometry UNKNOWN");
                h.assertTrue(RoomThermalManager.hasManagedRoomAt(s.l,s.c),"Suspended room retains its known membership");
                var a=ApocalypseState.get(s.l.getServer());
                h.assertTrue(TemperatureManager.getHeatSourceModifier(s.l,s.c,a.getCurrentDay(),a.getTotalDays(),false)>0,"Real nearby sources remain present");
                near(h,TemperatureManager.thermostatTerms(s.l,s.c).warmth(),0,"Suspended Core/fire cannot reappear as additive local heat");
                near(h,TemperatureManager.getTemperatureAt(s.l,s.c,a.getCurrentDay(),a.getTotalDays()),
                    TemperatureManager.getBackgroundTemperature(s.c.getY(),a.getCurrentDay(),a.getTotalDays())+TemperatureManager.getShelterModifier(s.l,s.c),
                    "UNKNOWN fallback excludes duplicate source heat");budget(h,s);
            }finally{s.l.setBlock(fire,Blocks.AIR.defaultBlockState(),2);s.l.setBlock(core,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,core);}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_heat",timeoutTicks=100)
    public static void coreSplitHeatImportsScaleAndRetainStoredEnergy(GameTestHelper h){scene(h,s->{
        var pos=s.c.east(5);
        try{
            s.l.setBlock(pos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);GeothermalCoreRegistry.register(s.l,pos);
            var core=(com.frozendawn.block.GeothermalCoreBlockEntity)s.l.getBlockEntity(pos);
            RoomThermalManager.temperatureAt(s.l,s.c);hot(s,-50,-50);
            double before=s.heat().ledger.passive;advanceHeat(s);double normal=s.heat().ledger.passive-before;
            h.assertTrue(normal>0,"Default Core imports real thermal energy");budget(h,s);
            hot(s,-50,-50);core.setHeatShare(30);before=s.heat().ledger.passive;advanceHeat(s);
            near(h,s.heat().ledger.passive-before,normal*.5,"30% heat transfers half the default 60% at identical reservoir temperatures");budget(h,s);
            double retained=s.heat().totalEnergy();core.setHeatShare(0);near(h,s.heat().totalEnergy(),retained,"Turning down the Core never deletes stored warmth");
            before=s.heat().ledger.passive;advanceHeat(s);near(h,s.heat().ledger.passive,before,"Zero heat supplies no reservoir energy");budget(h,s);
            near(h,core.getOxygenSupply(),20,"All-O2 setting exchanges heat for oxygen capacity");
        }finally{s.l.setBlock(pos,Blocks.GLASS.defaultBlockState(),2);GeothermalCoreRegistry.unregister(s.l,pos);}
    });}

    private static void smallRoom(Scene s) {
        for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)s.l.setBlock(s.c.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
            s.l.setBlock(s.c.offset(x,y,z),(Math.abs(x)==2||Math.abs(z)==2||y==-1||y==3?Blocks.WHITE_WOOL:Blocks.AIR).defaultBlockState(),2);
    }
    private static void ventVisualTick(Scene s,BlockPos vent) {
        long time=s.l.getGameTime();var clock=(net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData();
        clock.setGameTime(Math.floorDiv(time,20)*20);
        try{((com.frozendawn.block.HeatVentBlockEntity)s.l.getBlockEntity(vent)).serverTick();}finally{clock.setGameTime(time);}
    }
    private static void advanceHeat(Scene s) {
        var time=s.l.getGameTime();s.integrationTime=Math.max(time,s.integrationTime)+20;
        ((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(s.integrationTime);
        try {RoomThermalManager.tickLevel(s.l);}finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
    }
    private static double ventStep(Scene s,CompoundTag saved,net.minecraft.world.level.block.state.BlockState state) {
        RoomThermalManager.reset();s.l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(saved,s.l.registryAccess()));
        s.l.setBlock(s.c.east(5),state,3);RoomThermalManager.temperatureAt(s.l,s.c);RoomThermalManager.tickLevel(s.l);budgetForStep(s);
        return s.heat().totalEnergy();
    }
    private static void budgetForStep(Scene s) {
        if(Math.abs(s.heat().totalEnergy()-s.heat().ledger.net())>1e-5)throw new AssertionError("Vent step must balance energy ledger");
    }
    private static void hot(Scene s,double air,double walls) {
        for(var r:s.heat().rooms.values())r.airEnergy=RoomHeatMath.energy(r.airCapacity(),air);
        for(var m:s.heat().materials.values())m.energy=RoomHeatMath.energy(m.capacity,walls);
        // Deliberate test heat imports are explicit ledger entries, distinct from production heaters.
        double delta=s.heat().totalEnergy()-s.heat().ledger.net();
        if(delta>=0)s.heat().ledger.heater+=delta;else s.heat().ledger.environmentLoss-=delta;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<=1e-6*Math.max(1,Math.abs(expected)),message+": "+actual+" vs "+expected);
    }
    private static void budget(GameTestHelper h,Scene s){near(h,s.heat().totalEnergy(),s.heat().ledger.net(),"Every gas/material/heat transfer has a matching energy ledger entry");}
    private static void partition(Scene s,boolean closed) {
        for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)s.l.setBlock(s.c.offset(0,y,z),(closed?Blocks.GLASS:Blocks.AIR).defaultBlockState(),3);
    }
    private static void scene(GameTestHelper h,Consumer<Scene> test){scene(h,3,test);}
    private static void scene(GameTestHelper h,int height,Consumer<Scene> test){var s=new Scene(h,height);try{test.accept(s);h.succeed();}finally{s.close();}}
    private static final class Scene {
        final ServerLevel l;final BlockPos c;final long phase;final CompoundTag identities,air,heat;long integrationTime;
        Scene(GameTestHelper h) {this(h,3);}
        Scene(GameTestHelper h,int height) {
            l=h.getLevel();c=h.absolutePos(new BlockPos(10,height,10));var a=ApocalypseState.get(l.getServer());phase=a.getApocalypseTicks();
            identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());heat=RoomThermalState.get(l).save(new CompoundTag(),l.registryAccess());
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),l.getServer());RoomAtmosphere.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();AtmosphericBreach.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());l.getDataStorage().set(RoomThermalState.NAME,new RoomThermalState());
            // Include a loaded exterior margin and air above; the room itself has 81 cells.
            for(int x=-8;x<=8;x++)for(int y=-4;y<=5;y++)for(int z=-5;z<=5;z++)l.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
                l.setBlock(c.offset(x,y,z),(Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        RoomThermalState heat(){return RoomThermalState.get(l);}
        void close() {
            l.setBlock(c.west(2),Blocks.AIR.defaultBlockState(),2);ApocalypseState.get(l.getServer()).setApocalypseTicks(phase,l.getServer());
            RoomAtmosphere.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();AtmosphericBreach.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(heat,l.registryAccess()));
        }
    }
}
