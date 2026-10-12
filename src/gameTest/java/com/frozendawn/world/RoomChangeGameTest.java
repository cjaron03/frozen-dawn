package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomChangeGameTest {
    @BeforeBatch(batch="room_change")
    public static void report(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change",timeoutTicks=100)
    public static void roomChangeAirtightSharedWallOnlyReportsMaterial(GameTestHelper h) {
        scene(h, events -> {
            var l=h.getLevel();var center=room(h);partition(h,center,true);
            var left=RoomAtmosphere.view(l,center.west(2));var right=RoomAtmosphere.view(l,center.east(2));events.clear();
            var before=l.getBlockState(center);l.setBlock(center,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(events.size()==1&&events.getFirst().type()==RoomChangeEvent.Type.WALL_MATERIAL,"Airtight replacement reports material only");
            var change=(RoomChangeEvent.WallMaterialChange)events.getFirst().change();
            h.assertTrue(change.position().equals(center)&&change.before()==before&&change.after().is(Blocks.STONE),"Native hook retains exact changed block and states");
            h.assertTrue(change.rooms().equals(List.of(left,right)),"Both sides of physical shared partition are notified");
            h.assertTrue(RoomAtmosphere.view(l,center.west(2)).geometry()==left.geometry()
                    &&RoomAtmosphere.view(l,center.east(2)).geometry()==right.geometry(),"Material update does not flood-fill or replace geometry snapshots");
            h.assertTrue(RoomAtmosphere.hasAir(l,center.west(2))&&RoomAtmosphere.hasAir(l,center.east(2)),"Material update preserves pressure");
            l.setBlock(center,Blocks.STONE.defaultBlockState(),3);
            l.setBlock(center.west(2),Blocks.SHORT_GRASS.defaultBlockState(),2);
            h.assertTrue(events.size()==1,"Same-state write and nonboundary passage replacement are silent");
            try {change.rooms().clear();h.fail("Event snapshots must be immutable");}catch(UnsupportedOperationException expected){}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change",timeoutTicks=100)
    public static void roomChangeMergeAndSplitPublishAtomicMembershipSnapshots(GameTestHelper h) {
        scene(h,events -> {
            var l=h.getLevel();var center=room(h);partition(h,center,true);
            var left=RoomAtmosphere.view(l,center.west(2));var right=RoomAtmosphere.view(l,center.east(2));events.clear();
            Consumer<RoomChangeEvent> observer=event -> {
                if(event.level()==l&&event.change() instanceof RoomChangeEvent.GeometryChange g&&g.complete())
                    h.assertTrue(RoomAtmosphere.trackedRooms(l).equals(g.currentRooms()),"Subscribers see all reconciled children, never a partially published split");
            };
            NeoForge.EVENT_BUS.addListener(observer);
            try {
                partition(h,center,false);RoomAtmosphere.view(l,center.east(2));
                h.assertTrue(events.size()==1,"One merged-volume notification for the batched geometry edit");
                var merge=(RoomChangeEvent.GeometryChange)events.getFirst().change();
                h.assertTrue(merge.previousRooms().equals(List.of(left,right))&&merge.currentRooms().size()==1,"Merge supplies both old parents and one resulting volume");
                h.assertTrue(merge.previousMemberships().size()==2&&merge.complete(),"Saved overlap memberships are available for conservative energy reconciliation");
                var merged=merge.currentRooms().getFirst();events.clear();
                partition(h,center,true);RoomAtmosphere.view(l,center.east(2));
                h.assertTrue(events.size()==1,"One split notification after every sibling is rebuilt");
                var split=(RoomChangeEvent.GeometryChange)events.getFirst().change();
                h.assertTrue(split.previousRooms().equals(List.of(merged))&&split.currentRooms().size()==2,"Split provides one parent and both disjoint children");
                h.assertTrue(split.currentRooms().stream().mapToInt(r->r.geometry().cells().size()).sum()==72,"Partition consumes exactly its nine former air cells");
                h.assertTrue(merge.currentRooms().getFirst().geometry().cells().size()==81,"Earlier immutable snapshot is unchanged by a later split");
                events.clear();RoomAtmosphere.tickLevel(l);RoomAtmosphere.view(l,center.west(2));
                h.assertTrue(events.isEmpty(),"Unchanged polling does not repeat geometry notifications");
            } finally {NeoForge.EVENT_BUS.unregister(observer);}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change",timeoutTicks=100)
    public static void roomChangeBreachAndResealKeepGeometrySeparateFromAir(GameTestHelper h) {
        scene(h,events -> {
            var l=h.getLevel();var center=room(h);var initial=RoomAtmosphere.view(l,center);events.clear();
            l.setBlock(center.east(5),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(l,center)==null,"Native breach invalidates the room");
            h.assertTrue(events.size()==2&&events.getFirst().type()==RoomChangeEvent.Type.AIR_STATE
                    &&events.getLast().type()==RoomChangeEvent.Type.GEOMETRY,"Breach separately reports real air loss and geometry removal");
            var air=(RoomChangeEvent.AirStateChange)events.getFirst().change();
            h.assertTrue(air.depleted()&&air.cells().equals(initial.geometry().cells())&&air.roomIds().equals(Set.of(initial.id())),"Evacuation covers the authoritative old volume and identity");
            var breach=(RoomChangeEvent.GeometryChange)events.getLast().change();
            h.assertTrue(breach.complete()&&breach.previousRooms().equals(List.of(initial))&&breach.currentRooms().isEmpty(),"Completed open geometry retires the live volume");
            events.clear();l.setBlock(center.east(5),Blocks.GLASS.defaultBlockState(),3);
            var resealed=RoomAtmosphere.view(l,center);
            h.assertTrue(events.size()==1&&events.getFirst().type()==RoomChangeEvent.Type.GEOMETRY,"Reseal does not emit fake refill");
            h.assertTrue(resealed.id()==initial.id()&&!RoomAtmosphere.hasAir(l,center),"Identity survives; lost oxygen stays lost");
            var restore=(RoomChangeEvent.GeometryChange)events.getFirst().change();
            h.assertTrue(restore.previousMemberships().stream().anyMatch(m->m.id()==initial.id()),"Dormant membership remains available on reseal");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change",timeoutTicks=100)
    public static void roomChangeUnknownSuspendsOnceAndRepairPublishesRecovery(GameTestHelper h) {
        scene(h,events -> {
            var l=h.getLevel();var center=room(h);long id=RoomAtmosphere.view(l,center).id();events.clear();
            for(int y=3;y<=35;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                boolean wall=Math.abs(x)==1||Math.abs(z)==1||y==35;
                l.setBlock(center.offset(x,y,z),(wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            }
            events.clear(); // Construction may legitimately replace roof materials before opening it.
            h.assertTrue(RoomAtmosphere.view(l,center)==null,"Oversized sealed extension is UNKNOWN");
            h.assertTrue(events.size()==1&&events.getFirst().change() instanceof RoomChangeEvent.GeometryChange g&&!g.complete(),"Unknown geometry publishes suspension, not a completed breach");
            RoomAtmosphere.view(l,center);RoomAtmosphere.tickLevel(l);
            h.assertTrue(events.size()==1&&!RoomAirState.get(l).isDepleted(Set.of(center)),"Repeated uncertainty emits no duplicate and destroys no trapped air");
            l.setBlock(center.above(3),Blocks.GLASS.defaultBlockState(),3);events.clear();
            h.assertTrue(RoomAtmosphere.view(l,center).id()==id,"Repair resumes the saved identity");
            h.assertTrue(events.size()==1&&events.getFirst().change() instanceof RoomChangeEvent.GeometryChange g&&g.complete(),"Restored certainty emits geometry recovery even if the old shape is restored");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change",timeoutTicks=100)
    public static void roomChangeAirAuthorityEmitsOnlyRealChangedCellsAndSurvivesReload(GameTestHelper h) {
        scene(h,events -> {
            var l=h.getLevel();var center=room(h);var initial=RoomAtmosphere.view(l,center);events.clear();
            var air=RoomAirState.get(l);var mutable=center.mutable();var cells=new HashSet<BlockPos>();cells.add(mutable);
            air.evacuate(cells);
            h.assertTrue(events.size()==1&&air.isDepleted(Set.of(center)),"Air event follows persisted state mutation");
            var change=(RoomChangeEvent.AirStateChange)events.getFirst().change();mutable.move(5,0,0);cells.clear();
            h.assertTrue(change.cells().equals(Set.of(center))&&change.roomIds().equals(Set.of(initial.id())),"Payload defensively copies positions and resolves identity");
            air.evacuate(Set.of(center));air.refill(Set.of(center.east()));
            h.assertTrue(events.size()==1,"No-op air mutations emit nothing");
            h.assertTrue(RoomAtmosphere.view(l,center).geometry()==initial.geometry(),"Air-only mutation retains cached geometry and canonical identity");
            var nbt=air.save(new CompoundTag(),l.registryAccess());RoomAtmosphere.reset();events.clear();
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(nbt,l.registryAccess()));
            h.assertTrue(events.isEmpty(),"NBT load emits no live air transition");
            RoomAirState.get(l).refill(Set.of(center));
            h.assertTrue(events.size()==1&&events.getFirst().change() instanceof RoomChangeEvent.AirStateChange restored
                    &&!restored.depleted()&&restored.roomIds().equals(Set.of(initial.id())),"Reload binds authority and uncached membership still resolves refill");
            h.assertTrue(RoomAtmosphere.trackedRooms(l).isEmpty(),"Publishing refill does not force geometry discovery");
            var unloaded=new BlockPos(1234567,200,1234567);events.clear();
            h.assertFalse(l.isLoaded(unloaded),"Exterior diagnostic cell starts unloaded");
            RoomAirState.get(l).evacuate(Set.of(unloaded));
            h.assertTrue(events.isEmpty()&&!l.isLoaded(unloaded),"Unowned vacuum cells emit no room notification and force no chunks");
        });
    }

    @BeforeBatch(batch="room_change_core")
    public static void reportCore(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_change_core",timeoutTicks=150)
    public static void roomChangeCoreRefillTimerSurvivesMaterialReplacement(GameTestHelper h) {
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long phase=a.getApocalypseTicks();
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        var oldAir=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        var events=new ArrayList<RoomChangeEvent>();Consumer<RoomChangeEvent> capture=e->{if(e.level()==l)events.add(e);};
        var core=h.absolutePos(new BlockPos(9,3,10));
        Runnable cleanup=()->{
            NeoForge.EVENT_BUS.unregister(capture);l.setBlock(core,Blocks.AIR.defaultBlockState(),2);
            a.setApocalypseTicks(phase,l.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(oldAir,l.registryAccess()));
        };
        try {
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),l.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());
            var center=room(h);
            l.setBlock(core,com.frozendawn.init.ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);l.getBlockEntity(core).onLoad();
            var initial=RoomAtmosphere.view(l,center);NeoForge.EVENT_BUS.addListener(capture);
            RoomAirState.get(l).evacuate(initial.geometry().cells());
            h.assertFalse(RoomAtmosphere.hasAir(l,center),"Core refill requires five loaded seconds");
            h.runAfterDelay(60,()->{
                try {
                    l.setBlock(center.east(5),Blocks.STONE.defaultBlockState(),3);
                    h.assertTrue(RoomAtmosphere.view(l,center).id()==initial.id()&&!RoomAtmosphere.hasAir(l,center),"Airtight wall swap preserves identity while recovery remains in progress");
                } catch(RuntimeException|Error e) {cleanup.run();throw e;}
            });
            h.runAfterDelay(105,()->{
                try {
                    h.assertTrue(RoomAtmosphere.hasAir(l,center),"Material replacement does not restart the original five-second refill timer");
                    var airEvents=events.stream().filter(e->e.type()==RoomChangeEvent.Type.AIR_STATE).toList();
                    h.assertTrue(airEvents.size()==2&&((RoomChangeEvent.AirStateChange)airEvents.getFirst().change()).depleted()
                            &&!((RoomChangeEvent.AirStateChange)airEvents.getLast().change()).depleted(),"One evacuation and one completed Core refill transition");
                    h.assertTrue(events.stream().filter(e->e.type()==RoomChangeEvent.Type.WALL_MATERIAL).count()==1
                            &&events.stream().noneMatch(e->e.type()==RoomChangeEvent.Type.GEOMETRY),"Material-only edit leaves air geometry stable throughout recovery");
                    h.succeed();
                } finally {cleanup.run();}
            });
        } catch(RuntimeException|Error e) {cleanup.run();throw e;}
    }

    private static BlockPos room(GameTestHelper h) {
        var center=h.absolutePos(new BlockPos(10,3,10));var l=h.getLevel();
        for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++) {
            boolean wall=Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3;
            l.setBlock(center.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        return center;
    }
    private static void partition(GameTestHelper h,BlockPos center,boolean closed) {
        for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)h.getLevel().setBlock(center.offset(0,y,z),(closed?Blocks.GLASS:Blocks.AIR).defaultBlockState(),3);
    }
    private static void scene(GameTestHelper h,Consumer<List<RoomChangeEvent>> test) {
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long ticks=a.getApocalypseTicks();
        var identities=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
        var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        var events=new ArrayList<RoomChangeEvent>();Consumer<RoomChangeEvent> capture=e->{if(e.level()==l)events.add(e);};
        try {
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),l.getServer());
            RoomAtmosphere.reset();AtmosphericBreach.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());
            l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());NeoForge.EVENT_BUS.addListener(capture);
            test.accept(events);h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);a.setApocalypseTicks(ticks,l.getServer());
            RoomAtmosphere.reset();AtmosphericBreach.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,l.registryAccess()));
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));
        }
    }
}
