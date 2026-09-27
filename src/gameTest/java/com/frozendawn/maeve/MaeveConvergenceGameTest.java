package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveConvergenceGameTest {
    private static void scene(GameTestHelper h, int lane, Consumer<MaeveObservationGameTest.Scene> exercise) {
        MaeveObservationGameTest.withScene(h, lane, 4, s -> {
            boolean enabled = FrozenDawnConfig.ENABLE_ARCHITECT.get(); FrozenDawnConfig.ENABLE_ARCHITECT.set(true);
            try { exercise.accept(s); } finally { FrozenDawnConfig.ENABLE_ARCHITECT.set(enabled); }
        });
    }
    private static ConvergenceMemory memory(MaeveObservationGameTest.Scene s) { MaeveDirector.snapshot(s.server, null); return MaeveSavedData.get(s.server).convergence(); }
    private static long start(MaeveObservationGameTest.Scene s) { return (s.gameTime / 20 + 1) * 20; }
    private static void kill(MaeveObservationGameTest.Scene s, ArchitectEntity a) {
        a.invulnerableTime = 0; a.hurt(s.level.damageSources().genericKill(), 10000);
    }
    private static Hotspot history(MaeveObservationGameTest.Scene s) {
        long now = start(s);
        for (int episode = 0; episode < 3; episode++) {
            s.clock(now + episode * 640); kill(s, s.architect(2, 4)); kill(s, s.architect(3, 4));
        }
        s.clock(now + 1880); var m = memory(s); m.hotspots.values().forEach(h -> h.advance(now + 1880)); return m.hotspots.values().iterator().next();
    }
    private static ArchitectEntity donor(MaeveObservationGameTest.Scene s, int x, int z) {
        var a = s.architect(x, z); var standing = com.frozendawn.entity.architect.ArchitectWalkGeometry.observedStandingPosition(s.level, a.blockPosition()); if (standing != null) a.setPos(standing); a.tickCount = 80; a.setOnGround(true); MaeveDirector.observePawn(a); return a;
    }
    private static void floor(MaeveObservationGameTest.Scene s, boolean snow) {
        for (int x = -46; x <= 50; x++) for (int z = 0; z <= 8; z++) {
            s.block(x, -1, z, Blocks.STONE.defaultBlockState());
            if (snow) s.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(x / 3, 3)));
        }
    }
    private static void tick(MaeveObservationGameTest.Scene s, long now, ArchitectEntity... actors) {
        s.clock(now);
        for (var a : actors) { if (!a.isRemoved() && a.isAlive()) { a.tick(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(a)); } }
        MaeveDirector.tick(s.server);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnReplayFunctionsParseAtClientPermission(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var resources = server.getResourceManager().listResources("function", id -> id.getNamespace().equals("macs_pawn") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(resources.size() == 14, "The complete convergence replay must be registered: " + resources.size());
        resources.forEach((file, resource) -> {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("macs_pawn", file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function exists: " + id);
            try (var reader = resource.openAsReader()) {
                net.minecraft.commands.functions.CommandFunction.fromLines(id, server.getCommands().getDispatcher(), server.createCommandSourceStack().withPermission(2), reader.lines().toList());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnTwoRealWipesAvoidRegionAndReleaseOnDecay(GameTestHelper h) {
        scene(h, 102, s -> {
            floor(s, false); var hotspot = history(s); long now = s.level.getGameTime();
            for (int i = 0; i < 2; i++) {
                s.clock(now + i * 12000); var first = donor(s, -38, 4); var second = donor(s, 42, 4);
                MaeveDirector.tick(s.server); h.assertTrue(memory(s).active != null, "Each failure has a real admitted roster");
                kill(s, first); kill(s, second);
            }
            h.assertTrue(hotspot.avoid && hotspot.wipes == 2 && hotspot.deaths == 6, "Two confirmed pre-contact group wipes select avoidance without feeding it");
            h.assertTrue(!MaeveDirector.allowNaturalPawn(s.level, hotspot.anchor), "The avoided region cannot receive normal replacement pressure");
            var idle = donor(s, 10, 4); s.clock(now + 12020); MaeveDirector.tick(s.server);
            h.assertTrue(idle.isMaeveDisengaging() && MaeveDirector.knownDangers(idle, UUID.randomUUID()).contains(hotspot.anchor), "An idle ordinary pawn leaves and existing danger-aware walking sees the region");
            var origin = idle.position(); for (int t = 1; t <= 60; t++) { s.clock(now + 12020 + t); idle.tick(); }
            h.assertTrue(idle.position().distanceToSqr(origin) > 4, "Avoidance visibly moves the pawn away");
            s.clock(now + 36000); MaeveDirector.tick(s.server);
            h.assertTrue(!hotspot.avoid && hotspot.wipes == 0 && hotspot.cycle == 1 && hotspot.deaths == 6 && hotspot.encounters == 3,
                    "Quiet decay clears the failure bias while retaining lifetime history");
            h.assertTrue(memory(s).active == null && MaeveDirector.allowNaturalPawn(s.level, hotspot.anchor), "A dormant site can admit ordinary pressure again but stale history cannot dispatch");
            var before = MaeveSavedData.get(s.server).save(new CompoundTag(), s.level.registryAccess());
            MaeveDirector.diagnostics(s.server, null); MaeveDirector.diagnostics(s.server, UUID.randomUUID());
            h.assertTrue(before.equals(MaeveSavedData.get(s.server).save(new CompoundTag(), s.level.registryAccess())), "Reading convergence explanations must not mutate the save");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnDeathsUseFinalDeathAndExcludeIndependentRoles(GameTestHelper h) {
        scene(h, 96, s -> {
            var m = memory(s); var canceled = s.architect(2, 4);
            Consumer<LivingDeathEvent> cancel = event -> { if (event.getEntity() == canceled) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, cancel);
            try { kill(s, canceled); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(m.hotspots.isEmpty(), "A canceled LivingDeathEvent cannot report a confirmed death");
            var master = s.architect(2, 4); master.bindToHearthMasterArchitect(UUID.randomUUID(), s.origin, 0);
            var copy = s.architect(2, 4); copy.initializeMasterMindCopy(UUID.randomUUID(), 100, 100, 0);
            var child = s.architect(2, 4); child.getPersistentData().putBoolean(AggregateReinforcementManager.CHILD_TAG, true);
            var assessor = s.architect(2, 4); assessor.bindToHearthAssessor(UUID.randomUUID(), s.origin, 0);
            var resident = s.architect(2, 4); resident.bindToHearthPopulation(UUID.randomUUID(), s.origin, 0);
            for (var a : List.of(master, copy, child, assessor, resident)) { kill(s, a); MaeveDirector.observePawnDeath(a); }
            h.assertTrue(m.hotspots.isEmpty(), "Excluded roles cannot create even an empty death episode");
            var heavy = s.architect(2, 4); heavy.hurt(s.level.damageSources().fall(), 2);
            h.assertTrue(m.hotspots.isEmpty(), "Nonfatal damage is not a death");
            for (var source : List.of(s.level.damageSources().fall(), s.level.damageSources().lava(), s.level.damageSources().inWall())) {
                var a = s.architect(2, 4); a.invulnerableTime = 0; a.hurt(source, 10000); MaeveDirector.observePawnDeath(a);
            }
            h.assertTrue(m.hotspots.size() == 1 && m.hotspots.values().iterator().next().deaths == 3,
                    "Real fall/lava/trap damage counts once per actual ordinary death without a player source");
            var saved = m.save().toString(); h.assertTrue(!saved.contains("lava") && !saved.contains("killer"), "Cause is never claimed as knowledge");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnEncounterGateUsesLocalSightAndCancelsResumedFight(GameTestHelper h) {
        scene(h, 97, s -> {
            var hotspot = history(s); long now = s.level.getGameTime();
            var first = donor(s, -38, 4); var second = donor(s, 42, 4);
            var witness = s.architect(3, 4); var player = s.player("pawn_contact", 8, 4);
            MaeveDirector.observePresence(witness, player); MaeveDirector.tick(s.server);
            h.assertTrue(memory(s).active == null, "A visible local fight blocks dispatch even when the history qualifies");
            s.wall(true); s.clock(now + 600); MaeveDirector.observePresence(witness, player); MaeveDirector.tick(s.server);
            h.assertTrue(memory(s).active != null, "An occluded target pointer cannot extend the completed episode");
            s.wall(false); s.clock(now + 620); MaeveDirector.observePresence(witness, player);
            h.assertTrue(memory(s).active == null, "Resumed local contact cancels synchronously, before any further pawn AI tick");
            MaeveDirector.tick(s.server);
            h.assertTrue(memory(s).active == null && first.isMaeveDisengaging() && second.isMaeveDisengaging(), "Resumed observed combat cancels the warning and disperses all pawns");
            h.assertTrue(hotspot.results.getLast().getString("outcome").equals("UNKNOWN"), "A resumed fight is inconclusive, not a defeat");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 500)
    public static void pawnWarningPrecedesSnowApproachAndLongerTravel(GameTestHelper h) {
        scene(h, 98, s -> {
            floor(s, true); var hotspot = history(s); var first = donor(s, -38, 4); var second = donor(s, 42, 6);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var group = memory(s).active;
            h.assertTrue(group != null && MaeveDirector.attentionSnapshot(s.server).slots().stream().filter(slot -> slot.kind().equals("SIEGE")).count() == 1,
                    "The frozen roster owns exactly one real siege slot");
            var initial = first.position();
            for (int i = 1; i < 240; i++) tick(s, now + i, first, second);
            h.assertTrue(first.position().subtract(initial).horizontalDistanceSqr() < .1 && group.firstArrival < 0,
                    "A nearby donor cannot advance before the complete 240 tick warning");
            for (int i = 240; i < 550; i++) tick(s, now + i, first, second);
            h.assertTrue(memory(s).active == group && group.firstArrival >= now + 240 && group.firstArrival > now + 260,
                    "Real snow walking eventually arrives; travel extends the cloud beyond its minimum: " + first.position() + " result=" + hotspot.results + " firstArrival=" + group.firstArrival);
            h.assertTrue(group.contactAt == -1, "Reaching an empty region never invents a successful player encounter");
            h.assertTrue(first.position().distanceToSqr(initial) > 30, "The actual donor moved toward the historical region");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnWipeDoesNotFeedAnyRegionAndContactResetsFailure(GameTestHelper h) {
        scene(h, 99, s -> {
            var hotspot = history(s); var first = donor(s, -38, 4); var second = donor(s, 42, 4);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var g = memory(s).active;
            h.assertTrue(g != null, "Fixture dispatched the real group"); var before = hotspot.save();
            kill(s, first); h.assertTrue(hotspot.save().equals(before), "A first casualty only updates its roster");
            second.setPos(s.position(60, 4)); kill(s, second);
            h.assertTrue(hotspot.deaths == 6 && hotspot.encounters == 3 && memory(s).hotspots.size() == 1 && hotspot.wipes == 1,
                    "A wiped group contributes one failure and no new history, even at another location");
            s.clock(now + 12000); first = donor(s, -38, 4); second = donor(s, 42, 4); MaeveDirector.tick(s.server);
            g = memory(s).active; h.assertTrue(g != null, "Fresh idle donors retry after the same production cooldown");
            s.clock(now + 12240); var player = MaeveReconnaissanceGameTest.damageablePlayer(s, "pawn_reached"); player.setPos(s.position(8, 4)); first.setPos(s.position(7, 4));
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.setYRot(90); player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            try {
                var remaining = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("useItemRemaining");
                remaining.setAccessible(true); remaining.setInt(player, player.getUseItem().getUseDuration(player) - 6);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            h.assertTrue(player.isBlocking(), "Fixture uses vanilla raised-shield state");
            float health = player.getHealth(); first.doHurtTarget(player);
            h.assertTrue(player.getHealth() == health, "The reached player's real shield blocks the hit");
            h.assertTrue(g.contactPawn != null && g.contactPlayer.equals(player.getUUID()), "Actual local attack, even blocked, records reached player evidence");
            kill(s, first); kill(s, second);
            h.assertTrue(hotspot.wipes == 0 && hotspot.results.getLast().getString("outcome").equals("SUCCESS"), "Contact followed by casualties is success, not a wipe");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnReloadUnloadAndErasureRetainExclusionWithoutRetainingOrders(GameTestHelper h) {
        scene(h, 100, s -> {
            var hotspot = history(s); var first = donor(s, -38, 4); var second = donor(s, 42, 4);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server);
            var saved = MaeveSavedData.get(s.server).save(new CompoundTag(), s.level.registryAccess());
            var entity = new CompoundTag(); first.saveWithoutId(entity);
            s.storage(MaeveSavedData.load(saved, s.level.registryAccess()));
            h.assertTrue(memory(s).active == null, "Reload cannot resume a stale dispatch");
            hotspot = memory(s).hotspots.values().iterator().next();
            h.assertTrue(hotspot.results.getLast().getString("outcome").equals("UNKNOWN"), "Reload is UNKNOWN");
            first.load(entity); s.clock(now + 1); first.tick();
            h.assertTrue(first.isMaeveDisengaging() && MaeveDirector.pawnOrder(first) == null, "A loaded pawn visibly releases its stale assignment");
            first.setPos(s.position(60, 4)); kill(s, first); kill(s, second);
            h.assertTrue(hotspot.deaths == 6 && hotspot.wipes == 0 && memory(s).hotspots.size() == 1, "Reload/dispersal cannot launder a dispatched death into evidence");
            s.clock(now + 13000); donor(s, -38, 4); donor(s, 42, 4); MaeveDirector.tick(s.server);
            var memory = memory(s); h.assertTrue(memory.active != null, "Fixture has an active dispatch before erasure");
            PostMaeveWorldState.setForDebug(s.server, true);
            h.assertTrue(memory.active == null && memory.hotspots.isEmpty() && MaeveDirector.attentionSnapshot(s.server).slots().isEmpty(), "Erasure clears history, rosters and attention synchronously");
            h.assertTrue(MaeveDirector.diagnostics(s.server, null).stream().noneMatch(line -> line.startsWith("HOTSPOT")), "Erased diagnostics expose no old regions");
            PostMaeveWorldState.setForDebug(s.server, false); h.assertTrue(memory(s).hotspots.isEmpty(), "Debug reversal starts empty");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnSharedPopulationAndAttentionEvictionDisperseActualActors(GameTestHelper h) {
        scene(h, 101, s -> {
            floor(s, false); var hotspot = history(s); var first = donor(s, -38, 4); var second = donor(s, 42, 4);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var group = memory(s).active;
            h.assertTrue(group != null && !MaeveDirector.allowNaturalPawn(s.level, first.blockPosition()), "Diverted donor area cannot immediately replenish its pressure");
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); s.clock(now + 20); MaeveDirector.tick(s.server);
            h.assertTrue(memory(s).active == null && hotspot.wipes == 0 && second.isMaeveDisengaging(), "Unload cancels and disperses without faking a death");
            h.assertTrue(memory(s).population.pawns.containsKey(first.getUUID()), "Unload does not free a claim for duplicate replacement");
            // Exercise the actual SIEGE eviction callback at the existing priority/dwell boundary.
            second.discard(); s.clock(now + 12020); var a = donor(s, -38, 4); var b = donor(s, 42, 4); MaeveDirector.tick(s.server);
            group = memory(s).active; h.assertTrue(group != null, "Second group is admitted after cooldown");
            var runtime = DirectorRuntime.current(s.server); s.phase.setPresetName("cinematic");
            s.clock(now + 12120);
            for (int i = 0; i < 2; i++) {
                var other = new ConvergenceGroup(UUID.randomUUID(), hotspot, Map.of(UUID.randomUUID(), hotspot.anchor, UUID.randomUUID(), hotspot.anchor), s.level.getGameTime());
                h.assertTrue(runtime.attention.siege(other, List.of(), () -> {}), "Equal-priority fixture siege can contend after minimum dwell");
            }
            h.assertTrue(memory(s).active == null && a.isMaeveDisengaging() && b.isMaeveDisengaging(), "A real focus eviction visibly disperses both members");
            var before = a.position(); for (int i = 1; i <= 60; i++) { s.clock(now + 12120 + i); a.tick(); }
            h.assertTrue(a.position().distanceToSqr(before) > 4, "Dispersal moves the actual entity");
        });
    }
}
