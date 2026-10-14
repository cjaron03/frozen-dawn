package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerStorageGameTest {
    @BeforeBatch(batch="power_storage") public static void report(ServerLevel l) { GameTestReporting.installReporter(l); }
    @BeforeBatch(batch="power_storage_cadence") public static void reportCadence(ServerLevel l) { GameTestReporting.installReporter(l); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_storage",timeoutTicks=100)
    public static void powerCellsInventoryPersistenceAndBreakPreserveCharge(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=place(h,2,2,2,ModBlocks.BATTERY_BANK.get());var bank=store(l,p);
            h.assertTrue(bank.getContainerSize()==4&&bank.capacityJoules()==2_000_000,"Bank holds four 500 kJ cells");
            bank.setItem(0,new ItemStack(Items.DIAMOND));h.assertTrue(bank.isEmpty(),"Unrelated items cannot enter storage");
            for(int i=0;i<4;i++)bank.setItem(i,PowerCellItem.charged(i*1000));
            h.assertTrue(bank.charge(3_000_000)==1_994_000&&bank.storedJoules()==2_000_000,"Charge clamps at installed cell capacity without overflow");
            h.assertTrue(bank.charge(1)==0&&bank.draw(2_000_001)==2_000_000&&bank.draw(1)==0,"Full and empty limits conserve joules");
            bank.setItem(0,PowerCellItem.charged(123456));
            var saved=bank.saveWithFullMetadata(l.registryAccess());var restored=new PowerStorageBlockEntity(p,bank.getBlockState());restored.loadWithComponents(saved,l.registryAccess());
            h.assertTrue(restored.storedJoules()==123456&&PowerCellItem.energy(restored.getItem(0))==123456,"Item charge and inventory serialize together");
            var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"power_cells"));player.setPos(p.east().getCenter());
            var menu=new PowerStorageMenu(1,player.getInventory(),bank,bank.data(),p);
            h.assertTrue(menu.stillValid(player)&&!menu.quickMoveStack(player,0).isEmpty()&&bank.storedJoules()==0,"Shift extraction carries the charged physical cell");
            int slot=-1;for(int i=0;i<36;i++)if(PowerCellItem.energy(player.getInventory().getItem(i))==123456)slot=i;
            h.assertTrue(slot>=0,"Charge survives move to player inventory");
            int menuSlot=4+(slot<9?27+slot:slot-9);menu.quickMoveStack(player,menuSlot);
            h.assertTrue(bank.storedJoules()==123456,"Shift insertion preserves charge");
            player.setPos(p.east(12).getCenter());h.assertTrue(!menu.stillValid(player),"Remote menu is invalid");
            int before=l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).size();l.destroyBlock(p,false);
            var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(ModItems.POWER_CELL)).mapToInt(e->PowerCellItem.energy(e.getItem())).sum()==123456&&drops.size()>=before+4,"Breaking storage releases its four cells with exact remaining charge");
            player.setPos(p.getCenter());h.assertTrue(!menu.stillValid(player),"Removed storage invalidates a stale menu");h.succeed();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_storage",timeoutTicks=100)
    public static void powerBankSurplusDeficitCyclesAndDuplicateAccounting(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var core=place(h,2,2,4,ModBlocks.GEOTHERMAL_CORE.get());var p=place(h,6,2,4,ModBlocks.BATTERY_BANK.get());
            var q=place(h,6,2,8,ModBlocks.BATTERY_BANK.get());var lamp=place(h,10,2,4,ModBlocks.PANEL_LIGHT.get());
            var bank=store(l,p);var second=store(l,q);bank.setItem(0,PowerCellItem.charged(0));second.setItem(0,PowerCellItem.charged(0));
            ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(1);
            link(h,core,p);link(h,p,q);link(h,q,lamp);link(h,lamp,p);account(l);
            int total=bank.storedJoules()+second.storedJoules();
            h.assertTrue(total==100&&lit(l,lamp),"200 W generation powers 100 W lamp and stores only 100 J surplus across cyclic banks");
            account(l);h.assertTrue(bank.storedJoules()+second.storedJoules()==total,"Repeated same-tick accounting cannot charge twice");
            ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(0);
            h.runAfterDelay(1,()->{
                account(l);h.assertTrue(lit(l,lamp)&&bank.storedJoules()+second.storedJoules()==0,"Bank supplies exactly one second of 100 W shortage");
                account(l);h.assertTrue(bank.storedJoules()+second.storedJoules()==0,"Repeated account cannot draw twice");
                h.runAfterDelay(1,()->{account(l);h.assertTrue(!lit(l,lamp),"Empty cells cause ordinary whole-load shedding");
                    h.assertTrue(((GeothermalCoreBlockEntity)l.getBlockEntity(core)).getPowerShare()==0,"Storage never reallocates Core output");h.succeed();});
            });
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_storage",timeoutTicks=100)
    public static void powerDockIsolatedSwapAndNoFreeRecharge(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=place(h,2,2,3,ModBlocks.CELL_DOCK.get());var lamp=place(h,6,2,3,ModBlocks.PANEL_LIGHT.get());
            var relay=place(h,9,2,3,ModBlocks.POWER_RELAY.get());var dock=store(l,p);dock.setItem(0,PowerCellItem.charged(200));
            h.assertTrue(dock.getContainerSize()==1&&dock.capacityJoules()==500000,"Dock has exactly one cell");
            link(h,p,lamp);h.assertTrue(PowerNetworks.link(l,p,relay)==PowerLinkRules.Result.DOCK_CONNECTION,"Dock cannot join a pylon");
            h.assertTrue(PowerNetworks.link(l,lamp,relay)==PowerLinkRules.Result.DOCK_CONNECTION,"A lamp cannot bridge an isolated dock into infrastructure");
            account(l);h.assertTrue(lit(l,lamp)&&dock.storedJoules()==100,"Dock pays the lamp's 100 J interval");
            h.assertTrue(Math.abs(PowerNetworks.reading(l,lamp).storedKj()-.1)<1e-6,"Binder reports actual remaining storage");
            var cell=dock.removeItem(0,1);h.assertTrue(PowerCellItem.energy(cell)==100,"Removing a cell carries remaining energy");
            h.runAfterDelay(1,()->{account(l);h.assertTrue(!lit(l,lamp),"Empty dock cannot power a lamp");dock.setItem(0,cell);
                h.runAfterDelay(1,()->{account(l);h.assertTrue(lit(l,lamp)&&dock.storedJoules()==0,"Swapping a charged cell restores delivery without rebinding");
                    h.runAfterDelay(1,()->{account(l);h.assertTrue(!lit(l,lamp)&&dock.storedJoules()==0,"Depleted dock stays dark and cannot self-charge");h.succeed();});});});
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_storage_cadence",timeoutTicks=100)
    public static void powerStorageRunsOnRealTicksAndDisconnectedBankStopsCharging(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var core=place(h,2,2,2,ModBlocks.GEOTHERMAL_CORE.get());var p=place(h,6,2,2,ModBlocks.BATTERY_BANK.get());
            var lamp=place(h,10,2,2,ModBlocks.PANEL_LIGHT.get());var bank=store(l,p);bank.setItem(0,PowerCellItem.charged(0));
            ((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(25);link(h,core,p);link(h,p,lamp);
            h.runAfterDelay(25,()->{int energy=bank.storedJoules();h.assertTrue(energy>=4900&&energy<=9800&&energy%4900==0&&lit(l,lamp),"Real 20-tick cadence charges exactly the surplus");
                PowerNetworks.clear(l,core);
                h.runAfterDelay(25,()->{h.assertTrue(bank.storedJoules()<energy&&lit(l,lamp),"Disconnected bank discharges to its surviving lamp");
                    PowerNetworks.clear(l,p);int retained=bank.storedJoules();
                    h.runAfterDelay(25,()->{h.assertTrue(bank.storedJoules()==retained&&!lit(l,lamp),"Fully isolated bank neither leaks nor charges");
                        var absent=new BlockPos(1234567,200,1234567);PowerNetworks.reading(l,absent);h.assertTrue(!l.isLoaded(absent),"Storage never forces chunks");h.succeed();});});});
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="power_storage",timeoutTicks=100)
    public static void powerStorageReplayFunctionsParse(GameTestHelper h) {
        for(var name:List.of("load","tick","setup","controls","charge","outpost","backup"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("power_storage_check",name)).isPresent(),"Native parser loads storage helper "+name);
        h.succeed();
    }
    private static PowerStorageBlockEntity store(ServerLevel l,BlockPos p){return (PowerStorageBlockEntity)l.getBlockEntity(p);}
    private static BlockPos place(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){var p=new BlockPos(x,y,z);h.setBlock(p,b);return h.absolutePos(p);}
    private static void link(GameTestHelper h,BlockPos a,BlockPos b){h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected storage link");}
    private static void account(ServerLevel l){for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l);}
    private static boolean lit(ServerLevel l,BlockPos p){return l.getBlockState(p).getValue(PanelLightBlock.LIT);}
}
