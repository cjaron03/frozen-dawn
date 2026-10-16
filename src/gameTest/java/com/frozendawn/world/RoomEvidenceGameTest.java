package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.network.RoomRecoveryPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomEvidenceGameTest {
    @BeforeBatch(batch="room_evidence") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_evidence",timeoutTicks=100)
    public static void roomLargeHangarResolvesAcrossTicksAndPreservesUnverifiedAir(GameTestHelper h){
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long phase=a.getApocalypseTicks(),clock=l.getGameTime();
        var air=RoomAirState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var ids=RoomIdentityState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        net.minecraft.server.level.ServerPlayer player=null;
        try {
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.9),l.getServer());RoomAtmosphere.reset();RoomThermalManager.reset();
            var origin=h.absolutePos(new BlockPos(2,2,2));
            for(int x=0;x<=25;x++)for(int y=0;y<=25;y++)for(int z=0;z<=25;z++){
                boolean wall=x==0||x==25||y==0||y==25||z==0||z==25;
                l.setBlock(origin.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
            }
            var center=origin.offset(12,12,12);
            h.assertTrue(RoomAtmosphere.view(l,center)==null&&RoomAtmosphere.inspect(l,center).scanning(),"A13824cell sealed hangar yields work rather than pretending to be exterior");
            RoomAtmosphere.RoomView room=null;
            for(int i=1;i<=80&&room==null;i++){
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+i);
                RoomAtmosphere.tickLevel(l);room=RoomAtmosphere.view(l,center);
            }
            h.assertTrue(room!=null&&room.geometry().cells().size()==13824&&RoomAtmosphere.hasAir(l,center),"Frontier continues across actual room ticks and proves the complete hangar sealed");
            long id=room.id();var before=RoomAirState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
            var stages=new ArrayList<RoomRecoveryPayload.Stage>();
            player=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"seal_check"));
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(l.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                    if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof RoomRecoveryPayload p)stages.add(p.stage());
                }
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){send(packet);}
            };
            player.setPos(center.getCenter());l.addNewPlayer(player);
            RoomAtmosphere.reset();
            h.assertTrue(RoomAtmosphere.hasAir(l,center)&&RoomAtmosphere.view(l,center)==null,"Unchanged boundary retains confirmed air while a fresh budget-limited scan is pending");
            h.assertTrue(RoomIdentityState.get(l).at(center).id()==id&&before.equals(RoomAirState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess())),"Budget uncertainty changes neither saved identity nor any gas depletion bit");
            h.assertTrue(stages.contains(RoomRecoveryPayload.Stage.SEAL_UNVERIFIED),"The real client packet reports seal unverified instead of silently suffocating the player");
            h.succeed();
        }finally{
            if(player!=null)l.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            RoomAtmosphere.reset();RoomThermalManager.reset();
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(ids,l.registryAccess()));
            ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);a.setApocalypseTicks(phase,l.getServer());CombustionAtmosphere.reset();
        }
    }
    @BeforeBatch(batch="room_delayed_frontier") public static void delayedReport(ServerLevel l){GameTestReporting.installReporter(l);}
    @BeforeBatch(batch="room_large_region") public static void largeReport(ServerLevel l){GameTestReporting.installReporter(l);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_delayed_frontier",timeoutTicks=200)
    public static void roomDelayedFrontierBreachFindsSkyWithoutRecordedExterior(GameTestHelper h){preserved(h,()->{
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(64,2,64));box(l,origin,24);
        var center=origin.offset(12,12,12);long clock=l.getGameTime();
        RoomAtmosphere.RoomView room=null;
        for(int i=0;i<100&&room==null;i++){time(l,clock+i);RoomAtmosphere.tickLevel(l);room=RoomAtmosphere.view(l,center);}
        h.assertTrue(room!=null&&room.geometry().cells().size()==13824&&RoomAtmosphere.hasAir(l,center),"Guard: the actual hangar is proven sealed and pressurized before the edit");
        var previous=room.geometry().cells();long id=room.id();var hole=origin.offset(25,12,12);
        // A covered, one-cell corridor reaches genuine sky only after the room's frontier has expanded.
        for(int x=26;x<=105;x++){
            var cell=origin.offset(x,12,12);
            l.setBlock(cell,Blocks.AIR.defaultBlockState(),2);
            for(var d:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH})l.setBlock(cell.relative(d),Blocks.STONE.defaultBlockState(),2);
        }
        var sky=origin.offset(106,12,12);l.setBlock(sky,Blocks.AIR.defaultBlockState(),2);
        h.assertTrue(l.isLoaded(sky)&&sky.getY()>=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,sky.getX(),sky.getZ()),"Guard: the far exit has real loaded sky evidence");
        h.assertFalse(RoomAtmosphere.knownExteriorAt(l,sky)||RoomAtmosphere.knownExteriorAt(l,hole.east()),"No exterior witness is pre-recorded for either side");
        l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(RoomAtmosphere.hasBoundaryOpening(l,previous),"The removed historical boundary is a real new connection");
        h.assertTrue(RoomAtmosphere.hasAir(l,center)&&RoomAtmosphere.inspect(l,center).scanning(),"Initial unfinished frontier retains confirmed air rather than inventing exterior evidence");
        boolean open=false;int ticks=0;clock=l.getGameTime();
        for(int i=1;i<=160&&!open;i++){
            time(l,clock+i);RoomAtmosphere.tickLevel(l);var g=RoomAtmosphere.inspect(l,center);ticks=i;
            open=g.seal()==RoomAtmosphere.Seal.OPEN;if(!open)h.assertTrue(RoomAtmosphere.hasAir(l,center),"Unfinished slices keep last confirmed room pressure");
        }
        h.assertTrue(open&&ticks>1,"A later frontier slice discovers the real sky exit");
        h.assertFalse(RoomAtmosphere.hasAir(l,center),"Verified exterior connection vents the original room after delayed discovery");
        h.assertTrue(RoomAirState.get(l).isDepleted(previous)&&RoomIdentityState.get(l).at(center).id()==id,"Breach depletes gas while retaining dormant historical membership identity");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_large_region",timeoutTicks=200)
    public static void roomLargeCellRegionBreachRequiresBoundaryEvidenceAtHardCap(GameTestHelper h){preserved(h,()->{
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(64,2,64));box(l,origin,3);
        var center=origin.offset(2,2,2);var room=RoomAtmosphere.view(l,center);
        h.assertTrue(room!=null&&room.geometry().cells().size()==27&&RoomAtmosphere.hasAir(l,center),"Guard: independent small base starts sealed with gas");
        var previous=room.geometry().cells();var caveOrigin=origin.east(4);box(l,caveOrigin,52);
        var entry=origin.offset(5,2,2);var hole=origin.offset(4,2,2);
        h.assertTrue(52*52*52==140608&&52*52*52>131072,"This is a genuinely large-cell region beyond the scanner hard cap, not a narrow distance-only corridor");
        h.assertFalse(RoomAtmosphere.knownExteriorAt(l,entry),"Covered cave has no pre-recorded exterior witness");
        var closed=RoomAtmosphere.inspect(l,entry);
        h.assertTrue(closed.seal()==RoomAtmosphere.Seal.UNKNOWN&&closed.scanning()&&RoomAtmosphere.hasAir(l,center),"Large far-side scan uncertainty cannot vent the still-closed base");
        l.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(RoomAtmosphere.hasBoundaryOpening(l,previous),"Only the actual removed boundary supplies connection evidence");
        h.assertTrue(RoomAtmosphere.hasAir(l,center)&&RoomAtmosphere.inspect(l,center).scanning(),"A real connection still waits for its unfinished frontier");
        long clock=l.getGameTime();boolean open=false;int peak=0;
        for(int i=1;i<=360&&!open;i++){
            time(l,clock+i);RoomAtmosphere.tickLevel(l);var g=RoomAtmosphere.inspect(l,center);peak=Math.max(peak,g.cells().size());
            open=g.seal()==RoomAtmosphere.Seal.OPEN;
        }
        h.assertTrue(open&&peak==131072,"Actual large region reaches the cell hard cap before boundary-backed reservoir handling");
        h.assertFalse(RoomAtmosphere.knownExteriorAt(l,entry),"This branch uses a real breach and current-phase reservoir evidence, not a hidden sky witness");
        h.assertTrue(!RoomAtmosphere.hasAir(l,center)&&RoomAirState.get(l).isDepleted(previous),"Late-phase lower-pressure reservoir vents the breached base");
    });}
    private static void box(ServerLevel l,BlockPos origin,int interior){
        int side=interior+1;
        for(int x=0;x<=side;x++)for(int y=0;y<=side;y++)for(int z=0;z<=side;z++){
            boolean wall=x==0||y==0||z==0||x==side||y==side||z==side;
            l.setBlock(origin.offset(x,y,z),(wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
    }
    private static void time(ServerLevel l,long tick){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(tick);}
    private static void preserved(GameTestHelper h,Runnable body){
        var l=h.getLevel();var a=ApocalypseState.get(l.getServer());long phase=a.getApocalypseTicks(),clock=l.getGameTime();
        var air=RoomAirState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var ids=RoomIdentityState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var gas=com.frozendawn.airlock.AirlockSavedState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        var heat=RoomThermalState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
        try{
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.9),l.getServer());RoomAtmosphere.reset();RoomThermalManager.reset();
            body.run();h.succeed();
        }finally{
            RoomAtmosphere.reset();RoomThermalManager.reset();
            l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(ids,l.registryAccess()));
            l.getDataStorage().set("frozendawn_airlocks",com.frozendawn.airlock.AirlockSavedState.load(gas,l.registryAccess()));
            l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(heat,l.registryAccess()));
            time(l,clock);a.setApocalypseTicks(phase,l.getServer());CombustionAtmosphere.reset();
        }
    }

}
