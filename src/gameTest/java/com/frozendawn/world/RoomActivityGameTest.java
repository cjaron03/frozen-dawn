package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomActivityGameTest {
    @BeforeBatch(batch="room_activity")
    public static void report(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }

    @BeforeBatch(batch="room_activity_live")
    public static void reportLive(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_activity_live",timeoutTicks=1400)
    public static void roomActivityRealLitHeaterSurvivesIdleExpiryThenReleasesOnBurnout(GameTestHelper h) {
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long phase=a.getApocalypseTicks();
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        var center=h.absolutePos(new BlockPos(10,3,10));var heaterPos=center.west(2);
        var discoveries=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<RoomChangeEvent> capture=event->{
            if(event.level()==l&&event.change() instanceof RoomChangeEvent.GeometryChange g&&g.complete()
                    &&g.currentRooms().stream().anyMatch(r->r.geometry().cells().contains(center.west(3)))) discoveries.incrementAndGet();
        };
        Runnable cleanup=()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(capture);
            l.setBlock(heaterPos,Blocks.AIR.defaultBlockState(),2);
            a.setApocalypseTicks(phase,l.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));
        };
        try {
            a.setApocalypseTicks(0,l.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());
            l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());
            shell(h,center);partition(h,center,true);
            l.setBlock(heaterPos,ModBlocks.THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)l.getBlockEntity(heaterPos);heater.onLoad();heater.addFuel(100000);
            // Query only the passive sibling. The lit room must discover itself without a player query.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(capture);
            long passive=RoomAtmosphere.view(l,center.east(2)).id();
            h.runAfterDelay(30,()->{
                try {h.assertTrue(discoveries.get()==1,"Heater autonomously discovers its sealed room within one heartbeat");}
                catch(RuntimeException|Error e){cleanup.run();throw e;}
            });
            for (int delay : new int[]{100, 300, 500, 600}) h.runAfterDelay(delay,()->{
                try {
                    var diagnostic=RoomAtmosphere.cachedRoomDiagnostics(l).stream().filter(r->r.id()==passive).findFirst();
                    h.assertTrue(diagnostic.isPresent() && diagnostic.get().idleTicks() >= delay,
                            "Repeated cache inspection must not renew the unheated sibling's query lease");
                } catch(RuntimeException|Error e){cleanup.run();throw e;}
            });
            h.runAfterDelay(650,()->{
                try {
                    var rooms=fixtureRooms(l,center);
                    // At Phase0 this deep fixture may already exceed20C: the new default correctly idles.
                    var temperature=RoomThermalManager.cachedTemperatureAt(l,center.west(3));
                    h.assertTrue(heater.saveWithFullMetadata(l.registryAccess()).getInt("BurnTime")<100000
                                    || temperature.isPresent()&&temperature.getAsDouble()>=20&&heater.getBurnFraction()==0,
                            "Actual ticker consumes demanded fuel, or correctly idles an already-warm room");
                    h.assertTrue(rooms.size()==1&&rooms.getFirst().geometry().walls().contains(heaterPos),"Lit heater discovers and retains its room beyond 600 idle ticks");
                    h.assertTrue(discoveries.get()==1,"Lit room stays continuously cached without eviction/re-discovery notifications");
                    var diagnostic=RoomAtmosphere.cachedRoomDiagnostics(l).stream().filter(r->r.geometry().walls().contains(heaterPos)).findFirst().orElseThrow();
                    h.assertTrue(diagnostic.active() && diagnostic.sources().contains(heaterPos)
                            && diagnostic.lastQueryReason().equals("INFRASTRUCTURE_DISCOVERY"),
                            "Diagnostics identify the exact heartbeat source without inventing player queries");
                    h.assertTrue(rooms.stream().noneMatch(r->r.id()==passive),"Unheated sibling still expires without room queries");
                    h.assertTrue(RoomAtmosphere.activeRooms(l).stream().filter(r->r.geometry().walls().contains(heaterPos)).count()==1,"Read-only activity snapshots identify the fixture heater room");
                    long id=rooms.getFirst().id();heater.extinguish();
                    h.runAfterDelay(50,()->{
                        try {
                            h.assertTrue(RoomAtmosphere.activeRooms(l).stream().noneMatch(r->r.id()==id),"Burnout stops the fixture room renewable activity lease");
                            h.assertTrue(RoomAtmosphere.trackedRooms(l).stream().anyMatch(r->r.id()==id),"Stopping heat retains the ordinary idle grace period");
                        } catch(RuntimeException|Error e){cleanup.run();throw e;}
                    });
                } catch(RuntimeException|Error e){cleanup.run();throw e;}
            });
            h.runAfterDelay(1320,()->{
                try {
                    h.assertTrue(fixtureRooms(l,center).isEmpty(),"Burned-out heater room eventually expires without phantom renewal");
                    h.succeed();
                } finally {cleanup.run();}
            });
        } catch(RuntimeException|Error e){cleanup.run();throw e;}
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_activity",timeoutTicks=100)
    public static void roomActivityRebindsAfterCacheReloadAndTracksBothSplitChildren(GameTestHelper h) {
        var l=h.getLevel();var center=h.absolutePos(new BlockPos(10,3,10));
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        var positions=List.of(center.west(2),center.east(2));
        try {
            RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());shell(h,center);
            for(var pos:positions)l.setBlock(pos,ModBlocks.IRON_THERMAL_HEATER.get().defaultBlockState(),3);
            var first=(ThermalHeaterBlockEntity)l.getBlockEntity(positions.getFirst());
            var second=(ThermalHeaterBlockEntity)l.getBlockEntity(positions.getLast());first.addFuel(100000);second.addFuel(100000);
            RoomAtmosphere.keepAlive(first);RoomAtmosphere.keepAlive(second);RoomAtmosphere.tickLevel(l);
            var initial=RoomAtmosphere.trackedRooms(l);h.assertTrue(initial.size()==1,"Two sources share one canonical room");long id=initial.getFirst().id();
            RoomAtmosphere.reset();RoomAtmosphere.keepAlive(first);RoomAtmosphere.keepAlive(second);RoomAtmosphere.tickLevel(l);
            h.assertTrue(RoomAtmosphere.trackedRooms(l).size()==1&&RoomAtmosphere.trackedRooms(l).getFirst().id()==id,"Loaded heartbeat restores canonical membership after cache reset");
            partition(h,center,true);RoomAtmosphere.tickLevel(l);
            RoomAtmosphere.keepAlive(first);RoomAtmosphere.keepAlive(second);RoomAtmosphere.tickLevel(l);
            h.assertTrue(RoomAtmosphere.trackedRooms(l).size()==2&&RoomAtmosphere.activityStats(l).active()==2,"Geometry split assigns renewal to each source's own child");
            partition(h,center,false);RoomAtmosphere.tickLevel(l);RoomAtmosphere.keepAlive(first);RoomAtmosphere.keepAlive(second);
            h.assertTrue(RoomAtmosphere.trackedRooms(l).size()==1&&RoomAtmosphere.activityStats(l).active()==1,"Merge leaves one active volume with no duplicate source-owned room");
            first.setRemoved();second.setRemoved();RoomAtmosphere.keepAlive(first);RoomAtmosphere.keepAlive(second);
            h.succeed();
        } finally {
            for(var pos:positions)l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));
        }
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_activity",timeoutTicks=100)
    public static void roomActivityGenericControllerHookRejectsUnloadedOrReplacedSources(GameTestHelper h) {
        var l=h.getLevel();var center=h.absolutePos(new BlockPos(10,3,10));
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        try {
            RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());shell(h,center);
            // A vanilla chest supplies a real passable BE to exercise the future thermostat heartbeat contract.
            // This is hook coverage, not a claim that the thermostat block exists.
            l.setBlock(center,Blocks.CHEST.defaultBlockState(),3);var controller=l.getBlockEntity(center);
            RoomAtmosphere.keepAlive(controller);RoomAtmosphere.tickLevel(l);
            h.assertTrue(RoomAtmosphere.activityStats(l).active()==1,"Passable controller heartbeat keeps its containing volume active");
            l.setBlock(center,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.reset();RoomAtmosphere.keepAlive(controller);RoomAtmosphere.tickLevel(l);
            h.assertTrue(RoomAtmosphere.trackedRooms(l).isEmpty(),"Removed/replaced block entity cannot renew or discover pressure geometry");
            var unloaded=new BlockPos(1234567,200,1234567);var stale=new ThermalHeaterBlockEntity(unloaded,ModBlocks.THERMAL_HEATER.get().defaultBlockState());stale.setLevel(l);
            h.assertFalse(l.isLoaded(unloaded),"Diagnostic source chunk starts unloaded");RoomAtmosphere.keepAlive(stale);RoomAtmosphere.tickLevel(l);
            h.assertTrue(!l.isLoaded(unloaded)&&RoomAtmosphere.trackedRooms(l).isEmpty(),"Unloaded activity source neither forces chunks nor discovers a false room");
            h.succeed();
        } finally {l.setBlock(center,Blocks.AIR.defaultBlockState(),2);RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));}
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_activity",timeoutTicks=100)
    public static void roomActivityCachePressureEvictsPassiveRoomsBeforeActiveInfrastructure(GameTestHelper h) {
        var l=h.getLevel();var center=h.absolutePos(new BlockPos(10,3,10));var heaterPos=center.west(2);
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        try {
            RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());shell(h,center);
            l.setBlock(heaterPos,ModBlocks.GOLD_THERMAL_HEATER.get().defaultBlockState(),3);
            var heater=(ThermalHeaterBlockEntity)l.getBlockEntity(heaterPos);heater.addFuel(100000);
            RoomAtmosphere.keepAlive(heater);RoomAtmosphere.tickLevel(l);
            var active=RoomAtmosphere.trackedRooms(l).getFirst();
            for(int i=0;i<260;i++) {
                var cell=center.offset((i%8)*2,5+(i/64)*2,((i/8)%8)*2);
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                    l.setBlock(cell.offset(x,y,z),(x==0&&y==0&&z==0?Blocks.AIR:Blocks.STONE).defaultBlockState(),2);
                h.assertTrue(RoomAtmosphere.view(l,cell)!=null,"Cache-pressure fixture room is fully sealed");
            }
            h.assertTrue(RoomAtmosphere.trackedRooms(l).size()==256,"Ordinary cache remains bounded when passive eviction is available");
            h.assertTrue(RoomAtmosphere.trackedRooms(l).contains(active)&&RoomAtmosphere.activityStats(l).active()==1,
                    "More than 256 discoveries retain active infrastructure's original geometry record");
            h.succeed();
        } finally {l.setBlock(heaterPos,Blocks.AIR.defaultBlockState(),2);RoomAtmosphere.reset();l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));}
    }

    private static List<RoomAtmosphere.RoomView> fixtureRooms(net.minecraft.server.level.ServerLevel level,BlockPos center) {
        return RoomAtmosphere.trackedRooms(level).stream().filter(r->r.geometry().cells().contains(center.west(3))
                ||r.geometry().cells().contains(center.east(2))).toList();
    }

    private static void shell(GameTestHelper h,BlockPos center) {
        for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++) {
            boolean wall=Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3;
            h.getLevel().setBlock(center.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
    }
    private static void partition(GameTestHelper h,BlockPos center,boolean closed) {
        for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlock(center.offset(0,y,z),(closed?Blocks.GLASS:Blocks.AIR).defaultBlockState(),3);
    }
}
