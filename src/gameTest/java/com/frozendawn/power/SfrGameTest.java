package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.airlock.AirlockCycle;
import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.block.SfrVacuumPortBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.CombustionAtmosphere;
import com.frozendawn.world.RoomAtmosphere;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.DoorBlock;
import com.frozendawn.block.FirstWallBlock;
import com.frozendawn.block.ReactorControllerBlock;
import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.block.SfrControllerBlockEntity;
import com.frozendawn.block.SfrFuelPortBlockEntity;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.block.PowerStorageBlockEntity;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SfrGameTest {
    private static final BlockPos CENTER = new BlockPos(10, 3, 10);
    @BeforeBatch(batch = "sfr")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }
    private static long oldPhase;
    @BeforeBatch(batch = "sfr_vacuum")
    public static void vacuumStage(ServerLevel level) {
        GameTestReporting.installReporter(level); var a = ApocalypseState.get(level.getServer()); oldPhase = a.getApocalypseTicks();
        a.setApocalypseTicks((long) (a.getTotalDays() * 24000L * .90), level.getServer()); RoomAtmosphere.reset(); CombustionAtmosphere.reset();
    }
    @AfterBatch(batch = "sfr_vacuum")
    public static void restoreStage(ServerLevel level) {
        ApocalypseState.get(level.getServer()).setApocalypseTicks(oldPhase, level.getServer()); RoomAtmosphere.reset(); CombustionAtmosphere.reset();
    }
    @BeforeBatch(batch = "sfr_stage")
    public static void reportStage(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100, skyAccess = true)
    public static void sfrShellValidatesAndNamesTheWrongCell(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            var controller = SfrScenes.build(h, CENTER);
            var result = SfrMultiblock.validate(level, controller, Direction.NORTH);
            h.assertTrue(result.valid(), "Expected a valid torus, got " + result.problem() + " at " + result.wrong());
            h.assertValueEqual(result.center(), h.absolutePos(CENTER), "center");
            h.assertValueEqual(result.coolantPorts().size(), 2, "coolant ports");
            h.assertValueEqual(result.channel().size(), 72, "channel cells");

            var wall = SfrScenes.cell(h, CENTER, 2, 0, 0);
            SfrScenes.set(h, wall, Blocks.STONE.defaultBlockState());
            result = SfrMultiblock.validate(level, controller, Direction.NORTH);
            h.assertValueEqual(result.problem(), SfrMultiblock.Problem.FIRST_WALL, "stone in the First Wall");
            h.assertValueEqual(result.wrong(), wall, "wrong cell");
            SfrScenes.set(h, wall, ModBlocks.FIRST_WALL.get().defaultBlockState());

            var channel = SfrScenes.cell(h, CENTER, 3, 0, 0);
            SfrScenes.set(h, channel, Blocks.DIRT.defaultBlockState());
            result = SfrMultiblock.validate(level, controller, Direction.NORTH);
            h.assertValueEqual(result.problem(), SfrMultiblock.Problem.CHANNEL, "dirt in the channel");
            h.assertValueEqual(result.wrong(), channel, "wrong cell");
            SfrScenes.set(h, channel, Blocks.AIR.defaultBlockState());

            SfrScenes.removeHatch(h, CENTER);
            result = SfrMultiblock.validate(level, controller, Direction.NORTH);
            h.assertValueEqual(result.problem(), SfrMultiblock.Problem.PORTS, "no hatch");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100)
    public static void sfrWornFirstWallDropsNothing(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
            var worn = ModBlocks.FIRST_WALL.get().defaultBlockState().setValue(FirstWallBlock.WEAR, 1);
            h.assertTrue(Block.getDrops(worn, level, pos, null).isEmpty(), "A worn First Wall must drop nothing");
            var drops = Block.getDrops(ModBlocks.FIRST_WALL.get().defaultBlockState(), level, pos, null);
            h.assertValueEqual(drops.size(), 1, "clean First Wall drop count");
            h.assertTrue(drops.get(0).is(ModItems.FIRST_WALL.get()) && drops.get(0).getCount() == 1, "A clean First Wall drops itself");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100, skyAccess = true)
    public static void sfrHighTemperatureRadiatorIsNotACfu7Sink(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = buildCfu7(h);
            if (!(level.getBlockEntity(controller) instanceof ReactorControllerBlockEntity be)) throw new GameTestAssertException("CFU-7 controller missing");
            var port = h.absolutePos(new BlockPos(6, 3, 5)); var radiator = h.absolutePos(new BlockPos(2, 7, 15));
            level.setBlock(radiator, ModBlocks.HIGH_TEMPERATURE_RADIATOR.get().defaultBlockState(), 3);
            h.assertTrue(PowerNetworks.link(level, port, radiator) == PowerLinkRules.Result.OK, "Expected the port to link the radiator");
            be.physics().setCoreC(300); be.second(level);
            h.assertValueEqual(be.radiatorCount(), 0, "CFU-7 sinks through a High-Temperature Radiator");
            // Replacing a linked block drops its links, so the standard radiator is linked afresh.
            level.setBlock(radiator, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState(), 3);
            h.assertTrue(PowerNetworks.link(level, port, radiator) == PowerLinkRules.Result.OK, "Expected the port to link the standard radiator");
            be.second(level);
            h.assertValueEqual(be.radiatorCount(), 1, "CFU-7 sinks through a standard radiator");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 300, skyAccess = true)
    public static void sfrIgnitesToBurningOnABankAndCreativeFuel(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var be = ready(h, 8_000_000);
            h.succeedWhen(() -> {
                h.assertValueEqual(be.physics().state(), FusionPhysics.State.BURNING, "state");
                h.assertTrue(Math.abs(be.physics().tritium() - 3) < 0.01, "Ignition spends one tritium, got " + be.physics().tritium());
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 300, skyAccess = true)
    public static void sfrBankTooSmallAbortsIgnitionCleanly(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var be = ready(h, 1_000_000);
            h.succeedWhen(() -> {
                h.assertValueEqual(be.physics().state(), FusionPhysics.State.READY, "state");
                h.assertValueEqual(be.physics().status(), FusionPhysics.Status.ABORTED, "status");
                h.assertTrue(be.physics().tritium() == 4, "An aborted ignition spends no tritium, got " + be.physics().tritium());
                h.assertTrue(be.wear() == 0, "An aborted ignition adds no wear, got " + be.wear());
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100)
    public static void sfrFuelPortRefusesOtherItems(GameTestHelper h) {
        scene(h, () -> {
            var rel = new BlockPos(2, 2, 2);
            h.setBlock(rel, ModBlocks.SFR_FUEL_PORT.get());
            if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof SfrFuelPortBlockEntity port)) throw new GameTestAssertException("Fuel Port block entity missing");
            h.assertFalse(port.canPlaceItem(0, new ItemStack(Items.DIRT)), "Fuel Port accepts dirt");
            h.assertFalse(port.canPlaceItem(0, new ItemStack(ModItems.TRITIUM_CANISTER.get())), "Fuel Port accepts a Tritium Canister");
            h.assertTrue(port.canPlaceItem(0, new ItemStack(ModItems.DEUTERIUM_CANISTER.get())), "Fuel Port refuses a Deuterium Canister");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 300, skyAccess = true)
    public static void sfrControllerTagRoundTripKeepsBurning(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var be = ready(h, 8_000_000);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertValueEqual(be.physics().state(), FusionPhysics.State.BURNING, "state"))
                    .thenExecute(() -> {
                        var level = h.getLevel(); var registries = level.registryAccess();
                        var tag = be.saveWithFullMetadata(registries);
                        var fresh = new SfrControllerBlockEntity(be.getBlockPos(), be.getBlockState());
                        fresh.loadWithComponents(tag, registries);
                        level.setBlockEntity(fresh);
                        fresh.second(level);
                        h.assertValueEqual(fresh.physics().state(), FusionPhysics.State.BURNING, "state after reload");
                        h.assertFalse(fresh.readout().disrupted(), "Reloading a burning controller disrupts it");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 200)
    public static void sfrChannelPumpsDownOutsideTheVacuumStage(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level = h.getLevel(); var be = controller(h, SfrScenes.build(h, CENTER));
            bank(h, 1_000_000, SfrScenes.cell(h, CENTER, 2, 0, -4));
            h.assertTrue(be.channelPressure() == 1, "A fresh torus starts at nominal pressure");
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(be.channelPressure() < 1 - 0.5 / 150, "The paid pump lowers pressure, at " + be.channelPressure()))
                    .thenExecute(() -> {
                        for (int s = 0; s < 145; s++) be.second(level);
                        h.assertTrue(be.channelPressure() <= 0.05, "Pumped down within 145 s, at " + be.channelPressure());
                    })
                    .thenSucceed();
        });
    }

    /** A real hand click on a block, as AirlockGameTest's doorClick does; GameTestHelper.useBlock does not reach the hatch. */
    private static void click(GameTestHelper h, BlockPos pos) {
        var actor = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), actor, new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.SOUTH, pos, false));
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100)
    public static void sfrHatchRefusesAcrossAPressureDifference(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var be = controller(h, SfrScenes.build(h, CENTER));
            be.setChannelPressure(0); be.second(level);
            var hatch = be.shell().hatch();
            click(h, hatch);
            h.assertFalse(level.getBlockState(hatch).getValue(DoorBlock.OPEN), "The hatch opens across a pressure difference");
            var port = be.shell().vacuumPort();
            level.setBlock(port, level.getBlockState(port).setValue(SfrVacuumPortBlock.MODE, SfrVacuumPortBlock.Mode.VENT), 3);
            for (int s = 0; s < 10; s++) be.second(level);
            h.assertTrue(be.channelPressure() == 1, "Vent reaches outside pressure, at " + be.channelPressure());
            click(h, hatch);
            h.assertTrue(level.getBlockState(hatch).getValue(DoorBlock.OPEN), "The hatch stays shut once pressures match");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr", timeoutTicks = 100)
    public static void sfrOpenHatchShowsTorusOpenAndBlocksIgnition(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var be = controller(h, SfrScenes.build(h, CENTER));
            be.second(level);
            var hatch = be.shell().hatch();
            level.setBlock(hatch, level.getBlockState(hatch).setValue(DoorBlock.OPEN, true), 3);
            be.physics().setReadyForTest(); be.physics().start(); be.second(level);
            h.assertValueEqual(be.physics().status(), FusionPhysics.Status.TORUS_OPEN, "status");
            h.assertFalse(be.physics().state() == FusionPhysics.State.IGNITION, "Ignition starts with the torus open");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_vacuum", timeoutTicks = 400)
    public static void sfrVacuumPortExhaustsIntoTheOuterRoom(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level = h.getLevel(); var be = controller(h, SfrScenes.build(h, CENTER));
            var room = outerRoom(h); var gas = AirlockSavedState.get(level);
            int capacity = room.size() * AirlockCycle.UNITS_PER_CELL;
            gas.setRoomGas(level, room, capacity / 2);
            bank(h, 1_000_000, SfrScenes.cell(h, CENTER, 2, 0, -4));
            be.second(level);
            var channel = be.shell().channel();
            h.assertTrue(be.shell().valid(), "Torus is valid: " + be.shell().problem());
            int channel0 = gas.roomGas(level, channel), room0 = gas.roomGas(level, room);
            h.assertTrue(channel0 > 0, "The sealed channel enters the vacuum stage holding gas");
            h.runAfterDelay(200, () -> {
                int channel1 = gas.roomGas(level, channel), room1 = gas.roomGas(level, room);
                h.assertTrue(room1 > room0, "The outer room gains gas: " + room0 + " -> " + room1);
                h.assertValueEqual(channel0 - channel1, room1 - room0, "gas the outer room gained against gas the channel lost");
                for (int s = 0; s < 60; s++) be.second(level);
                h.assertValueEqual(gas.roomGas(level, room), capacity, "outer room gas once full");
                h.assertTrue(channel0 - gas.roomGas(level, channel) > capacity - room0, "Gas the full room refuses is lost");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_stage", timeoutTicks = 100)
    public static void sfrPressureSurvivesEnteringTheVacuumStage(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var be = controller(h, SfrScenes.build(h, CENTER));
            be.setChannelPressure(0.4); be.second(level);
            var apoc = ApocalypseState.get(level.getServer()); long previous = apoc.getApocalypseTicks();
            try {
                apoc.setApocalypseTicks((long) (apoc.getTotalDays() * 24000L * .90), level.getServer());
                h.assertTrue(CombustionAtmosphere.isVacuum(level), "Fixture is the vacuum stage");
                be.second(level);
                h.assertTrue(Math.abs(be.channelPressure() - 0.4) < 0.01, "Pressure across the crossing, got " + be.channelPressure());
                int expected = (int) Math.round(0.4 * be.shell().channel().size() * AirlockCycle.UNITS_PER_CELL);
                h.assertTrue(Math.abs(AirlockSavedState.get(level).roomGas(level, be.shell().channel()) - expected) <= 1, "The channel's room gas carries the pressure");
            } finally { apoc.setApocalypseTicks(previous, level.getServer()); RoomAtmosphere.reset(); CombustionAtmosphere.reset(); }
        });
    }

    private static SfrControllerBlockEntity controller(GameTestHelper h, BlockPos pos) {
        if (!(h.getLevel().getBlockEntity(pos) instanceof SfrControllerBlockEntity be)) throw new GameTestAssertException("SFR-1 controller block entity missing");
        return be;
    }
    /** Banks holding joules beside the template's west edge, each linked to target. */
    private static void bank(GameTestHelper h, int joules, BlockPos target) {
        var level = h.getLevel(); int left = joules;
        for (int i = 0; left > 0; i++) {
            var bank = h.absolutePos(new BlockPos(4 + i * 2, 1, 1));
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(bank) instanceof PowerStorageBlockEntity store)) throw new GameTestAssertException("Bank block entity missing");
            for (int cell = 0; cell < store.getContainerSize() && left > 0; cell++) {
                int amount = Math.min(left, PowerCellItem.CAPACITY_JOULES);
                store.setItem(cell, PowerCellItem.charged(amount)); left -= amount;
            }
            h.assertTrue(PowerNetworks.link(level, bank, target) == PowerLinkRules.Result.OK, "Expected a power link from " + bank);
        }
    }
    /** A sealed 3x3x3 room on the Vacuum Port's outer face; returns its air cells. */
    private static Set<BlockPos> outerRoom(GameTestHelper h) {
        var level = h.getLevel(); var cells = new HashSet<BlockPos>();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = 4; dz <= 8; dz++) {
            var pos = SfrScenes.cell(h, CENTER, dx, dy, dz);
            boolean inside = Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && dz >= 5 && dz <= 7;
            if (inside) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); cells.add(pos.immutable()); }
            else if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        return cells;
    }

    /** Torus, 14 High-Temperature Radiators on its roof, banks holding bankJoules on the Power Port and
     * 4 Deuterium Canisters; READY with ignition requested and 4 tritium. */
    private static SfrControllerBlockEntity ready(GameTestHelper h, int bankJoules) {
        var level = h.getLevel(); var controller = SfrScenes.build(h, CENTER);
        var ports = new BlockPos[] { SfrScenes.cell(h, CENTER, -4, 0, 0), SfrScenes.cell(h, CENTER, 4, 0, 0) };
        int n = 0;
        for (int dx = -4; dx <= 4; dx += 2) for (int dz = -4; dz <= 4; dz += 4) {
            if (n == 14) break;
            var radiator = SfrScenes.cell(h, CENTER, dx, 4, dz);
            level.setBlock(radiator, ModBlocks.HIGH_TEMPERATURE_RADIATOR.get().defaultBlockState(), 3);
            h.assertTrue(PowerNetworks.link(level, ports[n++ % 2], radiator) == PowerLinkRules.Result.OK, "Expected a coolant link to " + radiator);
        }
        bank(h, bankJoules, SfrScenes.cell(h, CENTER, 2, 0, -4));
        if (!(level.getBlockEntity(SfrScenes.cell(h, CENTER, -2, 0, -4)) instanceof SfrFuelPortBlockEntity fuel)) throw new GameTestAssertException("Fuel Port block entity missing");
        fuel.setItem(0, new ItemStack(ModItems.DEUTERIUM_CANISTER.get(), 4));
        if (!(level.getBlockEntity(controller) instanceof SfrControllerBlockEntity be)) throw new GameTestAssertException("SFR-1 controller block entity missing");
        be.physics().addTritium(4); be.setChannelPressure(0); be.physics().setReadyForTest(); be.physics().start();
        return be;
    }

    /** The CFU-7 shell from ReactorGameTest, core at (5,3,5), ports east and south, controller north. */
    private static BlockPos buildCfu7(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var rel = new BlockPos(5 + dx, 3 + dy, 5 + dz);
            Block block = ModBlocks.REACTOR_CASING.get();
            if (dx == 0 && dz == 0 && dy == 1) block = ModBlocks.CONTROL_ROD_DRIVE.get();
            else if (dx == 0 && dy == 0 && dz == 0) block = ModBlocks.REACTOR_CORE.get();
            else if (dy == 0 && (dx == 1 && dz == 0 || dx == 0 && dz == 1)) block = ModBlocks.COOLANT_PORT.get();
            else if (dy == 0 && dx == 0 && dz == -1) {
                h.setBlock(rel, ModBlocks.REACTOR_CONTROLLER.get().defaultBlockState().setValue(ReactorControllerBlock.FACING, Direction.NORTH));
                continue;
            }
            h.setBlock(rel, block);
        }
        return h.absolutePos(new BlockPos(5, 3, 4));
    }

    private static void scene(GameTestHelper h, Runnable body) { h.runAfterDelay(1, () -> { body.run(); h.succeed(); }); }
}
