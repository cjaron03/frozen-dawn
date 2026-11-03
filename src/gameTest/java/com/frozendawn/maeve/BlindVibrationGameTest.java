package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Impulse;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Kind;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Outcome;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Result;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlindVibrationGameTest {
    private static ArchitectEntity fixture(MaeveObservationGameTest.Scene s) {
        // Four blocks of air below/around every support isolate this from Scene's template floor.
        for (int x=0;x<=12;x++) for (int z=0;z<=8;z++) for (int y=0;y<=8;y++)
            s.block(x,y,z,Blocks.AIR.defaultBlockState());
        for (int x=2;x<=8;x++) s.block(x,4,4,Blocks.STONE.defaultBlockState());
        var actor=s.architect(2,4); actor.setPos(s.position(2,4).add(0,5,0)); actor.setOnGround(true);
        actor.setBlindArchitect(true); actor.tickCount=80; return actor;
    }
    private static Impulse impulse(MaeveObservationGameTest.Scene s) {
        BlockPos source=s.origin.offset(8,4,4);
        return BlindVibrationPropagation.block(Kind.TOOL,source,s.level.getBlockState(source),source.getCenter(),12,s.level.getGameTime());
    }
    private static Result receive(MaeveObservationGameTest.Scene s, Impulse impulse, ArchitectEntity a) {
        return BlindVibrationPropagation.receive(s.level,impulse,a,512);
    }
    private static void evidence(String name, Result... results) {
        try {
            var directory=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports", "build/architect-reports"),"blind-vibration");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results)+"\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static ArchitectEntity behaviorFixture(MaeveObservationGameTest.Scene s, boolean broad) {
        for(int x=0;x<=30;x++)for(int z=-12;z<=10;z++)for(int y=-5;y<=6;y++)
            s.block(x,y,z,(x==0||x==30||z==-12||z==10||y==-5||y==6 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
        int end=broad?20:18,near=broad?2:4,far=broad?8:6,roof=broad?3:2;
        for(int x=8;x<=end;x++)for(int z=near;z<=far;z++) {
            s.block(x,-1,z,Blocks.STONE.defaultBlockState());
            for(int y=0;y<=roof;y++)s.block(x,y,z,(x==8||x==end||z==near||z==far||y==roof||x==13
                    ? Blocks.GLASS : Blocks.AIR).defaultBlockState());
        }
        s.block(10,roof,5,Blocks.SEA_LANTERN.defaultBlockState());
        s.block(broad?17:16,roof,5,Blocks.SEA_LANTERN.defaultBlockState());
        var actor=s.architect(9,5);actor.setBlindArchitect(true);actor.setOnGround(true);actor.tickCount=80;
        return actor;
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationBroadFloorPreservesContactWithinBudget(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,423,s->{
            var actor=behaviorFixture(s,true);var player=s.player("vibration_broad_floor",17,3);player.setOnGround(true);
            var results=new java.util.ArrayList<Result>();
            for(int z:new int[]{3,7,5}) {
                player.setPos(s.position(17,z));
                results.add(BlindVibrationPropagation.receive(s.level,BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()),actor,384));
            }
            // Cutting the whole substrate, wall and roof removes every conducting connection.
            for(int y=-1;y<=3;y++)for(int z=2;z<=8;z++)s.block(13,y,z,Blocks.AIR.defaultBlockState());
            var split=BlindVibrationPropagation.receive(s.level,BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()),actor,384);
            evidence("broad-floor-budget",results.get(0),results.get(1),results.get(2),split);
            for(var result:results) {
                h.assertTrue(result.detected()&&result.work()<=384,"Preserved broad-floor contact must fit the existing passive allowance: "+result);
                h.assertTrue(result.path().getFirst().equals(result.sourceContacts().getFirst())
                        &&result.path().getLast().equals(actor.blockPosition().below()),"Reported energy has an actual contact path");
                h.assertTrue(Math.abs(result.received()-6*Math.exp(-.135*result.length()))<1e-8,
                        "Energy is attenuated along the validated uniform-material path");
            }
            h.assertTrue(!split.detected() && split.work()<=384,"Search guidance cannot bridge air or convert exhaustion into reception");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationAirWorkDoesNotReserveTheSameCapacityTwice(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,424,s->{
            var actor=behaviorFixture(s,false);var player=s.player("vibration_air_budget",16,5);
            // Source pose immediately before the recorded live tick200 miss, translated with the fixture.
            player.setPos(s.origin.getX()+16.20050531128685,s.origin.getY(),s.origin.getZ()+5.487167802932223);player.setOnGround(true);
            var air=com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level,actor.getEyePosition(),player.getEyePosition(),24,512);
            h.assertTrue(!air.reached() && air.visited()>=256 && air.visited()<350,"Preserved sealed-air work: "+air.visited());
            actor.blindController().emit(player.getEyePosition(),player,24);
            var result=actor.blindController().vibrate(BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()));
            evidence("air-reserve-after-spend",result);
            h.assertTrue(result!=null && result.detected(),"Already consumed air work must release its reserved share: "+result);
            h.assertTrue(air.visited()+result.work()<=512,"Both channels still share the same actor cap");
            var overflow=actor.blindController().vibrate(BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()));
            h.assertTrue(air.visited()+result.work()+overflow.work()<=512,"A later burst cannot spend the released reserve twice");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationMovingFeetAcrossFourReceiverSupportsStayBounded(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,425,s->{
            var actor=behaviorFixture(s,true);var player=s.player("vibration_live_contacts",17,6);
            actor.setPos(s.origin.getX()+10.866067800234008,s.origin.getY(),s.origin.getZ()+4.133924470495982);
            actor.setOnGround(true);player.setOnGround(true);
            var results=new java.util.ArrayList<Result>();
            // Actual run5 poses around the seven nonzero-budget misses, plus a sweep
            // across adjoining source footprints. Receiver touches four supports.
            for(double z:new double[]{6.231286200415422,6.9243669240608865,7.39881478610076,3.1,3.3,3.5,3.7,3.9,4.1,4.3,4.5,4.7,4.9,5.1,5.3,5.5,5.7,5.9,6.1,6.3,6.5,6.7,6.9,7.1,7.3,7.5,7.7,7.9}) {
                player.setPos(s.origin.getX()+17.488726795479693,s.origin.getY(),s.origin.getZ()+z);
                results.add(BlindVibrationPropagation.receive(s.level,
                        BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()),actor,384));
            }
            evidence("moving-contact-footprints",results.toArray(Result[]::new));
            for(var result:results)h.assertTrue(result.detected()&&result.work()<=384,
                    "Recorded moving-foot geometry retains connected reception within the existing allowance: "+result);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationFirstStepSurvivesWanderingScan(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,428,s->{
            var actor=behaviorFixture(s,true);var player=s.player("vibration_first_contact",17,4);
            // Run6's first input arrived after a wandering scan, before any
            // vibration track existed to activate the former reservation.
            actor.setPos(s.origin.getX()+10.459155326109833,s.origin.getY(),s.origin.getZ()+4.5408369446222325);
            player.setPos(s.origin.getX()+17.5,s.origin.getY(),s.origin.getZ()+4.622671428677334);
            player.setOnGround(true);s.clock(s.level.getGameTime()+1);
            var air=com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level,actor.getEyePosition(),player.getEyePosition(),24,512);
            h.assertTrue(!air.reached()&&air.visited()==512,"Recorded sealed survey can consume the full allowance");
            actor.blindController().tick();
            h.assertTrue(actor.blindController().signal()==null&&actor.blindController().tracks().isEmpty(),
                    "The first contact has no earlier vibration belief");
            s.level.gameEvent(GameEvent.STEP,player.position(),GameEvent.Context.of(player));
            var result=actor.blindController().lastVibration();evidence("wander-first-step",result);
            h.assertTrue(result!=null&&result.detected(),"A wandering scan leaves capacity for the first real STEP: "+result);
            s.clock(s.level.getGameTime()+1);actor.blindController().tick();
            var signal=actor.blindController().signal();
            h.assertTrue(signal!=null&&signal.sense()==Sense.VIBRATION&&signal.subject()==null
                    &&actor.getNavigation().isDone(),"First reception begins anonymous listening without a pursuit target");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationListeningScanKeepsCapacityForIncomingSteps(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,426,s->{
            var actor=behaviorFixture(s,true);var player=s.player("vibration_scan_share",17,5);player.setOnGround(true);
            // Let collision physics establish the new actor's support before the
            // ordering check. Its first zero-velocity tick can clear onGround.
            actor.setNoAi(true);
            for(int tick=0;tick<3;tick++) { s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(actor); }
            actor.setNoAi(false);
            h.assertTrue(actor.onGround(),"Receiver contact must be settled by real physics before the scan-order test");
            var results=new java.util.ArrayList<Result>();
            var grounded=new java.util.ArrayList<Boolean>();
            for(int tick=0;tick<100;tick++) {
                s.clock(s.level.getGameTime()+1);
                // An active scan may execute before the player emits in this tick.
                if(tick>0)s.level.tickNonPassenger(actor);
                grounded.add(actor.onGround());
                var result=actor.blindController().vibrate(BlindVibrationPropagation.foot(player,Kind.FOOTSTEP,6,s.level.getGameTime()));
                results.add(result);
            }
            evidence("listen-scan-share",results.toArray(Result[]::new));
            try {
                var dir=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports"),"blind-vibration");
                java.nio.file.Files.writeString(dir.resolve("listen-scan-contact-flags.json"),new com.google.gson.Gson().toJson(grounded));
            } catch(java.io.IOException e) { throw new IllegalStateException(e); }
            h.assertTrue(grounded.stream().filter(Boolean::booleanValue).count()>=95,"Exercise repeated grounded steps across several active scans");
            for(int i=0;i<results.size();i++) {
                var result=results.get(i);
                // Native physics can briefly clear support on enabling AI. A real
                // absent receiver contact remains a valid negative, never reception.
                h.assertTrue(result!=null&&(grounded.get(i)?result.detected():result.outcome()==Outcome.NO_RECEIVER_CONTACT),
                        "Incoming grounded steps retain capacity after a scan; absent contact stays negative: tick="+i+" grounded="+grounded.get(i)+" "+result);
            }
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationListeningTurnsBodyWithoutSourceBearing(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,427,s->{
            var actor=behaviorFixture(s,true);var scans=new java.util.ArrayList<java.util.List<Float>>();
            for(var source:java.util.List.of(s.origin.offset(9,-1,4),s.origin.offset(11,-1,6))) {
                actor.blindController().clearLocal();actor.setPos(s.position(10,5));actor.setDeltaMovement(0,0,0);
                actor.setYRot(90);actor.setYHeadRot(90);actor.setYBodyRot(90);actor.setOnGround(true);
                var yaws=new java.util.ArrayList<Float>();
                for(int tick=0;tick<80;tick++) {
                    s.clock(s.level.getGameTime()+1);
                    if(tick%8==0) {
                        var input=BlindVibrationPropagation.block(Kind.TOOL,source,s.level.getBlockState(source),source.getCenter(),12,s.level.getGameTime());
                        h.assertTrue(actor.blindController().vibrate(input).detected(),"Local conducting disturbance reaches the listener");
                    }
                    s.level.tickNonPassenger(actor);
                    yaws.add(actor.getYRot());
                    var selected=actor.blindController().signal();
                    h.assertTrue(selected!=null&&selected.sense()==Sense.VIBRATION&&selected.subject()==null
                            &&actor.getNavigation().isDone(),"Body response grants neither a bearing nor pursuit");
                }
                scans.add(yaws);
            }
            try {
                var dir=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports"),"blind-vibration");
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.writeString(dir.resolve("listening-body-scans.json"),new com.google.gson.Gson().toJson(scans));
            } catch(java.io.IOException e) { throw new IllegalStateException(e); }
            for(var scan:scans)h.assertTrue(java.util.Collections.max(scan)-java.util.Collections.min(scan)>90,
                    "Repeated steps sustain a visible body sweep instead of a fixed body with only head motion");
            for(int i=0;i<80;i++)h.assertTrue(Math.abs(scans.get(0).get(i)-scans.get(1).get(i))<.01,
                    "Identical scalar evidence on opposite sides cannot steer the body toward the hidden source");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationIdenticalImpulseRequiresConnectedSupports(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,410,s->{
            var a=fixture(s);var input=impulse(s);var joined=receive(s,input,a);
            s.block(5,4,4,Blocks.AIR.defaultBlockState());var split=receive(s,input,a);
            s.block(5,4,4,Blocks.STONE.defaultBlockState());var restored=receive(s,input,a);
            h.assertTrue(joined.detected(),"Positive connected control: "+joined);
            h.assertTrue(split.outcome()==Outcome.NO_PATH,"Disconnected negative must finish, not exhaust: "+split);
            h.assertTrue(restored.equals(joined),"Same time/input/restored geometry is deterministic and uncached");
            h.assertTrue(a.blindController().tracks().isEmpty(),"Sensor-level pair never runs another sense or controller admission");
            evidence("connected-disconnected",joined,split,restored);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationFoundationDetourReconnectsVisibleGap(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,411,s->{
            var a=fixture(s);var input=impulse(s);var direct=receive(s,input,a);
            s.block(5,4,4,Blocks.AIR.defaultBlockState());
            for(int x=2;x<=8;x++)s.block(x,4,5,Blocks.STONE.defaultBlockState());
            var detour=receive(s,input,a);
            h.assertTrue(detour.detected()&&detour.length()>direct.length(),"Connected side foundation conducts around gap: "+detour);
            h.assertTrue(detour.received()<direct.received(),"Longer otherwise identical material path loses strength");
            evidence("foundation-detour",direct,detour);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationSourceCushionDoesNotSoftenToolStrike(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,412,s->{
            var a=fixture(s);var p=s.player("vibration_cushion",8,4);p.setPos(s.position(8,4).add(0,5,0));p.setOnGround(true);
            var bare=receive(s,BlindVibrationPropagation.foot(p,Kind.FOOTSTEP,6,s.level.getGameTime()),a);
            var tool=receive(s,impulse(s),a);
            s.block(8,5,4,Blocks.WHITE_CARPET.defaultBlockState());p.setPos(p.position().add(0,.0625,0));
            var soft=receive(s,BlindVibrationPropagation.foot(p,Kind.FOOTSTEP,6,s.level.getGameTime()),a);
            p.setShiftKeyDown(true);var crouchedTool=receive(s,impulse(s),a);
            h.assertTrue(bare.detected()&&soft.detected()&&soft.received()<bare.received()*.3,"Actual carpet contact cushions steps: "+soft);
            h.assertTrue(soft.sourceContacts().contains(s.origin.offset(8,5,4)),"Source touches carpet, not guessed block below");
            h.assertTrue(tool.received()==crouchedTool.received(),"Carpet under miner and crouching do not change the struck block impulse");
            evidence("source-cushion",bare,soft,tool,crouchedTool);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationReceiverRequiresActualContact(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,413,s->{
            var a=fixture(s);var input=impulse(s);var bare=receive(s,input,a);
            a.setPos(a.position().add(0,.05,0));var hovering=receive(s,input,a);
            a.setPos(a.position().add(0,-.05,0));a.setOnGround(false);var flying=receive(s,input,a);
            a.setOnGround(true);s.block(2,5,4,Blocks.WHITE_CARPET.defaultBlockState());a.setPos(a.position().add(0,.0625,0));
            var cushioned=receive(s,input,a);
            h.assertTrue(bare.detected()&&cushioned.detected()&&cushioned.received()<bare.received(),"Receiver coupling changes reception: "+cushioned);
            h.assertTrue(hovering.outcome()==Outcome.NO_RECEIVER_CONTACT&&flying.outcome()==Outcome.NO_RECEIVER_CONTACT,"Ground flag and actual contact are both required");
            evidence("receiver-contact",bare,hovering,flying,cushioned);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationSlabsNeedTouchingFaces(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,414,s->{
            var a=fixture(s);
            for(int x=2;x<=8;x++)s.block(x,4,4,Blocks.STONE_SLAB.defaultBlockState());
            a.setPos(a.position().add(0,-.5,0));var input=impulse(s);var bottom=receive(s,input,a);
            s.block(5,4,4,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));var separated=receive(s,input,a);
            s.block(5,4,4,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.DOUBLE));var touching=receive(s,input,a);
            h.assertTrue(bottom.detected()&&touching.detected(),"Touching slab faces conduct: "+bottom+" / "+touching);
            h.assertTrue(separated.outcome()==Outcome.NO_PATH,"Adjacent upper/lower slabs share only edges, not face area: "+separated);
            evidence("slab-contact",bottom,separated,touching);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationMaterialDampsWithoutChangingConnectivity(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,415,s->{
            var a=fixture(s);var input=impulse(s);var stone=receive(s,input,a);
            s.block(5,4,4,Blocks.WHITE_WOOL.defaultBlockState());var wool=receive(s,input,a);
            h.assertTrue(stone.detected()&&wool.detected()&&wool.received()<stone.received(),"Same path, different structural loss: "+wool);
            h.assertTrue(stone.length()==wool.length()&&stone.path().equals(wool.path()),"Material loss stays separate from geometric length");
            evidence("material-loss",stone,wool);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationFootGenerationSeparatesPostureFlightAndLanding(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,416,s->{
            var a=fixture(s);var p=s.player("vibration_steps",8,4);p.setPos(s.position(8,4).add(0,5,0));p.setOnGround(true);
            var walk=receive(s,BlindVibrationPropagation.foot(p,Kind.FOOTSTEP,6,s.level.getGameTime()),a);
            p.setShiftKeyDown(true);var sneak=receive(s,BlindVibrationPropagation.foot(p,Kind.FOOTSTEP,6,s.level.getGameTime()),a);
            p.setOnGround(false);var flight=receive(s,BlindVibrationPropagation.foot(p,Kind.FOOTSTEP,6,s.level.getGameTime()),a);
            p.setOnGround(true);var landing=receive(s,BlindVibrationPropagation.foot(p,Kind.LANDING,10,s.level.getGameTime()),a);
            h.assertTrue(walk.detected()&&sneak.detected()&&Math.abs(sneak.received()/walk.received()-.25)<1e-8,"Crouching changes generated step strength only");
            h.assertTrue(flight.outcome()==Outcome.NO_SOURCE_CONTACT,"Flight generates no foot-coupled impulse");
            h.assertTrue(landing.detected()&&landing.received()>walk.received(),"Landing retains its impact even while crouched");
            evidence("step-generation",walk,sneak,flight,landing);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationAnonymousContactCannotBecomeHiddenCoordinates(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,417,s->{
            var a=fixture(s);var result=a.blindController().vibrate(impulse(s));
            h.assertTrue(result!=null&&result.detected(),"Controller admits a real contact: "+result);
            var signal=a.blindController().tracks().getFirst();
            h.assertTrue(signal.sense()==Sense.VIBRATION&&signal.subject()==null&&signal.region().equals(a.blockPosition())&&signal.uncertainty()==com.frozendawn.entity.blind.BlindVibrationPropagation.RANGE,
                    "Received magnitude contains no source/identity/navigation coordinate");
            a.aiStep();h.assertTrue(a.blindController().diagnostics().contains("blind=LISTEN")&&a.getTarget()==null&&!a.isMiningBlock(),"Anonymous disturbance triggers a listening scan, not hidden-source excavation");
            evidence("controller-measurement",result);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationRealStepEventUsesStructuralChannel(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,418,s->{
            var a=fixture(s);var p=s.player("vibration_event",8,4);p.setPos(s.position(8,4).add(0,5,0));p.setOnGround(true);
            s.level.gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
            var landed=a.blindController().lastVibration();
            h.assertTrue(landed!=null&&landed.detected(),"Real STEP event reaches mechanical contact solver");
            a.blindController().clearLocal();s.clock(s.level.getGameTime()+1);p.setOnGround(false);p.setPos(p.position().add(0,1,0));
            s.level.gameEvent(GameEvent.STEP,p.position(),GameEvent.Context.of(p));
            h.assertTrue(a.blindController().tracks().stream().noneMatch(t->t.sense()==Sense.VIBRATION),"Air hearing cannot pass for structural contact in flight");
            evidence("real-step",landed,a.blindController().lastVibration());
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationUnknownIsBoundedAndNeverLoadsChunks(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,419,s->{
            var a=fixture(s);var limited=BlindVibrationPropagation.receive(s.level,impulse(s),a,1);
            h.assertTrue(limited.outcome()==Outcome.UNKNOWN_BUDGET&&limited.work()<=1,"Work includes contact/geometry and exhaustion is unknown");
            BlockPos missing=s.origin.offset(0,4,100000);
            h.assertTrue(!s.level.hasChunkAt(missing),"Unloaded fixture precondition");
            var remote=BlindVibrationPropagation.block(Kind.TOOL,missing,Blocks.STONE.defaultBlockState(),missing.getCenter(),12,s.level.getGameTime());
            var absent=BlindVibrationPropagation.receive(s.level,remote,new AABB(missing.above()),true,512);
            h.assertTrue(absent.outcome()==Outcome.UNKNOWN_UNLOADED&&!s.level.hasChunkAt(missing),"Missing terrain stays unloaded and unknown: "+absent);
            evidence("bounded-unknown",limited,absent);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationPreparedWorldPairHasNoHiddenFoundation(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,420,s->{
            var results=new java.util.ArrayList<Result>();
            for(var variant:java.util.List.of("connected","disconnected")) {
                var resource=s.server.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blind_vibration_"+variant,"qa/layout.json")).orElseThrow();
                try(var reader=resource.openAsReader()) {
                    for(var item:com.google.gson.JsonParser.parseReader(reader).getAsJsonArray()) {
                        var row=item.getAsJsonObject();var from=row.getAsJsonArray("from");var to=row.getAsJsonArray("to");
                        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(row.get("block").getAsString()));
                        for(int x=from.get(0).getAsInt();x<=to.get(0).getAsInt();x++)
                            for(int y=from.get(1).getAsInt();y<=to.get(1).getAsInt();y++)
                                for(int z=from.get(2).getAsInt();z<=to.get(2).getAsInt();z++) s.block(x,y+44,z,block.defaultBlockState());
                    }
                } catch(java.io.IOException e) { throw new IllegalStateException(e); }
                // Load the actual generated summon payload. Manually setting onGround
                // here hid a live failure: NoAI actors never settled after summoning.
                var a=preparedReceiver(s,variant);
                BlockPos block=s.origin.offset(21,-1,5);
                var input=BlindVibrationPropagation.block(Kind.TOOL,block,s.level.getBlockState(block),block.getCenter(),12,s.level.getGameTime());
                for(int tick=1;tick<=340;tick++) {
                    a.tick();
                    if(tick!=100&&tick!=220&&tick!=340) continue;
                    var result=receive(s,input,a);results.add(result);
                    h.assertTrue(a.isNoAi()&&a.onGround()&&a.position().distanceToSqr(s.position(9,5))<1e-8,
                            "Generated held receiver remains grounded at pulse "+tick+": "+result);
                    h.assertTrue(variant.equals("connected")?result.detected():result.outcome()==Outcome.NO_PATH,
                            "Actual generated "+variant+" actor/geometry at pulse "+tick+": "+result);
                    h.assertTrue(a.blindController().tracks().isEmpty(),"Held sensor bench cannot admit ordinary hearing/echo");
                }
                a.discard();
            }
            evidence("prepared-world-pair",results.toArray(Result[]::new));
        });
    }
    private static ArchitectEntity preparedReceiver(MaeveObservationGameTest.Scene s,String variant) {
        var resource=s.server.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "blind_vibration_"+variant,"function/prepare.mcfunction")).orElseThrow();
        try(var reader=resource.openAsReader()) {
            String summon=reader.lines().filter(line->line.startsWith("summon frozendawn:architect ")).findFirst().orElseThrow();
            String[] fields=summon.split(" ",6);
            var data=net.minecraft.nbt.TagParser.parseTag(fields[5]);data.putString("id",fields[1]);
            var entity=net.minecraft.world.entity.EntityType.loadEntityRecursive(data,s.level,loaded->{loaded.setPos(s.position(9,5));return loaded;});
            if(!(entity instanceof ArchitectEntity actor)) throw new IllegalStateException("Generated receiver did not load");
            s.level.addFreshEntity(actor);s.entities.add(actor);return actor;
        } catch(java.io.IOException|com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationPassiveBurstsHonorActorAndLevelBudgets(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,421,s->{
            fixture(s);int work=0,unknown=0;
            for(int i=0;i<32;i++) {
                var a=s.architect(2,4);a.setPos(s.position(2,4).add(0,5,0));a.setOnGround(true);a.setBlindArchitect(true);
                int actorWork=0;
                for(int j=0;j<10;j++) {
                    var result=a.blindController().vibrate(impulse(s));actorWork+=result.work();if(result.unknown())unknown++;
                }
                h.assertTrue(actorWork<=384,"Passive work leaves the actor scan reserve: "+actorWork);work+=actorWork;
            }
            h.assertTrue(work<=8192&&unknown>0,"Level work stays bounded under actual event bursts: "+work+" unknown="+unknown);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,timeoutTicks=300)
    public static void vibrationFenceSupportUsesExtendedCollisionHeight(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,422,s->{
            var a=fixture(s);
            for(int x=2;x<=8;x++)s.block(x,4,4,Blocks.AIR.defaultBlockState());
            for(int x=2;x<=4;x++)s.block(x,4,4,Blocks.OAK_FENCE.defaultBlockState());
            a.setPos(a.position().add(0,.5,0));
            BlockPos source=s.origin.offset(4,4,4);
            var input=BlindVibrationPropagation.block(Kind.TOOL,source,s.level.getBlockState(source),source.getCenter(),12,s.level.getGameTime());
            var joined=receive(s,input,a);s.block(3,4,4,Blocks.AIR.defaultBlockState());var separated=receive(s,input,a);
            h.assertTrue(joined.detected(),"Feet on a 1.5-high fence couple into actual collision geometry: "+joined);
            h.assertTrue(separated.outcome()==Outcome.NO_PATH,"Removing the connecting fence disconnects supports: "+separated);
            evidence("fence-contact",joined,separated);
        });
    }
}
