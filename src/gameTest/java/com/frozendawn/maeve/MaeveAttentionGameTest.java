package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.data.ReturnedHearthSavedData;
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
public final class MaeveAttentionGameTest {
    private static long clock(MaeveObservationGameTest.Scene scene) { return (scene.gameTime / 20 + 1) * 20; }
    private static void see(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, MaeveObservationGameTest.TestPlayer player, long tick) {
        scene.clock(tick); actor.setTarget(player); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));
    }
    private static ArchitectEntity provoke(MaeveObservationGameTest.Scene scene, MaeveObservationGameTest.TestPlayer player, int x, int z) {
        var actor = scene.architect(x, z); actor.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
        if (!scene.hit(actor, player, true, 1)) throw new AssertionError("Actual projectile provocation must succeed");
        return actor;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionPressureVisiblyShedsSharedTracking(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 18, scene -> {
            scene.phase.setPresetName("cinematic");
            for (int x = -12; x <= 20; x++) for (int z = 0; z <= 14; z++) scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            var player = scene.player("focus_pressure", 12, 4);
            var stalker = scene.architect(6, 4); var witness = scene.architect(6, 7);
            long now = clock(scene); see(scene, stalker, player, now); see(scene, witness, player, now);
            var view = MaeveDirector.attentionSnapshot(scene.server);
            helper.assertTrue(view.capacity() == 2 && view.slots().size() == 1 && view.slots().getFirst().executors().size() == 2,
                    "Two observers tracking the same player share one real concern");
            var first = provoke(scene, player, 13, 8); see(scene, first, player, now);
            var second = provoke(scene, player, 15, 8); see(scene, second, player, now + 80);
            helper.assertTrue(!stalker.isMaeveDisengaging(), "Fresh concerns cannot be dropped before minimum dwell");
            see(scene, second, player, now + 100);
            helper.assertTrue(stalker.isMaeveDisengaging() && witness.isMaeveDisengaging(), "Eviction releases every executor of the concern");
            var start = stalker.position(); stalker.tickCount = 80; stalker.setOnGround(true); stalker.setDeltaMovement(Vec3.ZERO);
            for (int i = 1; i <= 80; i++) { scene.clock(now + 100 + i); stalker.tick(); }
            helper.assertTrue(stalker.getX() < start.x - 3 && stalker.getTarget() == null && !stalker.isHoldingMaevePosition(),
                    "Actual entity physics must turn and move away, not just change a slot counter: " + stalker.position());
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().stream().allMatch(s -> s.kind().equals("MASTER_ENCOUNTER")),
                    "Pressure holds both shared slots, while Master combat stays protected");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, player.getUUID()).stream().anyMatch(s -> s.contains("EVICTED") && s.contains("PASSIVE_TRACKING")),
                    "Operator output explains the dropped concern and replacement");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(!stalker.isMaeveDisengaging() && !witness.isMaeveDisengaging(), "Erasure clears already-evicted local orders immediately");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty()
                    && MaeveDirector.attentionSnapshot(scene.server).events().isEmpty(), "Erasure retains no attention archive");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionReleasesHeldCommitmentAndPreservesBeliefs(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 19, scene -> {
            scene.phase.setPresetName("cinematic"); var actor = scene.architect(2, 4); var player = scene.player("focus_hold", 8, 4);
            scene.roof(true); long start = clock(scene);
            for (int i = 0; i < 5; i++) { scene.clock(start + i * 640); player.finish(scene.potion()); }
            long now = start + 5 * 640;
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            for (int i = 0; i < 120; i++) { scene.clock(now + i); actor.tick(); }
            var held = MaeveDirector.positionDirective(actor);
            helper.assertTrue(held != null && held.arrivedAt() >= 0, "Fixture actually reaches and holds a historical commitment");
            var belief = scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.RECOVERY)).findFirst().orElseThrow();
            var first = provoke(scene, player, 7, 7); see(scene, first, player, now + 120);
            var second = provoke(scene, player, 9, 7); see(scene, second, player, now + 120);
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null && actor.isMaeveDisengaging() && !actor.isHoldingMaevePosition(),
                    "Eviction must interrupt the actual held position immediately");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("ATTENTION_EVICTED"), "Release has an explicit cause");
            var after = scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.RECOVERY)).findFirst().orElseThrow();
            helper.assertTrue(after.confidence() == belief.confidence() && after.contradictions() == belief.contradictions(), "Changing focus is not evidence against the belief");
            helper.assertTrue(scene.hit(actor, player, false, 1) && !actor.isMaeveDisengaging(), "New local damage restores self-defense without a focus slot");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionRejectsHiddenPressureAndProtectsMasters(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 20, scene -> {
            scene.phase.setPresetName("cinematic"); var player = scene.player("focus_hidden", 8, 4);
            var hidden = scene.architect(2, 4); scene.wall(true); long now = clock(scene);
            see(scene, hidden, player, now);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "An omniscient target pointer cannot create attention");
            scene.wall(false);
            var first = provoke(scene, player, 7, 7); see(scene, first, player, now);
            var second = provoke(scene, player, 9, 7); see(scene, second, player, now);
            var third = provoke(scene, player, 3, 7); see(scene, third, player, now + 200);
            helper.assertTrue(third.isMasterFightActive(), "A full budget never delays local boss provocation");
            var view = MaeveDirector.attentionSnapshot(scene.server);
            helper.assertTrue(view.slots().size() == 2 && view.events().stream().noneMatch(e -> e.contains("EVICTED")), "Protected Masters cannot be displaced or exceed the cap");
            first.discard(); scene.clock(now + 220); MaeveDirector.tick(scene.server); see(scene, third, player, now + 220);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().stream().anyMatch(s -> s.subject().equals(third.getUUID())),
                    "Completing a concern frees its slot for a waiting local encounter");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionSharesBudgetAndClearsAcrossReloadAndReset(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 21, scene -> {
            scene.phase.setPresetName("default"); long now = clock(scene);
            for (int i = 0; i < 3; i++) see(scene, scene.architect(2, 2 + i), scene.player("focus_" + i, 8, 2 + i), now);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 3, "Three players share one Normal budget");
            var newcomer = scene.player("focus_new", 8, 7); see(scene, scene.architect(2, 7), newcomer, now + 100);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 3, "A fourth player's concern evicts rather than adding a per-player budget");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            helper.assertTrue(!saved.toString().contains("PASSIVE_TRACKING"), "Transient attention is not a permanent world archive");
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "Server reload cannot resurrect stale executors");
            PostMaeveWorldState.setForDebug(scene.server, true); PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).events().isEmpty(), "Debug reversal starts with empty attention state");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveAttentionDoorReplayExercisesRealMasterAndStalkerAi(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 22, scene -> {
            for (String name : java.util.List.of("setup", "control", "tactic", "start", "prompt", "finish")) {
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("maeve_attention:" + name)).isPresent(),
                        "Native parser must accept the live replay function: " + name);
            }
            scene.phase.setPresetName("cinematic"); long now = clock(scene);
            for (int x = -12; x <= 30; x++) for (int z = 0; z <= 20; z++) scene.block(x, -1, z, Blocks.BEDROCK.defaultBlockState());
            var player = scene.player("focus_door", -6, 10); player.setInvulnerable(true);
            scene.hearths.setRelationshipForDebug(player.getUUID(), ReturnedHearthSavedData.HiveRelationship.ORSATHAE, now);
            var stalker = scene.architect(20, 10);
            var first = scene.architect(-6, 18); first.bindToHearthMasterArchitect(UUID.randomUUID(), first.blockPosition(), 0);
            var second = scene.architect(-6, 6); second.bindToHearthMasterArchitect(UUID.randomUUID(), second.blockPosition(), 0);
            for (int x = -7; x <= -5; x++) for (int z = 17; z <= 19; z++) for (int y = 0; y < 4; y++) scene.block(x, y, z, Blocks.BEDROCK.defaultBlockState());
            scene.block(-6, 0, 18, Blocks.AIR.defaultBlockState()); scene.block(-6, 1, 18, Blocks.AIR.defaultBlockState()); scene.block(-6, 1, 17, Blocks.AIR.defaultBlockState());
            for (int x = -7; x <= -5; x++) for (int z = 5; z <= 8; z++) for (int y = 0; y < 4; y++) scene.block(x, y, z, Blocks.BEDROCK.defaultBlockState());
            scene.block(-6, 0, 6, Blocks.AIR.defaultBlockState()); scene.block(-6, 1, 6, Blocks.AIR.defaultBlockState()); scene.block(-6, 1, 7, Blocks.AIR.defaultBlockState());
            var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
            scene.block(-6, 0, 8, door);
            scene.block(-6, 1, 8, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            for (int i = 0; i < 150; i++) {
                scene.clock(now + i);
                for (var actor : java.util.List.of(first, second, stalker)) { actor.tickCount++; actor.tick(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor)); }
                MaeveDirector.tick(scene.server);
            }
            var before = MaeveDirector.attentionSnapshot(scene.server);
            helper.assertTrue(before.slots().size() == 2 && before.slots().stream().anyMatch(s -> s.kind().equals("PASSIVE_TRACKING")),
                    "Real control round must fill one tracking and one boss slot before opening the door: " + before);
            helper.assertTrue(!stalker.isMaeveDisengaging(), "Closed door cannot produce an eviction");
            var initial = stalker.position();
            ((net.minecraft.world.level.block.DoorBlock) Blocks.OAK_DOOR).setOpen(player, scene.level, door, scene.origin.offset(-6, 0, 8), true);
            for (int i = 150; i < 240; i++) {
                scene.clock(now + i);
                for (var actor : java.util.List.of(first, second, stalker)) { actor.tickCount++; actor.tick(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor)); }
                MaeveDirector.tick(scene.server);
            }
            helper.assertTrue(stalker.isMaeveDisengaging() && stalker.getX() > initial.x + 3,
                    "Opening the real door must expose new pressure and physically shed the real stalker: " + MaeveDirector.attentionSnapshot(scene.server));
            helper.assertTrue(first.isMasterFightActive() && second.isMasterFightActive(), "Both local Master controllers remain active");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(scene.hearths.relationship(player.getUUID()) == ReturnedHearthSavedData.HiveRelationship.ORSATHAE,
                    "Attention erasure never removes the separate permanent violation memory");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionHonorsActivationLatchAndExcludedObservers(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 23, scene -> {
            var player = scene.player("focus_lifecycle", 8, 4); var actor = scene.architect(2, 4); long now = clock(scene);
            scene.phase.setApocalypseTicks(0, scene.server); scene.storage(new MaeveSavedData());
            see(scene, actor, player, now);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "Dormant worlds have no focus concerns");
            scene.phase.setApocalypseTicks(scene.phase.getTotalDays() * 24000L, scene.server);
            see(scene, actor, player, now + 20);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 1, "Late Phase 6 activates focus");
            scene.phase.setApocalypseTicks(0, scene.server); see(scene, actor, player, now + 40);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 1, "Reversing the phase does not put Maeve to sleep");
            actor.setNoAi(true); scene.clock(now + 60); MaeveDirector.tick(scene.server);
            var copy = scene.architect(3, 4); copy.initializeMasterMindCopy(UUID.randomUUID(), 100, 100, 0);
            see(scene, actor, player, now + 80); see(scene, copy, player, now + 80);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "NoAI actors and mind copies cannot claim or retain focus");
        });
    }
}
