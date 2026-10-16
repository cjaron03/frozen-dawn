package com.frozendawn.airlock;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.world.*;
import com.frozendawn.power.*;
import com.frozendawn.item.PowerCellItem;
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
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_loaded_cycles",timeoutTicks=240)
    public static void airlockLoadedPanelsPumpOncePerTickAndRestoreAir(GameTestHelper h) {
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var second=s.center.south(2);l.setBlock(second,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.NORTH),2);
        h.assertTrue(AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(second))==c,"Both ticking panels share authority");
        h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent")&&c.gas.cycling(),"Native cycle begins");
        PowerNetworks.update(l);PowerNetworks.account(l);
        h.runAfterDelay(30,()->h.assertTrue(c.gas.elapsed()>0&&c.gas.elapsed()<=30,"Allocation starts within its cadence; two panels cannot advance twice per server tick"));
        h.runAfterDelay(110,()->{
            h.assertTrue(!c.gas.cycling()&&c.gas.air()==0&&c.gas.reserve()==2430,"Real 80-tick cycle recovers exactly 90 percent");
            h.assertTrue(l.getBlockState(s.panel).getValue(AirlockControllerBlock.INDICATOR)==0,"Native block state shows evacuated red");
            var tank=new ItemStack(ModItems.O2_TANK_MK3.get());tank.set(ModDataComponents.O2_LEVEL.get(),270);
            h.assertTrue(AirlockManager.charge(l,c,tank)==270,"Finite canister replaces actual loss");
            h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_fill")&&c.gas.cycling(),"No-Core backup operation starts");
        });
        h.runAfterDelay(225,()->{
            h.assertTrue(c.gas.breathable()&&c.gas.reserve()==0,"Native pump debit matches complete chamber requirement");
            h.assertTrue(RoomAtmosphere.hasAir(l,s.center),"Production room query sees restored air");
            h.assertTrue(l.getBlockState(second).getValue(AirlockControllerBlock.INDICATOR)==2,"Optional panel synchronizes green state");
            h.succeed();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_loaded_cycles",timeoutTicks=160)
    public static void poweredAirlockKeepsRunningStatusAcrossNativeAllocations(GameTestHelper h) {
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        PowerNetworks.clear(l,s.panel);
        var corePos=s.panel.above(2);l.setBlock(corePos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(corePos);core.setPowerShare(100);
        h.assertTrue(PowerNetworks.link(l,corePos,s.panel)==PowerLinkRules.Result.OK,"Core supplies the real ticking controller");
        PowerNetworks.update(l);AirlockManager.start(l,c);
        var pausedTicks=new ArrayList<Long>();
        for(int i=1;i<=110;i++)h.runAfterDelay(i,()->{
            if(c.gas.cycling()&&c.gas.elapsed()>0&&c.waitingForPower)pausedTicks.add(l.getGameTime());
        });
        for(int i:new int[]{25,35,45,55,65,75})h.runAfterDelay(i,()->
                PowerNetworks.chunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(l.getChunkAt(s.panel),false)));
        h.runAfterDelay(120,()->{
            h.assertTrue(!c.gas.cycling()&&c.gas.reserve()==2430,"Ordinary block ticker completes the recovery cycle");
            h.assertTrue(pausedTicks.isEmpty(),"Continuous generation must not bounce back to waiting between ordinary allocations: "+pausedTicks);
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
    private static net.minecraft.server.level.ServerPlayer soundListener(ServerLevel level,BlockPos pos,String name,List<net.minecraft.sounds.SoundEvent> sounds) {
        return soundListener(level,pos,name,sounds,new ArrayList<>());
    }
    private static net.minecraft.server.level.ServerPlayer soundListener(ServerLevel level,BlockPos pos,String name,List<net.minecraft.sounds.SoundEvent> sounds,List<com.frozendawn.network.AirlockStatusPayload> statuses) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound)sounds.add(sound.getSound().value());
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.AirlockStatusPayload status)statuses.add(status);
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {send(packet);}
        };
        player.setPos(pos.getCenter());level.addNewPlayer(player);
        broadcastPlayers(level).add(player);
        return player;
    }
    @SuppressWarnings("unchecked")
    private static List<net.minecraft.server.level.ServerPlayer> broadcastPlayers(ServerLevel level) {
        // Native getPlayers() exposes an immutable view. Instrument the underlying broadcast
        // recipients only in this synchronous fixture, removing both listeners in finally.
        try {
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);
            return (List<net.minecraft.server.level.ServerPlayer>)field.get(level.getServer().getPlayerList());
        } catch(ReflectiveOperationException error){throw new AssertionError("Cannot install native sound recipients",error);}
    }
    private static void removeListener(ServerLevel level,net.minecraft.server.level.ServerPlayer player) {
        broadcastPlayers(level).remove(player);
        level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockDoorSoundIncludesClickerOnceAndBlocksFalseOpening(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);var actorSounds=new ArrayList<net.minecraft.sounds.SoundEvent>();var observerSounds=new ArrayList<net.minecraft.sounds.SoundEvent>();
        var actor=soundListener(l,s.center,"hatch_actor",actorSounds);var observer=soundListener(l,s.center,"hatch_observer",observerSounds);
        try {
            var hit=new net.minecraft.world.phys.BlockHitResult(s.inner.getCenter(),Direction.EAST,s.inner,false);
            l.getBlockState(s.inner).useWithoutItem(l,actor,hit);
            h.assertTrue(l.getBlockState(s.inner).getValue(DoorBlock.OPEN)&&l.getBlockState(s.inner.above()).getValue(DoorBlock.OPEN),"Real click opens both halves");
            h.assertTrue(actorSounds.equals(List.of(ModSounds.AIRLOCK_DOOR_OPEN.get()))&&observerSounds.equals(actorSounds),"Clicker and observer each receive exactly one custom open packet");
            ModBlocks.AIRLOCK_DOOR.get().setOpen(actor,l,l.getBlockState(s.inner.above()),s.inner.above(),false);
            h.assertTrue(actorSounds.equals(List.of(ModSounds.AIRLOCK_DOOR_OPEN.get(),ModSounds.AIRLOCK_DOOR_CLOSE.get()))&&observerSounds.equals(actorSounds),"Upper-half closure broadcasts once to both listeners");
            actorSounds.clear();observerSounds.clear();
            l.getBlockState(s.outer).useWithoutItem(l,actor,new net.minecraft.world.phys.BlockHitResult(s.outer.getCenter(),Direction.WEST,s.outer,false));
            h.assertTrue(!l.getBlockState(s.outer).getValue(DoorBlock.OPEN)&&actorSounds.equals(List.of(ModSounds.AIRLOCK_REFUSE.get()))&&observerSounds.equals(actorSounds),"Pressure denial has only a lock cue, never an opening cue");
            actorSounds.clear();observerSounds.clear();
            l.setBlock(s.outer.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(actorSounds.isEmpty()&&observerSounds.isEmpty(),"Blocked redstone opening makes no false hatch sound");
            l.setBlock(s.outer.east(),Blocks.AIR.defaultBlockState(),3);
            l.setBlock(s.inner.north(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(actorSounds.equals(List.of(ModSounds.AIRLOCK_DOOR_OPEN.get()))&&observerSounds.equals(actorSounds),"Allowed redstone opens with one custom sound: actor="+actorSounds+", observer="+observerSounds+", state="+l.getBlockState(s.inner));
            l.setBlock(s.inner.north(),Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(actorSounds.equals(List.of(ModSounds.AIRLOCK_DOOR_OPEN.get(),ModSounds.AIRLOCK_DOOR_CLOSE.get()))&&observerSounds.equals(actorSounds),"Removing power closes with one custom sound: actor="+actorSounds+", observer="+observerSounds+", state="+l.getBlockState(s.inner));
        } finally {removeListener(l,actor);removeListener(l,observer);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockMachineryIsPerChamberAndOnlyRunsDuringRealCycle(GameTestHelper h) {check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;var sounds=new ArrayList<net.minecraft.sounds.SoundEvent>();var listener=soundListener(l,s.center,"pump_observer",sounds);
        var second=s.center.south(2);l.setBlock(second,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.NORTH),2);
        h.assertTrue(AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(second))==c,"Optional panel shares the same chamber");
        try {
            h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent")&&c.gas.cycling(),"Actual recovery starts");
            for(int i=0;i<120&&c.gas.cycling();i++){paidTick(l,c);AirlockManager.tick(l,c);}
            h.assertTrue(sounds.stream().filter(v->v==ModSounds.AIRLOCK_PUMP_RECOVER.get()).count()==4&&sounds.stream().filter(v->v==ModSounds.AIRLOCK_EVACUATED.get()).count()==1&&sounds.size()==5,"80-tick recovery emits four segments and one finish despite two panel ticks: progress="+c.gas.elapsed()+" sounds="+sounds);
            sounds.clear();c.lastTick=Long.MIN_VALUE;AirlockManager.tick(l,c);h.assertTrue(sounds.isEmpty(),"Idle chamber is silent");
            c.gas.fill(c.gas.capacity());h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_fill")&&c.gas.cycling(),"Finite reserve starts refill");
            for(int i=0;i<120&&c.gas.cycling();i++){paidTick(l,c);AirlockManager.tick(l,c);}
            h.assertTrue(sounds.stream().filter(v->v==ModSounds.AIRLOCK_PUMP_FILL.get()).count()==4&&sounds.stream().filter(v->v==ModSounds.AIRLOCK_READY.get()).count()==1&&sounds.size()==5,"Refill has its own pump and one ready cue");
            sounds.clear();AirlockManager.start(l,c);l.setBlock(s.center.south(2).above(),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(sounds.contains(ModSounds.AIRLOCK_INTERRUPTED.get())&&!c.gas.cycling(),"Broken shell stops machinery with an interruption cue");
            sounds.clear();c.lastTick=Long.MIN_VALUE;AirlockManager.tick(l,c);h.assertTrue(sounds.isEmpty(),"Invalid chamber cannot keep pumping");
        } finally {removeListener(l,listener);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockSharesCoreProductionWithTankCanisterAndSuitWithoutMinting(GameTestHelper h){check(h,()->{
        var l=h.getLevel();long clock=l.getGameTime();var ledger=OxygenTransferState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var chambers=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        l.getDataStorage().set(OxygenTransferState.NAME,new OxygenTransferState());l.getDataStorage().set("frozendawn_airlocks",new AirlockSavedState());RoomOxygenManager.reset();
        var s=scene(h);var pos=s.base.west();l.setBlock(pos,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);var core=(GeothermalCoreBlockEntity)l.getBlockEntity(pos);core.onLoad();
        var canister=new ItemStack(ModItems.O2_TANK.get());canister.set(ModDataComponents.O2_LEVEL.get(),0);core.getItems().set(3,canister);
        var vessel=s.base.south();l.setBlock(vessel,ModBlocks.OXYGEN_RESERVOIR.get().defaultBlockState(),3);var tank=(OxygenReservoirBlockEntity)l.getBlockEntity(vessel);tank.onLoad();
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"shared_charge");var transport=new net.neoforged.neoforge.common.util.FakePlayer(l,profile);
        var player=new net.minecraft.server.level.ServerPlayer(l.getServer(),l,profile,net.minecraft.server.level.ClientInformation.createDefault());player.connection=transport.connection;player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(s.base.getCenter());
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
        var suitTank=new ItemStack(ModItems.O2_TANK.get());suitTank.set(ModDataComponents.O2_LEVEL.get(),0);player.getInventory().setItem(0,suitTank);l.addNewPlayer(player);
        try{
            RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);for(int i=0;i<5;i++)RoomOxygenManager.tickLevel(l);
            long before=tank.storedMicro()+1000L*(canister.get(ModDataComponents.O2_LEVEL.get())+suitTank.get(ModDataComponents.O2_LEVEL.get())+s.chamber.gas.reserve());
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+20);RoomOxygenManager.tickLevel(l);
            long after=tank.storedMicro()+1000L*(canister.get(ModDataComponents.O2_LEVEL.get())+suitTank.get(ModDataComponents.O2_LEVEL.get())+s.chamber.gas.reserve());
            h.assertTrue(after-before==70000,"8 production minus 1 breathing grants exactly 7 units across four charging paths: "+(after-before));
            h.assertTrue(tank.storedMicro()>0&&canister.get(ModDataComponents.O2_LEVEL.get())>0&&suitTank.get(ModDataComponents.O2_LEVEL.get())>0&&s.chamber.gas.reserve()>0,"All four receive a fair grant");
            AirlockManager.tick(l,s.chamber);core.serverTick();com.frozendawn.event.SuitIntegrityHandler.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));RoomOxygenManager.tickLevel(l);
            h.assertTrue(after==tank.storedMicro()+1000L*(canister.get(ModDataComponents.O2_LEVEL.get())+suitTank.get(ModDataComponents.O2_LEVEL.get())+s.chamber.gas.reserve()),"Legacy Core/suit/controller ticks cannot independently mint refill");
        }finally{player.discard();l.setBlock(vessel,Blocks.AIR.defaultBlockState(),3);l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);l.setBlock(s.panel,Blocks.AIR.defaultBlockState(),3);RoomOxygenManager.reset();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);l.getDataStorage().set(OxygenTransferState.NAME,OxygenTransferState.load(ledger,l.registryAccess()));l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(chambers,l.registryAccess()));}
    });}
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
        ((AirlockControllerBlockEntity)l.getBlockEntity(panel)).onLoad();
        var dock=panel.above(2);l.setBlock(dock,ModBlocks.CELL_DOCK.get().defaultBlockState(),2);
        var store=(PowerStorageBlockEntity)l.getBlockEntity(dock);store.onLoad();
        var cell=new ItemStack(ModItems.POWER_CELL.get());PowerCellItem.setEnergy(cell,500000);store.setItem(0,cell);
        h.assertTrue(PowerNetworks.link(l,panel,dock)==PowerLinkRules.Result.OK,"Legacy gas/interlock fixture now has real isolated pump power");PowerNetworks.update(l);
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
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long old=a.getApocalypseTicks(),clock=l.getGameTime();
        try {a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),l.getServer());CombustionAtmosphere.reset();RoomAtmosphere.reset();body.run();h.succeed();}
        finally {((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);PowerNetworks.stopped(null);a.setApocalypseTicks(old,l.getServer());CombustionAtmosphere.reset();RoomAtmosphere.reset();AtmosphericBreach.reset();}
    }
    static void finish(ServerLevel l,AirlockSavedState.Chamber c) {
        for(int i=0;i<120&&c.gas.cycling();i++)paidTick(l,c);
    }
    static void paidTick(ServerLevel l,AirlockSavedState.Chamber c){
        ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);
        PowerNetworks.update(l);
        if(l.getGameTime()%20==0||c.gas.elapsed()==0)PowerNetworks.account(l);
        AirlockManager.tick(l,c);
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockMetersOnePumpAndIdlesFree(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;var store=(PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2));
        PowerNetworks.account(l);h.assertTrue(store.storedJoules()==500000,"Idle pump draws no electricity");
        var second=s.center.south(2);l.setBlock(second,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.NORTH),2);
        var panel=(AirlockControllerBlockEntity)l.getBlockEntity(second);panel.onLoad();AirlockManager.resolve(l,panel);
        h.assertTrue(PowerNetworks.link(l,second,s.panel)==PowerLinkRules.Result.OK,"Second powered panel shares chamber");
        AirlockManager.start(l,c);for(int i=0;i<120&&c.gas.cycling();i++){paidTick(l,c);AirlockManager.tick(l,c);}
        h.assertTrue(!c.gas.cycling()&&c.gas.air()==0&&c.gas.reserve()==2430,"Paid recovery retains exact 90 percent O2: "+Arrays.toString(c.gas.snapshot())+" charge="+store.storedJoules());
        h.assertTrue(store.storedJoules()==492000,"80 paid ticks consume exactly 8 kJ despite optional panels and duplicate callbacks");
        c.gas.fill(270);AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(c.gas.breathable()&&c.gas.reserve()==0&&store.storedJoules()==484000,"Paid refill costs 8 kJ and restores measured gas");
        int charge=store.storedJoules();for(int i=0;i<40;i++)paidTick(l,c);
        h.assertTrue(store.storedJoules()==charge,"Completed pump remains free at idle");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockPausesResumesReloadsAndManualVentWorks(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;var dock=s.panel.above(2);var store=(PowerStorageBlockEntity)l.getBlockEntity(dock);
        store.removeItem(0,1);h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent"),"Unpowered request clearly waits");
        var before=c.gas.snapshot();for(int i=0;i<25;i++)paidTick(l,c);
        h.assertTrue(Arrays.equals(before,c.gas.snapshot())&&AirlockManager.canOpen(l,s.inner)&&!AirlockManager.canOpen(l,s.outer)&&RoomAtmosphere.hasAir(l,s.center),"An unpowered start keeps full chamber air and the matching-pressure return door available");
        h.assertTrue(l.getBlockState(s.panel).getValue(AirlockControllerBlock.INDICATOR)==2,"An unstarted full chamber remains green");
        var cell=new ItemStack(ModItems.POWER_CELL.get());PowerCellItem.setEnergy(cell,2000);store.setItem(0,cell);AirlockManager.start(l,c);
        for(int i=0;i<80&&c.gas.elapsed()<20;i++)paidTick(l,c);h.assertTrue(c.gas.elapsed()==20,"Finite cell pays twenty actual ticks: "+Arrays.toString(c.gas.snapshot())+" charge="+store.storedJoules());before=c.gas.snapshot();
        for(int i=0;i<25;i++)paidTick(l,c);
        h.assertTrue(store.storedJoules()==0&&Arrays.equals(before,c.gas.snapshot()),"Empty cell pauses at actual partial progress without refund");
        var saved=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(saved,l.registryAccess()));c=AirlockSavedState.get(l).byId(c.id);
        PowerNetworks.stopped(null);((AirlockControllerBlockEntity)l.getBlockEntity(s.panel)).onLoad();store.onLoad();
        h.assertTrue(Arrays.equals(before,c.gas.snapshot()),"Saved partial gas and reserve reconstruct exactly");
        PowerCellItem.setEnergy(cell,50000);store.setItem(0,cell);int remaining=80-c.gas.elapsed();
        for(int i=0;i<160&&c.gas.cycling();i++)paidTick(l,c);h.assertTrue(!c.gas.cycling(),"Restored pump completes within its paid window");
        h.assertTrue(store.storedJoules()==50000-remaining*100&&c.gas.reserve()==2430,"Cell swap resumes only remaining paid ticks after reload");
        c.gas.fill(270);AirlockManager.start(l,c);for(int i=0;i<10;i++)paidTick(l,c);
        PowerNetworks.clear(l,s.panel);before=c.gas.snapshot();for(int i=0;i<25;i++)paidTick(l,c);
        h.assertTrue(Arrays.equals(before,c.gas.snapshot()),"Unlink immediately revokes pump grants");
        h.assertTrue(AirlockManager.emergencyVent(l,s.panel,Direction.SOUTH)&&c.gas.air()==0&&!c.gas.cycling(),"Manual vent works without electrical power");
    });}
    private static void nextPaidTickWithoutAllocation(GameTestHelper h,Scene s,List<com.frozendawn.network.AirlockStatusPayload> statuses) {
        var l=h.getLevel();var c=s.chamber;var store=(PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2));
        int before=c.gas.elapsed(),energy=store.storedJoules();statuses.clear();
        ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);
        AirlockManager.tick(l,c);
        h.assertTrue(c.gas.elapsed()==before+1&&store.storedJoules()==energy-100,
                "Refresh preserves one paid100J tick: progress "+before+" -> "+c.gas.elapsed()
                        +", energy "+energy+" -> "+store.storedJoules()+", waiting="+c.waitingForPower+", network="+PowerNetworks.reading(l,s.panel));
        h.assertTrue(statuses.stream().noneMatch(p->p.status()==com.frozendawn.network.AirlockStatusPayload.Status.WAITING_TO_VENT
                ||p.status()==com.frozendawn.network.AirlockStatusPayload.Status.WAITING_TO_FILL),"No false waiting status");
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockKeepsGrantThroughUnrelatedBlockUpdates(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var statuses=new ArrayList<com.frozendawn.network.AirlockStatusPayload>();
        var listener=soundListener(l,s.center,"unrelated_blocks",new ArrayList<>(),statuses);
        var p=h.absolutePos(new BlockPos(27,3,28));var q=p.east(3);var original=l.getBlockState(p);var other=l.getBlockState(q);
        try {
            h.assertTrue(l.isLoaded(p)&&l.isLoaded(q),"Unrelated edits stay in already loaded fixture chunks");
            AirlockManager.start(l,s.chamber);paidTick(l,s.chamber);
            l.setBlock(p,Blocks.STONE.defaultBlockState(),3);nextPaidTickWithoutAllocation(h,s,statuses);
            l.setBlock(p,Blocks.AIR.defaultBlockState(),3);nextPaidTickWithoutAllocation(h,s,statuses);
            l.setBlock(p,ModBlocks.POWER_RELAY.get().defaultBlockState(),3);l.setBlock(q,ModBlocks.POWER_RELAY.get().defaultBlockState(),3);
            h.assertTrue(PowerNetworks.link(l,p,q)==PowerLinkRules.Result.OK,"Separate nearby network changes the graph without touching the airlock component");
            nextPaidTickWithoutAllocation(h,s,statuses);PowerNetworks.update(l);nextPaidTickWithoutAllocation(h,s,statuses);
            PowerNetworks.clear(l,p);nextPaidTickWithoutAllocation(h,s,statuses);PowerNetworks.update(l);nextPaidTickWithoutAllocation(h,s,statuses);
            PowerNetworks.clear(l,s.panel);var gas=s.chamber.gas.snapshot();
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);AirlockManager.tick(l,s.chamber);
            h.assertTrue(Arrays.equals(gas,s.chamber.gas.snapshot())&&s.chamber.waitingForPower,"Real disconnection still pauses immediately");
            int energy=((PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2))).storedJoules();
            PowerNetworks.account(l); // Deliberately before a caller refreshes topology.
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);AirlockManager.tick(l,s.chamber);
            h.assertTrue(Arrays.equals(gas,s.chamber.gas.snapshot())&&((PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2))).storedJoules()==energy,
                    "Reallocation cannot mint a replacement grant through a newly cut connection");
        } finally {removeListener(l,listener);l.setBlock(p,original,3);l.setBlock(q,other,3);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockRevalidatesPaidConnectionBeforeWaiting(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var previous=PowerNetworkSavedData.get(l);
        // Other synchronous cases leave saved links in the shared level. Their queued
        // validation must not consume this one-edge fixture's bounded work window.
        l.getDataStorage().set(PowerNetworkSavedData.NAME,new PowerNetworkSavedData());PowerNetworks.stopped(null);
        try {
            var s=scene(h);var c=s.chamber;
            h.assertTrue(PowerNetworkSavedData.get(l).links().size()==1,"Regression owns one isolated pump connection");
            var statuses=new ArrayList<com.frozendawn.network.AirlockStatusPayload>();
            var listener=soundListener(l,s.center,"revalidated_pump",new ArrayList<>(),statuses);
            try {
                for(boolean filling:new boolean[]{false,true}) {
                    if(filling)c.gas.fill(c.gas.capacity()-c.gas.reserve());
                    h.assertTrue(AirlockManager.start(l,c).equals(filling?"waiting_to_fill":"waiting_to_vent")&&c.gas.cycling()&&c.waitingForPower,
                            "Admitted cycle reports waiting until a paid tick, without an optimistic running flash");
                    paidTick(l,c);
                    // Recheck the actual edge while every chunk and block remains loaded.
                    PowerNetworks.chunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(l.getChunkAt(s.panel),false));
                    nextPaidTickWithoutAllocation(h,s,statuses);
                    h.assertTrue(!c.waitingForPower,"Validation of an unchanged connection cannot publish waiting");
                    finish(l,c);
                }
                AirlockManager.start(l,c);paidTick(l,c);
                var gas=c.gas.snapshot();int energy=((PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2))).storedJoules();
                PowerNetworkSavedData.get(l).clear(s.panel);
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);
                AirlockManager.tick(l,c);
                h.assertTrue(Arrays.equals(gas,c.gas.snapshot())&&c.waitingForPower
                        &&((PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2))).storedJoules()==energy,
                        "Refreshing a real saved-link removal stops gas and energy before delivery");
            } finally {removeListener(l,listener);}
        } finally {l.getDataStorage().set(PowerNetworkSavedData.NAME,previous);PowerNetworks.stopped(null);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockKeepsGrantThroughUnrelatedChunkUpdates(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var statuses=new ArrayList<com.frozendawn.network.AirlockStatusPayload>();
        var listener=soundListener(l,s.center,"unrelated_chunks",new ArrayList<>(),statuses);
        try {
            var p=h.absolutePos(new BlockPos(28,3,28));h.assertTrue(l.isLoaded(p),"Notification probe uses an already loaded chunk");
            var chunk=l.getChunkAt(p);h.assertTrue(!chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(s.panel)),"Unrelated notification is outside the pump chunk");
            AirlockManager.start(l,s.chamber);paidTick(l,s.chamber);
            PowerNetworks.chunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));nextPaidTickWithoutAllocation(h,s,statuses);
            PowerNetworks.chunkLoad(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false));nextPaidTickWithoutAllocation(h,s,statuses);
            PowerNetworks.update(l);nextPaidTickWithoutAllocation(h,s,statuses);
            PowerNetworks.chunkUnload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(l.getChunkAt(s.panel)));
            var gas=s.chamber.gas.snapshot();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);AirlockManager.tick(l,s.chamber);
            h.assertTrue(Arrays.equals(gas,s.chamber.gas.snapshot())&&s.chamber.waitingForPower,"Affected chunk notification revokes credit before disconnection");
        } finally {removeListener(l,listener);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockCombinesCycleDirectionProgressAndPowerState(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var store=(PowerStorageBlockEntity)l.getBlockEntity(s.panel.above(2));
        var statuses=new ArrayList<com.frozendawn.network.AirlockStatusPayload>();
        var listener=soundListener(l,s.center,"combined_status",new ArrayList<>(),statuses);
        try {
            for(boolean filling:new boolean[]{false,true}) {
                store.removeItem(0,1);
                String waiting=filling?"waiting_to_fill":"waiting_to_vent";
                h.assertTrue(AirlockManager.start(l,c).equals(waiting),"Unpowered initial request names its intended cycle");
                if(filling)c.gas.fill(c.gas.capacity()-c.gas.reserve());
                var cell=new ItemStack(ModItems.POWER_CELL.get());PowerCellItem.setEnergy(cell,2000);store.setItem(0,cell);
                AirlockManager.start(l,c);
                for(int i=0;i<80&&c.gas.elapsed()<20;i++)paidTick(l,c);
                h.assertTrue(c.gas.elapsed()==20,"Finite supply reaches exactly25percent before pausing");
                statuses.clear();var gas=c.gas.snapshot();
                for(int i=0;i<25;i++)paidTick(l,c);
                var expected=filling?com.frozendawn.network.AirlockStatusPayload.Status.WAITING_TO_FILL:com.frozendawn.network.AirlockStatusPayload.Status.WAITING_TO_VENT;
                h.assertTrue(!statuses.isEmpty()&&statuses.stream().allMatch(p->p.status()==expected&&p.percent()==25),
                        "Every paused HUD packet combines the right direction, frozen progress and waiting state");
                h.assertTrue(Arrays.equals(gas,c.gas.snapshot())&&AirlockManager.status(c).equals(waiting),"Shift-read and periodic status agree without moving paused gas");
                statuses.clear();PowerCellItem.setEnergy(cell,50000);store.setItem(0,cell);
                for(int i=0;i<40&&c.gas.elapsed()==20;i++)paidTick(l,c);
                h.assertTrue(c.gas.elapsed()>20&&!statuses.isEmpty()&&statuses.getLast().status()==(filling
                        ?com.frozendawn.network.AirlockStatusPayload.Status.PRESSURIZING:com.frozendawn.network.AirlockStatusPayload.Status.DEPRESSURIZING)
                        &&statuses.getLast().percent()==c.gas.elapsed()*100/AirlockCycle.DURATION,
                        "The first resumed paid tick replaces waiting with current cycle progress");
                finish(l,c);
            }
        } finally {removeListener(l,listener);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockGeometryEditsRepairSavedLocksWithoutMintingGas(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;var panel=(AirlockControllerBlockEntity)l.getBlockEntity(s.panel);
        h.assertTrue(AirlockManager.emergencyVent(l,s.panel,Direction.SOUTH),"Seed a genuinely empty chamber through its mechanical valve");
        c.gas.fill(1200);int reserve=c.gas.reserve();
        var stale=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        // A glass-backed wall opening adds a sealed pocket, exactly like the owner's edit.
        var wall=s.center.south(2).above();l.setBlock(wall.south(),Blocks.GLASS.defaultBlockState(),3);
        l.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
        var current=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(current.cells.size()==28&&current.gas.capacity()==2800&&current.gas.air()==0&&!current.gas.cycling(),"Changed chamber volume cancels the old cycle and cannot create gas");
        h.assertTrue(current.gas.reserve()==reserve&&current.panels.contains(s.panel),"Rebuilt chamber retains finite reserve and its panel");
        h.assertFalse(RoomAtmosphere.hasAir(l,s.center),"Geometry edits cannot borrow automatic room air");
        h.assertTrue(AirlockManager.canOpen(l,s.outer)&&!AirlockManager.canOpen(l,s.inner),"Empty chamber can exit to vacuum while the pressurized base remains protected");
        // Restore the actual old saved record against the new blocks: opening alone must heal it.
        l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(stale,l.registryAccess()));panel.assign(c.id);
        h.assertTrue(AirlockManager.canOpen(l,s.outer),"An old persisted geometry/cycle cannot leave a permanent door lock");
        current=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(current.cells.size()==28&&current.gas.reserve()==reserve&&current.gas.air()==0,"Saved-record repair neither refunds moved gas nor duplicates reserve");
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.outer.above()),s.outer.above(),true);
        h.assertTrue(l.getBlockState(s.outer).getValue(DoorBlock.OPEN)&&l.getBlockState(s.outer.above()).getValue(DoorBlock.OPEN),"Real upper-half interaction opens both halves after reconciliation");
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.outer),s.outer,false);
        long previousId=current.id;var inside=current.cells.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
        l.setBlock(inside,Blocks.IRON_BLOCK.defaultBlockState(),3);
        current=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(current.id!=previousId&&current.cells.size()==27&&current.gas.reserve()==reserve,"Interior placement updates canonical chamber/panel identity without losing reserve");
        l.setBlock(inside,Blocks.AIR.defaultBlockState(),3);current=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(current.cells.size()==28&&current.gas.reserve()==reserve&&AirlockManager.canOpen(l,s.outer),"Interior removal restores volume and keeps the exit usable");
        var saved=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var restored=AirlockSavedState.load(saved,l.registryAccess()).byId(current.id);
        h.assertTrue(restored.cells.equals(current.cells)&&Arrays.equals(restored.gas.snapshot(),current.gas.snapshot()),"Reconciled geometry and finite gas survive the existing save format");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockSealedExpansionConservesGasAndDilutesDensity(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var panel=(AirlockControllerBlockEntity)l.getBlockEntity(s.panel);s.chamber.gas.fill(1000);
        var hole=s.center.south(2).above();l.setBlock(hole.south(),Blocks.GLASS.defaultBlockState(),3);
        int total=s.chamber.gas.air()+s.chamber.gas.reserve();l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        var c=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(c.cells.size()==28&&c.gas.air()==2700&&c.gas.reserve()==1000,"Sealed 27-to28 expansion preserves chamber gas and independent reserve");
        h.assertTrue(Math.abs(c.gas.density()-27.0/28)<1e-12&&RoomAtmosphere.hasAir(l,s.center),"Density drops by27/28 without turning remaining air into vacuum");
        var solid=s.center.above();l.setBlock(solid,Blocks.IRON_BLOCK.defaultBlockState(),3);c=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(c.gas.air()+c.gas.reserve()==total&&c.gas.capacity()==2700,"Displacing a cell conserves gas through compression");
        l.setBlock(solid,Blocks.AIR.defaultBlockState(),3);c=AirlockSavedState.get(l).byId(panel.chamberId());
        h.assertTrue(c.gas.air()+c.gas.reserve()==total&&c.cells.size()==28,"Removing that solid block cannot create replacement gas");
        h.assertTrue(AirlockManager.canOpen(l,s.inner),"A27/28 density is already within the same5percent base-door tolerance");
        AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(c.gas.air()==0&&c.gas.reserve()==3430&&c.gas.lost()==270,"Cycling from a matching base pressure recovers exact existing gas toward exterior vacuum");
        AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(c.gas.air()==2800&&c.gas.reserve()==630&&AirlockManager.canOpen(l,s.inner),"Returning to the actual nominal base pays exactly2800units after the real recovery loss");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockGlassPocketMergesAndSplitsWithoutGasLoss(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var panel=(AirlockControllerBlockEntity)l.getBlockEntity(s.panel);
        var hole=s.center.south(2).above();var pocket=hole.south();
        for(var dir:Direction.values())l.setBlock(pocket.relative(dir),Blocks.GLASS.defaultBlockState(),3);
        l.setBlock(pocket,Blocks.AIR.defaultBlockState(),3);RoomAirState.get(l).refill(Set.of(pocket));
        h.assertTrue(RoomAtmosphere.view(l,pocket).geometry().cells().size()==1&&RoomAtmosphere.hasAir(l,pocket),"Pocket is independently sealed and starts with100units");
        int before=s.chamber.gas.air()+100;l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        var saved=AirlockSavedState.get(l);var c=saved.byId(panel.chamberId());
        h.assertTrue(c.cells.size()==29&&c.gas.air()==before,"Merged chamber plus pocket preserves total gas; removed glass contributes none");
        h.assertTrue(Math.abs(c.gas.density()-28.0/29)<1e-12,"Combined gas spreads across both volumes and the new opening");
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);c=saved.byId(panel.chamberId());
        h.assertTrue(c.cells.size()==27&&!c.cells.contains(pocket)&&saved.measuredPocketGas(pocket)>0,
                "Closing the pocket connection must actually retain the door-bounded chamber and separate finite pocket gas");
        h.assertTrue(c.gas.air()+saved.measuredPocketGas(pocket)==before,"Separating the pocket stores its share rather than duplicating it");
        var nbt=saved.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        h.assertTrue(AirlockSavedState.load(nbt,l.registryAccess()).measuredPocketGas(pocket)==saved.measuredPocketGas(pocket),"Finite pocket gas survives save reconstruction");
        RoomAirState.get(l).evacuate(Set.of(pocket));h.assertTrue(saved.measuredPocketGas(pocket)==0,"A later real pocket vent cannot leave reusable stored gas");
        var emptyUnowned=s.center.north(7).above(6);RoomAirState.get(l).evacuate(Set.of(emptyUnowned));
        h.assertFalse(RoomAtmosphere.knownExteriorAt(l,emptyUnowned),"An empty unowned cell is not exterior evidence without a sky observation or registered endpoint");
        RoomAirState.get(l).refill(Set.of(pocket));h.assertTrue(saved.measuredPocketGas(pocket)==100,"A real external refill restores that pocket's finite gas");
        l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);c=saved.byId(panel.chamberId());
        h.assertTrue(c.gas.air()==before,"Reopening the same pocket cannot multiply gas");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockReservoirEvidenceUsesTheFarRoomsOwnPressure(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var previous=RoomAtmosphere.view(l,s.base).geometry().cells();
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.inner),s.inner,true);
        h.assertTrue(l.getBlockState(s.inner).getValue(DoorBlock.OPEN),"Two pressurized rooms genuinely connect through a real two-block doorway");
        try {
            var method=RoomAtmosphere.class.getDeclaredMethod("lowBackgroundConnection",ServerLevel.class,Set.class);method.setAccessible(true);
            h.assertFalse((boolean)method.invoke(null,l,previous),"The new doorway cells cannot override the far room's known positive pressure");
            s.chamber.gas.vent();RoomAirState.get(l).evacuate(s.chamber.cells);
            h.assertTrue((boolean)method.invoke(null,l,previous),"A genuinely evacuated far room supplies lower-pressure reservoir evidence");
            ApocalypseState.get(l.getServer()).setApocalypseTicks(0,l.getServer());CombustionAtmosphere.reset();
            h.assertFalse((boolean)method.invoke(null,l,previous),"Early-phase ambient conditions cannot become a vacuum reservoir");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockExitsLargeVacuumCavesWithoutGuessingUnloadedPressure(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;
        var original=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try {
        // Closed long cavern exceeds the room model's range, matching deep natural cave behavior.
        for(int x=8;x<=62;x++)for(int y=-1;y<=2;y++)for(int z=-2;z<=2;z++){
            boolean shell=y==-1||y==2||Math.abs(z)==2||x==62;
            var pos=s.base.offset(x,y,z);original.put(pos,l.getBlockState(pos));
            l.setBlock(pos,(shell?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
        }
        var outside=RoomAtmosphere.inspectAirlockPartition(l,c.outside,12000);
        h.assertTrue(outside.seal()==RoomAtmosphere.Seal.UNKNOWN&&outside.exceededBounds()&&RoomAtmosphere.knownExteriorAt(l,c.outside)&&!RoomAtmosphere.hasAir(l,c.outside),"Budget alone stays unknown; this far side separately has recorded exterior exposure");
        var missing=s.base.offset(4096,0,4096);h.assertTrue(!l.isLoaded(missing),"Guard probe uses an actually unloaded chunk");
        var unknown=RoomAtmosphere.inspectAirlockPartition(l,missing,12000);
        h.assertTrue(unknown.seal()==RoomAtmosphere.Seal.UNKNOWN&&!unknown.exceededBounds()&&!l.isLoaded(missing),"Unloaded query stays unknown and never loads its chunk");
        for(int i=0;i<20&&!PowerNetworks.component(l,s.panel).contains(s.panel.above(2));i++)PowerNetworks.update(l);
        h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent")&&c.gas.cycling(),"Real powered cave airlock starts");finish(l,c);
        h.assertTrue(!c.gas.cycling()&&c.gas.air()==0&&AirlockManager.canOpen(l,s.outer),"Evacuated outer door opens into modeled-vacuum cave");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&!AirlockManager.canOpen(l,s.inner),"Base stays pressurized and inner door refuses");
        var baseId=RoomAtmosphere.view(l,s.base).id();RoomThermalManager.temperatureAt(l,s.base);
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.outer),s.outer,true);
        h.assertTrue(l.getBlockState(s.outer).getValue(DoorBlock.OPEN)&&!RoomAtmosphere.hasAir(l,s.center),"Opening into a loaded deep cave stays vacuum");
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.outer),s.outer,false);
        c.gas.fill(270);AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(c.gas.breathable()&&RoomAtmosphere.hasAir(l,s.center),"Paid refill restores air before a real cave breach");
        var hole=s.outer.south();original.putIfAbsent(hole,l.getBlockState(hole));l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(RoomAtmosphere.inspect(l,s.center).seal()==RoomAtmosphere.Seal.OPEN&&c.gas.air()==0&&!RoomAtmosphere.hasAir(l,s.center),"A real shell hole into a cave vents chamber gas and shared room pressure");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&AirlockManager.canOpen(l,s.outer),"Actual cave breach keeps the vacuum-side door usable and base isolated");
        original.putIfAbsent(s.inner,l.getBlockState(s.inner));original.putIfAbsent(s.inner.above(),l.getBlockState(s.inner.above()));
        l.setBlock(s.inner,Blocks.AIR.defaultBlockState(),3);
        h.assertFalse(RoomAtmosphere.hasAir(l,s.base),"Breaking the inner door now vents the connected main base into the cave");
        h.assertTrue(RoomAirState.get(l).isDepleted(Set.of(s.base)),"Cave breach persists depletion rather than suspending old air");
        RoomThermalManager.tickLevel(l);
        var thermal=RoomThermalManager.snapshots(l).stream().filter(r->r.id()==baseId).findFirst().orElseThrow();
        h.assertTrue(!thermal.sealed()&&!thermal.suspended()&&!thermal.airPresent(),"Thermal state drops the breached base air rather than freezing a pressurized room");
        ApocalypseState.get(l.getServer()).setApocalypseTicks(0,l.getServer());CombustionAtmosphere.reset();
        h.assertTrue(!CombustionAtmosphere.isVacuum(l)&&CombustionAtmosphere.canBurnAt(l,c.outside),"Early atmosphere remains ambient despite the same large cave geometry");
        }finally{original.forEach((pos,state)->l.setBlock(pos,state,3));}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockAuthoredBaseHasValidChamberAndPowerPath(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var base=h.absolutePos(new BlockPos(9,3,10));
        try{var method=com.frozendawn.labbridge.LabPoweredAirlock.class.getDeclaredMethod("build",ServerLevel.class,BlockPos.class);method.setAccessible(true);method.invoke(null,l,base);}catch(ReflectiveOperationException e){throw new AssertionError("Authored base construction must succeed",e);}
        var c=AirlockSavedState.get(l).at(base.east(5));h.assertTrue(c!=null&&c.cells.size()==27&&c.panels.size()==1,"Authored inside controller resolves 27-cell chamber");
        var panel=base.offset(3,1,-1);var be=(AirlockControllerBlockEntity)l.getBlockEntity(panel);be.onLoad();h.assertTrue(AirlockManager.resolve(l,be)==c,"Base-side panel controls the same chamber");
        var core=base.offset(-1,0,1);var bank=base.offset(-1,0,-1);var relay=base.offset(0,1,-1);
        for(var edge:List.of(new BlockPos[]{core,bank},new BlockPos[]{bank,relay},new BlockPos[]{relay,panel}))h.assertTrue(PowerNetworks.link(l,edge[0],edge[1])==PowerLinkRules.Result.OK,"Authored base power path stays within its sealed side");
        var hp=base.offset(1,0,1);var heater=(ResistiveHeaterBlockEntity)l.getBlockEntity(hp);heater.onLoad();
        h.assertTrue(PowerNetworks.link(l,relay,hp)==PowerLinkRules.Result.OK,"Cold RH5 shares authored power network");
        RoomThermalManager.temperatureAt(l,base);var generator=(GeothermalCoreBlockEntity)l.getBlockEntity(core);generator.setPowerShare(10);generator.setHeatShare(0);
        for(int i=0;i<20&&!PowerNetworks.component(l,panel).contains(core);i++)PowerNetworks.update(l);
        h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent")&&c.gas.cycling(),"Settled authored connection accepts a powered start");for(int i=0;i<25;i++)paidTick(l,c);PowerNetworks.account(l);
        h.assertTrue(c.gas.elapsed()>0&&heater.requestWatts()>0&&PowerNetworks.heaterGrant(l,hp)==0,"Authored 2 kW base serves airlock before cold RH5: progress="+c.gas.elapsed()+" request="+heater.requestWatts()+" grant="+PowerNetworks.heaterGrant(l,hp));
        generator.setPowerShare(50);generator.setHeatShare(0);for(int i=0;i<20;i++)paidTick(l,c);PowerNetworks.account(l);
        h.assertTrue(PowerNetworks.heaterGrant(l,hp)>0,"Extra headroom restores heater while pump continues");
    });}
    @GameTest(template=GameTestTemplates.EMPTY,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockReplayParsesAndRejectsOtherWorlds(GameTestHelper h){
        for(var name:List.of("load","tick","setup","controls","cave","surface","steps"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("powered_airlock",name)).isPresent(),"Real parser loads guarded powered-airlock helper "+name);
        h.assertTrue(com.frozendawn.labbridge.LabPoweredAirlock.prepare(h.getLevel().getServer().createCommandSourceStack())==0,"Fixture cannot edit another world or run without the owner player");h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void poweredAirlockPriorityAndMonitorStayLegible(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var s=scene(h);var c=s.chamber;var source=s.panel.above(2);l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.setPowerShare(10);core.setHeatShare(0);
        h.assertTrue(PowerNetworks.link(l,source,s.panel)==PowerLinkRules.Result.OK,"Pump has real 2 kW Core network");
        var lamp=s.panel.east();l.setBlock(lamp,ModBlocks.PANEL_LIGHT.get().defaultBlockState(),3);h.assertTrue(PowerNetworks.link(l,source,lamp)==PowerLinkRules.Result.OK,"Lamp shares network");
        AirlockManager.start(l,c);for(int i=0;i<25;i++)paidTick(l,c);PowerNetworks.account(l);
        var reading=PowerNetworks.monitorReading(l,s.panel);
        h.assertTrue(c.gas.elapsed()>0&&!PowerNetworks.powered(l,lamp)&&reading.demandWatts()==2100,"Airlock's full 2 kW takes priority over light: progress="+c.gas.elapsed()+" reading="+reading);
        core.setPowerShare(9);core.setHeatShare(0);int progress=c.gas.elapsed();
        for(int i=0;i<40;i++)paidTick(l,c);
        h.assertTrue(c.gas.elapsed()==progress&&PowerNetworks.monitorReading(l,s.panel).shed().stream().anyMatch(load->load.kind()==5),"Below 2 kW pump waits with friendly monitor device type");
        core.setPowerShare(11);core.setHeatShare(0);for(int i=0;i<40;i++)paidTick(l,c);
        h.assertTrue(c.gas.elapsed()>progress&&PowerNetworks.powered(l,lamp),"2.2 kW restores pump and lamp");
    });}
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
        h.assertTrue(AirlockManager.start(l,s.chamber).equals("waiting_to_vent")&&s.chamber.gas.cycling(),"Closed chamber begins real vent cycle");
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
    private static void connectedRefillCheck(GameTestHelper h,java.util.function.Consumer<Scene> body) {
        check(h,()->{
            var l=h.getLevel();var chambers=AirlockSavedState.get(l);var transfer=OxygenTransferState.get(l);
            var links=PowerNetworkSavedData.get(l);var heat=RoomThermalState.get(l);
            l.getDataStorage().set("frozendawn_airlocks",new AirlockSavedState());
            l.getDataStorage().set(OxygenTransferState.NAME,new OxygenTransferState());
            l.getDataStorage().set(PowerNetworkSavedData.NAME,new PowerNetworkSavedData());
            l.getDataStorage().set(RoomThermalState.NAME,new RoomThermalState());
            RoomOxygenManager.reset();RoomThermalManager.reset();PowerNetworks.stopped(null);
            try {body.accept(scene(h));}
            finally {
                l.getDataStorage().set("frozendawn_airlocks",chambers);l.getDataStorage().set(OxygenTransferState.NAME,transfer);
                l.getDataStorage().set(PowerNetworkSavedData.NAME,links);l.getDataStorage().set(RoomThermalState.NAME,heat);
                RoomOxygenManager.reset();RoomThermalManager.reset();PowerNetworks.stopped(null);
            }
        });
    }
    private static void refillSecond(GameTestHelper h,Scene s,boolean warm) {
        var l=h.getLevel();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+20);
        AirlockManager.tick(l,AirlockSavedState.get(l).byId(s.chamber.id));
        RoomAtmosphere.tickLevel(l);RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        if(warm) {
            RoomThermalManager.temperatureAt(l,s.base);var heat=RoomThermalState.get(l);
            for(var room:heat.rooms.values())if(room.cells.contains(s.base)) {
                room.airEnergy=com.frozendawn.thermal.RoomHeatMath.energy(room.airCapacity(),24);
                for(var pos:room.structure){var material=heat.materials.get(pos);if(material!=null)material.energy=com.frozendawn.thermal.RoomHeatMath.energy(material.capacity,24);}
            }
        }
        for(int i=0;i<3;i++)RoomOxygenManager.tickLevel(l);
    }
    private static BlockPos joinEmptyChamber(GameTestHelper h,Scene s,boolean emptyBase) {
        var l=h.getLevel();s.chamber.gas.vent();s.chamber.gas.fill(500);RoomAirState.get(l).evacuate(s.chamber.cells);
        var base=RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        if(emptyBase)RoomAirState.get(l).evacuate(base.geometry().cells());
        var hole=s.inner.north();l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        var joined=RoomAtmosphere.view(l,s.base);
        h.assertTrue(joined!=null&&joined.geometry().cells().contains(s.center)&&joined.geometry().seal()==RoomAtmosphere.Seal.SEALED,
                "Actual internal wall opening joins chamber and base without opening the exterior");
        h.assertTrue((!emptyBase||s.chamber.gas.air()==0)&&s.chamber.gas.reserve()==500,"Internal join cannot mint gas or spend its reserve");
        return hole;
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockInternalBreachRefillsFromSharedProductionAndPersists(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var hole=joinEmptyChamber(h,s,true);var c=s.chamber;
        var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(100);core.setHeatShare(0);
        for(int i=0;i<6;i++)refillSecond(h,s,false);
        h.assertTrue(c.gas.air()==0&&c.gas.reserve()==500&&!RoomAtmosphere.hasAir(l,s.base),"No production means no recovery despite a sealed internal connection");
        core.setPowerShare(0);core.setHeatShare(0);
        for(int i=0;i<2;i++)refillSecond(h,s,false);
        var cells=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==80&&c.gas.reserve()==500&&!RoomAtmosphere.hasAir(l,s.base),
                "Before the ordinary recovery delay, actual production funds only80 units and cannot restore atmosphere early");
        int air=c.gas.air();for(int i=0;i<20;i++)RoomOxygenManager.tickLevel(l);
        h.assertTrue(c.gas.air()==air&&c.gas.reserve()==500,"Repeated accounting does not duplicate production");
        var probe=s.base.above(2);l.setBlock(probe,Blocks.STONE.defaultBlockState(),3);
        cells=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&AirlockSavedState.get(l).roomGas(l,cells)==80,
                "Placing a block during an empty-room refill conserves gas without declaring premature recovery");
        l.setBlock(probe,Blocks.AIR.defaultBlockState(),3);cells=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&AirlockSavedState.get(l).roomGas(l,cells)==80,
                "Removing the block also preserves the partial refill and its airless state");
        for(int i=0;i<7;i++)refillSecond(h,s,false);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&AirlockSavedState.get(l).roomGas(l,cells)==360&&c.gas.reserve()==500,
                "Normal producer recovery restores atmosphere after its delay while retaining exactly360 funded units, not nominal room inventory");
        air=c.gas.air();
        var saved=AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(saved,l.registryAccess()));c=AirlockSavedState.get(l).byId(c.id);
        RoomAtmosphere.reset();RoomOxygenManager.reset();
        h.assertTrue(c.gas.air()==air&&c.gas.reserve()==500,"Partial connected-room gas and reserve survive the real saved codec");
        for(int i=0;i<400&&AirlockManager.roomAirPending(l,cells);i++)refillSecond(h,s,false);
        h.assertTrue(c.gas.air()==2700&&c.gas.reserve()==500&&RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center),
                "Funded chamber gas permits the connected sealed room to recover");
        int total=AirlockSavedState.get(l).roomGas(l,cells);
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);var base=RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(s.panel));
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,base.geometry().cells())+c.gas.air()==total&&c.gas.reserve()==500&&RoomAtmosphere.hasAir(l,s.center),"Resealing retains all funded air through compression without a second credit");
        l.setBlock(s.center.south(2),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(c.gas.air()==0&&c.gas.reserve()==500,"A genuine exterior breach still vents chamber air and leaves reserve separate");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockInternalBreachPixiePaysAndPartialResealStopsRoomFeed(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var hole=joinEmptyChamber(h,s,true);var c=s.chamber;
        var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(100);core.setHeatShare(0);
        var intake=s.base.south();l.setBlock(intake,ModBlocks.PIXIE.get().defaultBlockState(),3);
        var pixie=(PixieBlockEntity)l.getBlockEntity(intake);pixie.onLoad();pixie.setItem(0,new ItemStack(net.minecraft.world.item.Items.ICE));
        h.assertTrue(PowerNetworks.link(l,source,intake)==PowerLinkRules.Result.OK,"Room PIXIE has actual grid power");PowerNetworks.update(l);
        long water=pixie.availableWater();refillSecond(h,s,true);
        var cells=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==40&&c.gas.reserve()==500&&water-pixie.availableWater()==40000&&pixie.activity()==PixieBlockEntity.ROOM,
                "Paid PIXIE output becomes exact chamber gas and is labeled room supply: air="+c.gas.air()+" output="+pixie.outputWatts());
        core.setPowerShare(50);core.setHeatShare(0);water=pixie.availableWater();refillSecond(h,s,true);
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==80&&pixie.outputWatts()==0&&pixie.availableWater()==water,
                "Existing Core O2 covers the same gas request without unnecessary PIXIE power or water");
        core.setPowerShare(0);core.setHeatShare(100);water=pixie.availableWater();refillSecond(h,s,true);
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==80&&pixie.availableWater()==water,"No power means no gas or water debit");
        core.setPowerShare(100);core.setHeatShare(0);refillSecond(h,s,true);
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==120&&c.gas.reserve()==500,"Production resumes only new actual gas");
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        int isolated=c.gas.air();
        for(int i=0;i<7;i++)refillSecond(h,s,true);
        h.assertTrue(c.gas.air()==isolated&&c.gas.reserve()==500&&!c.gas.breathable()&&RoomAtmosphere.hasAir(l,s.base)&&!RoomAtmosphere.hasAir(l,s.center),
                "After the normal delay, the paid PIXIE restores only the sealed base; the isolated partial chamber still requires its pump");
        var baseCells=RoomAtmosphere.view(l,s.base).geometry().cells();
        for(int i=0;i<400&&AirlockSavedState.get(l).roomAirRequest(l,baseCells)>0;i++)refillSecond(h,s,true);
        h.assertTrue(AirlockSavedState.get(l).roomAirRequest(l,baseCells)==0&&c.gas.air()==isolated&&RoomAtmosphere.hasAir(l,s.base),
                "Spare production restores the diluted base after repair without feeding the isolated chamber");
        l.setBlock(source,ModBlocks.CELL_DOCK.get().defaultBlockState(),3);
        var dock=(PowerStorageBlockEntity)l.getBlockEntity(source);dock.onLoad();dock.setItem(0,PowerCellItem.charged(500000));
        h.assertTrue(PowerNetworks.link(l,source,intake)==PowerLinkRules.Result.OK,"PIXIE can run without a Core from a real finite dock");PowerNetworks.update(l);
        var pig=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,l);pig.setNoAi(true);pig.setPos(s.base.getCenter());l.addFreshEntity(pig);
        try {
            water=pixie.availableWater();refillSecond(h,s,true);
            h.assertTrue(pixie.outputWatts()==1300&&pixie.activity()==PixieBlockEntity.ROOM&&water-pixie.availableWater()==13000,
                    "Coreless normal room supply keeps its correct activity label and actual water cost");
        } finally {pig.discard();}
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockSealedBaseSharesAirWithEmptyChamberAndConservesGas(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var initial=RoomAtmosphere.view(l,s.base).geometry().cells();
        int before=initial.size()*AirlockCycle.UNITS_PER_CELL;
        var hole=joinEmptyChamber(h,s,false);var saved=AirlockSavedState.get(l);var c=s.chamber;
        var cells=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center),
                "Opening an internal wall between a pressurized base and empty chamber cannot turn either side into vacuum");
        h.assertTrue(c.gas.air()>0&&c.gas.air()<c.gas.capacity()&&saved.roomGas(l,cells)==before&&c.gas.reserve()==500,
                "Existing base air dilutes into the chamber with exact total conservation and no reserve debit");
        for(int i=0;i<6;i++)refillSecond(h,s,false);
        h.assertTrue(saved.roomGas(l,cells)==before&&RoomAtmosphere.hasAir(l,s.center),"Retained air needs no producer to remain present");
        var encoded=saved.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(encoded,l.registryAccess()));saved=AirlockSavedState.get(l);c=saved.byId(c.id);
        RoomAtmosphere.reset();RoomOxygenManager.reset();
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center)&&saved.roomGas(l,cells)==before,
                "Diluted base and chamber gas survives reload without nominal-air credits");
        for(int i=0;i<3;i++) {
            l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);
            var base=RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
            h.assertTrue(saved.roomGas(l,base.geometry().cells())+c.gas.air()==before&&c.gas.reserve()==500&&RoomAtmosphere.hasAir(l,s.center),
                    "Repair compresses and separates the existing gas without loss or duplication");
            h.assertTrue(AirlockManager.canOpen(l,s.inner)&&!AirlockManager.canOpen(l,s.outer),
                    "Equal diluted pressures permit the inner door while exterior vacuum remains blocked");
            l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);cells=RoomAtmosphere.view(l,s.base).geometry().cells();
            h.assertTrue(saved.roomGas(l,cells)==before&&RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center),
                    "Repeated internal edits preserve air and its finite total");
        }
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);var base=RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        int baseGas=saved.roomGas(l,base.geometry().cells());l.setBlock(s.center.south(2),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(c.gas.air()==0&&saved.roomGas(l,base.geometry().cells())==baseGas&&RoomAtmosphere.hasAir(l,s.base),
                "A real exterior breach still vents the isolated chamber while preserving base air");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockRetainedAirPixieTopupKeepsGaugeAndActivityCorrect(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(100);core.setHeatShare(0);
        var intake=s.base.south();l.setBlock(intake,ModBlocks.PIXIE.get().defaultBlockState(),3);
        var pixie=(PixieBlockEntity)l.getBlockEntity(intake);pixie.onLoad();pixie.setItem(0,new ItemStack(net.minecraft.world.item.Items.ICE));
        h.assertTrue(PowerNetworks.link(l,source,intake)==PowerLinkRules.Result.OK,"Real PIXIE power path is wholly inside the base");
        int before=RoomAtmosphere.view(l,s.base).geometry().cells().size()*AirlockCycle.UNITS_PER_CELL;
        joinEmptyChamber(h,s,false);var cells=RoomAtmosphere.view(l,s.base).geometry().cells();long water=pixie.availableWater();
        refillSecond(h,s,true);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center)&&RoomOxygenManager.readingAt(l,s.base).mode()==com.frozendawn.oxygen.OxygenStatus.Mode.SUPPLIED,
                "The gauge reads supplied while an intact connected base and chamber retain their air");
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==before+40&&water-pixie.availableWater()==40000&&pixie.activity()==PixieBlockEntity.ROOM,
                "Only paid PIXIE production tops up the shared room; activity says room supply rather than storage");
        int gas=AirlockSavedState.get(l).roomGas(l,cells);water=pixie.availableWater();
        for(int i=0;i<20;i++)RoomOxygenManager.tickLevel(l);
        h.assertTrue(AirlockSavedState.get(l).roomGas(l,cells)==gas&&pixie.availableWater()==water,"Repeated readings cannot duplicate gas or water costs");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockRepairThenPumpKeepsBaseAirAndSource(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(0);core.setHeatShare(0);
        var hole=joinEmptyChamber(h,s,false);var c=s.chamber;
        for(int i=0;i<400&&AirlockManager.roomAirPending(l,RoomAtmosphere.view(l,s.base).geometry().cells());i++)refillSecond(h,s,false);
        h.assertTrue(c.gas.breathable()&&RoomAtmosphere.hasAir(l,s.base),"Actual Core production has filled the still joined sealed volume");
        // Repair and immediately start the pump before any player, gauge or thermostat rescan.
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);
        h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_vent"),"A full repaired chamber can start its ordinary paid vent");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base),"Evacuating only the repaired chamber must not mark its old merged parent/base airless");
        var base=RoomAtmosphere.view(l,s.base).geometry().cells();int gas=AirlockSavedState.get(l).roomGas(l,base);
        h.assertTrue(gas>base.size()*90,"The base keeps its measured gas after the physical split");
        finish(l,c);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&AirlockSavedState.get(l).roomGas(l,base)==gas,
                "An isolated chamber pump cannot remove or remix base gas through stale membership");
        for(int i=0;i<3;i++)RoomOxygenManager.tickLevel(l);
        var reading=RoomOxygenManager.readingAt(l,s.base);
        h.assertTrue(reading.mode()==com.frozendawn.oxygen.OxygenStatus.Mode.SUPPLIED&&reading.supply()>0,
                "The working source stays in the pressurized base after wall repair and chamber evacuation: "+reading);
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockCyclesToActualDilutedBasePressure(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var hole=joinEmptyChamber(h,s,false);var c=s.chamber;
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);var base=RoomAtmosphere.view(l,s.base).geometry().cells();RoomAtmosphere.view(l,s.center);
        var saved=AirlockSavedState.get(l);int baseGas=saved.roomGas(l,base);
        h.assertTrue(c.gas.air()>0&&c.gas.air()<c.gas.capacity()&&AirlockManager.canOpen(l,s.inner),"Diluted but matching air initially allows return to base");
        c.gas.fill(6000);int before=c.gas.air()+c.gas.reserve();
        AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(c.gas.air()==0&&AirlockManager.canOpen(l,s.outer),"The controller vents a chamber already matched to the diluted base");
        h.assertTrue(c.gas.reserve()+c.gas.lost()==before,"Recovery keeps exact gas including the existing pump loss");
        before=c.gas.reserve();AirlockManager.start(l,c);finish(l,c);
        int target=(int)Math.round((double)baseGas*c.gas.capacity()/(base.size()*100));
        h.assertTrue(c.gas.air()==target&&c.gas.air()+c.gas.reserve()==before,"Refill targets actual base density and debits only that exact amount");
        h.assertTrue(RoomAtmosphere.hasAir(l,s.center)&&AirlockManager.canOpen(l,s.inner)&&!AirlockManager.canOpen(l,s.outer),
                "The completed partial-pressure chamber has air and permits return to base without opening the vacuum side");
        h.assertTrue(saved.roomGas(l,base)==baseGas,"Equalizing an isolated chamber never draws gas from the base itself");
        ModBlocks.AIRLOCK_DOOR.get().setOpen(null,l,l.getBlockState(s.inner),s.inner,true);
        var joined=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(l.getBlockState(s.inner).getValue(DoorBlock.OPEN)&&saved.roomGas(l,joined)==baseGas+target,
                "Opening the matched actual door conserves total base plus chamber gas");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockBaseExteriorBreachRefillsWithoutBorrowingChamberGas(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(100);core.setHeatShare(0);
        var hole=joinEmptyChamber(h,s,false);var c=s.chamber;
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        int chamberGas=c.gas.air(),reserve=c.gas.reserve();var breach=s.base.west(3);
        l.setBlock(breach,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&RoomAtmosphere.hasAir(l,s.center)&&c.gas.air()==chamberGas,
                "A real exterior breach empties the base while its closed airlock retains its own air");
        l.setBlock(breach,Blocks.GLASS.defaultBlockState(),3);var base=RoomAtmosphere.view(l,s.base).geometry().cells();
        for(int i=0;i<6;i++)refillSecond(h,s,false);
        var saved=AirlockSavedState.get(l);
        h.assertTrue(saved.roomGas(l,base)==0&&!RoomAtmosphere.hasAir(l,s.base)&&c.gas.air()==chamberGas,
                "Repair with zero production cannot borrow the isolated chamber's gas");
        core.setPowerShare(0);core.setHeatShare(0);
        for(int i=0;i<6;i++)refillSecond(h,s,false);
        int partial=saved.roomGas(l,base);
        h.assertTrue(partial>0&&partial<base.size()*100&&RoomAtmosphere.hasAir(l,s.base)&&c.gas.air()==chamberGas&&c.gas.reserve()>=reserve,
                "Normal Core recovery restores the base before nominal inventory, without changing isolated chamber air; reserve may separately receive spare Core output");
        for(int i=0;i<800&&saved.roomAirRequest(l,base)>0;i++)refillSecond(h,s,false);
        refillSecond(h,s,false);
        h.assertTrue(saved.roomAirRequest(l,base)==0&&RoomAtmosphere.hasAir(l,s.base)&&c.gas.air()==chamberGas,
                "The base recovers when its actual missing gas has been produced");
        var reading=RoomOxygenManager.readingAt(l,s.base);
        h.assertTrue(reading.mode()==com.frozendawn.oxygen.OxygenStatus.Mode.SUPPLIED&&reading.supply()>0&&reading.sources()==1,
                "The recovered base still owns its actual Core source: "+reading);
        c.gas.fill(6000);AirlockManager.start(l,c);finish(l,c);
        h.assertTrue(AirlockManager.canOpen(l,s.inner),"A completed refill allows return to the recovered base after the owner's full breach sequence");
    });}
    private static void independentBaseRecovery(GameTestHelper h,Scene s,boolean pixieSource,boolean emptyChamber) {
        var l=h.getLevel();var hole=joinEmptyChamber(h,s,false);var c=s.chamber;
        l.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);RoomAtmosphere.view(l,s.base);RoomAtmosphere.view(l,s.center);
        if(emptyChamber){AirlockManager.start(l,c);finish(l,c);}
        c.gas.fill(6400);int chamberGas=c.gas.air(),reserve=c.gas.reserve();
        h.assertTrue(emptyChamber?chamberGas==0:RoomAtmosphere.hasAir(l,s.center),"Replay begins with the requested isolated chamber pressure");
        var source=s.base.west();l.setBlock(source,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var core=(GeothermalCoreBlockEntity)l.getBlockEntity(source);core.onLoad();core.setPowerShare(100);core.setHeatShare(0);
        var intake=s.base.south();PixieBlockEntity pixie=null;
        if(pixieSource) {
            l.setBlock(intake,ModBlocks.PIXIE.get().defaultBlockState(),3);pixie=(PixieBlockEntity)l.getBlockEntity(intake);
            pixie.onLoad();pixie.setItem(0,new ItemStack(net.minecraft.world.item.Items.ICE));
        }
        var breach=s.base.west(3);l.setBlock(breach,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&c.gas.air()==chamberGas,"A cave/exterior breach evacuates only the base");
        l.setBlock(breach,Blocks.GLASS.defaultBlockState(),3);var base=RoomAtmosphere.view(l,s.base).geometry().cells();
        var saved=AirlockSavedState.get(l);
        for(int i=0;i<6;i++)refillSecond(h,s,pixieSource);
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==0&&c.gas.air()==chamberGas&&c.gas.reserve()==reserve,
                "Zero actual output cannot restore the repaired base or borrow chamber/reserve gas");
        long water=pixie==null?0:pixie.availableWater();
        if(pixieSource) {
            h.assertTrue(PowerNetworks.link(l,source,intake)==PowerLinkRules.Result.OK,"Only a real powered PIXIE can produce the base's recovery gas");PowerNetworks.update(l);
        } else {core.setPowerShare(0);core.setHeatShare(0);}
        for(int i=0;i<2;i++)refillSecond(h,s,pixieSource);
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==80,"Two seconds cannot bypass the normal delay, despite actual funded gas");
        var probe=s.base.above(2);l.setBlock(probe,Blocks.STONE.defaultBlockState(),3);RoomAtmosphere.view(l,s.base);
        l.setBlock(probe,Blocks.AIR.defaultBlockState(),3);base=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(!RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==80,"Placing/removing a block cannot create gas or prematurely restore the base");
        for(int i=0;i<7;i++)refillSecond(h,s,pixieSource);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==360&&saved.roomGas(l,base)<base.size()*100,
                "Producer recovery completes normally without waiting for, or inventing, nominal room inventory");
        h.assertTrue(c.gas.air()==chamberGas&&c.gas.reserve()==reserve&&RoomAtmosphere.hasAir(l,s.center)!=emptyChamber,
                "Closed chamber pressure and its separate reserve are unchanged by base recovery");
        var reading=RoomOxygenManager.readingAt(l,s.base);
        h.assertTrue(reading.mode()==com.frozendawn.oxygen.OxygenStatus.Mode.SUPPLIED&&reading.supply()>0,"The recovered base gauge reports its actual producer supply: "+reading);
        if(pixie!=null)h.assertTrue(water-pixie.availableWater()==360000&&pixie.activity()==PixieBlockEntity.ROOM,"PIXIE pays water for exactly360 gas units and reports room supply");
        var gasNbt=saved.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var airNbt=RoomAirState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        l.getDataStorage().set("frozendawn_airlocks",AirlockSavedState.load(gasNbt,l.registryAccess()));
        l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(airNbt,l.registryAccess()));RoomAtmosphere.reset();
        saved=AirlockSavedState.get(l);c=saved.byId(c.id);
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==360&&c.gas.air()==chamberGas,"Both real saved codecs retain recovered atmosphere and exact partial gas independently of the chamber");
        l.setBlock(probe,Blocks.STONE.defaultBlockState(),3);RoomAtmosphere.view(l,s.base);
        l.setBlock(probe,Blocks.AIR.defaultBlockState(),3);base=RoomAtmosphere.view(l,s.base).geometry().cells();
        h.assertTrue(RoomAtmosphere.hasAir(l,s.base)&&saved.roomGas(l,base)==360,"Later block updates retain recovery and exact gas without requiring the chamber to fill");
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void coreRestoresBaseWithPressurizedAirlockIndependently(GameTestHelper h){connectedRefillCheck(h,s->independentBaseRecovery(h,s,false,false));}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void coreRestoresBaseWithEmptyAirlockIndependently(GameTestHelper h){connectedRefillCheck(h,s->independentBaseRecovery(h,s,false,true));}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void pixieRestoresBaseWithPressurizedAirlockIndependently(GameTestHelper h){connectedRefillCheck(h,s->independentBaseRecovery(h,s,true,false));}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void pixieRestoresBaseWithEmptyAirlockIndependently(GameTestHelper h){connectedRefillCheck(h,s->independentBaseRecovery(h,s,true,true));}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="airlock_native",timeoutTicks=100)
    public static void airlockCompressedGasBroadcastResumesSavedVentWithoutCrash(GameTestHelper h){connectedRefillCheck(h,s->{
        var l=h.getLevel();var c=s.chamber;var statuses=new ArrayList<com.frozendawn.network.AirlockStatusPayload>();
        var player=soundListener(l,s.center,"compressed_status",new ArrayList<>(),statuses);
        try {
            // Exact finite gas/reserve/mode from the owner's crash save.
            c.gas=AirlockCycle.restore(27,new int[]{2700,5140,2756,2,0,2756,0,0,0});RoomAirState.get(l).evacuate(c.cells);
            PowerNetworks.clear(l,s.panel);c.lastTick=Long.MIN_VALUE;AirlockManager.tick(l,c);
            h.assertTrue(c.gas.air()==2756&&c.gas.reserve()==5140&&c.gas.cycling()&&c.waitingForPower&&statuses.getLast().air()==2756,
                    "Real waiting broadcast accepts saved compressed air without clamping it or losing the cycle");
            var buffer=io.netty.buffer.Unpooled.buffer();
            try {
                var payload=statuses.getLast();com.frozendawn.network.AirlockStatusPayload.STREAM_CODEC.encode(buffer,payload);
                h.assertTrue(com.frozendawn.network.AirlockStatusPayload.STREAM_CODEC.decode(buffer).equals(payload),
                        "Actual compressed status preserves exact gas, capacity, reserve and direction through the wire codec");
            } finally {buffer.release();}
            try {new com.frozendawn.network.AirlockStatusPayload(com.frozendawn.network.AirlockStatusPayload.Status.STATUS,0,-1,2700,0,0);h.fail("Negative gas must still be rejected");}catch(IllegalArgumentException expected){}
            try {new com.frozendawn.network.AirlockStatusPayload(com.frozendawn.network.AirlockStatusPayload.Status.STATUS,0,1,0,0,0);h.fail("A missing chamber cannot report positive gas");}catch(IllegalArgumentException expected){}
            h.assertTrue(PowerNetworks.link(l,s.panel,s.panel.above(2))==PowerLinkRules.Result.OK,"Rebind real finite pump power");
            paidTick(l,c);
            h.assertTrue(c.gas.air()>c.gas.capacity()&&statuses.stream().anyMatch(p->p.status()==com.frozendawn.network.AirlockStatusPayload.Status.DEPRESSURIZING&&p.air()>p.capacity()),
                    "The first paid tick also broadcasts legitimate overpressure to the player");
            finish(l,c);
            h.assertTrue(c.gas.air()==0&&c.gas.reserve()==6400&&c.gas.lost()==1496&&!c.gas.cycling(),
                    "Saved vent completes with exact recovered gas and real overflow loss");
            h.assertTrue(statuses.getLast().status()==com.frozendawn.network.AirlockStatusPayload.Status.EVACUATED,
                    "The final real player packet reports successful evacuation");
        } finally {removeListener(l,player);}
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
        c.gas.fill(c.gas.capacity());h.assertTrue(AirlockManager.start(l,c).equals("waiting_to_fill")&&c.gas.cycling(),"Reserve feeds measured refill");finish(l,c);
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
