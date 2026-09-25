package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveReconnaissanceGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconReplayPracticeAndNativeFunctionsWork(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 31, 2, scene -> {
            for (String name : java.util.List.of("fixture", "load", "setup", "initialize", "restart", "retry", "cleanup", "next_site", "advance", "crossed", "ready",
                    "start", "tick", "finish", "sealed", "status", "walk_prompt", "practice_0", "practice_1", "practice_2",
                    "crossing_0", "crossing_1", "crossing_2", "encounter_0", "encounter_1", "encounter_2")) {
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("macs_recon:" + name)).isPresent(),
                        "Native command parser accepts live function " + name);
                // GameTestServer compiles at permission 4; the integrated client uses 2.
                // Reparse the actual pack at the client's level so unsupported commands cannot silently remove a function.
                var file = net.minecraft.resources.ResourceLocation.parse("macs_recon:function/" + name + ".mcfunction");
                try (var reader = scene.server.getResourceManager().getResource(file).orElseThrow().openAsReader()) {
                    net.minecraft.commands.functions.CommandFunction.fromLines(
                            net.minecraft.resources.ResourceLocation.parse("macs_recon:" + name), scene.server.getCommands().getDispatcher(),
                            scene.server.createCommandSourceStack().withPermission(2), reader.lines().toList());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Cannot read live function " + name, e);
                }
            }
            var protectedPlayer = scene.player("wrong_recon_world", 8, 4);
            var protectedActor = scene.architect(2, 4);
            scene.hit(protectedActor, protectedPlayer, true, 2);
            var beliefs = scene.beliefs(protectedPlayer);
            helper.assertTrue(!beliefs.isEmpty(), "The wrong-world guard protects actual observed history");
            protectedPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            var position = protectedPlayer.position();
            for (String name : java.util.List.of("load", "setup", "initialize", "restart", "start", "practice_0")) {
                scene.server.getCommands().performPrefixedCommand(scene.server.createCommandSourceStack()
                        .withEntity(protectedPlayer).withPosition(position).withPermission(2), "function macs_recon:" + name);
                helper.assertTrue(scene.beliefs(protectedPlayer).equals(beliefs)
                                && protectedPlayer.position().equals(position)
                                && protectedPlayer.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND),
                        "Wrong-world " + name + " must preserve learned history, position and inventory");
            }
            protectedPlayer.discard(); protectedActor.discard();
            MaeveWorldModelGameTest.shelter(scene);
            for (int x = 2; x <= 5; x++) for (int y = 0; y < 4; y++) {
                scene.block(x, y, 3, Blocks.OAK_PLANKS.defaultBlockState()); scene.block(x, y, 7, Blocks.OAK_PLANKS.defaultBlockState());
            }
            for (int z = 3; z <= 7; z++) for (int y = 0; y < 4; y++) {
                scene.block(2, y, z, Blocks.OAK_PLANKS.defaultBlockState());
                if (z != 5 || y == 3) scene.block(5, y, z, Blocks.OAK_PLANKS.defaultBlockState());
            }
            for (int x = 11; x <= 13; x++) for (int z = 4; z <= 6; z++) for (int y = 0; y < 4; y++) {
                boolean air = x == 12 && z == 5 && y < 3 || x == 11 && z == 5 && y == 1;
                scene.block(x, y, z, (air ? Blocks.AIR : Blocks.BEDROCK).defaultBlockState());
            }
            long now = scene.gameTime + 1;
            for (int offset : new int[]{0, 7, 19}) for (boolean entering : new boolean[]{false, true}) {
                int start = entering ? 8 : 4;
                var player = scene.player("recon_practice_" + offset + entering, start, 5); player.setYRot(-90);
                var witness = scene.architect(12, 5);
                for (int i = 0; i <= 640; i++) {
                    if (i >= 600 && i < 620) player.setPos(scene.position(start, 5).add((entering ? -1 : 1) * (i - 599) / 5.0, 0, 0));
                    tick(scene, witness, now + offset + i);
                    helper.assertTrue(witness.getX() >= scene.origin.getX() + 12 && witness.getX() < scene.origin.getX() + 13
                                    && witness.getZ() >= scene.origin.getZ() + 5 && witness.getZ() < scene.origin.getZ() + 6,
                            "Practice observer escaped while a player was getting ready at tick " + i + ": " + witness.position());
                }
                helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream().anyMatch(p -> p.label().equals("ACCESS_POINT")),
                        "Live booth must witness either crossing after the final 20-tick gold-tile dwell at offset " + offset + ", entering=" + entering);
                helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Practice observer cannot reach and hit the player");
                witness.setNoAi(true); witness.discard(); player.discard(); now += 1000;
            }
        });
    }

    private static long history(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player) {
        MaeveWorldModelGameTest.shelter(scene);
        var witness = scene.architect(10, 5);
        long now = (scene.gameTime / 20 + 1) * 20;
        MaeveWorldModelGameTest.sample(scene, witness, player, now, 4, 5);
        MaeveWorldModelGameTest.sample(scene, witness, player, now + 10, 6, 5);
        witness.discard(); player.setPos(scene.position(12, 12));
        scene.clock(now + 650); MaeveDirector.tick(scene.server);
        return now + 650;
    }
    private static void tick(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, long now) {
        scene.clock(now); actor.tickCount++; actor.tick();
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor)); MaeveDirector.tick(scene.server);
    }
    private static ArchitectEntity scout(MaeveObservationGameTest.Scene scene) {
        var actor = scene.architect(18, 5); actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(Vec3.ZERO); return actor;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconCloudCleanupCannotLeaveHiddenEntities(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 98, scene -> {
            var player = scene.player("recon_cloud_cleanup", 4, 5); long now = history(scene, player);
            var actor = scout(scene);
            helper.assertTrue(MaeveDirector.requestReconnaissance(actor, player), "A real admission owns the departure lifecycle");
            MaeveDirector.finishMission(actor, "TEST_EXTRACTION", true);
            tick(scene, actor, now + 220);
            helper.assertTrue(actor.getReconnaissanceDissolve() == 20, "Withdrawal reaches the soul cloud");
            helper.assertTrue(actor.getReconnaissanceCloudAge() == 20, "The visual exchange follows the actual departure clock");
            var saved = actor.saveWithoutId(new CompoundTag());
            var reloaded = com.frozendawn.init.ModEntities.ARCHITECT.get().create(scene.level); reloaded.load(saved);
            helper.assertTrue(reloaded.getReconnaissanceDissolve() == 0 && reloaded.getReconnaissanceCloudAge() == -1 && !reloaded.hasReconnaissanceEyes()
                            && !reloaded.isMaeveDisengaging(), "An entity load cannot restore an orphaned invisible pause");
            reloaded.discard();
            PostMaeveWorldState.markErased(scene.level);
            helper.assertTrue(actor.getReconnaissanceDissolve() == 0 && actor.getReconnaissanceCloudAge() == -1 && !actor.hasReconnaissanceEyes()
                            && !actor.isMaeveDisengaging(), "Authoritative erasure immediately restores the visible ordinary body");
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, null).isEmpty(), "No old mission remains accessible");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconCannotInterruptCombatOrRepeatAcrossReload(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 97, scene -> {
            var player = scene.player("recon_one", 4, 5); long now = history(scene, player);
            var first = scout(scene);
            helper.assertTrue(MaeveDirector.requestReconnaissance(first, player), "Historical uncertainty permits a first survey");
            MaeveDirector.finishMission(first, "TEST_INTERRUPTED", false);
            var second = scout(scene);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(second, player),
                    "A fresh observer cannot buy a second survey of the same encounter");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(!MaeveDirector.requestReconnaissance(second, player), "Reload preserves the spent survey");
            scene.clock(now + 610); MaeveDirector.commitmentHints(second, player);
            helper.assertTrue(MaeveDirector.requestReconnaissance(second, player), "A later encounter can revisit unresolved uncertainty");
            MaeveDirector.finishMission(second, "TEST_INTERRUPTED", false);
            scene.clock(now + 1220);
            var fighter = scout(scene); player.setPos(scene.position(17, 5));
            // Let the ordinary minimum action hold expire; do not force a brain state.
            for (int i = 0; i < 10; i++) {
                scene.clock(now + 1220 + i); scene.level.tickNonPassenger(fighter);
            }
            helper.assertTrue(fighter.getBrainAction() != ArchitectEntity.ACTION_OBSERVE
                            && !fighter.hasReconnaissanceEyes(), "A nearby threat begins actual local combat");
            player.setPos(scene.position(12, 12));
            helper.assertTrue(!MaeveDirector.requestReconnaissance(fighter, player), "A fighting Architect cannot start surveying");
            var fresh = scout(scene);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(fresh, player), "Combat closes survey admission for other observers too");
            saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(!MaeveDirector.requestReconnaissance(fresh, player), "Reload cannot turn a combat encounter into reconnaissance");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconCueLastsUntilNaturalReacquisition(GameTestHelper helper) {
        departureCue(helper, 44, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconDamageEndsDepartureCueAndAvoidance(GameTestHelper helper) {
        departureCue(helper, 45, true);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconCloudIsVulnerableAndCannotAttackAnotherPlayer(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 47, 2, scene -> {
            var subject = damageablePlayer(scene, "recon_departure_subject");
            long now = history(scene, subject); var actor = scout(scene); actor.startDecisionRecording(1337L);
            for (int i = 0; i < 300; i++) {
                tick(scene, actor, now + i);
                if (MaeveDirector.missionSnapshots(scene.server, subject.getUUID()).stream()
                        .anyMatch(m -> m.outcome().equals("SURVEY_COMPLETE"))) break;
            }
            var mission = MaeveDirector.missionSnapshots(scene.server, subject.getUUID()).getFirst();
            helper.assertTrue(mission.outcome().equals("SURVEY_COMPLETE"), "A completed survey begins departure");
            long departed = mission.time();
            actor.setPos(scene.position(18, 5)); actor.setDeltaMovement(Vec3.ZERO); actor.setOnGround(true);
            tick(scene, actor, departed + 200);
            helper.assertTrue(actor.isShowingReconnaissancePose() && actor.getMainHandItem().isEmpty(),
                    "Roaming with no eligible combat target retains the scout presentation");
            var other = damageablePlayer(scene, "recon_departure_other");
            other.setPos(scene.position(17, 5)); other.setYRot(-90); other.setXRot(0);
            var identity = actor.getUUID(); float health = actor.getHealth(); Vec3 cloud = actor.position();
            for (int i = 1; i <= 60; i++) tick(scene, actor, departed + 200 + i);
            helper.assertTrue(actor.getReconnaissanceDissolve() == 20 && actor.getTarget() == null
                            && other.getHealth() == other.getMaxHealth() && subject.getHealth() == subject.getMaxHealth(),
                    "A dissolved scout cannot invisibly attack either player");
            helper.assertTrue(actor.getUUID().equals(identity) && actor.getHealth() == health
                            && actor.position().distanceToSqr(cloud) < .01,
                    "Dissolution preserves the physical entity, location and health");
            helper.assertTrue(scene.hit(actor, other, false, 1) && actor.getHealth() < health,
                    "The visible soul cloud remains vulnerable to real damage");
            helper.assertTrue(actor.getReconnaissanceDissolve() == 0 && actor.getReconnaissanceCloudAge() == -1 && !actor.hasReconnaissanceEyes()
                            && !actor.isMaeveDisengaging(), "Damage reforms it immediately for visible local defense");
            for (int i = 1; i <= 300 && other.getHealth() == other.getMaxHealth(); i++) tick(scene, actor, departed + 260 + i);
            helper.assertTrue(other.getHealth() < other.getMaxHealth(), "The reformed Architect really retaliates");
        });
    }

    private static void departureCue(GameTestHelper helper, int lane, boolean hitDuringDeparture) {
        MaeveObservationGameTest.withScene(helper, lane, 2, scene -> {
            var player = damageablePlayer(scene, "recon_departure_" + lane);
            long now = history(scene, player); var actor = scout(scene); actor.startDecisionRecording(1337L);
            actor.decisionJournal().useExtendedLabBuffer();
            for (int i = 0; i < 300; i++) {
                tick(scene, actor, now + i);
                if (MaeveDirector.missionSnapshots(scene.server, player.getUUID()).stream()
                        .anyMatch(m -> m.outcome().equals("SURVEY_COMPLETE"))) break;
            }
            var mission = MaeveDirector.missionSnapshots(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(mission.outcome().equals("SURVEY_COMPLETE"), "The departure follows an actual completed survey");
            long departed = mission.time();
            // Jump only the quiet interval, then exercise actual entity ticks at its boundaries.
            for (int elapsed : new int[] {199, 200, hitDuringDeparture ? 300 : 599}) {
                actor.setPos(scene.position(18, 5)); actor.setDeltaMovement(Vec3.ZERO); actor.setOnGround(true);
                tick(scene, actor, departed + elapsed);
                helper.assertTrue(actor.isMaeveDisengaging() && actor.hasReconnaissanceEyes(),
                        "Purple cue must cover the whole avoidance interval, including tick " + elapsed);
                helper.assertTrue(actor.isShowingReconnaissancePose() && actor.getMainHandItem().isEmpty(),
                        "Both directed departure and later roaming use the scout pose with no misleading ice or weapon");
                helper.assertTrue(actor.getTarget() == null, "Avoidance still suppresses the released subject");
                helper.assertTrue(elapsed < 200 ? actor.getReconnaissanceDissolve() == 0
                                : actor.getReconnaissanceDissolve() != 0,
                        "Dissolution starts after ten seconds, independent of distance walked");
            }
            long released = departed + (hitDuringDeparture ? 301 : 600);
            if (hitDuringDeparture) {
                helper.assertTrue(scene.hit(actor, player, true, 1), "A real bow hit interrupts withdrawal");
                helper.assertTrue(!actor.isMaeveDisengaging() && !actor.hasReconnaissanceEyes() && !actor.isShowingReconnaissancePose(),
                        "Damage immediately clears both avoidance and its cue");
            }
            player.setPos(scene.position(17, 5)); player.setYRot(-90); player.setXRot(0);
            tick(scene, actor, released);
            helper.assertTrue(!actor.isMaeveDisengaging() && !actor.hasReconnaissanceEyes() && !actor.isShowingReconnaissancePose(),
                    "Local combat has ordinary eyes at natural expiry or after damage");
            // Allow the normal 200-tick OBSERVE phase plus pursuit/melee after reacquisition.
            for (int i = 1; i <= 400 && player.getHealth() == player.getMaxHealth(); i++) tick(scene, actor, released + i);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(),
                    "Ordinary combat resumes without requiring another player hit: " + actor.decisionJournal().entries());
            helper.assertTrue(actor.getMainHandItem().is(net.minecraft.world.item.Items.WOODEN_SWORD),
                    "Returning to combat restores the actual combat item");
        });
    }

    static MaeveObservationGameTest.TestPlayer damageablePlayer(MaeveObservationGameTest.Scene scene, String name) {
        // FakePlayer otherwise rejects ordinary damage and never ticks away spawn protection.
        // Keep the native hurt/event path so this regression requires a real melee hit.
        var player = new MaeveObservationGameTest.TestPlayer(scene.level, name) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        try {
            var protection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            protection.setAccessible(true); protection.setInt(player, 0);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot clear the test player's unticked spawn protection", error);
        }
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.setPos(scene.position(4, 5));
        scene.level.addNewPlayer(player); scene.entities.add(player);
        return player;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconSurveysAndLeavesThroughRealAi(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 27, scene -> {
            var player = scene.player("recon_witnessed", 4, 5); long now = history(scene, player); var actor = scout(scene);
            float health = player.getHealth(); boolean thinking = false, purple = false; MaeveDirector.MissionPacket inherited = null;
            double extractionX = Double.NEGATIVE_INFINITY;
            long extractionStarted = -1;
            boolean peacefulExtraction = true;
            for (int i = 0; i < 400; i++) {
                tick(scene, actor, now + i);
                var packet = MaeveDirector.missionPacket(actor);
                if (packet != null) inherited = packet;
                thinking |= actor.isHoldingMaevePosition(); purple |= actor.hasReconnaissanceEyes();
                if (actor.hasReconnaissanceEyes()) helper.assertTrue(actor.isShowingReconnaissancePose()
                                && actor.getMainHandItem().isEmpty(),
                        "Scout travel, inspection and departure retain the noncombat presentation");
                // Directed extraction lasts 200 ticks; later roaming can reverse direction
                // while the cue still shows avoidance. Measure only that directed walk.
                if (packet == null && actor.hasReconnaissanceEyes() && actor.isMaeveDisengaging()) {
                    if (extractionStarted < 0) extractionStarted = now + i;
                    if (now + i - extractionStarted < 200) extractionX = Math.max(extractionX, actor.getX());
                    peacefulExtraction &= actor.getTarget() == null;
                }
            }
            var views = MaeveDirector.missionSnapshots(scene.server, player.getUUID());
            helper.assertTrue(inherited != null && views.stream().anyMatch(s -> s.outcome().equals("SURVEY_COMPLETE") && s.report().equals("OPEN")),
                    "Actual entity AI must inspect and finish its non-combat mission: " + views);
            helper.assertTrue(thinking && purple && player.getHealth() == health, "Survey has existing thinking pose and purple eyes, without attacking the player");
            helper.assertTrue(extractionX > scene.position(16, 5).x && peacefulExtraction && actor.getTarget() == null,
                    "Scout visibly extracts without targeting the player during withdrawal: maxX=" + extractionX
                            + " finalPosition=" + actor.position() + " target=" + actor.getTarget());
            helper.assertTrue(inherited.access().confidence() == .2 && inherited.access().source().action().equals("WITNESSED_SKY_BOUNDARY_CROSSING"),
                    "Inherited packet remains the original incomplete observation");
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(point.confidence() >= .9 && point.provenance().getLast().action().equals("RECON_INSPECTED_ACCESS_OPEN"), "Report updates only locally inspected access");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "Completed survey releases its concern");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, player.getUUID()).isEmpty()
                    && !actor.hasReconnaissanceEyes() && !actor.isShowingReconnaissancePose(), "Reload drops packets, role cue and transient history");
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst().confidence() >= .9, "The witnessed report survives save/load");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconCannotReportHiddenAccessAndDiscoversVisibleSeal(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 28, scene -> {
            var player = scene.player("recon_seal", 4, 5); long now = history(scene, player); var actor = scout(scene);
            helper.assertTrue(MaeveDirector.requestReconnaissance(actor, player), "Valid historical uncertainty produces a packet");
            for (int z = 3; z <= 7; z++) for (int y = 0; y < 3; y++) scene.block(5, y, z, Blocks.STONE.defaultBlockState());
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst().state().equals("OPEN"), "Unseen construction cannot alter stored knowledge");
            helper.assertTrue(MaeveDirector.inspectMission(actor).equals("UNSEEN"), "Far observer cannot inspect the crossing remotely");
            actor.setPos(scene.position(9, 5));
            for (int y = 0; y < 4; y++) for (int z = 3; z <= 7; z++) scene.block(7, y, z, Blocks.STONE.defaultBlockState());
            scene.clock(now + 20);
            helper.assertTrue(MaeveDirector.inspectMission(actor).equals("UNSEEN"), "An unrelated nearer wall does not reveal the remembered entrance");
            for (int y = 0; y < 4; y++) for (int z = 3; z <= 7; z++) scene.block(7, y, z, Blocks.AIR.defaultBlockState());
            scene.clock(now + 40);
            helper.assertTrue(MaeveDirector.inspectMission(actor).equals("BLOCKED"), "Visible seal is reported after actual local inspection");
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(point.state().equals("BLOCKED") && point.contradictions() == 1 && point.confidence() == .9,
                    "Local report contradicts old access without inventing a player tendency");
            helper.assertTrue(scene.beliefs(player).getFirst().confidence() == .2, "Geometry inspection does not assert a player retreat habit");
            tick(scene, actor, now + 41);
            helper.assertTrue(actor.hasReconnaissanceEyes(), "Admitted mission synchronizes its eye color");
            helper.assertTrue(scene.hit(actor, player, false, 1) && MaeveDirector.missionPacket(actor) == null && !actor.hasReconnaissanceEyes(),
                    "Real damage ends the survey and clears its cue for local self-defense");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconEvictionPreservesUncertaintyAndErasureClearsEverything(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 29, scene -> {
            scene.phase.setPresetName("cinematic"); var player = scene.player("recon_pressure", 4, 5);
            long now = history(scene, player); var actor = scout(scene);
            helper.assertTrue(MaeveDirector.requestReconnaissance(actor, player), "Scout admitted");
            var other = scene.player("recon_other", 15, 10); var watcher = scene.architect(18, 10);
            scene.clock(now); MaeveDirector.observeAttention(watcher, other);
            var third = scene.player("recon_third", 14, 11); var newcomer = scene.architect(18, 11);
            scene.clock(now + 99); MaeveDirector.observeAttention(newcomer, third);
            helper.assertTrue(MaeveDirector.missionPacket(actor) != null, "Minimum dwell protects a newly admitted mission");
            scene.clock(now + 100); MaeveDirector.observeAttention(newcomer, third);
            helper.assertTrue(MaeveDirector.missionPacket(actor) == null && actor.isMaeveDisengaging(), "Player pressure visibly abandons reconnaissance first");
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst().confidence() == .2, "Unanswered uncertainty is retained after eviction");
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, other.getUUID()).isEmpty(), "Packet diagnostics isolate players");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(!actor.hasReconnaissanceEyes() && !actor.isShowingReconnaissancePose() && !actor.isMaeveDisengaging()
                    && MaeveDirector.missionSnapshots(scene.server, null).isEmpty(), "Erasure immediately releases scout, departure and retained packet history");
            helper.assertTrue(!MaeveSavedData.get(scene.server).save(new CompoundTag(), null).contains("beliefs"), "No packet or observations survive in an erased save");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, null).isEmpty(), "Debug reset starts empty");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconPlayerSwitchReleasesOnlyPriorMembership(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 32, scene -> {
            scene.phase.setPresetName("cinematic");
            var subject = scene.player("recon_switch_new", 4, 5); history(scene, subject);
            var previous = scene.player("recon_switch_old", 12, 5); var actor = scout(scene);
            var third = scene.player("recon_switch_third", 12, 10); var watcher = scene.architect(18, 10);
            MaeveDirector.observeAttention(actor, previous); MaeveDirector.observeAttention(watcher, third);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 2, "Old tracking fills the capacity");
            helper.assertTrue(MaeveDirector.requestReconnaissance(actor, subject), "Switching players releases the executor's old slot before admission");
            var slots = MaeveDirector.attentionSnapshot(scene.server).slots();
            helper.assertTrue(slots.size() == 2 && slots.stream().noneMatch(s -> s.subject().equals(previous.getUUID()))
                    && slots.stream().filter(s -> s.executors().contains(actor.getUUID())).count() == 1,
                    "One actor occupies only its new mission concern");
            MaeveDirector.finishMission(actor, "TEST_RELEASE", false);
            var nextSubject = scene.player("recon_switch_second", 4, 5);
            history(scene, nextSubject);
            MaeveDirector.observeAttention(watcher, third);
            var changing = scene.architect(18, 6); var shared = scene.architect(18, 7);
            MaeveDirector.observeAttention(changing, previous); MaeveDirector.observeAttention(shared, previous);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(changing, nextSubject), "A shared old concern and other tracking keep both slots occupied");
            slots = MaeveDirector.attentionSnapshot(scene.server).slots();
            helper.assertTrue(slots.stream().filter(s -> s.subject().equals(previous.getUUID())).anyMatch(s ->
                    s.executors().contains(shared.getUUID()) && !s.executors().contains(changing.getUUID())),
                    "A denied conversion removes only the changing actor, preserving the other player's observer");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveReconRequiresHistoryAndOrdinaryVisibleObserver(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 30, scene -> {
            var player = scene.player("recon_eligible", 4, 5); var actor = scout(scene);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(actor, player), "Unknown shelter geometry is never invented");
            long now = history(scene, player);
            var master = scout(scene); master.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(master, player) && !master.hasReconnaissanceEyes(), "Master guardian is unchanged and receives no packet");
            actor.setNoAi(true); helper.assertTrue(!MaeveDirector.requestReconnaissance(actor, player), "NoAI excluded"); actor.setNoAi(false);
            for (int y = 0; y < 4; y++) for (int z = 0; z <= 12; z++) scene.block(15, y, z, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!MaeveDirector.requestReconnaissance(actor, player), "Through-wall target selection cannot supply an admission");
            for (int y = 0; y < 4; y++) for (int z = 0; z <= 12; z++) scene.block(15, y, z, Blocks.AIR.defaultBlockState());
            var other = scene.player("recon_no_history", 12, 11);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(actor, other), "A second player cannot inherit someone else's entrance");
            helper.assertTrue(MaeveDirector.requestReconnaissance(actor, player), "Visible ordinary observer can receive its subject's packet");
            scene.clock(now + MissionPlanner.TIMEOUT); MaeveDirector.tick(scene.server);
            // TIMEOUT need not coincide with the once-per-second lifecycle boundary.
            scene.clock((now + MissionPlanner.TIMEOUT + 19) / 20 * 20); MaeveDirector.tick(scene.server);
            helper.assertTrue(MaeveDirector.missionPacket(actor) == null, "Abandoned executor is bounded by a hard deadline");
        });
    }
}
