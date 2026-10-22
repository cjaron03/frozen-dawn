package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.block.HeatVentBlock;
import com.frozendawn.block.ThermalHeaterBlock;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** §9.4a Pyro: gate, single claim, thermal sight rules, upload on sight and local watch/defense/leave behavior. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaevePyroGameTest {
    private static final UUID WITNESS = new UUID(0x5C81BEL, 4);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), STONE = Blocks.STONE.defaultBlockState(),
            WOOL = Blocks.WHITE_WOOL.defaultBlockState();

    /** One covered shelter sample: Maeve suspects a home here. */
    static BlockPos home(MaeveObservationGameTest.Scene scene, UUID player, int x, int z) {
        MaeveDirector.snapshot(scene.server, player);
        String dim = scene.level.dimension().location().toString();
        var world = MaeveSavedData.get(scene.server).store().observeContact(player, WITNESS, dim, scene.gameTime);
        world.shelter(dim, scene.origin.offset(x, 0, z), scene.gameTime);
        return world.center(dim);
    }

    static List<MaeveDirector.WorldPointSnapshot> heat(MaeveObservationGameTest.Scene scene, UUID player) {
        var store = MaeveSavedData.get(scene.server).store();
        var world = store == null ? null : store.world(player);
        return world == null ? List.of() : world.snapshot(scene.server.overworld().getGameTime()).stream()
                .filter(p -> p.label().equals("HEAT_SOURCE")).toList();
    }

    /** A real server player: a FakePlayer's advancements ignore awards. */
    static ServerPlayer awardable(MaeveObservationGameTest.Scene scene, String name, int x, int z) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(scene.server, scene.level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(scene.level, profile).connection;
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(scene.position(x, z));
        scene.level.addNewPlayer(player);
        scene.entities.add(player);
        return player;
    }

    static boolean idle(MaeveObservationGameTest.Scene scene, UUID player) {
        return MaeveDirector.diagnostics(scene.server, player).stream().anyMatch(line -> line.equals("PYRO active: none"));
    }

    static BlockState heater() { return ModBlocks.THERMAL_HEATER.get().defaultBlockState().setValue(ThermalHeaterBlock.LIT, true); }
    /** A decoy that no vacuum can snuff: a lit campfire would go out the moment Maeve's late phase is active. */
    static BlockState magma() { return Blocks.MAGMA_BLOCK.defaultBlockState(); }

    /**
     * A room with inside x 1..5, y 0..2, z 1..5 under its own roof, on a double floor. The south face (z 6) looks at the
     * viewer; {@code doubleSouth} adds a second layer at z 7. Every call rebuilds the whole area, so cases can follow one another.
     */
    static void room(MaeveObservationGameTest.Scene scene, BlockState wall, boolean doubleSouth) {
        for (int x = -1; x <= 7; x++) for (int y = -2; y <= 4; y++) for (int z = -1; z <= 8; z++) {
            boolean inside = x >= 0 && x <= 6 && z >= 0 && z <= 6 && y >= 0 && y <= 3;
            boolean shell = inside && (x == 0 || x == 6 || z == 0 || z == 6 || y == 3);
            boolean outer = doubleSouth && z == 7 && x >= 0 && x <= 6 && y >= 0 && y <= 3;
            boolean floor = y == -1 || y == -2 && x >= 0 && x <= 6 && z >= 0 && z <= 7;
            scene.block(x, y, z, floor ? STONE : shell || outer ? wall : AIR);
        }
    }

    static void door(MaeveObservationGameTest.Scene scene, int x, int z, boolean open) {
        var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, open);
        scene.block(x, 0, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        scene.block(x, 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static ArchitectEntity viewer(MaeveObservationGameTest.Scene scene, int x, int z) {
        var eye = scene.architect(x, z); eye.setNoAi(true); return eye;
    }

    static List<String> look(MaeveObservationGameTest.Scene scene, ArchitectEntity eye, int x, int y, int z) {
        var scan = ThermalSight.scan(scene.level, eye, scene.origin.offset(x, y, z));
        if (!scan.complete()) throw new IllegalStateException("Thermal fixture must be loaded");
        return scan.sightings().stream().map(s -> s.kind() + "@" + s.seen().subtract(scene.origin).toShortString()).toList();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroGateAllowsOneClaimAndStopsAfterErased(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 210, scene -> {
            var player = scene.player("pyro_gate", 3, 3); UUID id = player.getUUID();
            long t = MaeveScribeGameTest.gate(scene, id);
            var first = scene.architect(8, 3);
            helper.assertFalse(MaeveDirector.designatePyro(first, player), "No suspected home, nothing to look at");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("NO_SUSPECTED_HOME")),
                    "Refused for want of a home");
            BlockPos home = home(scene, id, 5, 5);
            helper.assertTrue(MaeveDirector.designatePyro(first, player), "A suspected home with no known heat");
            first.becomePyro();
            var order = MaeveDirector.pyroOrder(first);
            helper.assertTrue(first.isPyro() && order != null && order.subject().equals(id) && order.home().equals(home),
                    "The Pyro is sent to the suspected home: " + order);
            helper.assertFalse(MaeveDirector.designateScribe(first, player), "A Pyro is never also a Scribe");
            helper.assertTrue(MaeveDirector.commitmentHints(first, player).isEmpty(), "A Pyro takes no counter-family slot");
            var scribe = scene.architect(8, 5);
            helper.assertTrue(MaeveDirector.designateScribe(scribe, player), "The Scribe has its own claim");
            scribe.becomeScribe();
            var second = scene.architect(8, 7);
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "One Pyro at a time");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("PYRO_ALREADY_ACTIVE")), "Single claim");
            MaeveDirector.pyroEnded(first, "QA_ENDED");
            helper.assertTrue(MaeveDirector.pyroOrder(first) == null && idle(scene, id), "An ended claim releases its order");
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "The next Pyro waits for the cooldown");
            scene.clock(t + PyroPolicy.COOLDOWN);
            helper.assertFalse(MaeveDirector.designatePyro(scribe, player), "A Scribe is never also a Pyro");
            var master = scene.architect(8, 9); master.bindToHearthMasterArchitect(UUID.randomUUID(), scene.origin, 0);
            helper.assertFalse(MaeveDirector.designatePyro(master, player), "Masters never participate");
            player.setGameMode(GameType.CREATIVE);
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "Creative players are never a subject");
            player.setGameMode(GameType.SURVIVAL);
            var world = MaeveSavedData.get(scene.server).store().world(id);
            String dim = scene.level.dimension().location().toString();
            world.event("HEAT_SOURCE", dim, home.offset(3, 0, 0),
                    new ObservedEvidence(WITNESS, UUID.randomUUID(), dim, home, t + PyroPolicy.COOLDOWN, "QA_WITNESSED", true), 1);
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "Heat already known there leaves nothing to find out");
            helper.assertTrue(MaeveDirector.diagnostics(scene.server, id).stream().anyMatch(line -> line.contains("HOME_ALREADY_WARM")), "Warm home");
            PostMaeveWorldState.setForDebug(scene.server, true);
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "Pyros stop appearing after ERASED");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertFalse(MaeveDirector.designatePyro(second, player), "Debug reversal starts empty: no home, no Pyro");
        });
    }

    /** Thin walls glow, thick and wool walls do not; glass blocks sight; an open door leaks and a closed one does not. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroThermalSightFollowsWallsGlassAndDoors(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 211, 3, scene -> {
            var eye = viewer(scene, 3, 26);
            room(scene, STONE, false); scene.block(3, 0, 5, heater());
            var seen = look(scene, eye, 3, 0, 5);
            helper.assertTrue(seen.equals(List.of("WALL_GLOW@3, 0, 6")),
                    "A thin stone wall beside a heater glows faintly, and only the wall: " + seen);
            room(scene, STONE, true); scene.block(3, 0, 5, heater());
            helper.assertTrue(look(scene, eye, 3, 0, 5).isEmpty(), "A double wall hides the heater");
            room(scene, WOOL, false); scene.block(3, 0, 5, heater());
            helper.assertTrue(look(scene, eye, 3, 0, 5).isEmpty(), "A wool wall hides the heater");
            scene.block(3, 0, 6, Blocks.GLASS.defaultBlockState()); scene.block(3, 1, 6, Blocks.GLASS_PANE.defaultBlockState());
            helper.assertTrue(look(scene, eye, 3, 0, 5).isEmpty(), "Glass blocks thermal sight, though the heater is in plain view");
            room(scene, WOOL, false); scene.block(3, 0, 2, heater()); door(scene, 3, 6, true);
            seen = look(scene, eye, 3, 0, 2);
            helper.assertTrue(seen.size() == 1 && !seen.getFirst().startsWith("WALL_GLOW"), "An open door leaks heat: " + seen);
            door(scene, 3, 6, false);
            seen = look(scene, eye, 3, 0, 2);
            helper.assertTrue(seen.isEmpty(), "A closed door four blocks from the heater shows nothing: " + seen);
        });
    }

    /** Vents, magma and anything else the thermal model counts are heat, decoys included. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroThermalSightCountsVentsAndDecoys(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 212, 3, scene -> {
            var eye = viewer(scene, 3, 26);
            MaeveScribeGameTest.floor(scene, 10);
            scene.block(3, 0, 5, magma());
            var seen = look(scene, eye, 3, 0, 5);
            helper.assertTrue(seen.equals(List.of("DIRECT@3, 0, 5")), "A magma decoy in the open is seen directly: " + seen);
            scene.block(3, 0, 5, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
            helper.assertTrue(look(scene, eye, 3, 0, 5).isEmpty(), "An unlit campfire is cold");
            room(scene, STONE, true); scene.block(3, 0, 3, heater());
            var vent = ModBlocks.HEAT_VENT.get().defaultBlockState().setValue(HeatVentBlock.FACING, Direction.SOUTH);
            scene.block(3, 1, 6, vent.setValue(HeatVentBlock.OPEN, true)); scene.block(3, 1, 7, AIR);
            seen = look(scene, eye, 3, 0, 3);
            helper.assertTrue(seen.size() == 1 && seen.getFirst().startsWith("LEAKED@"), "An open vent leaks heat through a thick wall: " + seen);
            scene.block(3, 1, 6, vent);
            helper.assertTrue(look(scene, eye, 3, 0, 3).isEmpty(), "A closed vent keeps it in");
            scene.block(3, 1, 6, vent.setValue(HeatVentBlock.VENTING, true));
            seen = look(scene, eye, 3, 0, 3);
            helper.assertTrue(seen.equals(List.of("DIRECT@3, 1, 6")), "A venting radiator is itself a heat source: " + seen);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroThermalSightEndsAtSixtyFourBlocks(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 213, 5, scene -> {
            scene.block(3, 0, 5, heater());
            var eye = viewer(scene, 3, 5 + 70);
            helper.assertTrue(look(scene, eye, 3, 0, 5).isEmpty(), "Out of thermal range at 70 blocks");
            eye.setPos(scene.position(3, 5 + 60));
            var seen = look(scene, eye, 3, 0, 5);
            helper.assertTrue(seen.equals(List.of("DIRECT@3, 0, 5")), "In range at 60 blocks: " + seen);
        });
    }

    /** Real AI: walks to a cold post, stares, uploads on its first look; killing it afterwards undoes nothing. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pyroWatchesFromTheColdAndItsLookOutlivesIt(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 214, 4, scene -> {
            MaeveScribeGameTest.floor(scene, 60);
            var player = scene.player("pyro_watched", 6, 6); UUID id = player.getUUID();
            BlockPos home = home(scene, id, 10, 10);
            scene.block(12, 0, 8, heater());
            long t = scene.gameTime + 1;
            var actor = scene.architect(54, 14);
            helper.assertTrue(MaeveDirector.designatePyro(actor, player), "Designated");
            actor.becomePyro(); actor.tickCount = 80; actor.setOnGround(true); actor.setDeltaMovement(Vec3.ZERO);
            MaeveScribeGameTest.tick(scene, actor, t, 120);
            double distance = Math.sqrt(actor.blockPosition().distSqr(home));
            helper.assertTrue(actor.isPyroStaring() && actor.getTarget() == null && distance >= 30,
                    "Stands still out in the cold and stares, never approaching: " + distance + " " + actor.position());
            helper.assertTrue(heat(scene, id).isEmpty(), "Nothing is uploaded before the first look");
            MaeveScribeGameTest.tick(scene, actor, t + 120, 220);
            var seen = heat(scene, id);
            helper.assertTrue(seen.size() == 1 && seen.getFirst().position().equals(scene.origin.offset(12, 0, 8))
                    && seen.getFirst().confidence() > .1 && seen.getFirst().confidence() <= PyroPolicy.SEEN + 1e-6, "The first look uploads the heat it saw: " + seen);
            MaeveScribeGameTest.tick(scene, actor, t + 340, 100);
            helper.assertTrue(heat(scene, id).getFirst().evidence() == 1, "Repeated looks from one visit never stack");
            actor.kill();
            MaeveScribeGameTest.tick(scene, actor, t + 440, 30);
            helper.assertTrue(actor.isRemoved() && idle(scene, id), "Death ends the claim");
            helper.assertTrue(heat(scene, id).size() == 1, "Killing it after it has seen undoes nothing");
            helper.assertFalse(MaeveDirector.designatePyro(scene.architect(54, 20), player), "The next Pyro waits for the cooldown");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pyroKilledBeforeItsFirstLookUploadsNothing(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 218, 4, scene -> {
            MaeveScribeGameTest.floor(scene, 60);
            var player = scene.player("pyro_stopped", 6, 6); UUID id = player.getUUID();
            home(scene, id, 10, 10);
            scene.block(12, 0, 8, heater());
            long t = scene.gameTime + 1;
            var actor = scene.architect(54, 14);
            helper.assertTrue(MaeveDirector.designatePyro(actor, player), "Designated");
            actor.becomePyro(); actor.tickCount = 80; actor.setOnGround(true);
            MaeveScribeGameTest.tick(scene, actor, t, 150);
            actor.kill();
            MaeveScribeGameTest.tick(scene, actor, t + 150, 30);
            helper.assertTrue(actor.isRemoved() && idle(scene, id) && heat(scene, id).isEmpty(), "Stopped in time, it reported nothing");
        });
    }

    /** Owner, 2026-10-22: a base read completely with no heat anywhere earns Covered in Mud. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroFindingNoHeatGrantsCoveredInMud(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 215, 3, scene -> {
            var player = awardable(scene, "pyro_mud", 6, 6); UUID id = player.getUUID();
            room(scene, WOOL, false); scene.block(3, 0, 5, heater());
            home(scene, id, 3, 3);
            var actor = scene.architect(3, 40);
            helper.assertTrue(MaeveDirector.designatePyro(actor, player), "Designated");
            actor.becomePyro(); actor.setNoAi(true);
            var first = MaeveDirector.pyroObserve(actor, false);
            helper.assertTrue(first != null && first.complete() && first.sources() == 1 && first.sightings() == 0 && !first.cold(),
                    "The heater is there, but nothing shows: " + first);
            var last = MaeveDirector.pyroObserve(actor, true);
            var advancement = scene.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "covered_in_mud"));
            helper.assertTrue(last != null && last.cold() && advancement != null
                    && player.getAdvancements().getOrStartProgress(advancement).isDone(), "Cold rock. Nothing to see here: " + last);
            helper.assertTrue(heat(scene, id).isEmpty(), "No heat was uploaded");
        });
    }

    /** §9.13a: struck, it fights back; once left alone, it leaves rather than resuming its watch. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 400)
    public static void pyroDefendsWhenStruckThenLeaves(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 216, 4, scene -> {
            MaeveScribeGameTest.floor(scene, 60);
            var player = scene.player("pyro_striker", 54, 12); UUID id = player.getUUID();
            home(scene, id, 10, 10);
            long t = scene.gameTime + 1;
            var actor = scene.architect(54, 14);
            helper.assertTrue(MaeveDirector.designatePyro(actor, player), "Designated");
            actor.becomePyro(); actor.tickCount = 80; actor.setOnGround(true);
            MaeveScribeGameTest.tick(scene, actor, t, 20);
            helper.assertTrue(actor.getTarget() == null, "It never initiates");
            var held = MaeveDirector.snapshot(scene.server, id).beliefs().stream().map(b -> b.pattern() + " " + b.evidence()).toList();
            player.setPos(actor.position().add(2, 0, 0));
            scene.hit(actor, player, false, 1);
            helper.assertTrue(actor.getTarget() == player && !actor.isPyroStaring(), "Struck, it defends itself");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, id).beliefs().stream().map(b -> b.pattern() + " " + b.evidence()).toList().equals(held),
                    "Hits on the Pyro report no evidence");
            MaeveScribeGameTest.tick(scene, actor, t + 400, 2);
            helper.assertTrue(actor.getTarget() == null && !actor.isRemoved() && !actor.isPyroStaring(),
                    "Left alone, defense ends and it walks away while watched");
            player.setPos(scene.position(0, 60));
            MaeveScribeGameTest.tick(scene, actor, t + 402, 2);
            helper.assertTrue(actor.isRemoved() && idle(scene, id) && heat(scene, id).isEmpty(), "Out of sight, it is gone");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 250)
    public static void pyroLeavesWhenErasedWithoutUploading(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 217, 3, scene -> {
            MaeveScribeGameTest.floor(scene, 40);
            var player = scene.player("pyro_distant", 0, 0); UUID id = player.getUUID();
            home(scene, id, 5, 5);
            scene.block(7, 0, 5, heater());
            var actor = scene.architect(38, 38);
            helper.assertTrue(MaeveDirector.designatePyro(actor, player), "Designated");
            actor.becomePyro(); actor.tickCount = 80; actor.setOnGround(true);
            long t = scene.gameTime + 1;
            MaeveScribeGameTest.tick(scene, actor, t, 20);
            helper.assertTrue(!actor.isRemoved() && actor.isPyro(), "A Pyro remains while its claim holds");
            PostMaeveWorldState.setForDebug(scene.server, true);
            MaeveScribeGameTest.tick(scene, actor, t + 20, 20);
            helper.assertTrue(actor.isRemoved(), "After ERASED the Pyro leaves rather than lingering");
            PostMaeveWorldState.setForDebug(scene.server, false);
            helper.assertTrue(heat(scene, id).isEmpty(), "Nothing it might have seen survives ERASED");
        });
    }

    /** The MACS Pyro Check world ships exactly these functions; each must parse at the integrated server's level 2. */
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 40)
    public static void pyroPlaytestFunctionsParseAtPermissionTwo(GameTestHelper helper) {
        MaeveScribeGameTest.assertPlaytestFunctionsParse(helper, "macs_pyro", 19);
    }
}
