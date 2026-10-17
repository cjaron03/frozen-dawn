package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.blind.BlindArchitectSensing;
import com.frozendawn.entity.blind.BlindSignalMemory.Sense;
import com.frozendawn.event.BlindArchitectEvents;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.world.BlindArchitectSpawner;
import com.frozendawn.world.BlindSpawnMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlindArchitectGameTest {
    private static ArchitectEntity blind(MaeveObservationGameTest.Scene s, int x, int z) {
        var actor = s.architect(x,z); actor.setBlindArchitect(true); actor.setOnGround(true); actor.tickCount = 80; return actor;
    }
    private static void tick(MaeveObservationGameTest.Scene s, ArchitectEntity actor, int count) {
        for (int i = 0; i < count; i++) { s.clock(s.level.getGameTime() + 1); actor.aiStep(); }
    }
    private static void room(MaeveObservationGameTest.Scene s, int length, int width) {
        for (int x = 0; x <= length; x++) for (int z = 0; z <= width; z++) for (int y = -1; y <= 3; y++)
            s.block(x,y,z, x == 0 || x == length || z == 0 || z == width || y == -1 || y == 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindActivityEventsRespectAirWallAndAnonymity(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 439, s -> {
            room(s, 10, 8); var actor = blind(s, 2, 4); var player = s.player("channel_activity", 6, 4);
            player.setOnGround(true);
            var evidence = new com.google.gson.JsonArray();
            var events = java.util.List.of(GameEvent.EAT, GameEvent.DRINK, GameEvent.STEP, GameEvent.HIT_GROUND,
                    GameEvent.BLOCK_OPEN, GameEvent.BLOCK_CLOSE, GameEvent.BLOCK_PLACE, GameEvent.BLOCK_DESTROY,
                    GameEvent.BLOCK_ACTIVATE, GameEvent.BLOCK_DEACTIVATE, GameEvent.NOTE_BLOCK_PLAY, GameEvent.PROJECTILE_LAND);
            for (var event : events) for (boolean sealed : java.util.List.of(false, true)) {
                actor.blindController().clearLocal(); s.clock(s.level.getGameTime() + 1);
                for (int z = 1; z < 8; z++) for (int y = 0; y < 3; y++)
                    s.block(4, y, z, (sealed ? Blocks.GLASS : Blocks.AIR).defaultBlockState());
                // Exercise the actual NeoForge vanilla-event subscriber. This adapter
                // matrix does not simulate item-use animations or a player input packet.
                s.level.gameEvent(event, player.getEyePosition(), GameEvent.Context.of(player));
                var hearing = actor.blindController().tracks().stream().filter(t -> t.sense() == Sense.HEARING).toList();
                h.assertTrue(sealed ? hearing.isEmpty() : hearing.size() == 1,
                        "Physical air wall controls delivered activity event: " + event + " sealed=" + sealed);
                h.assertTrue(hearing.stream().allMatch(t -> t.subject() == null && !t.lock(s.level.getGameTime()))
                        && actor.blindController().tracks().stream().noneMatch(t -> t.sense() == Sense.ECHO),
                        "An activity event never supplies player identity or an echo lock");
                var row = new com.google.gson.JsonObject(); row.addProperty("event", event.getRegisteredName());
                row.addProperty("sealed", sealed); row.addProperty("hearingTracks", hearing.size()); evidence.add(row);
            }
            channelEvidence("activity-air-wall", evidence);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindContactRequiresActualLocalUnblockedPlayerHit(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 440, s -> {
            room(s, 12, 8); var actor = blind(s, 2, 4); var player = s.player("channel_touch", 4, 4);
            // Inspect the damage callback before an AI tick can emit a click.
            actor.invulnerableTime = 0;
            actor.hurt(s.level.damageSources().playerAttack(player), 1);
            h.assertTrue(actor.blindController().tracks().stream().anyMatch(t -> t.sense() == Sense.ECHO
                    && player.getUUID().equals(t.subject()) && t.region().equals(player.blockPosition())
                    && t.observed() == s.level.getGameTime()), "An actual nearby hit provides local contact");
            h.assertTrue(actor.blindController().lastEcho() == null, "Touch contact is distinct from a measured echo return");
            actor.blindController().clearLocal(); player.setPos(s.position(8,4)); actor.invulnerableTime = 0;
            actor.hurt(s.level.damageSources().playerAttack(player), 1);
            h.assertTrue(actor.blindController().tracks().isEmpty(), "A remote attributed hit cannot provide contact coordinates");
            player.setPos(s.position(4,4));
            for (int y = 0; y < 3; y++) s.block(3,y,4,Blocks.STONE.defaultBlockState());
            actor.invulnerableTime = 0; actor.hurt(s.level.damageSources().playerAttack(player), 1);
            h.assertTrue(actor.blindController().tracks().isEmpty(), "Occluded nearby damage cannot invent local contact");
            var evidence = new com.google.gson.JsonObject(); evidence.addProperty("nearOpenContact", true);
            evidence.addProperty("farRejected", true); evidence.addProperty("occludedRejected", true);
            evidence.addProperty("echoQueries", 0); channelEvidence("physical-contact", evidence);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSneakMotionHookIsAudibleWithoutFootstepEvent(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 441, s -> {
            room(s,12,8); var actor=blind(s,2,4); var player=s.player("channel_sneak",6,4);
            player.setOnGround(true); player.setShiftKeyDown(true);
            com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
            player.setPos(player.position().add(0,0,.7)); s.clock(s.level.getGameTime()+1);
            com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
            h.assertTrue(actor.blindController().tracks().stream().anyMatch(t -> t.sense()==Sense.HEARING && t.subject()==null),
                    "Actual measured crouch displacement reaches the quiet air hook without a vanilla STEP event");
            h.assertTrue(actor.blindController().lastVibration()!=null,"Quiet displacement also samples its real support");
            actor.blindController().clearLocal(); player.setOnGround(false); player.setPos(player.position().add(0,0,.7));
            s.clock(s.level.getGameTime()+1);
            com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
            h.assertTrue(actor.blindController().tracks().isEmpty(),"The same airborne displacement cannot emit a grounded sneak step");
            player.setOnGround(true);player.setPos(player.position().add(3,0,0));s.clock(s.level.getGameTime()+1);
            com.frozendawn.event.BlindArchitectEvents.sneakSteps(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
            h.assertTrue(actor.blindController().tracks().isEmpty(),"Teleport-sized displacement is rejected");
            // Packet movement can arrive between entity ticks. Unchanged positions must retain
            // the distance already walked, while inactive/airborne/teleport samples reset it.
            var packetPlayer = s.player("channel_packet_sneak", 6, 4);
            packetPlayer.setOnGround(true); packetPlayer.setShiftKeyDown(true);
            java.util.function.DoubleConsumer sample = distance -> {
                packetPlayer.setPos(packetPlayer.position().add(0, 0, distance));
                s.clock(s.level.getGameTime() + 1);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(packetPlayer));
            };
            sample.accept(0);
            for (int i = 0; i < 30; i++) sample.accept(0);
            h.assertTrue(actor.blindController().tracks().isEmpty(), "Quiet idle ticks emit no step");
            for (int i = 0; i < 6; i++) { sample.accept(.1); sample.accept(0); }
            h.assertTrue(actor.blindController().tracks().isEmpty(), "Sub-threshold crouch distance stays quiet");
            sample.accept(.1);
            h.assertTrue(actor.blindController().tracks().stream().anyMatch(t -> t.sense() == Sense.HEARING && t.subject() == null),
                    "Interleaved stationary entity ticks retain measured crouch distance and emit a quiet step");
            h.assertTrue(actor.blindController().lastVibration() != null, "Packet-spaced movement also samples real support");
            for (String reset : java.util.List.of("inactive", "airborne", "teleport")) {
                packetPlayer.setShiftKeyDown(false);
                packetPlayer.setPos(s.position(6, 4)); sample.accept(0);
                actor.blindController().clearLocal(); packetPlayer.setShiftKeyDown(true);
                sample.accept(.35); sample.accept(0);
                if (reset.equals("inactive")) packetPlayer.setShiftKeyDown(false);
                if (reset.equals("airborne")) packetPlayer.setOnGround(false);
                sample.accept(reset.equals("teleport") ? -2 : 0);
                packetPlayer.setShiftKeyDown(true); packetPlayer.setOnGround(true);
                sample.accept(.35); sample.accept(0);
                h.assertTrue(actor.blindController().tracks().isEmpty(), reset + " clears partial quiet-step distance");
                sample.accept(.35);
                h.assertTrue(actor.blindController().tracks().stream().anyMatch(t -> t.sense() == Sense.HEARING),
                        "Fresh grounded distance resumes after " + reset);
            }
            var evidence=new com.google.gson.JsonObject();evidence.addProperty("packetSpacedGroundedSneakAudible",true);
            evidence.addProperty("quietIdleRejected",true);evidence.addProperty("inactiveDistanceReset",true);
            evidence.addProperty("airborneDistanceReset",true);evidence.addProperty("teleportDistanceReset",true);
            evidence.addProperty("groundedSneakAudible",true);
            evidence.addProperty("airborneRejected",true);evidence.addProperty("teleportRejected",true);channelEvidence("sneak-motion",evidence);
        });
    }

    private static void channelEvidence(String name, com.google.gson.JsonElement evidence) {
        try {
            var directory=java.nio.file.Path.of(System.getProperty("frozendawn.evidenceDir","build/architect-evidence"),"blind-channels");
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence)+"\n");
        } catch(java.io.IOException error) { throw new IllegalStateException(error); }
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSilentOpenEchoIgnoresSneakingAndWool(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 180, s -> {
            room(s,12,8); var a = blind(s,2,4); var p = s.player("blind_silent",6,4); p.setShiftKeyDown(true);
            s.block(6,-1,4,Blocks.WHITE_WOOL.defaultBlockState());
            a.aiStep(); tick(s,a,10);
            h.assertTrue(a.blindController().signal() != null && a.blindController().signal().subject().equals(p.getUUID()), "Silent sneaking player on wool still returns an echo");
            h.assertTrue(a.getTarget() == null && !a.isCustomNameVisible(), "Ordinary target and visible name stay suppressed");
            h.assertTrue(MaeveDirector.knownPlayers(s.server).contains(p.getUUID()), "Acquired acoustic evidence uploads instantly");
            h.assertTrue(s.beliefs(p).isEmpty(), "Acoustic contact does not create visual weapon/behavior beliefs");
            h.assertTrue(MaeveDirector.worldSnapshot(s.server,p.getUUID()).stream().anyMatch(v -> v.label().equals("ACOUSTIC_CONTACT") && v.provenance().getFirst().action().startsWith("ACOUSTIC_ECHO")), "Snapshot retains acoustic provenance");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindBentTunnelEchoAndSealedWall(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,181,s -> {
            for (int x=0;x<=12;x++) for (int z=0;z<=10;z++) for(int y=-1;y<=3;y++) s.block(x,y,z,Blocks.STONE.defaultBlockState());
            for(int x=2;x<=9;x++) for(int y=0;y<=2;y++) s.block(x,y,2,Blocks.AIR.defaultBlockState());
            for(int z=2;z<=7;z++) for(int y=0;y<=2;y++) s.block(9,y,z,Blocks.AIR.defaultBlockState());
            var a=blind(s,2,2);var p=s.player("blind_bend",9,7);
            h.assertTrue(!a.hasLineOfSight(p),"Fixture has no visual LOS");
            var result=BlindArchitectSensing.air(s.level,a.getEyePosition(),p.getEyePosition(),24,512);
            h.assertTrue(result.reached(),"Connected bent tunnel supplies a passive air path, not proof of a returned echo: "+result);
            for(int y=0;y<=2;y++) s.block(6,y,2,Blocks.STONE.defaultBlockState());
            h.assertTrue(!BlindArchitectSensing.air(s.level,a.getEyePosition(),p.getEyePosition(),24,512).reached(),"Sealed rock stops echolocation");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindPostPulseRelocationKeepsOldSnapshot(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,182,s -> {
            room(s,14,10);var a=blind(s,2,4);var p=s.player("blind_moved",6,4);
            a.aiStep();p.setPos(s.position(12,8));tick(s,a,9);
            var signal=a.blindController().signal();
            h.assertTrue(signal!=null && signal.region().distSqr(s.origin.offset(6,0,4))<=4,"Delayed return retains emitted position: "+signal);
            h.assertTrue(signal.region().distSqr(p.blockPosition())>16 && a.getTarget()==null,"No live target coordinate refresh after relocation");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindPendingEchoCancelledByClosedDoor(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,183,s -> {
            room(s,12,8); for(int z=1;z<8;z++) for(int y=0;y<=2;y++) s.block(5,y,z,Blocks.STONE.defaultBlockState());
            s.block(5,0,4,Blocks.AIR.defaultBlockState());s.block(5,1,4,Blocks.AIR.defaultBlockState());
            var a=blind(s,2,4);var p=s.player("blind_door",8,4);a.aiStep();
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,net.minecraft.core.Direction.EAST);
            s.block(5,0,4,door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));s.block(5,1,4,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            tick(s,a,10);
            h.assertTrue(a.blindController().tracks().stream().noneMatch(t->t.sense()==Sense.ECHO),"Closing sampled aperture cancels queued player returns");
            h.assertTrue(!MaeveDirector.knownPlayers(s.server).contains(p.getUUID()),"Cancelled return uploads no player knowledge");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindPartialAperturesRejectSolidDiagonals(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,184,s -> {
            room(s,8,6);Vec3 first=s.origin.offset(3,0,3).getCenter().add(-.25,-.25,0),second=first.add(.5,0,0);
            s.block(3,0,3,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
            h.assertTrue(BlindArchitectSensing.openEdge(s.level,first,second),"Air below an upper slab propagates");
            h.assertTrue(!BlindArchitectSensing.openEdge(s.level,first.add(0,.5,0),second.add(0,.5,0)),"Slab collision rejects the occupied aperture");
            var a=blind(s,2,2);var p=s.player("blind_diagonal",4,4);
            for(int x=1;x<8;x++)for(int z=1;z<6;z++)for(int y=0;y<3;y++)if((x==3||z==3))s.block(x,y,z,Blocks.STONE.defaultBlockState());
            h.assertTrue(!BlindArchitectSensing.air(s.level,a.getEyePosition(),p.getEyePosition(),24,512).reached(),"No corner cut through diagonal solids");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSnowballDecoyNeverTracksThrower(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,186,2,s -> {
            room(s,14,8);var a=blind(s,2,4);var p=s.player("blind_thrower",30,4);var ball=new Snowball(s.level,p);
            Vec3 impact=s.position(6,4).add(0,1,0); ball.setPos(impact);
            s.level.gameEvent(GameEvent.PROJECTILE_LAND,impact,GameEvent.Context.of(ball));
            h.assertTrue(a.blindController().tracks().stream().anyMatch(t->t.sense()==Sense.HEARING && t.subject()==null),"Impact creates an anonymous region");
            p.setPos(s.position(34,4));a.aiStep();
            h.assertTrue(a.getTarget()==null && !MaeveDirector.knownPlayers(s.server).contains(p.getUUID()),"Projectile owner metadata grants no player profile or pursuit");
            h.assertTrue(a.blindController().signal()!=null && a.blindController().signal().region().distSqr(BlockPos.containing(impact))<=4,"Investigates frozen impact region");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindEatingHeardWithoutIdentity(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,187,s -> {
            room(s,12,8);var a=blind(s,2,4);var p=s.player("blind_eats",6,4);
            s.level.gameEvent(GameEvent.EAT,p.getEyePosition(),GameEvent.Context.of(p));
            h.assertTrue(a.blindController().tracks().stream().anyMatch(t->t.sense()==Sense.HEARING && t.subject()==null),"Eating has an independent anonymous air channel");
            h.assertTrue(MaeveDirector.knownPlayers(s.server).isEmpty(),"Eating alone is not player identity evidence");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindBellCooldownPreventsDamageAndStunSpam(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,188,s -> {
            room(s,12,8);var a=blind(s,2,4);s.block(4,0,4,Blocks.BELL.defaultBlockState());
            Vec3 origin=s.origin.offset(4,0,4).getCenter();float hp=a.getHealth();
            s.level.gameEvent(GameEvent.BLOCK_CHANGE,origin,GameEvent.Context.of(Blocks.BELL.defaultBlockState()));tick(s,a,1);
            h.assertTrue(a.getHealth()==hp-2 && a.blindController().diagnostics().contains("STUN"),"Bell damages and stuns on propagated local noise");
            String saved=a.blindController().diagnostics();
            for(int i=0;i<30;i++) { s.level.gameEvent(GameEvent.BLOCK_CHANGE,origin,GameEvent.Context.of(Blocks.BELL.defaultBlockState()));tick(s,a,1); }
            h.assertTrue(a.getHealth()==hp-2 && a.blindController().diagnostics().contains("FLEE"),"Spam neither adds damage nor extends stun; retreat proceeds");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindExplosionPhysicalDamageIsNotDoubled(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,189,s -> {
            room(s,12,8);var a=blind(s,2,4);float hp=a.getHealth();a.invulnerableTime=0;
            var source=s.level.damageSources().explosion(null,null);a.hurt(source,6);float after=a.getHealth();
            s.level.gameEvent(GameEvent.EXPLODE,a.position().add(1,1,0),GameEvent.Context.of((net.minecraft.world.entity.Entity)null));tick(s,a,2);
            h.assertTrue(after<hp && a.getHealth()==after,"Physical blast response adds no duplicate acoustic damage");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindCoreNoiseIsAnonymousAndLocalHeatBurns(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,190,s -> {
            room(s,12,8);var a=blind(s,2,4);s.block(7,0,4,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState());
            BlockPos core=s.origin.offset(7,0,4);
            var be=(com.frozendawn.block.GeothermalCoreBlockEntity)s.level.getBlockEntity(core);
            com.frozendawn.world.GeothermalCoreRegistry.register(s.level,core);
            s.clock(s.level.getGameTime()+Math.floorMod(-s.level.getGameTime()-Math.floorMod(core.asLong(),100),100));be.serverTick();
            h.assertTrue(!a.blindController().tracks().isEmpty() && a.blindController().tracks().stream().allMatch(t->t.subject()==null),"Operating core emits an anonymous machine signal");
            a.setPos(s.position(5,4));float hp=a.getHealth();s.clock(s.level.getGameTime()+Math.floorMod(-s.level.getGameTime(),20));a.aiStep();
            h.assertTrue(a.getHealth()<hp && a.blindController().diagnostics().contains("FLEE"),"Actual heat exposure burns before backing off");
            com.frozendawn.world.GeothermalCoreRegistry.unregister(s.level,core);
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindDiggingIsSlowerAndStaleSignalsExpire(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,191,s -> {
            room(s,12,8);var a=blind(s,2,4);var normal=s.architect(2,6);BlockPos block=s.origin.offset(3,0,4);s.block(3,0,4,Blocks.STONE.defaultBlockState());
            var slow=new ArchitectBlockBreaker(a,p->{});var fast=new ArchitectBlockBreaker(normal,p->{});slow.setTarget(block);fast.setTarget(block);
            h.assertTrue(slow.debugRequiredTicks()==fast.debugRequiredTicks()*2,"Blind-only break timing scales after the normal cap");slow.clearTarget();fast.clearTarget();
            a.blindController().emit(s.position(8,4).add(0,1,0),null,24);a.aiStep();
            s.clock(s.level.getGameTime()+241);a.aiStep();
            h.assertTrue(a.blindController().signal()==null && !a.isMiningBlock(),"Expired estimate starts no new dig and releases local concern");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSealedContactExpiresWithoutArtificialMemoryClear(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,449,s -> {
            boolean grief=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);
            try{
                for(int x=1;x<=5;x++)for(int z=3;z<=5;z++)for(int y=-1;y<=3;y++){
                    var block=Blocks.WHITE_WOOL.defaultBlockState();
                    if(z==4&&(x==2||x==4)&&y>=0&&y<=2)block=Blocks.AIR.defaultBlockState();
                    if(z==4&&(x==2||x==4)&&y==-1)block=Blocks.STONE.defaultBlockState();
                    s.block(x,y,z,block);
                }
                s.block(3,0,4,Blocks.AIR.defaultBlockState());s.block(3,1,4,Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
                var a=blind(s,2,4);var p=s.player("blind_stale",4,4);p.setOnGround(true);
                for(int i=0;i<200&&(a.blindController().signal()==null||!a.blindController().signal().lock(s.level.getGameTime()));i++){s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(a);}
                var contact=a.blindController().signal();h.assertTrue(contact!=null&&contact.lock(s.level.getGameTime())&&p.getUUID().equals(contact.subject()),"Real body echo supplies identified lock before seal");
                long closed=s.level.getGameTime();s.block(3,0,4,Blocks.GLASS.defaultBlockState());s.block(3,1,4,Blocks.GLASS.defaultBlockState());
                var frames=new com.google.gson.JsonArray();boolean lost=false,searched=false;int idle=0;
                for(int i=1;i<=360;i++){
                    s.clock(closed+i);s.level.tickNonPassenger(a);
                    var signal=a.blindController().signal();lost|=signal==null||!signal.lock(s.level.getGameTime());searched|=a.blindController().diagnostics().startsWith("blind=SEARCH ");
                    boolean empty=signal==null&&a.blindController().tracks().isEmpty()&&a.blindController().diagnostics().startsWith("blind=WANDER ");idle=empty?idle+1:0;
                    h.assertTrue(!a.isNoAi()&&a.getTarget()==null&&!a.isMiningBlock()&&a.successfulBreakCount()==0&&a.getHealth()==40&&p.getHealth()==20,"Ordinary physics/AI decay has no held AI, target, damage or mining");
                    h.assertTrue(a.blindController().tracks().stream().noneMatch(t->t.observed()>closed),"Sealed barrier permits no fresh contact observations");
                    var frame=new com.google.gson.JsonObject();frame.addProperty("timer",i);frame.addProperty("gameTime",s.level.getGameTime());frame.addProperty("diagnostics",a.blindController().diagnostics());frame.addProperty("tracks",a.blindController().tracks().size());frame.addProperty("locked",signal!=null&&signal.lock(s.level.getGameTime()));frames.add(frame);
                }
                var proof=new com.google.gson.JsonObject();proof.addProperty("actualIdentifiedEchoBeforeSeal",true);proof.addProperty("noPostSealRefresh",true);proof.addProperty("lostLock",lost);proof.addProperty("searched",searched);proof.addProperty("idleTail",idle);proof.addProperty("ordinaryAiAndPhysicsThroughout",true);proof.addProperty("limits","Native physical fixture; no real client input or owner acceptance implied.");proof.add("frames",frames);
                try{java.nio.file.Files.writeString(s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-stale-control.json"),proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}
                h.assertTrue(lost&&searched&&idle>=40,"Sealed actual contact loses lock, searches, then becomes idle without any clearLocal call");
            }finally{s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(grief,s.server);}
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSpectatorHostDoesNotBorrowGuestAcousticKnowledge(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,450,s -> {
            boolean grief=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);
            try{
                for(int x=1;x<=5;x++)for(int z=3;z<=5;z++)for(int y=-1;y<=3;y++){
                    var block=Blocks.WHITE_WOOL.defaultBlockState();
                    if(z==4&&(x==2||x==4)&&y>=0&&y<=2)block=Blocks.AIR.defaultBlockState();
                    if(z==4&&(x==2||x==4)&&y==-1)block=Blocks.STONE.defaultBlockState();s.block(x,y,z,block);
                }
                s.block(3,0,4,Blocks.AIR.defaultBlockState());s.block(3,1,4,Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
                var a=blind(s,2,4);var guest=s.player("blind_lan_guest",4,4);guest.setOnGround(true);
                var host=s.player("blind_lan_host",4,4);host.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                h.assertTrue(!host.getUUID().equals(guest.getUUID()),"Two distinct native participant identities");long start=s.level.getGameTime();
                for(int i=0;i<200&&(a.blindController().signal()==null||!a.blindController().signal().lock(s.level.getGameTime()));i++){s.clock(s.level.getGameTime()+1);s.level.tickNonPassenger(a);}
                var contact=a.blindController().signal();h.assertTrue(contact!=null&&contact.sense()==Sense.ECHO&&contact.lock(s.level.getGameTime())&&guest.getUUID().equals(contact.subject()),"Ordinary physical echo identifies the live guest, excluding co-located spectator host");
                var knowledge=MaeveDirector.worldSnapshot(s.server,guest.getUUID());
                h.assertTrue(knowledge.stream().anyMatch(x->x.label().equals("ACOUSTIC_CONTACT")&&x.provenance().stream().anyMatch(e->e.observer().equals(a.getUUID())&&e.time()>start&&e.action().startsWith("ACOUSTIC_ECHO"))),"Guest Maeve world point records real actor acoustic provenance");
                h.assertTrue(MaeveDirector.worldSnapshot(s.server,host.getUUID()).stream().noneMatch(x->x.label().equals("ACOUSTIC_CONTACT")),"Spectator host receives no borrowed guest contact");
                long closed=s.level.getGameTime();s.block(3,0,4,Blocks.GLASS.defaultBlockState());s.block(3,1,4,Blocks.GLASS.defaultBlockState());int idle=0;
                for(int i=1;i<=360;i++){s.clock(closed+i);s.level.tickNonPassenger(a);
                    h.assertTrue(a.blindController().tracks().stream().noneMatch(t->t.observed()>closed||t.subject()!=null&&!t.subject().equals(guest.getUUID())),"Seal permits neither fresh guest refresh nor host substitution");
                    h.assertTrue(!a.isNoAi()&&a.getTarget()==null&&!a.isMiningBlock()&&a.successfulBreakCount()==0&&a.getHealth()==40&&guest.getHealth()==20,"Ordinary physics/AI has no damage, mining or held-AI shortcut");
                    boolean empty=a.blindController().signal()==null&&a.blindController().tracks().isEmpty()&&a.blindController().diagnostics().startsWith("blind=WANDER ");idle=empty?idle+1:0;
                }
                h.assertTrue(idle>=40&&MaeveDirector.worldSnapshot(s.server,host.getUUID()).stream().noneMatch(x->x.label().equals("ACOUSTIC_CONTACT")),"Guest contact naturally expires; host remains unattributed");
                var proof=new com.google.gson.JsonObject();proof.addProperty("actor",a.getUUID().toString());proof.addProperty("guest",guest.getUUID().toString());proof.addProperty("host",host.getUUID().toString());proof.addProperty("identifiedGuestEcho",true);proof.addProperty("hostExcluded",true);proof.addProperty("guestKnowledgeAttributed",true);proof.addProperty("noHostAcousticKnowledge",true);proof.addProperty("noPostSealRefresh",true);proof.addProperty("idleTail",idle);proof.add("guestKnowledge",new com.google.gson.Gson().toJsonTree(knowledge));proof.addProperty("limits","Native physical two-player fixture; actual network clients, guest input and owner acceptance remain separate.");
                try{java.nio.file.Files.writeString(s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-lan-attribution-control.json"),proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}
            }finally{s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(grief,s.server);}
        });
    }
    private static MaeveObservationGameTest.TestPlayer attentionPlayer(MaeveObservationGameTest.Scene s,String name,int x,int z){
        var p=new MaeveObservationGameTest.TestPlayer(s.level,name){@Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}};try{var field=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(p,0);}catch(ReflectiveOperationException e){throw new IllegalStateException("Attention fixture must remove fake-player permanent spawn immunity",e);}p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(s.position(x,z));s.level.addNewPlayer(p);s.entities.add(p);return p;
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindGuestRemovalExpiresAndNewPlayerRequiresFreshReturn(GameTestHelper h){
        MaeveObservationGameTest.withScene(h,454,s->{
            boolean grief=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),regen=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION);
            s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,s.server);
            var proof=new com.google.gson.JsonObject();var trace=new com.google.gson.JsonArray();proof.addProperty("outcome","INCOMPLETE");proof.add("trace",trace);proof.addProperty("limits","Physical actual entity removal/new same-UUID player object and ordinary actor physics. Network disconnect, real input, persistence handshake and owner acceptance remain separate.");
            try{
                for(int x=3;x<=20;x++)for(int z=1;z<=7;z++)for(int y=-1;y<=4;y++)s.block(x,y,z,com.frozendawn.labbridge.LabBlindAttention.wanted(x-15,y,z-5,false,false));s.block(16,-2,5,Blocks.GLASS.defaultBlockState());
                var a=blind(s,15,5);var host=attentionPlayer(s,"reconnect_host",15,1);host.setPos(host.getX(),host.getY(),host.getZ()-.2);host.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var guest=attentionPlayer(s,"reconnect_guest",18,5);var id=guest.getUUID();net.minecraft.server.level.ServerPlayer returned=null;var origin=a.position();long start=s.level.getGameTime(),gone=-1,reopened=-1,sealed=-1;int absenceIdle=0,finalIdle=0;boolean first=false,second=false;
                for(int i=1;i<=1120;i++){
                    s.clock(start+i);
                    String phase=i<=40?"GROUND_FIRST":i<=200?"FIRST":i<=560?"OFFLINE":i<=600?"GROUND_RETURN":i<=760?"RETURN":"SEALED";
                    if(i==41||i==561||i==601||i==761){boolean open=i==41||i==601;for(int y=0;y<=1;y++)s.block(16,y,5,com.frozendawn.labbridge.LabBlindAttention.wanted(1,y,0,open,false));if(i==601)reopened=s.level.getGameTime();if(i==761)sealed=s.level.getGameTime();}
                    if(i==201){gone=s.level.getGameTime();s.level.removePlayerImmediately(guest,net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER);h.assertTrue(s.level.getEntity(id)==null&&!s.level.players().contains(guest),"Actual removed guest is absent while aperture remains open");}
                    if(i==561){h.assertTrue(absenceIdle>=40,"Absent guest naturally reaches idle before holding/rejoin");a.setNoAi(true);
                        returned=new net.neoforged.neoforge.common.util.FakePlayer(s.level,new com.mojang.authlib.GameProfile(id,"reconnect_guest")){@Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage){return false;}};
                        try{var field=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(returned,0);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                        returned.setPos(s.position(18,5));returned.setGameMode(net.minecraft.world.level.GameType.CREATIVE);s.level.addNewPlayer(returned);s.entities.add(returned);h.assertTrue(returned!=guest&&returned.getUUID().equals(id)&&s.level.getEntity(id)==returned,"New actual player object uses same returned identity");
                    }
                    if(i==601){returned.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);a.setNoAi(false);}
                    s.level.tickNonPassenger(a);long now=s.level.getGameTime();var tracks=a.blindController().tracks();var signal=a.blindController().signal();
                    h.assertTrue(a.position().distanceToSqr(origin)<.001&&a.getHealth()==40&&host.getHealth()==20&&guest.getHealth()==20&&(returned==null||returned.getHealth()==20)&&a.getTarget()==null&&!a.isMiningBlock()&&a.successfulBreakCount()==0,"Original safe actor and all actual participants stay healthy/stationary without repair");
                    h.assertTrue(a.isNoAi()==phase.equals("GROUND_RETURN"),"AI holds only at guarded return readiness after natural absence expiry");h.assertTrue(tracks.stream().allMatch(t->t.subject()==null||id.equals(t.subject())),"Covered Creative host never substitutes for guest");
                    if(phase.equals("FIRST"))first|=signal!=null&&id.equals(signal.subject())&&signal.lock(now);
                    if(phase.equals("OFFLINE")){long departure=gone;h.assertTrue(tracks.stream().noneMatch(t->t.observed()>departure),"No post-removal observation refresh through open aperture");absenceIdle=tracks.isEmpty()&&signal==null&&a.blindController().diagnostics().startsWith("blind=WANDER ")?absenceIdle+1:0;}
                    if(phase.equals("GROUND_RETURN"))h.assertTrue(tracks.isEmpty()&&signal==null,"Same returned identity cannot resurrect stale contact behind sealed readiness window");
                    if(phase.equals("RETURN"))second|=signal!=null&&id.equals(signal.subject())&&signal.observed()>=reopened&&signal.lock(now);
                    if(phase.equals("SEALED")){long cover=sealed;h.assertTrue(tracks.stream().noneMatch(t->t.observed()>cover),"Returned guest sealed view has no hidden refresh");finalIdle=tracks.isEmpty()&&signal==null&&a.blindController().diagnostics().startsWith("blind=WANDER ")?finalIdle+1:0;}
                    var row=new com.google.gson.JsonObject();row.addProperty("timer",i);row.addProperty("gameTime",now);row.addProperty("phase",phase);row.addProperty("guestPresent",s.level.getEntity(id)!=null);row.addProperty("noAI",a.isNoAi());row.add("tracks",new com.google.gson.Gson().toJsonTree(tracks));row.add("selected",new com.google.gson.Gson().toJsonTree(signal));row.addProperty("diagnostics",a.blindController().diagnostics());trace.add(row);
                }
                final long firstOpened=start+40,secondOpened=reopened;
                var knowledge=MaeveDirector.worldSnapshot(s.server,id);h.assertTrue(first&&second&&absenceIdle>=40&&finalIdle>=40,"Actual departure expires, same returned player requires fresh ordinary echo and final seal expires");
                for(long boundary:new long[]{firstOpened,secondOpened})h.assertTrue(knowledge.stream().anyMatch(x->x.label().equals("ACOUSTIC_CONTACT")&&x.provenance().stream().anyMatch(e->e.observer().equals(a.getUUID())&&e.time()>boundary&&e.action().startsWith("ACOUSTIC_ECHO"))),"Guest retains fresh attributable acoustic evidence for both appearances");
                h.assertTrue(MaeveDirector.worldSnapshot(s.server,host.getUUID()).stream().noneMatch(x->x.label().equals("ACOUSTIC_CONTACT")),"Covered Creative host acquires no borrowed guest acoustic evidence");proof.addProperty("firstLock",first);proof.addProperty("returnedFreshLock",second);proof.addProperty("sameUuidNewPlayerObject",returned!=guest&&returned.getUUID().equals(id));proof.addProperty("absenceIdleTail",absenceIdle);proof.addProperty("finalIdleTail",finalIdle);proof.addProperty("outcome","PASSED");
            }finally{
                try{java.nio.file.Files.writeString(s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-reconnect-control.json"),proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(grief,s.server);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(regen,s.server);
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindAttentionPreviousHostWindowAllowsActualMelee(GameTestHelper h){
        MaeveObservationGameTest.withScene(h,452,s->{boolean grief=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);try{
            for(int x=3;x<=20;x++)for(int z=3;z<=7;z++)for(int y=-1;y<=4;y++)s.block(x,y,z,com.frozendawn.labbridge.LabBlindAttention.previousWanted(x-15,y,z-5,false,true));var a=blind(s,15,5);var host=attentionPlayer(s,"attention_unsafe_host",15,3);host.setPos(host.getX(),host.getY(),host.getZ()-.2);host.setOnGround(true);long start=s.level.getGameTime();for(int i=1;i<=200&&host.getHealth()==20;i++){s.clock(start+i);s.level.tickNonPassenger(a);}h.assertTrue(host.getHealth()<20,"Previous2.2-block raised window must reproduce actual ordinary melee damage with damageable participant");
        }finally{s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(grief,s.server);}});
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindTwoActivePlayersPreferFreshNearContactThenSwitchAfterCover(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,451,s -> {
            boolean grief=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING),regen=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION);
            s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false,s.server);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,s.server);
            var proof=new com.google.gson.JsonObject();var trace=new com.google.gson.JsonArray();proof.add("trace",trace);var probes=new com.google.gson.JsonArray();proof.add("geometryProbes",probes);proof.addProperty("damageableParticipants",true);proof.addProperty("guestDistance",3.0);proof.addProperty("hostDistance",4.2);proof.addProperty("limits","Physical native two-active-player control with ordinary actor physics/AI. Real client focus/input/network and owner acceptance remain separate.");
            try{
                for(int x=3;x<=20;x++)for(int z=1;z<=7;z++)for(int y=-1;y<=4;y++)s.block(x,y,z,com.frozendawn.labbridge.LabBlindAttention.wanted(x-15,y,z-5,false,false));s.block(16,-2,5,Blocks.GLASS.defaultBlockState());
                var a=blind(s,15,5);var guest=attentionPlayer(s,"attention_guest",18,5);var host=attentionPlayer(s,"attention_host",15,1);host.setPos(host.getX(),host.getY(),host.getZ()-.2);guest.setOnGround(true);host.setOnGround(true);h.assertTrue(!guest.isInvulnerableTo(s.level.damageSources().mobAttack(a))&&!host.isInvulnerableTo(s.level.damageSources().mobAttack(a)),"Attention participants must accept real damage; NeoForge fake-player immunity cannot hide hits");h.assertTrue(a.distanceToSqr(guest)>2.8*2.8&&a.distanceToSqr(host)>2.8*2.8,"Both physical cells lie beyond ordinary melee reach");var origin=a.position();long start=s.level.getGameTime(),both=-1,guestSealed=-1,sealed=-1;int peers=0,preferred=0,idle=0;boolean guestLock=false,hostLock=false;
                for(int i=1;i<=1000;i++){
                    String phase=i<=40?"GROUND":i<=200?"GUEST":i<=400?"BOTH":i<=640?"HOST":"SEALED";
                    if(i==41||i==201||i==401||i==641){boolean g=i==41||i==201,hostOpen=i==201||i==401;for(int y=0;y<=1;y++){s.block(16,y,5,com.frozendawn.labbridge.LabBlindAttention.wanted(1,y,0,g,hostOpen));s.block(15,y,4,com.frozendawn.labbridge.LabBlindAttention.wanted(0,y,-1,g,hostOpen));}if(i==201)both=s.level.getGameTime();if(i==401)guestSealed=s.level.getGameTime();if(i==641)sealed=s.level.getGameTime();}
                    if(java.util.Set.of(41,201,300,401,500,641).contains(i)){
                        var probe=new com.google.gson.JsonObject();probe.addProperty("timer",i);probe.addProperty("phase",phase);probe.add("eye",new com.google.gson.Gson().toJsonTree(a.getEyePosition()));probe.addProperty("headYaw",a.getYHeadRot());probe.addProperty("pitch",a.getXRot());var bodies=new com.google.gson.JsonArray();
                        for(var player:java.util.List.of(guest,host)){
                            var body=new com.google.gson.JsonObject();body.addProperty("player",player.getGameProfile().getName());body.addProperty("uuid",player.getUUID().toString());body.add("position",new com.google.gson.Gson().toJsonTree(player.position()));body.add("bounds",new com.google.gson.Gson().toJsonTree(player.getBoundingBox()));body.add("echo",new com.google.gson.Gson().toJsonTree(com.frozendawn.entity.blind.BlindAcousticPropagation.echo(s.level,a.getEyePosition(),a.getYHeadRot(),a.getXRot(),player.getBoundingBox(),s.level.getGameTime(),512)));var rays=new com.google.gson.JsonArray();
                            for(double height:new double[]{.85,.50,.25}){var box=player.getBoundingBox();var center=new net.minecraft.world.phys.Vec3((box.minX+box.maxX)*.5,box.minY+box.getYsize()*height,(box.minZ+box.maxZ)*.5);var patch=box.clip(a.getEyePosition(),center).orElse(center);var hit=s.level.clip(new net.minecraft.world.level.ClipContext(a.getEyePosition(),patch,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,a));var ray=new com.google.gson.JsonObject();ray.addProperty("height",height);ray.addProperty("hit",hit.getType().name());ray.addProperty("block",hit.getBlockPos().subtract(a.blockPosition()).toShortString());ray.addProperty("state",s.level.getBlockState(hit.getBlockPos()).toString());ray.add("point",new com.google.gson.Gson().toJsonTree(hit.getLocation().subtract(a.position())));rays.add(ray);}body.add("rays",rays);bodies.add(body);
                        }probe.add("bodies",bodies);probes.add(probe);
                    }
                    s.clock(start+i);s.level.tickNonPassenger(a);long now=s.level.getGameTime();var tracks=a.blindController().tracks();var selected=a.blindController().signal();h.assertTrue(a.position().distanceToSqr(origin)<.001&&!a.isNoAi()&&a.getHealth()==40&&guest.getHealth()==20&&host.getHealth()==20&&a.getTarget()==null&&!a.isMiningBlock()&&a.successfulBreakCount()==0,"Physical fixture remains stationary/healthy with ordinary AI and no damage/mining");
                    var row=new com.google.gson.JsonObject();row.addProperty("timer",i);row.addProperty("gameTime",now);row.addProperty("phase",phase);row.add("tracks",new com.google.gson.Gson().toJsonTree(tracks));row.add("selected",new com.google.gson.Gson().toJsonTree(selected));row.addProperty("diagnostics",a.blindController().diagnostics());row.add("lastEcho",new com.google.gson.Gson().toJsonTree(a.blindController().lastEcho()));trace.add(row);
                    h.assertTrue(tracks.stream().allMatch(t->t.subject()==null||java.util.Set.of(host.getUUID(),guest.getUUID()).contains(t.subject())),"Only actually present participant identities enter memory");
                    if(phase.equals("GROUND"))h.assertTrue(tracks.isEmpty(),"Both sealed participants supply no direct returns");
                    else if(phase.equals("GUEST")){h.assertTrue(tracks.stream().noneMatch(t->host.getUUID().equals(t.subject())),"Covered host is not identified through the guest opening");guestLock|=selected!=null&&guest.getUUID().equals(selected.subject())&&selected.lock(now);}
                    else if(phase.equals("BOTH")){long opened=both;boolean two=tracks.stream().anyMatch(t->guest.getUUID().equals(t.subject())&&t.observed()>opened&&t.lock(now))&&tracks.stream().anyMatch(t->host.getUUID().equals(t.subject())&&t.observed()>opened&&t.lock(now));if(two){peers++;if(selected!=null&&guest.getUUID().equals(selected.subject())&&selected.lock(now))preferred++;}}
                    else if(phase.equals("HOST")){long cover=guestSealed;h.assertTrue(tracks.stream().noneMatch(t->guest.getUUID().equals(t.subject())&&t.observed()>cover),"Covered guest has no post-shutter refresh while actual host remains exposed");hostLock|=selected!=null&&host.getUUID().equals(selected.subject())&&selected.observed()>cover&&selected.lock(now);}
                    else{long cover=sealed;h.assertTrue(tracks.stream().noneMatch(t->t.observed()>cover),"Both seals block new observations without memory clearing");boolean empty=tracks.isEmpty()&&selected==null&&a.blindController().diagnostics().startsWith("blind=WANDER ");idle=empty?idle+1:0;}
                }
                h.assertTrue(guestLock&&peers>=20&&preferred==peers&&hostLock&&idle>=40,"Guest first, both actual fresh peers prefer nearer guest, then ordinary switch to uncovered host and natural empty idle tail: peers="+peers+" preferred="+preferred+" host="+hostLock+" idle="+idle);
                final long opened=start+40;for(var player:java.util.List.of(guest,host))h.assertTrue(MaeveDirector.worldSnapshot(s.server,player.getUUID()).stream().anyMatch(x->x.label().equals("ACOUSTIC_CONTACT")&&x.provenance().stream().anyMatch(e->e.observer().equals(a.getUUID())&&e.time()>opened&&e.action().startsWith("ACOUSTIC_ECHO"))),"Each actual acquired player has its own actor acoustic provenance");
                proof.addProperty("guestLock",guestLock);proof.addProperty("peerFrames",peers);proof.addProperty("guestPreferredFrames",preferred);proof.addProperty("hostSwitch",hostLock);proof.addProperty("idleTail",idle);proof.addProperty("outcome","PASSED");
            }finally{
                try{java.nio.file.Files.writeString(s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-attention-control.json"),proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(grief,s.server);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(regen,s.server);
            }
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindUnloadConfirmationWaitsForActualStorageRemoval(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 448, s -> {
            room(s,12,8);var a=blind(s,2,4);a.setNoAi(true);
            // Reproduce observed ordering with the actual native entity: tracking ends first.
            boolean oldCallbackLatch=a.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK;
            h.assertTrue(!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,true,true,false,false),"Tracking/chunk callbacks without actual storage removal cannot return");
            a.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(!oldCallbackLatch&&com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,true,true,false,false),"Later real removal reason confirms storage despite earlier null callback");
            h.assertTrue(!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,false,true,false,false)
                &&!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,true,false,false,false)
                &&!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,true,true,true,false)
                &&!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(a,true,true,false,true),"Both callbacks and actual chunk/entity absence remain required");
            for(var reason:java.util.List.of(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,net.minecraft.world.entity.Entity.RemovalReason.KILLED)){
                var other=blind(s,3,4);other.setRemoved(reason);
                h.assertTrue(!com.frozendawn.labbridge.LabBlindUnload.storageConfirmed(other,true,true,false,false),"Non-storage removal refuses: "+reason);
            }
            var proof=new com.google.gson.JsonObject();proof.addProperty("callbackBeforeRemovalRejected",true);proof.addProperty("laterStorageRemovalAccepted",true);proof.addProperty("absenceAndCallbacksRequired",true);proof.addProperty("nonStorageRemovalRejected",true);proof.addProperty("limits","Synthetic ordering regression using native entity removal; live chunk storage still pending.");
            try{java.nio.file.Files.writeString(s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-unload-order-control.json"),proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSaveReloadDropsTargetsAndRetainsNoiseCooldown(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,192,s -> {
            room(s,12,8);var a=blind(s,2,4);var p=s.player("blind_saved",6,4);a.aiStep();tick(s,a,10);
            h.assertTrue(!a.blindController().tracks().isEmpty(),"Serializer control begins with actually sensed contact");
            a.setNoAi(true);a.setPersistenceRequired();
            CompoundTag saved=new CompoundTag();h.assertTrue(a.save(saved),"Actual entity storage serializer includes ID/UUID");saved.putLong("BlindNoiseUntil",s.level.getGameTime()+200);
            var loadedEntity=net.minecraft.world.entity.EntityType.loadEntityRecursive(saved,s.level,e->e);
            h.assertTrue(loadedEntity instanceof ArchitectEntity,"Storage loader reconstructs Architect type");
            var loaded=(ArchitectEntity)loadedEntity;s.entities.add(loaded);
            h.assertTrue(loaded!=a&&loaded.getUUID().equals(a.getUUID())&&loaded.isNoAi()&&loaded.getHealth()==a.getHealth()&&loaded.position().equals(a.position()),"New instance retains UUID/health/position/held state");
            h.assertTrue(loaded.isBlindArchitect() && !loaded.isCustomNameVisible() && loaded.getTarget()==null && loaded.blindController().tracks().isEmpty()&&loaded.blindController().signal()==null,"Reload preserves variant and discards contact UUIDs/positions");
            h.assertTrue(loaded.saveWithoutId(new CompoundTag()).getLong("BlindNoiseUntil")==s.level.getGameTime()+200,"Pain cooldown survives reload");
            a.discard();h.assertTrue(s.level.addFreshEntity(loaded),"Storage instance rejoins actual level after old executor leaves");loaded.setNoAi(false);
            var proof=new com.google.gson.JsonObject();proof.addProperty("actor",a.getUUID().toString());proof.addProperty("sameUuidNewObject",loaded!=a&&loaded.getUUID().equals(a.getUUID()));proof.addProperty("emptyAfterStorageLoad",loaded.blindController().tracks().isEmpty());
            var samples=new com.google.gson.JsonArray();boolean sensedAfterLoad=false;
            for(int i=0;i<300;i++){s.clock(s.level.getGameTime()+1);loaded.tickCount++;loaded.tick();sensedAfterLoad|=loaded.blindController().tracks().stream().anyMatch(t->p.getUUID().equals(t.subject()));if(i%20==0){var f=new com.google.gson.JsonObject();f.addProperty("timer",i+1);f.addProperty("entityTickCount",loaded.tickCount);f.addProperty("gameTime",s.level.getGameTime());f.addProperty("diagnostics",loaded.blindController().diagnostics());f.addProperty("position",loaded.position().toString());f.add("tracks",new com.google.gson.Gson().toJsonTree(loaded.blindController().tracks()));samples.add(f);}}
            proof.add("samples",samples);proof.addProperty("reacquired",sensedAfterLoad);
            try{var path=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("blind-storage-control.json");java.nio.file.Files.writeString(path,proof+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}

            h.assertTrue(sensedAfterLoad,"Fresh ordinary sensing reacquires after storage reconstruction");

        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindErasureClearsContactAndPreventsRetaliation(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,193,s -> {
            room(s,12,8);var a=blind(s,2,4);var p=s.player("blind_erased",6,4);a.aiStep();tick(s,a,10);
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(true);MaeveDirector.erase(s.server);tick(s,a,1);
            a.invulnerableTime=0;a.hurt(s.level.damageSources().playerAttack(p),2);tick(s,a,60);
            h.assertTrue(a.getTarget()==null && a.blindController().tracks().isEmpty() && a.blindController().diagnostics().contains("ERASED"),"Erased actor clicks/wanders and damage never restarts hunting");
            h.assertTrue(!BlindArchitectSpawner.enabled(s.level),"ERASED closes natural spawning");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindErasedLiveDriverUsesSavedLifecycleAndPlayerDamage(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 404, s -> {
            var manager = s.server.tickRateManager(); boolean frozen = manager.isFrozen();
            boolean spawning = s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
            try {
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true, s.server);
                manager.setFrozen(false);
                for(int x=0;x<=30;x++)for(int z=0;z<=10;z++)for(int y=-1;y<=2;y++)
                    s.block(x,y,z,x==0||x==30||z==0||z==10||y==-1||y==2?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
                s.block(9,-1,5,Blocks.SEA_LANTERN.defaultBlockState());s.block(23,-1,5,Blocks.SEA_LANTERN.defaultBlockState());
                for(int z=1;z<=9;z++)s.block(16,0,z,Blocks.BEDROCK.defaultBlockState());
                var a=blind(s,9,5);var p=s.player("erased_live_driver",23,5);p.setOnGround(true);
                a.setNoAi(true);a.setPersistenceRequired();a.setYRot(-90);a.setYHeadRot(-90);a.setYBodyRot(-90);
                var path=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("erased-driver-"+a.getUUID());
                java.nio.file.Files.createDirectory(path);
                var view=new com.frozendawn.labbridge.LabBlindErased.Session(s.level,p,a,path);view.begin();a.setNoAi(false);
                for(int i=1;i<=440;i++){
                    s.clock(s.level.getGameTime()+1);a.tickCount++;a.tick();view.tick();
                    if(i==159){java.nio.file.Files.writeString(path.resolve("pre-contact-decisions.tsv"),a.decisionJournal().tsv());h.assertTrue(a.blindController().signal()!=null && p.getUUID().equals(a.blindController().signal().subject()),"Actual echoes acquire the owner across the protected barrier before erasure");}
                    if(i==160)h.assertTrue(s.hearths.maeveErased()&&!com.frozendawn.config.FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()
                            &&com.frozendawn.labbridge.LabBlindErased.Session.cleared(a),"Actual saved lifecycle clears local contacts immediately without a config shortcut");
                }
                var end=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path.resolve("end.json"))).getAsJsonObject();
                h.assertTrue(end.get("outcome").getAsString().equals("COMPLETE_OWNER_ACCOUNT_PENDING")&&end.get("dropped").getAsInt()==0,"The exact live driver completes all440 frames without loss");
                var damage=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path.resolve("damage.json"))).getAsJsonObject();
                h.assertTrue(damage.get("accepted").getAsBoolean()&&damage.get("healthAfter").getAsFloat()<damage.get("healthBefore").getAsFloat(),"Actual player-source hurt path applies health damage after erasure");
                var frames=java.nio.file.Files.readAllLines(path.resolve("trace.jsonl"));
                h.assertTrue(frames.size()==440&&frames.stream().skip(160).map(com.google.gson.JsonParser::parseString).map(x->x.getAsJsonObject())
                        .allMatch(x->x.get("diagnostics").getAsString().startsWith("blind=ERASED")&&!x.get("spawnGateOpen").getAsBoolean()
                                &&x.get("mobSpawning").getAsBoolean()&&x.get("vanillaTarget").getAsString().isEmpty()&&x.get("tracks").getAsString().equals("[]")),
                        "After the saved erasure and real hit, every frame remains cleared and natural spawn gate is closed while mob spawning stays enabled");
                h.assertTrue(a.decisionJournal().tsv().lines().noneMatch(line->line.contains("\tMELEE_HIT\t")),"Protected view contains no melee before or after transition");
                h.assertTrue(s.hearths.maeveErased()&&manager.isFrozen()&&a.isAlive()&&!a.isNoAi(),"One-use completion preserves ERASED, actor state and held final scene");
            } catch(java.io.IOException error){throw new IllegalStateException(error);}
            finally{manager.setFrozen(frozen);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(spawning,s.server);}
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindErasedLiveDriverRefusesMissingContactAndChangedParticipant(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,405,s->{
            var manager=s.server.tickRateManager();boolean frozen=manager.isFrozen();
            boolean spawning=s.level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
            try{
                s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(true,s.server);manager.setFrozen(false);
                room(s,12,8);var a=blind(s,2,4);var p=s.player("erased_no_contact",6,4);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var path=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("erased-no-contact-"+a.getUUID());java.nio.file.Files.createDirectory(path);
                var view=new com.frozendawn.labbridge.LabBlindErased.Session(s.level,p,a,path);view.begin();
                for(int i=1;i<=160;i++){s.clock(s.level.getGameTime()+1);a.tickCount++;a.tick();view.tick();}
                var end=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path.resolve("end.json"))).getAsJsonObject();
                h.assertTrue(end.get("outcome").getAsString().equals("NO_CONFIRMED_PRE_ERASURE_CONTACT")&&!s.hearths.maeveErased()
                        &&manager.isFrozen()&&!java.nio.file.Files.exists(path.resolve("damage.json")),"No acquired pre-contact leaves lifecycle unchanged, scene held and no test hit");
                manager.setFrozen(false);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                var path2=s.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("erased-moved-"+a.getUUID());java.nio.file.Files.createDirectory(path2);
                var moved=new com.frozendawn.labbridge.LabBlindErased.Session(s.level,p,a,path2);moved.begin();p.setPos(p.position().add(1,0,0));s.clock(s.level.getGameTime()+1);moved.tick();
                var movedEnd=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path2.resolve("end.json"))).getAsJsonObject();
                h.assertTrue(movedEnd.get("outcome").getAsString().equals("PLAYER_ACTOR_OR_CLOCK_CHANGED")&&!s.hearths.maeveErased()
                        &&manager.isFrozen(),"Changed participant position stops before the irreversible transition");
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
            finally{manager.setFrozen(frozen);s.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(spawning,s.server);}
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindNearestMultiplayerAndAttentionEviction(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,194,s -> {
            room(s,14,10);var a=blind(s,2,4);var far=s.player("blind_far",8,4);var near=s.player("blind_near",4,4);a.aiStep();tick(s,a,10);
            h.assertTrue(a.blindController().signal()!=null && a.blindController().signal().subject().equals(near.getUUID()),"Nearest confident player signal wins");
            h.assertTrue(MaeveDirector.attentionSnapshot(s.server).slots().stream().anyMatch(slot->slot.executors().contains(a.getUUID())),"Blind consumes shared attention");
            a.blindController().evicted(a.blindController().signal().region());tick(s,a,1);
            h.assertTrue(a.blindController().tracks().isEmpty() && a.getTarget()==null && a.getCurrentAction()==ArchitectEntity.ACTION_RETREAT,"Evicted concern visibly disengages and drops contact");
            h.assertTrue(MaeveDirector.pawnOrder(a)==null && MaeveDirector.commitmentHints(a,far).isEmpty(),"Blind cannot become a surface pawn or visual counter executor");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindDeathBeforeContactReportsOnlyDangerRegion(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,195,s -> {
            room(s,12,8);var a=blind(s,2,4);MaeveDirector.observePawn(a);MaeveDirector.observePawnDeath(a);
            h.assertTrue(MaeveDirector.knownPlayers(s.server).isEmpty(),"Unsensed death invents no player profile");
            var memory=MaeveSavedData.get(s.server).convergence();
            h.assertTrue(!memory.hotspots.isEmpty() && !memory.population.pawns.containsKey(a.getUUID()),"Death region enters danger/convergence history and releases physical population");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindQueriesAndSavedSpawnCooldownAreBounded(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,196,s -> {
            room(s,12,8);var a=blind(s,2,4);var p=s.player("blind_budget",8,4);
            var unknown=BlindArchitectSensing.air(s.level,a.getEyePosition(),p.getEyePosition(),24,1);
            h.assertTrue(!unknown.reached() && unknown.exhausted() && unknown.visited()<=1,"Budget exhaustion is unknown, never a player shortcut");
            var memory=new BlindSpawnMemory();memory.spawned(a.blockPosition(),s.level.getGameTime());
            h.assertTrue(!memory.ready(a.blockPosition(),s.level.getGameTime()+11999) && memory.ready(a.blockPosition(),s.level.getGameTime()+12000),"Regional spawn cooldown uses the full ten-minute window");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindProductionBreachAndMeleeUseFreshLocalEchoes(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,197,s -> {
            room(s,14,10);for(int z=1;z<10;z++)for(int y=0;y<=2;y++)s.block(5,y,z,Blocks.STONE.defaultBlockState());
            // A direct slit across the wall reflects the exposed upper body while
            // blocking movement, including while the actor approaches its uncertain
            // measured region. A one-cell opening tests aperture search instead.
            for(int z=1;z<10;z++)s.block(5,1,z,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            var a=blind(s,2,4);var p=MaeveReconnaissanceGameTest.damageablePlayer(s,"blind_breach");p.setPos(s.position(8,4));a.setOnGround(true);long start=s.level.getGameTime();
            var frames=new java.util.ArrayList<java.util.Map<String,Object>>();
            // Bearing bins may first open an adjacent column, then refine the region.
            // Allow three physical cuts at the unchanged doubled break time plus approach.
            for(int i=0;i<400;i++) {
                s.clock(start+i);
                s.level.tickNonPassenger(a);
                var frame=new java.util.LinkedHashMap<String,Object>();frame.put("tick",i);frame.put("position",a.position());
                frame.put("signal",a.blindController().signal());frame.put("echo",a.blindController().lastEcho());
                frame.put("breaks",a.successfulBreakCount());frame.put("navigationDone",a.getNavigation().isDone());
                frame.put("diagnostics",a.blindController().diagnostics());frames.add(frame);
                if(!p.isAlive()) break;
            }
            try {
                var directory=java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports","build/architect-reports"),"blind-acoustic");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve("production-breach.json"),new com.google.gson.Gson().toJson(frames)+"\n");
            } catch(java.io.IOException failure) { throw new IllegalStateException(failure); }
            h.assertTrue(a.successfulBreakCount()>=2,"Production executor opens both torso blocks: "+a.blindController().diagnostics()+" position="+a.position()+" breaks="+a.successfulBreakCount());
            h.assertTrue(a.getTarget()==null && a.distanceTo(p)<3.5,"Breach hands off to snapshot navigation, retaining no live vanilla target");
            h.assertTrue(p.getHealth()<p.getMaxHealth(),"Fresh close echo permits a real ordinary-damage melee strike: "+a.blindController().diagnostics()+" distance="+a.distanceTo(p)+" actor="+a.position()+" player="+p.position());
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindSharedAttentionActuallyDefersAndEvicts(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,198,s -> {
            room(s,20,14);int capacity=MaeveDirector.attentionSnapshot(s.server).capacity();var originals=new java.util.ArrayList<ArchitectEntity>();
            for(int i=0;i<capacity;i++) {
                var a=blind(s,2+i*3,4);a.blindController().emit(a.getEyePosition().add(0,0,2),null,24);a.aiStep();originals.add(a);
            }
            var denied=blind(s,2,8);denied.blindController().emit(denied.getEyePosition().add(0,0,2),null,24);denied.aiStep();
            h.assertTrue(MaeveDirector.attentionSnapshot(s.server).slots().size()==capacity && denied.blindController().tracks().isEmpty(),"Minimum dwell defers another real Blind concern");
            s.clock(s.level.getGameTime()+101);var admitted=blind(s,8,8);admitted.blindController().emit(admitted.getEyePosition().add(0,0,2),null,24);admitted.aiStep();
            h.assertTrue(MaeveDirector.attentionSnapshot(s.server).slots().size()==capacity && admitted.blindController().signal()!=null,"After dwell a new concern reuses an existing slot");
            h.assertTrue(originals.stream().anyMatch(a->a.blindController().tracks().isEmpty()),"Manager eviction clears the actual old executor");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindErasureCancelsUnadmittedPendingReturnsImmediately(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,199,s -> {
            room(s,12,8);var a=blind(s,2,4);s.player("blind_pending_erased",6,4);a.aiStep();
            h.assertTrue(a.blindController().diagnostics().contains("pending=1"),"Fixture has a pending return before admission");
            MaeveDirector.erase(s.server);
            h.assertTrue(a.blindController().diagnostics().contains("pending=0") && a.blindController().tracks().isEmpty(),"Authoritative erasure clears pending return before any executor tick");
        });
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindUnloadKeepsSharedPhysicalPopulationClaim(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,200,s -> {
            room(s,14,10);MaeveDirector.snapshot(s.server,null);var memory=MaeveSavedData.get(s.server).convergence();
            for(int i=0;i<ConvergencePolicy.populationLimit(0);i++) { var a=blind(s,2+i*2,4);MaeveDirector.observePawn(a);a.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK); }
            h.assertTrue(memory.population.pawns.size()==ConvergencePolicy.populationLimit(0),"Chunk unload never frees physical claims");
            h.assertTrue(!MaeveDirector.allowNaturalPawn(s.level,s.origin.offset(10,0,4)),"Blind actors consume the existing global physical population cap");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindOngoingMiningUsesServerAcceptedProgress(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,201,s -> {
            room(s,14,10);for(int z=1;z<10;z++)for(int y=0;y<=2;y++)s.block(5,y,z,Blocks.STONE.defaultBlockState());
            var a=blind(s,2,4);var p=s.player("blind_mining",6,4);BlockPos mined=s.origin.offset(5,0,4);
            p.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,net.minecraft.core.Direction.UP,s.level.getMaxBuildHeight(),0);
            long start=s.level.getGameTime();for(int i=1;i<=10;i++){s.clock(start+i);p.gameMode.tick();}
            h.assertTrue(s.level.getBlockState(mined).is(Blocks.STONE),"Ongoing uncompleted stone mining fixture");
            h.assertTrue(a.blindController().tracks().stream().anyMatch(t->t.sense()==Sense.VIBRATION && t.subject()==null),"Actual server mining progress is audible through rock before a completed break");
            double standingStrength=a.blindController().lastVibration().received();
            p.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,net.minecraft.core.Direction.UP,s.level.getMaxBuildHeight(),1);
            a.blindController().clearLocal();for(int i=11;i<=30;i++){s.clock(start+i);p.gameMode.tick();}
            h.assertTrue(a.blindController().tracks().isEmpty(),"Aborted mining emits no synthetic continuing noise");
            p.setShiftKeyDown(true);
            p.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,net.minecraft.core.Direction.UP,s.level.getMaxBuildHeight(),2);
            for(int i=31;i<=40;i++){s.clock(start+i);p.gameMode.tick();}
            h.assertTrue(a.blindController().lastVibration()!=null&&a.blindController().lastVibration().received()==standingStrength,
                    "Actual accepted crouched mining retains the same structural tool impulse");
            p.gameMode.handleBlockBreakAction(mined,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,net.minecraft.core.Direction.UP,s.level.getMaxBuildHeight(),3);
            a.blindController().clearLocal();
            s.level.gameEvent(GameEvent.BLOCK_DESTROY,mined.getCenter(),GameEvent.Context.of(a));
            h.assertTrue(a.blindController().tracks().isEmpty(),"An Architect's own block work cannot feed its sensing loop");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void blindBellRetreatMovesAwayThroughRealPhysics(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h,202,s -> {
            room(s,20,10);var a=blind(s,5,4);a.setOnGround(true);s.block(3,0,4,Blocks.BELL.defaultBlockState());
            Vec3 source=s.origin.offset(3,0,4).getCenter();double initial=a.position().distanceTo(source);long start=s.level.getGameTime();
            s.level.gameEvent(GameEvent.BLOCK_CHANGE,source,GameEvent.Context.of(Blocks.BELL.defaultBlockState()));
            for(int i=1;i<=90;i++){s.clock(start+i);s.level.tickNonPassenger(a);}
            h.assertTrue(a.position().distanceTo(source)>initial+3,"Noise pain produces physical retreat after stun: "+a.blindController().diagnostics()+" distance="+a.position().distanceTo(source));
            h.assertTrue(a.getTarget()==null && a.blindController().tracks().isEmpty(),"Retreat never follows a player target or accumulates contacts");
        });
    }

}
