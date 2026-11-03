package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.world.HeaterRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveWorldModelGameTest {
    private static final String EAST = "RETREAT_BEARING_E";

    static void shelter(MaeveObservationGameTest.Scene scene) {
        for (int x = 0; x <= 20; x++) for (int z = 0; z <= 12; z++) scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
        for (int x = 2; x <= 5; x++) for (int z = 3; z <= 7; z++) scene.block(x, 4, z, Blocks.STONE.defaultBlockState());
        scene.settleLight();
    }

    static void sample(MaeveObservationGameTest.Scene scene, ArchitectEntity actor,
                       MaeveObservationGameTest.TestPlayer player, long time, int x, int z) {
        scene.clock(time); player.setPos(scene.position(x, z)); actor.setTarget(player);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));
    }

    static long train(MaeveObservationGameTest.Scene scene, ArchitectEntity actor, MaeveObservationGameTest.TestPlayer player) {
        long start = (scene.gameTime / 10 + 1) * 10;
        for (int i = 0; i < 5; i++) {
            long time = start + i * 640L;
            sample(scene, actor, player, time, 3, 5);
            sample(scene, actor, player, time + 10, 4, 5);
            sample(scene, actor, player, time + 20, 6, 5);
        }
        return start + 5 * 640L;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveSpatialHooksRequireContinuousVisibleCrossing(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 10, scene -> {
            shelter(scene); var actor = scene.architect(10, 5); var other = scene.architect(10, 6);
            var player = scene.player("spatial_seen", 3, 5); var hidden = scene.player("spatial_hidden", 3, 5);
            long now = (scene.gameTime / 10 + 1) * 10;
            helper.assertTrue(!scene.level.canSeeSky(player.blockPosition()), "Fixture starts beneath the real roof");
            sample(scene, actor, player, now, 4, 5); sample(scene, other, player, now, 4, 5);
            sample(scene, actor, player, now + 10, 6, 5); sample(scene, other, player, now + 10, 6, 5);
            var belief = scene.beliefs(player).stream().filter(b -> b.pattern().equals(EAST)).findFirst().orElseThrow();
            helper.assertTrue(belief.evidence() == 1 && belief.confidence() == .2, "Duplicate witnesses share one contribution");
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).size() == 1, "Crossing produces a real access point");
            // Missing a sample through target loss, a long teleport, and an occluded endpoint cannot establish continuity.
            sample(scene, actor, hidden, now + 20, 4, 5);
            sample(scene, actor, hidden, now + 40, 6, 5);
            sample(scene, actor, hidden, now + 50, 3, 5);
            sample(scene, actor, hidden, now + 60, 12, 5);
            sample(scene, actor, hidden, now + 70, 4, 5);
            scene.wall(true); sample(scene, actor, hidden, now + 80, 6, 5); scene.wall(false);
            helper.assertTrue(scene.beliefs(hidden).isEmpty(), "Invisible and discontinuous crossings never become player history");
            hidden.setGameMode(GameType.CREATIVE);
            sample(scene, actor, hidden, now + 90, 4, 5); sample(scene, actor, hidden, now + 100, 6, 5);
            helper.assertTrue(scene.beliefs(hidden).isEmpty(), "Creative observations stay excluded");
            hidden.setGameMode(GameType.SURVIVAL);
            sample(scene, actor, hidden, now + 110, 4, 5);
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            sample(scene, actor, hidden, now + 120, 6, 5);
            helper.assertTrue(scene.beliefs(hidden).isEmpty(), "Reload never joins transient movement samples");
            helper.assertTrue(scene.beliefs(player).size() == 1, "Another player's history survives reload independently");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveAccessCommitmentPhysicallyReachesWitnessedEntrance(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 11, scene -> {
            shelter(scene); var actor = scene.architect(10, 5); var player = scene.player("spatial_walk", 3, 5);
            long now = train(scene, actor, player);
            helper.assertTrue(scene.beliefs(player).getFirst().evidence() == 5, "Five witnessed outward crossings train the bearing");
            actor.setPos(scene.position(16, 5)); player.setPos(scene.position(14, 10));
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player); actor.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 200; i++) { scene.clock(now + i); actor.tick(); }
            var directive = MaeveDirector.positionDirective(actor);
            helper.assertTrue(directive != null && directive.spatial() != null && directive.arrivedAt() > directive.startedAt(),
                    "Actual entity physics must reach the remembered entrance: " + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(directive.position().equals(scene.origin.offset(6, 0, 5)) && actor.blockPosition().distSqr(directive.position()) <= 1,
                    "Held location is the real witnessed crossing, not current player coordinates");
            helper.assertTrue(actor.isHoldingMaevePosition(), "The reached access point has the visible thinking cue");
            var saved = MaeveSavedData.get(scene.server).save(new CompoundTag(), scene.level.registryAccess());
            scene.storage(MaeveSavedData.load(saved, scene.level.registryAccess()));
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null, "Reload releases execution");
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst().state().equals("OPEN"), "Reload retains observed access knowledge");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).isEmpty(), "Erasure releases spatial knowledge immediately");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).isEmpty(), "Reset cannot recover the old world model");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveSealedAccessChangesOnlyOnVisibleDiscovery(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 12, scene -> {
            shelter(scene); var actor = scene.architect(10, 5); var player = scene.player("spatial_seal", 3, 5);
            long now = train(scene, actor, player);
            actor.setPos(scene.position(16, 5)); player.setPos(scene.position(14, 10));
            for (int y = 0; y < 3; y++) for (int z = 3; z <= 7; z++) scene.block(5, y, z, Blocks.STONE.defaultBlockState());
            helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst().state().equals("OPEN"),
                    "Sealing an unseen side does not invalidate the remembered crossing");
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player); actor.setDeltaMovement(Vec3.ZERO);
            MaeveDirector.PositionDirective discovered = null;
            for (int i = 0; i < 220; i++) {
                scene.clock(now + i); actor.tick();
                var current = MaeveDirector.positionDirective(actor);
                if (current != null && current.obstruction() != null) { discovered = current; break; }
            }
            helper.assertTrue(discovered != null, "Walking toward the stale access point must expose the actual obstruction: "
                    + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(point.state().equals("BLOCKED") && point.confidence() == .9 && point.contradictions() == 1,
                    "Discovery replaces the open hypothesis with fresh blocked evidence");
            helper.assertTrue(Math.abs(scene.beliefs(player).getFirst().confidence() - .35) < .001, "Direct disproof sharply reduces the old bearing confidence");
            helper.assertTrue(point.provenance().stream().anyMatch(p -> p.action().equals("OBSERVED_ACCESS_OBSTRUCTION")), "The found wall is explainable");
            helper.assertTrue(actor.isHoldingMaevePosition(), "Discovery shows a deliberate inspection beat");
            long deadline = discovered.contradictedAt() + 60;
            scene.clock(deadline - 1); actor.tick();
            helper.assertTrue(MaeveDirector.positionDirective(actor) != null, "Inspection holds for its full bounded beat");
            scene.clock(deadline); actor.tick();
            helper.assertTrue(MaeveDirector.positionDirective(actor) == null && !actor.isHoldingMaevePosition(), "Discovery then visibly releases into local replanning");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).blockNext().contains(EAST), "Disproved bearing is cautious next encounter");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveLocalHeatAndDamageReportsRetainTheirProvenance(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 13, scene -> {
            var actor = scene.architect(2, 4); var player = scene.player("spatial_hazards", 3, 4);
            BlockPos visible = scene.origin.offset(3, 0, 7), hidden = scene.origin.offset(8, 0, 4);
            HeaterRegistry.register(scene.level, visible); HeaterRegistry.register(scene.level, hidden);
            try {
                scene.wall(true); long now = (scene.gameTime / 20 + 1) * 20;
                sample(scene, actor, player, now, 3, 4);
                var heat = MaeveDirector.worldSnapshot(scene.server, player.getUUID());
                helper.assertTrue(heat.size() == 1 && heat.getFirst().position().equals(visible), "Only the locally visible registered heater is reported");
                scene.hit(actor, player, false, actor.getMaxHealth() * .25F);
                helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream().anyMatch(p -> p.label().equals("DANGER_ZONE")), "Heavy actual damage records a danger point");
                var fatal = scene.architect(2, 7); fatal.setTarget(player); scene.hit(fatal, player, false, 10000);
                helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream().flatMap(p -> p.provenance().stream())
                        .anyMatch(p -> p.action().equals("ARCHITECT_FATAL_DAMAGE")), "Fatal evidence reaches spatial memory before death cleanup");
                HeaterRegistry.unregister(scene.level, visible);
                helper.assertTrue(MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream().anyMatch(p -> p.label().equals("HEAT_SOURCE")), "An unseen registry removal cannot erase observed heat memory");
            } finally { HeaterRegistry.unregister(scene.level, visible); HeaterRegistry.unregister(scene.level, hidden); }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveRememberedDangerChangesBoundedWalkingRoute(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 14, scene -> {
            shelter(scene); BlockPos start = scene.origin.offset(2, 0, 9), goal = scene.origin.offset(18, 0, 9), danger = scene.origin.offset(10, 0, 9);
            var normal = route(scene, start, goal, List.of()); var careful = route(scene, start, goal, List.of(danger));
            helper.assertTrue(normal.stream().anyMatch(p -> p.distSqr(danger) <= 1), "The ordinary shortest route crosses the hazard fixture");
            helper.assertTrue(careful.stream().noneMatch(p -> p.distSqr(danger) <= 9), "Remembered danger produces a recoverable walking detour");
            helper.assertTrue(careful.size() < 50, "The detour remains local and bounded");
            int chunks = scene.level.getChunkSource().getLoadedChunksCount();
            var remote = new DStarLitePathfinder(); remote.configureObservedWalk(List.of());
            remote.initialize(scene.origin.offset(1000, 0, 0), start, scene.level); remote.computePartial(80, scene.level);
            helper.assertTrue(chunks == scene.level.getChunkSource().getLoadedChunksCount(), "An unloaded target never loads chunks to seek a route");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveShelterReplayCommandsAndWitnessBoothAreValid(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 15, scene -> {
            for (String name : List.of("setup", "practice_start", "crossed", "gap", "ready", "encounter", "finish", "seal_prepare", "blocked")) {
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("maeve_world:" + name)).isPresent(),
                        "Native command parser must load the generated replay function: " + name);
            }
            shelter(scene);
            for (int x = 2; x <= 7; x++) for (int z = 3; z <= 7; z++) scene.block(x, 4, z, Blocks.STONE.defaultBlockState());
            for (int x = 19; x <= 21; x++) for (int y = 0; y <= 3; y++) for (int z = 4; z <= 6; z++) scene.block(x, y, z, Blocks.BEDROCK.defaultBlockState());
            scene.block(20, 0, 5, Blocks.AIR.defaultBlockState()); scene.block(20, 1, 5, Blocks.AIR.defaultBlockState()); scene.block(19, 1, 5, Blocks.AIR.defaultBlockState());
            for (int y = 0; y <= 3; y++) for (int z = 3; z <= 7; z++) {
                if (y == 3 || z == 3 || z == 7) scene.block(7, y, z, Blocks.OAK_PLANKS.defaultBlockState());
            }
            scene.settleLight();
            var actor = scene.architect(20, 5); var player = scene.player("spatial_booth", 6, 5);
            long now = (scene.gameTime / 10 + 1) * 10;
            sample(scene, actor, player, now, 6, 5); sample(scene, actor, player, now + 10, 8, 5);
            helper.assertTrue(scene.beliefs(player).stream().anyMatch(b -> b.pattern().equals(EAST) && b.evidence() == 1),
                    "The protected practice booth must really witness both sides through its eye-height opening");
        });
    }

    private static List<BlockPos> route(MaeveObservationGameTest.Scene scene, BlockPos start, BlockPos goal, List<BlockPos> dangers) {
        var path = new DStarLitePathfinder(); path.configureObservedWalk(dangers); path.initialize(goal, start, scene.level);
        var points = new java.util.ArrayList<BlockPos>(); var current = start;
        for (int i = 0; i < 100 && !current.equals(goal); i++) {
            path.updateStart(current);
            if (!path.computePartial(80, scene.level)) continue;
            var step = path.getNextStep(current, scene.level);
            if (step == null || step.type() != DStarLitePathfinder.StepType.WALK) throw new AssertionError("No local walk: " + step);
            current = step.pos(); points.add(current);
        }
        if (!current.equals(goal)) throw new AssertionError("Walking route never reached its goal");
        return points;
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveSealedDestinationGetsReachableInspection(GameTestHelper helper) {
        inspectUnreachable(helper, 16, true);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void maeveOppositeSideGetsReachableInspection(GameTestHelper helper) {
        inspectUnreachable(helper, 17, false);
    }

    private static void inspectUnreachable(GameTestHelper helper, int lane, boolean sealDestination) {
        MaeveObservationGameTest.withScene(helper, lane, scene -> {
            shelter(scene); var actor = scene.architect(10, 5); var player = scene.player("inspection_" + lane, 3, 5);
            long now = train(scene, actor, player);
            int wall = sealDestination ? 6 : 5;
            for (int y = 0; y < 3; y++) for (int z = 0; z <= 12; z++) scene.block(wall, y, z, Blocks.STONE.defaultBlockState());
            actor.setPos(scene.position(sealDestination ? 16 : 0, 5));
            player.setPos(scene.position(sealDestination ? 14 : 2, 10));
            helper.assertTrue(actor.blockPosition().distSqr(scene.origin.offset(6, 0, 5)) > 16, "Start beyond discovery range");
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player); actor.setDeltaMovement(Vec3.ZERO);
            int chunks = scene.level.getChunkSource().getLoadedChunksCount();
            MaeveDirector.PositionDirective discovered = null;
            for (int i = 0; i < 240; i++) {
                scene.clock(now + i); actor.tick();
                var current = MaeveDirector.positionDirective(actor);
                if (current != null && current.obstruction() != null) { discovered = current; break; }
            }
            helper.assertTrue(discovered != null, "Unreachable exact goal must get a reachable inspection vantage: "
                    + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()));
            helper.assertTrue(actor.blockPosition().distSqr(discovered.position()) <= 16, "Actual actor reaches discovery range");
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).getFirst();
            helper.assertTrue(point.state().equals("BLOCKED") && point.contradictions() == 1, "Real inspection revises stale knowledge");
            helper.assertTrue(point.provenance().stream().anyMatch(p -> p.observer().equals(actor.getUUID())
                    && p.action().equals("OBSERVED_ACCESS_OBSTRUCTION")), "Actual observer reports the wall");
            helper.assertTrue(MaeveDirector.commitmentSnapshot(scene.server, player.getUUID()).blockNext().contains(EAST), "Discovery schedules cooldown");
            helper.assertTrue(chunks == scene.level.getChunkSource().getLoadedChunksCount(), "Inspection loads no chunks");
            helper.assertTrue(scene.level.getBlockState(scene.origin.offset(wall, 0, 5)).is(Blocks.STONE), "Inspection does not breach the seal");
        });
    }
}
