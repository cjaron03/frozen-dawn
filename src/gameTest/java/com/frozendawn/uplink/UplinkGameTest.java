package com.frozendawn.uplink;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.power.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UplinkGameTest {
    @BeforeBatch(batch="uplink_budget") public static void budget(ServerLevel l){fresh(l);}
    @BeforeBatch(batch="uplink_abort") public static void abort(ServerLevel l){fresh(l);}
    @BeforeBatch(batch="uplink_complete") public static void complete(ServerLevel l){fresh(l);}
    @BeforeBatch(batch="uplink_rewards") public static void rewards(ServerLevel l){fresh(l);}
    private static void fresh(ServerLevel l){GameTestReporting.installReporter(l);UplinkSavedData.get(l.getServer()).clearForTests();}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="uplink_budget",timeoutTicks=200)
    public static void uplinkListenDrawDishRulesAndTransmitBudget(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var core=place(h,2,3,2,ModBlocks.GEOTHERMAL_CORE.get());var c=place(h,6,3,2,ModBlocks.UPLINK_CONSOLE.get());
        var console=(UplinkConsoleBlockEntity)l.getBlockEntity(c);var player=player(l,c);
        ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(10);link(h,core,c);
        h.assertTrue(console.press(player,1)&&console.demandWatts()==2000,"Listen requests 2 kW");account(l);
        h.assertTrue(PowerNetworks.powered(l,c)&&PowerNetworks.monitorReading(l,c).demandWatts()==2000,"A 2 kW core serves the receiver");
        h.assertTrue(console.press(player,2)&&console.state()==UplinkConsoleBlockEntity.State.ARMED&&console.demandWatts()==2000,"Transmit warns first and draws nothing extra while armed");
        h.assertTrue(console.press(player,4)&&console.state()==UplinkConsoleBlockEntity.State.IDLE&&last(console).endsWith("no_dish"),"No dish refuses: "+last(console));
        var far=place(h,6,3,12,ModBlocks.UPLINK_DISH.get());
        console.press(player,2);console.press(player,4);h.assertTrue(last(console).endsWith("no_dish"),"A dish beyond 8 blocks does not count");
        var dish=place(h,9,3,4,ModBlocks.UPLINK_DISH.get());h.setBlock(new BlockPos(9,5,4),Blocks.STONE);
        // Sky light settles a few ticks after a block changes.
        h.runAfterDelay(20,()->{
        console.press(player,2);console.press(player,4);h.assertTrue(last(console).endsWith("sky")&&console.dishStatus()==UplinkConsoleBlockEntity.DISH_NO_SKY,"Roofed dish refuses: "+last(console));
        h.setBlock(new BlockPos(9,5,4),Blocks.AIR);
        h.runAfterDelay(20,()->{
        console.press(player,2);console.press(player,4);
        var data=UplinkSavedData.get(l.getServer());
        h.assertTrue(last(console).endsWith("refuse.power")&&console.dish().equals(dish)&&data.nextTransmit()==0&&data.sites().isEmpty(),"2 kW cannot fund 40 kW; nothing is spent: "+last(console));
        h.assertTrue(!console.press(player,4)&&console.press(player,3)&&console.boost()&&console.press(player,5)&&console.state()==UplinkConsoleBlockEntity.State.IDLE,"Confirm needs an armed prompt; Boost arms; Cancel disarms");
        // Underpowered transmit is shed and identified on the power monitor.
        ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(5);account(l);
        h.assertTrue(PowerNetworks.monitorReading(l,c).shed().stream().anyMatch(s->s.kind()==8&&s.pos().equals(c)),"Monitor names the shed Uplink");
        h.assertTrue(PowerLinkRules.range(l.getBlockState(c))==16&&far!=null,"Console is a Binder node");
        console.press(player,0);h.assertTrue(console.demandWatts()==0,"Off draws nothing");h.succeed();
        });});
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="uplink_abort",timeoutTicks=600)
    public static void uplinkTransmitAbortsOnPowerLossButSignalIsHeard(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var bank=place(h,2,3,2,ModBlocks.BATTERY_BANK.get());var c=place(h,6,3,2,ModBlocks.UPLINK_CONSOLE.get());place(h,9,3,4,ModBlocks.UPLINK_DISH.get());
        var console=(UplinkConsoleBlockEntity)l.getBlockEntity(c);var player=player(l,c);var store=(PowerStorageBlockEntity)l.getBlockEntity(bank);
        store.setItem(0,PowerCellItem.charged(250000));link(h,bank,c);account(l);
        console.press(player,2);console.press(player,4);
        var data=UplinkSavedData.get(l.getServer());long now=l.getGameTime();
        h.assertTrue(console.state()==UplinkConsoleBlockEntity.State.TRANSMITTING&&console.demandWatts()==40000,"Battery funds the handshake start: "+last(console));
        h.assertTrue(data.nextTransmit()==now+UplinkPolicy.COOLDOWN_TICKS,"Cooldown is spent at start");
        var site=data.sites().getFirst();
        h.assertTrue(site.pressure(now)==0&&site.pressure(site.armed)>0.95*UplinkPolicy.STANDARD_SIGNAL&&site.armed==now+UplinkPolicy.SIGNAL_LAG_TICKS,"Pressure is lagged, then raised");
        h.assertTrue(UplinkEvents.spawnMultiplier(l,c)==1,"No pressure before the lag");
        h.succeedWhen(()->{
            h.assertTrue(console.state()==UplinkConsoleBlockEntity.State.IDLE,"Waiting for the cell to run dry: stored="+store.storedJoules());
            h.assertTrue(last(console).endsWith("abort.power"),"Aborted for power: "+last(console));
            h.assertTrue(data.archiveDelivered()==0&&data.sites().size()==1&&player.getInventory().isEmpty(),"No reward, signal still recorded");
        });
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="uplink_complete",timeoutTicks=400)
    public static void uplinkBoostCompletesArchiveFirstAcrossReloadThenCools(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var bank=place(h,2,3,2,ModBlocks.BATTERY_BANK.get());var c=place(h,6,3,2,ModBlocks.UPLINK_CONSOLE.get());place(h,9,3,4,ModBlocks.UPLINK_DISH.get());
        var console=(UplinkConsoleBlockEntity)l.getBlockEntity(c);var player=player(l,c);var store=(PowerStorageBlockEntity)l.getBlockEntity(bank);
        for(int i=0;i<4;i++)store.setItem(i,PowerCellItem.charged(500000));link(h,bank,c);account(l);
        console.press(player,1);console.press(player,3);console.press(player,4);
        h.assertTrue(console.state()==UplinkConsoleBlockEntity.State.TRANSMITTING&&console.demandWatts()==60000&&console.totalTicks()==UplinkPolicy.Mode.BOOST.ticks,"Boost: 60 kW for 90 s");
        // Persist and reload near the end; the in-flight handshake resumes and must stay funded.
        var tag=console.saveWithFullMetadata(l.registryAccess());tag.putInt("Elapsed",UplinkPolicy.Mode.BOOST.ticks-30);console.loadWithComponents(tag,l.registryAccess());
        h.succeedWhen(()->{
            h.assertTrue(console.state()==UplinkConsoleBlockEntity.State.IDLE&&console.log().stream().anyMatch(x->x.key().endsWith("log.complete")),"Waiting for completion: "+last(console));
            var data=UplinkSavedData.get(l.getServer());
            h.assertTrue(data.archiveDelivered()==1&&console.mode()==UplinkPolicy.Mode.LISTEN,"First reward is archive #1; console returns to Listen");
            console.deliverHeld(player);
            var doc=player.getInventory().items.stream().filter(s->s.is(ModItems.ORSA_DOCUMENT)).findFirst().orElse(null);
            h.assertTrue(doc!=null&&doc.get(DataComponents.CUSTOM_DATA).copyTag().getString("doc_type").equals("relay_archive_1"),"Printed relay transcript #1 reaches the transmitter");
            h.assertTrue(player.getInventory().items.stream().anyMatch(UplinkGameTest::helionPlot)&&console.log().stream().anyMatch(x->x.key().endsWith("log.helion")),"The fragment also plots a Helion facility");
            console.press(player,2);console.press(player,4);
            h.assertTrue(last(console).endsWith("refuse.cooldown"),"One transmission per in-game day: "+last(console));
        });
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="uplink_rewards",timeoutTicks=100)
    public static void uplinkRewardPlotsAndSavedDataRoundTrip(GameTestHelper h){
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(1,2,1));var data=UplinkSavedData.get(l.getServer());
        var drop=UplinkRewards.supplyDrop(l,origin,l.random);
        h.assertTrue(drop.item().is(Items.FILLED_MAP)&&drop.item().get(DataComponents.MAP_ID)!=null&&data.drops().stream().anyMatch(d->d.center==null),"Supply drop prints a locked plot and queues a drop");
        var target=data.drops().getLast().target;double d=Math.sqrt(Math.pow(target.getX()-origin.getX(),2)+Math.pow(target.getZ()-origin.getZ(),2));
        h.assertTrue(d>=UplinkPolicy.DROP_MIN_DISTANCE-1&&d<=UplinkPolicy.DROP_MAX_DISTANCE+1,"Drop 150-300 blocks out: "+d);
        data.signal("minecraft:overworld",origin,10,true);
        var saved=data.save(new CompoundTag(),l.registryAccess());
        h.assertTrue(saved.getList("sites",10).size()==1&&saved.getList("drops",10).size()>=1,"Sites and drops persist");
        for(int i=0;i<3;i++){var doc=UplinkRewards.archive(i);h.assertTrue(doc.is(ModItems.ORSA_DOCUMENT)&&doc.get(DataComponents.CUSTOM_DATA).copyTag().getString("doc_type").equals(UplinkRewards.docType(i)),"Fragment "+(i+1)+" prints as a relay transcript");
            h.assertTrue(l.getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"relay_archive/"+(i+1)))!=null,"Field Manual unlock exists for fragment "+(i+1));}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="uplink_rewards",timeoutTicks=100)
    public static void uplinkFragmentRevealsNearestUnlootedHelionSite(GameTestHelper h){
        var l=h.getLevel();var data=UplinkSavedData.get(l.getServer());var helion=com.frozendawn.data.HelionSiteState.get(l.getServer());
        // Far from every other test, so only these halls are in reach.
        var console=new BlockPos(40000,64,40000);var looted=console.offset(200,-30,0);var standing=console.offset(0,-30,300);
        helion.markBuilt(looted.getX()>>4,looted.getZ()>>4,looted);helion.markLooted(looted);helion.markBuilt(standing.getX()>>4,standing.getZ()>>4,standing);
        var first=UplinkRewards.helionReveal(l,console);
        h.assertTrue(first!=null&&helionPlot(first.plot())&&first.chunkX()==standing.getX()>>4&&first.chunkZ()==standing.getZ()>>4,"Nearest unlooted hall is plotted, the looted one skipped");
        h.assertTrue(data.promised(standing.getX()>>4,standing.getZ()>>4)&&!data.promised(looted.getX()>>4,looted.getZ()>>4),"The plotted hall is promised");
        var second=UplinkRewards.helionReveal(l,console);
        h.assertTrue(second!=null&&(second.chunkX()!=first.chunkX()||second.chunkZ()!=first.chunkZ()),"The next fragment reveals another site");
        var expected=com.frozendawn.world.HelionSitePlacement.siteChunk(l.getSeed(),Math.floorDiv(second.chunkX(),com.frozendawn.world.HelionSitePlacement.REGION_SIZE),Math.floorDiv(second.chunkZ(),com.frozendawn.world.HelionSitePlacement.REGION_SIZE));
        h.assertTrue(expected!=null&&expected[0]==second.chunkX()&&expected[1]==second.chunkZ()&&data.promised(second.chunkX(),second.chunkZ()),"An unbuilt candidate is promised so it stands when found");
        h.succeed();
    }
    private static boolean helionPlot(net.minecraft.world.item.ItemStack s){
        return s.is(Items.FILLED_MAP)&&s.get(DataComponents.ITEM_NAME) instanceof net.minecraft.network.chat.Component n&&n.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("item.frozendawn.uplink_helion_map");
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="uplink_rewards",timeoutTicks=100)
    public static void uplinkArmedPromptSchedulesLoadFollowRamp(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var core=place(h,2,3,2,ModBlocks.GEOTHERMAL_CORE.get());var c=place(h,6,3,2,ModBlocks.UPLINK_CONSOLE.get());
        var console=(UplinkConsoleBlockEntity)l.getBlockEntity(c);var player=player(l,c);
        ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(10);link(h,core,c);
        console.press(player,1);account(l);
        int idle=PowerNetworks.loadFollowTargetWatts(l,core,2000);
        h.assertTrue(idle==2000,"Listening asks load-follow for the receiver only: "+idle);
        console.press(player,2);
        h.assertTrue(console.scheduledWatts()==38000&&PowerNetworks.loadFollowTargetWatts(l,core,2000)==40000,"Armed transmit schedules its extra 38 kW for load-follow");
        h.assertTrue(PowerNetworks.monitorReading(l,c).demandWatts()==2000&&console.demandWatts()==2000,"Scheduling serves and charges nothing");
        h.assertTrue(console.availableWatts()==2000,"Console sees 2 kW available, short of the 40 kW it needs");
        console.press(player,3);h.assertTrue(PowerNetworks.loadFollowTargetWatts(l,core,2000)==60000,"Boost schedules its 60 kW");
        console.press(player,5);h.assertTrue(console.scheduledWatts()==0&&PowerNetworks.loadFollowTargetWatts(l,core,2000)==2000,"Cancel releases the scheduled load");
        h.succeed();
    });}

    private static String last(UplinkConsoleBlockEntity c){var log=c.log();return log.isEmpty()?"":log.getLast().key();}
    private static FakePlayer player(ServerLevel l,BlockPos p){var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"uplink_user"));player.setPos(p.getCenter());return player;}
    private static BlockPos place(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){h.setBlock(new BlockPos(x,y,z),b);return h.absolutePos(new BlockPos(x,y,z));}
    private static void link(GameTestHelper h,BlockPos a,BlockPos b){h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected Uplink link");}
    private static void account(ServerLevel l){for(int i=0;i<260;i++)PowerNetworks.update(l);PowerNetworks.account(l);}
}
