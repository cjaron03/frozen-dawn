package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindAcousticPropagation;
import com.frozendawn.entity.blind.BlindAcousticPropagation.Outcome;
import com.frozendawn.entity.blind.BlindAcousticPropagation.Result;
import com.frozendawn.entity.blind.BlindArchitectSensing;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlindAcousticGameTest {
    private static ArchitectEntity fixture(MaeveObservationGameTest.Scene s) {
        for(int x=0;x<=20;x++)for(int z=0;z<=14;z++)for(int y=-1;y<=5;y++)
            s.block(x,y,z,(x==0||x==20||z==0||z==14||y==-1||y==5?Blocks.STONE:Blocks.AIR).defaultBlockState());
        var a=s.architect(2,6);a.setBlindArchitect(true);a.setOnGround(true);a.tickCount=80;
        a.setYRot(-90);a.setYHeadRot(-90);a.setYBodyRot(-90);return a;
    }
    private static Result sample(MaeveObservationGameTest.Scene s,ArchitectEntity a,AABB body,float yaw,int limit) {
        return BlindAcousticPropagation.echo(s.level,a.getEyePosition(),yaw,0,body,s.level.getGameTime(),limit);
    }
    private static void evidence(String name,Object value) {
        try {
            var directory=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports","build/architect-reports"),"blind-acoustic");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");
        } catch(java.io.IOException error) { throw new IllegalStateException(error); }
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoOrientationChangesReturnedEnergyAtEqualRange(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,430,s->{
            var a=fixture(s);var p=s.player("echo_orientation",10,6);var body=p.getBoundingBox();
            var front=sample(s,a,body,-90,256);var side=sample(s,a,body,0,256);var rear=sample(s,a,body,90,256);
            var rescanned=sample(s,a,body,-90,256);
            evidence("orientation",java.util.List.of(front,side,rear,rescanned));
            h.assertTrue(front.outcome()==Outcome.LOCALIZED&&side.detected()&&rear.outcome()==Outcome.BELOW_THRESHOLD,
                    "Equal geometry produces usable front/side and a weak rear return: "+front+" / "+side+" / "+rear);
            h.assertTrue(front.received()>side.received()&&side.received()>rear.received()&&rear.received()>0,
                    "Directivity varies smoothly, without a hard vision cutoff");
            h.assertTrue(rescanned.equals(front)&&front.work()<=256,"Turning back restores the same measured evidence within the reserved share");
            h.assertTrue(Math.abs(front.arrivalMillis()-(front.outwardLength()+front.returnLength())/343*1000)<1e-9
                    &&front.returnLength()>0&&front.due()>front.emitted()+front.arrivalTicks(),"Physical round trip and processing delay are separate");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoNarrowApertureReducesExposedReflectorEnergy(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,436,s->{
            var a=fixture(s);var p=s.player("echo_exposed_patch",10,6);var body=p.getBoundingBox();
            var open=sample(s,a,body,-90,256);
            for(int y=0;y<=4;y++)for(int z=1;z<14;z++)s.block(6,y,z,Blocks.STONE.defaultBlockState());
            s.block(6,1,6,Blocks.STONE_SLAB.defaultBlockState().setValue(
                    net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
            var aperture=sample(s,a,body,-90,256);
            s.block(6,1,6,Blocks.STONE.defaultBlockState());var sealed=sample(s,a,body,-90,256);
            evidence("exposed-area",java.util.List.of(open,aperture,sealed));
            h.assertTrue(open.detected()&&aperture.detected()&&Math.abs(aperture.received()*3-open.received())<1e-9,
                    "Only the exposed upper patch contributes through a narrow physical slit: "+aperture);
            h.assertTrue(sealed.outcome()==Outcome.NO_DIRECT_RETURN&&sealed.measurement()==null,"Sealing that same slit removes the direct return");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoStaticClutterChangesLocalizationAtSameBodyPose(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,431,s->{
            var a=fixture(s);var p=s.player("echo_clutter",10,6);var body=p.getBoundingBox();
            var clear=sample(s,a,body,-90,256);
            for(int y=0;y<=3;y++)for(int z=4;z<=8;z++)s.block(11,y,z,Blocks.STONE.defaultBlockState());
            var stone=sample(s,a,body,-90,256);
            for(int y=0;y<=3;y++)for(int z=4;z<=8;z++)s.block(11,y,z,Blocks.WHITE_WOOL.defaultBlockState());
            var wool=sample(s,a,body,-90,256);
            evidence("static-clutter",java.util.List.of(clear,stone,wool));
            h.assertTrue(clear.outcome()==Outcome.LOCALIZED&&stone.clutter()>clear.clutter()&&stone.signalToNoise()<clear.signalToNoise(),
                    "A static reflector in the delay/bearing neighborhood competes with the body return: "+stone);
            h.assertTrue(stone.outcome()!=Outcome.LOCALIZED&&wool.clutter()<stone.clutter()&&wool.detected(),
                    "The material changes returned clutter, without making the unchanged body itself disappear: "+wool);
            h.assertTrue(Math.abs(clear.received()-stone.received())<1e-12&&Math.abs(stone.received()-wool.received())<1e-12,
                    "Matched comparison changes the competing surface, not the source or body impulse");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoReturnRevalidatesClosureWithoutLiveTargetRefresh(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,432,s->{
            var a=fixture(s);var p=s.player("echo_return_path",10,6);var result=sample(s,a,p.getBoundingBox(),-90,256);
            h.assertTrue(result.detected(),"Positive initial return");
            p.setPos(s.position(16,10));
            var unchanged=BlindAcousticPropagation.validate(s.level,result,a.getEyePosition(),256);
            h.assertTrue(unchanged.open(),"Moving the source does not refresh or erase its already frozen reflection");
            for(int y=0;y<=4;y++)for(int z=1;z<14;z++)s.block(6,y,z,Blocks.GLASS.defaultBlockState());
            var closed=BlindAcousticPropagation.validate(s.level,result,a.getEyePosition(),256);
            var blocked=sample(s,a,new AABB(s.position(10,6).add(-.3,0,-.3),s.position(10,6).add(.3,1.8,.3)),-90,256);
            evidence("return-closure",java.util.List.of(result,unchanged,closed,blocked));
            h.assertTrue(!closed.open()&&closed.outcome()==Outcome.NO_DIRECT_RETURN&&blocked.outcome()==Outcome.NO_DIRECT_RETURN,
                    "A visible glass wall blocks the modeled sound path in both emission and return validation");
            h.assertTrue(result.measurement().region().distSqr(p.blockPosition())>16,"Measurement cannot follow the moved player");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoIncompleteGeometryCannotProduceAMeasurement(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,433,s->{
            var a=fixture(s);var p=s.player("echo_unknown",10,6);
            var limited=sample(s,a,p.getBoundingBox(),-90,1);
            Vec3 unloaded=new Vec3(500000,100,500000);
            var missing=BlindAcousticPropagation.echo(s.level,unloaded,-90,0,new AABB(unloaded.add(7,0,0),unloaded.add(8,2,1)),s.level.getGameTime(),256);
            evidence("unknown",java.util.List.of(limited,missing));
            h.assertTrue(limited.outcome()==Outcome.UNKNOWN_BUDGET&&limited.work()==1&&limited.measurement()==null,
                    "Exhaustion cannot manufacture a positive or completed negative");
            h.assertTrue(missing.outcome()==Outcome.UNKNOWN_UNLOADED&&missing.measurement()==null
                    &&!s.level.hasChunkAt(net.minecraft.core.BlockPos.containing(unloaded)),"Geometry queries never load the missing chunk");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoConnectedBentAirDoesNotRevealHiddenFeet(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,434,s->{
            for(int x=0;x<=12;x++)for(int z=0;z<=10;z++)for(int y=-1;y<=3;y++)s.block(x,y,z,Blocks.STONE.defaultBlockState());
            for(int x=2;x<=9;x++)for(int y=0;y<=2;y++)s.block(x,y,2,Blocks.AIR.defaultBlockState());
            for(int z=2;z<=7;z++)for(int y=0;y<=2;y++)s.block(9,y,z,Blocks.AIR.defaultBlockState());
            var a=s.architect(2,2);var p=s.player("echo_bend_no_gps",9,7);
            var connected=BlindArchitectSensing.air(s.level,a.getEyePosition(),p.getEyePosition(),24,512);
            var result=sample(s,a,p.getBoundingBox(),-90,256);
            evidence("bent-air",java.util.List.of(connected,result));
            h.assertTrue(connected.reached()&&result.outcome()==Outcome.NO_DIRECT_RETURN&&result.measurement()==null,
                    "Air connectivity is not a measured returned echo; an unmodeled bend exposes no hidden feet");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoControllerTurnsOnlyAfterMeasuredReturn(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,435,s->{
            var a=fixture(s);var p=s.player("echo_delayed_turn",10,6);
            a.setYRot(-50);a.setYBodyRot(-50);a.setYHeadRot(-50);
            long emitted=s.level.getGameTime();a.aiStep();var echo=a.blindController().lastEcho();
            h.assertTrue(echo!=null&&echo.detected()&&a.blindController().tracks().isEmpty(),"Emission alone does not grant a sensed track: "+echo);
            var region=echo.measurement().region();p.setPos(s.position(16,12));
            for(long time=emitted+1;time<echo.due();time++) {
                s.clock(time);a.aiStep();h.assertTrue(a.blindController().tracks().isEmpty(),"No turn-to-target evidence before physical return plus processing");
            }
            s.clock(echo.due());a.aiStep();var signal=a.blindController().signal();
            h.assertTrue(signal!=null&&signal.sense()==Sense.ECHO&&signal.region().equals(region)
                    &&signal.region().distSqr(p.blockPosition())>16&&a.getTarget()==null,"Admitted track contains only the frozen measured region");
            for(int tick=1;tick<=8;tick++) {s.clock(echo.due()+tick);a.aiStep();}
            double bearing=Math.toDegrees(Math.atan2(-(region.getX()+.5-a.getX()),region.getZ()+.5-a.getZ()));
            double error=Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYHeadRot()-(float)bearing));
            evidence("controller-turn",java.util.Map.of("echo",echo,"signal",signal,"headYaw",a.getYHeadRot(),"measuredBearing",bearing,"error",error));
            h.assertTrue(error<15,"Head turns toward the returned bearing without following the relocated player: "+error);
        });
    }
    private record ScanPose(Vec3 position,float head,float body,net.minecraft.core.BlockPos region,long age,String state) { }
    private record SearchTrial(java.util.List<ScanPose> poses,int reacquiredAfter,double focusedError) { }
    private static SearchTrial searchTrial(GameTestHelper h,MaeveObservationGameTest.Scene s,
                                          net.minecraft.server.level.ServerPlayer player,boolean moveBehindWall) {
        var a=fixture(s);a.getRandom().setSeed(437);
        for(int x=1;x<=4;x++)for(int z=4;z<=8;z++)for(int y=0;y<=2;y++)
            s.block(x,y,z,(x==1||x==4||z==4||z==8||y==2?Blocks.GLASS:Blocks.AIR).defaultBlockState());
        for(int z=5;z<=7;z++)s.block(4,1,z,Blocks.AIR.defaultBlockState());
        player.setPos(s.position(10,6));player.setOnGround(true);
        long start=s.level.getGameTime();var poses=new java.util.ArrayList<ScanPose>();
        var emissions=new java.util.HashSet<Long>();int returned=0,blocked=0;
        for(int tick=1;tick<=480;tick++) {
            if(tick==120)for(int z=5;z<=7;z++)s.block(4,1,z,Blocks.GLASS.defaultBlockState());
            if(moveBehindWall&&tick>=120)player.setPos(s.position(10,tick%80<40?2:12));
            s.clock(start+tick);s.level.tickNonPassenger(a);
            var echo=a.blindController().lastEcho();
            if(echo!=null&&emissions.add(echo.emitted())) {
                h.assertTrue(!echo.unknown(),"Matched search fixture stays within existing geometry budgets");
                if(tick<120&&echo.detected())returned++;
                if(tick>=120) {h.assertTrue(echo.outcome()==Outcome.NO_DIRECT_RETURN,"Unheard movement remains behind a sealed wall");blocked++;}
            }
            var signal=a.blindController().signal();
            h.assertTrue(a.getTarget()==null&&a.blindController().lastVibration()==null,
                    "Moving a hidden fixture emits no footstep and supplies no live target");
            if(signal!=null)h.assertTrue(signal.sense()==Sense.ECHO&&signal.observed()<start+120,
                    "Only the frozen pre-closure echo can guide search");
            poses.add(new ScanPose(a.position(),a.getYHeadRot(),a.yBodyRot,signal==null?null:signal.region(),
                    signal==null?-1:s.level.getGameTime()-signal.observed(),a.blindController().diagnostics().split(" ",2)[0]));
        }
        h.assertTrue(returned>=2&&blocked>=2&&a.blindController().tracks().isEmpty(),"Both trials acquire, lose returns and naturally expire the contact");
        player.setPos(s.position(10,6));for(int z=5;z<=7;z++)s.block(4,1,z,Blocks.AIR.defaultBlockState());
        int reacquired=-1;double error=Double.NaN;
        for(int tick=481;tick<=650;tick++) {
            s.clock(start+tick);s.level.tickNonPassenger(a);var signal=a.blindController().signal();
            if(signal==null||signal.observed()<start+481||!signal.lock(s.level.getGameTime()))continue;
            if(reacquired<0)reacquired=tick-480;
            if(tick-480<reacquired+10)continue;
            double bearing=Math.toDegrees(Math.atan2(-(signal.region().getX()+.5-a.getX()),signal.region().getZ()+.5-a.getZ()));
            error=Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYHeadRot()-bearing));break;
        }
        h.assertTrue(reacquired>0&&reacquired<=150&&error<10,
                "A new physical return focuses the scan again after reopening: delay="+reacquired+" error="+error);
        a.blindController().clear();a.discard();return new SearchTrial(poses,reacquired,error);
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void echoSearchIgnoresUnheardMovementAndRefocusesOnReturn(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,437,s->{
            boolean griefing=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);
            try {
                var p=s.player("echo_search_hidden_motion",10,6);
                var still=searchTrial(h,s,p,false);var moving=searchTrial(h,s,p,true);
                h.assertTrue(still.poses().size()==480&&moving.poses().size()==480,"Both matched trials preserve their full pose sequence");
                double maxHeadDifference=0,maxBodyDifference=0;
                for(int i=0;i<480;i++) {
                    var a=still.poses().get(i);var b=moving.poses().get(i);
                    maxHeadDifference=Math.max(maxHeadDifference,Math.abs(net.minecraft.util.Mth.wrapDegrees(a.head()-b.head())));
                    maxBodyDifference=Math.max(maxBodyDifference,Math.abs(net.minecraft.util.Mth.wrapDegrees(a.body()-b.body())));
                    h.assertTrue(a.position().distanceToSqr(b.position())<1e-10&&java.util.Objects.equals(a.region(),b.region())
                            &&a.age()==b.age()&&a.state().equals(b.state()),"Hidden silent movement cannot alter the remembered region, age, route or search state at tick "+(i+1));
                }
                evidence("search-hidden-movement",java.util.Map.of("stationary",still,"hiddenMoving",moving,
                        "maximumHeadDifference",maxHeadDifference,"maximumBodyDifference",maxBodyDifference));
                h.assertTrue(maxHeadDifference<.001&&maxBodyDifference<.001,
                        "The actual pose sequence is independent of unheard live player coordinates: "+maxHeadDifference+" / "+maxBodyDifference);
            } finally {s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(griefing,s.server);}
        });
    }
}
