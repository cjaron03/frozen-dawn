package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModDataComponents;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerNetworkGameTest {
    @BeforeBatch(batch="power_network")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerRelayRangesAndTransparentBlocks(GameTestHelper h) {
        scene(h, () -> {
            var a = node(h, 1, 2, 2, false); var b = node(h, 17, 2, 2, false);
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, b) == PowerLinkRules.Result.OK, "Exactly 16 blocks is allowed");
            h.assertTrue(PowerNetworks.link(h.getLevel(), b, a) == PowerLinkRules.Result.DUPLICATE, "Reverse link is the same edge");
            var far = node(h, 18, 2, 2, false);
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, far) == PowerLinkRules.Result.RANGE, "17 blocks exceeds small range");
            h.setBlock(new BlockPos(1,2,2), ModBlocks.LARGE_POWER_RELAY.get());
            h.setBlock(new BlockPos(18,2,2), ModBlocks.LARGE_POWER_RELAY.get());
            for (int x = 5; x <= 7; x++) h.setBlock(new BlockPos(x,2,2), Blocks.STONE);
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, far) == PowerLinkRules.Result.OK, "Large relays link beyond 16 through ordinary stone");
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, a) == PowerLinkRules.Result.SELF, "Self links are rejected");
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, h.absolutePos(new BlockPos(8,3,8))) == PowerLinkRules.Result.NOT_NODE, "Air is not a node");
            var unloaded = new BlockPos(1234567,200,1234567);
            h.assertTrue(PowerNetworks.link(h.getLevel(), a, unloaded) == PowerLinkRules.Result.UNLOADED && !h.getLevel().isLoaded(unloaded), "Validation never loads an absent endpoint");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerGraphPersistsCyclesAndSplitsAfterRemoval(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var a=node(h,3,2,3,false);var b=node(h,8,2,3,false);var c=node(h,13,2,3,false);
            link(h,a,b);link(h,b,c);link(h,c,a);refresh(level);
            var r=PowerNetworks.reading(level,a);
            h.assertTrue(r.nodes()==3&&r.links()==3,"A cyclic graph has three unique nodes and three edges");
            h.assertTrue(r.supplyKw()==0&&r.demandKw()==0&&r.storedKj()==0,"Topology creates no free power or storage");
            var data=PowerNetworkSavedData.get(level);var decoded=PowerNetworkSavedData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(decoded.links().equals(data.links()),"SavedData reload retains topology exactly");
            h.assertTrue(decoded.clear(b)==2&&data.degree(b)==2,"Clearing a decoded copy cannot alter live links");
            h.assertTrue(PowerNetworks.clear(level,a)==2,"Shift clear removes all incident edges");
            refresh(level);h.assertTrue(PowerNetworks.reading(level,a).nodes()==1&&PowerNetworks.reading(level,b).nodes()==2,"Clearing a node splits the graph");
            h.setBlock(new BlockPos(8,2,3),Blocks.AIR);refresh(level);
            h.assertTrue(data.degree(b)==0&&PowerNetworks.reading(level,c).nodes()==1,"Real block removal clears saved incident links");
            h.setBlock(new BlockPos(8,2,3),ModBlocks.POWER_RELAY.get());refresh(level);
            h.assertTrue(data.degree(b)==0,"Replacement relay does not inherit deleted links");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerSealedWallRequiresFeedthroughAndRevalidates(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();box(h);var a=node(h,4,3,5,false);var b=node(h,10,3,5,false);
            h.assertTrue(PowerNetworks.link(level,a,b)==PowerLinkRules.Result.FEEDTHROUGH,"Solid sealed wall blocks a direct link");
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());
            link(h,a,b);refresh(level);h.assertTrue(PowerNetworks.reading(level,a).nodes()==2,"Beam intersects feedthrough and connects");
            h.setBlock(new BlockPos(7,3,5),Blocks.GLASS);refresh(level);
            h.assertTrue(PowerNetworkSavedData.get(level).links().contains(new PowerLink(a,b))&&PowerNetworks.reading(level,a).links()==0,"Wall repair suspends the link without erasing its history");
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());refresh(level);
            h.assertTrue(PowerNetworks.reading(level,a).links()==1,"Restoring the feedthrough reconnects the saved link");
            h.assertTrue(com.frozendawn.world.RoomAtmosphere.inspect(level,a).seal()==com.frozendawn.world.RoomAtmosphere.Seal.SEALED,"Feedthrough retains the pressure seal");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerFeedthroughCanBeLinkedAsWallNode(GameTestHelper h) {
        scene(h, () -> {
            box(h);var a=node(h,4,3,5,false);var b=node(h,10,3,5,false);
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());var wall=h.absolutePos(new BlockPos(7,3,5));
            link(h,a,wall);link(h,wall,b);refresh(h.getLevel());
            h.assertTrue(PowerNetworks.reading(h.getLevel(),a).nodes()==3,"Two links terminating at a feedthrough route through the wall");
            h.setBlock(new BlockPos(7,3,5),Blocks.AIR);refresh(h.getLevel());
            h.assertTrue(PowerNetworkSavedData.get(h.getLevel()).degree(wall)==0,"Removing a feedthrough clears both incident links");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerBinderSelectionFailureClearAndCoreInteraction(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var a=node(h,3,2,3,false);var b=node(h,8,2,3,false);
            var profile=new GameProfile(UUID.randomUUID(),"power_binder");var transport=new FakePlayer(level,profile);
            var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());player.connection=transport.connection;
            var stack=new ItemStack(ModItems.CONDUIT_BINDER.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            use(player,a);h.assertTrue(stack.get(ModDataComponents.CONDUIT_ANCHOR).equals(GlobalPos.of(level.dimension(),a)),"First click selects the node on this tool");
            use(player,h.absolutePos(new BlockPos(6,3,6)));h.assertTrue(stack.has(ModDataComponents.CONDUIT_ANCHOR),"Invalid target retains selection");
            use(player,b);h.assertTrue(!stack.has(ModDataComponents.CONDUIT_ANCHOR)&&PowerNetworkSavedData.get(level).links().contains(new PowerLink(a,b)),"Second click links and clears selection");
            var other=new ItemStack(ModItems.CONDUIT_BINDER.get());h.assertTrue(!other.has(ModDataComponents.CONDUIT_ANCHOR),"Another Binder has independent selection");
            player.setShiftKeyDown(true);use(player,b);player.setShiftKeyDown(false);
            h.assertTrue(PowerNetworkSavedData.get(level).degree(b)==0,"Shift click clears target links");
            h.setBlock(new BlockPos(3,2,3),ModBlocks.GEOTHERMAL_CORE.get());
            var hit=new BlockHitResult(Vec3.atCenterOf(a),Direction.UP,a,false);
            level.getBlockState(a).useItemOn(stack,level,player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(stack.has(ModDataComponents.CONDUIT_ANCHOR)&&player.containerMenu==player.inventoryMenu,"Binder selects Core through real block interaction without opening the menu");
            var encoded=stack.saveOptional(level.registryAccess());var restored=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)encoded);
            h.assertTrue(restored.get(ModDataComponents.CONDUIT_ANCHOR).equals(stack.get(ModDataComponents.CONDUIT_ANCHOR)),"Selected anchor survives item serialization");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerSnapshotCodecAndLinkLimits(GameTestHelper h) {
        scene(h, () -> {
            var a=h.absolutePos(new BlockPos(3,2,3));var b=h.absolutePos(new BlockPos(8,2,3));
            var payload=new com.frozendawn.network.PowerLinksPayload(h.getLevel().dimension().location().toString(),List.of(new PowerLink(a,b)));
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {com.frozendawn.network.PowerLinksPayload.STREAM_CODEC.encode(buffer,payload);
                h.assertTrue(com.frozendawn.network.PowerLinksPayload.STREAM_CODEC.decode(buffer).equals(payload),"Beam snapshot round-trips dimension and endpoints");}
            finally {buffer.release();}
            var data=new PowerNetworkSavedData();
            for(int i=1;i<=16;i++)h.assertTrue(data.add(new PowerLink(a,a.offset(i,0,1))),"First 16 node links accepted");
            h.assertTrue(!data.add(new PowerLink(a,a.offset(17,0,1))),"17th node link rejected");
            for(int i=0;i<1008;i++)h.assertTrue(data.add(new PowerLink(a.offset(i*2,0,4),a.offset(i*2+1,0,4))),"Disconnected links fit dimension budget");
            h.assertTrue(!data.add(new PowerLink(a.offset(3000,0,4),a.offset(3001,0,4))),"Dimension budget rejects 1025th link");
            h.assertTrue(PowerLinkRules.path(a,b).equals(PowerLinkRules.path(b,a).reversed()),"Voxel path is direction-independent");
            var diagonal=a.offset(16,16,16);h.assertTrue(PowerLinkRules.path(a,diagonal).equals(PowerLinkRules.path(diagonal,a).reversed()),"Corner traversal is direction-independent");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerBinderPreviewIsReadOnlyAndTracksWallChanges(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();box(h);var a=node(h,4,3,5,false);var b=node(h,10,3,5,false);
            var player=binderPlayer(level);var stack=player.getMainHandItem();
            stack.set(ModDataComponents.CONDUIT_ANCHOR,GlobalPos.of(level.dimension(),a));
            var before=Set.copyOf(PowerNetworkSavedData.get(level).links());
            var blocked=PowerBinderFeedback.snapshot(player,b);
            h.assertTrue(blocked.status()==com.frozendawn.network.PowerBinderPayload.Status.FEEDTHROUGH,"Preview rejects the sealed wall before a click");
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());
            var ready=PowerBinderFeedback.snapshot(player,b);
            h.assertTrue(ready.status()==com.frozendawn.network.PowerBinderPayload.Status.READY&&ready.distance()==6&&ready.reach()==16,"Wall mutation invalidates cached rejection immediately");
            h.assertTrue(PowerNetworkSavedData.get(level).links().equals(before)&&stack.get(ModDataComponents.CONDUIT_ANCHOR).pos().equals(a),"Looking never creates links or clears selection");
            link(h,a,b);refresh(level);
            h.assertTrue(PowerBinderFeedback.snapshot(player,b).status()==com.frozendawn.network.PowerBinderPayload.Status.ALREADY_LINKED,"Actual link changes invalidate cached ready state");
            h.setBlock(new BlockPos(7,3,5),Blocks.GLASS);
            h.assertTrue(PowerBinderFeedback.snapshot(player,b).status()==com.frozendawn.network.PowerBinderPayload.Status.FEEDTHROUGH,"Saved link does not mask its newly blocked wall");
            var unloaded=new BlockPos(1234567,200,1234567);stack.set(ModDataComponents.CONDUIT_ANCHOR,GlobalPos.of(level.dimension(),unloaded));
            h.assertTrue(PowerBinderFeedback.snapshot(player,b).status()==com.frozendawn.network.PowerBinderPayload.Status.UNAVAILABLE&&!level.isLoaded(unloaded),"Unavailable source neither loads chunks nor claims ready");
            stack.set(ModDataComponents.CONDUIT_ANCHOR,GlobalPos.of(net.minecraft.world.level.Level.NETHER,a));
            h.assertTrue(PowerBinderFeedback.snapshot(player,b).source()==null,"Selection in another dimension cannot preview a link here");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertTrue(PowerBinderFeedback.snapshot(player,b).source()==null&&PowerBinderFeedback.snapshot(player,b).target()==null,"No held tool exposes no targeting state");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_network",timeoutTicks=100)
    public static void powerBinderPreviewCodecsAndAirCancelPreserveLinks(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var a=node(h,3,2,3,false);var b=node(h,8,2,3,false);var far=node(h,20,2,3,false);
            var player=binderPlayer(level);var stack=player.getMainHandItem();
            stack.set(ModDataComponents.CONDUIT_ANCHOR,GlobalPos.of(level.dimension(),a));
            h.assertTrue(PowerBinderFeedback.snapshot(player,far).status()==com.frozendawn.network.PowerBinderPayload.Status.RANGE,"Out-of-range aim is rejected before clicking");
            var selected=PowerBinderFeedback.snapshot(player,null);
            h.assertTrue(selected.source().equals(a)&&selected.target()==null,"Source stays selected while looking at empty space");
            var ready=PowerBinderFeedback.snapshot(player,b);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try {
                for(var packet:List.of(selected,ready)) {
                    com.frozendawn.network.PowerBinderPayload.STREAM_CODEC.encode(buffer,packet);
                    h.assertTrue(com.frozendawn.network.PowerBinderPayload.STREAM_CODEC.decode(buffer).equals(packet),"Targeting payload retains nullable endpoints, validated status and units");
                }
                var notice=new com.frozendawn.network.PowerBinderNoticePayload("cleared",2);
                com.frozendawn.network.PowerBinderNoticePayload.STREAM_CODEC.encode(buffer,notice);
                h.assertTrue(com.frozendawn.network.PowerBinderNoticePayload.STREAM_CODEC.decode(buffer).equals(notice),"Short click feedback preserves its count");
            } finally {buffer.release();}
            link(h,a,b);var before=Set.copyOf(PowerNetworkSavedData.get(level).links());
            stack.getItem().use(level,player,InteractionHand.MAIN_HAND);
            h.assertTrue(!stack.has(ModDataComponents.CONDUIT_ANCHOR)&&PowerNetworkSavedData.get(level).links().equals(before),"Right-click air cancels only selection and preserves every saved link");
            var other=new ItemStack(ModItems.CONDUIT_BINDER.get());player.setItemInHand(InteractionHand.OFF_HAND,other);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            h.assertTrue(PowerBinderFeedback.held(player)==other&&PowerBinderFeedback.snapshot(player,b).source()==null,"Offhand Binder starts with its own selection");
        });
    }
    private static ServerPlayer binderPlayer(ServerLevel level) {
        var profile=new GameProfile(UUID.randomUUID(),"binder_preview");var transport=new FakePlayer(level,profile);
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());player.connection=transport.connection;
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.CONDUIT_BINDER.get()));return player;
    }
    private static void refresh(ServerLevel level) { for(int i=0;i<128;i++)PowerNetworks.update(level); }
    @GameTest(template=GameTestTemplates.EMPTY,batch="power_network",timeoutTicks=100)
    public static void powerReplayFunctionsParse(GameTestHelper h) {
        for(var name:List.of("load","tick","setup","controls","basic","large","stone","sealed","feedthrough","chain"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("power_network_check",name)).isPresent(),"Native parser loaded Power Network Check function "+name);
        h.succeed();
    }
    private static void use(ServerPlayer player,BlockPos pos) {
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
    }
    private static BlockPos node(GameTestHelper h,int x,int y,int z,boolean large) {
        var pos=new BlockPos(x,y,z);h.setBlock(pos,large?ModBlocks.LARGE_POWER_RELAY.get():ModBlocks.POWER_RELAY.get());return h.absolutePos(pos);
    }
    private static void link(GameTestHelper h,BlockPos a,BlockPos b) {
        h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected valid link "+a+" -> "+b);
    }
    private static void box(GameTestHelper h) {
        for(int x=2;x<=7;x++)for(int y=1;y<=6;y++)for(int z=2;z<=8;z++)
            h.setBlock(new BlockPos(x,y,z),x==2||x==7||y==1||y==6||z==2||z==8?Blocks.GLASS:Blocks.AIR);
    }
    private static void scene(GameTestHelper h,Runnable body) { h.runAfterDelay(1,()->{body.run();h.succeed();}); }
}
