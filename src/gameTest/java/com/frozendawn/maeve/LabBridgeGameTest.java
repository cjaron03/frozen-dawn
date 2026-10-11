package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.labbridge.LabRequest;
import com.frozendawn.labbridge.MinecraftLabRuntime;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabBridgeGameTest {
    private static JsonObject timelineClient(MinecraftLabRuntime runtime, net.minecraft.server.level.ServerPlayer player, long time, int tick) {
        var row=new JsonObject();row.addProperty("session",runtime.session());row.addProperty("player",player.getUUID().toString());
        row.addProperty("gameTime",time);row.addProperty("playerTick",tick);row.addProperty("sampledAtMillis",System.currentTimeMillis());
        row.addProperty("focused",true);row.addProperty("paused",false);row.addProperty("screenOpen",false);
        row.addProperty("syntheticNativeFixture",true);row.addProperty("pose",player.getPose().name());
        row.addProperty("sneaking",player.isShiftKeyDown());row.addProperty("flying",player.getAbilities().flying);return row;
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void blindLabMetadataLoadsThroughWorldList(GameTestHelper helper) {
        java.nio.file.Path temporary = null;
        try {
            temporary = Files.createTempDirectory("blind-world-list-");
            var valid = Files.createDirectories(temporary.resolve("valid")).resolve("level.dat");
            var resource = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blind_lab", "qa/level.dat"));
            try (var stream = resource.open()) { Files.copy(stream, valid); }
            var storage = net.minecraft.world.level.storage.LevelStorageSource.createDefault(temporary);
            var summaries = storage.loadLevelSummaries(storage.findLevelCandidates()).join();
            helper.assertTrue(summaries.size() == 1 && !(summaries.getFirst() instanceof net.minecraft.world.level.storage.LevelSummary.CorruptedLevelSummary),
                    "Python-prepared metadata must pass Minecraft's real world-list reader");
            helper.assertTrue(summaries.getFirst().getLevelName().equals("Blind Architect Lab"), "Prepared world name is readable");
            verifyQuietLabSettings(helper,temporary);
            // Reproduce the owner's failed save: DataVersion alone does not supply
            // the separate Anvil storage version used by world selection.
            var broken = net.minecraft.nbt.NbtIo.readCompressed(valid, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            broken.getCompound("Data").remove("version");
            net.minecraft.nbt.NbtIo.writeCompressed(broken, Files.createDirectories(temporary.resolve("missing-version")).resolve("level.dat"));
            summaries = storage.loadLevelSummaries(storage.findLevelCandidates()).join();
            helper.assertTrue(summaries.stream().anyMatch(summary -> summary instanceof net.minecraft.world.level.storage.LevelSummary.CorruptedLevelSummary),
                    "Missing lowercase version reproduces the world-list corruption report");
            helper.succeed();
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        finally {
            if (temporary != null) {
                try (var paths = Files.walk(temporary)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            }
        }
    }

    // This fixed-coordinate volume overlaps the wilderness course once its
    // source-isolation margin covers 40 blocks. Run after other fixture batches.
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300, batch = "blindWorkbench")
    public static void blindWorkbenchPreservesResultsBeforeChangingScene(GameTestHelper helper) {
        // Use the actual fixed fixture coordinates and generated functions. Restore
        // the bounded construction volume afterward; no integrated client is involved.
        try (var s = new MaeveObservationGameTest.Scene(helper, new net.minecraft.core.BlockPos(0, -44, 0))) {
            var board = s.server.getScoreboard();
            var old = board.getObjective("blind_vib");
            helper.assertTrue(old == null, "Native workbench owns a fresh objective");
            for (int x = 0; x <= 63; x++) for (int y = -5; y <= 10; y++) for (int z = -24; z <= 31; z++)
                s.block(x, y, z, s.level.getBlockState(s.origin.offset(x, y, z)));
            // Preserve the standard fixture mock for earlier views; permit the normal
            // ServerPlayer hurt/event path only in the new native melee replay.
            var player = new MaeveObservationGameTest.TestPlayer(s.level,"BlindWorkbenchQA") {
                @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage) {
                    return !getPersistentData().getBoolean("BlindNativeMeleeDamageable");
                }
            };
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(s.position(15,-9));
            s.level.addNewPlayer(player);s.entities.add(player);
            // FakePlayer normally ignores /tp. Exercise the same teleport as the live scene.
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(s.server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                        net.minecraft.network.PacketSendListener listener) {}
            };
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.addTag("blind_workbench_fixture");
            boolean previousGriefing=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            java.util.function.Function<String,Integer> command = name -> {
                var result = new java.util.concurrent.atomic.AtomicInteger(-999);
                s.server.getCommands().performPrefixedCommand(source.withCallback((ok,value) -> result.set(ok ? value : -Math.abs(value)-1)),
                        "function blind_vibration_lab:" + name);
                return result.get();
            };
            try {
                command.apply("load");
                helper.assertTrue(command.apply("prepare") == 1, "Actual setup reports success");
                var objective = board.getObjective("blind_vib");
                java.util.function.Function<String,Integer> score = holder -> board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),objective).get();
                var actors = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                helper.assertTrue(actors.size()==1 && actors.getFirst().onGround(), "Generated receiver is actually grounded");
                var first = actors.getFirst().getUUID();
                helper.assertTrue(command.apply("next") != 1 && score.apply("#case")==1, "Cannot skip the ready view");
                helper.assertTrue(command.apply("start") == 1 && score.apply("#stage")==2, "Explicit return fixes false FAILED callback");
                helper.assertTrue(command.apply("start") != 1 && score.apply("#timer")==0, "Duplicate Start cannot reset a run");
                for (int timer : new int[]{100,220,340}) {
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(timer);
                    helper.assertTrue(command.apply("impulse")==1, "Bare pulse executes at allowed time");
                    int samples=score.apply("#samples");
                    helper.assertTrue(command.apply("impulse")!=1 && score.apply("#samples")==samples, "Duplicate impulse stays consumed");
                }
                helper.assertTrue(score.apply("#detected")==3 && score.apply("#inconclusive")==0, "Bare stone positive control");
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(480);
                helper.assertTrue(command.apply("finish")==1 && score.apply("#archived")==1, "Completed run archived before next");
                var directory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("blind-lab-results/run-1-"+first);
                var summary=Files.readString(directory.resolve("summary.json"));
                var pulse=Files.readString(directory.resolve("pulse-1.json"));
                Files.writeString(directory.resolve("pulse-1.json"),pulse+" ");
                helper.assertTrue(command.apply("next")!=1 && score.apply("#case")==1, "Changed evidence blocks scene replacement");
                Files.writeString(directory.resolve("pulse-1.json"),pulse);
                command.apply("load");
                helper.assertTrue(score.apply("#stage")==3 && score.apply("#samples")==3, "Reload preserves completed state");
                helper.assertTrue(command.apply("next")==1 && score.apply("#case")==2 && score.apply("#stage")==1, "Next stages carpet in the same save");
                actors=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                helper.assertTrue(actors.size()==1 && !actors.getFirst().getUUID().equals(first)
                        && actors.getFirst().blindController().tracks().isEmpty(), "Next creates a fresh receiver with empty memory");
                helper.assertTrue(score.apply("#samples")==0 && Files.readString(directory.resolve("summary.json")).equals(summary), "Counters reset without changing prior evidence");
                helper.assertTrue(command.apply("start")==1,"Carpet view starts once");
                for(int timer:new int[]{100,220,340}) {
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(timer);
                    helper.assertTrue(command.apply("impulse")==1,"Carpet pulse executes");
                }
                helper.assertTrue(score.apply("#detected")==0 && score.apply("#inconclusive")==0,"Carpet attenuates below threshold without breaking the path");
                var actor=actors.getFirst();var block=new net.minecraft.core.BlockPos(21,-45,5);
                var tool=com.frozendawn.entity.blind.BlindVibrationPropagation.block(com.frozendawn.entity.blind.BlindVibrationPropagation.Kind.TOOL,
                        block,s.level.getBlockState(block),block.getCenter(),6,s.level.getGameTime());
                helper.assertTrue(com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,tool,actor,512).detected(),
                        "Underlying stone still conducts the equal tool impulse; carpet did not create a gap");
                // Verify the calibrated fixture agrees with production foot generation.
                player.setPos(21.5,-43.9375,5.5);player.setOnGround(true);player.setShiftKeyDown(false);
                var real=com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,
                        com.frozendawn.entity.blind.BlindVibrationPropagation.foot(player,com.frozendawn.entity.blind.BlindVibrationPropagation.Kind.FOOTSTEP,6,s.level.getGameTime()),actor,512);
                var calibrated=com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,
                        com.frozendawn.labbridge.LabBlindWorkbench.footImpulse(s.level,block),actor,512);
                helper.assertTrue(real.outcome()==calibrated.outcome() && real.sourceContacts().equals(calibrated.sourceContacts()),"Calibrated contact matches actual grounded player footprint");
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(480);
                helper.assertTrue(command.apply("finish")==1 && score.apply("#archived")==1,"Second result archived independently");
                helper.assertTrue(command.apply("next")!=1 && score.apply("#run")==2,"No automatic wraparound or replay after the pair");
                helper.assertTrue(command.apply("receiver")==1 && score.apply("#run")==3 && score.apply("#stage")==1,
                        "Separate guarded receiver transition preserves the completed source pair");
                actors=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                var lifted=actors.getFirst();
                helper.assertTrue(actors.size()==1 && lifted.getY()==-43.5 && !lifted.onGround() && lifted.isNoGravity(),
                        "Generated receiver is held above the actual support surface");
                helper.assertTrue(!lifted.getUUID().equals(actor.getUUID()) && lifted.blindController().tracks().isEmpty(),
                        "Lifted receiver starts with a fresh identity and empty memory");
                helper.assertTrue(s.level.getBlockState(block.above()).isAir() && s.level.getBlockState(block).is(net.minecraft.world.level.block.Blocks.STONE),
                        "Receiver view restores the original bare source and connected stone path");
                helper.assertTrue(command.apply("start")==1,"Lifted view starts separately");
                for(int tick=1;tick<=340;tick++) {
                    lifted.tick();
                    if(tick!=100 && tick!=220 && tick!=340)continue;
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(tick);
                    helper.assertTrue(lifted.getY()==-43.5 && !lifted.onGround(),"NoAI/NoGravity fixture remains suspended through pulse time");
                    helper.assertTrue(command.apply("impulse")==1,"No receiver contact is a completed sample only in the lifted case");
                }
                helper.assertTrue(score.apply("#samples")==3 && score.apply("#detected")==0 && score.apply("#inconclusive")==0,
                        "Lifted feet cannot receive structural vibration");
                var contactInput=com.frozendawn.labbridge.LabBlindWorkbench.footImpulse(s.level,block);
                lifted.setOnGround(true);
                var geometric=com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,contactInput,lifted,512);
                helper.assertTrue(geometric.outcome()==com.frozendawn.entity.blind.BlindVibrationPropagation.Outcome.NO_RECEIVER_CONTACT,
                        "Actual air gap rejects reception even if the ground flag is wrong");
                lifted.setPos(9.5,-44,5.5);
                helper.assertTrue(com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,contactInput,lifted,512).detected(),
                        "Restoring physical support restores reception with the same source and receiver");
                lifted.setPos(9.5,-43.5,5.5);lifted.setOnGround(false);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#timer"),objective).set(480);
                helper.assertTrue(command.apply("finish")==1 && score.apply("#archived")==1,"Lifted result archived independently");
                helper.assertTrue(command.apply("receiver")!=1 && command.apply("next")!=1,"Completed receiver view cannot loop");
                helper.assertTrue(command.apply("behavior")==1 && score.apply("#run")==4 && score.apply("#stage")==1,
                        "Completed receiver result stages real behavior without replaying the controls");
                actors=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                var live=actors.getFirst();
                helper.assertTrue(live.isNoAi() && live.onGround() && live.blindController().tracks().isEmpty(),"New behavior actor waits for explicit Start");
                var air=com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level,live.getEyePosition(),player.getEyePosition(),24,512);
                helper.assertTrue(!air.reached(),"Generated glass compartments have no connected air path");
                helper.assertTrue(command.apply("start")==1 && !live.isNoAi() && !player.isCreative(),"Start enables ordinary AI and Survival");
                helper.assertTrue(command.apply("start")!=1 && command.apply("finish")!=1 && score.apply("#stage")==2,
                        "Running view cannot restart or finish before 480 ticks");
                int received=0,listening=0;
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);
                    // Deliver vanilla STEP from the real grounded player, without direct sensor injection.
                    if(tick>=100 && tick<=220 && tick%20==0) {
                        player.setPos(tick%40==0 ? 14.5 : 16.5,-44,5.5);
                        player.setOnGround(true);player.setShiftKeyDown(false);player.setSprinting(false);
                        s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,player.position(),
                                net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                        if(live.blindController().lastVibration()!=null && live.blindController().lastVibration().detected())received++;
                    }
                    // Full ticks include the real 40-tick spawn warmup and movement physics.
                    s.level.tickNonPassenger(live);
                    if(live.blindController().signal()!=null && live.blindController().diagnostics().startsWith("blind=LISTEN")) {
                        listening++;
                        helper.assertTrue(live.getNavigation().isDone(),"Vibration LISTEN does not navigate toward the player");
                    }
                    helper.assertTrue(live.blindController().tracks().stream().allMatch(track->track.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                            && track.subject()==null && track.uncertainty()==com.frozendawn.entity.blind.BlindVibrationPropagation.RANGE),"Sealed scene admits only anonymous substrate evidence");
                    helper.assertTrue(command.apply("behavior_tick")==1,"Generated live tick records the real controller");
                }
                helper.assertTrue(received>0 && listening>0,"Real foot events drive LISTEN in generated geometry: received="+received+" listening="+listening);
                helper.assertTrue(score.apply("#stage")==3 && score.apply("#timer")==480 && score.apply("#frames")==480
                        && score.apply("#archived")==1 && live.isNoAi() && player.isCreative(),"End holds actor, restores Creative and archives all frames");
                var liveDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-4-"+live.getUUID());
                var liveSummary=com.google.gson.JsonParser.parseString(Files.readString(liveDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(liveSummary.get("complete").getAsBoolean() && Files.readAllLines(liveDirectory.resolve("trace.jsonl")).size()==480,
                        "Passive trace survives independently of the client log");
                helper.assertTrue(command.apply("behavior")!=1 && command.apply("start")!=1 && command.apply("behavior_tick")!=1,
                        "Completed behavior view cannot replay or keep recording");
                var previousTrace=Files.readString(liveDirectory.resolve("trace.jsonl"));
                Files.writeString(liveDirectory.resolve("trace.jsonl"),previousTrace+" ");
                helper.assertTrue(command.apply("behavior_broad")!=1 && score.apply("#run")==4,"Changed behavior evidence blocks the next scene");
                Files.writeString(liveDirectory.resolve("trace.jsonl"),previousTrace);
                helper.assertTrue(command.apply("behavior_broad")==1 && score.apply("#run")==5 && score.apply("#stage")==1,
                        "Hash-verified completed behavior stages the preserved broad floor");
                actors=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                var broad=actors.getFirst();
                helper.assertTrue(!broad.getUUID().equals(live.getUUID()) && broad.blindController().tracks().isEmpty()
                        && broad.isNoAi() && player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(17.5,-44,5.5))<.001,
                        "Broad view gets a fresh held actor and exact source pose");
                helper.assertTrue(command.apply("start")==1,"Broad view starts only on request");
                int broadReceived=0;
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);
                    if(tick>=100 && tick<=220 && tick%20==0) {
                        player.setPos(17.5,-44,tick%40==0?3.5:7.5);player.setOnGround(true);
                        s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,player.position(),
                                net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                        var result=broad.blindController().lastVibration();
                        helper.assertTrue(result!=null && result.detected(),"Real STEP in generated broad floor: "+result);
                        broadReceived++;
                    }
                    s.level.tickNonPassenger(broad);
                    helper.assertTrue(broad.blindController().tracks().stream().allMatch(track->track.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                            && track.subject()==null),"Broad view cannot obtain hidden coordinates through another sense");
                    helper.assertTrue(command.apply("behavior_tick")==1,"Broad passive trace uses the ordinary controller");
                }
                helper.assertTrue(broadReceived==7 && score.apply("#archived")==1 && score.apply("#frames")==480
                        && score.apply("#stage")==3 && broad.isNoAi() && player.isCreative(),"Broad view archives480 frames and holds at completion");
                helper.assertTrue(command.apply("behavior_broad")!=1 && Files.readString(liveDirectory.resolve("trace.jsonl")).equals(previousTrace),
                        "No broad replay and no changes to the previous behavior trace");
                var broadDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-5-"+broad.getUUID());
                var broadTrace=Files.readString(broadDirectory.resolve("trace.jsonl"));
                Files.writeString(broadDirectory.resolve("trace.jsonl"),broadTrace+" ");
                helper.assertTrue(command.apply("behavior_listen")!=1 && score.apply("#run")==5,"Changed wide-view evidence blocks its successor");
                Files.writeString(broadDirectory.resolve("trace.jsonl"),broadTrace);
                helper.assertTrue(command.apply("behavior_listen")==1 && score.apply("#run")==6 && score.apply("#stage")==1,
                        "Listening repair advances only from an intact completed wide view");
                var listener=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                helper.assertTrue(listener.isNoAi() && listener.blindController().tracks().isEmpty() && !listener.getUUID().equals(broad.getUUID()),
                        "Listening view has a fresh held actor with no inherited knowledge");
                helper.assertTrue(command.apply("start")==1 && command.apply("start")!=1,"Listening view starts exactly once");
                int listeningReceived=0;float minimumBody=Float.POSITIVE_INFINITY,maximumBody=Float.NEGATIVE_INFINITY;
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);
                    if(tick>=100 && tick<=240 && tick%10==0) {
                        player.setPos(17.488726795479693,-44,3.2+((tick-100)/10%9)*.5);player.setOnGround(true);
                        s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,player.position(),
                                net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                        var result=listener.blindController().lastVibration();
                        helper.assertTrue(result!=null && result.detected(),"Moving footprint in generated listening view: "+result);
                        listeningReceived++;
                    }
                    s.level.tickNonPassenger(listener);
                    var signal=listener.blindController().signal();
                    if(signal!=null) {
                        helper.assertTrue(signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                                &&signal.subject()==null&&listener.getNavigation().isDone(),"Visible listening acquires no hidden bearing or path");
                        minimumBody=Math.min(minimumBody,listener.yBodyRot);maximumBody=Math.max(maximumBody,listener.yBodyRot);
                    }
                    helper.assertTrue(command.apply("behavior_tick")==1,"Listening view records native body pose and source contacts");
                }
                helper.assertTrue(listeningReceived==15 && maximumBody-minimumBody>90 && score.apply("#archived")==1
                        &&score.apply("#frames")==480&&listener.isNoAi()&&player.isCreative(),"Fifteen moving contacts sustain visible body turns and archive the complete view");
                helper.assertTrue(command.apply("behavior_listen")!=1 && command.apply("start")!=1
                        &&Files.readString(broadDirectory.resolve("trace.jsonl")).equals(broadTrace),"Completed successor cannot replay or modify the preceding view");
                var listeningDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-6-"+listener.getUUID());
                var listeningTrace=Files.readString(listeningDirectory.resolve("trace.jsonl"));
                Files.writeString(listeningDirectory.resolve("trace.jsonl"),listeningTrace+" ");
                helper.assertTrue(command.apply("behavior_first")!=1&&score.apply("#run")==6,"First-contact view requires intact preceding evidence");
                Files.writeString(listeningDirectory.resolve("trace.jsonl"),listeningTrace);
                var stray=net.minecraft.world.entity.EntityType.PIG.create(s.level);stray.setPos(15.5,-48,-5.5);s.entities.add(stray);
                helper.assertTrue(s.level.addFreshEntity(stray),"Earlier views have no isolation hook");
                helper.assertTrue(command.apply("behavior_first")==1&&score.apply("#run")==7&&score.apply("#stage")==1,
                        "First-contact view preserves and isolates the existing fixture");
                var isolated=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                var isolationFile=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-isolation/run-7-before.json");
                var isolation=Files.readString(isolationFile);
                helper.assertTrue(stray.isRemoved()&&isolation.contains(stray.getUUID().toString())&&isolation.contains("minecraft:pig")
                        &&score.apply("#removed_mobs")==1,"Preexisting stray is saved before removal; fixture actor remains");
                var blocked=net.minecraft.world.entity.EntityType.PIG.create(s.level);blocked.setPos(15.5,-48,-5.5);
                helper.assertTrue(!s.level.addFreshEntity(blocked)&&score.apply("#blocked_mobs")==1,"New ambient source inside the listening radius is refused");
                s.block(-26,-5,5,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                var outside=net.minecraft.world.entity.EntityType.PIG.create(s.level);outside.setPos(-41.5,-48,5.5);outside.setNoAi(true);s.entities.add(outside);
                helper.assertTrue(s.level.addFreshEntity(outside),"The isolation hook does not affect mobs outside its bounded area");
                outside.setPos(15.5,-48,-5.5);
                helper.assertTrue(command.apply("start")!=1&&score.apply("#stage")==1,"Existing entrant makes Start refuse rather than silently contaminating evidence");
                outside.setPos(-41.5,-48,5.5);
                helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Clean isolated view starts once");
                int firstReceived=0;
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(isolated);
                    if(tick>=100&&tick<=240&&tick%10==0) {
                        player.setPos(17.488726795479693,-44,3.2+((tick-100)/10%9)*.5);player.setOnGround(true);
                        s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,player.position(),
                                net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                        var result=isolated.blindController().lastVibration();
                        helper.assertTrue(result!=null&&result.detected(),"STEP after the controller tick retains capacity: "+result);firstReceived++;
                    }
                    var signal=isolated.blindController().signal();
                    helper.assertTrue(signal==null||(signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                            &&signal.subject()==null&&isolated.getNavigation().isDone()),"Only anonymous listening can be selected in the isolated scene");
                    helper.assertTrue(command.apply("behavior_tick")==1,"Isolated view records every tick");
                }
                helper.assertTrue(firstReceived==15&&score.apply("#archived")==1&&score.apply("#inconclusive")==0
                        &&score.apply("#frames")==480&&isolated.isNoAi()&&player.isCreative(),"Clean first-contact view captures all steps and480 frames");
                var isolatedDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-7-"+isolated.getUUID());
                var isolatedSummary=com.google.gson.JsonParser.parseString(Files.readString(isolatedDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(isolatedSummary.getAsJsonObject("isolation").getAsJsonArray("unexpectedMobs").isEmpty()
                        &&isolatedSummary.getAsJsonObject("isolation").get("blockedMobs").getAsInt()==1,"Summary reports blocked spawns and zero unexplained sources");
                helper.assertTrue(command.apply("behavior_first")!=1&&command.apply("start")!=1
                        &&Files.readString(listeningDirectory.resolve("trace.jsonl")).equals(listeningTrace)
                        &&Files.readString(isolationFile).equals(isolation),"Completed view cannot replay or overwrite either preserved record");
                var isolatedTrace=Files.readString(isolatedDirectory.resolve("trace.jsonl"));
                Files.writeString(isolatedDirectory.resolve("trace.jsonl"),isolatedTrace+" ");
                helper.assertTrue(command.apply("behavior_echo")!=1&&score.apply("#run")==7,"Echo successor requires intact completed evidence");
                Files.writeString(isolatedDirectory.resolve("trace.jsonl"),isolatedTrace);
                helper.assertTrue(command.apply("behavior_echo")==1&&score.apply("#run")==8&&score.apply("#stage")==1,
                        "Echo view prepares separate supports and retains lab isolation");
                var echolocator=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                helper.assertTrue(echolocator.isNoAi()&&echolocator.blindController().tracks().isEmpty()
                        &&!echolocator.getUUID().equals(isolated.getUUID())&&score.apply("#isolated")==8,"Fresh echo receiver inherits no observation");
                var disconnected=com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,
                        com.frozendawn.entity.blind.BlindVibrationPropagation.foot(player,
                                com.frozendawn.entity.blind.BlindVibrationPropagation.Kind.FOOTSTEP,6,s.level.getGameTime()),echolocator,512);
                helper.assertTrue(disconnected.outcome()==com.frozendawn.entity.blind.BlindVibrationPropagation.Outcome.NO_PATH,
                        "Player and BA supports are mechanically disconnected: "+disconnected);
                helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Stationary echo view starts once");
                var emissions=new java.util.HashSet<Long>();int echoFrames=0,localized=0;
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(echolocator);
                    var echo=echolocator.blindController().lastEcho();
                    if(echo!=null&&emissions.add(echo.emitted())) {
                        helper.assertTrue(!echo.unknown(),"Generated positive echo geometry fits the unchanged work allowance: "+echo);
                        if(echo.detected())localized++;
                    }
                    var signal=echolocator.blindController().signal();
                    if(signal!=null) {
                        echoFrames++;
                        helper.assertTrue(signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO
                                &&signal.subject().equals(player.getUUID()),"Only the stationary reflector supplies the sensed region");
                        helper.assertTrue(echo!=null&&s.level.getGameTime()>=signal.observed()+3,"Directed response follows return delay");
                    }
                    helper.assertTrue(echolocator.blindController().lastVibration()==null,"No synthetic footstep or substrate cue enters the echo view");
                    helper.assertTrue(command.apply("behavior_tick")==1,"Stationary echo view records every ordinary controller tick");
                }
                helper.assertTrue(localized>=2&&echoFrames>100&&score.apply("#archived")==1&&score.apply("#inconclusive")==0
                        &&score.apply("#frames")==480&&echolocator.isNoAi()&&player.isCreative(),
                        "Repeated real clicks localize the silent stationary body and preserve480 clean frames: "+localized+" / "+echoFrames);
                helper.assertTrue(command.apply("behavior_echo")!=1&&command.apply("start")!=1
                        &&Files.readString(isolatedDirectory.resolve("trace.jsonl")).equals(isolatedTrace),"Completed echo view preserves run7 and cannot replay");
                var echoDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-8-"+echolocator.getUUID());
                var echoTrace=Files.readString(echoDirectory.resolve("trace.jsonl"));
                Files.writeString(echoDirectory.resolve("trace.jsonl"),echoTrace+" ");
                helper.assertTrue(command.apply("behavior_echo_loss")!=1&&score.apply("#run")==8,"Loss view requires intact acquisition evidence");
                Files.writeString(echoDirectory.resolve("trace.jsonl"),echoTrace);
                helper.assertTrue(command.apply("behavior_echo_loss")==1&&score.apply("#run")==9&&score.apply("#stage")==1,
                        "Loss view prepares the same open aperture on disconnected supports");
                var lossReceiver=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                helper.assertTrue(lossReceiver.isNoAi()&&lossReceiver.blindController().tracks().isEmpty()
                        &&!lossReceiver.getUUID().equals(echolocator.getUUID())&&score.apply("#isolated")==9
                        &&score.apply("#echo_closed")==0&&score.apply("#closed_at")==-1,"Fresh receiver starts without inherited contact or closure");
                var lossSupport=com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level,
                        com.frozendawn.entity.blind.BlindVibrationPropagation.foot(player,
                                com.frozendawn.entity.blind.BlindVibrationPropagation.Kind.FOOTSTEP,6,s.level.getGameTime()),lossReceiver,512);
                helper.assertTrue(lossSupport.outcome()==com.frozendawn.entity.blind.BlindVibrationPropagation.Outcome.NO_PATH,
                        "Closure fixture retains disconnected mechanical supports");
                helper.assertTrue(command.apply("behavior_echo_close")!=1,"Closure cannot run before the agreed live timer");
                var window=new net.minecraft.core.BlockPos(11,-43,5);
                s.level.setBlock(window,net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);
                helper.assertTrue(command.apply("start")!=1&&score.apply("#stage")==1,"Start refuses an already obstructed aperture");
                s.level.setBlock(window,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Loss view starts once with the real open aperture");
                var lossEmissions=new java.util.HashSet<Long>();int beforeClose=0,afterClose=0;
                long closedGameTime=Long.MAX_VALUE;boolean pursued=false,investigated=false,searched=false;
                var earlyScan=new java.util.ArrayList<Double>();var lateScan=new java.util.ArrayList<Double>();
                var expiredScan=new java.util.ArrayList<Double>();
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(lossReceiver);
                    var echo=lossReceiver.blindController().lastEcho();
                    if(echo!=null&&lossEmissions.add(echo.emitted())) {
                        helper.assertTrue(!echo.unknown(),"Closure does not exhaust the unchanged acoustic work allowance: "+echo);
                        if(tick<120&&echo.detected())beforeClose++;
                        if(echo.emitted()>closedGameTime) {
                            helper.assertTrue(echo.outcome()==com.frozendawn.entity.blind.BlindAcousticPropagation.Outcome.NO_DIRECT_RETURN,
                                    "A sealed physical aperture yields no direct return: "+echo);afterClose++;
                        }
                    }
                    var signal=lossReceiver.blindController().signal();
                    if(signal!=null) {
                        helper.assertTrue(signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO
                                &&signal.subject().equals(player.getUUID())&&signal.observed()<=closedGameTime,
                                "Every selected contact comes from a pre-closure echo");
                        if(tick<120&&signal.lock(s.level.getGameTime()))pursued=true;
                        double measuredBearing=Math.toDegrees(Math.atan2(-(signal.region().getX()+.5-lossReceiver.getX()),
                                signal.region().getZ()+.5-lossReceiver.getZ()));
                        double offset=net.minecraft.util.Mth.wrapDegrees(lossReceiver.getYHeadRot()-measuredBearing);
                        long age=s.level.getGameTime()-signal.observed();
                        if(age>40&&age<=80)earlyScan.add(offset);
                        if(age>120)lateScan.add(offset);
                    } else if(tick>360) {
                        expiredScan.add((double)net.minecraft.util.Mth.wrapDegrees(lossReceiver.getYHeadRot()));
                    }
                    String diagnostics=lossReceiver.blindController().diagnostics();
                    if(tick>120) {
                        investigated|=diagnostics.startsWith("blind=INVESTIGATE ");
                        searched|=diagnostics.startsWith("blind=SEARCH ");
                    }
                    helper.assertTrue(lossReceiver.blindController().lastVibration()==null,"Closure adds no synthetic sensory input");
                    helper.assertTrue(command.apply("behavior_tick")==1,"Loss view records the ordinary controller and physical window each tick");
                    if(tick==120) {
                        closedGameTime=s.level.getGameTime();
                        helper.assertTrue(score.apply("#echo_closed")==1&&score.apply("#closed_at")==120
                                &&command.apply("behavior_echo_close")!=1,"Aperture closes once at six seconds");
                    }
                }
                helper.assertTrue(beforeClose>=2&&afterClose>=2&&pursued&&investigated&&searched
                        &&lossReceiver.blindController().signal()==null&&lossReceiver.blindController().tracks().isEmpty()
                        &&lossReceiver.blindController().diagnostics().startsWith("blind=WANDER "),
                        "Natural decay loses lock, investigates, searches and expires the contact after physical closure: "+beforeClose+" / "+afterClose);
                helper.assertTrue(score.apply("#archived")==1&&score.apply("#inconclusive")==0&&score.apply("#frames")==480
                        &&lossReceiver.isNoAi()&&player.isCreative(),"Loss view preserves480 clean frames and holds the encounter");
                double earlySpan=java.util.Collections.max(earlyScan)-java.util.Collections.min(earlyScan);
                double lateSpan=java.util.Collections.max(lateScan)-java.util.Collections.min(lateScan);
                double expiredSpan=java.util.Collections.max(expiredScan)-java.util.Collections.min(expiredScan);
                helper.assertTrue(lateSpan>earlySpan+30&&lateSpan>80&&expiredSpan>40,
                        "Actual head scans widen around the ageing estimate and keep surveying after expiry: early="+earlySpan+" late="+lateSpan+" expired="+expiredSpan);
                var lossDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-9-"+lossReceiver.getUUID());
                var lossSummary=com.google.gson.JsonParser.parseString(Files.readString(lossDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(lossSummary.getAsJsonObject("echoClosure").get("allGlass").getAsBoolean()
                        &&lossSummary.getAsJsonObject("echoClosure").get("closedAt").getAsInt()==120
                        &&lossSummary.getAsJsonObject("isolation").getAsJsonArray("unexpectedMobs").isEmpty(),"Saved summary records physical closure and clean isolation");
                helper.assertTrue(command.apply("behavior_echo_loss")!=1&&command.apply("start")!=1
                        &&Files.readString(echoDirectory.resolve("trace.jsonl")).equals(echoTrace),"Completed loss view preserves acquisition evidence and refuses replay");
                var lossTrace=Files.readString(lossDirectory.resolve("trace.jsonl"));
                Files.writeString(lossDirectory.resolve("trace.jsonl"),lossTrace+" ");
                helper.assertTrue(command.apply("behavior_echo_search")!=1&&score.apply("#run")==9,"Search repeat requires the intact loss-view record");
                Files.writeString(lossDirectory.resolve("trace.jsonl"),lossTrace);
                helper.assertTrue(command.apply("behavior_echo_search")==1&&score.apply("#run")==10&&score.apply("#stage")==1,
                        "Search repeat prepares the same physical closure fixture without altering completed records");
                var searchReceiver=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                helper.assertTrue(searchReceiver.isNoAi()&&searchReceiver.blindController().tracks().isEmpty()
                        &&!searchReceiver.getUUID().equals(lossReceiver.getUUID())&&score.apply("#isolated")==10,
                        "Search repeat uses a fresh isolated actor");
                helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Search repeat starts once");
                for(int tick=1;tick<=480;tick++) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(searchReceiver);
                    helper.assertTrue(command.apply("behavior_tick")==1,"Search repeat records all ordinary controller ticks");
                }
                helper.assertTrue(score.apply("#archived")==1&&score.apply("#inconclusive")==0&&score.apply("#frames")==480
                        &&score.apply("#echo_closed")==1&&score.apply("#closed_at")==120
                        &&searchReceiver.isNoAi()&&player.isCreative(),"Search repeat preserves a complete unchanged closure protocol");
                helper.assertTrue(command.apply("behavior_echo_search")!=1&&command.apply("start")!=1
                        &&Files.readString(lossDirectory.resolve("trace.jsonl")).equals(lossTrace),"Search repeat cannot overwrite or replay the completed loss trace");
                var searchDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-10-"+searchReceiver.getUUID());
                var searchTrace=Files.readString(searchDirectory.resolve("trace.jsonl"));
                Files.writeString(searchDirectory.resolve("trace.jsonl"),searchTrace+" ");
                helper.assertTrue(command.apply("behavior_base")!=1&&score.apply("#run")==10,
                        "Base transition refuses a damaged search-view archive before touching the scene");
                Files.writeString(searchDirectory.resolve("trace.jsonl"),searchTrace);
                helper.assertTrue(command.apply("behavior_base")==1&&score.apply("#run")==11&&score.apply("#stage")==1,
                        "Base transition preserves completed view10 and prepares the actual underground fixture");
                var baseReceiver=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                baseReceiver.getRandom().setSeed(438L);
                helper.assertTrue(baseReceiver.isNoAi()&&baseReceiver.onGround()&&baseReceiver.blindController().tracks().isEmpty()
                        &&baseReceiver.position().equals(new net.minecraft.world.phys.Vec3(9.5,-44,.5))
                        &&player.position().equals(new net.minecraft.world.phys.Vec3(22.5,-44,.5))&&score.apply("#isolated")==11,
                        "Controlled base begins below Y0 with a fresh ignorant actor outside and a ready participant inside");
                helper.assertTrue(s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),
                        "Base hunting permits the real production block breaker");
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);
                helper.assertTrue(command.apply("start")!=1&&score.apply("#stage")==1,"Start refuses disabled mining");
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true,s.server);
                helper.assertTrue(com.frozendawn.event.MobFreezeHandler.getFullSetTier(player)==3
                        &&player.getInventory().items.stream().anyMatch(stack->stack.is(com.frozendawn.init.ModItems.O2_TANK_MK3.get())
                        &&stack.getOrDefault(com.frozendawn.init.ModDataComponents.O2_LEVEL.get(),0)==3600),
                        "Generated Phase6 base supplies real EVA climate protection and a fresh oxygen tank");
                var helmet=player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.item.ItemStack.EMPTY);
                helper.assertTrue(command.apply("start")!=1&&score.apply("#stage")==1,"Start refuses missing environmental protection");
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
                helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Base starts exactly once");
                int baseTicks=0;
                while(score.apply("#stage")==2&&baseTicks<1800) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(baseReceiver);
                    helper.assertTrue(command.apply("behavior_base_tick")==1,"Base pass records actual entity ticks");
                    if(baseTicks++==0)helper.assertTrue(command.apply("behavior_base_tick")!=1&&score.apply("#timer")==1,
                            "A repeated recorder call in the same world tick cannot advance or duplicate evidence");
                }
                var baseDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-11-"+baseReceiver.getUUID());
                var baseSummary=com.google.gson.JsonParser.parseString(Files.readString(baseDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(score.apply("#base_acquired")>0&&score.apply("#base_broken")>score.apply("#base_acquired")
                        &&score.apply("#base_entered")>score.apply("#base_broken")&&score.apply("#base_close")>0,
                        "Production sensing leads to physical mining, entry and approach in the exact base: "+baseSummary);
                helper.assertTrue(baseReceiver.successfulBreakCount()>=2&&baseReceiver.getTarget()==null,
                        "Real cuts open the impassable slot without a live vanilla target");
                helper.assertTrue(score.apply("#stage")==3&&score.apply("#archived")==1&&score.apply("#inconclusive")==0
                        &&score.apply("#frames")==baseTicks&&baseTicks==score.apply("#base_entered")+200
                        &&baseSummary.get("endReason").getAsString().equals("ENTRY_OBSERVATION_COMPLETE")
                        &&baseReceiver.isNoAi()&&player.isCreative(),"Encounter is held after ten ordinary seconds inside, with complete evidence");
                helper.assertTrue(command.apply("behavior_base")!=1&&command.apply("start")!=1
                        &&Files.readString(searchDirectory.resolve("trace.jsonl")).equals(searchTrace),
                        "Completed base cannot replay or overwrite the accepted search view");
                var completedBaseTrace=Files.readString(baseDirectory.resolve("trace.jsonl"));
                var leftoverItem=new net.minecraft.world.entity.item.ItemEntity(s.level,16.5,-43,2.5,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON,3));
                var leftoverOrb=new net.minecraft.world.entity.ExperienceOrb(s.level,25.5,-43,3.5,3);
                s.level.addFreshEntity(leftoverItem);s.level.addFreshEntity(leftoverOrb);
                s.entities.add(leftoverItem);s.entities.add(leftoverOrb);
                helper.assertTrue(s.level.getEntity(leftoverItem.getUUID())!=null&&s.level.getEntity(leftoverOrb.getUUID())!=null,
                        "Base activity may leave real item and XP objects before isolated sensing preparation");
                var previousChannelDirectory=baseDirectory;
                String[] channelTransitions={"channels_hearing","channels_hearing_closed","channels_vibration",
                        "channels_vibration_gap","channels_echo","channels_echo_closed"};
                for(int channelRun=12;channelRun<=17;channelRun++) {
                    String transition=channelTransitions[channelRun-12];
                    String predecessorChannelTrace=Files.readString(previousChannelDirectory.resolve("trace.jsonl"));
                    Files.writeString(previousChannelDirectory.resolve("trace.jsonl"),predecessorChannelTrace+"\n");
                    helper.assertTrue(command.apply(transition)!=1&&score.apply("#run")==channelRun-1,
                            "Channel transition refuses corrupt predecessor trace: "+channelRun);
                    Files.writeString(previousChannelDirectory.resolve("trace.jsonl"),predecessorChannelTrace);
                    helper.assertTrue(command.apply(transition)==1&&score.apply("#run")==channelRun&&score.apply("#stage")==1,
                            "Guarded channel transition prepares a fresh matched scene: "+channelRun);
                    var receiver=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                            new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    receiver.getRandom().setSeed(439L+(channelRun-12)/2);
                    helper.assertTrue(receiver.isNoAi()&&receiver.onGround()&&receiver.blindController().tracks().isEmpty()
                            &&score.apply("#isolated")==channelRun,"Each paired scene starts with an ignorant grounded actor");
                    if(channelRun==12) {
                        helper.assertTrue(s.level.getEntity(leftoverItem.getUUID())==null&&s.level.getEntity(leftoverOrb.getUUID())==null
                                &&score.apply("#removed_sources")>=2,"Preparation removes preserved item/XP sources before terrain changes");
                        var isolationDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-isolation");
                        String isolationBefore=Files.readString(isolationDirectory.resolve("run-12-before.json"));
                        helper.assertTrue(isolationBefore.contains(leftoverItem.getUUID().toString())&&isolationBefore.contains(leftoverOrb.getUUID().toString())
                                &&isolationBefore.contains("raw_iron"),"Removed objects retain their identity and contents in the preflight archive");
                        var generatedDrop=new net.minecraft.world.entity.item.ItemEntity(s.level,14.5,-43,5.5,
                                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL,2));
                        s.entities.add(generatedDrop);
                        helper.assertTrue(!s.level.addFreshEntity(generatedDrop)&&s.level.getEntity(generatedDrop.getUUID())==null
                                &&score.apply("#blocked_sources")>=1,"Drops created after terrain preparation cannot enter the sensing view");
                        try(var paths=Files.list(isolationDirectory)) {
                            var preservedDrop=paths.filter(path->path.getFileName().toString().contains(generatedDrop.getUUID().toString())).findFirst();
                            helper.assertTrue(preservedDrop.isPresent()&&Files.readString(preservedDrop.get()).contains("minecraft:coal"),
                                    "Blocked new drop is archived before cancellation");
                        }
                        // A moved-in object bypasses join interception: Start must still reject it.
                        var movedObject=new net.minecraft.world.entity.item.ItemEntity(s.level,100,-43,5.5,
                                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
                        s.level.addFreshEntity(movedObject);s.entities.add(movedObject);movedObject.setPos(14.5,-43,5.5);
                        helper.assertTrue(command.apply("start")!=1&&score.apply("#stage")==1,
                                "Passive source audit refuses an object entering without a new join event");
                        movedObject.discard();
                    }
                    helper.assertTrue(command.apply("start")==1&&command.apply("start")!=1,"Channel starts once: "+channelRun);
                    int selectedFrames=0;int listeningFrames=0;int incomingVibrations=0;
                    var mined=new net.minecraft.core.BlockPos(14,-43,5);
                    for(int channelTick=1;channelTick<=480;channelTick++) {
                        s.clock(s.level.getGameTime()+1);
                        if(channelRun<=13) {
                            if(channelTick==81)player.gameMode.handleBlockBreakAction(mined,
                                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                                    net.minecraft.core.Direction.SOUTH,s.level.getMaxBuildHeight(),0);
                            if(channelTick==240)player.gameMode.handleBlockBreakAction(mined,
                                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                                    net.minecraft.core.Direction.SOUTH,s.level.getMaxBuildHeight(),1);
                            player.gameMode.tick();
                        } else if(channelRun<=15&&channelTick>=100&&channelTick<=220&&channelTick%20==0) {
                            player.setPos(17.5,-44,channelTick%40==0?4.5:6.5);
                            player.setOnGround(true);player.setShiftKeyDown(false);player.setSprinting(false);
                            s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,player.position(),
                                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                            if(receiver.blindController().lastVibration()!=null&&receiver.blindController().lastVibration().detected())incomingVibrations++;
                        }
                        s.level.tickNonPassenger(receiver);
                        if(receiver.blindController().signal()!=null)selectedFrames++;
                        if(receiver.blindController().diagnostics().startsWith("blind=LISTEN"))listeningFrames++;
                        helper.assertTrue(command.apply("channels_tick")==1,"Channel captures ordinary entity tick: "+channelRun);
                        if(channelTick==1)helper.assertTrue(command.apply("channels_tick")!=1&&score.apply("#timer")==1,
                                "Duplicate capture cannot advance the timer");
                    }
                    var channelDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("blind-lab-results/run-"+channelRun+"-"+receiver.getUUID());
                    var channelSummary=com.google.gson.JsonParser.parseString(Files.readString(channelDirectory.resolve("summary.json"))).getAsJsonObject();
                    helper.assertTrue(score.apply("#stage")==3&&score.apply("#archived")==1&&score.apply("#inconclusive")==0
                            &&channelSummary.get("complete").getAsBoolean()&&receiver.isNoAi()&&player.isCreative(),
                            "All 480 frames preserve channel isolation and close safely: "+channelRun+" "+channelSummary);
                    var entries=receiver.decisionJournal().entries();
                    helper.assertTrue(channelSummary.getAsJsonObject("isolation").get("sourcesClean").getAsBoolean()
                            &&channelSummary.getAsJsonObject("isolation").getAsJsonArray("unexpectedSourceObjects").isEmpty()
                            &&entries.stream().noneMatch(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("kind=LANDING")),
                            "All matched views retain a clean source inventory and no leftover landing impulses");
                    boolean positive=channelRun%2==0;
                    helper.assertTrue(positive?selectedFrames>0:selectedFrames==0,
                            "Paired native channel response follows physical connectivity: run="+channelRun+" selected="+selectedFrames);
                    if(channelRun<=13)helper.assertTrue(entries.stream().anyMatch(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("kind=TOOL"))
                            &&entries.stream().noneMatch(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("outcome=DETECTED")),
                            "Real accepted mining is recorded, but disconnected structural contacts cannot contribute");
                    if(channelRun==14)helper.assertTrue(incomingVibrations>0&&listeningFrames>0,
                            "Connected sealed room produces anonymous listening from real STEP delivery");
                    if(channelRun==15)helper.assertTrue(incomingVibrations==0&&listeningFrames==0,
                            "Matched floor gap removes structural reception");
                    if(channelRun<=15||channelRun==17)helper.assertTrue(entries.stream().noneMatch(e->e.event().equals("BLIND_ECHO_RETURN")&&e.detail().contains("outcome=ADMITTED")),
                            "Geometry rejects every competing direct echo");
                    helper.assertTrue(receiver.decisionJournal().dropped()==0&&Files.readString(previousChannelDirectory.resolve("trace.jsonl")).equals(predecessorChannelTrace)
                            &&command.apply(transition)!=1&&command.apply("start")!=1,"Evidence retained and replay refused");
                    previousChannelDirectory=channelDirectory;
                }
                var sealedTrace = Files.readString(previousChannelDirectory.resolve("trace.jsonl"));
                Files.writeString(previousChannelDirectory.resolve("trace.jsonl"), sealedTrace + " ");
                helper.assertTrue(command.apply("cave") != 1 && score.apply("#run") == 17,
                        "Free cave cannot replace a sealed view whose evidence has changed");
                Files.writeString(previousChannelDirectory.resolve("trace.jsonl"), sealedTrace);
                helper.assertTrue(command.apply("cave") == 1 && score.apply("#run") == 18 && score.apply("#stage") == 1,
                        "Intact six-channel suite opens the cave exactly once");
                var caveBounds = new net.minecraft.world.phys.AABB(0,-49,-24,64,-33,32);
                var caveActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,caveBounds).getFirst();
                caveActor.getRandom().setSeed(442L);
                helper.assertTrue(caveActor.isNoAi() && caveActor.blindController().tracks().isEmpty()
                        && player.position().equals(new net.minecraft.world.phys.Vec3(23.5,-44,.5)),"Cave stages a fresh ignorant held actor behind stone cover");
                s.settleLight();
                var visited = new java.util.HashSet<net.minecraft.core.BlockPos>();
                var frontier = new java.util.ArrayDeque<net.minecraft.core.BlockPos>();
                frontier.add(new net.minecraft.core.BlockPos(8,-44,0));
                while (!frontier.isEmpty()) {
                    var pos=frontier.removeFirst();
                    if (!caveBounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)) || !visited.add(pos)) continue;
                    if (!s.level.getBlockState(pos).isAir() || !s.level.getBlockState(pos.above()).isAir()
                            || s.level.getBlockState(pos.below()).isAir()) { visited.remove(pos); continue; }
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        var next=pos.relative(direction);
                        if (!visited.contains(next) && s.level.getBlockState(next).isAir()
                                && s.level.getBlockState(next.above()).isAir()) frontier.add(next);
                    }
                }
                for (var pos : java.util.List.of(new net.minecraft.core.BlockPos(23,-44,0),new net.minecraft.core.BlockPos(32,-44,-17),
                        new net.minecraft.core.BlockPos(29,-44,20),new net.minecraft.core.BlockPos(43,-44,0)))
                    helper.assertTrue(visited.contains(pos),"Actor and owner can walk through the connected cave loop: "+pos);
                for (var pos : java.util.List.of(new net.minecraft.core.BlockPos(32,-43,-17),new net.minecraft.core.BlockPos(29,-43,20)))
                    helper.assertTrue(s.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos)==0
                            &&s.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos)==0,"Side chambers have actual zero light: "+pos);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,200));
                helper.assertTrue(command.apply("start") != 1 && score.apply("#timer")==0,"Night vision refuses the darkness handoff");
                player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                helper.assertTrue(command.apply("start")==1 && command.apply("start")!=1 && !caveActor.isNoAi(),"Cave starts ordinary AI and Survival exactly once");
                var caveDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-18-"+caveActor.getUUID());
                var caveStart=com.google.gson.JsonParser.parseString(Files.readString(caveDirectory.resolve("start.json"))).getAsJsonObject();
                helper.assertTrue(!caveStart.get("diagnosticDirectEyeRay").getAsBoolean(),"Starting rock pillar actually occludes the participant");
                // Native free-roam check: a quiet distant player supplies no scripted sensory input or route.
                player.setPos(51.5,-44,.5);player.setOnGround(true);
                var drop=new net.minecraft.world.entity.item.ItemEntity(s.level,51.5,-44,.5,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                helper.assertTrue(s.level.addFreshEntity(drop),"Ordinary encounter drops are admitted rather than filtered");s.entities.add(drop);
                var caveOrigin=caveActor.position();double caveTravel=0;
                for(int tick=1;tick<=600;tick++) {
                    s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(caveActor);s.level.tickNonPassenger(drop);
                    caveTravel=Math.max(caveTravel,caveActor.position().distanceTo(caveOrigin));
                    helper.assertTrue(command.apply("cave_tick")==1 && score.apply("#stage")==2,"Free movement remains captured without an early contact cutoff");
                    if(tick==1)helper.assertTrue(command.apply("cave_tick")!=1 && score.apply("#timer")==1,"Duplicate cave frames are refused");
                }
                helper.assertTrue(caveTravel>1 && caveActor.getTarget()==null,"Ordinary WANDER moves in the cave without a forced target: "+caveTravel);
                helper.assertTrue(command.apply("cave_stop")==1 && caveActor.isNoAi() && player.isCreative() && score.apply("#archived")==1,
                        "Owner stop archives real movement before safely holding the actor");
                var caveSummary=com.google.gson.JsonParser.parseString(Files.readString(caveDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(caveSummary.get("frames").getAsInt()==600 && caveSummary.get("endReason").getAsString().equals("OWNER_STOPPED")
                        &&caveSummary.get("journalDropped").getAsInt()==0 && !caveSummary.getAsJsonObject("isolation").getAsJsonArray("unexpectedSourceObjects").isEmpty(),
                        "Dropped items and complete production decisions remain visible in the capture");
                helper.assertTrue(command.apply("cave_stop")!=1 && command.apply("cave")!=1 && command.apply("start")!=1,
                        "A completed cave cannot restart or overwrite either encounter");
                String preservedCaveTrace=Files.readString(caveDirectory.resolve("trace.jsonl"));
                Files.writeString(caveDirectory.resolve("trace.jsonl"),preservedCaveTrace+" ");
                helper.assertTrue(command.apply("cave_range")!=1&&score.apply("#run")==18,"Range replay refuses altered cave evidence");
                Files.writeString(caveDirectory.resolve("trace.jsonl"),preservedCaveTrace);
                String preservedCaveDecisions=Files.readString(caveDirectory.resolve("decisions.json"));
                Files.writeString(caveDirectory.resolve("decisions.json"),preservedCaveDecisions+" ");
                helper.assertTrue(command.apply("cave_range")!=1&&score.apply("#run")==18,"Range replay verifies both trace and decision hashes");
                Files.writeString(caveDirectory.resolve("decisions.json"),preservedCaveDecisions);
                helper.assertTrue(command.apply("cave_range")==1&&score.apply("#run")==19&&score.apply("#stage")==1&&caveActor.isRemoved(),
                        "An independently preserved range replay resets only the approved cave after capture");
                var rangeActor=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,caveBounds).getFirst();
                helper.assertTrue(command.apply("start")==1,"Range replay starts once with the same cave geometry");
                s.clock(s.level.getGameTime()+1);helper.assertTrue(command.apply("cave_tick")==1&&command.apply("cave_range_stop")==1,"Range replay has independent capture and explicit ending");
                var rangeDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-19-"+rangeActor.getUUID());
                helper.assertTrue(Files.exists(rangeDirectory.resolve("summary.json"))&&Files.readString(caveDirectory.resolve("trace.jsonl")).equals(preservedCaveTrace)
                        &&Files.readString(caveDirectory.resolve("decisions.json")).equals(preservedCaveDecisions),"Range replay preserves every earlier cave byte");
                // Preserve the cave terrain for its independent ending controls below.
                var caveTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                        .forEach(pos -> caveTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                var posturePredecessor = rangeDirectory;
                var posturePreviousActor = rangeActor;
                java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> standingTerrain = null;
                for (int postureRun = 20; postureRun <= 21; postureRun++) {
                    String transition = postureRun == 20 ? "posture_standing" : "posture_crouched";
                    posturePreviousActor.setNoAi(false);
                    helper.assertTrue(command.apply(transition) != 1 && score.apply("#run") == postureRun - 1,
                            "A live unheld predecessor cannot be replaced");
                    posturePreviousActor.setNoAi(true);
                    posturePreviousActor.discard();
                    for (String evidence : java.util.List.of("trace.jsonl", "decisions.json")) {
                        String original = Files.readString(posturePredecessor.resolve(evidence));
                        Files.writeString(posturePredecessor.resolve(evidence), original + " ");
                        helper.assertTrue(command.apply(transition) != 1 && score.apply("#run") == postureRun - 1,
                                "Posture transition refuses altered predecessor " + evidence);
                        Files.writeString(posturePredecessor.resolve(evidence), original);
                    }
                    helper.assertTrue(command.apply(transition) == 1 && score.apply("#run") == postureRun
                            && score.apply("#stage") == 1, "A completed predecessor stages each posture once");
                    var postureTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                    net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                            .forEach(pos -> postureTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                    if (standingTerrain == null) standingTerrain = postureTerrain;
                    else helper.assertTrue(postureTerrain.equals(standingTerrain), "Both postures have identical blocks and aperture");
                    var receiver = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                            new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    receiver.getRandom().setSeed(449L);
                    helper.assertTrue(receiver.isNoAi() && receiver.onGround() && receiver.blindController().tracks().isEmpty()
                            && score.apply("#isolated") == postureRun, "Each posture starts grounded with empty memory");
                    player.setShiftKeyDown(postureRun == 21);
                    player.setPose(postureRun == 20 ? net.minecraft.world.entity.Pose.STANDING : net.minecraft.world.entity.Pose.CROUCHING);
                    helper.assertTrue(command.apply("start") == 1 && command.apply("start") != 1, "Each posture starts only once");
                    for (int tick = 1; tick <= 480; tick++) {
                        s.clock(s.level.getGameTime() + 1);
                        s.level.tickNonPassenger(receiver);
                        helper.assertTrue(command.apply("channels_tick") == 1, "Posture view records ordinary entity ticks");
                    }
                    var postureDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("blind-lab-results/run-" + postureRun + "-" + receiver.getUUID());
                    var postureSummary = com.google.gson.JsonParser.parseString(Files.readString(postureDirectory.resolve("summary.json"))).getAsJsonObject();
                    helper.assertTrue(score.apply("#stage") == 3 && score.apply("#archived") == 1
                            && score.apply("#inconclusive") == 0 && postureSummary.get("complete").getAsBoolean()
                            && postureSummary.get("frames").getAsInt() == 480 && receiver.isNoAi() && player.isCreative(),
                            "All posture frames are isolated and safely held: " + postureRun + " " + postureSummary);
                    helper.assertTrue(postureSummary.get("playerPose").getAsString().equals(postureRun == 20 ? "STANDING" : "CROUCHING")
                            && postureSummary.get("playerSneaking").getAsBoolean() == (postureRun == 21)
                            && postureSummary.get("postureScored").getAsBoolean(), "Recorded posture comes from the actual player body");
                    var entries = receiver.decisionJournal().entries();
                    helper.assertTrue(entries.stream().anyMatch(e -> e.event().equals("BLIND_ECHO_RETURN") && e.detail().contains("outcome=ADMITTED"))
                            && entries.stream().noneMatch(e -> e.event().equals("BLIND_AIR") || e.event().equals("BLIND_VIBRATION")),
                            "Each exposed posture permits a physical echo with no passive source");
                    helper.assertTrue(command.apply(transition) != 1 && command.apply("start") != 1, "Completed posture cannot repeat");
                    posturePredecessor = postureDirectory;
                    posturePreviousActor = receiver;
                    if (postureRun == 21) receiver.discard();
                }
                java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> walkingTerrain = null;
                for (int stepRun = 22; stepRun <= 23; stepRun++) {
                    String transition = stepRun == 22 ? "steps_walk" : "steps_crouched";
                    for (String evidence : java.util.List.of("trace.jsonl", "decisions.json")) {
                        String original = Files.readString(posturePredecessor.resolve(evidence));
                        Files.writeString(posturePredecessor.resolve(evidence), original + " ");
                        helper.assertTrue(command.apply(transition) != 1 && score.apply("#run") == stepRun - 1,
                                "Footstep transition preserves its predecessor " + evidence);
                        Files.writeString(posturePredecessor.resolve(evidence), original);
                    }
                    helper.assertTrue(command.apply(transition) == 1 && score.apply("#run") == stepRun,
                            "Guarded walking transition stages a fresh receiver");
                    var stepTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                    net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                            .forEach(pos -> stepTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                    if (walkingTerrain == null) walkingTerrain = stepTerrain;
                    else helper.assertTrue(stepTerrain.equals(walkingTerrain), "Walking pair uses identical connected support and sealed air");
                    var receiver = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                            new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    receiver.getRandom().setSeed(451L);
                    player.setShiftKeyDown(stepRun == 23); player.setSprinting(false); player.setOnGround(true);
                    player.setPose(stepRun == 22 ? net.minecraft.world.entity.Pose.STANDING : net.minecraft.world.entity.Pose.CROUCHING);
                    helper.assertTrue(command.apply("start") == 1 && command.apply("start") != 1, "Walking view starts only once");
                    double direction = 1;
                    for (int tick = 1; tick <= 480; tick++) {
                        s.clock(s.level.getGameTime() + 1);
                        if (tick > 80 && tick <= 240) {
                            if (player.getZ() >= 7.1) direction = -1;
                            if (player.getZ() <= 3.9) direction = 1;
                            player.setPos(17.5, -44, player.getZ() + direction * .08);
                            player.setOnGround(true);
                            if (stepRun == 22 && (tick - 80) % 9 == 0)
                                s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP, player.position(),
                                        net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                        }
                        // Real crouch hook derives cadence from measured displacement; no STEP is injected for run23.
                        com.frozendawn.event.BlindArchitectEvents.sneakSteps(
                                new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                        s.level.tickNonPassenger(receiver);
                        helper.assertTrue(command.apply("channels_tick") == 1, "Walking recorder captures real channel decisions");
                        helper.assertTrue(receiver.blindController().tracks().stream().allMatch(t ->
                                t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                                && t.subject() == null && t.travel() == 0
                                && t.uncertainty() == com.frozendawn.entity.blind.BlindVibrationPropagation.RANGE),
                                "Structural listening retains no source identity, range or pursuit destination");
                    }
                    var stepDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("blind-lab-results/run-" + stepRun + "-" + receiver.getUUID());
                    var stepSummary = com.google.gson.JsonParser.parseString(Files.readString(stepDirectory.resolve("summary.json"))).getAsJsonObject();
                    helper.assertTrue(score.apply("#archived") == 1 && score.apply("#inconclusive") == 0
                            && stepSummary.get("complete").getAsBoolean() && stepSummary.get("frames").getAsInt() == 480
                            && Math.abs(stepSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                            "Full walking capture measures the matched route and actual pose: " + stepRun + " " + stepSummary);
                    var entries = receiver.decisionJournal().entries();
                    helper.assertTrue(entries.stream().filter(e -> e.event().equals("BLIND_VIBRATION") && e.detail().contains("kind=FOOTSTEP")).count() >= 10
                            && entries.stream().noneMatch(e -> e.event().equals("BLIND_ECHO_RETURN") && e.detail().contains("outcome=ADMITTED")),
                            "Grounded movement emits structural footsteps while sealed geometry denies echoes");
                    if (stepRun == 22) helper.assertTrue(entries.stream().anyMatch(e -> e.event().equals("BLIND_VIBRATION") && e.detail().contains("outcome=DETECTED")),
                            "Normal steps reach the receiver in this connected fixture");
                    else helper.assertTrue(entries.stream().anyMatch(e -> e.event().equals("BLIND_AIR") && e.detail().contains("kind=QUIET_STEP")),
                            "Actual crouch displacement reaches the production quiet-step hook");
                    helper.assertTrue(command.apply(transition) != 1 && command.apply("start") != 1, "Completed walking view cannot repeat");
                    posturePredecessor = stepDirectory;
                    if (stepRun == 23) receiver.discard();
                }
                // A quiet non-walking control must not count as a successful footstep view.
                for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(22);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                player.setPos(17.5,-44,5.5); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                var quietControl = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                quietControl.setPos(9.5,-44,5.5); quietControl.setBlindArchitect(true); quietControl.setNoAi(true);
                quietControl.setOnGround(true); quietControl.addTag("blind_lab_actor"); s.level.addFreshEntity(quietControl); s.entities.add(quietControl);
                helper.assertTrue(command.apply("start") == 1, "Independent missing-movement control starts");
                for (int tick = 1; tick <= 480; tick++) {
                    s.clock(s.level.getGameTime() + 1); s.level.tickNonPassenger(quietControl);
                    helper.assertTrue(command.apply("channels_tick") == 1, "Quiet control preserves its full trace");
                }
                helper.assertTrue(score.apply("#stage") == 3 && score.apply("#archived") == 0 && score.apply("#inconclusive") > 0,
                        "No walking cannot produce a false passing footstep view");
                quietControl.discard();
                // A separately captured wrong-pose view23 can be preserved and retried as run24.
                for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(23);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                player.setPos(17.5,-44,5.5); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                var wrongPose = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                wrongPose.setPos(9.5,-44,5.5); wrongPose.setBlindArchitect(true); wrongPose.setNoAi(true);
                wrongPose.setOnGround(true); wrongPose.addTag("blind_lab_actor"); wrongPose.getRandom().setSeed(451L);
                s.level.addFreshEntity(wrongPose); s.entities.add(wrongPose);
                helper.assertTrue(command.apply("start") == 1 && command.apply("steps_crouched_retry") != 1,
                        "Retry cannot interrupt an active incomplete view");
                double retryDirection = 1;
                for (int tick = 1; tick <= 480; tick++) {
                    s.clock(s.level.getGameTime() + 1);
                    if (tick > 80 && tick <= 240) {
                        if (player.getZ() >= 7.1) retryDirection = -1;
                        if (player.getZ() <= 3.9) retryDirection = 1;
                        player.setPos(17.5, -44, player.getZ() + retryDirection * .08); player.setOnGround(true);
                        if ((tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                    }
                    s.level.tickNonPassenger(wrongPose);
                    helper.assertTrue(command.apply("channels_tick") == 1, "Wrong-pose view preserves all frames");
                }
                helper.assertTrue(score.apply("#archived") == 0 && score.apply("#inconclusive") == 400,
                        "Standing movement cannot pass a crouch comparison");
                var failedDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("blind-lab-results/run-23-" + wrongPose.getUUID());
                var failedEvidence = new java.util.HashMap<String, String>();
                for (String evidence : java.util.List.of("start.json", "trace.jsonl", "decisions.json", "summary.json"))
                    failedEvidence.put(evidence, Files.readString(failedDirectory.resolve(evidence)));
                wrongPose.setNoAi(false);
                helper.assertTrue(command.apply("steps_crouched_retry") != 1, "Retry refuses an unheld predecessor");
                wrongPose.setNoAi(true);
                for (String evidence : java.util.List.of("trace.jsonl", "decisions.json")) {
                    Files.writeString(failedDirectory.resolve(evidence), failedEvidence.get(evidence) + " ");
                    helper.assertTrue(command.apply("steps_crouched_retry") != 1 && score.apply("#run") == 23,
                            "Retry refuses damaged inconclusive evidence: " + evidence);
                    Files.writeString(failedDirectory.resolve(evidence), failedEvidence.get(evidence));
                }
                helper.assertTrue(command.apply("steps_crouched_retry") == 1 && score.apply("#run") == 24
                        && score.apply("#stage") == 1 && wrongPose.isRemoved(), "Preserved inconclusive23 stages a new run24 once");
                var retryTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                        .forEach(pos -> retryTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                helper.assertTrue(retryTerrain.equals(walkingTerrain), "Retry has identical sealed air and connected stone");
                var retryActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                        new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                helper.assertTrue(retryActor.isNoAi() && retryActor.onGround() && retryActor.blindController().tracks().isEmpty(),
                        "Retry begins with a fresh grounded actor and empty memory");
                retryActor.getRandom().setSeed(451L); player.setShiftKeyDown(true);
                player.setPose(net.minecraft.world.entity.Pose.CROUCHING); player.setOnGround(true); player.setSprinting(false);
                helper.assertTrue(command.apply("start") == 1 && command.apply("start") != 1, "Retry starts once");
                retryDirection = 1;
                for (int tick = 1; tick <= 480; tick++) {
                    s.clock(s.level.getGameTime() + 1);
                    if (tick > 80 && tick <= 240) {
                        if (player.getZ() >= 7.1) retryDirection = -1;
                        if (player.getZ() <= 3.9) retryDirection = 1;
                        player.setPos(17.5, -44, player.getZ() + retryDirection * .08); player.setOnGround(true);
                    }
                    com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                    s.level.tickNonPassenger(retryActor);
                    helper.assertTrue(command.apply("channels_tick") == 1, "Retry captures actual crouch movement");
                    helper.assertTrue(retryActor.blindController().tracks().stream().allMatch(t ->
                            t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION
                            && t.subject() == null && t.travel() == 0
                            && t.uncertainty() == com.frozendawn.entity.blind.BlindVibrationPropagation.RANGE),
                            "Retry structural listening stays anonymous with no pursuit destination");
                }
                var retryDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("blind-lab-results/run-24-" + retryActor.getUUID());
                var retrySummary = com.google.gson.JsonParser.parseString(Files.readString(retryDirectory.resolve("summary.json"))).getAsJsonObject();
                helper.assertTrue(score.apply("#archived") == 1 && score.apply("#inconclusive") == 0
                        && retrySummary.get("complete").getAsBoolean() && retrySummary.get("frames").getAsInt() == 480
                        && retrySummary.get("playerPose").getAsString().equals("CROUCHING")
                        && retrySummary.get("playerSneaking").getAsBoolean()
                        && Math.abs(retrySummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                        "Retry validates the full crouched route: " + retrySummary);
                helper.assertTrue(retryActor.decisionJournal().entries().stream().filter(e ->
                        e.event().equals("BLIND_VIBRATION") && e.detail().contains("kind=FOOTSTEP")).count() >= 10
                        && retryActor.decisionJournal().entries().stream().anyMatch(e ->
                        e.event().equals("BLIND_AIR") && e.detail().contains("kind=QUIET_STEP")), "Retry uses production quiet footsteps");
                helper.assertTrue(command.apply("steps_crouched_retry") != 1 && command.apply("start") != 1,
                        "Completed retry cannot be overwritten");
                for (var evidence : failedEvidence.entrySet()) helper.assertTrue(
                        Files.readString(failedDirectory.resolve(evidence.getKey())).equals(evidence.getValue()),
                        "Retry retains every failed predecessor byte");
                helper.assertTrue(command.apply("steps_crouched_input") != 1, "Input retry requires an inconclusive predecessor");
                retryActor.discard();
                // Preserve an independent wrong-pose run24 before testing its instrumented successor.
                for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(24);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                player.setPos(17.5,-44,5.5); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                var disputed = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                disputed.setPos(9.5,-44,5.5); disputed.setBlindArchitect(true); disputed.setNoAi(true); disputed.setOnGround(true);
                disputed.addTag("blind_lab_actor"); disputed.getRandom().setSeed(451L); s.level.addFreshEntity(disputed); s.entities.add(disputed);
                helper.assertTrue(command.apply("start") == 1 && command.apply("steps_crouched_input") != 1,
                        "Input retry cannot interrupt the disputed view");
                retryDirection = 1;
                for (int tick = 1; tick <= 480; tick++) {
                    s.clock(s.level.getGameTime() + 1);
                    if (tick > 80 && tick <= 240) {
                        if (player.getZ() >= 7.1) retryDirection = -1;
                        if (player.getZ() <= 3.9) retryDirection = 1;
                        player.setPos(17.5,-44,player.getZ() + retryDirection * .08); player.setOnGround(true);
                        if ((tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                    }
                    s.level.tickNonPassenger(disputed);
                    helper.assertTrue(command.apply("channels_tick") == 1, "Disputed view24 retains every frame");
                }
                helper.assertTrue(score.apply("#archived") == 0 && score.apply("#inconclusive") == 400,
                        "Wrong-pose run24 stays inconclusive");
                var disputedDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("blind-lab-results/run-24-" + disputed.getUUID());
                var disputedEvidence = new java.util.HashMap<String, String>();
                for (String evidence : java.util.List.of("start.json", "trace.jsonl", "decisions.json", "summary.json"))
                    disputedEvidence.put(evidence, Files.readString(disputedDirectory.resolve(evidence)));
                disputed.setNoAi(false);
                helper.assertTrue(command.apply("steps_crouched_input") != 1, "Input retry rejects an unheld predecessor");
                disputed.setNoAi(true);
                for (String evidence : java.util.List.of("trace.jsonl", "decisions.json")) {
                    Files.writeString(disputedDirectory.resolve(evidence), disputedEvidence.get(evidence) + " ");
                    helper.assertTrue(command.apply("steps_crouched_input") != 1 && score.apply("#run") == 24,
                            "Input retry verifies predecessor hashes: " + evidence);
                    Files.writeString(disputedDirectory.resolve(evidence), disputedEvidence.get(evidence));
                }
                helper.assertTrue(command.apply("steps_crouched_input") == 1 && score.apply("#run") == 25
                        && score.apply("#stage") == 1 && disputed.isRemoved(), "Input retry stages a fresh25 once");
                var inputTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                        .forEach(pos -> inputTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                helper.assertTrue(inputTerrain.equals(walkingTerrain), "Instrumented retry uses the same sealed air and connected stone");
                var inputActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                        new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                inputActor.getRandom().setSeed(451L); player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                player.setOnGround(true); player.setSprinting(false);
                helper.assertTrue(command.apply("start") != 1 && score.apply("#stage") == 1 && inputActor.isNoAi(),
                        "Run25 cannot start without its input recorder");
                var inputPolicy = Files.createTempFile("blind-input-view-", ".json");
                MinecraftLabRuntime inputRuntime = null;
                try {
                    var policyRoot = new JsonObject(); policyRoot.addProperty("schema", 1);
                    var policyFunctions = new JsonObject(); var policyRule = new JsonObject();
                    policyRule.addProperty("world", s.server.getWorldData().getLevelName());
                    policyRule.add("scores", com.google.gson.JsonParser.parseString("[{\"objective\":\"blind_vib\",\"holder\":\"#stage\",\"equals\":1},{\"objective\":\"blind_vib\",\"holder\":\"#run\",\"equals\":25}]").getAsJsonArray());
                    policyFunctions.add("blind_vibration_lab:start", policyRule); policyRoot.add("functions", policyFunctions);
                    Files.writeString(inputPolicy, policyRoot.toString());
                    inputRuntime = new MinecraftLabRuntime(s.server, inputPolicy, () -> java.util.List.of(player));
                    var inputArgs = new JsonObject(); inputArgs.addProperty("name", "blind_vibration_lab:start");
                    var inputStart = run(inputRuntime, "function", inputArgs);
                    helper.assertTrue(inputStart.get("commandOutcome").getAsString().equals("SUCCESS")
                            && inputStart.getAsJsonObject("inputCapture").get("serverFrames").getAsInt() == 0
                            && inputStart.getAsJsonObject("inputCapture").get("serverTickLimit").getAsInt() == 480
                            && score.apply("#stage") == 2 && !inputActor.isNoAi(), "Start atomically attaches480-tick input recording");
                    try { run(inputRuntime, "function", inputArgs); throw new AssertionError("Input view started twice"); }
                    catch (java.util.concurrent.CompletionException expected) { }
                    retryDirection = 1;
                    for (int tick = 1; tick <= 480; tick++) {
                        s.clock(s.level.getGameTime() + 1);
                        if (tick > 80 && tick <= 240) {
                            if (player.getZ() >= 7.1) retryDirection = -1;
                            if (player.getZ() <= 3.9) retryDirection = 1;
                            player.setPos(17.5,-44,player.getZ() + retryDirection * .08); player.setOnGround(true);
                        }
                        com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                        s.level.tickNonPassenger(inputActor);
                        // Headless transport fixture; these client samples are synthetic, not human input evidence.
                        var clientRow = new JsonObject(); clientRow.addProperty("session", inputRuntime.session());
                        clientRow.addProperty("player", player.getUUID().toString()); clientRow.addProperty("gameTime", s.level.getGameTime());
                        clientRow.addProperty("syntheticNativeFixture", true); clientRow.addProperty("pose", player.getPose().name());
                        clientRow.addProperty("sneaking", player.isShiftKeyDown()); clientRow.addProperty("x",player.getX());
                        clientRow.addProperty("y",player.getY()); clientRow.addProperty("z",player.getZ());
                        inputRuntime.inputClient(clientRow, false);
                        helper.assertTrue(command.apply("channels_tick") == 1, "Instrumented retry captures crouched footsteps");
                        inputRuntime.inputTick();
                        if (tick == 240) helper.assertTrue(run(inputRuntime, "input_dump", new JsonObject()).get("active").getAsBoolean(),
                                "Input recorder continues after STOP through the second half of the view");
                    }
                    var inputResult = run(inputRuntime, "input_dump", new JsonObject());
                    helper.assertTrue(inputResult.get("captureComplete").getAsBoolean()
                            && inputResult.get("serverFrames").getAsInt() == 480 && inputResult.get("clientFrames").getAsInt() == 480
                            && score.apply("#archived") == 1 && score.apply("#inconclusive") == 0,
                            "Both complete recordings preserve the full valid view: " + inputResult);
                    var inputDirectory = java.nio.file.Path.of(inputResult.get("directory").getAsString());
                    var serverRows = Files.readAllLines(inputDirectory.resolve("server.jsonl"));
                    helper.assertTrue(serverRows.size() == 480
                            && com.google.gson.JsonParser.parseString(serverRows.getLast()).getAsJsonObject().get("viewTimer").getAsInt() == 480,
                            "Input timeline is bound to every replay timer through completion");
                    for (var hash : inputResult.getAsJsonObject("hashes").entrySet()) helper.assertTrue(hash.getValue().getAsString().equals(
                            java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(Files.readAllBytes(inputDirectory.resolve(hash.getKey()))))), "Input stream hash verifies");
                    var instrumentedDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("blind-lab-results/run-25-" + inputActor.getUUID());
                    var instrumentedSummary = com.google.gson.JsonParser.parseString(Files.readString(instrumentedDirectory.resolve("summary.json"))).getAsJsonObject();
                    helper.assertTrue(instrumentedSummary.get("inputProbe").equals(inputResult.get("probe"))
                            && Math.abs(instrumentedSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                            "Gameplay and input records share the same probe and matched movement route");
                    helper.assertTrue(inputActor.blindController().tracks().stream().allMatch(t ->
                            t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION && t.subject() == null && t.travel() == 0),
                            "Instrumented run preserves anonymous structural listening");
                } finally { if (inputRuntime != null) inputRuntime.close(); Files.delete(inputPolicy); }
                helper.assertTrue(command.apply("steps_crouched_input") != 1 && command.apply("start") != 1,
                        "Completed instrumented view cannot be overwritten");
                for (var evidence : disputedEvidence.entrySet()) helper.assertTrue(
                        Files.readString(disputedDirectory.resolve(evidence.getKey())).equals(evidence.getValue()),
                        "Instrumented retry retains every disputed predecessor byte");
                helper.assertTrue(command.apply("steps_crouched_fixed") != 1, "Corrected retry refuses a successful predecessor");
                inputActor.discard();
                java.nio.file.Path failedInputDirectory = null, failedReplayDirectory = null;
                var failedInputBytes = new java.util.HashMap<String,String>();
                var failedReplayBytes = new java.util.HashMap<String,String>();
                for (int recordedRun = 25; recordedRun <= 26; recordedRun++) {
                    boolean corrected = recordedRun == 26;
                    com.frozendawn.entity.ArchitectEntity recordedActor;
                    if (!corrected) {
                        // Independent wrong-pose, edge-overflow control; earlier successful25 remains intact.
                        for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(25);
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                        for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                        recordedActor = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                        recordedActor.setPos(9.5,-44,5.5); recordedActor.setBlindArchitect(true); recordedActor.setNoAi(true);
                        recordedActor.setOnGround(true); recordedActor.addTag("blind_lab_actor");
                        s.level.addFreshEntity(recordedActor); s.entities.add(recordedActor);
                    } else {
                        recordedActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    }
                    recordedActor.getRandom().setSeed(451L);
                    player.setPos(17.5,-44,5.5); player.setShiftKeyDown(corrected);
                    player.setPose(corrected ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
                    player.setOnGround(true); player.setSprinting(false);
                    helper.assertTrue(command.apply("start") != 1 && score.apply("#stage") == 1 && recordedActor.isNoAi(),
                            "Each recorded view requires a freshly bound passive recorder");
                    var recordedPolicy = Files.createTempFile("blind-fixed-view-", ".json");
                    MinecraftLabRuntime recordedRuntime = null;
                    try {
                        var policyRoot = new JsonObject(); policyRoot.addProperty("schema", 1);
                        var functions = new JsonObject(); var rule = new JsonObject();
                        rule.addProperty("world", s.server.getWorldData().getLevelName());
                        var guards = new com.google.gson.JsonArray();
                        for (String holder : java.util.List.of("#stage", "#run")) {
                            var guard = new JsonObject(); guard.addProperty("objective", "blind_vib"); guard.addProperty("holder", holder);
                            guard.addProperty("equals", holder.equals("#stage") ? 1 : recordedRun); guards.add(guard);
                        }
                        rule.add("scores", guards); functions.add("blind_vibration_lab:start", rule); policyRoot.add("functions", functions);
                        Files.writeString(recordedPolicy, policyRoot.toString());
                        recordedRuntime = new MinecraftLabRuntime(s.server, recordedPolicy, () -> java.util.List.of(player));
                        var args = new JsonObject(); args.addProperty("name", "blind_vibration_lab:start");
                        helper.assertTrue(run(recordedRuntime, "function", args).get("commandOutcome").getAsString().equals("SUCCESS"),
                                "Recorded successor starts once with passive input capture");
                        int direction = 1;
                        for (int tick = 1; tick <= 480; tick++) {
                            s.clock(s.level.getGameTime() + 1);
                            if (tick > 80 && tick <= 240 && (!corrected || tick % 2 == 0)) {
                                if (player.getZ() >= 7.1) direction = -1;
                                if (player.getZ() <= 3.9) direction = 1;
                                player.setPos(17.5,-44,player.getZ() + direction * (corrected ? .16 : .08));
                                player.setOnGround(true);
                                if (!corrected && (tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                        player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                            }
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                    new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                            s.level.tickNonPassenger(recordedActor);
                            var row = new JsonObject(); row.addProperty("session", recordedRuntime.session());
                            row.addProperty("player", player.getUUID().toString()); row.addProperty("gameTime", s.level.getGameTime());
                            row.addProperty("syntheticNativeFixture", true); row.addProperty("pose", player.getPose().name());
                            row.addProperty("sneaking", player.isShiftKeyDown()); row.addProperty("x",player.getX());
                            row.addProperty("y",player.getY()); row.addProperty("z",player.getZ()); recordedRuntime.inputClient(row, false);
                            if (!corrected && tick <= 294) {
                                var edge = row.deepCopy(); edge.addProperty("key", 65); edge.addProperty("action", tick % 2);
                                recordedRuntime.inputClient(edge, true);
                            }
                            if (corrected) {
                                if (tick == 1) { var press = row.deepCopy(); press.addProperty("key", 340); press.addProperty("action", 1); recordedRuntime.inputClient(press, true); }
                                for (int repeat = 0; repeat < 4; repeat++) {
                                    var held = row.deepCopy(); held.addProperty("key", 65); held.addProperty("action", 2); recordedRuntime.inputClient(held, true);
                                }
                            }
                            helper.assertTrue(command.apply("channels_tick") == 1, "Recorded view retains every actual server pose and displacement");
                            recordedRuntime.inputTick();
                            if (tick == 240) helper.assertTrue(command.apply("steps_crouched_fixed") != 1,
                                    "Corrected retry cannot interrupt an active recording");
                        }
                        var result = run(recordedRuntime, "input_dump", new JsonObject());
                        var inputDirectory = java.nio.file.Path.of(result.get("directory").getAsString());
                        var replayDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .resolve("blind-lab-results/run-" + recordedRun + "-" + recordedActor.getUUID());
                        var recordedSummary = com.google.gson.JsonParser.parseString(Files.readString(replayDirectory.resolve("summary.json"))).getAsJsonObject();
                        helper.assertTrue(result.get("serverFrames").getAsInt() == 480 && result.get("clientFrames").getAsInt() == 480
                                && recordedSummary.get("inputProbe").equals(result.get("probe"))
                                && Math.abs(recordedSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                                "Both timelines retain480 frames, the bound probe and12.8 metres");
                        if (!corrected) {
                            helper.assertTrue(!result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 38
                                    && score.apply("#archived") == 0 && score.apply("#inconclusive") == 400,
                                    "Real edge overflow and wrong posture remain explicitly incomplete");
                            failedInputDirectory = inputDirectory; failedReplayDirectory = replayDirectory;
                            for (String name : java.util.List.of("start.json", "summary.json", "server.jsonl", "client.jsonl", "keys.jsonl"))
                                failedInputBytes.put(name, Files.readString(inputDirectory.resolve(name)));
                            for (String name : java.util.List.of("start.json", "summary.json", "trace.jsonl", "decisions.json"))
                                failedReplayBytes.put(name, Files.readString(replayDirectory.resolve(name)));
                            recordedActor.setNoAi(false);
                            helper.assertTrue(command.apply("steps_crouched_fixed") != 1, "Corrected retry refuses an unheld predecessor");
                            recordedActor.setNoAi(true);
                            for (String name : java.util.List.of("server.jsonl", "client.jsonl", "keys.jsonl")) {
                                Files.writeString(inputDirectory.resolve(name), failedInputBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_fixed") != 1 && score.apply("#run") == 25,
                                        "Corrected retry rejects a corrupt input stream: " + name);
                                Files.writeString(inputDirectory.resolve(name), failedInputBytes.get(name));
                            }
                            for (String name : java.util.List.of("trace.jsonl", "decisions.json")) {
                                Files.writeString(replayDirectory.resolve(name), failedReplayBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_fixed") != 1, "Corrected retry rejects corrupt gameplay: " + name);
                                Files.writeString(replayDirectory.resolve(name), failedReplayBytes.get(name));
                            }
                            var wrongStart = com.google.gson.JsonParser.parseString(failedInputBytes.get("start.json")).getAsJsonObject();
                            wrongStart.addProperty("actor", java.util.UUID.randomUUID().toString());
                            Files.writeString(inputDirectory.resolve("start.json"), wrongStart.toString());
                            helper.assertTrue(command.apply("steps_crouched_fixed") != 1, "Corrected retry checks the captured input actor identity");
                            Files.writeString(inputDirectory.resolve("start.json"), failedInputBytes.get("start.json"));
                            Files.delete(inputDirectory.resolve("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_fixed") != 1, "Missing input summary cannot authorize a retry");
                            Files.writeString(inputDirectory.resolve("summary.json"), failedInputBytes.get("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_fixed") == 1 && score.apply("#run") == 26
                                    && score.apply("#stage") == 1 && recordedActor.isRemoved(),
                                    "Fully retained inconclusive25 stages a fresh corrected26 once");
                            var fixedTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                            net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                                    .forEach(pos -> fixedTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                            helper.assertTrue(fixedTerrain.equals(walkingTerrain), "Corrected retry retains the sealed glass and connected stone fixture");
                        } else {
                            helper.assertTrue(result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 0
                                    && result.get("keyEvents").getAsInt() == 1 && result.get("keyRepeatsIgnored").getAsInt() == 1920
                                    && score.apply("#archived") == 1 && score.apply("#inconclusive") == 0,
                                    "Corrected replay retains all press edges and every valid crouched frame");
                            var rows = Files.readAllLines(inputDirectory.resolve("server.jsonl"));
                            for (int i = 0; i < rows.size(); i++) {
                                var row = com.google.gson.JsonParser.parseString(rows.get(i)).getAsJsonObject();
                                helper.assertTrue(row.get("viewRun").getAsInt() == 26 && row.get("viewTimer").getAsInt() == i + 1,
                                        "Corrected input recorder follows the exact successor timer");
                            }
                            var decisions = recordedActor.decisionJournal().entries();
                            helper.assertTrue(decisions.stream().filter(e -> e.event().equals("BLIND_VIBRATION")
                                    && e.detail().contains("kind=FOOTSTEP") && e.detail().contains("outcome=DETECTED")).count() >= 10,
                                    "Packet-spaced native movement emits real detected quiet footsteps");
                            helper.assertTrue(recordedActor.blindController().tracks().stream().allMatch(t ->
                                    t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION && t.subject() == null && t.travel() == 0),
                                    "Corrected native footsteps retain anonymous structural listening");
                            helper.assertTrue(command.apply("steps_crouched_fixed") != 1 && command.apply("start") != 1,
                                    "Corrected recording cannot be overwritten");
                            recordedActor.discard();
                        }
                    } finally { if (recordedRuntime != null) recordedRuntime.close(); Files.delete(recordedPolicy); }
                }
                for (var bytes : failedInputBytes.entrySet()) helper.assertTrue(Files.readString(failedInputDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every incomplete input predecessor byte");
                for (var bytes : failedReplayBytes.entrySet()) helper.assertTrue(Files.readString(failedReplayDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every wrong-pose predecessor byte");
                java.nio.file.Path readyFailedInputDirectory = null, readyFailedReplayDirectory = null;
                var readyFailedInputBytes = new java.util.HashMap<String,String>();
                var readyFailedReplayBytes = new java.util.HashMap<String,String>();
                for (int recordedRun = 26; recordedRun <= 27; recordedRun++) {
                    boolean corrected = recordedRun == 27;
                    com.frozendawn.entity.ArchitectEntity recordedActor;
                    if (!corrected) {
                        // Independent wrong-pose control with complete input; earlier successful26 remains intact.
                        for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(26);
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                        for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                        recordedActor = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                        recordedActor.setPos(9.5,-44,5.5); recordedActor.setBlindArchitect(true); recordedActor.setNoAi(true);
                        recordedActor.setOnGround(true); recordedActor.addTag("blind_lab_actor");
                        s.level.addFreshEntity(recordedActor); s.entities.add(recordedActor);
                    } else {
                        recordedActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    }
                    recordedActor.getRandom().setSeed(451L);
                    player.setPos(17.5,-44,5.5); player.setShiftKeyDown(corrected);
                    player.setPose(corrected ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
                    player.setOnGround(true); player.setSprinting(false);
                    helper.assertTrue(command.apply("start") != 1 && score.apply("#stage") == 1 && recordedActor.isNoAi(),
                            "Each recorded view requires a freshly bound passive recorder");
                    var recordedPolicy = Files.createTempFile("blind-fixed-view-", ".json");
                    MinecraftLabRuntime recordedRuntime = null;
                    try {
                        var policyRoot = new JsonObject(); policyRoot.addProperty("schema", 1);
                        var functions = new JsonObject(); var rule = new JsonObject();
                        rule.addProperty("world", s.server.getWorldData().getLevelName());
                        var guards = new com.google.gson.JsonArray();
                        for (String holder : java.util.List.of("#stage", "#run")) {
                            var guard = new JsonObject(); guard.addProperty("objective", "blind_vib"); guard.addProperty("holder", holder);
                            guard.addProperty("equals", holder.equals("#stage") ? 1 : recordedRun); guards.add(guard);
                        }
                        rule.add("scores", guards); functions.add("blind_vibration_lab:start", rule); policyRoot.add("functions", functions);
                        Files.writeString(recordedPolicy, policyRoot.toString());
                        recordedRuntime = new MinecraftLabRuntime(s.server, recordedPolicy, () -> java.util.List.of(player));
                        var args = new JsonObject(); args.addProperty("name", "blind_vibration_lab:start");
                        var readyAccepted = run(recordedRuntime, "function", args);
                        if (!corrected) {
                            helper.assertTrue(readyAccepted.get("commandOutcome").getAsString().equals("SUCCESS"),
                                    "Wrong-pose26 starts with its passive input recorder");
                        } else {
                            helper.assertTrue(readyAccepted.get("accepted").getAsString().equals("WAITING_FOR_CROUCH")
                                    && score.apply("#stage") == 1 && score.apply("#timer") == 0 && recordedActor.isNoAi(),
                                    "View27 arms its crouch check without starting or releasing the actor");
                            try { run(recordedRuntime, "function", args); throw new AssertionError("Crouch check armed twice"); }
                            catch (java.util.concurrent.CompletionException expected) { }
                            recordedRuntime.serverTick(null);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("sampledTicks").getAsInt() == 0,
                                    "A different server cannot sample or activate this readiness check");
                            player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                            for (int wait = 0; wait < 100; wait++) {
                                s.clock(s.level.getGameTime() + 1); s.level.tickNonPassenger(recordedActor);
                                recordedRuntime.serverTick(s.server); recordedRuntime.serverTick(s.server);
                                helper.assertTrue(score.apply("#stage") == 1 && score.apply("#timer") == 0 && recordedActor.isNoAi()
                                        && !recordedRuntime.inputActive(s.server), "Delayed Shift consumes no encounter or input-capture frames");
                            }
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("sampledTicks").getAsInt() == 100,
                                    "Integrated-server dispatch samples readiness before recording; duplicate callbacks cannot accelerate it");
                            player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                            for (int held = 1; held <= 20; held++) { s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server); }
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 20,
                                    "A partial crouch hold keeps the actor held");
                            player.setShiftKeyDown(false); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Releasing Shift resets the confirmation interval");
                            player.setShiftKeyDown(true);
                            player.setPos(17.5,-44,5.55); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Movement cannot count as still crouch confirmation");
                            player.setPos(17.5,-44,5.5); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            player.setOnGround(false); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Airborne pose cannot satisfy grounded confirmation");
                            player.setOnGround(true);
                            for (int held = 1; held <= 40; held++) {
                                s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                                helper.assertTrue(score.apply("#timer") == 0 && score.apply("#stage") == (held == 40 ? 2 : 1)
                                        && recordedActor.isNoAi() == (held != 40),
                                        "Exactly40 continuous actual crouch samples start the recorded encounter once");
                            }
                            var readyState = run(recordedRuntime, "input_dump", new JsonObject());
                            helper.assertTrue(readyState.get("active").getAsBoolean() && readyState.get("serverFrames").getAsInt() == 0
                                    && readyState.getAsJsonObject("crouchCheck").get("reason").getAsString().equals("CROUCH_CONFIRMED"),
                                    "Crouch confirmation attaches a new recorder with zero consumed frames");
                            var readyDirectory = java.nio.file.Path.of(readyState.getAsJsonObject("crouchCheck").get("directory").getAsString());
                            var readyRows = Files.readAllLines(readyDirectory.resolve("server.jsonl"));
                            helper.assertTrue(readyRows.size() == 164, "Readiness retains delay, resets and40 confirmed samples");
                            for (int row = readyRows.size() - 40; row < readyRows.size(); row++) {
                                var frame = com.google.gson.JsonParser.parseString(readyRows.get(row)).getAsJsonObject();
                                helper.assertTrue(frame.get("crouchValid").getAsBoolean() && frame.get("crouchSettled").getAsBoolean(),
                                        "All40 admitted confirmation rows are actual grounded stationary crouch");
                            }
                        }
                        int direction = 1;
                        for (int tick = 1; tick <= 480; tick++) {
                            s.clock(s.level.getGameTime() + 1);
                            if (tick > 80 && tick <= 240 && (!corrected || tick % 2 == 0)) {
                                if (player.getZ() >= 7.1) direction = -1;
                                if (player.getZ() <= 3.9) direction = 1;
                                player.setPos(17.5,-44,player.getZ() + direction * (corrected ? .16 : .08));
                                player.setOnGround(true);
                                if (!corrected && (tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                        player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                            }
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                    new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                            s.level.tickNonPassenger(recordedActor);
                            var row = new JsonObject(); row.addProperty("session", recordedRuntime.session());
                            row.addProperty("player", player.getUUID().toString()); row.addProperty("gameTime", s.level.getGameTime());
                            row.addProperty("syntheticNativeFixture", true); row.addProperty("pose", player.getPose().name());
                            row.addProperty("sneaking", player.isShiftKeyDown()); row.addProperty("x",player.getX());
                            row.addProperty("y",player.getY()); row.addProperty("z",player.getZ()); recordedRuntime.inputClient(row, false);
                            if (!corrected && tick <= 29) {
                                var edge = row.deepCopy(); edge.addProperty("key", 65); edge.addProperty("action", tick % 2);
                                recordedRuntime.inputClient(edge, true);
                            }
                            if (corrected) {
                                if (tick == 1) { var press = row.deepCopy(); press.addProperty("key", 340); press.addProperty("action", 1); recordedRuntime.inputClient(press, true); }
                                for (int repeat = 0; repeat < 4; repeat++) {
                                    var held = row.deepCopy(); held.addProperty("key", 65); held.addProperty("action", 2); recordedRuntime.inputClient(held, true);
                                }
                            }
                            helper.assertTrue(command.apply("channels_tick") == 1, "Recorded view retains every actual server pose and displacement");
                            recordedRuntime.serverTick(s.server);
                            if (tick == 240) helper.assertTrue(command.apply("steps_crouched_ready") != 1,
                                    "Corrected retry cannot interrupt an active recording");
                        }
                        var result = run(recordedRuntime, "input_dump", new JsonObject());
                        var inputDirectory = java.nio.file.Path.of(result.get("directory").getAsString());
                        var replayDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .resolve("blind-lab-results/run-" + recordedRun + "-" + recordedActor.getUUID());
                        var recordedSummary = com.google.gson.JsonParser.parseString(Files.readString(replayDirectory.resolve("summary.json"))).getAsJsonObject();
                        helper.assertTrue(result.get("serverFrames").getAsInt() == 480 && result.get("clientFrames").getAsInt() == 480
                                && recordedSummary.get("inputProbe").equals(result.get("probe"))
                                && Math.abs(recordedSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                                "Both timelines retain480 frames, the bound probe and12.8 metres");
                        if (!corrected) {
                            helper.assertTrue(result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 0
                                    && score.apply("#archived") == 0 && score.apply("#inconclusive") == 400,
                                    "Complete input does not make the wrong-pose gameplay control valid");
                            readyFailedInputDirectory = inputDirectory; readyFailedReplayDirectory = replayDirectory;
                            for (String name : java.util.List.of("start.json", "summary.json", "server.jsonl", "client.jsonl", "keys.jsonl"))
                                readyFailedInputBytes.put(name, Files.readString(inputDirectory.resolve(name)));
                            for (String name : java.util.List.of("start.json", "summary.json", "trace.jsonl", "decisions.json"))
                                readyFailedReplayBytes.put(name, Files.readString(replayDirectory.resolve(name)));
                            recordedActor.setNoAi(false);
                            helper.assertTrue(command.apply("steps_crouched_ready") != 1, "Corrected retry refuses an unheld predecessor");
                            recordedActor.setNoAi(true);
                            for (String name : java.util.List.of("server.jsonl", "client.jsonl", "keys.jsonl")) {
                                Files.writeString(inputDirectory.resolve(name), readyFailedInputBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_ready") != 1 && score.apply("#run") == 26,
                                        "Corrected retry rejects a corrupt input stream: " + name);
                                Files.writeString(inputDirectory.resolve(name), readyFailedInputBytes.get(name));
                            }
                            for (String name : java.util.List.of("trace.jsonl", "decisions.json")) {
                                Files.writeString(replayDirectory.resolve(name), readyFailedReplayBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_ready") != 1, "Corrected retry rejects corrupt gameplay: " + name);
                                Files.writeString(replayDirectory.resolve(name), readyFailedReplayBytes.get(name));
                            }
                            var wrongStart = com.google.gson.JsonParser.parseString(readyFailedInputBytes.get("start.json")).getAsJsonObject();
                            wrongStart.addProperty("actor", java.util.UUID.randomUUID().toString());
                            Files.writeString(inputDirectory.resolve("start.json"), wrongStart.toString());
                            helper.assertTrue(command.apply("steps_crouched_ready") != 1, "Corrected retry checks the captured input actor identity");
                            Files.writeString(inputDirectory.resolve("start.json"), readyFailedInputBytes.get("start.json"));
                            Files.delete(inputDirectory.resolve("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_ready") != 1, "Missing input summary cannot authorize a retry");
                            Files.writeString(inputDirectory.resolve("summary.json"), readyFailedInputBytes.get("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_ready") == 1 && score.apply("#run") == 27
                                    && score.apply("#stage") == 1 && recordedActor.isRemoved(),
                                    "Fully retained inconclusive26 stages a fresh crouch-checked27 once");
                            var fixedTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                            net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                                    .forEach(pos -> fixedTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                            helper.assertTrue(fixedTerrain.equals(walkingTerrain), "Corrected retry retains the sealed glass and connected stone fixture");
                        } else {
                            helper.assertTrue(result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 0
                                    && result.get("keyEvents").getAsInt() == 1 && result.get("keyRepeatsIgnored").getAsInt() == 1920
                                    && score.apply("#archived") == 1 && score.apply("#inconclusive") == 0,
                                    "Corrected replay retains all press edges and every valid crouched frame");
                            var rows = Files.readAllLines(inputDirectory.resolve("server.jsonl"));
                            for (int i = 0; i < rows.size(); i++) {
                                var row = com.google.gson.JsonParser.parseString(rows.get(i)).getAsJsonObject();
                                helper.assertTrue(row.get("viewRun").getAsInt() == 27 && row.get("viewTimer").getAsInt() == i + 1,
                                        "Corrected input recorder follows the exact successor timer");
                            }
                            var decisions = recordedActor.decisionJournal().entries();
                            helper.assertTrue(decisions.stream().filter(e -> e.event().equals("BLIND_VIBRATION")
                                    && e.detail().contains("kind=FOOTSTEP") && e.detail().contains("outcome=DETECTED")).count() >= 10,
                                    "Packet-spaced native movement emits real detected quiet footsteps");
                            helper.assertTrue(recordedActor.blindController().tracks().stream().allMatch(t ->
                                    t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION && t.subject() == null && t.travel() == 0),
                                    "Corrected native footsteps retain anonymous structural listening");
                            helper.assertTrue(command.apply("steps_crouched_ready") != 1 && command.apply("steps_crouched_repeat") != 1 && command.apply("start") != 1,
                                    "Corrected recording cannot be overwritten");
                            recordedActor.discard();
                        }
                    } finally { if (recordedRuntime != null) recordedRuntime.close(); Files.delete(recordedPolicy); }
                }
                for (var bytes : readyFailedInputBytes.entrySet()) helper.assertTrue(Files.readString(readyFailedInputDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every incomplete input predecessor byte");
                for (var bytes : readyFailedReplayBytes.entrySet()) helper.assertTrue(Files.readString(readyFailedReplayDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every wrong-pose predecessor byte");
                // Independent failed27 with successful readiness, followed by a fully recorded28.
                java.nio.file.Path repeatFailedInputDirectory = null, repeatFailedReplayDirectory = null;
                var repeatFailedInputBytes = new java.util.HashMap<String,String>();
                var repeatFailedReplayBytes = new java.util.HashMap<String,String>();
                java.nio.file.Path repeatFailedReadinessDirectory = null;
                var repeatFailedReadinessBytes = new java.util.HashMap<String,String>();
                for (int recordedRun = 27; recordedRun <= 28; recordedRun++) {
                    boolean corrected = recordedRun == 28;
                    com.frozendawn.entity.ArchitectEntity recordedActor;
                    if (!corrected) {
                        // Independent wrong-pose control with complete input; earlier successful27 remains intact.
                        for (String holder : java.util.List.of("#run", "#case", "#isolated"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(27);
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective).set(1);
                        for (String holder : java.util.List.of("#timer", "#frames", "#last_frame", "#archived", "#inconclusive"))
                            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(0);
                        recordedActor = com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                        recordedActor.setPos(9.5,-44,5.5); recordedActor.setBlindArchitect(true); recordedActor.setNoAi(true);
                        recordedActor.setOnGround(true); recordedActor.addTag("blind_lab_actor");
                        s.level.addFreshEntity(recordedActor); s.entities.add(recordedActor);
                    } else {
                        recordedActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                    }
                    recordedActor.getRandom().setSeed(451L);
                    player.setPos(17.5,-44,5.5); player.setShiftKeyDown(corrected);
                    player.setPose(corrected ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
                    player.setOnGround(true); player.setSprinting(false);
                    helper.assertTrue(command.apply("start") != 1 && score.apply("#stage") == 1 && recordedActor.isNoAi(),
                            "Each recorded view requires a freshly bound passive recorder");
                    var recordedPolicy = Files.createTempFile("blind-fixed-view-", ".json");
                    MinecraftLabRuntime recordedRuntime = null;
                    try {
                        var policyRoot = new JsonObject(); policyRoot.addProperty("schema", 1);
                        var functions = new JsonObject(); var rule = new JsonObject();
                        rule.addProperty("world", s.server.getWorldData().getLevelName());
                        var guards = new com.google.gson.JsonArray();
                        for (String holder : java.util.List.of("#stage", "#run")) {
                            var guard = new JsonObject(); guard.addProperty("objective", "blind_vib"); guard.addProperty("holder", holder);
                            guard.addProperty("equals", holder.equals("#stage") ? 1 : recordedRun); guards.add(guard);
                        }
                        rule.add("scores", guards); functions.add("blind_vibration_lab:start", rule); policyRoot.add("functions", functions);
                        Files.writeString(recordedPolicy, policyRoot.toString());
                        recordedRuntime = new MinecraftLabRuntime(s.server, recordedPolicy, () -> java.util.List.of(player));
                        var args = new JsonObject(); args.addProperty("name", "blind_vibration_lab:start");
                        var readyAccepted = run(recordedRuntime, "function", args);
                        {
                            helper.assertTrue(readyAccepted.get("accepted").getAsString().equals("WAITING_FOR_CROUCH")
                                    && score.apply("#stage") == 1 && score.apply("#timer") == 0 && recordedActor.isNoAi(),
                                    "The crouch view arms its crouch check without starting or releasing the actor");
                            try { run(recordedRuntime, "function", args); throw new AssertionError("Crouch check armed twice"); }
                            catch (java.util.concurrent.CompletionException expected) { }
                            recordedRuntime.serverTick(null);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("sampledTicks").getAsInt() == 0,
                                    "A different server cannot sample or activate this readiness check");
                            player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                            for (int wait = 0; wait < 100; wait++) {
                                s.clock(s.level.getGameTime() + 1); s.level.tickNonPassenger(recordedActor);
                                recordedRuntime.serverTick(s.server); recordedRuntime.serverTick(s.server);
                                helper.assertTrue(score.apply("#stage") == 1 && score.apply("#timer") == 0 && recordedActor.isNoAi()
                                        && !recordedRuntime.inputActive(s.server), "Delayed Shift consumes no encounter or input-capture frames");
                            }
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("sampledTicks").getAsInt() == 100,
                                    "Integrated-server dispatch samples readiness before recording; duplicate callbacks cannot accelerate it");
                            player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                            for (int held = 1; held <= 20; held++) { s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server); }
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 20,
                                    "A partial crouch hold keeps the actor held");
                            player.setShiftKeyDown(false); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Releasing Shift resets the confirmation interval");
                            player.setShiftKeyDown(true);
                            player.setPos(17.5,-44,5.55); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Movement cannot count as still crouch confirmation");
                            player.setPos(17.5,-44,5.5); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            player.setOnGround(false); s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                            helper.assertTrue(run(recordedRuntime, "input_dump", new JsonObject()).get("stableTicks").getAsInt() == 0,
                                    "Airborne pose cannot satisfy grounded confirmation");
                            player.setOnGround(true);
                            for (int held = 1; held <= 40; held++) {
                                s.clock(s.level.getGameTime() + 1); recordedRuntime.serverTick(s.server);
                                helper.assertTrue(score.apply("#timer") == 0 && score.apply("#stage") == (held == 40 ? 2 : 1)
                                        && recordedActor.isNoAi() == (held != 40),
                                        "Exactly40 continuous actual crouch samples start the recorded encounter once");
                            }
                            var readyState = run(recordedRuntime, "input_dump", new JsonObject());
                            helper.assertTrue(readyState.get("active").getAsBoolean() && readyState.get("serverFrames").getAsInt() == 0
                                    && readyState.getAsJsonObject("crouchCheck").get("reason").getAsString().equals("CROUCH_CONFIRMED"),
                                    "Crouch confirmation attaches a new recorder with zero consumed frames");
                            var readyDirectory = java.nio.file.Path.of(readyState.getAsJsonObject("crouchCheck").get("directory").getAsString());
                            var readyRows = Files.readAllLines(readyDirectory.resolve("server.jsonl"));
                            helper.assertTrue(readyRows.size() == 164, "Readiness retains delay, resets and40 confirmed samples");
                            for (int row = readyRows.size() - 40; row < readyRows.size(); row++) {
                                var frame = com.google.gson.JsonParser.parseString(readyRows.get(row)).getAsJsonObject();
                                helper.assertTrue(frame.get("crouchValid").getAsBoolean() && frame.get("crouchSettled").getAsBoolean(),
                                        "All40 admitted confirmation rows are actual grounded stationary crouch");
                            }
                        }
                        if (!corrected) {
                            player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                        }
                        int direction = 1;
                        for (int tick = 1; tick <= 480; tick++) {
                            s.clock(s.level.getGameTime() + 1);
                            if (tick > 80 && tick <= 240 && (!corrected || tick % 2 == 0)) {
                                if (player.getZ() >= 7.1) direction = -1;
                                if (player.getZ() <= 3.9) direction = 1;
                                player.setPos(17.5,-44,player.getZ() + direction * (corrected ? .16 : .08));
                                player.setOnGround(true);
                                if (!corrected && (tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                        player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                            }
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                    new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
                            s.level.tickNonPassenger(recordedActor);
                            var row = new JsonObject(); row.addProperty("session", recordedRuntime.session());
                            row.addProperty("player", player.getUUID().toString()); row.addProperty("gameTime", s.level.getGameTime());
                            row.addProperty("syntheticNativeFixture", true); row.addProperty("pose", player.getPose().name());
                            row.addProperty("sneaking", player.isShiftKeyDown()); row.addProperty("x",player.getX());
                            row.addProperty("y",player.getY()); row.addProperty("z",player.getZ()); recordedRuntime.inputClient(row, false);
                            if (!corrected && tick <= 29) {
                                var edge = row.deepCopy(); edge.addProperty("key", 65); edge.addProperty("action", tick % 2);
                                recordedRuntime.inputClient(edge, true);
                            }
                            if (corrected) {
                                if (tick == 1) { var press = row.deepCopy(); press.addProperty("key", 340); press.addProperty("action", 1); recordedRuntime.inputClient(press, true); }
                                for (int repeat = 0; repeat < 4; repeat++) {
                                    var held = row.deepCopy(); held.addProperty("key", 65); held.addProperty("action", 2); recordedRuntime.inputClient(held, true);
                                }
                            }
                            helper.assertTrue(command.apply("channels_tick") == 1, "Recorded view retains every actual server pose and displacement");
                            recordedRuntime.serverTick(s.server);
                            if (tick == 240) helper.assertTrue(command.apply("steps_crouched_repeat") != 1,
                                    "Corrected retry cannot interrupt an active recording");
                        }
                        var result = run(recordedRuntime, "input_dump", new JsonObject());
                        var inputDirectory = java.nio.file.Path.of(result.get("directory").getAsString());
                        var replayDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .resolve("blind-lab-results/run-" + recordedRun + "-" + recordedActor.getUUID());
                        var recordedSummary = com.google.gson.JsonParser.parseString(Files.readString(replayDirectory.resolve("summary.json"))).getAsJsonObject();
                        helper.assertTrue(result.get("serverFrames").getAsInt() == 480 && result.get("clientFrames").getAsInt() == 480
                                && recordedSummary.get("inputProbe").equals(result.get("probe"))
                                && Math.abs(recordedSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001,
                                "Both timelines retain480 frames, the bound probe and12.8 metres");
                        if (!corrected) {
                            helper.assertTrue(result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 0
                                    && score.apply("#archived") == 0 && score.apply("#inconclusive") == 400,
                                    "Complete input does not make the wrong-pose gameplay control valid");
                            repeatFailedInputDirectory = inputDirectory; repeatFailedReplayDirectory = replayDirectory;
                            for (String name : java.util.List.of("start.json", "summary.json", "server.jsonl", "client.jsonl", "keys.jsonl"))
                                repeatFailedInputBytes.put(name, Files.readString(inputDirectory.resolve(name)));
                            for (String name : java.util.List.of("start.json", "summary.json", "trace.jsonl", "decisions.json"))
                                repeatFailedReplayBytes.put(name, Files.readString(replayDirectory.resolve(name)));
                            var capturedStart = com.google.gson.JsonParser.parseString(Files.readString(inputDirectory.resolve("start.json"))).getAsJsonObject();
                            var capturedCheck = capturedStart.getAsJsonObject("crouchCheck").get("check").getAsString();
                            repeatFailedReadinessDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                    .resolve("blind-lab-ready/check-" + capturedCheck);
                            for (String name : java.util.List.of("start.json", "summary.json", "server.jsonl"))
                                repeatFailedReadinessBytes.put(name, Files.readString(repeatFailedReadinessDirectory.resolve(name)));
                            for (String name : java.util.List.of("start.json", "summary.json", "server.jsonl")) {
                                Files.writeString(repeatFailedReadinessDirectory.resolve(name), name.equals("server.jsonl")
                                        ? repeatFailedReadinessBytes.get(name) + " " : "{}");
                                helper.assertTrue(command.apply("steps_crouched_repeat") != 1 && score.apply("#run") == 27,
                                        "Repeat refuses missing identity or corrupted readiness bytes: " + name);
                                Files.writeString(repeatFailedReadinessDirectory.resolve(name), repeatFailedReadinessBytes.get(name));
                            }
                            recordedActor.setNoAi(false);
                            helper.assertTrue(command.apply("steps_crouched_repeat") != 1, "Corrected retry refuses an unheld predecessor");
                            recordedActor.setNoAi(true);
                            for (String name : java.util.List.of("server.jsonl", "client.jsonl", "keys.jsonl")) {
                                Files.writeString(inputDirectory.resolve(name), repeatFailedInputBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_repeat") != 1 && score.apply("#run") == 27,
                                        "Corrected retry rejects a corrupt input stream: " + name);
                                Files.writeString(inputDirectory.resolve(name), repeatFailedInputBytes.get(name));
                            }
                            for (String name : java.util.List.of("trace.jsonl", "decisions.json")) {
                                Files.writeString(replayDirectory.resolve(name), repeatFailedReplayBytes.get(name) + " ");
                                helper.assertTrue(command.apply("steps_crouched_repeat") != 1, "Corrected retry rejects corrupt gameplay: " + name);
                                Files.writeString(replayDirectory.resolve(name), repeatFailedReplayBytes.get(name));
                            }
                            var wrongStart = com.google.gson.JsonParser.parseString(repeatFailedInputBytes.get("start.json")).getAsJsonObject();
                            wrongStart.addProperty("actor", java.util.UUID.randomUUID().toString());
                            Files.writeString(inputDirectory.resolve("start.json"), wrongStart.toString());
                            helper.assertTrue(command.apply("steps_crouched_repeat") != 1, "Corrected retry checks the captured input actor identity");
                            Files.writeString(inputDirectory.resolve("start.json"), repeatFailedInputBytes.get("start.json"));
                            Files.delete(inputDirectory.resolve("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_repeat") != 1, "Missing input summary cannot authorize a retry");
                            Files.writeString(inputDirectory.resolve("summary.json"), repeatFailedInputBytes.get("summary.json"));
                            helper.assertTrue(command.apply("steps_crouched_repeat") == 1 && score.apply("#run") == 28
                                    && score.apply("#stage") == 1 && recordedActor.isRemoved(),
                                    "Fully retained inconclusive27 stages a fresh crouch-checked28 once");
                            var fixedTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                            net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                                    .forEach(pos -> fixedTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                            helper.assertTrue(fixedTerrain.equals(walkingTerrain), "Corrected retry retains the sealed glass and connected stone fixture");
                        } else {
                            helper.assertTrue(result.get("captureComplete").getAsBoolean() && result.get("dropped").getAsInt() == 0
                                    && result.get("keyEvents").getAsInt() == 1 && result.get("keyRepeatsIgnored").getAsInt() == 1920
                                    && score.apply("#archived") == 1 && score.apply("#inconclusive") == 0,
                                    "Corrected replay retains all press edges and every valid crouched frame");
                            var rows = Files.readAllLines(inputDirectory.resolve("server.jsonl"));
                            for (int i = 0; i < rows.size(); i++) {
                                var row = com.google.gson.JsonParser.parseString(rows.get(i)).getAsJsonObject();
                                helper.assertTrue(row.get("viewRun").getAsInt() == 28 && row.get("viewTimer").getAsInt() == i + 1,
                                        "Corrected input recorder follows the exact successor timer");
                            }
                            var decisions = recordedActor.decisionJournal().entries();
                            helper.assertTrue(decisions.stream().filter(e -> e.event().equals("BLIND_VIBRATION")
                                    && e.detail().contains("kind=FOOTSTEP") && e.detail().contains("outcome=DETECTED")).count() >= 10,
                                    "Packet-spaced native movement emits real detected quiet footsteps");
                            helper.assertTrue(recordedActor.blindController().tracks().stream().allMatch(t ->
                                    t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION && t.subject() == null && t.travel() == 0),
                                    "Corrected native footsteps retain anonymous structural listening");
                            helper.assertTrue(command.apply("steps_crouched_repeat") != 1 && command.apply("start") != 1,
                                    "Corrected recording cannot be overwritten");
                            // Continue only the fully recorded28 into a same-posture material pair.
                            helper.assertTrue(command.apply("steps_material_wool") != 1,
                                    "Wool cannot skip its fresh stone control");
                            recordedActor.setNoAi(false);
                            helper.assertTrue(command.apply("steps_material_stone") != 1,
                                    "Material transition refuses an unheld completed predecessor");
                            recordedActor.setNoAi(true);
                            for (String name : java.util.List.of("trace.jsonl", "decisions.json")) {
                                String original = Files.readString(replayDirectory.resolve(name));
                                Files.writeString(replayDirectory.resolve(name), original + " ");
                                helper.assertTrue(command.apply("steps_material_stone") != 1 && score.apply("#run") == 28,
                                        "Material preparation refuses corrupted predecessor replay: " + name);
                                Files.writeString(replayDirectory.resolve(name), original);
                            }
                            for (String name : java.util.List.of("server.jsonl", "client.jsonl", "keys.jsonl")) {
                                String original = Files.readString(inputDirectory.resolve(name));
                                Files.writeString(inputDirectory.resolve(name), original + " ");
                                helper.assertTrue(command.apply("steps_material_stone") != 1 && score.apply("#run") == 28,
                                        "Material preparation refuses corrupted predecessor input: " + name);
                                Files.writeString(inputDirectory.resolve(name), original);
                            }
                            String inputSummaryBytes = Files.readString(inputDirectory.resolve("summary.json"));
                            var incomplete = com.google.gson.JsonParser.parseString(inputSummaryBytes).getAsJsonObject();
                            incomplete.addProperty("captureComplete", false);
                            Files.writeString(inputDirectory.resolve("summary.json"), incomplete.toString());
                            helper.assertTrue(command.apply("steps_material_stone") != 1,
                                    "Material pair refuses an incomplete client capture even when gameplay passed");
                            Files.writeString(inputDirectory.resolve("summary.json"), inputSummaryBytes);
                            var materialTerrain = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                            java.nio.file.Path stoneReplay = null;
                            String stoneTrace = null;
                            for (int materialRun = 29; materialRun <= 30; materialRun++) {
                                var materialName = materialRun == 29 ? "steps_material_stone" : "steps_material_wool";
                                helper.assertTrue(command.apply(materialName) == 1 && score.apply("#run") == materialRun
                                        && score.apply("#stage") == 1, "Complete bound archive stages the material successor once");
                                var materialActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                        new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                helper.assertTrue(materialActor.isNoAi() && materialActor.getHealth() == 40,
                                        "Each material view has a fresh held actor");
                                if (materialRun == 29) {
                                    net.minecraft.core.BlockPos.betweenClosedStream(1,-48,-11,29,-39,9)
                                            .forEach(pos -> materialTerrain.put(pos.immutable(), s.level.getBlockState(pos)));
                                    helper.assertTrue(materialTerrain.equals(walkingTerrain), "Stone control retains the connected sealed fixture");
                                    player.setOnGround(true); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                                    var impulse = com.frozendawn.entity.blind.BlindVibrationPropagation.foot(player,
                                            com.frozendawn.entity.blind.BlindVibrationPropagation.Kind.FOOTSTEP, 6, s.level.getGameTime());
                                    var bare = com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level, impulse, materialActor, 384);
                                    net.minecraft.core.BlockPos.betweenClosedStream(16,-45,3,19,-45,7)
                                            .forEach(pos -> s.level.setBlock(pos, net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState(), 3));
                                    helper.assertTrue(com.frozendawn.labbridge.LabBlindChannels.inputViewActor(player.createCommandSourceStack().withPermission(2)) == null,
                                            "Ready stone view refuses a changed walking surface");
                                    var soft = com.frozendawn.entity.blind.BlindVibrationPropagation.receive(s.level, impulse, materialActor, 384);
                                    helper.assertTrue(bare.detected() && !soft.unknown() && soft.received() < bare.received()
                                            && (soft.detected() || soft.outcome() == com.frozendawn.entity.blind.BlindVibrationPropagation.Outcome.BELOW_THRESHOLD),
                                            "Equal impulses and fixed contacts isolate wool damping: " + soft);
                                    var proof = new JsonObject(); proof.add("stone", new com.google.gson.Gson().toJsonTree(bare));
                                    proof.add("wool", new com.google.gson.Gson().toJsonTree(soft));
                                    proof.addProperty("sourceStrength", 6); proof.addProperty("sameImpulseAndPositions", true);
                                    var proofDirectory = java.nio.file.Path.of("build/architect-evidence/blind-channels"); Files.createDirectories(proofDirectory);
                                    Files.writeString(proofDirectory.resolve("material-footsteps.json"), proof.toString() + "\n");
                                    net.minecraft.core.BlockPos.betweenClosedStream(16,-45,3,19,-45,7)
                                            .forEach(pos -> s.level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3));
                                } else {
                                    for (var cell : materialTerrain.entrySet()) {
                                        var pos = cell.getKey(); boolean walkingSurface = pos.getY() == -45 && pos.getX() >= 16
                                                && pos.getX() <= 19 && pos.getZ() >= 3 && pos.getZ() <= 7;
                                        helper.assertTrue(s.level.getBlockState(pos).equals(walkingSurface
                                                ? net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState() : cell.getValue()),
                                                "Wool changes only the20 walking-surface blocks; receiver and bridge remain stone");
                                    }
                                    helper.assertTrue(Files.readString(stoneReplay.resolve("trace.jsonl")).equals(stoneTrace),
                                            "Wool preparation retains every stone-control frame");
                                }
                                player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING); player.setOnGround(true);
                                guards.get(1).getAsJsonObject().addProperty("equals", materialRun);
                                Files.writeString(recordedPolicy, policyRoot.toString());
                                var materialStart = run(recordedRuntime, "function", args);
                                helper.assertTrue(materialStart.get("commandOutcome").getAsString().equals("SUCCESS")
                                        && materialStart.getAsJsonObject("inputCapture").get("serverFrames").getAsInt() == 0,
                                        "Material Start attaches the recorder before releasing the actor");
                                int materialDirection = 1;
                                for (int tick = 1; tick <= 480; tick++) {
                                    s.clock(s.level.getGameTime() + 1);
                                    if (tick > 80 && tick <= 240) {
                                        if (player.getZ() >= 7.1) materialDirection = -1;
                                        if (player.getZ() <= 3.9) materialDirection = 1;
                                        player.setPos(17.5,-44,player.getZ() + materialDirection * .08); player.setOnGround(true);
                                        if ((tick - 80) % 9 == 0) s.level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,
                                                player.position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                                    }
                                    s.level.tickNonPassenger(materialActor);
                                    var clientRow = new JsonObject(); clientRow.addProperty("session", recordedRuntime.session());
                                    clientRow.addProperty("player", player.getUUID().toString()); clientRow.addProperty("gameTime", s.level.getGameTime());
                                    clientRow.addProperty("syntheticNativeFixture", true); clientRow.addProperty("pose", player.getPose().name());
                                    clientRow.addProperty("sneaking", player.isShiftKeyDown()); recordedRuntime.inputClient(clientRow, false);
                                    if (tick == 81 || tick == 241) {
                                        var edge = clientRow.deepCopy(); edge.addProperty("key",65); edge.addProperty("action",tick == 81 ? 1 : 0);
                                        recordedRuntime.inputClient(edge,true);
                                    }
                                    helper.assertTrue(command.apply("channels_tick") == 1, "Standing material view records each ordinary tick");
                                    recordedRuntime.serverTick(s.server);
                                    if (tick == 240) helper.assertTrue(command.apply("steps_material_wool") != 1,
                                            "Material successor cannot interrupt the current recording");
                                }
                                var materialCapture = run(recordedRuntime,"input_dump",new JsonObject());
                                helper.assertTrue(materialCapture.get("captureComplete").getAsBoolean() && materialCapture.get("dropped").getAsInt() == 0
                                        && materialCapture.get("clientFrames").getAsInt() == 480 && materialCapture.get("serverFrames").getAsInt() == 480
                                        && score.apply("#archived") == 1 && score.apply("#inconclusive") == 0,
                                        "Both material views retain complete client/server and valid gameplay captures");
                                var materialDirectory = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                        .resolve("blind-lab-results/run-" + materialRun + "-" + materialActor.getUUID());
                                var materialSummary = com.google.gson.JsonParser.parseString(Files.readString(materialDirectory.resolve("summary.json"))).getAsJsonObject();
                                helper.assertTrue(Math.abs(materialSummary.get("walkingDistance").getAsDouble() - 12.8) < .0001
                                        && materialSummary.get("walkingSurface").getAsString().equals(materialRun == 29 ? "STONE" : "WHITE_WOOL"),
                                        "Material pair retains the same standing route and surface provenance");
                                var materialFrames = Files.readAllLines(materialDirectory.resolve("trace.jsonl")).stream()
                                        .map(line -> com.google.gson.JsonParser.parseString(line).getAsJsonObject()).toList();
                                var materialDecisions = new java.util.ArrayList<JsonObject>();
                                com.google.gson.JsonParser.parseString(Files.readString(materialDirectory.resolve("decisions.json"))).getAsJsonArray()
                                        .forEach(entry -> materialDecisions.add(entry.getAsJsonObject()));
                                helper.assertTrue(com.frozendawn.labbridge.LabFootstepWindow.qualifies(materialSummary, materialFrames, materialDecisions),
                                        "Both real native material views satisfy the standing movement qualification");
                                helper.assertTrue(materialActor.blindController().tracks().stream().allMatch(t ->
                                        t.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION && t.subject() == null && t.travel() == 0),
                                        "Wool footsteps cannot grant hidden player identity or source destination");
                                if (materialRun == 29) { stoneReplay = materialDirectory; stoneTrace = Files.readString(materialDirectory.resolve("trace.jsonl")); }
                                helper.assertTrue(command.apply("start") != 1 && command.apply(materialName) != 1,
                                        "Completed material views cannot overwrite themselves");
                                if (materialRun == 30) {
                                    materialActor.setNoAi(false);
                                    helper.assertTrue(command.apply("echo_motion") != 1, "Moving echo refuses an unheld predecessor");
                                    materialActor.setNoAi(true);
                                    for (var oldStream : java.util.List.of(materialDirectory.resolve("trace.jsonl"),
                                            materialDirectory.resolve("decisions.json"),
                                            s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                                    .resolve("blind-lab-input/probe-" + materialCapture.get("probe").getAsString()).resolve("server.jsonl"))) {
                                        String bytes = Files.readString(oldStream); Files.writeString(oldStream, bytes + " ");
                                        helper.assertTrue(command.apply("echo_motion") != 1 && score.apply("#run") == 30,
                                                "Moving echo refuses corrupt retained streams before changing the scene");
                                        Files.writeString(oldStream, bytes);
                                    }
                                    String retainedWool = Files.readString(materialDirectory.resolve("trace.jsonl"));
                                    helper.assertTrue(command.apply("echo_motion") == 1 && score.apply("#run") == 31,
                                            "Separate moving echo view follows the fully retained material result once");
                                    helper.assertTrue(Files.readString(materialDirectory.resolve("trace.jsonl")).equals(retainedWool),
                                            "Preparing moving echo preserves every prior wool frame");
                                    var echoActor = s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                            new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                    helper.assertTrue(!echoActor.getUUID().equals(materialActor.getUUID()) && echoActor.isNoAi(),
                                            "Moving echo has a fresh held receiver");
                                    echoActor.getRandom().setSeed(437);
                                    guards.get(1).getAsJsonObject().addProperty("equals",31); Files.writeString(recordedPolicy,policyRoot.toString());
                                    var echoStart = run(recordedRuntime,"function",args);
                                    helper.assertTrue(echoStart.get("commandOutcome").getAsString().equals("SUCCESS")
                                            && player.getAbilities().flying && player.isNoGravity() && !player.onGround()
                                            && player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL,
                                            "The recorder attaches before ordinary AI release and supplied Survival hovering");
                                    int hoverDirection = 1;
                                    java.util.Map<Long,com.frozendawn.entity.blind.BlindSignalMemory.Signal> measured = new java.util.HashMap<>();
                                    int rememberedFrames = 0, expiredFrames = 0, reopenedFrames = 0;
                                    long echoStarted = s.level.getGameTime();
                                    for (int tick = 1; tick <= 480; tick++) {
                                        s.clock(echoStarted + tick);
                                        if (tick > 80 && tick <= 300) {
                                            if (player.getZ() >= 7.1) hoverDirection = -1;
                                            if (player.getZ() <= 3.9) hoverDirection = 1;
                                            player.setPos(17.5,-44,player.getZ()+hoverDirection*.08);
                                        }
                                        player.setOnGround(false);
                                        // Exercise the exact timed shutter before the ordinary entity tick.
                                        if (tick == 121 || tick == 361) for (int z = 4; z <= 6; z++) s.level.setBlock(
                                                new net.minecraft.core.BlockPos(11,-43,z), (tick == 121
                                                ? net.minecraft.world.level.block.Blocks.GLASS : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),3);
                                        s.level.tickNonPassenger(echoActor);
                                        var selected = echoActor.blindController().signal();
                                        if (selected != null) {
                                            helper.assertTrue(selected.sense() == com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO
                                                    && echoActor.getTarget() == null, "Moving echo contains only returned echo contact");
                                            var earlier = measured.putIfAbsent(selected.observed(), selected);
                                            if (earlier != null) helper.assertTrue(earlier.region().equals(selected.region()),
                                                    "A stored return cannot follow a moving player's new coordinates");
                                            if (tick >= 125 && tick <= 360) {
                                                helper.assertTrue(selected.observed() < echoStarted + 121,
                                                        "Covered movement never refreshes the old observation time"); rememberedFrames++;
                                            }
                                            if (tick > 360 && selected.observed() > echoStarted+360) reopenedFrames++;
                                        } else if (tick > 340 && tick <= 360) expiredFrames++;
                                        helper.assertTrue(echoActor.blindController().lastVibration() == null,
                                                "Hovering has no structural footstep source");
                                        var hoverClient = new JsonObject(); hoverClient.addProperty("session",recordedRuntime.session());
                                        hoverClient.addProperty("player",player.getUUID().toString()); hoverClient.addProperty("gameTime",s.level.getGameTime());
                                        hoverClient.addProperty("syntheticNativeFixture",true); hoverClient.addProperty("pose",player.getPose().name());
                                        hoverClient.addProperty("sneaking",false); hoverClient.addProperty("flying",true);
                                        recordedRuntime.inputClient(hoverClient,false);
                                        helper.assertTrue(command.apply("channels_tick") == 1, "Every echo phase records one distinct gameplay frame");
                                        recordedRuntime.serverTick(s.server);
                                        if (tick == 240) helper.assertTrue(command.apply("echo_motion") != 1,
                                                "The moving view cannot replace a live encounter");
                                    }
                                    var hoverCapture = run(recordedRuntime,"input_dump",new JsonObject());
                                    helper.assertTrue(hoverCapture.get("captureComplete").getAsBoolean() && hoverCapture.get("serverFrames").getAsInt()==480
                                            && hoverCapture.get("clientFrames").getAsInt()==480 && hoverCapture.get("dropped").getAsInt()==0
                                            && score.apply("#inconclusive")==0 && score.apply("#archived")==1,
                                            "Moving echo retains all gameplay and synthetic client/server frames with no contamination");
                                    helper.assertTrue(rememberedFrames>0 && expiredFrames>0 && reopenedFrames>0,
                                            "The real fixture remembers, expires and reacquires only after reopening: "
                                            +rememberedFrames+" / "+expiredFrames+" / "+reopenedFrames);
                                    helper.assertTrue(echoActor.decisionJournal().entries().stream().noneMatch(e->e.event().equals("BLIND_AIR")
                                            || e.event().equals("BLIND_VIBRATION")), "No passive sound event competes with the moving echo");
                                    var echoProof = new JsonObject(); echoProof.addProperty("actor",echoActor.getUUID().toString());
                                    echoProof.addProperty("rememberedCoveredFrames",rememberedFrames); echoProof.addProperty("expiredCoveredFrames",expiredFrames);
                                    echoProof.addProperty("reopenedNewContactFrames",reopenedFrames); echoProof.addProperty("frozenObservedRegions",measured.size());
                                    echoProof.addProperty("clientRowsSynthetic",true); echoProof.addProperty("productionSettingsUnchanged",true);
                                    var echoProofDirectory=java.nio.file.Path.of("build/architect-evidence/blind-channels");Files.createDirectories(echoProofDirectory);
                                    Files.writeString(echoProofDirectory.resolve("moving-echo.json"),echoProof+"\n");
                                    helper.assertTrue(command.apply("echo_motion") != 1 && command.apply("start") != 1,
                                            "Completed moving view cannot be overwritten");
                                    var echoReplay = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                            .resolve("blind-lab-results/run-31-" + echoActor.getUUID());
                                    var echoInput = s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                            .resolve("blind-lab-input/probe-" + hoverCapture.get("probe").getAsString());
                                    var echoBytes = new java.util.HashMap<java.nio.file.Path,String>();
                                    for (var oldStream : java.util.List.of(echoReplay.resolve("trace.jsonl"),echoReplay.resolve("decisions.json"),
                                            echoInput.resolve("server.jsonl"),echoInput.resolve("client.jsonl"),echoInput.resolve("keys.jsonl"),
                                            s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                                    .resolve("blind-lab-isolation/run-31-before.json"))) {
                                        String bytes=Files.readString(oldStream);echoBytes.put(oldStream,bytes);
                                        Files.writeString(oldStream,oldStream.getFileName().toString().endsWith("before.json") ? "{}" : bytes+" ");
                                        helper.assertTrue(command.apply("echo_motion_repeat") != 1 && score.apply("#stage")==3,
                                                "Explicit echo repeat refuses corrupt retained streams before replacing the actor");
                                        Files.writeString(oldStream,bytes);
                                    }
                                    echoActor.setNoAi(false);
                                    helper.assertTrue(command.apply("echo_motion_repeat") != 1,
                                            "Explicit echo repeat refuses an unheld predecessor");
                                    echoActor.setNoAi(true);
                                    var repeatReady = new JsonObject();
                                    repeatReady.addProperty("ready",com.frozendawn.labbridge.LabBlindChannels.echoMotionReady(source));
                                    repeatReady.addProperty("stage",score.apply("#stage"));repeatReady.addProperty("archived",score.apply("#archived"));
                                    repeatReady.addProperty("inconclusive",score.apply("#inconclusive"));repeatReady.addProperty("playerMode",player.gameMode.getGameModeForPlayer().getName());
                                    repeatReady.addProperty("actor",player.getPersistentData().getUUID("BlindLabChannelActor").toString());
                                    repeatReady.addProperty("probe",player.getPersistentData().getUUID("BlindLabInputProbe").toString());
                                    repeatReady.add("gameplayStart",com.google.gson.JsonParser.parseString(Files.readString(echoReplay.resolve("start.json"))));
                                    repeatReady.add("gameplaySummary",com.google.gson.JsonParser.parseString(Files.readString(echoReplay.resolve("summary.json"))));
                                    repeatReady.add("inputStart",com.google.gson.JsonParser.parseString(Files.readString(echoInput.resolve("start.json"))));
                                    repeatReady.add("inputSummary",com.google.gson.JsonParser.parseString(Files.readString(echoInput.resolve("summary.json"))));
                                    Files.writeString(echoProofDirectory.resolve("echo-repeat-ready.json"),repeatReady+"\n");
                                    helper.assertTrue(command.apply("echo_motion_repeat") == 1 && score.apply("#stage")==1
                                            && score.apply("#run")==31 && score.apply("#timer")==0,
                                            "Authorized echo repeat stages the same protocol once with no consumed frame");
                                    var repeatEchoActor=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                            new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                    helper.assertTrue(!repeatEchoActor.getUUID().equals(echoActor.getUUID()) && repeatEchoActor.isNoAi()
                                            && repeatEchoActor.getHealth()==40 && player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.CREATIVE,
                                            "Repeat owns a fresh held actor and preserves the observation protocol");
                                    for(var bytes:echoBytes.entrySet())helper.assertTrue(Files.readString(bytes.getKey()).equals(bytes.getValue()),
                                            "Repeat preserves every retained gameplay and input predecessor byte");
                                    helper.assertTrue(command.apply("echo_motion_repeat") != 1,
                                            "Repeat cannot prepare twice or replace a staged encounter");
                                    // Exercise a real full repeat with the client ending one callback earlier than the server.
                                    var repeatedStart = run(recordedRuntime,"function",args);
                                    helper.assertTrue(repeatedStart.get("commandOutcome").getAsString().equals("SUCCESS"),
                                            "Fresh repeat starts through the bound input bridge once");
                                    repeatEchoActor.getRandom().setSeed(437);
                                    int repeatedDirection=1;
                                    for(int tick=1;tick<=480;tick++) {
                                        s.clock(s.level.getGameTime()+1);
                                        if(tick>80&&tick<=300) {
                                            if(player.getZ()>=7.1)repeatedDirection=-1;
                                            if(player.getZ()<=3.9)repeatedDirection=1;
                                            player.setPos(17.5,-44,player.getZ()+repeatedDirection*.08);
                                        }
                                        player.setOnGround(false);
                                        if(tick==121||tick==361)for(int z=4;z<=6;z++)s.level.setBlock(
                                                new net.minecraft.core.BlockPos(11,-43,z),(tick==121
                                                ?net.minecraft.world.level.block.Blocks.GLASS:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),3);
                                        s.level.tickNonPassenger(repeatEchoActor);
                                        if(tick<=479)recordedRuntime.inputClient(timelineClient(recordedRuntime,player,s.level.getGameTime(),tick),false);
                                        helper.assertTrue(command.apply("channels_tick")==1,"Repeat records its real gameplay frame");
                                        recordedRuntime.serverTick(s.server);
                                    }
                                    var repeatedCapture=run(recordedRuntime,"input_dump",new JsonObject());
                                    helper.assertTrue(repeatedCapture.get("clientFrames").getAsInt()==479&&score.apply("#inconclusive")==0
                                            &&com.frozendawn.labbridge.LabBlindChannels.echoMotionReady(source)==0
                                            &&com.frozendawn.labbridge.LabBlindChannels.hearingReady(source)==1,
                                            "Continuous479-client timeline qualifies only the new transition; old repeat minimum stays unchanged");
                                    var retainedRepeat=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                            .resolve("blind-lab-results/run-31-"+repeatEchoActor.getUUID());
                                    String retainedRepeatTrace=Files.readString(retainedRepeat.resolve("trace.jsonl"));
                                    var repeatInput=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                            .resolve("blind-lab-input/probe-"+repeatedCapture.get("probe").getAsString());
                                    for(var oldStream:java.util.List.of(retainedRepeat.resolve("trace.jsonl"),retainedRepeat.resolve("decisions.json"),
                                            repeatInput.resolve("server.jsonl"),repeatInput.resolve("client.jsonl"),repeatInput.resolve("keys.jsonl"))) {
                                        String original=Files.readString(oldStream);Files.writeString(oldStream,original+" ");
                                        helper.assertTrue(command.apply("hearing_bend")!=1&&score.apply("#run")==31,
                                                "New hearing transition refuses every corrupted predecessor stream");
                                        Files.writeString(oldStream,original);
                                    }
                                    repeatEchoActor.setNoAi(false);helper.assertTrue(command.apply("hearing_bend")!=1,
                                            "Hearing cannot replace a released predecessor");repeatEchoActor.setNoAi(true);
                                    java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> openLayout=new java.util.HashMap<>();
                                    JsonObject hearingProof=new JsonObject();
                                    for(int hearingRun=32;hearingRun<=33;hearingRun++) {
                                        String transition=hearingRun==32?"hearing_bend":"hearing_bend_closed_qualified";
                                        helper.assertTrue(command.apply(transition)==1&&score.apply("#run")==hearingRun&&score.apply("#stage")==1,
                                                "Hearing pair advances a fully archived predecessor once");
                                        var hearingActor=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                                new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                        hearingActor.getRandom().setSeed(439);player.setOnGround(true);
                                        for(int x=1;x<=29;x++)for(int y=-48;y<=-39;y++)for(int z=-11;z<=9;z++) {
                                            var pos=new net.minecraft.core.BlockPos(x,y,z);var state=s.level.getBlockState(pos);
                                            if(hearingRun==32)openLayout.put(pos,state);
                                            else helper.assertTrue(state.equals(pos.equals(new net.minecraft.core.BlockPos(9,-42,5))
                                                    ?net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState():openLayout.get(pos)),
                                                    "Only the aperture changes between matched hearing layouts");
                                        }
                                        guards.get(1).getAsJsonObject().addProperty("equals",hearingRun);Files.writeString(recordedPolicy,policyRoot.toString());
                                        var hearingStart=run(recordedRuntime,"function",args);
                                        helper.assertTrue(hearingStart.get("commandOutcome").getAsString().equals("SUCCESS")
                                                &&!player.getAbilities().flying&&!player.isNoGravity()&&player.onGround(),"Supported feet are primed before the first Survival mining tick");
                                        var mined=new net.minecraft.core.BlockPos(14,-43,5);int heardFrames=0;
                                        for(int tick=1;tick<=480;tick++) {
                                            // Explicit legacy recorder control reproduces the original two cached ground flags.
                                            s.clock(s.level.getGameTime()+1);player.setOnGround(hearingRun==32?tick>2:tick>1);
                                            if(tick==81)player.gameMode.handleBlockBreakAction(mined,
                                                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                                                    net.minecraft.core.Direction.WEST,s.level.getMaxBuildHeight(),0);
                                            if(tick==240)player.gameMode.handleBlockBreakAction(mined,
                                                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                                                    net.minecraft.core.Direction.WEST,s.level.getMaxBuildHeight(),1);
                                            player.gameMode.tick();s.level.tickNonPassenger(hearingActor);
                                            if(hearingActor.blindController().signal()!=null)heardFrames++;
                                            helper.assertTrue(hearingActor.blindController().tracks().stream().allMatch(contact->
                                                    contact.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING&&contact.subject()==null),
                                                    "Hearing yields anonymous coarse contacts without an identified player");
                                            if(tick<=479)recordedRuntime.inputClient(timelineClient(recordedRuntime,player,s.level.getGameTime(),tick),false);
                                            helper.assertTrue(command.apply("channels_tick")==1,"Matched hearing records all480 real gameplay ticks");
                                            recordedRuntime.serverTick(s.server);
                                            if(tick==200)helper.assertTrue(command.apply(transition)!=1,"A live hearing view cannot be replaced");
                                        }
                                        var hearingCapture=run(recordedRuntime,"input_dump",new JsonObject());
                                        var entries=hearingActor.decisionJournal().entries();
                                        long tools=entries.stream().filter(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("kind=TOOL")).count();
                                        helper.assertTrue(score.apply("#archived")==0&&score.apply("#inconclusive")== (hearingRun==32?2:1)&&tools>0
                                                &&hearingCapture.get("serverFrames").getAsInt()==480&&hearingCapture.get("clientFrames").getAsInt()==479
                                                &&hearingCapture.get("dropped").getAsInt()==0,"Accepted mining preserves clean paired captures");
                                        helper.assertTrue(hearingRun==32?heardFrames>0:heardFrames==0,"Open bend heard; sealed bend has no received contact");
                                        helper.assertTrue(entries.stream().noneMatch(e->e.event().equals("BLIND_ECHO_RETURN")&&e.detail().contains("outcome=ADMITTED")
                                                ||e.event().equals("BLIND_VIBRATION")&&e.detail().contains("outcome=DETECTED")),
                                                "No echo or structural reception competes with air hearing");
                                        if(hearingRun==32) {
                                            helper.assertTrue(command.apply("hearing_bend_closed")!=1
                                                    &&com.frozendawn.labbridge.LabBlindChannels.hearingQualifiedReady(source)==1,
                                                    "Original whole32 stays inconclusive; only the narrow qualification can advance");
                                            var legacy=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                                    .resolve("blind-lab-results/run-32-"+hearingActor.getUUID());
                                            for(var file:java.util.List.of(legacy.resolve("trace.jsonl"),legacy.resolve("decisions.json"))) {
                                                String original=Files.readString(file);Files.writeString(file,original+" ");
                                                helper.assertTrue(command.apply("hearing_bend_closed_qualified")!=1&&score.apply("#run")==32,
                                                        "Qualified hearing still refuses corrupt raw archives before scene replacement");
                                                Files.writeString(file,original);
                                            }
                                            hearingActor.setNoAi(false);helper.assertTrue(command.apply("hearing_bend_closed_qualified")!=1,
                                                    "Qualified hearing refuses an unheld actor");hearingActor.setNoAi(true);
                                        }
                                        var proof=new JsonObject();proof.addProperty("wholeViewFlags",score.apply("#inconclusive"));proof.addProperty("actor",hearingActor.getUUID().toString());
                                        proof.addProperty("selectedFrames",heardFrames);proof.addProperty("acceptedToolQueries",tools);
                                        proof.addProperty("frames",480);proof.addProperty("clientSamples",479);proof.addProperty("syntheticClientRows",true);
                                        hearingProof.add(String.valueOf(hearingRun),proof);
                                        helper.assertTrue(command.apply(transition)!=1&&command.apply("start")!=1
                                                &&Files.readString(retainedRepeat.resolve("trace.jsonl")).equals(retainedRepeatTrace),
                                                "Completed hearing views retain their predecessor and cannot replay");

                                    }
                                    hearingProof.addProperty("onlyApertureChanged",true);hearingProof.addProperty("productionSettingsUnchanged",true);
                                    Files.writeString(echoProofDirectory.resolve("bent-hearing.json"),hearingProof+"\n");
                                    JsonObject miningProof=new JsonObject();
                                    java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> connectedLayout=new java.util.HashMap<>();
                                    for(int miningRun=34;miningRun<=35;miningRun++) {
                                        String transition=miningRun==34?"mining_connected":"mining_isolated";
                                        helper.assertTrue(command.apply(transition)==1&&score.apply("#stage")==1&&score.apply("#run")==miningRun,
                                                "Mining advances only the retained qualified sealed33 or complete connected34");
                                        var miningActor=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                                                new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                        miningActor.getRandom().setSeed(439);
                                        for(int x=1;x<=29;x++)for(int y=-48;y<=-39;y++)for(int z=-11;z<=9;z++) {
                                            var pos=new net.minecraft.core.BlockPos(x,y,z);var state=s.level.getBlockState(pos);
                                            if(miningRun==34)connectedLayout.put(pos,state);
                                            else helper.assertTrue(state.equals(y==-45&&x>=12&&x<=15&&z>=3&&z<=7
                                                    ?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():connectedLayout.get(pos)),
                                                    "Only the20 support bridge blocks change; air and echoes stay sealed");
                                        }
                                        helper.assertTrue(command.apply("start")!=1&&miningActor.isNoAi()&&score.apply("#timer")==0,
                                                "Direct Start cannot bypass two-clock ground readiness");
                                        guards.get(1).getAsJsonObject().addProperty("equals",miningRun);Files.writeString(recordedPolicy,policyRoot.toString());
                                        var armed=run(recordedRuntime,"function",args);
                                        helper.assertTrue(armed.get("accepted").getAsString().equals("WAITING_FOR_GROUND")
                                                &&player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL
                                                &&miningActor.isNoAi()&&score.apply("#timer")==0,"Start arms readiness while the actor and scoring stay held");
                                        for(int tick=1;tick<=44;tick++) {
                                            s.clock(s.level.getGameTime()+1);player.setOnGround(tick>2);
                                            var row=timelineClient(recordedRuntime,player,s.level.getGameTime(),tick);row.addProperty("onGround",tick>4);
                                            row.addProperty("x",player.getX());row.addProperty("y",player.getY());row.addProperty("z",player.getZ());
                                            row.add("mapped",new JsonObject());row.add("windowKeys",new JsonObject());
                                            recordedRuntime.inputClient(row,false);recordedRuntime.serverTick(s.server);
                                            helper.assertTrue(tick==44?score.apply("#stage")==2&&!miningActor.isNoAi():score.apply("#stage")==1&&miningActor.isNoAi(),
                                                    "Independent early client/server false flags hold all scoring until40 valid ticks on both clocks");
                                            helper.assertTrue(score.apply("#timer")==0&&score.apply("#frames")==0,"Readiness never contributes encounter frames");
                                        }
                                        var miningInputStart=run(recordedRuntime,"input_dump",new JsonObject());
                                        var ground=miningInputStart.getAsJsonObject("groundCheck");
                                        helper.assertTrue(ground.get("reason").getAsString().equals("GROUND_CONFIRMED")
                                                &&ground.get("serverSamples").getAsInt()==44&&ground.get("stableTicks").getAsInt()==40
                                                &&miningInputStart.get("serverFrames").getAsInt()==0,"Readiness is archived before input and AI release");
                                        var mined=new net.minecraft.core.BlockPos(18,-43,5);int contactFrames=0;
                                        for(int tick=1;tick<=480;tick++) {
                                            s.clock(s.level.getGameTime()+1);
                                            if(tick==81)player.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                                                    net.minecraft.core.Direction.WEST,s.level.getMaxBuildHeight(),0);
                                            if(tick==241)player.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                                                    net.minecraft.core.Direction.WEST,s.level.getMaxBuildHeight(),1);
                                            player.gameMode.tick();s.level.tickNonPassenger(miningActor);
                                            if(miningActor.blindController().signal()!=null)contactFrames++;
                                            helper.assertTrue(miningActor.blindController().tracks().stream().allMatch(contact->
                                                    contact.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION&&contact.subject()==null),
                                                    "Connected mining yields only anonymous structural contacts");
                                            if(tick<=479)recordedRuntime.inputClient(timelineClient(recordedRuntime,player,s.level.getGameTime(),tick),false);
                                            helper.assertTrue(command.apply("channels_tick")==1,"Mining records all480 gameplay ticks");recordedRuntime.serverTick(s.server);
                                            if(tick==200)helper.assertTrue(command.apply(transition)!=1,"Live mining cannot be replaced");
                                        }
                                        var capture=run(recordedRuntime,"input_dump",new JsonObject());var entries=miningActor.decisionJournal().entries();
                                        long tools=entries.stream().filter(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("kind=TOOL")).count();
                                        long detected=entries.stream().filter(e->e.event().equals("BLIND_VIBRATION")&&e.detail().contains("outcome=DETECTED")).count();
                                        helper.assertTrue(score.apply("#archived")==1&&score.apply("#inconclusive")==0&&tools==16
                                                &&capture.get("serverFrames").getAsInt()==480&&capture.get("clientFrames").getAsInt()==479
                                                &&capture.get("dropped").getAsInt()==0,"Grounded mining retains complete gameplay/input without force-setting encounter ground flags");
                                        helper.assertTrue(miningRun==34?detected==tools&&contactFrames>0:detected==0&&contactFrames==0,
                                                "Same accepted tool impulses detect connected support and reject the isolated bridge gap");
                                        helper.assertTrue(entries.stream().noneMatch(e->e.event().equals("BLIND_AIR")&&!e.detail().contains("outcome=NO_PATH_WITHIN_RANGE")
                                                ||e.event().equals("BLIND_ECHO_QUERY")&&!e.detail().contains("outcome=NO_DIRECT_RETURN")||e.event().equals("BLIND_LOCK")),
                                                "No air hearing, returned echo, unknown air budget or identified lock competes");
                                        if(miningRun==34) {
                                            var gd=java.nio.file.Path.of(ground.get("directory").getAsString());String original=Files.readString(gd.resolve("client.jsonl"));
                                            Files.writeString(gd.resolve("client.jsonl"),original+" ");
                                            helper.assertTrue(command.apply("mining_isolated")!=1&&score.apply("#run")==34,"Corrupted readiness refuses the second view before replacement");
                                            Files.writeString(gd.resolve("client.jsonl"),original);
                                            miningActor.setNoAi(false);helper.assertTrue(command.apply("mining_isolated")!=1,"An unheld mining predecessor refuses transition");miningActor.setNoAi(true);
                                        }
                                        var proof=new JsonObject();proof.addProperty("actor",miningActor.getUUID().toString());proof.addProperty("toolQueries",tools);
                                        proof.addProperty("detectedToolQueries",detected);proof.addProperty("contactFrames",contactFrames);proof.addProperty("frames",480);
                                        proof.addProperty("clientSamples",479);proof.add("groundCheck",ground);proof.addProperty("syntheticClientRows",true);miningProof.add(String.valueOf(miningRun),proof);
                                        helper.assertTrue(command.apply(transition)!=1&&command.apply("start")!=1,"Completed mining cannot replay");
                                        if(miningRun==35) {
                                            helper.assertTrue(com.frozendawn.labbridge.LabBlindChannels.decoyReady(source)==1,"Complete isolated mining qualifies the decoy predecessor");
                                            var oldGround=java.nio.file.Path.of(ground.get("directory").getAsString());String groundBytes=Files.readString(oldGround.resolve("client.jsonl"));
                                            Files.writeString(oldGround.resolve("client.jsonl"),groundBytes+" ");
                                            helper.assertTrue(command.apply("projectile_decoy")!=1&&score.apply("#run")==35,"Corrupt readiness refuses decoy replacement");
                                            Files.writeString(oldGround.resolve("client.jsonl"),groundBytes);
                                            miningActor.setNoAi(false);helper.assertTrue(command.apply("projectile_decoy")!=1,"Unheld predecessor refuses decoy");miningActor.setNoAi(true);
                                            var decoyOriginalBytes=new java.util.HashMap<java.nio.file.Path,String>();
                                            for(int decoyRun=36;decoyRun<=53;decoyRun++) {
                                            String decoyTransition=decoyRun==36?"projectile_decoy":decoyRun==37?"projectile_decoy_repeat":decoyRun==38?"projectile_decoy_contact":decoyRun==39?"door_hearing":decoyRun==40?"door_hearing_repeat":decoyRun==41?"machine_hearing":decoyRun==42?"door_hearing_contact":decoyRun==43?"door_hearing_contact_repeat":decoyRun==44?"bell_reaction":decoyRun==45?"explosion_reaction":decoyRun==46?"heat_reaction":decoyRun==47?"breach_reaction":decoyRun==48?"protected_wall":decoyRun==49?"griefing_disabled":decoyRun==50?"melee_reach":decoyRun==51?"melee_range":decoyRun==52?"melee_range_repeat":"melee_obstruction";
                                            if(decoyRun==37) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.repeatReady(source)==1,"Complete physical decoy qualifies the guarded repeat");
                                                var traceFile=decoyOriginalBytes.keySet().stream().filter(path->path.getFileName().toString().equals("trace.jsonl")).findFirst().orElseThrow();String original=Files.readString(traceFile);Files.writeString(traceFile,original+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==36,"Damaged gameplay refuses repeat");Files.writeString(traceFile,original);
                                                var inputFile=decoyOriginalBytes.keySet().stream().filter(path->path.getFileName().toString().equals("client.jsonl")&&path.toString().contains("blind-lab-input")).findFirst().orElseThrow();String inputBytes=Files.readString(inputFile);Files.writeString(inputFile,inputBytes+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1,"Damaged client input refuses repeat");Files.writeString(inputFile,inputBytes);
                                                var priorActor=(com.frozendawn.entity.ArchitectEntity)s.level.getEntity(player.getPersistentData().getUUID("BlindLabDecoyActor"));priorActor.setNoAi(false);
                                                helper.assertTrue(command.apply(decoyTransition)!=1,"Unheld original actor refuses repeat");priorActor.setNoAi(true);
                                            }
                                            if(decoyRun==38) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.contactReady(source)==1,"Remote37 qualifies fresh-contact38");
                                                var priorActor=(com.frozendawn.entity.ArchitectEntity)s.level.getEntity(player.getPersistentData().getUUID("BlindLabDecoyActor"));
                                                var priorDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-37-"+priorActor.getUUID());
                                                var priorSummary=priorDir.resolve("summary.json");String original=Files.readString(priorSummary);var damaged=com.google.gson.JsonParser.parseString(original).getAsJsonObject();damaged.addProperty("remoteLanding",false);Files.writeString(priorSummary,damaged+"\n");
                                                helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==37,"Nonremote37 refuses contact transition");Files.writeString(priorSummary,original);
                                                var stream=priorDir.resolve("trace.jsonl");String retained=Files.readString(stream);Files.writeString(stream,retained+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==37,"Damaged37 refuses contact transition");Files.writeString(stream,retained);
                                                if(decoyRun==51) {
                                                    var file=priorDir.resolve("melee-shutter.jsonl");String originalShutter=Files.readString(file);Files.writeString(file,originalShutter+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==50,"Damaged50 shutter refuses range51 before mutation");Files.writeString(file,originalShutter);
                                                }
                                                var inputDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-input/probe-"+player.getPersistentData().getUUID("BlindLabInputProbe"));
                                                var inputFile=inputDir.resolve("client.jsonl");String clientBytes=Files.readString(inputFile);Files.writeString(inputFile,clientBytes+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1,"Damaged37 input refuses contact transition");Files.writeString(inputFile,clientBytes);
                                                priorActor.setNoAi(false);helper.assertTrue(command.apply(decoyTransition)!=1,"Unheld37 refuses contact transition");priorActor.setNoAi(true);
                                            }
                                            if(decoyRun==39) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.doorReady(source)==1,"Preserved38 qualifies door39");
                                                var priorActor=(com.frozendawn.entity.ArchitectEntity)s.level.getEntity(player.getPersistentData().getUUID("BlindLabDecoyActor"));
                                                var priorDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-38-"+priorActor.getUUID());
                                                var stream=priorDir.resolve("trace.jsonl");String original=Files.readString(stream);Files.writeString(stream,original+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==38,"Damaged38 refuses door replacement");Files.writeString(stream,original);
                                                if(decoyRun==51) {
                                                    var file=priorDir.resolve("melee-shutter.jsonl");String originalShutter=Files.readString(file);Files.writeString(file,originalShutter+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==50,"Damaged50 shutter refuses range51 before mutation");Files.writeString(file,originalShutter);
                                                }
                                                var inputDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-input/probe-"+player.getPersistentData().getUUID("BlindLabInputProbe"));
                                                var inputFile=inputDir.resolve("client.jsonl");String clientBytes=Files.readString(inputFile);Files.writeString(inputFile,clientBytes+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1,"Damaged38 input refuses door replacement");Files.writeString(inputFile,clientBytes);
                                                var captureSummary=inputDir.resolve("summary.json");String summaryBytes=Files.readString(captureSummary);
                                                var ui=com.google.gson.JsonParser.parseString(clientBytes.lines().findFirst().orElseThrow()).getAsJsonObject();ui.addProperty("screenOpen",true);
                                                String replaced=ui+"\n"+clientBytes.substring(clientBytes.indexOf('\n')+1);Files.writeString(inputFile,replaced);
                                                var edited=com.google.gson.JsonParser.parseString(summaryBytes).getAsJsonObject();
                                                edited.getAsJsonObject("hashes").addProperty("client.jsonl",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(replaced.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                                                Files.writeString(captureSummary,edited+"\n");helper.assertTrue(command.apply(decoyTransition)!=1,"Rehashed new UI contamination cannot use exact human38 exception");
                                                Files.writeString(inputFile,clientBytes);Files.writeString(captureSummary,summaryBytes);
                                                priorActor.setNoAi(false);helper.assertTrue(command.apply(decoyTransition)!=1,"Unheld38 refuses door replacement");priorActor.setNoAi(true);
                                            }
                                            if(decoyRun>=40) {
                                                int priorRun=decoyRun-1;
                                                helper.assertTrue((decoyRun==40?com.frozendawn.labbridge.LabBlindDecoy.doorRepeatReady(source):decoyRun==41?com.frozendawn.labbridge.LabBlindDecoy.machineReady(source):decoyRun==42?com.frozendawn.labbridge.LabBlindDecoy.doorContactReady(source):decoyRun==43?com.frozendawn.labbridge.LabBlindDecoy.doorContactRepeatReady(source):decoyRun==44?com.frozendawn.labbridge.LabBlindDecoy.bellReady(source):decoyRun==45?com.frozendawn.labbridge.LabBlindDecoy.explosionReady(source):decoyRun==46?com.frozendawn.labbridge.LabBlindDecoy.heatReady(source):decoyRun==47?com.frozendawn.labbridge.LabBlindDecoy.breachReady(source):decoyRun==48?com.frozendawn.labbridge.LabBlindDecoy.protectedWallReady(source):decoyRun==49?com.frozendawn.labbridge.LabBlindDecoy.griefingReady(source):decoyRun==50?com.frozendawn.labbridge.LabBlindDecoy.meleeReady(source):decoyRun==51?com.frozendawn.labbridge.LabBlindDecoy.meleeRangeReady(source):decoyRun==52?com.frozendawn.labbridge.LabBlindDecoy.meleeRangeRepeatReady(source):com.frozendawn.labbridge.LabBlindDecoy.meleeObstructionReady(source))==1,"Complete39 door pair qualifies repeat regardless of hearing outcome");
                                                var priorActor=(com.frozendawn.entity.ArchitectEntity)s.level.getEntity(player.getPersistentData().getUUID("BlindLabDecoyActor"));
                                                var priorDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-"+priorRun+"-"+priorActor.getUUID());
                                                for(String name:java.util.List.of("trace.jsonl",(decoyRun==52||decoyRun==53)?"range-damage.jsonl":decoyRun==51?"melee-damage.jsonl":decoyRun==47?"heaters.jsonl":decoyRun==46?"blasts.jsonl":decoyRun==45?"bells.jsonl":decoyRun==42?"machines.jsonl":"doors.jsonl")) {
                                                    var file=priorDir.resolve(name);String bytes=Files.readString(file);Files.writeString(file,bytes+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==priorRun,"Damaged predecessor "+name+" refuses repeat before mutation");Files.writeString(file,bytes);
                                                }
                                                if(decoyRun==51) {
                                                    var file=priorDir.resolve("melee-shutter.jsonl");String originalShutter=Files.readString(file);Files.writeString(file,originalShutter+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==50,"Damaged50 shutter refuses range51 before mutation");Files.writeString(file,originalShutter);
                                                }
                                                var inputDir=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-input/probe-"+player.getPersistentData().getUUID("BlindLabInputProbe"));
                                                var inputFile=inputDir.resolve("client.jsonl");String original=Files.readString(inputFile);Files.writeString(inputFile,original+" ");
                                                helper.assertTrue(command.apply(decoyTransition)!=1,"Damaged39 client timeline refuses repeat");Files.writeString(inputFile,original);
                                                if(decoyRun==53) {
                                                    var shutter=priorDir.resolve("range-shutter.jsonl");String originalShutter=Files.readString(shutter);Files.writeString(shutter,originalShutter+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==52,"Corrupt52 shutter refuses obstruction53");Files.writeString(shutter,originalShutter);
                                                }
                                                if(decoyRun==52) {
                                                    var shutter=priorDir.resolve("range-shutter.jsonl");String oldShutter=Files.readString(shutter);Files.writeString(shutter,oldShutter+" ");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==51,"Altered51 range shutter refuses repeat before mutation");Files.writeString(shutter,oldShutter);
                                                    var captureSummary=inputDir.resolve("summary.json");String summaryBytes=Files.readString(captureSummary);
                                                    var ui=com.google.gson.JsonParser.parseString(original.lines().findFirst().orElseThrow()).getAsJsonObject();ui.addProperty("screenOpen",true);
                                                    String contaminated=ui+"\n"+original.substring(original.indexOf('\n')+1);Files.writeString(inputFile,contaminated);
                                                    var edited=com.google.gson.JsonParser.parseString(summaryBytes).getAsJsonObject();
                                                    edited.getAsJsonObject("hashes").addProperty("client.jsonl",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contaminated.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                                                    Files.writeString(captureSummary,edited+"\n");
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==51,"Rehashed UI contamination cannot use exact archived human51 exception");
                                                    Files.writeString(inputFile,original);Files.writeString(captureSummary,summaryBytes);
                                                    helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.meleeRangeRepeatReady(source)==1,"Restored qualified51 permits repeat");
                                                }
                                                if(decoyRun==43) {
                                                    var captureSummary=inputDir.resolve("summary.json");String summaryBytes=Files.readString(captureSummary);
                                                    var gameplaySummary=priorDir.resolve("summary.json");String gameplayBytes=Files.readString(gameplaySummary);
                                                    var ui=com.google.gson.JsonParser.parseString(original.lines().findFirst().orElseThrow()).getAsJsonObject();ui.addProperty("screenOpen",true);
                                                    String contaminated=ui+"\n"+original.substring(original.indexOf('\n')+1);Files.writeString(inputFile,contaminated);
                                                    var edited=com.google.gson.JsonParser.parseString(summaryBytes).getAsJsonObject();
                                                    edited.getAsJsonObject("hashes").addProperty("client.jsonl",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contaminated.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                                                    Files.writeString(captureSummary,edited+"\n");
                                                    var gameplay=com.google.gson.JsonParser.parseString(gameplayBytes).getAsJsonObject();gameplay.addProperty("complete",false);gameplay.addProperty("inconclusiveFrames",1);gameplay.addProperty("doorPairPerformed",false);Files.writeString(gameplaySummary,gameplay+"\n");
                                                    s.level.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#archived"),objective).set(0);
                                                    s.level.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#inconclusive"),objective).set(1);
                                                    helper.assertTrue(command.apply(decoyTransition)!=1&&score.apply("#run")==42,"New rehashed UI contamination cannot use the exact reviewed human42 recovery");
                                                    Files.writeString(inputFile,original);Files.writeString(captureSummary,summaryBytes);Files.writeString(gameplaySummary,gameplayBytes);
                                                    s.level.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#archived"),objective).set(1);
                                                    s.level.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#inconclusive"),objective).set(0);
                                                }
                                                priorActor.setNoAi(false);helper.assertTrue(command.apply(decoyTransition)!=1,"Unheld39 refuses repeat");priorActor.setNoAi(true);
                                            }
                                            helper.assertTrue(command.apply(decoyTransition)==1&&score.apply("#run")==decoyRun,"Decoy stages once after preserved35");
                                            var decoyActor=s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).getFirst();
                                            decoyActor.getRandom().setSeed(decoyRun==40?157:439);player.getInventory().selected=0;
                                            if(decoyRun==40) {
                                                var matrix=new com.google.gson.JsonArray();var pos=com.frozendawn.labbridge.LabBlindDoor.DOOR;
                                                var lower=s.level.getBlockState(pos);var upper=s.level.getBlockState(pos.above());
                                                for(boolean open:java.util.List.of(false,true)) {
                                                    s.level.setBlock(pos,lower.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,open),3);
                                                    s.level.setBlock(pos.above(),upper.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,open),3);
                                                    for(int xi=0;xi<4;xi++)for(int zi=0;zi<6;zi++)for(int half=0;half<2;half++) {
                                                        var receiver=new net.minecraft.world.phys.Vec3(13.25+xi*.5,decoyActor.getEyeY(),4.25+zi*.5);
                                                        var sourcePoint=net.minecraft.world.phys.Vec3.atCenterOf(pos.above(half));
                                                        var path=com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level,receiver,sourcePoint,16,256);
                                                        var row=new JsonObject();row.add("receiver",new com.google.gson.Gson().toJsonTree(receiver));row.add("source",new com.google.gson.Gson().toJsonTree(sourcePoint));
                                                        row.addProperty("doorOpen",open);row.addProperty("reached",path.reached());row.addProperty("cost",path.cost());row.addProperty("work",path.visited());row.addProperty("exhausted",path.exhausted());matrix.add(row);
                                                    }
                                                }
                                                s.level.setBlock(pos,lower,3);s.level.setBlock(pos.above(),upper,3);
                                                Files.writeString(echoProofDirectory.resolve("door-hearing-range-matrix.json"),matrix+"\n");
                                                helper.assertTrue(matrix.size()==96&&java.util.stream.StreamSupport.stream(matrix.spliterator(),false).allMatch(x->x.getAsJsonObject().get("reached").getAsBoolean()
                                                    &&!x.getAsJsonObject().get("exhausted").getAsBoolean()&&x.getAsJsonObject().get("cost").getAsDouble()<16),"Both door halves/states remain inside range16 and reserved256 work across the shortened corridor");
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.geometry(s.level),"Range sampling restores the exact fresh door geometry");
                                            }
                                            if(decoyRun==48) {
                                                helper.assertTrue(s.level.getBlockState(new net.minecraft.core.BlockPos(13,-43,5)).isAir()
                                                    &&s.level.getBlockState(new net.minecraft.core.BlockPos(13,-44,5)).is(com.frozendawn.init.ModBlocks.ACHERONITE_BLOCK.get())
                                                    &&s.level.getBlockState(new net.minecraft.core.BlockPos(13,-42,5)).is(net.minecraft.world.level.block.Blocks.GLASS)
                                                    &&decoyActor.getBoundingBox().getYsize()>1,
                                                    "Center sight slot shows the actor above the protected lower wall but is too low for physical passage");
                                            }
                                            if(decoyRun==49) {
                                                for(int y=-44;y<=-43;y++)for(int z=4;z<=6;z++)helper.assertTrue(
                                                    com.frozendawn.entity.architect.ArchitectBlockEnvironment.breakRejection(s.level,new net.minecraft.core.BlockPos(13,y,z),java.util.List.of())==null,
                                                    "Every ordinary stone/slab cell is eligible material; disabled griefing is the mining exclusion");
                                            }
                                            if(decoyRun>=47&&decoyRun<=49) {
                                                var wall=new net.minecraft.core.BlockPos(13,-44,5);var saved=s.level.getBlockState(wall);s.level.setBlock(wall,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Incomplete fresh wall refuses ground arming");s.level.setBlock(wall,saved,3);
                                                var door=com.frozendawn.labbridge.LabBlindDoor.DOOR;var lower=s.level.getBlockState(door);var upper=s.level.getBlockState(door.above());
                                                s.level.setBlock(door,lower.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);s.level.setBlock(door.above(),upper.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Already-open fresh door refuses ground arming");s.level.setBlock(door,lower,3);s.level.setBlock(door.above(),upper,3);
                                                boolean expectedGriefing=decoyRun!=49;
                                                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(!expectedGriefing,s.server);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Wrong griefing rule refuses readiness");s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(expectedGriefing,s.server);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)!=null,"Restored exact fresh wall and closed door qualify readiness");
                                                var matrix=new com.google.gson.JsonArray();
                                                s.level.setBlock(door,lower.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);
                                                s.level.setBlock(door.above(),upper.setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true),3);
                                                for(double x:new double[]{12.25,12.75})for(double z:new double[]{5.25,5.75})for(int half=0;half<2;half++) {
                                                    var receiver=new net.minecraft.world.phys.Vec3(x,decoyActor.getEyeY(),z);
                                                    var point=net.minecraft.world.phys.Vec3.atCenterOf(door.above(half));
                                                    var path=com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level,receiver,point,16,256);
                                                    var row=new JsonObject();row.add("receiver",new com.google.gson.Gson().toJsonTree(receiver));row.add("source",new com.google.gson.Gson().toJsonTree(point));
                                                    row.addProperty("half",half);row.addProperty("reached",path.reached());row.addProperty("cost",path.cost());row.addProperty("work",path.visited());row.addProperty("exhausted",path.exhausted());matrix.add(row);
                                                }
                                                Files.writeString(echoProofDirectory.resolve(decoyRun==47?"wall-door-range-matrix.json":decoyRun==48?"protected-wall-door-range-matrix.json":"griefing-door-range-matrix.json"),matrix+"\n");
                                                s.level.setBlock(door,lower,3);s.level.setBlock(door.above(),upper,3);
                                                helper.assertTrue(java.util.stream.StreamSupport.stream(matrix.spliterator(),false).filter(v->v.getAsJsonObject().get("half").getAsInt()==1)
                                                    .allMatch(v->v.getAsJsonObject().get("reached").getAsBoolean()&&!v.getAsJsonObject().get("exhausted").getAsBoolean()&&v.getAsJsonObject().get("cost").getAsDouble()<16),
                                                    "Upper door emission reaches nearby receiver points inside range16 with reserved256 work: "+matrix);
                                                helper.assertTrue((decoyRun==47?com.frozendawn.labbridge.LabBlindBreach.fresh(s.level):decoyRun==48?com.frozendawn.labbridge.LabBlindProtectedWall.fresh(s.level):com.frozendawn.labbridge.LabBlindGriefing.fresh(s.level)),"Range sampling restores exact fresh wall and closed door");
                                            }
                                            if(decoyRun>=50) {
                                                player.getPersistentData().putBoolean("BlindNativeMeleeDamageable",true);
                                                // All these replay loops share one callback: no native player tick
                                                // has elapsed the ordinary 60-tick join protection, unlike the live client.
                                                try {
                                                    var protection=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                                                    protection.setAccessible(true);protection.setInt(player,0);
                                                } catch(ReflectiveOperationException failed) {throw new AssertionError("Cannot elapse the unticked native join protection",failed);}
                                                var slit=new net.minecraft.core.BlockPos(16,-43,5);var initialSlit=s.level.getBlockState(slit);s.level.setBlock(slit,net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Prematurely closed melee slit refuses ground arming");s.level.setBlock(slit,initialSlit,3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)!=null,"Fresh held melee actor and physical open slit qualify");
                                            }
                                            if(decoyRun==53) {
                                                helper.assertTrue(!player.isInvulnerableTo(s.level.damageSources().mobAttack(decoyActor))&&!decoyActor.hasLineOfSight(player)&&decoyActor.distanceTo(player)<=2.8,"Close obstruction has a damageable player and actual blocked eye LOS");
                                                var slit=new net.minecraft.core.BlockPos(16,-43,5);var initialSlit=s.level.getBlockState(slit);s.level.setBlock(slit,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                                                helper.assertTrue(decoyActor.hasLineOfSight(player)&&com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Removing the obstruction opens actual LOS but refuses exact fixture readiness");s.level.setBlock(slit,initialSlit,3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)!=null,"Restored obstruction qualifies");
                                            }
                                            if(decoyRun==51||decoyRun==52) {
                                                helper.assertTrue(!player.isInvulnerableTo(s.level.damageSources().mobAttack(decoyActor)),"Range player is actually damageable; immunity cannot make the negative pass");
                                                var gap=new net.minecraft.core.BlockPos(15,-43,5);s.level.setBlock(gap,net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Obstructed range aperture refuses readiness");s.level.setBlock(gap,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                                                decoyActor.setPos(15.5,-44,5.5);helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)==null,"Wrong close actor position refuses range readiness");decoyActor.setPos(14.5,-44,5.5);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindDecoy.inputViewActor(source)!=null&&decoyActor.hasLineOfSight(player)&&decoyActor.distanceTo(player)>2.8,"Real unobstructed range actor qualifies without damage immunity");
                                            }
                                            helper.assertTrue(command.apply(decoyTransition)!=1&&command.apply("start")!=1,"Cannot repeat or bypass decoy readiness");
                                            guards.get(1).getAsJsonObject().addProperty("equals",decoyRun);Files.writeString(recordedPolicy,policyRoot.toString());
                                            var decoyArmed=run(recordedRuntime,"function",args);helper.assertTrue(decoyArmed.get("waiting").getAsBoolean(),"Decoy arms both-clock ground check");
                                            for(int readyTick=1;readyTick<=44;readyTick++) {
                                                s.clock(s.level.getGameTime()+1);player.setOnGround(readyTick>2);
                                                var row=timelineClient(recordedRuntime,player,s.level.getGameTime(),readyTick);row.addProperty("onGround",readyTick>4);
                                                row.addProperty("x",player.getX());row.addProperty("y",player.getY());row.addProperty("z",player.getZ());row.add("mapped",new JsonObject());row.add("windowKeys",new JsonObject());
                                                recordedRuntime.inputClient(row,false);recordedRuntime.serverTick(s.server);
                                                helper.assertTrue(score.apply("#timer")==0&&decoyActor.isNoAi()==(readyTick<44),"No decoy frames or AI during settling");
                                            }
                                            int anonymousFrames=0;net.minecraft.world.entity.projectile.Snowball actualBall=null;
                                            for(int tick=1;tick<=480;tick++) {
                                                s.clock(s.level.getGameTime()+1);player.setOnGround(true);
                                                if((decoyRun<38&&tick==100)||(decoyRun==38&&score.apply("#decoy_cover")>0&&tick==score.apply("#decoy_cover")+16)) {
                                                    player.setYRot(90);player.setXRot(decoyRun==36?0:(float)-Math.toDegrees(Math.atan2(-40-player.getEyeY(),6)));
                                                    player.getMainHandItem().use(s.level,player,net.minecraft.world.InteractionHand.MAIN_HAND);
                                                    var balls=s.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Snowball.class,new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11));
                                                    helper.assertTrue(balls.size()==1,"Real snowball item admits exactly one physical projectile");actualBall=balls.getFirst();
                                                }
                                                if((decoyRun>=39&&decoyRun<=40&&(tick==100||tick==220))
                                                        ||(decoyRun>=47&&decoyRun<=49)&&tick==100||(decoyRun==42||decoyRun==43)&&(score.apply("#decoy_cover")>0&&tick==score.apply("#decoy_cover")+16||tick==220)) {
                                                    boolean opening=tick!=220;var pos=opening?com.frozendawn.labbridge.LabBlindDoor.DOOR.above():com.frozendawn.labbridge.LabBlindDoor.DOOR;
                                                    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.WEST,pos,false);
                                                    var used=player.gameMode.useItemOn(player,s.level,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
                                                    helper.assertTrue(used.consumesAction(),"Real door use succeeds with empty hand");
                                                    helper.assertTrue(s.level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==opening,"Door physically opens then closes");
                                                }
                                                if(actualBall!=null&&!actualBall.isRemoved())s.level.tickNonPassenger(actualBall);
                                                if(decoyRun==46) {var heater=(com.frozendawn.block.ThermalHeaterBlockEntity)s.level.getBlockEntity(com.frozendawn.labbridge.LabBlindHeat.MACHINE);heater.serverTick();}
                                                if(decoyRun==41) {
                                                    var heater=(com.frozendawn.block.ThermalHeaterBlockEntity)s.level.getBlockEntity(com.frozendawn.labbridge.LabBlindMachine.MACHINE);
                                                    heater.serverTick(); // actual production block ticker, not an injected sensory event
                                                }
                                                s.level.tickNonPassenger(decoyActor);
                                                if(tick<=479)recordedRuntime.inputClient(timelineClient(recordedRuntime,player,s.level.getGameTime(),tick),false);
                                                helper.assertTrue(command.apply("decoy_tick")==1,"Decoy records actual physics and ordinary AI");recordedRuntime.serverTick(s.server);
                                                helper.assertTrue(decoyActor.isBlindNoiseStunned()==decoyActor.blindController().diagnostics().startsWith("blind=STUN"),"Synced ear-covering flag follows stun only, including release into retreat and recovery");
                                                if(decoyActor.blindController().signal()!=null) {
                                                    anonymousFrames++;var signal=decoyActor.blindController().signal();helper.assertTrue(
                                                            signal.subject()==null&&(signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING||decoyRun==46&&signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.VIBRATION)
                                                            ||(decoyRun==53||decoyRun==52||decoyRun==51||decoyRun==50||decoyRun==38||decoyRun==42||decoyRun==43)&&player.getUUID().equals(signal.subject())&&signal.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.ECHO,
                                                            "Decoy selection retains anonymous hearing or genuinely returned player echoes");
                                                }
                                                if(decoyRun<38&&tick<100)helper.assertTrue(decoyActor.blindController().tracks().isEmpty(),"Quiet hidden thrower has no pre-impact contact");
                                            }
                                            var decoyCapture=run(recordedRuntime,"input_dump",new JsonObject());
                                            var decoyDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lab-results/run-"+decoyRun+"-"+decoyActor.getUUID());
                                            var decoySummary=com.google.gson.JsonParser.parseString(Files.readString(decoyDirectory.resolve("summary.json"))).getAsJsonObject();
                                            helper.assertTrue((decoyRun==53?decoySummary.get("meleeObstructionPerformed").getAsBoolean():(decoyRun==51||decoyRun==52)?decoySummary.get("meleeRangePerformed").getAsBoolean():decoyRun==50?decoySummary.get("meleePerformed").getAsBoolean():decoyRun==49?decoySummary.get("griefingDisabledPerformed").getAsBoolean():decoyRun==48?decoySummary.get("protectedWallPerformed").getAsBoolean():decoyRun==47?decoySummary.get("breachPerformed").getAsBoolean():decoyRun==46?decoySummary.get("heatCyclePerformed").getAsBoolean():decoyRun==41?decoySummary.get("machineCyclePerformed").getAsBoolean():decoyRun==45?decoySummary.get("explosionCyclePerformed").getAsBoolean():decoyRun==44?decoySummary.get("bellCyclePerformed").getAsBoolean():decoyRun>=39?decoySummary.get("doorPairPerformed").getAsBoolean():actualBall!=null&&actualBall.isRemoved()&&score.apply("#decoy_balls")==1)&&(decoyRun>=44||anonymousFrames>0)
                                                    &&decoySummary.get("complete").getAsBoolean()&&score.apply("#inconclusive")==0,decoyRun==47?"Real door hearing drives a completed wall breach without invalid frames":"Physical impact is recorded and anonymously investigated without invalid frames");
                                            var decoyEntries=com.google.gson.JsonParser.parseString(Files.readString(decoyDirectory.resolve("decisions.json"))).getAsJsonArray();
                                            if(decoyRun!=38&&decoyRun!=42&&decoyRun!=43&&decoyRun!=50&&decoyRun!=51&&decoyRun!=52&&decoyRun!=53)helper.assertTrue(java.util.stream.StreamSupport.stream(decoyEntries.spliterator(),false).noneMatch(e->
                                                    e.getAsJsonObject().get("event").getAsString().equals("BLIND_LOCK")||e.getAsJsonObject().get("event").getAsString().equals("BLIND_ECHO_RETURN")),"Decoy never identifies its hidden thrower");
                                            helper.assertTrue(decoyCapture.get("serverFrames").getAsInt()==480&&decoyCapture.get("clientFrames").getAsInt()==479&&decoyCapture.get("dropped").getAsInt()==0,"Decoy retains both independent synthetic clocks");
                                            var decoyProof=new JsonObject();decoyProof.addProperty("actor",decoyActor.getUUID().toString());decoyProof.addProperty("anonymousFrames",anonymousFrames);
                                            decoyProof.add("inputCapture",decoyCapture);decoyProof.addProperty("physicalSnowballUse",decoyRun<=38);decoyProof.addProperty("physicalDoorUse",(decoyRun>=39&&decoyRun<=40||decoyRun==42||decoyRun==43||decoyRun==47||decoyRun==48||decoyRun==49));decoyProof.addProperty("actualHeaterTicker",decoyRun==41);decoyProof.addProperty("syntheticClientRows",true);
                                            Files.writeString(echoProofDirectory.resolve(decoyRun==36?"projectile-decoy.json":decoyRun==37?"projectile-decoy-repeat.json":decoyRun==38?"projectile-decoy-contact.json":decoyRun==39?"door-hearing.json":decoyRun==40?"door-hearing-repeat.json":decoyRun==41?"machine-hearing.json":decoyRun==42?"door-hearing-contact.json":decoyRun==43?"door-hearing-contact-repeat.json":decoyRun==44?"bell-reaction.json":decoyRun==45?"explosion-reaction.json":decoyRun==46?"heat-reaction.json":decoyRun==47?"wall-breach.json":decoyRun==48?"protected-wall.json":decoyRun==49?"griefing-disabled.json":decoyRun==50?"melee-reach.json":decoyRun==51?"melee-range.json":decoyRun==52?"melee-range-repeat.json":"melee-obstruction.json"),decoyProof+"\n");
                                            helper.assertTrue(command.apply("start")!=1&&command.apply(decoyTransition)!=1,"Completed decoy cannot replay");helper.assertTrue(decoySummary.get("remoteLanding").getAsBoolean()==(decoyRun>=37&&decoyRun<=38),"Level near-glass impact is distinguished from an actual over-partition landing");
                                            if(decoyRun==48) {
                                                helper.assertTrue(anonymousFrames>0&&com.frozendawn.labbridge.LabBlindProtectedWall.performed(decoyDirectory,decoyEntries)
                                                    &&decoyActor.successfulBreakCount()==0&&decoyActor.getX()<13&&decoyActor.getHealth()==40&&player.getHealth()==20,
                                                    "Real received anonymous door hearing is investigated but all protected cells remain intact without mining or passage");
                                                decoyProof.addProperty("zeroBreaks",true);decoyProof.addProperty("noPassage",true);decoyProof.addProperty("materialRejection","TOO_HARD");
                                                Files.writeString(echoProofDirectory.resolve("protected-wall.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==50) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindMelee.performed(decoyDirectory,decoyEntries)&&decoyActor.getHealth()==40&&player.getHealth()>0&&player.getHealth()<20
                                                    &&score.apply("#decoy_cover")>0&&player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==0,
                                                    "Fresh real echo supplies one actual local melee hit; physical shutter blocks later damage and original knockback base restores");
                                                helper.assertTrue(decoyActor.blindController().signal()==null&&decoyActor.blindController().tracks().isEmpty(),"No covered target identity is refreshed after physical shutter");
                                                player.getPersistentData().putBoolean("BlindNativeMeleeDamageable",false);
                                                decoyProof.addProperty("oneActualMeleeHit",true);decoyProof.addProperty("firstHitShutter",true);decoyProof.addProperty("knockbackBaseRestored",true);
                                                Files.writeString(echoProofDirectory.resolve("melee-reach.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==51||decoyRun==52) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindMeleeRange.performed(decoyDirectory,decoyEntries)&&player.getHealth()==20&&decoyActor.getHealth()==40
                                                    &&score.apply("#decoy_cover")==160&&player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue()==0,
                                                    "Genuine fresh echo/lock and clear LOS outside ordinary strike range cause zero hits and zero damage; later shutter expires contact");
                                                var actual=Files.readAllLines(decoyDirectory.resolve("trace.jsonl")).stream().map(x->com.google.gson.JsonParser.parseString(x).getAsJsonObject()).toList();
                                                String originalTrace=Files.readString(decoyDirectory.resolve("trace.jsonl"));
                                                for(String changedField:java.util.List.of("selectedLock","lineOfSight","distance")) {
                                                    var mutated=new java.util.ArrayList<String>();
                                                    for(var originalFrame:actual) {
                                                        var frame=originalFrame.deepCopy();
                                                        if(changedField.equals("selectedLock"))frame.addProperty("selectedLock",false);
                                                        if(changedField.equals("lineOfSight"))frame.getAsJsonObject("meleeRange").addProperty("lineOfSight",false);
                                                        if(changedField.equals("distance"))frame.getAsJsonObject("meleeRange").addProperty("distance",2.0);
                                                        mutated.add(frame.toString());
                                                    }
                                                    Files.writeString(decoyDirectory.resolve("trace.jsonl"),String.join("\n",mutated)+"\n");
                                                    helper.assertTrue(!com.frozendawn.labbridge.LabBlindMeleeRange.performed(decoyDirectory,decoyEntries),"Range acceptance refuses vacuous or wrong-geometry proof: "+changedField);
                                                }
                                                Files.writeString(decoyDirectory.resolve("trace.jsonl"),originalTrace);
                                                Files.writeString(decoyDirectory.resolve("range-damage.jsonl"),"{}\n");
                                                helper.assertTrue(!com.frozendawn.labbridge.LabBlindMeleeRange.performed(decoyDirectory,decoyEntries),"Any actual range damage refuses completion");Files.writeString(decoyDirectory.resolve("range-damage.jsonl"),"");
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindMeleeRange.performed(decoyDirectory,decoyEntries),"Original real range evidence qualifies after negative controls");
                                                player.getPersistentData().putBoolean("BlindNativeMeleeDamageable",false);
                                                decoyProof.addProperty("genuineFreshEchoOutsideReach",true);decoyProof.addProperty("zeroActualMeleeHits",true);decoyProof.addProperty("damageablePlayer",true);
                                                Files.writeString(echoProofDirectory.resolve(decoyRun==51?"melee-range.json":decoyRun==52?"melee-range-repeat.json":"melee-obstruction.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==53) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindMeleeObstruction.performed(decoyDirectory,decoyEntries)&&player.getHealth()==20&&decoyActor.getHealth()==40,"Real fresh lower-body echo within reach but blocked eye LOS produces no hit/damage");
                                                var actual=Files.readAllLines(decoyDirectory.resolve("trace.jsonl")).stream().map(x->com.google.gson.JsonParser.parseString(x).getAsJsonObject()).toList();String originalTrace=Files.readString(decoyDirectory.resolve("trace.jsonl"));
                                                for(String field:java.util.List.of("selectedLock","lineOfSight","distance")) {
                                                    var mutated=new java.util.ArrayList<String>();
                                                    for(var originalFrame:actual) {var frame=originalFrame.deepCopy();if(field.equals("selectedLock"))frame.addProperty("selectedLock",false);if(field.equals("lineOfSight"))frame.getAsJsonObject("meleeObstruction").addProperty("lineOfSight",true);if(field.equals("distance"))frame.getAsJsonObject("meleeObstruction").addProperty("distance",3.0);mutated.add(frame.toString());}
                                                    Files.writeString(decoyDirectory.resolve("trace.jsonl"),String.join("\n",mutated)+"\n");helper.assertTrue(!com.frozendawn.labbridge.LabBlindMeleeObstruction.performed(decoyDirectory,decoyEntries),"Obstruction refuses vacuous/wrong geometry: "+field);
                                                }
                                                Files.writeString(decoyDirectory.resolve("trace.jsonl"),originalTrace);Files.writeString(decoyDirectory.resolve("obstruction-damage.jsonl"),"{}\n");
                                                helper.assertTrue(!com.frozendawn.labbridge.LabBlindMeleeObstruction.performed(decoyDirectory,decoyEntries),"Any actual obstruction damage refuses completion");Files.writeString(decoyDirectory.resolve("obstruction-damage.jsonl"),"");
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindMeleeObstruction.performed(decoyDirectory,decoyEntries),"Original genuine obstruction proof restored");player.getPersistentData().putBoolean("BlindNativeMeleeDamageable",false);
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindUnload.ready(source)==1,"Qualified held53 authorizes lifecycle preparation");
                                                for(String name:java.util.List.of("trace.jsonl","obstruction-damage.jsonl","obstruction-shutter.jsonl")){var f=decoyDirectory.resolve(name);String bytes=Files.readString(f);Files.writeString(f,bytes+" ");helper.assertTrue(com.frozendawn.labbridge.LabBlindUnload.ready(source)==0,"Corrupt53 refuses unload: "+name);Files.writeString(f,bytes);}
                                                decoyActor.setNoAi(false);helper.assertTrue(com.frozendawn.labbridge.LabBlindUnload.ready(source)==0,"Unheld53 refuses unload");decoyActor.setNoAi(true);
                                                decoyProof.addProperty("genuineFreshEchoInsideReach",true);decoyProof.addProperty("eyeLineOfSightBlocked",true);decoyProof.addProperty("damageablePlayer",true);Files.writeString(echoProofDirectory.resolve("melee-obstruction.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==49) {
                                                helper.assertTrue(anonymousFrames>0&&com.frozendawn.labbridge.LabBlindGriefing.performed(decoyDirectory,decoyEntries)
                                                    &&!s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                                                    &&decoyActor.successfulBreakCount()==0&&decoyActor.getX()<13&&decoyActor.getHealth()==40&&player.getHealth()==20,
                                                    "Real received anonymous door is investigated, but ordinary stone/slab wall is never mined with griefing disabled");
                                                decoyProof.addProperty("zeroBreaks",true);decoyProof.addProperty("noPassage",true);decoyProof.addProperty("mobGriefing",false);decoyProof.addProperty("ordinaryMaterialEligible",true);
                                                Files.writeString(echoProofDirectory.resolve("griefing-disabled.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==47)helper.assertTrue(com.frozendawn.labbridge.LabBlindBreach.performed(decoyDirectory,decoyEntries)&&decoyActor.successfulBreakCount()>=2&&decoyActor.getX()>=14&&player.getHealth()==20&&decoyActor.getHealth()==40,"One real anonymous door event drives twelve-tick warned doubled-time physical wall breach and passage without melee");
                                            if(decoyRun<=38) {
                                            var projectileRows=Files.readAllLines(decoyDirectory.resolve("projectiles.jsonl"));var flight=com.google.gson.JsonParser.parseString(projectileRows.getFirst()).getAsJsonObject();
                                            helper.assertTrue(flight.has("velocity")&&flight.has("playerPitch")&&flight.get("selectedSlot").getAsInt()==0,"Record actual item-use direction and aim");
                                            }
                                            for(var bytes:decoyOriginalBytes.entrySet())helper.assertTrue(Files.readString(bytes.getKey()).equals(bytes.getValue()),"Each decoy view preserves prior gameplay/input/readiness bytes");
                                            if(decoyRun==38||decoyRun==42||decoyRun==43) {
                                                helper.assertTrue(decoySummary.get("freshContactDecoyExercised").getAsBoolean(),"A real received impact overlaps fresh confirmed echo contact");
                                                helper.assertTrue(decoySummary.get("freshContactPriorityHeld").getAsBoolean()&&decoySummary.get("retainedLockWithDecoyFrames").getAsInt()>0,"Fresh echo remains selected while the anonymous decoy coexists");
                                                helper.assertTrue(decoyActor.blindController().signal()==null&&decoyActor.blindController().tracks().isEmpty()&&decoyActor.blindController().diagnostics().startsWith("blind=WANDER"),"Covered player and anonymous impact memories expire without freshening hidden contact");
                                            }
                                            if(decoyRun>=39&&decoyRun<=40||decoyRun==42||decoyRun==43) {
                                                helper.assertTrue(score.apply("#decoy_balls")==0&&score.apply("#decoy_impacts")==0,"Door view admits no projectile");
                                                for(String kind:java.util.List.of("block_open","block_close"))helper.assertTrue(
                                                    java.util.stream.StreamSupport.stream(decoyEntries.spliterator(),false).anyMatch(e->e.getAsJsonObject().get("event").getAsString().equals("BLIND_AIR")
                                                        &&e.getAsJsonObject().get("detail").getAsString().contains(kind)&&e.getAsJsonObject().get("detail").getAsString().contains("outcome=RECEIVED")),"Real "+kind+" is heard");
                                                helper.assertTrue(decoyActor.blindController().tracks().isEmpty()&&decoyActor.blindController().signal()==null,"Door memories expire without refresh or identified target");
                                            }
                                            if(decoyRun==41) {
                                                var machineAir=java.util.stream.StreamSupport.stream(decoyEntries.spliterator(),false).map(x->x.getAsJsonObject()).filter(x->x.get("event").getAsString().equals("BLIND_AIR")).toList();
                                                var machineVibration=java.util.stream.StreamSupport.stream(decoyEntries.spliterator(),false).map(x->x.getAsJsonObject()).filter(x->x.get("event").getAsString().equals("BLIND_VIBRATION")).toList();
                                                helper.assertTrue(!machineAir.isEmpty()&&machineAir.stream().allMatch(x->x.get("detail").getAsString().contains("kind=MACHINE")&&x.get("detail").getAsString().contains("outcome=RECEIVED")),"Real heater ticking produces received machine air hearing");
                                                helper.assertTrue(machineVibration.size()==machineAir.size()&&machineVibration.stream().allMatch(x->!x.get("detail").getAsString().contains("UNKNOWN")&&!x.get("detail").getAsString().contains("outcome=DETECTED")),"Disconnected heater vibration rejects without unknown budgets");
                                                // A machine tick immediately before OFF240 may arrive on that same tick.
                                                // At frame480 it is unselected, exactly on the production age240 boundary;
                                                // pruning is strict >240. Retain this boundary row rather than padding the view.
                                                helper.assertTrue(decoyActor.getHealth()==40&&decoyActor.blindController().signal()==null
                                                    &&decoyActor.blindController().tracks().stream().allMatch(t->t.subject()==null
                                                        &&t.sense()==com.frozendawn.entity.blind.BlindSignalMemory.Sense.HEARING
                                                        &&s.level.getGameTime()-t.observed()==com.frozendawn.entity.blind.BlindArchitectPolicy.SEARCH_TICKS),
                                                    "Heater stays beyond damage range; machine memory is unselected and expired or on its exact final age boundary");
                                                var actualFrames=Files.readAllLines(decoyDirectory.resolve("trace.jsonl")).stream().map(x->com.google.gson.JsonParser.parseString(x).getAsJsonObject()).toList();
                                                helper.assertTrue(actualFrames.stream().filter(x->x.get("timer").getAsInt()<80).allMatch(x->x.getAsJsonArray("tracks").isEmpty()),"Off heater baseline admits no contact");
                                                helper.assertTrue(score.apply("#decoy_balls")==0&&score.apply("#decoy_impacts")==0,"No projectile or player task competes with machinery");
                                            }
                                            if(decoyRun==44) {
                                                helper.assertTrue(decoyActor.getHealth()==36&&com.frozendawn.labbridge.LabBlindBell.performed(decoyDirectory,decoyEntries),"Actual bell rings cause two bounded pain cycles with cooldown recovery");
                                                helper.assertTrue(decoyActor.blindController().tracks().isEmpty()&&decoyActor.getTarget()==null,"Bell grants no hidden player identity");
                                                decoyProof.addProperty("actualBellRings",32);decoyProof.addProperty("bellDamage",4);decoyProof.addProperty("cooldownHeld",true);
                                                Files.writeString(echoProofDirectory.resolve("bell-reaction.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==45) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindExplosion.performed(decoyDirectory,decoyEntries),"Real physical blast is not doubled; later acoustic-only blast causes ordinary pain");
                                                var frames=Files.readAllLines(decoyDirectory.resolve("trace.jsonl")).stream().map(x->com.google.gson.JsonParser.parseString(x).getAsJsonObject()).toList();
                                                long ears=frames.stream().filter(x->x.get("noiseStunPose").getAsBoolean()).count();
                                                helper.assertTrue(ears>=40&&ears<=50&&decoyActor.getTarget()==null&&decoyActor.blindController().tracks().isEmpty(),"Both noise stuns expose the ear pose without player identity");
                                                decoyProof.addProperty("realExplosions",2);decoyProof.addProperty("earPoseServerFlagFrames",ears);decoyProof.addProperty("physicalDamageNotDoubled",true);
                                                Files.writeString(echoProofDirectory.resolve("explosion-reaction.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun==46) {
                                                helper.assertTrue(com.frozendawn.labbridge.LabBlindHeat.performed(decoyDirectory,decoyEntries),"Actual heater burns, bounded retreat escapes heat, and recovery occurs after fuel off");
                                                var frames=Files.readAllLines(decoyDirectory.resolve("trace.jsonl")).stream().map(x->com.google.gson.JsonParser.parseString(x).getAsJsonObject()).toList();
                                                helper.assertTrue(frames.stream().noneMatch(x->x.get("noiseStunPose").getAsBoolean())&&decoyActor.getTarget()==null,"Heat fleeing has no noise ear pose or identified target");
                                                decoyProof.addProperty("actualHeaterHeat",true);decoyProof.addProperty("heatOnlyEarPoseFrames",0);decoyProof.addProperty("finalHealth",decoyActor.getHealth());
                                                Files.writeString(echoProofDirectory.resolve("heat-reaction.json"),decoyProof+"\n");
                                            }
                                            if(decoyRun<=52) {
                                                var inputPath=java.nio.file.Path.of(decoyCapture.get("directory").getAsString());
                                                var groundPath=java.nio.file.Path.of(decoyCapture.getAsJsonObject("groundCheck").get("directory").getAsString());
                                                for(var dir:java.util.List.of(decoyDirectory,inputPath,groundPath))try(var paths=Files.list(dir)){for(var file:paths.toList())if(Files.isRegularFile(file))decoyOriginalBytes.put(file,Files.readString(file));}
                                            } else {
                                                for(var bytes:decoyOriginalBytes.entrySet())helper.assertTrue(Files.readString(bytes.getKey()).equals(bytes.getValue()),"Repeat preserves all original near-glass gameplay/input/readiness bytes");
                                                decoyActor.discard();
                                            }
                                            }
                                        }
                                    }
                                    miningProof.addProperty("onlySupportBridgeChanged",true);miningProof.addProperty("productionSettingsUnchanged",true);
                                    Files.writeString(echoProofDirectory.resolve("mining-supports.json"),miningProof+"\n");


                                }
                            }
                            recordedActor.discard();
                        }
                    } finally { if (recordedRuntime != null) recordedRuntime.close(); Files.delete(recordedPolicy); }
                }
                for (var bytes : repeatFailedInputBytes.entrySet()) helper.assertTrue(Files.readString(repeatFailedInputDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every incomplete input predecessor byte");
                for (var bytes : repeatFailedReplayBytes.entrySet()) helper.assertTrue(Files.readString(repeatFailedReplayDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Corrected replay retains every wrong-pose predecessor byte");
                for (var bytes : repeatFailedReadinessBytes.entrySet()) helper.assertTrue(Files.readString(repeatFailedReadinessDirectory.resolve(bytes.getKey())).equals(bytes.getValue()),
                        "Repeat retains all three readiness predecessor files");

                player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                caveTerrain.forEach((pos, state) -> s.level.setBlock(pos, state, 3));
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, s.server);
                rangeActor.discard();
                for(String holder:java.util.List.of("#run","#case","#isolated"))board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),objective).set(18);
                caveActor.discard();drop.discard();
                // Recorder termination controls use independent actors, without claiming a human hunt.
                for(String ending:java.util.List.of("PLAYER_DIED","ACTOR_DIED","MISSING_ACTOR","LEFT_AREA","TIME_LIMIT")) {
                    player.setPos(23.5,-44,.5);player.setHealth(player.getMaxHealth());
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"),objective).set(1);
                    for(String holder:java.util.List.of("#timer","#frames","#last_frame","#archived","#inconclusive"))
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),objective).set(0);
                    var control=com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                    control.setPos(8.5,-44,.5);control.setBlindArchitect(true);control.setNoAi(true);control.setOnGround(true);control.addTag("blind_lab_actor");
                    s.level.addFreshEntity(control);s.entities.add(control);
                    helper.assertTrue(command.apply("start")==1,"Cave recorder control starts: "+ending);
                    if(ending.equals("PLAYER_DIED"))player.setHealth(0);
                    if(ending.equals("ACTOR_DIED"))control.setHealth(0);
                    if(ending.equals("MISSING_ACTOR"))control.discard();
                    if(ending.equals("LEFT_AREA"))player.setPos(65.5,-44,.5);
                    int frames=ending.equals("TIME_LIMIT")?3600:1;
                    for(int tick=1;tick<=frames;tick++) {
                        s.clock(s.level.getGameTime()+1);
                        helper.assertTrue(command.apply("cave_tick")==1,"Cave recorder preserves the ending: "+ending);
                    }
                    boolean invalid=ending.equals("MISSING_ACTOR")||ending.equals("LEFT_AREA");
                    helper.assertTrue(score.apply("#stage")==3 && score.apply("#frames")==frames && player.isCreative()
                            &&(control.isRemoved()||control.isNoAi()) && score.apply("#archived")== (invalid?0:1),"Death, missing actor, area exit and full time bound stop safely: "+ending);
                    helper.assertTrue(command.apply("start")!=1 && command.apply("cave_tick")!=1,"Ended cave cannot repeat");
                    control.discard();player.setHealth(player.getMaxHealth());
                }
                helper.assertTrue(Files.readString(caveDirectory.resolve("trace.jsonl")).equals(preservedCaveTrace)
                        &&Files.readString(previousChannelDirectory.resolve("trace.jsonl")).equals(sealedTrace),"All cave controls preserve earlier raw records");
                // Native ending controls below deliberately return to their independent run11 bookkeeping.
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#run"),objective).set(11);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#case"),objective).set(11);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#isolated"),objective).set(11);
                s.level.getEntitiesOfClass(com.frozendawn.entity.ArchitectEntity.class,
                        new net.minecraft.world.phys.AABB(0,-49,-12,31,-37,11)).forEach(net.minecraft.world.entity.Entity::discard);
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true,s.server);
                s.block(9,-1,0,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                s.block(22,-1,0,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());

                baseReceiver.discard();
                // Separate recorder controls use new native actors and directories.
                // They do not simulate a hunt or replay the production encounter above.
                for(String ending:java.util.List.of("PLAYER_DIED","ACTOR_DIED","MISSING_ACTOR","LEFT_AREA","TIME_LIMIT")) {
                    for(int z=-4;z<=2;z++) {
                        s.block(15,0,z,net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                        s.block(15,1,z,net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
                    }
                    player.setPos(22.5,-44,.5);player.setHealth(player.getMaxHealth());
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"),objective).set(1);
                    for(String holder:java.util.List.of("#timer","#frames","#last_frame","#archived","#inconclusive"))
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),objective).set(0);
                    for(String holder:java.util.List.of("#base_acquired","#base_broken","#base_entered","#base_close"))
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),objective).set(-1);
                    var endingActor=com.frozendawn.init.ModEntities.ARCHITECT.get().create(s.level);
                    endingActor.setPos(9.5,-44,.5);endingActor.setBlindArchitect(true);endingActor.setNoAi(true);
                    endingActor.setOnGround(true);endingActor.addTag("blind_lab_actor");
                    s.level.addFreshEntity(endingActor);s.entities.add(endingActor);
                    helper.assertTrue(s.level.getEntity(endingActor.getUUID())==endingActor,"Control actor is tagged before isolation admission");
                    helper.assertTrue(command.apply("start")==1,"Recorder control starts its fresh native actor: "+ending);
                    if(ending.equals("PLAYER_DIED"))player.setHealth(0);
                    if(ending.equals("ACTOR_DIED"))endingActor.setHealth(0);
                    if(ending.equals("MISSING_ACTOR"))endingActor.discard();
                    if(ending.equals("LEFT_AREA"))player.setPos(32.5,-44,.5);
                    int frames=ending.equals("TIME_LIMIT")?1800:1;
                    for(int tick=1;tick<=frames;tick++) {
                        s.clock(s.level.getGameTime()+1);
                        helper.assertTrue(command.apply("behavior_base_tick")==1,"Recorder control preserves each tick: "+ending);
                    }
                    var endingDirectory=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("blind-lab-results/run-11-"+endingActor.getUUID());
                    var endingSummary=com.google.gson.JsonParser.parseString(Files.readString(endingDirectory.resolve("summary.json"))).getAsJsonObject();
                    boolean invalid=ending.equals("MISSING_ACTOR")||ending.equals("LEFT_AREA");
                    helper.assertTrue(score.apply("#stage")==3&&score.apply("#frames")==frames&&player.isCreative()
                            &&(endingActor.isRemoved()||endingActor.isNoAi())&&score.apply("#archived")== (invalid?0:1)
                            &&endingSummary.get("endReason").getAsString().equals(invalid?"INCONCLUSIVE":ending),
                            "Death, departure and the time bound stop safely without claiming encounter success: "+ending);
                    helper.assertTrue(command.apply("behavior_base_tick")!=1&&command.apply("start")!=1,
                            "Stopped recorder controls cannot advance or restart: "+ending);
                    endingActor.discard();player.setHealth(player.getMaxHealth());
                }
                helper.assertTrue(Files.readString(baseDirectory.resolve("trace.jsonl")).equals(completedBaseTrace),
                        "Recorder termination controls preserve the actual base-hunt trace byte for byte");
                helper.assertTrue(Files.readString(directory.resolve("summary.json")).equals(summary),"Original grounded control evidence remains byte-identical");
            } finally {
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(previousGriefing,s.server);
                s.level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(0,-49,-24,64,-33,32),
                        e->e.getTags().contains("blind_lab_actor")||e.getTags().contains("blind_lab_label")).forEach(net.minecraft.world.entity.Entity::discard);
                if(board.getObjective("blind_vib")!=null)board.removeObjective(board.getObjective("blind_vib"));
                helper.assertTrue(command.apply("prepare")!=1 && command.apply("scene")!=1,
                        "Missing objective refuses even in the opted-in native world");
            }
            helper.succeed();
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static void verifyQuietLabSettings(GameTestHelper helper,java.nio.file.Path temporary) throws java.io.IOException {
        var resources=helper.getLevel().getServer().getResourceManager();
        var saved=temporary.resolve("frozendawn_apocalypse.dat");
        try(var stream=resources.getResourceOrThrow(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "blind_lab","qa/frozendawn_apocalypse.dat")).open()) { Files.copy(stream,saved); }
        var state=com.frozendawn.data.ApocalypseState.load(net.minecraft.nbt.NbtIo.readCompressed(saved,
                net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data"),helper.getLevel().registryAccess());
        helper.assertTrue(state.isDifficultyLocked()&&state.getPresetName().equals("default")&&state.getApocalypseTicks()==0,
                "Fresh lab state preselects default before login, suppressing the difficulty picker without copying history");

        var quiet=Files.createDirectories(temporary.resolve("quiet-pack"));
        try(var stream=resources.getResourceOrThrow(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "blind_lab","qa/quiet-pack.mcmeta")).open()) { Files.copy(stream,quiet.resolve("pack.mcmeta")); }
        var metadata=net.minecraft.nbt.NbtIo.readCompressed(temporary.resolve("valid/level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("Data");
        var generated=net.minecraft.world.level.WorldDataConfiguration.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,metadata).getOrThrow();
        var legacyConfig=new net.minecraft.world.level.WorldDataConfiguration(new net.minecraft.world.level.DataPackConfig(
                java.util.List.of("vanilla","mod/neoforge","mod/frozendawn","file/blind-architect-lab"),java.util.List.of()),net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        var legacy=probeLabPackOrder(helper,quiet,legacyConfig);
        var corrected=probeLabPackOrder(helper,quiet,generated);
        helper.assertTrue(legacy.advancements()>0,"Previous per-mod pack IDs reproduce live leakage when NeoForge appends mod_data above the filter");
        helper.assertTrue(corrected.advancements()==0,"Actual NeoForge-selected generated pack stack removes all advancement definitions");
        helper.assertTrue(corrected.recipes()==legacy.recipes()&&corrected.recipes()>100,"Advancement filter preserves all real recipes");
        var directory=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports","build/architect-reports"),"blind-lab");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("quiet-pack-order.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(java.util.Map.of("legacy",legacy,"generated",corrected))+"\n");
    }

    private record PackProbe(java.util.List<String> enabled,int advancements,int recipes) { }
    private static PackProbe probeLabPackOrder(GameTestHelper helper,java.nio.file.Path quiet,
                                               net.minecraft.world.level.WorldDataConfiguration configuration) {
        var location=new net.minecraft.server.packs.PackLocationInfo("file/blind-architect-lab",Component.literal("quiet lab"),
                net.minecraft.server.packs.repository.PackSource.WORLD,java.util.Optional.empty());
        var pack=java.util.Objects.requireNonNull(net.minecraft.server.packs.repository.Pack.readMetaAndCreate(location,
                new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(quiet),net.minecraft.server.packs.PackType.SERVER_DATA,
                new net.minecraft.server.packs.PackSelectionConfig(false,net.minecraft.server.packs.repository.Pack.Position.TOP,false)));
        // Clone the real available mod/vanilla packs into a separate repository.
        // Minecraft's production selection path inserts required NeoForge roots
        // and expands their children; a hand-ordered mock stack missed this bug.
        var available=java.util.List.copyOf(helper.getLevel().getServer().getPackRepository().getAvailablePacks());
        var repository=new net.minecraft.server.packs.repository.PackRepository(consumer->{available.forEach(consumer);consumer.accept(pack);});
        var selected=net.minecraft.server.MinecraftServer.configurePackRepository(repository,configuration,false,false);
        try(var manager=new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,repository.openAllSelected())) {
            return new PackProbe(java.util.List.copyOf(selected.dataPacks().getEnabled()),manager.listResources("advancement",id->id.getPath().endsWith(".json")).size(),
                    manager.listResources("recipe",id->id.getPath().endsWith(".json")).size());
        }
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void blindLabFunctionsParseAndRefuseOtherWorlds(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 393, s -> {
            var player = s.player("BlindLabOperator", 2, 4);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var board = s.server.getScoreboard();
            var objective = board.addObjective("blind_lab", ObjectiveCriteria.DUMMY, Component.literal("Blind"), ObjectiveCriteria.RenderType.INTEGER, false, null);
            var stage = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective); stage.set(0);
            var source = s.server.createCommandSourceStack().withEntity(player).withPosition(player.position()).withPermission(2);
            var before = s.phase.save(new net.minecraft.nbt.CompoundTag(), s.server.registryAccess());
            var position = player.position();
            String previousOptIn = System.getProperty("frozendawn.labBridge");
            // Enable the lab capability so this case tests the world boundary,
            // rather than passing solely because the whole bridge is disabled.
            System.setProperty("frozendawn.labBridge", "true");
            try {
                var namespaces = java.util.List.of("blind_lab", "blind_appearance", "blind_audio", "blind_echo_open", "blind_echo_sealed", "blind_echo_wool", "blind_vibration_connected", "blind_vibration_disconnected", "blind_vibration_lab", "blind_natural", "blind_erased");
                var resources = s.server.getResourceManager().listResources("function", id -> namespaces.contains(id.getNamespace()) && id.getPath().endsWith(".mcfunction"));
                helper.assertTrue(resources.size() == 176, "All original views, workbench and natural cave functions are available");
                resources.forEach((file, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        net.minecraft.commands.functions.CommandFunction.fromLines(file, s.server.getCommands().getDispatcher(), source, reader.lines().toList());
                    } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                });
                var result = new java.util.concurrent.atomic.AtomicInteger(-1);
                for (var namespace : namespaces) {
                    if (namespace.equals("blind_natural") || namespace.equals("blind_erased")) continue; // Its own objective is established below.
                    result.set(-1);
                    s.server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(value)), "function " + namespace + ":prepare");
                    helper.assertTrue(result.get() == 0 && stage.get() == 0 && player.position().equals(position), "Wrong-world setup refuses before terrain/actor/player staging: " + namespace);
                }
                var natural = board.addObjective("blind_natural", ObjectiveCriteria.DUMMY, Component.literal("Natural"), ObjectiveCriteria.RenderType.INTEGER, false, null);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), natural).set(0);
                try {
                    result.set(-1);
                    s.server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(value)), "function blind_natural:prepare");
                    helper.assertTrue(result.get() == 0 && player.position().equals(position), "Even a valid natural stage cannot bypass the exact world boundary");
                    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), natural).set(1);
                    boolean mobSpawning = s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
                    result.set(-1);
                    s.server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(value)), "function blind_natural:start");
                    helper.assertTrue(result.get() == 0 && s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING) == mobSpawning,
                            "Wrong-world natural start refuses before changing spawn rules or player mode");
                    boolean frozen = s.server.tickRateManager().isFrozen();
                    for (var entry : java.util.Map.of("fast_wait", 3, "fast_play", 2, "fast_repeat", 3, "arrival_play", 3).entrySet()) {
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), natural).set(entry.getValue());
                        result.set(-1);
                        s.server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(value)), "function blind_natural:" + entry.getKey());
                        helper.assertTrue(result.get() == 0 && s.server.tickRateManager().isFrozen() == frozen && player.position().equals(position),
                                "Fast-wait functions cannot accelerate or release another world: " + entry.getKey());
                    }
                } finally { board.removeObjective(natural); }
                var erased = board.addObjective("blind_erased", ObjectiveCriteria.DUMMY, Component.literal("Erased"), ObjectiveCriteria.RenderType.INTEGER, false, null);
                try {
                    for (var entry : java.util.Map.of("prepare", 0, "start", 1).entrySet()) {
                        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), erased).set(entry.getValue());
                        result.set(-1);
                        s.server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(value)), "function blind_erased:" + entry.getKey());
                        helper.assertTrue(result.get() == 0 && !com.frozendawn.homo.PostMaeveWorldState.isErased(s.server)
                                && player.position().equals(position), "ERASED fixture refuses wrong world before any lifecycle or staging mutation");
                    }
                } finally { board.removeObjective(erased); }
                helper.assertTrue(before.equals(s.phase.save(new net.minecraft.nbt.CompoundTag(), s.server.registryAccess())), "Wrong-world setup never changes apocalypse history");
            } finally {
                if (previousOptIn == null) System.clearProperty("frozendawn.labBridge");
                else System.setProperty("frozendawn.labBridge", previousOptIn);
                board.removeObjective(objective);
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void naturalFastWaitStopsOnActualJoinBeforeBlindAiAndReleasesNormally(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 401, s -> {
            var manager = s.server.tickRateManager(); boolean frozen = manager.isFrozen();
            var player = s.player("fast_wait_join", 2, 4); player.setOnGround(true);
            var records = new java.util.ArrayList<JsonObject>();
            var wait = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 24000, records::add);
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> listener = e -> {
                if (e.getEntity() instanceof com.frozendawn.entity.ArchitectEntity actor) wait.joining(actor);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
                    net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, listener);
            com.frozendawn.entity.ArchitectEntity actor = null;
            try {
                manager.setFrozen(false); manager.tick();
                for (int i = 1; i <= 40; i++) { s.clock(1000 + i); wait.tick(player, true); }
                helper.assertTrue(manager.isSprinting() && wait.phase().equals("WAITING"), "Forty ordinary grounded samples precede real sprint acceptance");
                actor = new com.frozendawn.entity.ArchitectEntity(com.frozendawn.init.ModEntities.ARCHITECT.get(), s.level);
                actor.setBlindArchitect(true); actor.setPos(player.getX() + 28, player.getY(), player.getZ());
                var position = actor.position(); float health = actor.getHealth();
                helper.assertTrue(s.level.addFreshEntity(actor), "Real native insertion delivers the arrival event");
                wait.confirmInsertion(); manager.tick();
                helper.assertTrue(wait.arrivalConfirmed() && !manager.isSprinting() && manager.isFrozen()
                                && manager.isEntityFrozen(actor) && actor.tickCount == 0,
                        "Actual join ends sprint and freezes the engine before any Blind AI tick");
                helper.assertTrue(actor.position().equals(position) && actor.getHealth() == health && !actor.isNoAi(),
                        "Waiting guard leaves actor position, health and ordinary AI flag unchanged");
                var foreign = s.player("fast_wait_foreign", 3, 4);
                helper.assertTrue(!wait.play(foreign) && manager.isFrozen(), "A foreign participant cannot release the encounter");
                helper.assertTrue(wait.play(player), "Only the original participant releases confirmed insertion"); manager.tick();
                helper.assertTrue(!manager.isSprinting() && !manager.isFrozen() && !manager.isEntityFrozen(actor)
                                && manager.tickrate() == 20.0f && wait.playing(), "The encounter resumes ordinary engine ticking");
                helper.assertTrue(records.stream().anyMatch(row -> row.get("event").getAsString().equals("BLIND_ARRIVAL")),
                        "Accepted actual entity, actor NBT and accelerated clock are archived separately");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
                if (actor != null) actor.discard();
                if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
                manager.setFrozen(frozen); manager.tick();
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void naturalFastWaitSelectsSameTickBlindAfterRegularWithoutAdvancingAi(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 404, s -> {
            var manager = s.server.tickRateManager(); boolean frozen = manager.isFrozen();
            var player = s.player("same_tick_wait", 2, 4); player.setOnGround(true);
            var records = new java.util.ArrayList<JsonObject>();
            var wait = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 24000, records::add);
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> listener = e -> {
                if (e.getEntity() instanceof com.frozendawn.entity.ArchitectEntity actor) wait.joining(actor);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
                    net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, listener);
            var regular = new com.frozendawn.entity.ArchitectEntity(com.frozendawn.init.ModEntities.ARCHITECT.get(), s.level);
            var blind = new com.frozendawn.entity.ArchitectEntity(com.frozendawn.init.ModEntities.ARCHITECT.get(), s.level);
            regular.setPos(player.getX() + 30, player.getY(), player.getZ());
            blind.setBlindArchitect(true); blind.setPos(player.getX() + 28, player.getY(), player.getZ());
            try {
                manager.setFrozen(false); manager.tick();
                for (int i = 1; i <= 40; i++) { s.clock(3000 + i); wait.tick(player, true); }
                helper.assertTrue(s.level.addFreshEntity(regular) && wait.reason().equals("OTHER_ARCHITECT_ARRIVAL"),
                        "First real regular arrival immediately stops acceleration");
                helper.assertTrue(s.level.addFreshEntity(blind), "Same-tick Blind insertion succeeds");
                wait.confirmInsertion(); manager.tick();
                helper.assertTrue(wait.arrivalConfirmed() && manager.isFrozen() && !manager.isSprinting()
                                && regular.tickCount == 0 && blind.tickCount == 0 && !regular.isNoAi() && !blind.isNoAi(),
                        "Same-tick Blind takes the candidate while both real actors remain untouched before AI");
                helper.assertTrue(records.getLast().get("candidate").getAsString().equals(blind.getUUID().toString()), "Confirmed candidate is the Blind actor");
                var failure = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 100, row -> true);
                failure.joining(regular); failure.hold("PLAYER_OR_CONTEXT_CHANGED"); failure.joining(blind);
                helper.assertTrue(failure.reason().equals("PLAYER_OR_CONTEXT_CHANGED") && !failure.play(player),
                        "An arrival never overrides a player/context failure");
                var later = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 100, row -> true);
                later.joining(regular); s.clock(3041); later.joining(blind);
                helper.assertTrue(later.reason().equals("OTHER_ARCHITECT_ARRIVAL") && !later.play(player), "A later arrival cannot revive an old hold");
                var firstBlind = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 100, row -> true);
                firstBlind.joining(blind); firstBlind.confirmInsertion(); firstBlind.joining(regular);
                helper.assertTrue(firstBlind.arrivalConfirmed(), "A later regular join cannot displace an already confirmed Blind");
            } finally {
                regular.discard(); blind.discard(); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
                if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
                manager.setFrozen(frozen); manager.tick();
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 200)
    public static void naturalArrivalStartupHoldPreservesActualNbtAndRejectsChangedProof(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().tickRateManager(); boolean frozen = manager.isFrozen();
        java.nio.file.Path temporary = null;
        try {
            temporary = Files.createTempDirectory("blind-natural-arrival-proof-");
            for (var prefix : java.util.List.of("qa/stopped-wait", "qa/same-tick-arrivals")) {
                var resources = helper.getLevel().getServer().getResourceManager().listResources(prefix, id -> id.getNamespace().equals("blind_natural"));
                helper.assertTrue(resources.size() == (prefix.endsWith("stopped-wait") ? 8 : 5), "All actual predecessor files are present");
                for (var entry : resources.entrySet()) {
                    var path = temporary.resolve(entry.getKey().getPath().substring(prefix.length() + 1));
                    Files.createDirectories(path.getParent()); try (var stream = entry.getValue().open()) { Files.copy(stream, path); }
                }
            }
            helper.assertTrue(com.frozendawn.labbridge.LabNaturalArrivalHold.proof(temporary), "Actual simultaneous NATURAL arrivals bind to the earlier interrupted wait");
            Files.createDirectory(temporary.resolve("arrival-play"));
            Files.writeString(temporary.resolve("arrival-play/startup-request.txt"), com.frozendawn.labbridge.LabNaturalArrivalHold.REQUEST);
            manager.setFrozen(false); manager.tick();
            var hold = new com.frozendawn.labbridge.LabNaturalArrivalHold(helper.getLevel().getServer(), temporary); manager.tick();
            helper.assertTrue(hold.evidenceReady() && manager.isFrozen() && !manager.runsNormally(), "Startup freezes the scheduler before loading the actor NBT");
            var end = com.google.gson.JsonParser.parseString(Files.readString(temporary.resolve("fastwait-repeat1/end.jsonl"))).getAsJsonObject();
            for (var item : end.getAsJsonArray("journals")) {
                var expected = net.minecraft.nbt.TagParser.parseTag(item.getAsJsonObject().get("nbt").getAsString());
                var actor = new com.frozendawn.entity.ArchitectEntity(com.frozendawn.init.ModEntities.ARCHITECT.get(), helper.getLevel());
                actor.load(expected);
                helper.assertTrue(com.frozendawn.labbridge.LabNaturalArrivalHold.actorMatches(actor, expected)
                                && manager.isEntityFrozen(actor) && actor.tickCount == 0 && !actor.isNoAi(),
                        "Both actual recorded actors reload unchanged and are engine-held without disabling AI");
                actor.setHealth(39); helper.assertTrue(!com.frozendawn.labbridge.LabNaturalArrivalHold.actorMatches(actor, expected), "Changed health refuses release");
                actor.load(expected); actor.setPos(actor.getX() + 1, actor.getY(), actor.getZ());
                helper.assertTrue(!com.frozendawn.labbridge.LabNaturalArrivalHold.actorMatches(actor, expected), "Changed position refuses release");
                actor.load(expected); actor.setNoAi(true);
                helper.assertTrue(!com.frozendawn.labbridge.LabNaturalArrivalHold.actorMatches(actor, expected), "Changed AI flag refuses release");
                actor.load(expected); actor.tickCount = 1;
                helper.assertTrue(!com.frozendawn.labbridge.LabNaturalArrivalHold.actorMatches(actor, expected), "Any actor tick before owner release refuses the continuation");
                actor.discard();
            }
            java.util.List<java.nio.file.Path> paths;
            try (var stream = Files.walk(temporary)) { paths = stream.filter(Files::isRegularFile).filter(p -> !p.toString().contains("arrival-play")).toList(); }
            for (var path : paths) {
                var original = Files.readAllBytes(path); Files.writeString(path, "\n", java.nio.file.StandardOpenOption.APPEND);
                helper.assertTrue(!hold.evidenceReady(), "Every predecessor file is immutable: " + path.getFileName()); Files.write(path, original);
            }
            helper.assertTrue(hold.evidenceReady() && !hold.release(null) && manager.isFrozen(), "Restored proof cannot authorize a missing/foreign participant");
            var second = new com.frozendawn.labbridge.LabNaturalArrivalHold(helper.getLevel().getServer(), temporary);
            helper.assertTrue(!second.evidenceReady() && !second.release(null) && manager.isFrozen(), "A duplicate startup evidence write stays held and cannot silently retry");
            helper.succeed();
        } catch (Exception e) { throw new IllegalStateException(e); }
        finally {
            if (temporary != null) try (var paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (java.io.IOException e) { throw new IllegalStateException(e); } });
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            manager.setFrozen(frozen); manager.tick();
        }
    }
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 200)
    public static void naturalFastRepeatBindsActualStoppedMovementAndRejectsChangedHistory(GameTestHelper helper) {
        java.nio.file.Path temporary = null;
        try {
            temporary = Files.createTempDirectory("blind-fast-repeat-proof-");
            var resources = helper.getLevel().getServer().getResourceManager();
            var prefix = "qa/stopped-wait";
            var files = resources.listResources(prefix, id -> id.getNamespace().equals("blind_natural"));
            helper.assertTrue(files.size() == 8, "All eight stopped native files are present in the fixture");
            for (var entry : files.entrySet()) {
                var path = temporary.resolve(entry.getKey().getPath().substring(prefix.length() + 1));
                Files.createDirectories(path.getParent());
                try (var stream = entry.getValue().open()) { Files.copy(stream, path); }
            }
            var player = UUID.fromString("380df991-f603-344c-a090-369bad2a924a");
            helper.assertTrue(com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player),
                    "Actual immutable original and movement-held native evidence authorizes its separate continuation");
            helper.assertTrue(!com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, UUID.randomUUID()),
                    "The stopped attempt cannot authorize a foreign participant");
            java.util.List<java.nio.file.Path> paths;
            try (var stream = Files.walk(temporary)) { paths = stream.filter(Files::isRegularFile).toList(); }
            for (var path : paths) {
                byte[] original = Files.readAllBytes(path);
                Files.writeString(path, "\n", java.nio.file.StandardOpenOption.APPEND);
                helper.assertTrue(!com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player),
                        "Changing any prior input, clock, ending or preparation byte refuses repeat: " + path.getFileName());
                Files.write(path, original);
                helper.assertTrue(com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player),
                        "Restoring exact original history restores qualification");
            }
            var arrivals = temporary.resolve("fastwait/arrivals.jsonl"); Files.writeString(arrivals, "{}\n");
            helper.assertTrue(!com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player),
                    "An extra unbound actor arrival cannot masquerade as the preserved empty wait");
            Files.delete(arrivals);
            var removed = paths.getFirst(); byte[] original = Files.readAllBytes(removed); Files.delete(removed);
            helper.assertTrue(!com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player), "Missing predecessor evidence refuses repeat");
            Files.write(removed, original);
            helper.assertTrue(com.frozendawn.labbridge.LabNaturalRepeatProof.verify(temporary, player), "Original evidence remains intact after all controls");
            helper.succeed();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        finally {
            if (temporary != null) try (var paths = Files.walk(temporary)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (java.io.IOException e) { throw new IllegalStateException(e); } });
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void naturalFastWaitLastScheduledTickCannotResumeAnEmptyEncounter(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 402, s -> {
            var manager = s.server.tickRateManager(); boolean frozen = manager.isFrozen();
            var player = s.player("fast_wait_deadline", 2, 4); player.setOnGround(true);
            try {
                manager.setFrozen(false); manager.tick();
                var wait = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 1, row -> true);
                for (int i = 1; i <= 40; i++) { s.clock(2000 + i); wait.tick(player, true); }
                helper.assertTrue(manager.checkShouldSprintThisTick() && manager.isSprinting(), "Last scheduled tick has remaining zero while scheduled sprint is still active");
                s.clock(2041); wait.tick(player, true); manager.tick();
                helper.assertTrue(wait.reason().equals("NO_BLIND_ARRIVAL") && manager.isFrozen() && !manager.isSprinting(),
                        "Exhaustion explicitly finishes the scheduled sprint before holding");
                helper.assertTrue(!wait.play(player), "An empty wait cannot be reported or released as a BA encounter");
                manager.tick(); helper.assertTrue(manager.isFrozen(), "A later scheduler tick cannot restore the old unpaused state");
            } finally {
                if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
                manager.setFrozen(frozen); manager.tick();
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void naturalFastWaitRefusesUninsertedArrivalsAndStopsOnPlayerDamage(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 403, s -> {
            var manager = s.server.tickRateManager(); boolean frozen = manager.isFrozen();
            var player = s.player("fast_wait_damage", 2, 4); player.setOnGround(true);
            try {
                manager.setFrozen(false); manager.tick();
                var wait = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 24000, row -> true);
                for (int i = 1; i <= 40; i++) { s.clock(3000 + i); wait.tick(player, true); }
                player.setHealth(player.getHealth() - 1); s.clock(3041); wait.tick(player, true);
                helper.assertTrue(wait.held() && wait.reason().equals("PLAYER_OR_CONTEXT_CHANGED") && player.getHealth() == 19
                                && manager.isFrozen() && !manager.isSprinting(), "Damage ends acceleration immediately and is preserved without repair");
                manager.setFrozen(false); manager.tick();
                var refused = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 24000, row -> true);
                var uninserted = new com.frozendawn.entity.ArchitectEntity(com.frozendawn.init.ModEntities.ARCHITECT.get(), s.level);
                uninserted.setBlindArchitect(true); refused.joining(uninserted); refused.confirmInsertion();
                helper.assertTrue(refused.reason().equals("ARRIVAL_NOT_LOADED") && !refused.arrivalConfirmed() && !refused.play(player),
                        "A join notification without real insertion never qualifies a natural encounter");
                manager.setFrozen(false); manager.tick();
                var canWrite = new java.util.concurrent.atomic.AtomicBoolean(true);
                var failedRelease = new com.frozendawn.labbridge.LabNaturalWait(s.level, player, 24000, row -> canWrite.get());
                var inserted = s.architect(6, 4); inserted.setBlindArchitect(true);
                failedRelease.joining(inserted); failedRelease.confirmInsertion();
                helper.assertTrue(failedRelease.arrivalConfirmed(), "Control has a real loaded arrival before disk-failure simulation");
                canWrite.set(false);
                helper.assertTrue(!failedRelease.play(player) && manager.isFrozen() && failedRelease.reason().equals("EVIDENCE_FAILED"),
                        "A failed release evidence write never unfreezes an unseen encounter");
            } finally {
                if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
                manager.setFrozen(frozen); manager.tick();
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void blindLabSensingDividerPreservesEchoComparison(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 394, s -> {
            for (int x = 0; x <= 30; x++) for (int z = 0; z <= 10; z++) for (int y = -1; y <= 4; y++) {
                boolean shell = x == 0 || x == 30 || z == 0 || z == 10 || y == -1 || y == 4;
                s.block(x, y, z, (shell ? net.minecraft.world.level.block.Blocks.BEDROCK : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
            }
            for (int x = 1; x <= 29; x++) for (int z = 1; z <= 9; z++) s.block(x, -1, z, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for (int z = 1; z <= 9; z++) for (int y = 0; y <= 1; y++) s.block(16, y, z, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
            var actor = s.architect(9, 5); var player = s.player("BlindEchoFixture", 23, 5);
            var open = com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level, actor.getEyePosition(), player.getEyePosition(), 24, 512);
            helper.assertTrue(open.reached(), "Protected divider must retain a bounded connected-air echo: " + open);
            for (int x = 1; x <= 29; x++) for (int z = 1; z <= 9; z++) s.block(x, -1, z, net.minecraft.world.level.block.Blocks.WHITE_WOOL.defaultBlockState());
            var wool = com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level, actor.getEyePosition(), player.getEyePosition(), 24, 512);
            helper.assertTrue(wool.reached() && wool.cost() == open.cost(), "Changing only the floor to wool leaves the echo path cost unchanged");
            for (int z = 1; z <= 9; z++) for (int y = 2; y <= 3; y++) s.block(16, y, z, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!com.frozendawn.entity.blind.BlindArchitectSensing.air(s.level, actor.getEyePosition(), player.getEyePosition(), 24, 512).reached(), "Sealing only the upper opening blocks the new echo");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void labBridgeNativeCommandsHonorWorldAndStage(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 390, 3, s -> {
            var board = s.server.getScoreboard();
            var objective = board.addObjective("labbridge", ObjectiveCriteria.DUMMY, Component.literal("Lab"), ObjectiveCriteria.RenderType.INTEGER, false, null);
            var stage = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"), objective); stage.set(2);
            var runs = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#runs"), objective);
            var player = s.player("LabOperator", 0, 0);
            boolean frozen = s.server.tickRateManager().isFrozen();
            MinecraftLabRuntime runtime = null;
            java.nio.file.Path policy = null;
            try {
                policy = Files.createTempFile("lab-bridge-native-", ".json");
                JsonObject root = new JsonObject(); root.addProperty("schema", 1);
                JsonObject rule = new JsonObject(); rule.addProperty("world", s.server.getWorldData().getLevelName());
                rule.add("scores", com.google.gson.JsonParser.parseString("[{\"objective\":\"labbridge\",\"holder\":\"#stage\",\"equals\":2}]").getAsJsonArray());
                JsonObject functions = new JsonObject(); functions.add("lab_bridge:probe", rule); functions.add("lab_bridge:refuse", rule); root.add("functions", functions);
                Files.writeString(policy, root.toString()); runtime = new MinecraftLabRuntime(s.server, policy, () -> java.util.List.of(player));
                player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                var inputPlayer = run(runtime, "snapshot", new JsonObject()).getAsJsonArray("players").get(0).getAsJsonObject();
                helper.assertTrue(inputPlayer.get("uuid").getAsString().equals(player.getUUID().toString())
                        && inputPlayer.get("pose").getAsString().equals("CROUCHING") && inputPlayer.get("sneaking").getAsBoolean()
                        && inputPlayer.get("x").getAsDouble() == player.getX(), "Snapshot reports actual server player identity, pose and position");
                player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                try { run(runtime, "input_start", new JsonObject()); throw new AssertionError("Input capture allowed an unrelated world"); }
                catch (java.util.concurrent.CompletionException expected) { }
                try { run(runtime, "input_dump", new JsonObject()); throw new AssertionError("Input capture fabricated an unarmed result"); }
                catch (java.util.concurrent.CompletionException expected) { }
                var dumpArgs = new JsonObject(); dumpArgs.addProperty("player", player.getUUID().toString());
                var dump = run(runtime, "maeve_dump", dumpArgs);
                helper.assertTrue(dump.get("commandSuccess").getAsBoolean() && !dump.getAsJsonArray("output").isEmpty(), "Native Maeve dump must return captured diagnostics");
                var args = new JsonObject(); args.addProperty("name", "lab_bridge:probe"); args.addProperty("player", player.getUUID().toString());
                var result = run(runtime, "function", args);
                helper.assertTrue(result.get("commandSuccess").getAsBoolean() && runs.get() == 1, "Approved native function executes exactly once");
                stage.set(3);
                try { run(runtime, "function", args); throw new AssertionError("Wrong stage ran"); }
                catch (java.util.concurrent.CompletionException expected) { helper.assertTrue(runs.get() == 1, "Stage guard prevented mutation"); }
                stage.set(2); args.addProperty("name", "lab_bridge:refuse");
                var refused = run(runtime, "function", args);
                helper.assertTrue(refused.get("commandResult").getAsInt() == 0 && refused.get("commandOutcome").getAsString().equals("ZERO_RESULT"), "Native return zero stays visible");
                var request = request(runtime, "unfreeze", new JsonObject());
                var stale = new LabRequest(request.id(), "previous-session", request.world(), request.issuedAt(), request.expiresAt(), request.operation(), request.args());
                run(runtime, "freeze", new JsonObject());
                helper.assertTrue(run(runtime, "snapshot", new JsonObject()).get("frozen").getAsBoolean(), "Diagnostics work in a frozen simulation");
                try { runtime.execute(stale).join(); throw new AssertionError("Stale request ran"); }
                catch (java.util.concurrent.CompletionException expected) { helper.assertTrue(s.server.tickRateManager().isFrozen(), "Old session cannot unfreeze the world"); }
                runtime.close();
                try { run(runtime, "snapshot", new JsonObject()); throw new AssertionError("Closed runtime ran"); }
                catch (java.util.concurrent.CompletionException expected) { }
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            finally {
                if (runtime != null) runtime.close();
                s.server.tickRateManager().setFrozen(frozen);
                board.removeObjective(objective);
                if (policy != null) try { Files.delete(policy); } catch (java.io.IOException ignored) { }
            }
        });
    }
    private static LabRequest request(MinecraftLabRuntime runtime, String operation, JsonObject args) {
        long now = System.currentTimeMillis();
        return new LabRequest(UUID.randomUUID().toString(), runtime.session(), runtime.world(), now, now + 30000, operation, args);
    }
    private static JsonObject run(MinecraftLabRuntime runtime, String operation, JsonObject args) {
        return runtime.execute(request(runtime, operation, args)).join();
    }
}
