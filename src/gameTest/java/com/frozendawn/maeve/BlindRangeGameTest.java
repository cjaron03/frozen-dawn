package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindAcousticPropagation;
import com.frozendawn.entity.blind.BlindArchitectSensing;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.frozendawn.event.BlindArchitectEvents;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlindRangeGameTest {
    private static ArchitectEntity room(MaeveObservationGameTest.Scene s) {
        for(int x=0;x<=38;x++)for(int z=0;z<=16;z++)for(int y=-1;y<=5;y++)
            s.block(x,y,z,(x==0||x==38||z==0||z==16||y==-1||y==5?Blocks.STONE:Blocks.AIR).defaultBlockState());
        var a=s.architect(2,8);a.setBlindArchitect(true);a.setOnGround(true);a.tickCount=80;
        a.setYRot(-90);a.setYHeadRot(-90);a.setYBodyRot(-90);return a;
    }
    private static void evidence(String name,Object value) {
        try {
            var p=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports","build/architect-reports"),"blind-range");
            java.nio.file.Files.createDirectories(p);
            java.nio.file.Files.writeString(p.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void blindEchoRoomRangeRetainsWallsAndDirection(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,443,3,s->{
            var a=room(s);var p=s.player("range_echo",12,8);var values=new java.util.ArrayList<BlindAcousticPropagation.Result>();
            for(int distance:new int[]{10,16,20}) {
                p.setPos(s.position(2+distance,8));
                var q=BlindAcousticPropagation.echo(s.level,a.getEyePosition(),-90,0,p.getBoundingBox(),s.level.getGameTime(),256);
                values.add(q);h.assertTrue(q.detected()&&q.work()<=256,"Exposed body has a usable return at "+distance+" blocks: "+q);
                h.assertTrue(q.measurement().rangeHalfWidth()>0&&q.measurement().bearingHalfWidth()>0,"Long returns retain measurement uncertainty");
            }
            p.setPos(s.position(12,8));
            var rear=BlindAcousticPropagation.echo(s.level,a.getEyePosition(),90,0,p.getBoundingBox(),s.level.getGameTime(),256);values.add(rear);
            h.assertTrue(!rear.detected(),"A quiet rear lobe does not turn the range increase into omnidirectional sight");
            for(int z=1;z<16;z++)for(int y=0;y<5;y++)s.block(7,y,z,Blocks.GLASS.defaultBlockState());
            var sealed=BlindAcousticPropagation.echo(s.level,a.getEyePosition(),-90,0,p.getBoundingBox(),s.level.getGameTime(),256);values.add(sealed);
            h.assertTrue(sealed.outcome()==BlindAcousticPropagation.Outcome.NO_DIRECT_RETURN&&sealed.measurement()==null,"Sealed glass blocks the same longer return");
            evidence("echo-distance",values);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void blindEchoReturnSurvivesOrdinaryReceiverMovement(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,447,3,s->{
            var a=room(s);var p=s.player("range_moving_echo",20,8);
            var initial=BlindAcousticPropagation.echo(s.level,a.getEyePosition(),-90,0,p.getBoundingBox(),s.level.getGameTime(),256);
            h.assertTrue(initial.detected(),"Long echo is emitted before either body moves");
            var measured=initial.measurement();p.setPos(s.position(32,12));
            // Largest rejected displacement from human cave18: 0.723885 blocks
            // during five ticks. Geometry is rechecked against a frozen reflector.
            var walked=BlindAcousticPropagation.validate(s.level,initial,a.getEyePosition().add(.7238850916172572,0,0),256);
            var teleported=BlindAcousticPropagation.validate(s.level,initial,a.getEyePosition().add(3,0,0),256);
            h.assertTrue(walked.open()&&!teleported.open()&&initial.measurement().equals(measured)
                    &&measured.region().distSqr(p.blockPosition())>16,"Ordinary ego movement survives; large displacement and live player tracking do not");
            for(int z=1;z<16;z++)for(int y=0;y<5;y++)s.block(7,y,z,Blocks.STONE.defaultBlockState());
            var closed=BlindAcousticPropagation.validate(s.level,initial,a.getEyePosition().add(.7238850916172572,0,0),256);
            h.assertTrue(!closed.open(),"Receiver motion never bypasses a newly closed return path");
            for(int z=1;z<16;z++)for(int y=0;y<5;y++)s.block(7,y,z,Blocks.AIR.defaultBlockState());
            p.setPos(s.position(22,8));a.getRandom().setSeed(447);a.startDecisionRecording(null);var origin=a.position();
            for(int i=1;i<=65;i++) {s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(a);}
            var entries=a.decisionJournal().entries();long returns=entries.stream().filter(e->e.event().equals("BLIND_ECHO_RETURN")&&e.detail().contains("outcome=ADMITTED")).count();
            h.assertTrue(a.position().distanceTo(origin)>3&&returns>=2
                    &&entries.stream().noneMatch(e->e.event().equals("BLIND_ECHO_RETURN")&&e.detail().contains("outcome=NO_DIRECT_RETURN")),
                    "Actual ordinary pursuit sustains distant returns while moving: displacement="+a.position().distanceTo(origin)+" returns="+returns);
            evidence("moving-return",java.util.Map.of("emission",initial,"walked",walked,"teleported",teleported,"closed",closed,
                    "actualDisplacement",a.position().distanceTo(origin),"admitted",returns,"decisions",entries));
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void blindMiningThirtyBlocksNeedsConductingStructure(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,444,3,s->{
            for(int x=0;x<=36;x++)for(int z=0;z<=8;z++)for(int y=0;y<=8;y++)s.block(x,y,z,Blocks.AIR.defaultBlockState());
            for(int x=2;x<=32;x++)s.block(x,4,4,Blocks.STONE.defaultBlockState());
            var a=s.architect(2,4);a.setPos(s.position(2,4).add(0,5,0));a.setBlindArchitect(true);a.setOnGround(true);a.tickCount=80;
            var p=s.player("range_miner",32,4);p.setPos(s.position(32,4).add(0,5,0));p.setOnGround(true);
            BlockPos source=s.origin.offset(32,4,4);s.clock((s.level.getGameTime()/10+1)*10);
            BlindArchitectEvents.mining(s.level,p,source);var connected=a.blindController().lastVibration();
            h.assertTrue(connected!=null&&connected.detected()&&connected.length()>=30&&connected.work()<=384,"Accepted real mining hook reaches connected rock at 30 blocks: "+connected);
            var track=a.blindController().tracks().getFirst();
            h.assertTrue(track.sense()==Sense.VIBRATION&&track.subject()==null&&track.region().equals(a.blockPosition()),"Distant substrate reception supplies no source position or identity");
            s.block(17,4,4,Blocks.AIR.defaultBlockState());a.blindController().clearLocal();s.clock(s.level.getGameTime()+10);
            BlindArchitectEvents.mining(s.level,p,source);var gap=a.blindController().lastVibration();
            h.assertTrue(gap!=null&&gap.outcome()==BlindVibrationPropagation.Outcome.NO_PATH&&a.blindController().tracks().isEmpty(),"The same 30-block impulse cannot cross an actual structural gap: "+gap);
            s.block(17,4,4,Blocks.WHITE_WOOL.defaultBlockState());a.blindController().clearLocal();s.clock(s.level.getGameTime()+10);
            BlindArchitectEvents.mining(s.level,p,source);var wool=a.blindController().lastVibration();
            h.assertTrue(wool!=null&&wool.received()<connected.received(),"A lossy joint reduces the same mining impulse");
            // Broad connected stone reproduces the cave's branching search volume.
            for(int x=2;x<=32;x++)for(int z=1;z<=7;z++)s.block(x,4,z,Blocks.STONE.defaultBlockState());
            a.blindController().clearLocal();s.clock(s.level.getGameTime()+10);BlindArchitectEvents.mining(s.level,p,source);
            var broad=a.blindController().lastVibration();h.assertTrue(broad.detected()&&broad.work()<=384,"Broad rock does not exhaust the contact search before a direct conducting route");
            evidence("mining-thirty",java.util.List.of(connected,gap,wool,broad));
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void blindFootstepAirRangeChecksGeometryAndBudget(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,445,3,s->{
            var a=room(s);var p=s.player("range_step",12,8);p.setOnGround(true);
            s.level.gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
            h.assertTrue(a.blindController().tracks().stream().anyMatch(t->t.sense()==Sense.HEARING&&t.subject()==null),"Actual floor-level walking event is heard at 10 blocks");
            a.blindController().clearLocal();s.clock(s.level.getGameTime()+1);p.setPos(s.position(18,8));
            s.level.gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
            h.assertTrue(a.blindController().tracks().stream().noneMatch(t->t.sense()==Sense.HEARING),"Normal footsteps remain limited by air range");
            a.blindController().clearLocal();s.clock(s.level.getGameTime()+1);p.setSprinting(true);
            s.level.gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
            h.assertTrue(a.blindController().tracks().stream().anyMatch(t->t.sense()==Sense.HEARING),"Sprinting carries farther than ordinary walking");
            var from=a.getEyePosition();var to=p.position().add(0,.15,0);
            var open=BlindArchitectSensing.air(s.level,from,to,24,512);
            var limited=BlindArchitectSensing.air(s.level,from,to,24,1);
            h.assertTrue(open.reached()&&open.visited()<100&&limited.exhausted()&&!limited.reached(),"Verified direct air stays bounded; exhaustion cannot grant hearing");
            for(int z=1;z<16;z++)for(int y=0;y<5;y++)s.block(7,y,z,Blocks.STONE.defaultBlockState());
            var blocked=BlindArchitectSensing.air(s.level,from,to,24,512);
            h.assertTrue(!blocked.reached()&&!BlindArchitectSensing.stillOpen(s.level,open),"Closing the full wall invalidates both new and frozen air paths");
            evidence("footstep-air",java.util.List.of(open,limited,blocked));
        });
    }
}
