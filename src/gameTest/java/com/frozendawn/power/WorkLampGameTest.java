package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.world.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkLampGameTest {
    @BeforeBatch(batch="work_lamp") public static void report(ServerLevel l) { GameTestReporting.installReporter(l); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="work_lamp",timeoutTicks=100)
    public static void workLampPlacesOnWallsAndCeilingAndLosesSupport(GameTestHelper h) {
        var l=h.getLevel();var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"lamp_mount"));
        var support=h.absolutePos(new BlockPos(5,5,5));
        player.setPos(support.south(3).getCenter());
        for(var face:Direction.values()) {
            l.setBlock(support,Blocks.STONE.defaultBlockState(),3);var pos=support.relative(face);
            l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            var item=new ItemStack(ModItems.WORK_LAMP.get());
            var hit=new BlockHitResult(support.getCenter().relative(face,.5),face,support,false);
            var context=new BlockPlaceContext(l,player,InteractionHand.MAIN_HAND,item,hit);
            var result=((BlockItem)item.getItem()).place(context);
            if(face==Direction.UP) {h.assertTrue(!result.consumesAction()&&l.isEmptyBlock(pos),"Work lamp rejects a floor mount");continue;}
            var state=l.getBlockState(pos);
            h.assertTrue(result.consumesAction()&&state.is(ModBlocks.WORK_LAMP)&&state.getValue(WorkLampBlock.FACING)==face,"Real item attaches to "+face);
            var foot=support.getCenter().relative(face,9.0/16).subtract(pos.getX(),pos.getY(),pos.getZ());
            h.assertTrue(state.getShape(l,pos).toAabbs().stream().anyMatch(b->b.contains(foot)),"Selection base touches its mounting face "+face);
            var saved=state.setValue(WorkLampBlock.ENABLED,false).setValue(WorkLampBlock.LIT,true);
            h.assertTrue(NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),NbtUtils.writeBlockState(saved)).equals(saved),"Mount, switch and light survive palette serialization");
            var relay=pos.south(4);l.setBlock(relay,ModBlocks.POWER_RELAY.get().defaultBlockState(),3);link(h,pos,relay);
            l.removeBlock(support,false);
            h.assertTrue(l.isEmptyBlock(pos)&&PowerNetworkSavedData.get(l).degree(pos)==0,"Removing support drops the fixture and removes incident links");
            l.removeBlock(relay,false);
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="work_lamp",timeoutTicks=100)
    public static void workLampDockPaysExactlyAndRecoversAfterDepletion(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=lamp(h,6,3,4);var dock=h.absolutePos(new BlockPos(2,3,4));
            l.setBlock(dock,ModBlocks.CELL_DOCK.get().defaultBlockState(),3);
            var store=(PowerStorageBlockEntity)l.getBlockEntity(dock);store.setItem(0,PowerCellItem.charged(49));link(h,dock,p);account(l);
            h.assertTrue(!lit(l,p)&&store.storedJoules()==49,"49 J cannot fund a full 50 W interval or be partially consumed");
            h.assertTrue(PowerNetworks.monitorReading(l,p).shed().stream().anyMatch(s->s.kind()==10&&s.pos().equals(p)),"Monitor identifies a shed Work Lamp");
            h.runAfterDelay(1,()->{
                store.setItem(0,PowerCellItem.charged(100));account(l);
                h.assertTrue(lit(l,p)&&store.storedJoules()==50&&l.getBlockState(p).getLightEmission(l,p)==12,"50 W costs exactly 50 J and emits light 12");
                var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"lamp_flow"));
                var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeBlockPos(dock);buf.writeBoolean(false);
                var clientMenu=new PowerStorageMenu(1,player.getInventory(),buf);
                for(int i=0;i<PowerStorageMenu.DATA_SIZE;i++){buf.clear();buf.writeShort(store.data().get(i));clientMenu.data().set(i,buf.readShort());}
                h.assertTrue(clientMenu.feedingWatts()==50&&clientMenu.chargingWatts()==0,"Real signed-short menu values preserve 50 W instead of displaying Idle");buf.release();
                account(l);h.assertTrue(store.storedJoules()==50,"Repeated same-tick accounting cannot charge twice");
                var saved=PowerNetworkSavedData.get(l);
                h.assertTrue(PowerNetworkSavedData.load(saved.save(new CompoundTag(),l.registryAccess()),l.registryAccess()).links().equals(saved.links()),"Dock topology persists");
                h.runAfterDelay(1,()->{account(l);h.assertTrue(lit(l,p)&&store.storedJoules()==0,"Last full interval is paid");
                    h.runAfterDelay(1,()->{account(l);h.assertTrue(!lit(l,p),"Depletion turns the lamp off");
                        store.setItem(0,PowerCellItem.charged(50));
                        h.runAfterDelay(1,()->{account(l);h.assertTrue(lit(l,p)&&store.storedJoules()==0,"New cell resumes without rebinding");
                            l.setBlock(p,l.getBlockState(p).setValue(WorkLampBlock.ENABLED,false),3);
                            h.runAfterDelay(1,()->{account(l);h.assertTrue(!lit(l,p)&&PowerNetworks.reading(l,p).demandKw()==0,"Manual off draws zero");h.succeed();});
                        });
                    });
                });
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="work_lamp",timeoutTicks=100)
    public static void workLampGridWorksInVacuumAndOrphanedLightReconciles(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=lamp(h,7,3,4);var source=h.absolutePos(new BlockPos(3,3,4));
            l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            ((GeothermalCoreBlockEntity)l.getBlockEntity(source)).setPowerShare(1);link(h,source,p);
            var state=ApocalypseState.get(l.getServer());long previous=state.getApocalypseTicks();
            try {
                state.setApocalypseTicks((long)(state.getTotalDays()*24000L*.90),l.getServer());account(l);
                h.assertTrue(CombustionAtmosphere.isVacuum(l)&&!RoomAtmosphere.hasAir(l,p)&&lit(l,p),"50 W work light works in real vacuum without oxygen");
                h.assertTrue(RoomAtmosphere.isPassage(l,p,l.getBlockState(p))&&l.getBlockEntity(p)==null,"Small fixture cannot plug a pressure breach or store fuel");
                h.assertTrue(Math.abs(PowerNetworks.reading(l,p).demandKw()-.05)<1e-6,"Grid reports 0.05 kW");
            } finally {state.setApocalypseTicks(previous,l.getServer());}
            PowerNetworks.clear(l,p);account(l);h.assertTrue(!lit(l,p),"Disconnected fixture goes dark");
            l.setBlock(p,l.getBlockState(p).setValue(WorkLampBlock.LIT,true),3);
            h.runAfterDelay(25,()->{h.assertTrue(!lit(l,p),"Orphaned saved lit state reconciles on ordinary ticks");h.succeed();});
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="work_lamp",timeoutTicks=100)
    public static void workLampBinderBothHandsAndEmptyHandToggle(GameTestHelper h) {
        var l=h.getLevel();var p=lamp(h,4,3,4);var relay=h.absolutePos(new BlockPos(8,3,4));
        l.setBlock(relay,ModBlocks.POWER_RELAY.get().defaultBlockState(),3);
        var profile=new GameProfile(UUID.randomUUID(),"lamp_binder");
        var player=new net.minecraft.server.level.ServerPlayer(l.getServer(),l,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var channel=new io.netty.channel.embedded.EmbeddedChannel();
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public io.netty.channel.Channel channel() {return channel;}
        };
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(l.getServer(),connection,player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
            @Override public void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {}
        };
        player.setPos(p.south().getCenter());var hit=new BlockHitResult(p.getCenter(),Direction.SOUTH,p,false);
        try {
            for(var hand:InteractionHand.values()) {
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                var binder=new ItemStack(ModItems.CONDUIT_BINDER.get());player.setItemInHand(hand,binder);
                if(hand==InteractionHand.OFF_HAND)h.assertTrue(!player.gameMode.useItemOn(player,l,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit).consumesAction(),"Empty main hand leaves the offhand Binder its click");
                player.gameMode.useItemOn(player,l,binder,hand,hit);
                h.assertTrue(GlobalPos.of(l.dimension(),p).equals(binder.get(ModDataComponents.CONDUIT_ANCHOR))&&l.getBlockState(p).getValue(WorkLampBlock.ENABLED),"Binder selects without toggling: "+hand);
                player.gameMode.useItemOn(player,l,binder,hand,new BlockHitResult(relay.getCenter(),Direction.UP,relay,false));
                h.assertTrue(PowerNetworkSavedData.get(l).degree(p)==1,"Binder creates actual lamp link");
                player.setShiftKeyDown(true);player.gameMode.useItemOn(player,l,binder,hand,hit);player.setShiftKeyDown(false);
                h.assertTrue(PowerNetworkSavedData.get(l).degree(p)==0&&l.getBlockState(p).getValue(WorkLampBlock.ENABLED),"Shift Binder clears without toggling");
            }
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.gameMode.useItemOn(player,l,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(!l.getBlockState(p).getValue(WorkLampBlock.ENABLED),"Empty hand turns off");
            player.gameMode.useItemOn(player,l,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(l.getBlockState(p).getValue(WorkLampBlock.ENABLED),"Empty hand turns on");h.succeed();
        } finally {channel.finishAndReleaseAll();}
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="work_lamp",timeoutTicks=100)
    public static void workLampPlaytestFunctionsLoad(GameTestHelper h) {
        for(var name:List.of("load","bootstrap","tick","setup","controls","grid","panel","cell","mounts","core","read"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("work_lamp_check",name)).isPresent(),"Native parser loads Work Lamp function "+name);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="work_lamp",timeoutTicks=100)
    public static void workLampBrownoutActivatesExistingEmergencyReserve(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=lamp(h,8,3,6);var source=h.absolutePos(new BlockPos(2,3,6));
            var machine=h.absolutePos(new BlockPos(4,3,6));var reserve=h.absolutePos(new BlockPos(6,3,6));
            l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
            l.setBlock(machine,ModBlocks.INDUCTION_FURNACE.get().defaultBlockState(),3);
            l.setBlock(reserve,ModBlocks.ORSA_EMERGENCY_LIGHT.get().defaultBlockState(),3);
            var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.setPowerShare(100);
            link(h,source,machine);link(h,machine,reserve);link(h,reserve,p);account(l);
            var backup=(OrsaEmergencyLightBlockEntity)l.getBlockEntity(reserve);backup.serverTick();
            h.assertTrue(backup.backupTicks()==12000&&lit(l,p),"Healthy grid arms existing reserve alongside Work Lamp");
            ((InductionFurnaceBlockEntity)l.getBlockEntity(machine)).setItem(0,new ItemStack(Items.RAW_IRON));
            core.setPowerShare(10);account(l);
            h.assertTrue(PowerNetworks.powered(l,machine)&&!lit(l,p),"2 kW supports the existing machine and sheds the 50 W light");
            h.runAfterDelay(1,()->{backup.serverTick();
                h.assertTrue(l.getBlockState(reserve).getValue(OrsaEmergencyLightBlock.LIT)&&backup.backupTicks()<12000,"Shed Work Lamp triggers the real emergency reserve");h.succeed();
            });
        });
    }
    private static BlockPos lamp(GameTestHelper h,int x,int y,int z) {
        var p=h.absolutePos(new BlockPos(x,y,z));h.getLevel().setBlock(p.north(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(p,ModBlocks.WORK_LAMP.get().defaultBlockState().setValue(WorkLampBlock.FACING,Direction.SOUTH),3);return p;
    }
    private static void link(GameTestHelper h,BlockPos a,BlockPos b) {h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected work lamp link");}
    private static void account(ServerLevel l) {for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l);}
    private static boolean lit(ServerLevel l,BlockPos p) {return l.getBlockState(p).getValue(WorkLampBlock.LIT);}
}
