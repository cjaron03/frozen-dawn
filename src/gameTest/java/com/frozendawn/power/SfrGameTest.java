package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
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
        var powerPort = SfrScenes.cell(h, CENTER, 2, 0, -4);
        int left = bankJoules;
        for (int i = 0; left > 0; i++) {
            var bank = h.absolutePos(new BlockPos(4 + i * 2, 1, 1));
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(bank) instanceof PowerStorageBlockEntity store)) throw new GameTestAssertException("Bank block entity missing");
            for (int cell = 0; cell < store.getContainerSize() && left > 0; cell++) {
                int joules = Math.min(left, PowerCellItem.CAPACITY_JOULES);
                store.setItem(cell, PowerCellItem.charged(joules)); left -= joules;
            }
            h.assertTrue(PowerNetworks.link(level, bank, powerPort) == PowerLinkRules.Result.OK, "Expected a power link from " + bank);
        }
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
