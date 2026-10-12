package com.frozendawn.airlock;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirlockGameTest {
    @BeforeBatch(batch="airlock_native")
    public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}
    private static long oldPhase;
    @BeforeBatch(batch="airlock_loaded_cycles")
    public static void cyclePhase(ServerLevel level) {
        GameTestReporting.installReporter(level);var a=ApocalypseState.get(level.getServer());oldPhase=a.getApocalypseTicks();
        a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),level.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
    }
    @AfterBatch(batch="airlock_loaded_cycles")
    public static void restorePhase(ServerLevel level) {
        ApocalypseState.get(level.getServer()).setApocalypseTicks(oldPhase,level.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_loaded_cycles",timeoutTicks=200)
    public static void airlockLoadedPanelsPumpOncePerTickAndRestoreAir(GameTestHelper h) {
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var second=s.center.south(2);l.setBlock(second,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.NORTH),2);
        h.assertTrue(AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(second))==c,"Both ticking panels share authority");
        h.assertTrue(AirlockManager.start(l,c).equals("depressurizing"),"Native cycle begins");
        h.runAfterDelay(10,()->h.assertTrue(c.gas.elapsed()>=9&&c.gas.elapsed()<=11,"Two panel entities advance one server tick, not twice"));
        h.runAfterDelay(85,()->{
            h.assertTrue(!c.gas.cycling()&&c.gas.air()==0&&c.gas.reserve()==2430,"Real 80-tick cycle recovers exactly 90 percent");
            h.assertTrue(l.getBlockState(s.panel).getValue(AirlockControllerBlock.INDICATOR)==0,"Native block state shows evacuated red");
            var tank=new ItemStack(ModItems.O2_TANK_MK3.get());tank.set(ModDataComponents.O2_LEVEL.get(),270);
            h.assertTrue(AirlockManager.charge(l,c,tank)==270,"Finite canister replaces actual loss");
            h.assertTrue(AirlockManager.start(l,c).equals("pressurizing"),"No-Core backup operation starts");
        });
        h.runAfterDelay(175,()->{
            h.assertTrue(c.gas.breathable()&&c.gas.reserve()==0,"Native pump debit matches complete chamber requirement");
            h.assertTrue(RoomAtmosphere.hasAir(l,s.center),"Production room query sees restored air");
            h.assertTrue(l.getBlockState(second).getValue(AirlockControllerBlock.INDICATOR)==2,"Optional panel synchronizes green state");
            h.succeed();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="airlock_native",timeoutTicks=100)
    public static void airlockPlaytestFunctionsLoad(GameTestHelper h) {
        var server=h.getLevel().getServer();var functions=server.getResourceManager().listResources("function",id->id.getNamespace().equals("airlock_check")&&id.getPath().endsWith(".mcfunction"));
        h.assertTrue(functions.size()==10,"All authored test-world functions present");
        functions.forEach((file,res)->{
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("airlock_check",file.getPath().substring("function/".length()).replace(".mcfunction",""));
            h.assertTrue(server.getFunctions().get(id).isPresent(),"Native parser loads "+id);
        });h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="airlock_native",timeoutTicks=100)
    public static void airlockSilentStatusUsesTypedPacketsAndRoundTrips(GameTestHelper h) {
        var level=h.getLevel();var sent=new java.util.ArrayList<com.frozendawn.network.AirlockStatusPayload>();
        var chat=new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"airlock_status"));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.AirlockStatusPayload status)sent.add(status);
                else chat.add(packet);
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {send(packet);}
        };
        for(var status:com.frozendawn.network.AirlockStatusPayload.Status.values()) {
            AirlockManager.notify(player,status,null,42);
            var payload=sent.getLast();var buffer=io.netty.buffer.Unpooled.buffer();
            try {
                com.frozendawn.network.AirlockStatusPayload.STREAM_CODEC.encode(buffer,payload);
                h.assertTrue(payload.equals(com.frozendawn.network.AirlockStatusPayload.STREAM_CODEC.decode(buffer)),"Native status codec retains type and exact quantities");
            } finally {buffer.release();}
        }
        h.assertTrue(sent.size()==com.frozendawn.network.AirlockStatusPayload.Status.values().length,"Every routine status uses the typed client path");
        h.assertTrue(chat.isEmpty(),"Routine status emits no server chat/actionbar/sound packet");
        h.succeed();
    }
    record Scene(BlockPos base,BlockPos center,BlockPos inner,BlockPos outer,BlockPos panel,AirlockSavedState.Chamber chamber) {}
    static Scene scene(GameTestHelper h) {
        var l=h.getLevel();var base=h.absolutePos(new BlockPos(9,3,10));var center=base.east(5);
        room(l,base,3);room(l,center,2);
        var inner=base.east(3);var outer=center.east(2);
        for(var pos:new BlockPos[]{inner,outer}) {
            var s=ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
            l.setBlock(pos,s,2);l.setBlock(pos.above(),s.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        }
        var panel=center.north(2);
        l.setBlock(panel,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.SOUTH),2);
        var c=AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(panel));
        h.assertTrue(c!=null,"Two real complete doors and arbitrary glass shell resolve a chamber");
        h.assertTrue(c.cells.size()==27,"27 passable cells; wall/controller blocks excluded");
        return new Scene(base,center,inner,outer,panel,c);
    }
    static void room(ServerLevel l,BlockPos center,int radius) {
        for(int x=-radius;x<=radius;x++)for(int y=-1;y<=3;y++)for(int z=-radius;z<=radius;z++) {
            boolean wall=Math.abs(x)==radius||Math.abs(z)==radius||y==-1||y==3;
            l.setBlock(center.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
    }
    static void check(GameTestHelper h,Runnable body) {
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long old=a.getApocalypseTicks();
        try {a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),l.getServer());CombustionAtmosphere.reset();RoomAtmosphere.reset();body.run();h.succeed();}
        finally {a.setApocalypseTicks(old,l.getServer());CombustionAtmosphere.reset();RoomAtmosphere.reset();AtmosphericBreach.reset();}
    }
    static void finish(ServerLevel l,AirlockSavedState.Chamber c) {
        for(int i=0;i<79;i++)c.gas.tick();c.lastTick=Long.MIN_VALUE;AirlockManager.tick(l,c);
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockPressureGuardsBothHalvesAndDirectAutomation(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);
        h.assertTrue(AirlockManager.canOpen(l,s.inner),"Inner door initially equalizes two breathable rooms");
        h.assertFalse(AirlockManager.canOpen(l,s.outer),"Outer door refuses true vacuum differential");
        for(var pos:new BlockPos[]{s.outer,s.outer.above()}) {
            l.setBlock(pos,l.getBlockState(pos).setValue(DoorBlock.OPEN,true).setValue(DoorBlock.POWERED,true),3);
            h.assertFalse(l.getBlockState(pos).getValue(DoorBlock.OPEN),"LevelChunk guard blocks lower/upper direct automation");
        }
        l.setBlock(s.outer.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        h.assertFalse(l.getBlockState(s.outer).getValue(DoorBlock.OPEN),"Actual redstone update cannot override differential");
        l.setBlock(s.outer.east(),Blocks.AIR.defaultBlockState(),3); // Remove solid obstruction before asserting a usable vacuum-side doorway.
        h.assertTrue(l.getBlockState(s.outer).is(net.minecraft.tags.BlockTags.DOORS),"Native 1.21 door tag loaded");
        h.assertTrue(l.getBlockState(s.panel).is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"Native mining tag loaded");
        h.assertTrue(AirlockManager.start(l,s.chamber).equals("depressurizing"),"Closed chamber begins real vent cycle");
        h.assertFalse(AirlockManager.canOpen(l,s.inner)&&AirlockManager.canOpen(l,s.outer),"Cycle keeps both doors locked");
        h.assertFalse(AirlockManager.canOpen(l,s.inner),"Inner is locked while cycling");
        finish(l,s.chamber);
        h.assertFalse(AirlockManager.canOpen(l,s.inner),"Evacuated chamber cannot open to base");
        h.assertTrue(AirlockManager.canOpen(l,s.outer),"Equal vacuum unlocks outer door");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base),"Isolated shelter retains oxygen");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockCanisterDebitSharedPanelsAndSavedGas(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var tank=new ItemStack(ModItems.O2_TANK_MK3.get());tank.set(ModDataComponents.O2_LEVEL.get(),1000);
        h.assertTrue(AirlockManager.charge(l,c,tank)==1000&&tank.get(ModDataComponents.O2_LEVEL.get())==0,"Canister transfers exact stored O2 once");
        h.assertTrue(AirlockManager.charge(l,c,tank)==0,"Empty canister cannot charge twice");
        var second=s.center.south(2);l.setBlock(second,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.NORTH),2);
        h.assertTrue(AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(second))==c,"Optional panel shares exact authority");
        AirlockManager.start(l,c);for(int i=0;i<31;i++)c.gas.tick();
        var nbt=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var restored=AirlockSavedState.load(nbt,l.registryAccess()).byId(c.id);
        h.assertTrue(Arrays.equals(c.gas.snapshot(),restored.gas.snapshot()),"Native SavedData codec retains in-flight measured air/reserve");
        c.gas.interrupt();int air=c.gas.air(),reserve=c.gas.reserve();
        AirlockManager.removePanel(l,s.panel,c.id);h.assertTrue(c.gas.reserve()==reserve,"Removing one panel preserves shared storage");
        AirlockManager.removePanel(l,second,c.id);h.assertTrue(c.gas.reserve()==0&&c.gas.air()==air,"Last controller drops empty and cannot clone reserve");
        var replaced=AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(s.panel));
        h.assertTrue(replaced==c&&replaced.gas.air()==air&&replaced.gas.reserve()==0,"Replacing panel does not generate trapped air or reserve");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockCoreCannotBypassCycleAndValveKeepsSpentTorches(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var core=s.base.west();l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);l.getBlockEntity(core).onLoad();
        h.assertTrue(AirlockManager.hasCoreFeed(l,c),"Core inside connected base supplies reserve");
        var torch=s.center.west();var soul=s.center.south();
        l.setBlock(torch,Blocks.TORCH.defaultBlockState(),2);l.setBlock(soul,Blocks.SOUL_TORCH.defaultBlockState(),2);
        h.assertTrue(AirlockManager.emergencyVent(l,s.center.north(2),Direction.SOUTH),"Valve works with no pump requirement");
        h.assertTrue(c.gas.air()==0&&c.gas.reserve()==0,"Emergency vent grants no recovered O2");
        h.assertFalse(RoomAtmosphere.hasAir(l,s.center),"Nearby Core cannot instantly refill managed evacuated chamber");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base),"Closed inner door preserves base");
        h.assertTrue(l.getBlockState(torch).is(ModBlocks.SPENT_TORCH.get()),"Spent torch stays in place");
        h.assertTrue(l.getBlockState(soul).is(Blocks.SOUL_TORCH),"Soul exception remains active");
        c.gas.fill(c.gas.capacity());h.assertTrue(AirlockManager.start(l,c).equals("pressurizing"),"Reserve feeds measured refill");finish(l,c);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.center),"Completed cycle restores actual breathable chamber");
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.inner),s.inner,true);
        h.assertTrue(l.getBlockState(s.inner).getValue(DoorBlock.OPEN),"Equal air allows inner door");
        h.assertTrue(AirlockManager.emergencyVent(l,s.center.north(2),Direction.SOUTH),"Valve resolves existing chamber with inner open");
        h.assertFalse(RoomAtmosphere.hasAir(l,s.base),"Emergency valve with inner open also vents connected base");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockBrokenShellAndOversizeRejectFreeAir(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        l.setBlock(s.center.south(2),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(c.gas.air()==0,"Real shell breach invalidates measured chamber gas immediately");
        l.setBlock(s.center.south(2),Blocks.GLASS.defaultBlockState(),3);
        h.assertTrue(AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(s.panel))==c&&c.gas.air()==0,"Repair preserves evacuation history");
        h.assertFalse(RoomAtmosphere.hasAir(l,s.center),"Repair alone cannot credit air");
        l.setBlock(s.outer.above(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(AirlockManager.discover(l,s.panel,Direction.SOUTH)==null,"Incomplete two-block door rejects recognition");
        var oversized=RoomAtmosphere.inspectAirlockPartition(l,s.base,33);
        h.assertTrue(oversized.seal()!=RoomAtmosphere.Seal.SEALED,"Base exceeding 32 interior cells is never an airlock");
    });}
}
