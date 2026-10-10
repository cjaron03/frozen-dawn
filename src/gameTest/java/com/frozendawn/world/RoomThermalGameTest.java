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
            var terms=TemperatureManager.thermostatTerms(s.l,panelPos);h.assertTrue(terms.warmth()>0,"Real registered Core contributes local warmth");
            // Finish dirty wall profiles before importing controlled thermal fixture energy.
            RoomThermalManager.tickLevel(s.l);
            double base=terms.baseTarget(30);
            var apocalypse=ApocalypseState.get(s.l.getServer());
            h.assertTrue(TemperatureManager.getBackgroundTemperature(panelPos.getY(),apocalypse.getCurrentDay(),apocalypse.getTotalDays())<base,"Heating fixture starts colder than the Core-adjusted gas target; passive heat cannot require cooling");
            hot(s,base-.1,base-.1);double before=s.heat().ledger.heater;
            var time=s.l.getGameTime();((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time+20);
            try {RoomThermalManager.tickLevel(s.l);}finally {((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(time);}
            var value=RoomThermalManager.snapshots(s.l).getFirst();
            near(h,panel.reading().sensed(),30,"Core-aware sensor reaches its selected target rather than air target plus Core");
            near(h,value.airTemperature(),base,"Core remains an additive local term, not duplicated in gas energy");
            near(h,TemperatureManager.getTemperatureAt(s.l,panelPos,ApocalypseState.get(s.l.getServer()).getCurrentDay(),ApocalypseState.get(s.l.getServer()).getTotalDays()),30,"Player environmental reading agrees at sensor");
            h.assertTrue(panel.reading().heaters()==2&&heaters.stream().allMatch(heater->heater.getBurnFraction()>0&&heater.getBurnFraction()<1),"Mixed upgraded tiers link and throttle");
            h.assertTrue(s.heat().ledger.heater>before,"Actual grant is accounted");budget(h,s);
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
    private static void hot(Scene s,double air,double walls) {
        for(var r:s.heat().rooms.values())r.airEnergy=RoomHeatMath.energy(r.airCapacity(),air);
        for(var m:s.heat().materials.values())m.energy=RoomHeatMath.energy(m.capacity,walls);
        // Deliberate test heat imports are explicit ledger entries, distinct from production heaters.
        s.heat().ledger.heater+=s.heat().totalEnergy()-s.heat().ledger.net();
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
        final ServerLevel l;final BlockPos c;final long phase;final CompoundTag identities,air,heat;
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
