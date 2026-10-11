package com.frozendawn.world;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.oxygen.OxygenStatus;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomOxygenGameTest {
    @BeforeBatch(batch="room_oxygen") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenSharedCoreBoundaryBurnerAndTopologyChangesDoNotDuplicate(GameTestHelper h){scene(h,s->{
        var l=s.l;var left=s.c.west(2);var right=s.c.east(2);
        l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
        l.setBlock(s.c.above(),Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);
        l.setBlock(left,Blocks.TORCH.defaultBlockState(),3);l.setBlock(left.south(),Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES,4).setValue(BlockStateProperties.LIT,true),3);
        l.setBlock(right,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);l.setBlock(right.south(),Blocks.SOUL_TORCH.defaultBlockState(),3);
        var a=RoomAtmosphere.view(l,left.north());var b=RoomAtmosphere.view(l,right.north());h.assertTrue(a!=null&&b!=null&&a.id()!=b.id(),"Two sealed room fixture");
        RoomOxygenManager.tickLevel(l);var budgets=RoomOxygenManager.snapshots(l);h.assertTrue(budgets.size()==2,"Exactly two known rooms");
        for(var r:budgets){near(h,r.supply(),8,"Shared Core is 8 total, not 16");near(h,r.demand(),4.25,"One shared furnace plus four candles and one torch, souls exempt");h.assertTrue(r.sources()==1&&r.rooms()==2,"One physical source connects both rooms");}
        var extra=s.c.above(2);l.setBlock(extra,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(extra)).onLoad();s.sources.add(extra);
        RoomAtmosphere.view(l,left.north());RoomAtmosphere.view(l,right.north());RoomOxygenManager.tickLevel(l);for(var r:RoomOxygenManager.snapshots(l))near(h,r.supply(),16,"Overlapping second Core contributes once");
        l.setBlock(extra,Blocks.STONE.defaultBlockState(),3);RoomOxygenManager.tickLevel(l);for(var r:RoomOxygenManager.snapshots(l))near(h,r.supply(),8,"Removed Core loses capacity");
        l.setBlock(s.c.above(),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(l,left.north());RoomOxygenManager.tickLevel(l);
        h.assertTrue(RoomOxygenManager.snapshots(l).size()==1,"Opening partition merges canonical rooms");near(h,RoomOxygenManager.snapshots(l).getFirst().demand(),1.25,"Removed furnace disappears from demand");
        l.setBlock(s.c.above(),Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);RoomAtmosphere.view(l,left.north());RoomAtmosphere.view(l,right.north());RoomOxygenManager.tickLevel(l);
        h.assertTrue(RoomOxygenManager.snapshots(l).size()==2,"Closing partition restores independent identities and one shared supply");
        var remote=s.c.west(6);l.setBlock(remote,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(remote)).onLoad();s.sources.add(remote);
        RoomOxygenManager.tickLevel(l);for(var r:RoomOxygenManager.snapshots(l))near(h,r.supply(),8,"Range alone cannot supply through unrelated walls");
        var old=RoomOxygenManager.snapshots(l);RoomOxygenManager.reset();RoomOxygenManager.tickLevel(l);h.assertTrue(RoomOxygenManager.snapshots(l).equals(old),"Runtime reconstruction does not grant or duplicate capacity");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenDiscoversUnvisitedRoomTouchingSharedSourceBeforeSafeReading(GameTestHelper h){scene(h,s->{
        var l=s.l;var left=s.c.west(2);var right=s.c.east(2);
        l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
        var cow=new Cow(EntityType.COW,l);cow.setPos(right.getCenter());l.addFreshEntity(cow);
        try {
            RoomAtmosphere.view(l,left);h.assertTrue(RoomAtmosphere.trackedRooms(l).size()==1,"Only the first room has been queried");
            RoomOxygenManager.tickLevel(l);h.assertTrue(RoomOxygenManager.readingAt(l,left).mode()==OxygenStatus.Mode.WAITING,"Uninspected shared source intake cannot show safe");
            for(int i=0;i<4;i++)RoomOxygenManager.tickLevel(l);
            h.assertTrue(RoomOxygenManager.snapshots(l).size()==2,"Source discovers its other directly attached sealed volume without an operator query");
            for(var r:RoomOxygenManager.snapshots(l)){near(h,r.supply(),8,"Source budget stays single");near(h,r.demand(),5,"Previously unvisited room's cow is included");}
        } finally {cow.discard();}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenOccupantsRefreshWithoutRebuildingCachedBlockProfiles(GameTestHelper h){scene(h,s->{
        var l=s.l;var left=s.c.west(2);var right=s.c.east(2);RoomAtmosphere.view(l,left);RoomAtmosphere.view(l,right);
        var player=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"oxygen_breather"));player.setGameMode(GameType.SURVIVAL);player.setPos(left.getCenter());l.addNewPlayer(player);
        var cow=new Cow(EntityType.COW,l);cow.setPos(left.getCenter());l.addFreshEntity(cow);
        try {
            RoomOxygenManager.tickLevel(l);long builds=RoomOxygenManager.profileBuilds(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),6,"One survival player plus adult cow");
            cow.setPos(right.getCenter());RoomOxygenManager.tickLevel(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),1,"Moving cow updates original room");near(h,RoomOxygenManager.readingAt(l,right).localDemand(),5,"Moving cow updates destination room");
            player.setGameMode(GameType.CREATIVE);RoomOxygenManager.tickLevel(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),0,"Creative has no room breathing demand");
            player.setGameMode(GameType.SPECTATOR);RoomOxygenManager.tickLevel(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),0,"Spectator excluded");
            player.setGameMode(GameType.ADVENTURE);RoomOxygenManager.tickLevel(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),1,"Adventure breathes");
            h.assertTrue(builds==RoomOxygenManager.profileBuilds(l),"Entity movement/mode changes do not scan cells or rebuild block membership");
        } finally {l.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);cow.discard();}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenGaugeReportsDepletionBreachAndReloadWithoutChangingAir(GameTestHelper h){scene(h,s->{
        var l=s.l;var p=s.c.west(2);l.setBlock(p,ModBlocks.OXYGEN_GAUGE.get().defaultBlockState().setValue(OxygenGaugeBlock.FACING,Direction.NORTH),3);
        s.gauges.add(p);var gauge=(OxygenGaugeBlockEntity)l.getBlockEntity(p);gauge.onLoad();var view=RoomAtmosphere.view(l,p);RoomOxygenManager.tickLevel(l);
        var before=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());for(int i=0;i<20;i++)RoomOxygenManager.tickLevel(l);
        h.assertTrue(before.equals(RoomAirState.get(l).save(new CompoundTag(),l.registryAccess())),"Demand accounting cannot mutate persisted room air");
        ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
        RoomAirState.get(l).evacuate(view.geometry().cells());RoomOxygenManager.tickLevel(l);
        h.assertTrue(gauge.reading().mode()==OxygenStatus.Mode.NO_AIR&&gauge.comparator()==15,"Existing depleted air is red and comparator 15");
        var replica=new OxygenGaugeBlockEntity(p,gauge.getBlockState());replica.loadWithComponents(gauge.getUpdateTag(l.registryAccess()),l.registryAccess());
        h.assertTrue(replica.reading().equals(gauge.reading()),"Native block update tag transports full readings to renderer");
        var saved=gauge.saveWithoutMetadata(l.registryAccess());h.assertTrue(!saved.contains("Mode"),"Cached telemetry is not persisted as safe after reload");
        replica.loadWithComponents(saved,l.registryAccess());h.assertTrue(replica.reading().mode()==OxygenStatus.Mode.WAITING,"Reload starts unknown until authoritative cache rebuild");
        l.setBlock(s.c.west(4).above(),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(l,p);RoomOxygenManager.tickLevel(l);
        h.assertTrue(RoomOxygenManager.readingAt(l,p).mode()==OxygenStatus.Mode.NO_SEAL,"Actual open volume is red no seal");
        RoomAtmosphere.reset();h.assertTrue(RoomOxygenManager.readingAt(l,p).mode()==OxygenStatus.Mode.WAITING,"Unknown geometry never retains a safe gauge reading");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenBlastPitSupplyAndConsumerStateChangesStayObservable(GameTestHelper h){scene(h,s->{
        var l=s.l;var left=s.c.west(2);var source=s.c;BlastPitWarmZoneRegistry.register(l,source);s.pits.add(source);
        RoomAtmosphere.view(l,left);RoomAtmosphere.view(l,s.c.east(2));RoomOxygenManager.tickLevel(l);for(var r:RoomOxygenManager.snapshots(l))near(h,r.supply(),8,"One Blast Pit anchor supplies one shared pool");
        var candle=left.south();l.setBlock(candle,Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES,4).setValue(BlockStateProperties.LIT,true),3);RoomAtmosphere.view(l,left);RoomOxygenManager.tickLevel(l);
        near(h,RoomOxygenManager.readingAt(l,left).localDemand(),1,"Four lit candles draw one rate unit");
        l.setBlock(candle,l.getBlockState(candle).setValue(BlockStateProperties.LIT,false),3);RoomOxygenManager.tickLevel(l);near(h,RoomOxygenManager.readingAt(l,left).localDemand(),0,"Unlit consumer drops demand without geometry scan");
        BlastPitWarmZoneRegistry.unregister(l,source);RoomOxygenManager.tickLevel(l);for(var r:RoomOxygenManager.snapshots(l))near(h,r.supply(),0,"Removed Blast Pit cannot retain free capacity");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenOutdoorSourceProbeCannotEvacuateSampledAir(GameTestHelper h){scene(h,s->{
        var l=s.l;var p=s.c.west(4);l.setBlock(p,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        ((GeothermalCoreBlockEntity)l.getBlockEntity(p)).onLoad();s.sources.add(p);
        RoomAtmosphere.view(l,s.c.west(2));
        ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
        var before=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        for(int i=0;i<20;i++)RoomOxygenManager.tickLevel(l);
        h.assertTrue(RoomAtmosphere.cachedSealAt(l,p.west())==RoomAtmosphere.Seal.OPEN,"Loaded outdoor source intake is accounted as open");
        h.assertTrue(before.equals(RoomAirState.get(l).save(new CompoundTag(),l.registryAccess())),"Observing outdoor intakes cannot evacuate sampled cells or change persisted air");
        near(h,RoomOxygenManager.readingAt(l,s.c.west(2)).supply(),8,"Exterior sample does not suppress the sealed room source capacity");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenOpenIntakeResolvesAfterRepeatedGeometryInvalidation(GameTestHelper h){scene(h,s->{
        var l=s.l;var left=s.c.west(2);var right=s.c.east(2);
        l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        ((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
        RoomAtmosphere.view(l,left);RoomAtmosphere.view(l,right);
        for(int i=0;i<4;i++)RoomOxygenManager.tickLevel(l);
        l.setBlock(s.c.west(4).above(),Blocks.AIR.defaultBlockState(),3);
        RoomAtmosphere.view(l,left);RoomAtmosphere.view(l,right);
        var open=s.c.west();
        var remote=s.c.offset(8,0,0);
        var airBefore=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        for(int i=0;i<4;i++){
            // Late-phase terrain edits invalidate negative/open samples between accounting checks.
            RoomAtmosphere.blockChanged(l,remote,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            h.assertTrue(RoomAtmosphere.cachedSealAt(l,open)==RoomAtmosphere.Seal.UNKNOWN,"Open source intake invalidated before update");
            RoomOxygenManager.tickLevel(l);
            h.assertTrue(RoomAtmosphere.cachedSealAt(l,open)==RoomAtmosphere.Seal.OPEN,"Bounded probe resolves the breached intake as open");
            var reading=RoomOxygenManager.readingAt(l,right);
            h.assertTrue(reading.mode()==OxygenStatus.Mode.SUPPLIED,"Unbreached supplied sibling must settle in the same update, got "+reading.mode());
            near(h,reading.supply(),8,"Shared source remains single");near(h,reading.demand(),0,"Breached room is no longer in the sealed pool");
            h.assertTrue(reading.rooms()==1&&reading.sources()==1,"Only sealed sibling remains in the source pool");
        }
        h.assertTrue(airBefore.equals(RoomAirState.get(l).save(new CompoundTag(),l.registryAccess())),"Open intake readiness checks cannot vent or refill air");
    });}
    private record Scene(ServerLevel l,BlockPos c,List<BlockPos> sources,List<BlockPos> pits,List<BlockPos> gauges) {}
    private static void scene(GameTestHelper h,java.util.function.Consumer<Scene> test){
        var l=h.getLevel();var phase=ApocalypseState.get(l.getServer());long ticks=phase.getApocalypseTicks();var identity=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
        var s=new Scene(l,h.absolutePos(new BlockPos(10,3,10)),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        try {phase.setApocalypseTicks(0,l.getServer());RoomAtmosphere.reset();RoomOxygenManager.reset();CombustionAtmosphere.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());
            for(int x=-4;x<=4;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)l.setBlock(s.c.offset(x,y,z),(Math.abs(x)==4||y==-1||y==3||Math.abs(z)==2||x==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            test.accept(s);h.succeed();
        } finally {for(var p:s.gauges)l.setBlock(p,Blocks.AIR.defaultBlockState(),2);for(var p:s.sources)l.setBlock(p,Blocks.AIR.defaultBlockState(),2);for(var p:s.pits)BlastPitWarmZoneRegistry.unregister(l,p);
            phase.setApocalypseTicks(ticks,l.getServer());RoomAtmosphere.reset();RoomOxygenManager.reset();CombustionAtmosphere.reset();AtmosphericBreach.reset();RoomThermalManager.reset();
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identity,l.registryAccess()));l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));}
    }
    private static void near(GameTestHelper h,double a,double b,String why){h.assertTrue(Math.abs(a-b)<1e-8,why+": "+a+" vs "+b);}
}
