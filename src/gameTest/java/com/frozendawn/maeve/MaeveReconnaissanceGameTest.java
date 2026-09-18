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
            for (String name : java.util.List.of("load", "setup", "initialize", "restart", "retry", "cleanup", "next_site", "advance", "crossed", "ready",
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
    public static void maeveReconSurveysAndLeavesThroughRealAi(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 27, scene -> {
            var player = scene.player("recon_witnessed", 4, 5); long now = history(scene, player); var actor = scout(scene);
            float health = player.getHealth(); boolean thinking = false, purple = false; MaeveDirector.MissionPacket inherited = null;
            for (int i = 0; i < 400; i++) {
                tick(scene, actor, now + i);
                var packet = MaeveDirector.missionPacket(actor);
                if (packet != null) inherited = packet;
                thinking |= actor.isHoldingMaevePosition(); purple |= actor.hasReconnaissanceEyes();
            }
            var views = MaeveDirector.missionSnapshots(scene.server, player.getUUID());
            helper.assertTrue(inherited != null && views.stream().anyMatch(s -> s.outcome().equals("SURVEY_COMPLETE") && s.report().equals("OPEN")),
                    "Actual entity AI must inspect and finish its non-combat mission: " + views);
            helper.assertTrue(thinking && purple && player.getHealth() == health, "Survey has existing thinking pose and purple eyes, without attacking the player");
            helper.assertTrue(actor.getX() > scene.position(16, 5).x && actor.getTarget() == null, "Scout visibly extracts after inspecting the entrance");
            helper.assertTrue(inherited.access().confidence() == .2 && inherited.access().source().action().equals("WITNESSED_SKY_BOUNDARY_CROSSING"),
                    "Inherited packet remains the original incomplete observation");
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(point.confidence() >= .9 && point.provenance().getLast().action().equals("RECON_INSPECTED_ACCESS_OPEN"), "Report updates only locally inspected access");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "Completed survey releases its concern");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(MaeveDirector.missionSnapshots(scene.server, player.getUUID()).isEmpty() && !actor.hasReconnaissanceEyes(), "Reload drops packets, role cue and transient history");
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
            helper.assertTrue(!actor.hasReconnaissanceEyes() && !actor.isMaeveDisengaging()
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
            var changing = scene.architect(18, 6); var shared = scene.architect(18, 7);
            MaeveDirector.observeAttention(changing, previous); MaeveDirector.observeAttention(shared, previous);
            helper.assertTrue(!MaeveDirector.requestReconnaissance(changing, subject), "A shared old concern and other tracking keep both slots occupied");
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
