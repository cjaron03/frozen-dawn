package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerMonitorGameTest {
    @BeforeBatch(batch="power_monitor") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}
    @BeforeBatch(batch="power_monitor_live") public static void reportLive(ServerLevel l){GameTestReporting.installReporter(l);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_monitor",timeoutTicks=100)
    public static void powerMonitorReadingsShedAndPassiveConservation(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var core=place(h,2,3,2,ModBlocks.GEOTHERMAL_CORE.get());var bank=place(h,6,3,2,ModBlocks.POWER_RELAY.get());var p=monitor(h,6,3,6);
        var a=place(h,10,3,2,ModBlocks.INDUCTION_FURNACE.get());var b=place(h,10,3,4,ModBlocks.INDUCTION_FURNACE.get());var lamp=place(h,10,3,8,ModBlocks.PANEL_LIGHT.get());
        var source=(GeothermalCoreBlockEntity)l.getBlockEntity(core);
        ((InductionFurnaceBlockEntity)l.getBlockEntity(a)).setItem(0,new ItemStack(Items.RAW_IRON,64));((InductionFurnaceBlockEntity)l.getBlockEntity(b)).setItem(0,new ItemStack(Items.RAW_IRON,64));
        link(h,core,bank);link(h,bank,p);link(h,bank,a);link(h,a,b);link(h,b,lamp);
        source.setPowerShare(10);account(l);var r=PowerNetworks.monitorReading(l,p);
        h.assertTrue(r.supplyWatts()==2000&&r.demandWatts()==4100&&r.shed().size()==2&&r.shed().stream().anyMatch(s->s.kind()==2&&s.pos().equals(b))&&r.shed().stream().anyMatch(s->s.kind()==1&&s.pos().equals(lamp)),"2kW serves the first furnace and identifies the other furnace and lamp as shed");
        h.assertTrue(r.status()==PowerMonitorReading.BROWNOUT&&r.capacityJoules()==0,"Brownout without storage reports zero battery capacity");
        source.setPowerShare(20);account(l);h.assertTrue(PowerNetworks.monitorReading(l,p).shed().size()==1&&PowerNetworks.monitorReading(l,p).shed().getFirst().pos().equals(lamp),"4kW sheds only the lamp");
        source.setPowerShare(25);var storage=place(h,2,3,6,ModBlocks.BATTERY_BANK.get());var store=(PowerStorageBlockEntity)l.getBlockEntity(storage);store.setItem(0,PowerCellItem.charged(250000));link(h,bank,storage);account(l);int before=store.storedJoules();h.assertTrue(PowerNetworks.monitorReading(l,p).capacityJoules()==2000000,"Connected bank exposes four-slot rack capacity");
        var player=player(l,p);var menu=new PowerMonitorMenu(1,player.getInventory(),(PowerMonitorBlockEntity)l.getBlockEntity(p));
        for(int i=0;i<100;i++){PowerNetworks.monitorReading(l,p);menu.broadcastChanges();((PowerMonitorBlockEntity)l.getBlockEntity(p)).refresh();}
        h.assertTrue(store.storedJoules()==before&&menu.slots.isEmpty()&&menu.quickMoveStack(player,0).isEmpty(),"Reading and opening a slotless monitor never charges or discharges cells");
        h.assertTrue(PowerNetworks.monitorReading(l,p).status()==PowerMonitorReading.NORMAL&&PowerNetworks.monitorReading(l,p).shed().isEmpty(),"5kW serves all current loads");
        PowerNetworks.clear(l,p);account(l);h.assertTrue(!PowerNetworks.monitorReading(l,p).connected()&&PowerNetworks.monitorReading(l,p).status()==PowerMonitorReading.UNLINKED,"Cleared monitor cannot retain a foreign component reading");h.succeed();
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_monitor",timeoutTicks=100)
    public static void powerMonitorMenuWordsPagingAndSecurity(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var p=monitor(h,3,3,2);var relay=place(h,3,3,4,ModBlocks.POWER_RELAY.get());link(h,p,relay);
        for(int i=0;i<8;i++){var lamp=place(h,3+i,3,6,ModBlocks.PANEL_LIGHT.get());link(h,relay,lamp);}account(l);
        var player=player(l,p);var m=new PowerMonitorMenu(2,player.getInventory(),(PowerMonitorBlockEntity)l.getBlockEntity(p));
        h.assertTrue(m.data().get(13)==8&&m.data().get(15)==2&&m.data().get(16)==6,"Long shed lists use bounded six-row pages");var first=m.rowPos(0);
        h.assertTrue(m.clickMenuButton(player,1)&&m.data().get(16)==2&&!m.rowPos(0).equals(first)&&m.rowKind(2)==0,"Second page exposes remaining loads and clears unused rows");
        h.assertTrue(!m.clickMenuButton(player,99),"Unknown button refused");for(int i=0;i<10;i++)m.clickMenuButton(player,1);h.assertTrue(m.data().get(14)==1,"Page cannot go outside the real list");
        var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());buf.writeBlockPos(p);var client=new PowerMonitorMenu(2,player.getInventory(),buf);
        m.data().set(4,0x5e00);m.data().set(5,0xb2d0);m.data().set(6,0);m.data().set(7,0); // 3,000,000,000 J exceeds a signed int.
        int offset=PowerMonitorMenu.START+1,coordinate=-30000000;m.data().set(offset,coordinate&65535);m.data().set(offset+1,(coordinate>>>16)&65535);
        for(int i=0;i<PowerMonitorMenu.DATA_SIZE;i++){buf.clear();buf.writeShort(m.data().get(i));client.data().set(i,buf.readShort());}
        h.assertTrue(client.value(4,4)==3000000000L&&client.rowPos(0).getX()==coordinate,"Actual signed-short wire values retain long energy and negative world coordinates");buf.release();
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);h.assertTrue(!m.stillValid(player),"Spectator menu refused");player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(p.east(12).getCenter());h.assertTrue(!m.stillValid(player)&&!m.clickMenuButton(player,0),"Remote user cannot page an invalid menu");player.setPos(p.getCenter());l.destroyBlock(p,false);h.assertTrue(!m.stillValid(player),"Removed monitor invalidates stale menu");h.succeed();
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_monitor",timeoutTicks=100)
    public static void powerMonitorWallSupportSavedLinksAndDockIsolation(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var p=monitor(h,6,3,6);var dock=place(h,3,3,6,ModBlocks.CELL_DOCK.get());var core=place(h,3,3,2,ModBlocks.GEOTHERMAL_CORE.get());link(h,dock,p);
        h.assertTrue(PowerLinkRules.validate(l,p,core)==PowerLinkRules.Result.DOCK_CONNECTION,"Passive monitor cannot bridge a dock to infrastructure");
        var saved=PowerNetworkSavedData.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var restored=PowerNetworkSavedData.load(saved,l.registryAccess());h.assertTrue(restored.links().contains(new PowerLink(dock,p)),"Monitor Binder link survives SavedData serialization");
        for(var facing:Direction.Plane.HORIZONTAL){var q=h.absolutePos(new BlockPos(12,3,12));var state=ModBlocks.POWER_MONITOR.get().defaultBlockState().setValue(PowerMonitorBlock.FACING,facing);l.setBlock(q.relative(facing.getOpposite()),Blocks.STONE.defaultBlockState(),3);h.assertTrue(state.canSurvive(l,q),"Each horizontal wall attachment has valid support");}
        var absent=new BlockPos(1234567,200,1234567);h.assertTrue(PowerNetworks.monitorReading(l,absent)==PowerMonitorReading.EMPTY&&!l.isLoaded(absent),"Monitor queries never load absent chunks");
        l.destroyBlock(p.south(),false);h.runAfterDelay(2,()->{h.assertTrue(l.getBlockState(p).isAir()&&PowerNetworkSavedData.get(l).links().stream().noneMatch(link->link.touches(p)),"Lost wall support removes monitor and clears saved links");h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).stream().filter(e->e.getItem().is(ModItems.POWER_MONITOR)).mapToInt(e->e.getItem().getCount()).sum()==1,"Wall removal drops one monitor");h.succeed();});
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_monitor_live",timeoutTicks=230)
    public static void powerMonitorRealTickBatteryStatusAndComparator(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var core=place(h,2,3,2,ModBlocks.GEOTHERMAL_CORE.get());var bank=place(h,6,3,2,ModBlocks.BATTERY_BANK.get());var p=monitor(h,6,3,6);var f=place(h,10,3,2,ModBlocks.INDUCTION_FURNACE.get());var source=(GeothermalCoreBlockEntity)l.getBlockEntity(core);var store=(PowerStorageBlockEntity)l.getBlockEntity(bank);var monitor=(PowerMonitorBlockEntity)l.getBlockEntity(p);
        l.setBlock(p.north().below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(p.north(),Blocks.COMPARATOR.defaultBlockState().setValue(net.minecraft.world.level.block.ComparatorBlock.FACING,Direction.SOUTH),3);
        source.setPowerShare(25);store.setItem(0,PowerCellItem.charged(500000));((InductionFurnaceBlockEntity)l.getBlockEntity(f)).setItem(0,new ItemStack(Items.RAW_IRON,64));link(h,core,bank);link(h,bank,p);link(h,bank,f);
        h.runAfterDelay(25,()->{h.assertTrue(monitor.comparator()==4&&signal(l,p)==4,"One full cell gives quarter-rack charge and real comparator4");store.setItem(1,PowerCellItem.charged(500000));
            h.runAfterDelay(25,()->{h.assertTrue(monitor.comparator()==8&&signal(l,p)==8,"Two full cells give half-rack charge and comparator8");for(int i=0;i<4;i++)store.setItem(i,PowerCellItem.charged(500000));
                h.runAfterDelay(25,()->{h.assertTrue(monitor.comparator()==15&&signal(l,p)==15&&monitor.reading().status()==PowerMonitorReading.NORMAL&&monitor.reading().storedJoules()==2000000,"Normal generation keeps full cells and real full comparator signal");source.setPowerShare(0);
                    h.runAfterDelay(45,()->{h.assertTrue(monitor.reading().status()==PowerMonitorReading.BACKUP&&store.storedJoules()<2000000&&monitor.comparator()==14,"Ordinary ticks show battery backup, falling energy and comparator update: status="+monitor.reading().status()+", joules="+store.storedJoules()+", comparator="+monitor.comparator());for(int i=0;i<4;i++)store.setItem(i,ItemStack.EMPTY);
                        h.runAfterDelay(25,()->{h.assertTrue(monitor.reading().status()==PowerMonitorReading.NO_POWER&&monitor.reading().shed().size()==1&&monitor.comparator()==0&&signal(l,p)==0,"Empty bank turns off load and reports No power with real zero comparator");PowerNetworks.clear(l,p);
                            h.runAfterDelay(25,()->{h.assertTrue(monitor.getBlockState().getValue(PowerMonitorBlock.STATUS)==PowerMonitorReading.UNLINKED&&monitor.comparator()==0,"Unlinked wall status resets without saving fake grants");h.succeed();});});});});});});
    });}
    private static int signal(ServerLevel l,BlockPos p){return ((net.minecraft.world.level.block.entity.ComparatorBlockEntity)l.getBlockEntity(p.north())).getOutputSignal();}
    @GameTest(template=GameTestTemplates.EMPTY,batch="power_monitor",timeoutTicks=100)
    public static void powerMonitorReplayFunctionsParse(GameTestHelper h){for(var name:List.of("load","tick","setup","controls","basic","brownout","battery"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("power_monitor_check",name)).isPresent(),"Native parser loads monitor helper "+name);h.succeed();}
    private static FakePlayer player(ServerLevel l,BlockPos p){var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"monitor_user"));player.setPos(p.getCenter());return player;}
    private static BlockPos place(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){h.setBlock(new BlockPos(x,y,z),b);return h.absolutePos(new BlockPos(x,y,z));}
    private static BlockPos monitor(GameTestHelper h,int x,int y,int z){h.setBlock(new BlockPos(x,y,z+1),Blocks.STONE);return place(h,x,y,z,ModBlocks.POWER_MONITOR.get());}
    private static void link(GameTestHelper h,BlockPos a,BlockPos b){h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected monitor link");}
    private static void account(ServerLevel l){for(int i=0;i<260;i++)PowerNetworks.update(l);PowerNetworks.account(l);}
}
