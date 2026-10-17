package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.thermal.RoomHeatMath;
import com.frozendawn.world.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcheronForgeGameTest {
    @BeforeBatch(batch="acheron") public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch="acheron_live") public static void reportLive(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch="acheron_heat") public static void reportHeat(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron",timeoutTicks=100)
    public static void acheronFullPowerCombinedSourcesAndMenuSecurity(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=placeAtYZero(h,6,4,ModBlocks.ACHERON_FORGE.get());var f=forge(l,p);input(f,ModItems.ACHERONITE_SHARD.get(),4);
            var a=placeAtYZero(h,2,4,ModBlocks.GEOTHERMAL_CORE.get());core(l,a).setPowerShare(100);link(h,a,p);account(l);f.serverTick();
            h.assertTrue(p.getY()>=0&&f.progress()==0,"One 20 kW core cannot partially operate a surface forge");
            h.assertTrue(PowerNetworks.monitorReading(l,p).shed().stream().anyMatch(s->s.kind()==7&&s.pos().equals(p)),"Monitor names the waiting forge as its own load");
            var b=placeAtYZero(h,10,4,ModBlocks.GEOTHERMAL_CORE.get());core(l,b).setPowerShare(25);link(h,b,p);account(l);f.serverTick();
            h.assertTrue(f.progress()==3&&f.status()==1&&PowerNetworks.reading(l,p).demandKw()==25,"Combined geothermal sources pay the full 25 kW at any height");
            var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"acheron_menu"));player.setPos(p.getCenter());
            var menu=new AcheronForgeMenu(1,player.getInventory(),f);
            h.assertTrue(menu.clickMenuButton(player,2)&&f.mode()==AcheronForgeBlockEntity.Mode.HEAT&&!f.wantsPower(),"Heat selection draws no electricity");
            h.assertTrue(!menu.clickMenuButton(player,3)&&!menu.clickMenuButton(player,-1),"Forged mode IDs are rejected");
            player.setPos(p.east(12).getCenter());h.assertTrue(!menu.clickMenuButton(player,1),"Remote mode changes are rejected");
            player.setPos(p.getCenter());l.removeBlock(p,false);h.assertTrue(!menu.stillValid(player)&&!menu.clickMenuButton(player,1),"Removed forge rejects stale menu actions");l.removeBlock(a,false);l.removeBlock(b,false);h.succeed();
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron",timeoutTicks=100)
    public static void acheronBinderRoutesBothHandsWithoutOpeningMenu(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=place(h,4,3,4,ModBlocks.ACHERON_FORGE.get());var relay=place(h,8,3,4,ModBlocks.POWER_RELAY.get());
            var profile=new GameProfile(UUID.randomUUID(),"acheron_binder");
            var player=new net.minecraft.server.level.ServerPlayer(l.getServer(),l,profile,net.minecraft.server.level.ClientInformation.createDefault());
            // Keep real ServerPlayer menu behavior with NeoForge's expected channel attributes.
            var channel=new io.netty.channel.embedded.EmbeddedChannel();
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                @Override public io.netty.channel.Channel channel() {return channel;}
            };
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(l.getServer(),
                    connection,player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
                @Override public void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {}
            };
            player.setPos(p.south().getCenter());
            try {
            for(var hand:net.minecraft.world.InteractionHand.values()) {
                var binder=new ItemStack(ModItems.CONDUIT_BINDER.get());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
                player.setItemInHand(hand,binder);
                var hit=new net.minecraft.world.phys.BlockHitResult(p.getCenter(),net.minecraft.core.Direction.SOUTH,p,false);
                if(hand==net.minecraft.world.InteractionHand.OFF_HAND) {
                    var emptyResult=player.gameMode.useItemOn(player,l,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit);
                    h.assertTrue(!emptyResult.consumesAction()&&player.containerMenu==player.inventoryMenu,"Main-hand fallback leaves the offhand Binder its click");
                }
                player.gameMode.useItemOn(player,l,binder,hand,hit);
                h.assertTrue(net.minecraft.core.GlobalPos.of(l.dimension(),p).equals(binder.get(ModDataComponents.CONDUIT_ANCHOR))
                        &&player.containerMenu==player.inventoryMenu,"Binder selects the forge without opening its menu: "+hand);
                player.gameMode.useItemOn(player,l,binder,hand,new net.minecraft.world.phys.BlockHitResult(relay.getCenter(),net.minecraft.core.Direction.UP,relay,false));
                h.assertTrue(PowerNetworkSavedData.get(l).links().contains(new PowerLink(p,relay))&&!binder.has(ModDataComponents.CONDUIT_ANCHOR),"Second click creates the forge link: "+hand);
                player.setShiftKeyDown(true);player.gameMode.useItemOn(player,l,binder,hand,hit);player.setShiftKeyDown(false);
                h.assertTrue(PowerNetworkSavedData.get(l).degree(p)==0&&player.containerMenu==player.inventoryMenu,"Shift-use clears the link without opening the menu: "+hand);
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.gameMode.useItemOn(player,l,ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(p.getCenter(),net.minecraft.core.Direction.SOUTH,p,false));
            h.assertTrue(player.containerMenu instanceof AcheronForgeMenu,"Ordinary empty-hand click still opens the forge");
            player.closeContainer();h.succeed();
            } finally {channel.finishAndReleaseAll();}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron_live",timeoutTicks=180)
    public static void acheronElectricSpeedBothRecipesAndExactBatteryPayment(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=place(h,4,3,4,ModBlocks.ACHERON_FORGE.get());var q=place(h,12,3,4,ModBlocks.ACHERON_FORGE.get());
            var f=forge(l,p);var g=forge(l,q);input(f,ModItems.ACHERONITE_SHARD.get(),2);input(g,ModItems.SPENT_LATTICE.get(),4);
            var a=place(h,4,3,8,ModBlocks.CELL_DOCK.get());var b=place(h,12,3,8,ModBlocks.CELL_DOCK.get());
            var sa=store(l,a);var sb=store(l,b);sa.setItem(0,PowerCellItem.charged(500000));sb.setItem(0,PowerCellItem.charged(500000));link(h,a,p);link(h,b,q);account(l);
            f.serverTick();g.serverTick();f.serverTick();
            h.assertTrue(f.progress()==3&&sa.storedJoules()==498750,"Only one 1,250 J processing tick can be charged per server tick");
            h.runAfterDelay(132, () -> {
                f.serverTick();g.serverTick();
                h.assertTrue(f.progress()==399&&g.progress()==399&&f.getItems().get(1).isEmpty(),"133 electric ticks remain just below one complete operation");
            });
            h.runAfterDelay(133, () -> {
                f.serverTick();g.serverTick();
                h.assertTrue(f.getItems().get(1).is(ModItems.REFINED_ACHERONITE.get())&&g.getItems().get(1).is(ModItems.SEALED_LATTICE.get()),"Both recipes finish after 134 paid ticks instead of 200");
                h.assertTrue(f.getItems().get(0).isEmpty()&&g.getItems().get(0).isEmpty()&&sa.storedJoules()==332500&&sb.storedJoules()==332500,"Inputs and exactly 167.5 kJ per operation are conserved");
                h.runAfterDelay(22, () -> {h.assertTrue(PowerNetworks.reading(l,p).demandKw()==0&&sa.storedJoules()==332500,"Finished forge draws zero idle power");h.succeed();});
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron",timeoutTicks=100)
    public static void acheronOutageDisconnectAndBlockedOutputPreserveWork(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=place(h,6,3,4,ModBlocks.ACHERON_FORGE.get());var f=forge(l,p);input(f,ModItems.ACHERONITE_SHARD.get(),4);
            var a=place(h,2,3,4,ModBlocks.BATTERY_BANK.get());var bank=store(l,a);bank.setItem(0,PowerCellItem.charged(500000));link(h,a,p);account(l);f.serverTick();
            var cell=bank.removeItem(0,1);int progress=f.progress();
            h.runAfterDelay(2, () -> {
                f.serverTick();h.assertTrue(f.progress()==progress&&f.status()==3,"Removing the charged cell pauses without losing progress");bank.setItem(0,cell);
                h.runAfterDelay(1, () -> {
                    account(l);f.serverTick();h.assertTrue(f.progress()==progress+3,"Replacing the same cell resumes the operation");PowerNetworks.clear(l,p);
                    h.runAfterDelay(1, () -> {
                        int paused=f.progress(),energy=bank.storedJoules();f.serverTick();
                        h.assertTrue(f.progress()==paused&&bank.storedJoules()==energy,"Disconnected forge cannot spend a stale grant");link(h,a,p);
                        f.getItems().set(1,new ItemStack(ModItems.REFINED_ACHERONITE.get(),64));
                        h.runAfterDelay(1, () -> {account(l);f.serverTick();h.assertTrue(!f.wantsPower()&&f.status()==6&&f.progress()==paused&&bank.storedJoules()==energy,"Full output pauses work and spends no energy");h.succeed();});
                    });
                });
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron",timeoutTicks=100)
    public static void acheronModesPersistAndLegacyProgressMigrates(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=place(h,6,3,4,ModBlocks.ACHERON_FORGE.get());var f=forge(l,p);input(f,ModItems.SPENT_LATTICE.get(),8);
            var legacy=f.saveWithFullMetadata(l.registryAccess());legacy.remove("ProgressUnits");legacy.remove("ActiveRecipe");legacy.remove("Mode");legacy.putInt("Progress",73);
            f.loadWithComponents(legacy,l.registryAccess());h.assertTrue(f.progress()==146&&f.mode()==AcheronForgeBlockEntity.Mode.AUTO,"Old saves retain 73/200 completed work and gain Auto mode");
            for(var mode:AcheronForgeBlockEntity.Mode.values()) {
                f.setMode(mode);var restored=new AcheronForgeBlockEntity(p,f.getBlockState());restored.loadWithComponents(f.saveWithFullMetadata(l.registryAccess()),l.registryAccess());
                h.assertTrue(restored.progress()==146&&restored.mode()==mode&&restored.getItems().get(0).getCount()==8,"Mode, progress and inventory survive serialization: "+mode);
            }
            f.setMode(AcheronForgeBlockEntity.Mode.ELECTRIC);f.serverTick();h.assertTrue(f.progress()==146,"No power preserves migrated progress");
            input(f,ModItems.ACHERONITE_SHARD.get(),2);
            h.runAfterDelay(1, () -> {f.serverTick();h.assertTrue(f.progress()==0,"Changing recipe cannot transfer partial lattice progress to Acheronite");h.succeed();});
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron",timeoutTicks=100)
    public static void acheronAutoFallsBackAndHeatModeConservesPower(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var p=h.absolutePos(new BlockPos(6,3,4)).atY(-16);var a=p.west(4);var b=p.east(4);
            l.setBlock(p,ModBlocks.ACHERON_FORGE.get().defaultBlockState(),3);l.setBlock(a,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);l.setBlock(b,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            l.getBlockEntity(p).onLoad();l.getBlockEntity(a).onLoad();l.getBlockEntity(b).onLoad();
            var f=forge(l,p);input(f,ModItems.ACHERONITE_SHARD.get(),4);core(l,a).setPowerShare(100);core(l,b).setPowerShare(25);link(h,a,p);account(l);f.serverTick();
            h.assertTrue(f.progress()==2&&f.status()==2,"Auto falls back to nearby heat below Y=0 when only 20 kW is available");f.setMode(AcheronForgeBlockEntity.Mode.ELECTRIC);
            h.runAfterDelay(1, () -> {
                f.serverTick();h.assertTrue(f.progress()==2&&f.status()==3,"Electric mode does not silently fall back to heat");link(h,b,p);f.setMode(AcheronForgeBlockEntity.Mode.AUTO);account(l);f.serverTick();
                h.assertTrue(f.progress()==5&&f.status()==1,"Auto prefers full electric power without resetting thermal progress");f.setMode(AcheronForgeBlockEntity.Mode.HEAT);
                h.runAfterDelay(1, () -> {
                    account(l);f.serverTick();h.assertTrue(f.progress()==7&&f.status()==2&&PowerNetworks.reading(l,p).demandKw()==0,"Heat mode retains work and declines available electricity");
                    l.removeBlock(p,false);l.removeBlock(a,false);l.removeBlock(b,false);h.succeed();
                });
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="acheron_heat",timeoutTicks=100)
    public static void acheronVacuumWasteHeatOnlyForPaidWork(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l=h.getLevel();var apocalypse=ApocalypseState.get(l.getServer());long previous=apocalypse.getApocalypseTicks();
            try {
                apocalypse.setApocalypseTicks((long)(apocalypse.getTotalDays()*24000L*.90),l.getServer());RoomAtmosphere.reset();RoomThermalManager.reset();
                for(int x=2;x<=7;x++)for(int y=1;y<=6;y++)for(int z=2;z<=8;z++)h.setBlock(new BlockPos(x,y,z),x==2||x==7||y==1||y==6||z==2||z==8?Blocks.GLASS:Blocks.AIR);
                var p=place(h,7,3,5,ModBlocks.ACHERON_FORGE.get());var a=place(h,4,3,5,ModBlocks.CELL_DOCK.get());var f=forge(l,p);var dock=store(l,a);dock.setItem(0,PowerCellItem.charged(500000));input(f,ModItems.ACHERONITE_SHARD.get(),2);
                var room=RoomAtmosphere.view(l,h.absolutePos(new BlockPos(4,3,4)));h.assertTrue(room!=null,"Known sealed thermal room");RoomAirState.get(l).evacuate(room.geometry().cells());link(h,a,p);account(l);
                double before=RoomThermalState.get(l).ledger.passive;f.serverTick();f.serverTick();RoomThermalManager.tickLevel(l);
                double heat=RoomThermalState.get(l).ledger.passive-before;
                h.assertTrue(f.progress()==3&&Math.abs(heat-312.5*RoomHeatMath.VACUUM_HEATER_EFFICIENCY)<1e-6,"One paid electric tick delivers exactly 6.25 kW of raw room heat at vacuum efficiency: "+heat);
                h.assertTrue(dock.storedJoules()==498750,"Room heat and progress are both backed by one actual 1,250 J payment");h.succeed();
            } finally {apocalypse.setApocalypseTicks(previous,l.getServer());}
        });
    }

    private static void input(AcheronForgeBlockEntity f,Item item,int count) { f.getItems().set(0,new ItemStack(item,count));f.setChanged(); }
    private static AcheronForgeBlockEntity forge(ServerLevel l,BlockPos p) { return (AcheronForgeBlockEntity)l.getBlockEntity(p); }
    private static GeothermalCoreBlockEntity core(ServerLevel l,BlockPos p) { return (GeothermalCoreBlockEntity)l.getBlockEntity(p); }
    private static PowerStorageBlockEntity store(ServerLevel l,BlockPos p) { return (PowerStorageBlockEntity)l.getBlockEntity(p); }
    private static BlockPos place(GameTestHelper h,int x,int y,int z,Block block) { var p=new BlockPos(x,y,z);h.setBlock(p,block);return h.absolutePos(p); }
    private static BlockPos placeAtYZero(GameTestHelper h,int x,int z,Block block) { var p=h.absolutePos(new BlockPos(x,3,z)).atY(0);h.getLevel().setBlock(p,block.defaultBlockState(),3);h.getLevel().getBlockEntity(p).onLoad();return p; }
    private static void link(GameTestHelper h,BlockPos a,BlockPos b) { h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected forge power link"); }
    private static void account(ServerLevel l) { for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l); }
}
