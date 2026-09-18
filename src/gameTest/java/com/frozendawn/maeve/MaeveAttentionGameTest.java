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
            var other = scene.player("pressure_other", 13, 8); var newcomer = scene.player("pressure_new", 15, 8);
            var first = scene.architect(12, 8); see(scene, first, other, now + 20);
            var second = scene.architect(14, 8); see(scene, second, newcomer, now + 80);
            helper.assertTrue(!stalker.isMaeveDisengaging(), "Fresh concerns cannot be dropped before minimum dwell");
            see(scene, second, newcomer, now + 100);
            helper.assertTrue(stalker.isMaeveDisengaging() && witness.isMaeveDisengaging(), "Eviction releases every executor of the concern");
            var start = stalker.position(); stalker.tickCount = 80; stalker.setOnGround(true); stalker.setDeltaMovement(Vec3.ZERO);
            for (int i = 1; i <= 80; i++) { scene.clock(now + 100 + i); stalker.tick(); }
            helper.assertTrue(stalker.getX() < start.x - 3 && stalker.getTarget() == null && !stalker.isHoldingMaevePosition(),
                    "Actual entity physics must turn and move away, not just change a slot counter: " + stalker.position());
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().stream().noneMatch(slot -> slot.subject().equals(player.getUUID())),
                    "Other observed players hold the shared slots after the original tracking concern leaves");
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
            var other = scene.player("hold_other", 7, 4); var newcomer = scene.player("hold_new", 9, 4);
            scene.roof(true); long start = clock(scene);
            for (int i = 0; i < 5; i++) {
                scene.clock(start + i * 640); player.finish(scene.potion()); other.finish(scene.potion()); newcomer.finish(scene.potion());
            }
            other.setPos(scene.position(7, 7)); newcomer.setPos(scene.position(9, 7));
            long now = start + 5 * 640;
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            for (int i = 0; i < 120; i++) { scene.clock(now + i); actor.tick(); }
            var held = MaeveDirector.positionDirective(actor);
            helper.assertTrue(held != null && held.arrivedAt() >= 0, "Fixture actually reaches and holds a historical commitment");
            var belief = scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.RECOVERY)).findFirst().orElseThrow();
            var first = scene.architect(7, 8); see(scene, first, other, now + 120);
            var second = scene.architect(9, 8); see(scene, second, newcomer, now + 120);
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null && actor.isHoldingMaevePosition()
                    && !actor.isMaeveDisengaging(), "New passive tracking cannot interrupt a mature commitment");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).events().stream().anyMatch(e -> e.contains("HIGHER_PRIORITY_FOCUSED")),
                    "Diagnostics explain the lower-priority deferral");
            helper.assertTrue(MaeveDirector.chooseCommitment(first, other, java.util.List.of(
                    new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY, first.blockPosition(), null, 0))), "The sole tracker promotes to a real commitment");
            helper.assertTrue(MaeveDirector.chooseCommitment(second, newcomer, java.util.List.of(
                    new MaeveDirector.PositionCandidate(BeliefStore.RECOVERY, second.blockPosition(), null, 0))), "Equal-priority commitment can displace the mature hold");
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null && actor.isMaeveDisengaging() && !actor.isHoldingMaevePosition(),
                    "Eviction must interrupt the actual held position immediately");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).outcome().equals("ATTENTION_EVICTED"), "Release has an explicit cause");
            var after = scene.beliefs(player).stream().filter(b -> b.pattern().equals(BeliefStore.RECOVERY)).findFirst().orElseThrow();
            helper.assertTrue(after.confidence() == belief.confidence() && after.contradictions() == belief.contradictions(), "Changing focus is not evidence against the belief");
            helper.assertTrue(scene.hit(actor, player, false, 1) && !actor.isMaeveDisengaging(), "New local damage restores self-defense without a focus slot");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAttentionIgnoresMastersAndRejectsHiddenPressure(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 20, scene -> {
            scene.phase.setPresetName("cinematic"); var player = scene.player("focus_hidden", 8, 4);
            var hidden = scene.architect(2, 4); scene.wall(true); long now = clock(scene);
            see(scene, hidden, player, now);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().isEmpty(), "An omniscient target pointer cannot create attention");
            scene.wall(false); see(scene, hidden, player, now);
            var other = scene.player("focus_other", 8, 8); var second = scene.architect(3, 8); see(scene, second, other, now);
            var before = MaeveDirector.attentionSnapshot(scene.server);
            var master = provoke(scene, player, 7, 7); see(scene, master, player, now + 200);
            helper.assertTrue(master.isMasterFightActive(), "The guardian's local provocation must remain active");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).events().equals(before.events())
                            && MaeveDirector.attentionSnapshot(scene.server).slots().stream().map(MaeveDirector.FocusSnapshot::subject).toList()
                            .equals(before.slots().stream().map(MaeveDirector.FocusSnapshot::subject).toList()),
                    "A real Master encounter cannot consume, refresh, defer or evict a focus slot");
            master.beginMaeveDisengagement(player.getUUID(), player.blockPosition(), "PASSIVE_TRACKING");
            helper.assertTrue(!master.isMaeveDisengaging() && master.getTarget() == player,
                    "Even a direct eviction order cannot take over the guardian's local combat");
            helper.assertTrue(MaeveDirector.commitmentHints(master, player).isEmpty()
                    && MaeveDirector.positionDirective(master) == null
                    && MaeveDirector.knownDangers(master, player.getUUID()).isEmpty()
                    && MaeveDirector.utilityBias(master, player).equals(MaeveDirector.UtilityBias.NONE),
                    "Masters receive no Maeve beliefs, directives or utility bias");
            hidden.discard(); scene.clock(now + 220); MaeveDirector.tick(scene.server);
            var newcomer = scene.player("focus_new", 9, 7); see(scene, scene.architect(9, 8), newcomer, now + 220);
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().stream().anyMatch(slot -> slot.subject().equals(newcomer.getUUID())),
                    "Completed ordinary work frees capacity for another ordinary concern");
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
    public static void maeveAttentionOrdinaryPressureExercisesRealStalkerAi(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 22, scene -> {
            for (String name : java.util.List.of("setup", "control", "tactic", "cleanup")) {
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("maeve_attention:" + name)).isPresent(),
                        "Native parser must accept the retired replay's safe cleanup command: " + name);
            }
            scene.phase.setPresetName("cinematic"); long now = clock(scene);
            for (int x = -12; x <= 30; x++) for (int z = 0; z <= 20; z++) scene.block(x, -1, z, Blocks.BEDROCK.defaultBlockState());
            var player = scene.player("focus_departure", -6, 10);
            var stalker = scene.architect(20, 10);
            for (int i = 0; i < 150; i++) {
                scene.clock(now + i); stalker.tickCount++; stalker.tick();
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(stalker)); MaeveDirector.tick(scene.server);
            }
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 1 && !stalker.isMaeveDisengaging(),
                    "A real ordinary observer claims one tracking slot without losing focus on its own");
            var other = scene.player("pressure_elsewhere", -6, 2); var first = scene.architect(-8, 2);
            var newcomer = scene.player("pressure_newcomer", -6, 18); var second = scene.architect(-8, 18);
            see(scene, first, other, now + 160); see(scene, second, newcomer, now + 160);
            var initial = stalker.position();
            for (int i = 160; i < 240; i++) {
                scene.clock(now + i); stalker.tickCount++; stalker.tick();
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(stalker)); MaeveDirector.tick(scene.server);
            }
            helper.assertTrue(stalker.isMaeveDisengaging() && stalker.getX() > initial.x + 3,
                    "Ordinary locally observed pressure must visibly move the stalker away through real entity AI");
            helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).slots().size() == 2,
                    "The two new ordinary concerns occupy the shared Cinematic budget");
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
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveRetiredAttentionReplayRemovesOnlyItsActors(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 24, scene -> {
            var player = scene.player("retired_replay", 8, 4);
            var retired = scene.architect(3, 3);
            retired.bindToHearthMasterArchitect(UUID.randomUUID(), retired.blockPosition(), 0);
            retired.setHealth(20); retired.addTag("maeve_attention_actor");
            var guardian = scene.architect(3, 7);
            guardian.bindToHearthMasterArchitect(UUID.randomUUID(), guardian.blockPosition(), 0);
            float health = guardian.getHealth();
            scene.server.getCommands().performPrefixedCommand(scene.server.createCommandSourceStack()
                    .withEntity(player).withPosition(player.position()).withPermission(4), "function maeve_attention:cleanup");
            helper.assertTrue(!retired.isAlive() && !retired.isMasterArchitectVisual(),
                    "Native cleanup removes the QA boss binding before kill, avoiding its survival phase");
            helper.assertTrue(guardian.isAlive() && guardian.isMasterArchitectVisual() && guardian.getHealth() == health,
                    "Cleanup never changes an untagged guardian");
        });
    }

}
