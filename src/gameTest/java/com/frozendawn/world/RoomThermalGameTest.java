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
            RoomAirState.get(s.l).evacuate(view.geometry().cells());double heat=s.heat().ledger.heater;
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
    private static void scene(GameTestHelper h,Consumer<Scene> test){var s=new Scene(h);try{test.accept(s);h.succeed();}finally{s.close();}}
    private static final class Scene {
        final ServerLevel l;final BlockPos c;final long phase;final CompoundTag identities,air,heat;
        Scene(GameTestHelper h) {
            l=h.getLevel();c=h.absolutePos(new BlockPos(10,3,10));var a=ApocalypseState.get(l.getServer());phase=a.getApocalypseTicks();
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
