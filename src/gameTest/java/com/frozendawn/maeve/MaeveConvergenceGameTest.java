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
        scene(h, lane, 4, exercise);
    }
    private static void scene(GameTestHelper h, int lane, int radius, Consumer<MaeveObservationGameTest.Scene> exercise) {
        MaeveObservationGameTest.withScene(h, lane, radius, s -> {
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
        helper.assertTrue(resources.size() == 116, "The complete convergence replay must be registered: " + resources.size());
        resources.forEach((file, resource) -> {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("macs_pawn", file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function exists: " + id);
            try (var reader = resource.openAsReader()) {
                net.minecraft.commands.functions.CommandFunction.fromLines(id, server.getCommands().getDispatcher(), server.createCommandSourceStack().withPermission(2), reader.lines().toList());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnSurvivalBaseProvidesAirKitAndRealExposure(GameTestHelper h) {
        scene(h, 111, 2, s -> {
            // Preserve everything the actual relative build function can change.
            for (int x = -18; x <= 8; x++) for (int z = -11; z <= 23; z++)
                for (int y = -1; y <= 5; y++) s.block(x, y, z, Blocks.AIR.defaultBlockState());
            var player = s.player("base_survival_operator", 0, 0);
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            s.server.getCommands().performPrefixedCommand(source, "function macs_pawn:base_safe_mode");
            h.assertTrue(player.isCreative(), "Staging must protect the player before a heated base is installed");
            s.server.getCommands().performPrefixedCommand(source, "function macs_pawn:base_structure");
            s.server.getCommands().performPrefixedCommand(source, "function macs_pawn:base_kit");
            s.settleLight();
            var corePos = s.origin.offset(3, 0, 3);
            var heaterPos = s.origin.offset(-3, 0, 3);
            h.assertTrue(s.level.getBlockEntity(corePos) instanceof com.frozendawn.block.GeothermalCoreBlockEntity,
                    "The live base function creates a real oxygen/refill core");
            h.assertTrue(s.level.getBlockEntity(heaterPos) instanceof com.frozendawn.block.ThermalHeaterBlockEntity heater
                            && heater.isLit(), "The heater has finite real starting fuel");
            // The isolated callback has not yielded a normal block-entity load tick yet.
            // Exercise the real load hook before querying its registered oxygen zone.
            s.level.getBlockEntity(corePos).onLoad();
            s.level.getBlockEntity(heaterPos).onLoad();
            s.phase.setApocalypseTicks(0, s.server);
            float stagingTemperature = com.frozendawn.world.TemperatureManager.getTemperatureAt(
                    s.level, s.origin, s.phase.getCurrentDay(), s.phase.getTotalDays());
            h.assertTrue(Float.isFinite(stagingTemperature) && player.isCreative(),
                    "Staging stays Creative while the newly fueled room warms gradually");
            h.assertTrue(com.frozendawn.world.TemperatureManager.hasOxygenSupport(s.level, s.origin),
                    "The indoor starting position can replenish an intact suit");
            var outside = s.origin.offset(-12, 0, 14);
            h.assertTrue(!com.frozendawn.world.TemperatureManager.hasBreathableAir(s.level, outside),
                    "The outdoor supply path really exposes the player to vacuum");
            h.assertTrue(com.frozendawn.event.SuitIntegrityHandler.isWearingSealedSuit(player),
                    "The production EVA kit is fully sealed before hazards activate");
            h.assertTrue(player.getInventory().getItem(8).is(com.frozendawn.init.ModItems.O2_TANK_MK3.get()),
                    "The player starts with a normal Mk III tank in slot nine");
            h.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE),
                    "No persistent invulnerability buff hides survival pressure");
            s.server.getCommands().performPrefixedCommand(source, "function macs_pawn:base_live_mode");
            h.assertTrue(!player.isCreative() && !player.isSpectator()
                            && com.frozendawn.phase.PhaseManager.isVacuumActive(s.phase.getPhase(), s.phase.getProgress()),
                    "Survival is enabled only with actual Phase-6-late vacuum active");
            h.assertTrue(com.frozendawn.world.TemperatureManager.getTemperatureAt(s.level, s.origin,
                            s.phase.getCurrentDay(), s.phase.getTotalDays()) <= 60,
                    "The operating shelter is below the production overheating threshold");
            for (int z : new int[]{-10, -6, 6, 10}) {
                h.assertTrue(s.level.getBlockState(s.origin.offset(0, 0, z)).is(Blocks.SPRUCE_DOOR)
                                && s.level.getBlockState(s.origin.offset(0, 1, z)).is(Blocks.SPRUCE_DOOR),
                        "Both full-height airlock doors exist at " + z);
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnBaseIdleRetryRequiresAdmissionAndExplicitSurvival(GameTestHelper h) {
        scene(h, 112, s -> {
            var board = s.server.getScoreboard();
            var previous = board.getObjective("mpc");
            var objective = previous != null ? previous : board.addObjective("mpc",
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Base replay"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            var holders = List.of("#stage", "#idle_timer", "#idle_result", "#idle_cloud_tick",
                    "#base_roster", "#base_admitted", "#base_at_dispatch", "#base_timer", "#base_dispatch", "#base_min");
            Map<String, Integer> saved = new java.util.HashMap<>();
            for (String name : holders) {
                var value = board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective);
                saved.put(name, value == null ? null : value.value());
            }
            java.util.function.BiConsumer<String, Integer> set = (name, value) ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).set(value);
            java.util.function.ToIntFunction<String> get = name ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            var player = s.player("base_idle_operator", 0, 0);
            player.addTag("macs_base_player");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            java.util.function.Consumer<String> run = name -> s.server.getCommands()
                    .performPrefixedCommand(source, "function macs_pawn:" + name);
            boolean frozen = s.server.tickRateManager().isFrozen();
            var deaths = board.getObjective("mb_deaths");
            if (deaths == null) s.server.getCommands().performPrefixedCommand(source, "scoreboard objectives add mb_deaths deathCount");
            var first = s.architect(2, 2); var second = s.architect(3, 2);
            for (var actor : List.of(first, second)) actor.addTag("macs_pawn_natural");
            s.block(-3, 0, 3, com.frozendawn.init.ModBlocks.DIAMOND_THERMAL_HEATER.get().defaultBlockState());
            try {
                set.accept("#stage", 111); set.accept("#idle_timer", 0);
                s.server.tickRateManager().setFrozen(false);
                s.server.getCommands().performPrefixedCommand(source, "fdlab base_pause");
                h.assertTrue(!s.server.tickRateManager().isFrozen(), "The pause helper refuses unrelated replay stages");
                run.accept("base_idle_tick");
                h.assertTrue(get.applyAsInt("#stage") == 111 && player.isCreative()
                                && !first.getPersistentData().hasUUID("macsConvergence"),
                        "Waiting never creates a dispatch or exposes the player to Survival");
                // Inject only assignment fixtures to test the QA handshake, not production admission.
                var dispatch = UUID.randomUUID();
                first.getPersistentData().putUUID("macsConvergence", dispatch);
                run.accept("base_idle_tick");
                h.assertTrue(get.applyAsInt("#stage") == 111, "One assigned pawn cannot unlock the retry");
                second.getPersistentData().putUUID("macsConvergence", dispatch);
                set.accept("#idle_timer", 1799); run.accept("base_idle_tick");
                h.assertTrue(get.applyAsInt("#stage") == 112 && get.applyAsInt("#idle_result") == 1
                                && s.server.tickRateManager().isFrozen() && player.isCreative()
                                && !first.isNoAi() && !second.isNoAi(),
                        "Admission at the deadline freezes real travel without holding pawns or starting Survival");
                second.getPersistentData().remove("macsConvergence");
                run.accept("base_idle_go");
                h.assertTrue(get.applyAsInt("#stage") == 112 && player.isCreative(),
                        "A stale or interrupted roster refuses the Survival handoff");
                second.getPersistentData().putUUID("macsConvergence", dispatch);
                run.accept("base_idle_go");
                h.assertTrue(get.applyAsInt("#stage") == 102 && get.applyAsInt("#base_timer") == 0
                                && !player.isCreative() && !player.isSpectator()
                                && com.frozendawn.phase.PhaseManager.isVacuumActive(s.phase.getPhase(), s.phase.getProgress())
                                && first.getPersistentData().getUUID("macsConvergence").equals(dispatch)
                                && second.getPersistentData().getUUID("macsConvergence").equals(dispatch),
                        "Explicit start enables real late-phase Survival without replacing the existing group");
                h.assertTrue(com.frozendawn.event.SuitIntegrityHandler.isWearingSealedSuit(player)
                                && player.getInventory().getItem(8).is(com.frozendawn.init.ModItems.O2_TANK_MK3.get()),
                        "Warmup cannot spend the Survival starting kit");
                player.removeTag("macs_base_player");
                s.server.getCommands().performPrefixedCommand(source, "fdlab base_resume");
                h.assertTrue(s.server.tickRateManager().isFrozen(), "Only the tagged replay participant can resume");
                player.addTag("macs_base_player");
                s.server.getCommands().performPrefixedCommand(source, "fdlab base_resume");
                h.assertTrue(!s.server.tickRateManager().isFrozen(), "The explicit live handoff can resume at function permission two");
                run.accept("base_idle_go");
                h.assertTrue(get.applyAsInt("#stage") == 102, "An already-started replay is not reset");
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                for (var actor : List.of(first, second)) actor.getPersistentData().remove("macsConvergence");
                set.accept("#stage", 111); set.accept("#idle_timer", 1799);
                run.accept("base_idle_tick");
                h.assertTrue(get.applyAsInt("#stage") == 113 && get.applyAsInt("#idle_result") == -1
                                && player.isCreative() && first.isNoAi() && second.isNoAi(),
                        "No admission ends as an explicit inconclusive result without an ordinary ambush");
            } finally {
                s.server.tickRateManager().setFrozen(frozen);
                if (deaths == null) board.removeObjective(board.getObjective("mb_deaths"));
                else s.server.getCommands().performPrefixedCommand(source, "scoreboard players reset " + player.getScoreboardName() + " mb_deaths");
                if (previous == null) board.removeObjective(objective);
                else saved.forEach((name, value) -> {
                    if (value == null) s.server.getCommands().performPrefixedCommand(source, "scoreboard players reset " + name + " mpc");
                    else set.accept(name, value);
                });
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnFocusedBaseStartsSurvivalBeforeAdmissionAndBoundsFailure(GameTestHelper h) {
        scene(h, 116, s -> {
            var board = s.server.getScoreboard();
            var previous = board.getObjective("mpc");
            var objective = previous != null ? previous : board.addObjective("mpc",
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Focused replay"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            var holders = List.of("#stage", "#base_roster", "#base_timer", "#base_released", "#base_before",
                    "#base_dispatch", "#base_admitted", "#base_near", "#base_original_near", "#base_alive",
                    "#base_player_x", "#base_player_z", "#base_player_health", "#base_interval", "#base_mod",
                    "#base_min", "#focus_result", "#focus_original_128", "#focus_at_dispatch_128");
            Map<String, Integer> saved = new java.util.HashMap<>();
            for (String name : holders) {
                var value = board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective);
                saved.put(name, value == null ? null : value.value());
            }
            java.util.function.BiConsumer<String, Integer> set = (name, value) ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).set(value);
            java.util.function.ToIntFunction<String> get = name ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            var player = s.player("focused_base_operator", 0, 0);
            player.addTag("macs_base_player"); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            java.util.function.Consumer<String> run = name -> s.server.getCommands()
                    .performPrefixedCommand(source, "function macs_pawn:" + name);
            var deaths = board.getObjective("mb_deaths");
            if (deaths == null) s.server.getCommands().performPrefixedCommand(source, "scoreboard objectives add mb_deaths deathCount");
            var first = s.architect(2, 2); var second = s.architect(3, 2);
            first.addTag("macs_pawn_natural"); first.setNoAi(true); second.setNoAi(true);
            try {
                var memory = memory(s); var before = memory.save();
                set.accept("#stage", 120); set.accept("#base_dispatch", -1); set.accept("#base_released", 0);
                run.accept("focus_start");
                h.assertTrue(player.isCreative() && first.isNoAi() && get.applyAsInt("#stage") == 120,
                        "A missing original member cannot expose the player or release a partial roster");
                second.addTag("macs_pawn_natural"); run.accept("focus_start");
                h.assertTrue(get.applyAsInt("#stage") == 122 && !player.isCreative() && !player.isSpectator()
                                && com.frozendawn.phase.PhaseManager.isVacuumActive(s.phase.getPhase(), s.phase.getProgress()),
                        "The live session starts with real vacuum and Survival before any group exists");
                h.assertTrue(!first.isNoAi() && !second.isNoAi() && memory.active == null
                                && !first.getPersistentData().hasUUID(ConvergenceCoordinator.DISPATCH)
                                && memory.save().equals(before),
                        "Start releases the saved roster without granting orders or modifying tactical history");
                h.assertTrue(get.applyAsInt("#base_timer") == 0 && get.applyAsInt("#base_dispatch") == -1,
                        "Protected preparation contributes no live time or admission evidence");
                set.accept("#base_timer", 1799); run.accept("focus_tick");
                h.assertTrue(get.applyAsInt("#stage") == 123 && get.applyAsInt("#focus_result") == -1
                                && player.isSpectator() && first.isNoAi() && second.isNoAi(),
                        "Ninety seconds without admission ends visibly as inconclusive");
                h.assertTrue(first.isAlive() && second.isAlive() && memory.save().equals(before),
                        "Inconclusive cleanup cannot kill actors or manufacture hotspot/outcome evidence");
                run.accept("focus_start");
                h.assertTrue(get.applyAsInt("#stage") == 123 && first.isNoAi() && player.isSpectator(),
                        "A completed check cannot be accidentally restarted");
            } finally {
                first.removeTag("macs_pawn_natural"); second.removeTag("macs_pawn_natural");
                player.removeTag("macs_base_player");
                if (deaths == null) board.removeObjective(board.getObjective("mb_deaths"));
                if (previous == null) board.removeObjective(objective);
                else for (String name : holders) {
                    var holder = net.minecraft.world.scores.ScoreHolder.forNameOnly(name);
                    if (saved.get(name) == null) board.resetSinglePlayerScore(holder, objective);
                    else set.accept(name, saved.get(name));
                }
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnBaseReturnBoundsAdmissionAndPreventsRestart(GameTestHelper h) {
        scene(h, 119, s -> {
            var board = s.server.getScoreboard();
            var previous = board.getObjective("mpc");
            var objective = previous != null ? previous : board.addObjective("mpc",
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Return replay"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            var holders = List.of("#stage", "#base_roster", "#return_timer", "#base_released", "#base_before",
                    "#return_dispatch", "#base_admitted", "#base_near", "#base_original_near", "#base_alive",
                    "#base_player_x", "#base_player_z", "#base_player_health", "#base_interval", "#base_mod",
                    "#return_result", "#return_near", "#return_all_near", "#return_alive", "#return_assigned", "#return_arrival", "#return_clear_ticks", "#return_left_early", "#return_interval", "#return_mod");
            Map<String, Integer> saved = new java.util.HashMap<>();
            for (String name : holders) {
                var value = board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective);
                saved.put(name, value == null ? null : value.value());
            }
            java.util.function.BiConsumer<String, Integer> set = (name, value) ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).set(value);
            java.util.function.ToIntFunction<String> get = name ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            var player = s.player("return_base_operator", 0, 0);
            player.addTag("macs_base_player"); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            java.util.function.Consumer<String> run = name -> s.server.getCommands()
                    .performPrefixedCommand(source, "function macs_pawn:" + name);
            var deaths = board.getObjective("mb_deaths");
            if (deaths == null) s.server.getCommands().performPrefixedCommand(source, "scoreboard objectives add mb_deaths deathCount");
            var first = s.architect(2, 2); var second = s.architect(3, 2);
            first.addTag("macs_pawn_natural"); first.setNoAi(true); second.setNoAi(true);
            try {
                var memory = memory(s); var before = memory.save();
                set.accept("#stage", 130); set.accept("#return_dispatch", -1); set.accept("#base_released", 0);
                run.accept("return_start");
                h.assertTrue(player.isCreative() && first.isNoAi() && get.applyAsInt("#stage") == 130,
                        "A missing original member cannot expose the player or release a partial roster");
                second.addTag("macs_pawn_natural"); run.accept("return_start");
                h.assertTrue(get.applyAsInt("#stage") == 132 && !player.isCreative() && !player.isSpectator()
                                && com.frozendawn.phase.PhaseManager.isVacuumActive(s.phase.getPhase(), s.phase.getProgress()),
                        "The live session starts with real vacuum and Survival before any group exists");
                h.assertTrue(!first.isNoAi() && !second.isNoAi() && memory.active == null
                                && !first.getPersistentData().hasUUID(ConvergenceCoordinator.DISPATCH)
                                && memory.save().equals(before),
                        "Start releases the saved roster without granting orders or modifying tactical history");
                h.assertTrue(get.applyAsInt("#return_timer") == 0 && get.applyAsInt("#return_dispatch") == -1,
                        "Protected preparation contributes no live time or admission evidence");
                set.accept("#return_timer", 599); run.accept("return_tick");
                h.assertTrue(get.applyAsInt("#stage") == 133 && get.applyAsInt("#return_result") == -1
                                && player.isSpectator() && first.isNoAi() && second.isNoAi(),
                        "Thirty seconds without admission ends visibly as inconclusive");
                h.assertTrue(first.isAlive() && second.isAlive() && memory.save().equals(before),
                        "Inconclusive cleanup cannot kill actors or manufacture hotspot/outcome evidence");
                run.accept("return_start");
                h.assertTrue(get.applyAsInt("#stage") == 133 && first.isNoAi() && player.isSpectator(),
                        "A completed check cannot be accidentally restarted");
            } finally {
                first.removeTag("macs_pawn_natural"); second.removeTag("macs_pawn_natural");
                player.removeTag("macs_base_player");
                if (deaths == null) board.removeObjective(board.getObjective("mb_deaths"));
                if (previous == null) board.removeObjective(objective);
                else for (String name : holders) {
                    var holder = net.minecraft.world.scores.ScoreHolder.forNameOnly(name);
                    if (saved.get(name) == null) board.resetSinglePlayerScore(holder, objective);
                    else set.accept(name, saved.get(name));
                }
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnNaturalSampleLandsOnlyOnReadyPlatform(GameTestHelper h) {
        scene(h, 109, s -> {
            var player = s.player("natural_sample_landing", 0, 0);
            // NeoForge's FakePlayer connection deliberately ignores teleports. Use native
            // teleport handling here, suppressing only outbound network traffic.
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(s.server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                        net.minecraft.network.PacketSendListener listener) {}
            };
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            var waiting = s.position(0, 0).add(0, -20, 0);
            var landing = s.position(3, 3);
            player.setPos(waiting);
            var command = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(landing).withPermission(2);
            s.block(3, -1, 3, Blocks.AIR.defaultBlockState());
            s.server.getCommands().performPrefixedCommand(command, "function macs_pawn:natural_land");
            h.assertTrue(player.isSpectator() && player.position().equals(waiting),
                    "Missing platform cannot expose the waiting player to gravity");
            s.block(3, -1, 3, Blocks.LAPIS_BLOCK.defaultBlockState());
            s.block(3, 1, 3, Blocks.STONE.defaultBlockState());
            s.server.getCommands().performPrefixedCommand(command, "function macs_pawn:natural_land");
            h.assertTrue(player.isSpectator() && player.position().equals(waiting),
                    "Obstructed headroom must refuse landing");
            s.block(3, 0, 3, Blocks.AIR.defaultBlockState());
            s.block(3, 1, 3, Blocks.AIR.defaultBlockState());
            s.server.getCommands().performPrefixedCommand(command, "function macs_pawn:natural_land");
            h.assertTrue(player.isCreative() && player.position().distanceToSqr(landing) < .001,
                    "Ready platform teleports the player from below the field before enabling Creative sampling");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void pawnNaturalWatchWaitsForLateDispatchAndArrival(GameTestHelper h) {
        scene(h, 110, s -> {
            var board = s.server.getScoreboard();
            var previous = board.getObjective("mpc");
            var objective = previous != null ? previous : board.addObjective("mpc",
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("Pawn replay"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
            var holders = List.of("#stage", "#timer", "#admitted", "#arrived", "#watch_result", "#cloud_tick", "#arrival_tick", "#elapsed");
            Map<String, Integer> saved = new java.util.HashMap<>();
            for (String name : holders) {
                var value = board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective);
                saved.put(name, value == null ? null : value.value());
            }
            java.util.function.BiConsumer<String, Integer> set = (name, value) ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).set(value);
            java.util.function.ToIntFunction<String> get = name ->
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name), objective).get();
            var player = s.player("natural_watch_operator", 0, 0);
            var first = s.architect(2, 2); var second = s.architect(3, 2);
            for (var actor : List.of(first, second)) actor.addTag("macs_pawn_natural");
            var source = s.server.createCommandSourceStack().withEntity(player).withLevel(s.level)
                    .withPosition(player.position()).withPermission(2);
            Runnable step = () -> s.server.getCommands().performPrefixedCommand(source, "function macs_pawn:natural_watch_tick");
            try {
                set.accept("#stage", 93); set.accept("#timer", 1199); set.accept("#watch_result", 0);
                var dispatch = UUID.randomUUID();
                for (var actor : List.of(first, second)) actor.getPersistentData().putUUID("macsConvergence", dispatch);
                step.run();
                h.assertTrue(get.applyAsInt("#stage") == 95 && get.applyAsInt("#timer") == 0,
                        "Late native dispatch begins a fresh approach clock instead of stopping at the old 60-second limit");
                set.accept("#timer", 1199); step.run();
                h.assertTrue(get.applyAsInt("#stage") == 95 && !first.isNoAi() && !second.isNoAi(),
                        "An active group with no arrivals stays observable beyond the former cutoff");
                first.addTag("macs_natural_arrived"); step.run();
                h.assertTrue(get.applyAsInt("#stage") == 95, "One recorded arrival cannot complete a two-pawn view");
                second.addTag("macs_natural_arrived"); step.run();
                h.assertTrue(get.applyAsInt("#stage") == 96 && get.applyAsInt("#watch_result") == 1,
                        "Both recorded arrivals start a five-second observation tail");
                set.accept("#timer", 98); step.run();
                h.assertTrue(!first.isNoAi() && get.applyAsInt("#stage") == 96, "Do not truncate the observation tail");
                step.run();
                h.assertTrue(first.isNoAi() && second.isNoAi() && get.applyAsInt("#stage") == 94,
                        "Hold the roster only after the arrival observation is complete");
                for (var actor : List.of(first, second)) { actor.removeTag("macs_natural_arrived"); actor.setNoAi(false); }
                set.accept("#stage", 95); set.accept("#timer", 300); set.accept("#watch_result", 0);
                first.getPersistentData().remove("macsConvergence"); step.run();
                h.assertTrue(get.applyAsInt("#stage") == 94 && get.applyAsInt("#watch_result") == -3,
                        "A released group is inconclusive rather than a fabricated arrival");
                second.getPersistentData().remove("macsConvergence");
                set.accept("#stage", 93); set.accept("#timer", 1799); set.accept("#watch_result", 0); step.run();
                h.assertTrue(get.applyAsInt("#stage") == 94 && get.applyAsInt("#watch_result") == -1,
                        "No-admission wait is bounded and explicitly inconclusive");
            } finally {
                if (previous == null) board.removeObjective(objective);
                else saved.forEach((name, value) -> {
                    if (value == null) s.server.getCommands().performPrefixedCommand(source, "scoreboard players reset " + name + " mpc");
                    else set.accept(name, value);
                });
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 500)
    public static void pawnNaturalSpawnerHonorsLocalDensityWithSharedCapacityFree(GameTestHelper h) {
        scene(h, 108, 5, s -> {
            double previousMultiplier = FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get();
            FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.set(1.0);
            try {
                for (int x = -66; x <= 66; x++) for (int z = -66; z <= 66; z++)
                    s.block(x, -1, z, Blocks.STONE.defaultBlockState());
                var observer = s.player("pawn_natural_stationary", 0, 0);
                observer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var area = observer.getBoundingBox().inflate(80);
                long now = (s.level.getGameTime() / 200 + 1) * 200;
                List<ArchitectEntity> spawned = List.of();
                // Exercise ordinary 2% production rolls, not a forced spawn or a raised rate.
                // This bounded headless loop tests admission; it is not a live-frequency estimate.
                for (int i = 0; i < 4096 && spawned.isEmpty(); i++) {
                    s.clock(now); now += 200;
                    com.frozendawn.world.ArchitectSpawner.tick(s.level, 6, .90f);
                    spawned = s.level.getEntitiesOfClass(ArchitectEntity.class, area);
                }
                s.entities.addAll(spawned);
                h.assertTrue(spawned.size() == 1, "The actual natural spawner produces the first ordinary pawn on valid loaded terrain");
                var first = spawned.getFirst();
                h.assertTrue(memory(s).population.pawns.containsKey(first.getUUID()), "Natural admission registers the real UUID in the shared population");
                h.assertTrue(MaeveDirector.allowNaturalPawn(s.level, s.origin.offset(60, 0, 0)),
                        "Shared capacity remains available; the global ceiling cannot mask this local-density check");
                for (int i = 0; i < 1024; i++) {
                    s.clock(now); now += 200;
                    com.frozendawn.world.ArchitectSpawner.tick(s.level, 6, .90f);
                }
                var after = s.level.getEntitiesOfClass(ArchitectEntity.class, area);
                after.stream().filter(a -> !s.entities.contains(a)).forEach(s.entities::add);
                h.assertTrue(after.size() == 1 && after.getFirst() == first,
                        "Further production rolls cannot add a second pawn beside a stationary player while one remains nearby");
                h.assertTrue(memory(s).active == null, "A single natural pawn and no history never fabricate a convergence group");
            } finally { FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.set(previousMultiplier); }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnEnvironmentalTrapPhysicsRecordsDeaths(GameTestHelper h) {
        scene(h, 106, s -> {
            var m = memory(s); long now = start(s);
            var falling = s.architect(2, 4);
            falling.setPos(s.position(2, 4).add(0, 60, 0));
            falling.setOnGround(false);
            for (int t = 1; t <= 100 && falling.isAlive(); t++) tick(s, now + t, falling);
            h.assertTrue(!falling.isAlive() && m.hotspots.size() == 1,
                    "Full-health ordinary pawn dies through actual falling physics without a player attacker");
            var hotspot = m.hotspots.values().iterator().next();
            h.assertTrue(hotspot.deaths == 1, "The actual fall contributes exactly one death");
            for (int x = 4; x <= 6; x++) for (int z = 3; z <= 5; z++) for (int y = -1; y <= 3; y++) {
                s.block(x, y, z, x == 5 && z == 4 && y >= 0 && y < 3
                        ? Blocks.AIR.defaultBlockState() : Blocks.BARRIER.defaultBlockState());
            }
            s.block(5, 0, 4, Blocks.LAVA.defaultBlockState());
            var burning = s.architect(5, 4);
            burning.setPos(s.position(5, 4).add(0, 1, 0));
            for (int t = 101; t <= 400 && burning.isAlive(); t++) tick(s, now + t, burning);
            h.assertTrue(!burning.isAlive() && hotspot.deaths == 2 && m.hotspots.size() == 1,
                    "Full-health pawn dies to the actual bounded lava trap and adds one death to the same region");
            h.assertTrue(hotspot.encounters == 0 && hotspot.evidence.get(0).encounter().equals(hotspot.evidence.get(1).encounter()),
                    "Nearby physical trap deaths inside the quiet boundary remain one open encounter");
            h.assertTrue(memory(s).active == null, "Two environmental deaths alone cannot dispatch a group");
        });
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
            h.assertTrue(idle.position().distanceToSqr(origin) > 4 && idle.isSprinting(), "Avoidance visibly sprints away");
            var attacker = s.player("pawn_avoid_defense", 8, 4);
            attacker.setPos(idle.position().add(-2, 0, 0));
            h.assertTrue(s.hit(idle, attacker, true, 1), "A real hit can interrupt the departing pawn");
            h.assertTrue(!idle.isMaeveDisengaging() && !idle.isSprinting(), "Local defense clears the avoidance sprint immediately");
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

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 500)
    public static void pawnDistantDonorsExtendWarningThroughActualSnowTravel(GameTestHelper h) {
        scene(h, 107, 7, s -> {
            for (int x = -106; x <= 110; x++) for (int z = 0; z <= 8; z++) {
                s.block(x, -1, z, Blocks.STONE.defaultBlockState());
                s.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(x / 3, 3)));
            }
            var hotspot = history(s); var first = donor(s, -98, 4); var second = donor(s, 102, 4);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var group = memory(s).active;
            h.assertTrue(group != null, "Distant real donors inside the 128-block bound are admitted");
            var origin = first.position();
            for (int i = 1; i <= 360; i++) tick(s, now + i, first, second);
            h.assertTrue(memory(s).active == group && group.firstArrival < 0,
                    "The long-route cloud remains pending beyond the accepted 18-second nearby arrival");
            h.assertTrue(first.position().distanceToSqr(origin) > 4, "The delayed arrival comes from actual walking");
            for (int i = 361; i <= 1200 && group.firstArrival < 0; i++) tick(s, now + i, first, second);
            h.assertTrue(memory(s).active == group && group.firstArrival > now + 360,
                    "Actual snow travel eventually reaches the region without forced arrival: " + first.position() + " result=" + hotspot.results);
            h.assertTrue(group.contactAt == -1, "Arrival at an empty region does not invent player contact");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pawnWipeDoesNotFeedAnyRegionAndContactResetsFailure(GameTestHelper h) {
        scene(h, 99, s -> {
            var hotspot = history(s); var first = donor(s, -38, 4); var second = donor(s, 42, 4);
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var g = memory(s).active;
            h.assertTrue(g != null, "Fixture dispatched the real group"); var before = hotspot.save();
            var serialized = new CompoundTag(); first.saveWithoutId(serialized);
            h.assertTrue(serialized.getCompound("NeoForgeData").hasUUID("macsConvergence"),
                    "Live replay admission reads the actual persistent dispatch UUID from native entity NBT");
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

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 500)
    public static void pawnDiversionVacatesSourceWithoutReplacementOrExtraActors(GameTestHelper h) {
        scene(h, 103, s -> {
            floor(s, true); var hotspot = history(s);
            var first = donor(s, -38, 2); var second = donor(s, -38, 6);
            var firstOrigin = first.blockPosition(); var secondOrigin = second.blockPosition();
            long now = s.level.getGameTime(); MaeveDirector.tick(s.server); var group = memory(s).active;
            h.assertTrue(group != null && group.donors.keySet().equals(java.util.Set.of(first.getUUID(), second.getUUID())),
                    "The two existing source actors become the frozen roster");
            h.assertTrue(MaeveDirector.allowNaturalPawn(s.level, s.origin.offset(160, 0, 4)),
                    "Unrelated location has free population capacity, isolating donor suppression from the global cap");
            h.assertTrue(!MaeveDirector.allowNaturalPawn(s.level, firstOrigin), "The donor area cannot immediately replace diverted pressure");
            for (int i = 1; i <= 500; i++) tick(s, now + i, first, second);
            h.assertTrue(memory(s).active == group && first.blockPosition().distSqr(firstOrigin) > 12 * 12
                            && second.blockPosition().distSqr(secondOrigin) > 12 * 12,
                    "Both actual pawns vacate the source area by the live replay checkpoint");
            h.assertTrue(memory(s).population.pawns.keySet().equals(group.donors.keySet()),
                    "Diversion has neither created a replacement nor fabricated extra actors");
            h.assertTrue(!MaeveDirector.allowNaturalPawn(s.level, firstOrigin)
                            && MaeveDirector.allowNaturalPawn(s.level, s.origin.offset(160, 0, 4)),
                    "Source replacement remains withheld after physical departure while unrelated capacity remains usable");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 600)
    public static void pawnBaseReturnWithoutDecoyRetainsRealPressure(GameTestHelper h) {
        baseReturn(h, 117, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 600)
    public static void pawnBaseReturnWithDecoyDivertsSameNearbyPawns(GameTestHelper h) {
        baseReturn(h, 118, true);
    }

    private static void baseReturn(GameTestHelper h, int lane, boolean qualifying) {
        scene(h, lane, 8, s -> {
            // Identical starting geometry, terrain, player itinerary and actor RNG in
            // both arms. The history arm alone differs: two vs three real encounters.
            for (int x = -127; x <= 18; x++) for (int z = -96; z <= 20; z++) {
                s.block(x, -1, z, Blocks.STONE.defaultBlockState());
                s.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
            }
            s.clock(200000);
            long evidenceAt = start(s);
            for (int episode = 0; episode < (qualifying ? 3 : 2); episode++) {
                s.clock(evidenceAt + episode * 640);
                kill(s, s.architect(2, 4)); kill(s, s.architect(3, 4));
            }
            s.clock(evidenceAt + 1880);
            var m = memory(s); m.hotspots.values().forEach(hotspot -> hotspot.advance(s.level.getGameTime()));
            var first = donor(s, -60, 0); var second = donor(s, -60, 8);
            first.startDecisionRecording(1337L); second.startDecisionRecording(7331L);
            var roster = java.util.Set.of(first.getUUID(), second.getUUID());
            var firstOrigin = first.position(); var secondOrigin = second.position();
            var base = s.position(-120, 4);
            var player = s.player("base_return_" + qualifying, -120, -88);
            long now = s.level.getGameTime();
            h.assertTrue(first.getTarget() == null && second.getTarget() == null
                            && first.distanceToSqr(player) > 96 * 96 && second.distanceToSqr(player) > 96 * 96
                            && first.position().distanceToSqr(base) < 96 * 96 && second.position().distanceToSqr(base) < 96 * 96,
                    "Both ordinary idle donors threaten the base while the Survival player is outside detection");
            tick(s, now, first, second);
            var group = m.active;
            h.assertTrue(qualifying ? group != null && group.donors.keySet().equals(roster) : group == null,
                    "Only the qualifying history admits these two physical pawns through production dispatch");
            boolean targeted = false;
            double closest = Double.POSITIVE_INFINITY;
            int clearBaseTicks = 0;
            for (int t = 1; t <= 1600; t++) {
                // A fixed return starts after thirty seconds; no assignment-gated
                // player teleport, creative interval or runtime safety bubble.
                double z = -88 + Math.clamp((t - 600) * .2, 0, 92);
                player.setPos(s.position(-120, 0).add(0, 0, z));
                tick(s, now + t, first, second);
                targeted |= java.util.stream.Stream.of(first, second).flatMap(a -> a.decisionJournal().entries().stream())
                        .anyMatch(entry -> entry.event().equals("TARGET_CHANGE") && entry.detail().contains(player.getUUID().toString()));
                if (t >= 1060) {
                    double distance = Math.min(first.position().distanceToSqr(base), second.position().distanceToSqr(base));
                    closest = Math.min(closest, distance);
                    if (distance > 96 * 96 && !targeted) clearBaseTicks++;
                }
            }
            try {
                var directory = java.nio.file.Path.of(System.getProperty("frozendawn.architect.reports"), "base-return");
                java.nio.file.Files.createDirectories(directory);
                String arm = qualifying ? "decoy" : "control";
                java.nio.file.Files.writeString(directory.resolve(arm + "-first.tsv"), first.decisionJournal().tsv());
                java.nio.file.Files.writeString(directory.resolve(arm + "-second.tsv"), second.decisionJournal().tsv());
                var result = new com.google.gson.JsonObject();
                result.addProperty("qualifyingHistory", qualifying); result.addProperty("targetedPlayer", targeted);
                result.addProperty("nearestBaseDistance", Math.sqrt(closest)); result.addProperty("clearBaseTicks", clearBaseTicks);
                result.addProperty("firstTravel", first.position().distanceTo(firstOrigin)); result.addProperty("secondTravel", second.position().distanceTo(secondOrigin));
                result.addProperty("firstArrival", group == null ? -1 : group.firstArrival - now);
                result.addProperty("firstUuid", first.getUUID().toString()); result.addProperty("secondUuid", second.getUUID().toString());
                java.nio.file.Files.writeString(directory.resolve(arm + ".json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            if (qualifying) {
                h.assertTrue(m.active == group && group.firstArrival >= 0 && group.contactAt < 0,
                        "Production group reaches the decoy and stays away from the returning player");
                h.assertTrue(!targeted && clearBaseTicks >= 500,
                        "Both pawns remain beyond ordinary detection for a usable base window: ticks=" + clearBaseTicks + " nearest=" + Math.sqrt(closest));
                h.assertTrue(first.position().distanceToSqr(firstOrigin) > 32 * 32
                                && second.position().distanceToSqr(secondOrigin) > 32 * 32,
                        "The same two pawns physically leave the base vicinity");
                h.assertTrue(m.population.pawns.keySet().equals(roster)
                                && !MaeveDirector.allowNaturalPawn(s.level, s.origin.offset(-120, 0, 4)),
                        "The source cannot immediately replenish either diverted pawn");
            } else {
                h.assertTrue(m.active == null && targeted && closest < 32 * 32,
                        "The matched return without qualifying history causes actual pursuit into the base vicinity: targeted=" + targeted + " nearest=" + Math.sqrt(closest));
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 500)
    public static void pawnAvoidanceMovesIdleActorOutAcrossLayeredSnow(GameTestHelper h) {
        scene(h, 104, s -> {
            floor(s, true);
            // Match the live field's broad snow apron; a nine-block test lane would
            // impose a false cliff on the departure controller's safe side steps.
            for (int x = 12; x <= 50; x++) for (int z = -26; z <= 22; z++) {
                s.block(x, -1, z, Blocks.STONE.defaultBlockState());
                s.block(x, 0, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(x / 3, 3)));
            }
            var hotspot = history(s); long now = s.level.getGameTime();
            for (int i = 0; i < 2; i++) {
                s.clock(now + i * 12000); var first = donor(s, -38, 2); var second = donor(s, -38, 6);
                MaeveDirector.tick(s.server); h.assertTrue(memory(s).active != null, "Real roster precedes each wipe");
                kill(s, first); kill(s, second);
            }
            var idle = donor(s, 18, 4); var origin = idle.position();
            s.clock(now + 12020); MaeveDirector.tick(s.server);
            h.assertTrue(hotspot.avoid && idle.isMaeveDisengaging(), "An unprovoked eligible pawn receives regional avoidance");
            boolean sprinted = false; double fastestStep = 0;
            for (int t = 1; t <= 200; t++) {
                var previous = idle.position(); s.clock(now + 12020 + t); idle.tick();
                sprinted |= idle.isSprinting();
                fastestStep = Math.max(fastestStep, idle.position().subtract(previous).horizontalDistance());
            }
            h.assertTrue(sprinted && fastestStep > .2, "Snow departure uses visibly faster physical sprint movement: " + fastestStep);
            h.assertTrue(!idle.isSprinting(), "The ten-second handoff clears sprint before ordinary behavior resumes");
            h.assertTrue(idle.position().distanceToSqr(origin) > 16
                            && idle.blockPosition().distSqr(hotspot.anchor) > ConvergencePolicy.RADIUS * ConvergencePolicy.RADIUS,
                    "The real idle pawn visibly exits the avoided region on layered snow: origin=" + origin + " now=" + idle.position());
            h.assertTrue(hotspot.deaths == 6 && hotspot.wipes == 2 && hotspot.avoid,
                    "Observing withdrawal creates no death and does not reset avoidance");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void pawnAvoidanceSprintStopsAtBlockedExit(GameTestHelper h) {
        scene(h, 105, s -> {
            floor(s, false); var actor = donor(s, 18, 4); long now = start(s);
            s.clock(now); actor.beginMaeveDisengagement(actor.getUUID(), s.origin.offset(2, 0, 4), "PAWN_AVOID");
            for (int t = 1; t <= 20; t++) { s.clock(now + t); actor.tick(); }
            h.assertTrue(actor.isSprinting(), "Open ground admits the avoidance sprint");
            var center = actor.blockPosition();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                for (int y = 0; y <= 2; y++) {
                    var pos = center.offset(x, y, z).subtract(s.origin);
                    s.block(pos.getX(), pos.getY(), pos.getZ(), Blocks.BEDROCK.defaultBlockState());
                }
            }
            s.clock(now + 21); actor.tick(); var stopped = actor.position();
            h.assertTrue(!actor.isSprinting(), "An obstructed exit releases sprint immediately");
            for (int t = 22; t <= 45; t++) { s.clock(now + t); actor.tick(); }
            h.assertTrue(actor.position().distanceToSqr(stopped) < .01 && s.level.noCollision(actor),
                    "Queued chase movement cannot push a stopped pawn into the obstruction");
            actor.clearMaeveAttention();
            actor.beginMaeveDisengagement(actor.getUUID(), s.origin.offset(2, 0, 4), "PASSIVE_TRACKING");
            s.clock(now + 46); actor.tick();
            h.assertTrue(!actor.isSprinting(), "Ordinary attention eviction does not inherit the avoidance sprint");
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
