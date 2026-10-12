package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphericBreachGameTest {
    @net.minecraft.gametest.framework.BeforeBatch(batch="atmospheric_breach")
    public static void reportRooms(net.minecraft.server.level.ServerLevel level) {
        com.frozendawn.gametest.GameTestReporting.installReporter(level);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="atmospheric_breach_lights")
    public static void reportLights(net.minecraft.server.level.ServerLevel level) {
        com.frozendawn.gametest.GameTestReporting.installReporter(level);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="atmospheric_breach_motion")
    public static void reportMotion(net.minecraft.server.level.ServerLevel level) {
        com.frozendawn.gametest.GameTestReporting.installReporter(level);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="atmospheric_recovery_notices")
    public static void reportRecovery(net.minecraft.server.level.ServerLevel level) {
        com.frozendawn.gametest.GameTestReporting.installReporter(level);
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="atmospheric_airlock_isolation")
    public static void reportAirlockIsolation(net.minecraft.server.level.ServerLevel level) {
        com.frozendawn.gametest.GameTestReporting.installReporter(level);
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="atmospheric_breach", timeoutTicks=100)
    public static void atmosphericGlassRoofAndDoorAreGeometricallySealed(GameTestHelper h) {
        scene(h,()->{
            var level=h.getLevel(); var center=room(h);
            h.assertTrue(RoomAtmosphere.inspect(level,center).seal()==RoomAtmosphere.Seal.SEALED,
                    "Transparent glass encloses real room air without light propagation waits");
            h.assertTrue(TemperatureManager.hasBreathableAir(level,center),"Existing sealed air remains available");
            var door=center.east(3);
            level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),2);
            h.assertTrue(RoomAtmosphere.inspect(level,center).seal()==RoomAtmosphere.Seal.SEALED,"Closed door is an air seal");
            level.setBlock(door,level.getBlockState(door).setValue(BlockStateProperties.OPEN,true),2);
            h.assertTrue(RoomAtmosphere.inspect(level,center).seal()==RoomAtmosphere.Seal.OPEN,"Open door connects real room to exterior");
            level.setBlock(door,Blocks.OAK_FENCE.defaultBlockState(),2);
            h.assertTrue(RoomAtmosphere.inspect(level,center).seal()==RoomAtmosphere.Seal.OPEN,"Fence cannot masquerade as pressure wall");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="atmospheric_breach", timeoutTicks=100)
    public static void atmosphericCoreAndBlastPitRequireConnectedSealedAir(GameTestHelper h) {
        scene(h,()->{
            var level=h.getLevel(); var center=room(h); var core=center.west();
            level.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);
            level.getBlockEntity(core).onLoad();
            h.assertTrue(TemperatureManager.hasOxygenSupport(level,center),"Core supplies its actual closed room");
            var outside=center.south(6);
            h.assertFalse(TemperatureManager.hasOxygenSupport(level,outside),"Core radius does not supply vacuum outside walls");
            h.assertFalse(TemperatureManager.hasBreathableAir(level,outside),"Exterior remains vacuum beside core");
            level.setBlock(center.east(3),Blocks.AIR.defaultBlockState(),3);
            h.assertFalse(TemperatureManager.hasOxygenSupport(level,center),"Breach disables core ambient support immediately");
            h.assertFalse(TemperatureManager.hasBreathableAir(level,center),"Breach evacuates the room");
            BlastPitWarmZoneRegistry.register(level,center);
            try {
                h.assertFalse(TemperatureManager.hasOxygenSupport(level,center),"Blast Pit warmth cannot supply an open vacuum room");
                h.assertTrue(BlastPitWarmZoneRegistry.isInsideWarmZone(level,center),"The heat zone remains independent of air");
            } finally {BlastPitWarmZoneRegistry.unregister(level,center);}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="atmospheric_breach", timeoutTicks=160)
    public static void atmosphericBreachNeedsRecoveryAndPersistsDepletion(GameTestHelper h) {
        var level=h.getLevel();var apocalypse=ApocalypseState.get(level.getServer());long previous=apocalypse.getApocalypseTicks();
        phase(h);RoomAtmosphere.reset();AtmosphericBreach.reset();
        var center=room(h);var hole=center.east(3);
        h.assertTrue(TemperatureManager.hasBreathableAir(level,center),"Sealed starting room retains air");
        level.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        RoomAtmosphere.tickLevel(level);
        h.assertTrue(AtmosphericBreach.activeBursts(level)==1,"One real sealed-to-open transition emits one burst");
        level.setBlock(hole.above(),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.tickLevel(level);
        h.assertTrue(AtmosphericBreach.activeBursts(level)==1,"More broken blocks in evacuated room do not repeat burst");
        level.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);level.setBlock(hole.above(),Blocks.GLASS.defaultBlockState(),3);
        RoomAtmosphere.reset(); // Models dropping transient caches; persistent SavedData remains.
        h.assertFalse(TemperatureManager.hasBreathableAir(level,center),"Replacing wall or dropping cache cannot recreate air");
        var geometry=RoomAtmosphere.inspect(level,center);
        var persisted=RoomAirState.get(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        var restored=RoomAirState.load(persisted,level.registryAccess());
        h.assertTrue(restored.isDepleted(geometry.cells()),"Real SavedData codec retains depleted room across reload");
        var core=center.west();level.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);level.getBlockEntity(core).onLoad();
        h.assertFalse(TemperatureManager.hasBreathableAir(level,center),"Oxygen generator cannot restore room instantly");
        h.runAfterDelay(105,()->{
            try {
                h.assertTrue(TemperatureManager.hasBreathableAir(level,center),"Five loaded seconds of sealed core support recover air");
                level.setBlock(hole,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.tickLevel(level);
                h.assertTrue(AtmosphericBreach.activeBursts(level)==1,"Recovered room can produce a new event; old event expired");
                h.succeed();
            } finally {apocalypse.setApocalypseTicks(previous,level.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="atmospheric_breach_motion", timeoutTicks=100)
    public static void atmosphericAirflowMovesItemsAndStopsAfterBurst(GameTestHelper h) {
        var level=h.getLevel();var apocalypse=ApocalypseState.get(level.getServer());long previous=apocalypse.getApocalypseTicks();
        phase(h);RoomAtmosphere.reset();AtmosphericBreach.reset();
        var center=room(h);var hole=center.east(3);
        TemperatureManager.hasBreathableAir(level,center);
        var inside=new ItemEntity(level,center.getX()+0.5,center.getY()+0.2,center.getZ()+0.5,new ItemStack(Items.COBBLESTONE));
        var outside=new ItemEntity(level,center.getX()-4.5,center.getY()+0.2,center.getZ()+0.5,new ItemStack(Items.DIRT));
        inside.setNoGravity(true);outside.setNoGravity(true);inside.setDeltaMovement(Vec3.ZERO);outside.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(inside);level.addFreshEntity(outside);
        level.setBlock(hole,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.tickLevel(level);AtmosphericBreach.tickLevel(level);
        h.assertTrue(inside.getDeltaMovement().x>0,"Connected item receives real server impulse toward breach");
        h.assertTrue(outside.getDeltaMovement().equals(Vec3.ZERO),"No pull through solid walls on another side of room");
        var normal=AtmosphericBreach.impulse(Vec3.ZERO,new Vec3(2,0,0),false,false,1);
        var braced=AtmosphericBreach.impulse(Vec3.ZERO,new Vec3(2,0,0),true,false,1);
        h.assertTrue(braced.length()<normal.length()/3,"Crouching has meaningful bracing reduction");
        h.assertTrue(AtmosphericBreach.impulse(new Vec3(0.28,0,0),new Vec3(2,0,0),false,false,1).equals(Vec3.ZERO),"Cap prevents continued acceleration/pinning");
        h.runAfterDelay(45,()->{
            try {h.assertTrue(AtmosphericBreach.activeBursts(level)==0,"Production burst expires without resealing");h.succeed();}
            finally {inside.discard();outside.discard();apocalypse.setApocalypseTicks(previous,level.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_breach_lights",timeoutTicks=100)
    public static void atmosphericBreachSnuffsVolumeAndPreservesSpentTorches(GameTestHelper h) {
        scene(h,()->{
            var level=h.getLevel();var center=room(h);var torch=center.west();var wall=center.west(2);
            var campfire=center.south();var candle=center.north();var furnace=center.east();
            level.setBlock(torch,Blocks.TORCH.defaultBlockState(),2);
            level.setBlock(wall,Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING,Direction.EAST),2);
            level.setBlock(campfire,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,true),2);
            level.setBlock(candle,Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT,true),2);
            level.setBlock(furnace,Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT,true),2);
            var soul=center.north(2);level.setBlock(soul,Blocks.SOUL_TORCH.defaultBlockState(),2);
            h.assertTrue(TemperatureManager.hasBreathableAir(level,center),"Room is pressurized before breach");
            level.setBlock(center.east(3),Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.tickLevel(level);
            h.assertTrue(level.getBlockState(torch).is(ModBlocks.SPENT_TORCH.get()),"Standing torch remains as spent_torch in same breach tick");
            h.assertTrue(level.getBlockState(wall).is(ModBlocks.SPENT_WALL_TORCH.get())
                    &&level.getBlockState(wall).getValue(net.minecraft.world.level.block.WallTorchBlock.FACING)==Direction.EAST,
                    "Wall torch retains position and facing");
            for(var pos:new BlockPos[]{campfire,candle,furnace})h.assertFalse(level.getBlockState(pos).getValue(BlockStateProperties.LIT),"Entire connected volume snuffs simultaneously: "+pos);
            h.assertTrue(level.getBlockState(soul).is(Blocks.SOUL_TORCH),"Soul-light exception survives breach");
            var drops=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(torch),level,torch,null);
            h.assertTrue(drops.size()==1&&drops.getFirst().is(Items.TORCH),"Spent torch still drops original lighting item");
            var oldName=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"extinguished_torch");
            h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(oldName)==ModBlocks.SPENT_TORCH.get(),"Legacy saved torch registry alias resolves to canonical block");
            var palette=new net.minecraft.nbt.CompoundTag();palette.putString("Name",oldName.toString());
            h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),palette).is(ModBlocks.SPENT_TORCH.get()),"Actual saved block palette codec preserves legacy torch");
            var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"relight"));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
            var flint=player.getMainHandItem();
            var hit=new net.minecraft.world.phys.BlockHitResult(torch.getCenter(),Direction.UP,torch,false);
            var failed=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,torch,hit);
            VacuumFlames.relight(failed);
            h.assertTrue(level.getBlockState(torch).is(ModBlocks.SPENT_TORCH.get())&&flint.getDamageValue()==0,"Failed vacuum relight preserves torch and tool");
            var a=ApocalypseState.get(level.getServer());a.setApocalypseTicks(0,level.getServer());CombustionAtmosphere.reset();
            var success=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,torch,hit);
            VacuumFlames.relight(success);
            h.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),"Production flint-and-steel handler restores original torch once breathable atmosphere returns");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="atmospheric_breach_lights",timeoutTicks=100)
    public static void atmosphericBreachWorldFunctionsLoad(GameTestHelper h) {
        var server=h.getLevel().getServer();
        var functions=server.getResourceManager().listResources("function",id->id.getNamespace().equals("atmospheric_breach_check")&&id.getPath().endsWith(".mcfunction"));
        h.assertTrue(functions.size()==9,"All nine authored fixture functions are present");
        functions.forEach((file,resource)->{
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("atmospheric_breach_check",file.getPath().substring("function/".length()).replace(".mcfunction",""));
            h.assertTrue(server.getFunctions().get(id).isPresent(),"Native command parser loads fixture "+id);
        });
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_recovery_notices",timeoutTicks=150)
    public static void atmosphericRecoveryNoticesFollowRealRoomTransitions(GameTestHelper h) {
        var level=h.getLevel();var apocalypse=ApocalypseState.get(level.getServer());long previous=apocalypse.getApocalypseTicks();
        phase(h);RoomAtmosphere.reset();AtmosphericBreach.reset();
        var center=room(h);var hole=center.east(3);
        var notices=new java.util.ArrayList<com.frozendawn.network.RoomRecoveryPayload.Stage>();
        var outsideNotices=new java.util.ArrayList<com.frozendawn.network.RoomRecoveryPayload.Stage>();
        var occupant=noticePlayer(level,center,"room_notice",notices);
        var outside=noticePlayer(level,center.south(6),"outside_notice",outsideNotices);
        TemperatureManager.hasBreathableAir(level,center);
        h.assertTrue(notices.isEmpty(),"Ordinary initially pressurized room does not announce a fictitious repair");
        level.setBlock(hole,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.tickLevel(level);
        level.setBlock(hole,Blocks.GLASS.defaultBlockState(),3);
        h.assertFalse(TemperatureManager.hasBreathableAir(level,center),"Repair without source leaves real air depleted");
        for(int i=0;i<10;i++)RoomAtmosphere.tickLevel(level);
        h.assertTrue(notices.equals(java.util.List.of(com.frozendawn.network.RoomRecoveryPayload.Stage.WAITING_FOR_OXYGEN)),
                "Actual network transport emits one no-supply notice, not one each poll");
        var core=center.west();level.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);level.getBlockEntity(core).onLoad();
        TemperatureManager.hasBreathableAir(level,center);
        h.assertTrue(notices.size()==2&&notices.get(1)==com.frozendawn.network.RoomRecoveryPayload.Stage.RESTORING_AIR,
                "Connected core begins recovery and emits restoring notice");
        h.runAfterDelay(90,()->{
            h.assertFalse(TemperatureManager.hasBreathableAir(level,center),"No early breathable claim before five seconds");
            h.assertTrue(notices.size()==2,"No per-tick notification spam during refill");
        });
        h.runAfterDelay(110,()->{
            try {
                h.assertTrue(TemperatureManager.hasBreathableAir(level,center),"Real refill completes");
                for(int i=0;i<10;i++)RoomAtmosphere.tickLevel(level);
                h.assertTrue(notices.equals(java.util.List.of(
                        com.frozendawn.network.RoomRecoveryPayload.Stage.WAITING_FOR_OXYGEN,
                        com.frozendawn.network.RoomRecoveryPayload.Stage.RESTORING_AIR,
                        com.frozendawn.network.RoomRecoveryPayload.Stage.AIR_RESTORED)),
                        "Sealed, restoring and restored packets arrive exactly once in order");
                h.assertTrue(outsideNotices.isEmpty(),"Recovery notices stay inside the affected connected room");
                h.succeed();
            } finally {
                occupant.discard();outside.discard();apocalypse.setApocalypseTicks(previous,level.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();
            }
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_airlock_isolation",timeoutTicks=150)
    public static void atmosphericTwoDoorChamberIsolatesShelter(GameTestHelper h) {
        var level=h.getLevel();var apocalypse=ApocalypseState.get(level.getServer());long previous=apocalypse.getApocalypseTicks();
        phase(h);RoomAtmosphere.reset();AtmosphericBreach.reset();
        var base=room(h);var chamber=base.east(5);
        for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++) {
            boolean wall=Math.abs(x)==2||Math.abs(z)==2||y==-1||y==3;
            level.setBlock(chamber.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        var inner=base.east(3);var outer=chamber.east(2);
        for(var door:new BlockPos[]{inner,outer}) {
            var state=Blocks.IRON_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST);
            level.setBlock(door,state,2);
            level.setBlock(door.above(),state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        }
        // A Core directly adjoining both air spaces can supply both without opening the divider.
        var core=inner.north();level.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),2);level.getBlockEntity(core).onLoad();
        h.assertTrue(TemperatureManager.hasBreathableAir(level,base)&&TemperatureManager.hasBreathableAir(level,chamber),
                "Closed doors divide two initially breathable connected volumes");
        for(var pos:new BlockPos[]{outer,outer.above()})level.setBlock(pos,level.getBlockState(pos).setValue(BlockStateProperties.OPEN,true),3);
        RoomAtmosphere.tickLevel(level);
        h.assertFalse(TemperatureManager.hasBreathableAir(level,chamber),"Open outer door evacuates the entry chamber");
        h.assertTrue(TemperatureManager.hasBreathableAir(level,base),"Closed inner door preserves base air");
        for(var pos:new BlockPos[]{outer,outer.above()})level.setBlock(pos,level.getBlockState(pos).setValue(BlockStateProperties.OPEN,false),3);
        h.assertFalse(TemperatureManager.hasBreathableAir(level,chamber),"Closed outer door does not instantly create air");
        h.runAfterDelay(110,()->{
            try {
                h.assertTrue(TemperatureManager.hasBreathableAir(level,chamber),"Shared connected source restores only entry chamber");
                for(var pos:new BlockPos[]{inner,inner.above()})level.setBlock(pos,level.getBlockState(pos).setValue(BlockStateProperties.OPEN,true),3);
                h.assertTrue(TemperatureManager.hasBreathableAir(level,base)&&TemperatureManager.hasBreathableAir(level,chamber),
                        "Opening inner door after refill keeps joined shelter breathable");
                for(var pos:new BlockPos[]{outer,outer.above()})level.setBlock(pos,level.getBlockState(pos).setValue(BlockStateProperties.OPEN,true),3);
                h.assertFalse(TemperatureManager.hasBreathableAir(level,base),"Both open doors really breach the whole connected shelter");
                h.succeed();
            } finally {apocalypse.setApocalypseTicks(previous,level.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();}
        });
    }

    private static net.neoforged.neoforge.common.util.FakePlayer noticePlayer(
            net.minecraft.server.level.ServerLevel level,BlockPos pos,String name,
            java.util.List<com.frozendawn.network.RoomRecoveryPayload.Stage> notices) {
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),name));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.RoomRecoveryPayload recovery)
                    notices.add(recovery.stage());
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {send(packet);}
        };
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);level.addNewPlayer(player);
        return player;
    }

    private static BlockPos room(GameTestHelper h) {
        var center=h.absolutePos(new BlockPos(10,3,10));var level=h.getLevel();
        for(int x=-3;x<=3;x++)for(int y=-1;y<=3;y++)for(int z=-3;z<=3;z++) {
            boolean wall=Math.abs(x)==3||Math.abs(z)==3||y==-1||y==3;
            level.setBlock(center.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        return center;
    }
    private static void phase(GameTestHelper h) {
        var a=ApocalypseState.get(h.getLevel().getServer());a.setApocalypseTicks((long)(a.getTotalDays()*24000L*0.90),h.getLevel().getServer());CombustionAtmosphere.reset();
    }
    private static void scene(GameTestHelper h,Runnable test) {
        var a=ApocalypseState.get(h.getLevel().getServer());long previous=a.getApocalypseTicks();
        try {phase(h);RoomAtmosphere.reset();AtmosphericBreach.reset();test.run();h.succeed();}
        finally {a.setApocalypseTicks(previous,h.getLevel().getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();}
    }
}
