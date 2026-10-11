package com.frozendawn.world;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.oxygen.OxygenStatus;
import com.frozendawn.oxygen.OxygenControlledFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
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
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFurnacesShedSequentiallyAndRecoverWithoutOverbooking(GameTestHelper h){scene(h,s->{
        var l=s.l;long clock=l.getGameTime();
        var cow=new Cow(EntityType.COW,l);
        try {
            l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
            var positions=List.of(s.c.west(2),s.c.west(2).south(),s.c.east(2),s.c.east(2).south());
            for(var p:positions)fueled(l,p,Blocks.FURNACE);
            cow.setPos(s.c.west().north().getCenter());l.addFreshEntity(cow);
            RoomAtmosphere.view(l,s.c.west().north());RoomAtmosphere.view(l,s.c.east().north());
            for(int i=0;i<5;i++)RoomOxygenManager.tickLevel(l);
            ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
            var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
            for(int t=0;t<200;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,positions)==0,"Full 10-second grace before shedding");
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+200);RoomOxygenManager.tickLevel(l);h.assertTrue(paused(l,positions)==1,"One furnace per accounting check");
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+220);RoomOxygenManager.tickLevel(l);h.assertTrue(paused(l,positions)==2,"Second shed only when still overloaded");
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+240);RoomOxygenManager.tickLevel(l);h.assertTrue(paused(l,positions)==3,"Three furnaces paused to fit cow 5 plus furnace 3 into supply 8");
            for(int t=260;t<=400;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,positions)==3,"No restart without capacity for the extra 3 units");
            for(var r:RoomOxygenManager.snapshots(l)){near(h,r.supply(),8,"Shared supply counted once");near(h,r.demand(),8,"Post-shed gauge budget refreshed");}
            h.assertTrue(air.equals(RoomAirState.get(l).save(new CompoundTag(),l.registryAccess())),"Overload cannot evacuate sealed gas or emit a breach");
            var extra=s.c.above();l.setBlock(extra,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(extra)).onLoad();s.sources.add(extra);
            RoomAtmosphere.view(l,s.c.west().north());RoomAtmosphere.view(l,s.c.east().north());
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+420);RoomOxygenManager.tickLevel(l);
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+440);RoomOxygenManager.tickLevel(l);h.assertTrue(paused(l,positions)==3,"Recovery must remain stable for two seconds");
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+460);RoomOxygenManager.tickLevel(l);
            h.assertTrue(paused(l,positions)==1,"Two sleepers fit supply 16; the third cannot overbook it");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),14,"Recovery reserves resumed demand immediately");
            cow.discard();
            for(int t=480;t<=520;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,positions)==0,"Removing livestock admits the last sleeping furnace automatically");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),12,"All four burners counted once after restart");
        } finally {cow.discard();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFurnacePausePersistsFuelProgressAndInventoryAcrossReload(GameTestHelper h){scene(h,s->{
        for(var block:List.of(Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.SMOKER)){
            var p=s.c.west(2);var f=fueled(s.l,p,block);
            var paused=(OxygenControlledFurnace)f;paused.frozendawn$starveForOxygen();
            for(int i=0;i<400;i++)AbstractFurnaceBlockEntity.serverTick(s.l,p,s.l.getBlockState(p),f);
            var save=f.saveWithoutMetadata(s.l.registryAccess());
            h.assertTrue(!s.l.getBlockState(p).getValue(BlockStateProperties.LIT),"World flame stays out");
            h.assertTrue(save.getInt("BurnTime")==0&&save.getInt("FrozenDawnPausedBurnTime")==1000,"Prepaid fuel held separately; GUI flame is off");
            h.assertTrue(save.getInt("CookTime")==70&&f.getItem(1).getCount()==4&&f.getItem(2).isEmpty(),"Paused cooking never decays or consumes queued fuel");
            var replacement=(AbstractFurnaceBlockEntity)f.getType().create(p,s.l.getBlockState(p));
            replacement.loadWithComponents(save,s.l.registryAccess());s.l.setBlockEntity(replacement);
            for(int i=0;i<100;i++)AbstractFurnaceBlockEntity.serverTick(s.l,p,s.l.getBlockState(p),replacement);
            h.assertTrue(((OxygenControlledFurnace)replacement).frozendawn$isOxygenStarved(),"Reload cannot auto-light a saved pause before admission");
            h.assertTrue(replacement.saveWithoutMetadata(s.l.registryAccess()).getInt("CookTime")==70,"Reload preserves cooking progress");
            ((OxygenControlledFurnace)replacement).frozendawn$resumeForOxygen();
            AbstractFurnaceBlockEntity.serverTick(s.l,p,s.l.getBlockState(p),replacement);
            var resumed=replacement.saveWithoutMetadata(s.l.registryAccess());
            h.assertTrue(resumed.getInt("BurnTime")==999&&resumed.getInt("CookTime")==71&&replacement.getItem(1).getCount()==4,"Resume spends existing fuel once and continues the same recipe");
            ((OxygenControlledFurnace)replacement).frozendawn$starveForOxygen();
            replacement.setItem(0,new ItemStack(Items.SAND));
            h.assertTrue(replacement.saveWithoutMetadata(s.l.registryAccess()).getInt("CookTime")==0,"Changing recipe input retains vanilla progress reset; no progress transfer exploit");
            s.l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        }
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFurnaceRecoveryWaitsForKnownAirAndEarlyAirNeedsNoCore(GameTestHelper h){scene(h,s->{
        var l=s.l;long clock=l.getGameTime();var p=s.c.west(2);var f=fueled(l,p,Blocks.FURNACE);
        try {
            RoomAtmosphere.view(l,s.c.west().north());
            for(int t=0;t<=300;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(!((OxygenControlledFurnace)f).frozendawn$isOxygenStarved(),"Early ambient air never requires a Core");
            ((OxygenControlledFurnace)f).frozendawn$starveForOxygen();
            ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
            RoomAtmosphere.reset();
            for(int t=320;t<=500;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);AbstractFurnaceBlockEntity.serverTick(l,p,l.getBlockState(p),f);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(((OxygenControlledFurnace)f).frozendawn$isOxygenStarved(),"Unknown memberships cannot restart a furnace or accrue recovery");
            var source=s.c;l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(source)).onLoad();s.sources.add(source);
            var view=RoomAtmosphere.view(l,s.c.west().north());RoomAirState.get(l).evacuate(view.geometry().cells());
            for(int t=520;t<=600;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(((OxygenControlledFurnace)f).frozendawn$isOxygenStarved(),"Supply numbers alone cannot ignite a room without gas");
            RoomAirState.get(l).refill(view.geometry().cells());CombustionAtmosphere.reset();
            for(int t=620;t<=700;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(!((OxygenControlledFurnace)f).frozendawn$isOxygenStarved(),"Known air and stable supported capacity permit automatic restart");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);}
    });}
    private static long paused(ServerLevel l,List<BlockPos> positions){return positions.stream()
        .filter(p->((OxygenControlledFurnace)l.getBlockEntity(p)).frozendawn$isOxygenStarved()).count();}
    private static AbstractFurnaceBlockEntity fueled(ServerLevel l,BlockPos p,Block block){
        l.setBlock(p,block.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);
        var f=(AbstractFurnaceBlockEntity)l.getBlockEntity(p);
        f.setItem(0,new ItemStack(block==Blocks.SMOKER?Items.BEEF:Items.RAW_IRON,16));f.setItem(1,new ItemStack(Items.COAL,4));
        var tag=f.saveWithoutMetadata(l.registryAccess());tag.putInt("BurnTime",1000);tag.putInt("CookTime",70);tag.putInt("CookTimeTotal",block==Blocks.FURNACE?200:100);
        f.loadWithComponents(tag,l.registryAccess());return f;
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFurnaceReplayFunctionsLoad(GameTestHelper h){
        var server=h.getLevel().getServer();
        var functions=server.getResourceManager().listResources("function",id->id.getNamespace().equals("room_oxygen_check")&&id.getPath().endsWith(".mcfunction"));
        h.assertTrue(functions.size()==21,"All authored O2 test functions present, including read-only furnace checkpoints");
        functions.forEach((file,res)->{
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("room_oxygen_check",file.getPath().substring("function/".length()).replace(".mcfunction",""));
            h.assertTrue(server.getFunctions().get(id).isPresent(),"Native command parser loads "+id);
        });h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFurnaceGraceSurvivesRoutineLiveRoomReinspection(GameTestHelper h){scene(h,s->{
        var l=s.l;long clock=l.getGameTime();
        try {
            l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
            var positions=List.of(s.c.west(2),s.c.east(2),s.c.east(2).south());
            for(var p:positions)fueled(l,p,Blocks.FURNACE);
            RoomAtmosphere.view(l,s.c.west().north());RoomAtmosphere.view(l,s.c.east().north());
            for(int i=0;i<5;i++)RoomOxygenManager.tickLevel(l);
            ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
            var initial=RoomAtmosphere.cachedRoomViews(l);
            for(int t=0;t<200;t+=20){
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);
                // Real integrated-server pressure maintenance reconstructs geometry every second.
                RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);
                h.assertTrue(paused(l,positions)==0,"Full grace with routine pressure maintenance running");
            }
            var refreshed=RoomAtmosphere.cachedRoomViews(l);
            h.assertTrue(initial.stream().allMatch(old->refreshed.stream().anyMatch(r->r.id()==old.id()&&r.geometry().equals(old.geometry())&&r.geometry()!=old.geometry())),
                    "Periodic maintenance produces equivalent fresh geometry objects");
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+200);
            RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);
            h.assertTrue(paused(l,positions)==1,"Unchanged live room rechecks must not reset the ten-second furnace grace");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),6,"One furnace sheds from 9 to 6 despite reinspection");
            var second=s.c.above();l.setBlock(second,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(second)).onLoad();s.sources.add(second);
            for(int t=220;t<=260;t+=20){
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);
                RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);
                if(t<260)h.assertTrue(paused(l,positions)==1,"Recovery waits for two seconds despite routine reinspection");
            }
            h.assertTrue(paused(l,positions)==0,"Routine room rechecks also preserve supported recovery time");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),9,"Automatic restart restores full demand");
        } finally {((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenMixedFlamesShedByDemandWithoutVentingAndStayOut(GameTestHelper h){scene(h,s->{
        var l=s.l;long clock=l.getGameTime();var cows=new ArrayList<Cow>();
        try {
            l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
            var furnaces=List.of(s.c.above(),s.c.above(2));for(var p:furnaces)fueled(l,p,Blocks.FURNACE);
            var torch=s.c.west(2).south();var wall=s.c.west(3).above();var lantern=s.c.east(2).above(2);
            var candle=s.c.west(2).north();var cake=s.c.east(2).north();var camp=s.c.east(2).south();
            var fire=s.c.west(3).north();var mite=s.c.east(3).north();
            l.setBlock(torch,Blocks.TORCH.defaultBlockState(),3);
            l.setBlock(wall,Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,Direction.EAST),3);
            l.setBlock(lantern,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true),3);
            l.setBlock(candle,Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES,4).setValue(BlockStateProperties.LIT,true),3);
            l.setBlock(cake,Blocks.CANDLE_CAKE.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);
            l.setBlock(camp,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,true),3);
            l.setBlock(fire.below(),Blocks.NETHERRACK.defaultBlockState(),3);l.setBlock(fire,Blocks.FIRE.defaultBlockState(),3);
            l.setBlock(mite,ModBlocks.MITEAWAY.get().defaultBlockState().setValue(MiteAwayBlock.LIT,true),3);
            var repellent=(MiteAwayBlockEntity)l.getBlockEntity(mite);repellent.onLoad();int charge=repellent.getBurnTimeRemaining();
            var flames=List.of(torch,wall,lantern,candle,cake,camp,fire,mite);
            var soul=s.c.east(3).south();l.setBlock(soul,Blocks.SOUL_CAMPFIRE.defaultBlockState(),3);
            var soulTorch=s.c.west(3).south();l.setBlock(soulTorch,Blocks.SOUL_TORCH.defaultBlockState(),3);
            for(int i=0;i<2;i++){var cow=new Cow(EntityType.COW,l);cow.setPos(s.c.west().north().getCenter());l.addFreshEntity(cow);cows.add(cow);}
            RoomAtmosphere.view(l,s.c.west().north());RoomAtmosphere.view(l,s.c.east().north());for(int i=0;i<5;i++)RoomOxygenManager.tickLevel(l);
            ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
            var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
            for(int t=0;t<200;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,furnaces)==0&&flames.stream().allMatch(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))),"Full grace leaves every consumer on");
            for(int t=200;t<=220;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,furnaces)==2&&flames.stream().allMatch(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))),"Three-unit furnaces shed before any lower-demand flame");
            var remaining=new HashSet<>(flames);
            for(int t=240;!remaining.isEmpty()&&t<=500;t+=20){
                double highest=remaining.stream().mapToDouble(p->RoomOxygenManager.combustionDemand(l.getBlockState(p))).max().orElseThrow();
                var rates=new HashMap<BlockPos,Double>();for(var p:remaining)rates.put(p,RoomOxygenManager.combustionDemand(l.getBlockState(p)));
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);
                var shed=remaining.stream().filter(p->!VacuumFlames.isOrdinaryFlame(l.getBlockState(p))).toList();
                h.assertTrue(shed.size()==1,"One ordinary flame block sheds per second after the furnace pauses");near(h,rates.get(shed.getFirst()),highest,"Highest remaining demand sheds first");remaining.removeAll(shed);
            }
            h.assertTrue(remaining.isEmpty(),"Breathing-only overload sheds every ordinary flame");
            h.assertTrue(l.getBlockState(torch).is(ModBlocks.SPENT_TORCH.get()),"Torch remains in place as spent");
            h.assertTrue(l.getBlockState(wall).is(ModBlocks.SPENT_WALL_TORCH.get())&&l.getBlockState(wall).getValue(WallTorchBlock.FACING)==Direction.EAST,"Wall torch retains facing");
            h.assertTrue(l.getBlockState(lantern).is(ModBlocks.EXTINGUISHED_LANTERN.get())&&l.getBlockState(lantern).getValue(BlockStateProperties.HANGING),"Lantern retains hanging mount");
            h.assertTrue(l.getBlockState(candle).is(Blocks.WHITE_CANDLE)&&l.getBlockState(candle).getValue(CandleBlock.CANDLES)==4,"Colored multi-candle stack retained");
            h.assertTrue(l.getBlockState(cake).is(Blocks.CANDLE_CAKE)&&l.getBlockState(camp).is(Blocks.CAMPFIRE)&&l.getBlockState(fire).isAir(),"Cake and campfire retained; fire removed");
            for(int i=0;i<100;i++)repellent.serverTick();h.assertTrue(repellent.getBurnTimeRemaining()==charge,"Starved MiteAway retains unspent charge");
            h.assertTrue(l.getBlockState(soul).getValue(BlockStateProperties.LIT)&&l.getBlockState(soulTorch).is(Blocks.SOUL_TORCH),"Soul flames exempt");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),10,"Breathing remains counted after shedding all combustion");
            h.assertTrue(air.equals(RoomAirState.get(l).save(new CompoundTag(),l.registryAccess())),"Starvation preserves gas and pressure without a breach");
            var extra=s.c.above(2);l.setBlock(extra,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(extra)).onLoad();s.sources.add(extra);
            // This wall position held a paused furnace; remove it from the expected sleeper list.
            for(int t=520;t<=580;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(paused(l,List.of(furnaces.getFirst()))==0,"Remaining furnace recovers automatically with adequate supply");
            h.assertTrue(flames.stream().noneMatch(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))),"Restoring supply does not automatically relight ordinary flames");
            var player=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"starvation_relight"));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
            var hit=new net.minecraft.world.phys.BlockHitResult(torch.getCenter(),Direction.UP,torch,false);
            VacuumFlames.relight(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,torch,hit));
            h.assertTrue(l.getBlockState(torch).is(Blocks.TORCH),"Existing manual relight handler works after supply recovery");
        } finally {cows.forEach(Cow::discard);((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_oxygen",timeoutTicks=100)
    public static void roomOxygenFlamesStopAtSupportedDemandAndIgnoreEarlyOrUnknownAir(GameTestHelper h){scene(h,s->{
        var l=s.l;long clock=l.getGameTime();var cow=new Cow(EntityType.COW,l);
        try {
            l.setBlock(s.c,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);((GeothermalCoreBlockEntity)l.getBlockEntity(s.c)).onLoad();s.sources.add(s.c);
            var flames=List.of(s.c.west(2),s.c.west(2).south(),s.c.east(2),s.c.east(2).south(),s.c.west(2).north());
            l.setBlock(flames.get(0),Blocks.CAMPFIRE.defaultBlockState(),3);
            l.setBlock(flames.get(1),Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES,4).setValue(BlockStateProperties.LIT,true),3);
            l.setBlock(flames.get(2).below(),Blocks.NETHERRACK.defaultBlockState(),3);l.setBlock(flames.get(2),Blocks.FIRE.defaultBlockState(),3);
            l.setBlock(flames.get(3),Blocks.TORCH.defaultBlockState(),3);l.setBlock(flames.get(4),Blocks.LANTERN.defaultBlockState(),3);
            cow.setPos(s.c.west().north().above().getCenter());l.addFreshEntity(cow);
            RoomAtmosphere.view(l,s.c.west().above());RoomAtmosphere.view(l,s.c.east().above());for(int i=0;i<5;i++)RoomOxygenManager.tickLevel(l);
            ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());
            for(int t=0;t<=300;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);RoomOxygenManager.tickLevel(l);}
            h.assertTrue(flames.stream().filter(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))).count()==4,"One one-unit consumer sheds; supported flames stay lit");
            for(var r:RoomOxygenManager.snapshots(l))near(h,r.demand(),7.5,"Shedding stops when demand fits supply 8");
            ApocalypseState.get(l.getServer()).setApocalypseTicks(0,l.getServer());cow.setBaby(false);
            var second=new Cow(EntityType.COW,l);second.setPos(s.c.east().above().getCenter());l.addFreshEntity(second);
            try {
                for(int t=320;t<=600;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
                h.assertTrue(flames.stream().filter(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))).count()==4,"Early ambient air ignores scarcity despite high demand");
                ApocalypseState.get(l.getServer()).setApocalypseTicks((long)(ApocalypseState.get(l.getServer()).getTotalDays()*24000L*.90),l.getServer());RoomAtmosphere.reset();
                for(int t=620;t<=900;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomOxygenManager.tickLevel(l);}
                h.assertTrue(flames.stream().filter(p->VacuumFlames.isOrdinaryFlame(l.getBlockState(p))).count()==4,"Unknown room data cannot extinguish lights from stale budgets");
            } finally {second.discard();}
        } finally {cow.discard();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);}
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
