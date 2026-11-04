package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.HyperionControllerBlock;
import com.frozendawn.block.HyperionMultiblock;
import com.frozendawn.block.SuperconductingCellBlock;
import com.frozendawn.block.SuperconductingCellBlock.Look;
import com.frozendawn.block.HyperionMultiblock.Problem;
import com.frozendawn.gametest.*;
import com.frozendawn.init.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HyperionGameTest {
    private static final BlockPos MIN = new BlockPos(3, 2, 3);
    @BeforeBatch(batch = "hyperion")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionShellValidatesSizesAndCountsCells(GameTestHelper h) {
        int[][] sizes = {{4, 32}, {6, 50}, {8, 68}};
        for (var s : sizes) {
            clear(h);
            var b = HyperionScenes.silo(h, MIN, s[0], 1, 1);
            var r = HyperionMultiblock.discover(h.getLevel(), b.controller());
            h.assertTrue(r.valid(), "3x3x" + s[0] + " is valid, got " + r.problem() + " at " + r.wrong());
            h.assertTrue(r.cells() == s[1], "Counts " + s[1] + " cells, got " + r.cells());
            h.assertTrue(r.ports().equals(b.ports()) && r.coolers().equals(b.coolers()) && r.vents().equals(b.vents()), "Finds its fittings");
            h.assertTrue(r.min().equals(b.min()) && r.max().equals(b.max()) && r.height() == s[0], "Finds its box");
            h.assertTrue(r.front() == Direction.NORTH, "The Controller's facing is the front: " + r.front());
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionSiloNamesTheWrongPart(GameTestHelper h) {
        var l = h.getLevel();
        var b = fresh(h, 4); var at = b.min().offset(0, 1, 0); l.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
        expect(h, b, Problem.WALL, at);
        b = fresh(h, 4); at = b.min().offset(1, 1, 1); l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        expect(h, b, Problem.INTERIOR, at);
        b = fresh(h, 4); at = b.min().offset(1, 2, 0); HyperionScenes.place(h, at, ModBlocks.HYPERION_PORT.get(), Direction.NORTH);
        expect(h, b, Problem.GAUGE, at);
        b = fresh(h, 4); at = b.min().offset(1, 3, 1); l.setBlock(at, ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        expect(h, b, Problem.VENTS, at);
        b = fresh(h, 4); at = b.vents().getFirst(); HyperionScenes.place(h, at, ModBlocks.QUENCH_VENT.get(), Direction.NORTH);
        expect(h, b, Problem.VENTS, at);
        b = fresh(h, 4); at = b.min().offset(0, 3, 0); HyperionScenes.place(h, at, ModBlocks.QUENCH_VENT.get(), Direction.UP);
        expect(h, b, Problem.VENTS, at);
        b = fresh(h, 4); at = b.min().offset(0, 2, 1); HyperionScenes.place(h, at, ModBlocks.CRYOCOOLER.get(), Direction.WEST);
        expect(h, b, Problem.COOLERS, at);
        b = fresh(h, 4); at = b.min().offset(0, 0, 1); HyperionScenes.place(h, at, ModBlocks.CRYOCOOLER.get(), Direction.DOWN);
        expect(h, b, Problem.COOLERS, at);
        b = fresh(h, 4); l.setBlock(b.coolers().getFirst(), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        expect(h, b, Problem.COOLERS, null);
        b = fresh(h, 4); l.setBlock(b.ports().getFirst(), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        expectAny(h, b, Problem.PORTS);
        for (var facing : new Direction[]{Direction.UP, Direction.DOWN, Direction.SOUTH}) {
            b = fresh(h, 4); HyperionScenes.place(h, b.controller(), ModBlocks.HYPERION_CONTROLLER.get(), facing);
            expect(h, b, Problem.CONTROLLERS, b.controller());
        }
        // Moved to the top layer, or to a front corner: either way the front-centre bottom is a cell and the Controller is wrong.
        for (var moved : new BlockPos[]{new BlockPos(1, 3, 0), new BlockPos(0, 0, 0)}) {
            b = fresh(h, 4); at = b.min().offset(moved);
            l.setBlock(b.controller(), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
            HyperionScenes.place(h, at, ModBlocks.HYPERION_CONTROLLER.get(), Direction.NORTH);
            var r = HyperionMultiblock.discover(l, at);
            h.assertTrue(r.problem() == Problem.CONTROLLERS && at.equals(r.wrong()), "A Controller at " + moved + " is wrong, got " + r.problem() + " at " + r.wrong());
        }
        b = fresh(h, 4); HyperionScenes.place(h, b.min().offset(1, 3, 2), ModBlocks.HYPERION_CONTROLLER.get(), Direction.SOUTH);
        expectAny(h, b, Problem.CONTROLLERS);
        clear(h); expectAny(h, HyperionScenes.silo(h, MIN, 9, 1, 1), Problem.TOO_TALL);
        clear(h); expectAny(h, HyperionScenes.silo(h, MIN, 3, 1, 1), Problem.TOO_SHORT);
        b = fresh(h, 4);
        for (int y = 0; y < 4; y++) l.setBlock(b.min().offset(3, y, 1), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        expectAny(h, b, Problem.FOOTPRINT);
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "hyperion", timeoutTicks = 20)
    public static void hyperionPartsRefusePistons(GameTestHelper h) {
        for (var b : List.of(ModBlocks.SUPERCONDUCTING_CELL, ModBlocks.HYPERION_PORT, ModBlocks.CRYOCOOLER,
                ModBlocks.QUENCH_VENT, ModBlocks.HYPERION_CONTROLLER))
            h.assertTrue(b.get().defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK, b.getId() + " refuses pistons");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionPortsCapTheRatePerInterval(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b);
        c.setStoredForTest(10_000_000);
        h.assertTrue(c.drawEnergy(1_000_000) == 500_000 && c.drawEnergy(1) == 0, "One port draws 500 kJ per interval");
        h.assertTrue(c.availableEnergy() == 0, "Nothing more is available this interval");
        h.runAfterDelay(20, () -> {
            h.assertTrue(c.drawEnergy(1_000_000) == 500_000, "The next interval draws again");
            c.setStoredForTest(c.capacity() - 100);
            h.assertTrue(c.chargeEnergy(1_000_000) == 100 && c.storedEnergy() == c.capacity(), "Charging stops at capacity");
            clear(h); var two = HyperionScenes.silo(h, MIN, 4, 2, 1); var d = controller(h, two);
            h.assertTrue(d.chargeEnergy(3_000_000) == 1_000_000 && d.chargeEnergy(1) == 0, "Two ports charge 1 MJ per interval");
            d.setStoredForTest(5_000_000);
            h.assertTrue(d.drawEnergy(3_000_000) == 1_000_000, "Two ports draw 1 MJ per interval");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionPowersALampAndMergesNetworks(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 4, 2, 1); var c = controller(h, b);
        var lampA = place(h, new BlockPos(1, 2, 1), ModBlocks.PANEL_LIGHT.get()); var lampB = place(h, new BlockPos(12, 2, 12), ModBlocks.PANEL_LIGHT.get());
        link(h, b.ports().get(0), lampA); link(h, b.ports().get(1), lampB);
        c.setStoredForTest(1_000_000); account(l);
        h.assertTrue(lit(l, lampA) && lit(l, lampB), "The Hyperion powers both lamps");
        h.assertTrue(c.storedEnergy() == 1_000_000 - 200, "It pays exactly one second of both lamps: " + c.storedEnergy());
        h.assertTrue(PowerNetworks.component(l, lampA).contains(lampB), "Its ports join one network");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionLongEnergySurvivesSaveAndMonitor(GameTestHelper h) {
        // One silo tops out near 1.4 GJ, so the save holds an uncapped test charge and the monitor sums two full ones.
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 8, 1, 1); var c = controller(h, b);
        c.setStoredForTest(5_000_000_000L);
        var saved = c.saveWithFullMetadata(l.registryAccess());
        var restored = new com.frozendawn.block.HyperionControllerBlockEntity(b.controller(), c.getBlockState()); restored.loadWithComponents(saved, l.registryAccess());
        h.assertTrue(restored.stored() == 5_000_000_000L, "Past 2.1 GJ survives a save: " + restored.stored());
        var other = HyperionScenes.silo(h, MIN.offset(5, 0, 0), 8, 1, 1); var d = controller(h, other);
        c.setStoredForTest(c.capacity()); d.setStoredForTest(d.capacity());
        var relay = place(h, new BlockPos(1, 2, 1), ModBlocks.POWER_RELAY.get()); var monitor = place(h, new BlockPos(1, 2, 12), ModBlocks.POWER_MONITOR.get());
        link(h, relay, b.ports().getFirst()); link(h, relay, other.ports().getFirst()); link(h, relay, monitor); account(l);
        var m = PowerNetworks.monitorReading(l, monitor);
        h.assertTrue(m.storedJoules() == 2_720_000_000L && m.capacityJoules() == 2_720_000_000L && m.storedJoules() > Integer.MAX_VALUE,
                "Monitor reads " + m.storedJoules() + " / " + m.capacityJoules());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionInvalidShellStopsService(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b);
        h.assertTrue(PowerNetworks.store(l, b.ports().getFirst()).isPresent(), "A formed port reaches its store");
        l.setBlock(b.min().offset(1, 1, 0), Blocks.AIR.defaultBlockState(), 3); c.second(l);
        h.assertTrue(c.availableEnergy() == 0 && !c.acceptsCharge(), "A broken shell neither gives nor takes");
        h.assertTrue(PowerNetworks.store(l, b.ports().getFirst()).isEmpty(), "Its port reaches nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionLoadFollowAsksForPortHeadroom(GameTestHelper h) {
        var l = h.getLevel();
        var empty = controller(h, HyperionScenes.silo(h, MIN, 4, 1, 1));
        var fullBox = HyperionScenes.silo(h, new BlockPos(7, 2, 11), 4, 2, 1); var full = controller(h, fullBox);
        var relay = place(h, new BlockPos(1, 2, 1), ModBlocks.POWER_RELAY.get());
        var bankPos = place(h, new BlockPos(1, 2, 8), ModBlocks.BATTERY_BANK.get());
        var bank = (com.frozendawn.block.PowerStorageBlockEntity) l.getBlockEntity(bankPos); bank.setItem(0, com.frozendawn.item.PowerCellItem.charged(0));
        full.setStoredForTest(full.capacity());
        link(h, relay, empty.shell().ports().getFirst()); link(h, relay, fullBox.ports().getFirst()); link(h, relay, bankPos);
        account(l);
        long target = PowerNetworks.loadFollowTargetWatts(l, relay, 0);
        h.assertTrue(target == 5_000 + 500_000, "Asks for the empty Hyperion's one port plus the bank margin, got " + target);
        empty.setStoredForTest(empty.capacity()); for (int i = 0; i < 4; i++) bank.setItem(i, com.frozendawn.item.PowerCellItem.charged(500_000));
        h.runAfterDelay(20, () -> {
            account(l);
            long after = PowerNetworks.loadFollowTargetWatts(l, relay, 0);
            h.assertTrue(after == 0, "Everything full asks for nothing, got " + after);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionHeatQuenchFollowsTheLadder(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var c = controller(h, HyperionScenes.silo(h, MIN, 8, 1, 1));
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(20.0); int before = quenches(l);
        for (int i = 0; i < 299; i++) c.second(l);
        h.assertTrue(c.stored() > 0 && c.readout().stage() == HyperionPhysics.Stage.FINAL, "Still holding in the final seconds: " + c.readout().stage());
        c.second(l);
        h.assertTrue(c.stored() == 0 && quenches(l) == before + 1, "Quenches at five minutes in a warm room");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionEnoughCoolingHoldsAndDrainsSlowly(GameTestHelper h) {
        var l = h.getLevel(); var c = controller(h, fresh(h, 4)); long full = c.capacity();
        c.setStoredForTest(full); c.setSurroundingsForTest(20.0); int before = quenches(l);
        for (int i = 0; i < 3600; i++) c.second(l);
        long lost = full - c.stored();
        h.assertTrue(Math.abs(lost - full / 50) <= full / 5000 && quenches(l) == before, "Loses 2% an hour in a warm room: " + lost);
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionOutageNeverQuenches(GameTestHelper h) {
        var l = h.getLevel(); var c = controller(h, fresh(h, 4));
        c.setStoredForTest(1_000_000); c.setSurroundingsForTest(20.0); int before = quenches(l);
        int guard = 0; while (c.stored() > 0 && guard++ < 5000) c.second(l);
        for (int i = 0; i < 400; i++) c.second(l);
        h.assertTrue(c.stored() == 0 && quenches(l) == before && c.readout().stage() == HyperionPhysics.Stage.WARM, "Drains, warms, never vents");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionWarmBuildCoolsOnGridBeforeCharging(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(b.controller());
        c.setSurroundingsForTest(20.0); c.second(l);
        h.assertTrue(c.shell().valid() && c.physics().stage() == HyperionPhysics.Stage.WARM, "A warm build forms warm");
        long grant = c.chargeEnergy(1_000_000);
        h.assertTrue(grant <= HyperionPhysics.cooldownWatts(32) && c.stored() == 0, "Warm cells only take cool-down power: " + grant);
        h.assertTrue(c.chargeHeadroomWatts() == HyperionPhysics.cooldownWatts(32), "Load-follow asks for the cool-down");
        c.second(l);
        for (int i = 1; i < 55; i++) { c.chargeEnergy(1_000_000); c.second(l); }
        h.assertTrue(c.physics().stage() == HyperionPhysics.Stage.COLD, "Cold within a minute of full grants: " + c.physics().cellC());
        h.assertTrue(c.chargeEnergy(1_000_000) > 0 && c.stored() > 0, "Then it stores");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionBreakingAChargedPartQuenches(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.min().offset(1, 1, 0), true);
        h.assertTrue(c.stored() == 0 && quenches(l) == before + 1, "A charged battery vents the moment a part breaks");
        h.succeed();
    }

    /** Review finding: an empty battery warns above -180C; taking it apart must put out its cracks and lamps. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionQuietBreakClearsTheWarningLook(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b);
        c.setSurroundingsForTest(20.0); c.second(l);
        h.assertTrue(c.readout().stage() == HyperionPhysics.Stage.CHIRP && c.stored() == 0, "An empty battery in a warm room warns: " + c.readout().stage());
        l.destroyBlock(b.min().offset(1, 1, 0), false);
        var cell = b.min().offset(1, 1, 2);
        h.assertTrue(l.getBlockState(cell).getValue(SuperconductingCellBlock.LOOK) == Look.LOOSE, "The cells left behind go loose: " + l.getBlockState(cell));
        h.assertTrue(!l.getBlockState(b.ports().get(0)).getValue(HyperionControllerBlock.ALERT) && !l.getBlockState(b.controller()).getValue(HyperionControllerBlock.ALERT), "Its lamps go out");
        b = fresh(h, 4); c = controller(h, b); c.setSurroundingsForTest(20.0); c.second(l);
        l.destroyBlock(b.controller(), false);
        h.assertTrue(l.getBlockState(cell).getValue(SuperconductingCellBlock.LOOK) == Look.LOOSE && !l.getBlockState(b.ports().get(0)).getValue(HyperionControllerBlock.ALERT),
                "Breaking the Controller of a warning empty battery does the same");
        h.succeed();
    }

    /** Review finding: a broken silo kept showing its window and rings for a second or two. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionBrokenSiloStopsShowingAtOnce(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b);
        h.assertTrue(c.snapshot().height() == 4, "Formed, the client sees a 4-tall: " + c.snapshot().height());
        l.destroyBlock(b.min().offset(1, 1, 0), false);
        h.assertTrue(c.snapshot().height() == 0 && c.snapshot().problem() != Problem.NONE, "Broken, it stops showing the same tick: " + c.snapshot());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionEmptyBatteryComesApartQuietly(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); controller(h, b); int before = quenches(l);
        var cell = b.min().offset(1, 1, 0); l.destroyBlock(cell, true);
        var drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cell).inflate(2));
        h.assertTrue(quenches(l) == before && drops.stream().anyMatch(e -> e.getItem().is(com.frozendawn.init.ModItems.SUPERCONDUCTING_CELL)), "An empty battery drops its cell quietly");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionControllerRemovalQuenchesOnlyWhenCharged(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.controller(), true);
        h.assertTrue(quenches(l) == before + 1, "Breaking a charged Controller vents");
        h.assertTrue(com.frozendawn.block.HyperionVentJets.venting(l, b.vents().get(0)), "The vents jet even with the Controller gone");
        var scar = b.min().offset(1, 1, 0);
        h.assertTrue(l.getBlockState(scar).getValue(SuperconductingCellBlock.LOOK) == Look.LOOSE, "A broken silo is no longer a silo: its cells go loose");
        b = fresh(h, 4); controller(h, b); before = quenches(l);
        l.destroyBlock(b.controller(), true);
        var drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(b.controller()).inflate(2));
        h.assertTrue(quenches(l) == before && drops.stream().anyMatch(e -> e.getItem().is(com.frozendawn.init.ModItems.HYPERION_CONTROLLER)), "An empty Controller drops quietly");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionMultiBreakQuenchesOnce(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.min().offset(1, 1, 0), false); l.destroyBlock(b.min().offset(0, 1, 1), false); l.destroyBlock(b.max().offset(-1, -1, 0), false);
        h.assertTrue(quenches(l) == before + 1, "Three breaks in one tick vent once: " + (quenches(l) - before));
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionNeighborBuildDoesNotQuench(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        HyperionScenes.silo(h, MIN.offset(3, 0, 0), 4, 1, 1);
        l.setBlock(b.min().offset(1, 1, -1), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        c.second(l);
        h.assertTrue(c.shell().valid() && c.stored() == 10_000_000 && quenches(l) == before, "Building beside a charged battery leaves it alone: " + c.shell().problem());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionHotNeighborCounts(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4); var c = controller(h, b);
        var spot = b.min().offset(1, 1, -1);
        l.setBlock(spot, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState().setValue(com.frozendawn.block.ReactorRadiatorBlock.HOT, true), 3);
        h.assertTrue(c.hotNeighbors(l) == 1, "A hot radiator against a face counts");
        l.setBlock(spot, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState(), 3);
        h.assertTrue(c.hotNeighbors(l) == 0, "A cold one does not");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionSaveReloadMidWarningResumes(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 8, 1, 1); var c = controller(h, b);
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(20.0); int before = quenches(l);
        int guard = 0; do c.second(l); while (c.readout().stage() != HyperionPhysics.Stage.WARNING && guard++ < 400);
        var saved = c.saveWithFullMetadata(l.registryAccess());
        var copy = new com.frozendawn.block.HyperionControllerBlockEntity(b.controller(), c.getBlockState()); copy.setLevel(l); copy.loadWithComponents(saved, l.registryAccess());
        h.assertTrue(copy.physics().stage() == HyperionPhysics.Stage.WARNING && copy.physics().cellC() == c.physics().cellC(), "Stage and cell temperature survive");
        copy.setSurroundingsForTest(20.0); copy.second(l);
        h.assertTrue(copy.stored() > 0 && quenches(l) == before, "Reloading never quenches by itself");
        h.succeed();
    }

    /** A chunk holding only ports can unload and come back while the controller stays: nothing else would set the link again. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionPortReloadedAloneFindsItsControllerAgain(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 6, 1, 1); var c = controller(h, b); c.second(l);
        var at = b.ports().getFirst(); var state = l.getBlockState(at);
        l.removeBlockEntity(at);
        var port = new com.frozendawn.block.HyperionPortBlockEntity(at, state);
        l.setBlockEntity(port);
        h.assertTrue(port.controller().isEmpty(), "A port fresh from its chunk knows no controller");
        c.second(l);
        h.assertTrue(port.controller().isPresent(), "The next second finds it");
        h.succeed();
    }

    /** Review focus: after a reload the client must still see the scorch and resting rings, not a fresh silo. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionSnapshotRoundTripsSiloFacts(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 6, 1, 2); var c = controller(h, b);
        c.setStoredForTest(100_000_000L); c.setSurroundingsForTest(-10.0); int before = quenches(l);
        l.setBlock(b.min().offset(-1, 2, 1), Blocks.LAVA.defaultBlockState(), 3);
        int guard = 0; do c.second(l); while (quenches(l) == before && guard++ < 200);
        h.assertTrue(quenches(l) == before + 1, "Lava against the side quenches it");
        var copy = new com.frozendawn.block.HyperionControllerBlockEntity(b.controller(), c.getBlockState());
        copy.handleUpdateTag(c.getUpdateTag(l.registryAccess()), l.registryAccess());
        var s = copy.snapshot();
        h.assertTrue(s.height() == 6 && s.scarred() && s.coolers().equals(b.coolers()), "The client sees the silo's facts: " + s.height() + ", " + s.scarred() + ", " + s.coolers());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionControllerExplainsAnIncompleteShell(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 4);
        l.setBlock(b.vents().getFirst(), ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        var c = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(b.controller()); c.second(l);
        h.assertTrue("hyperion.frozendawn.problem.vents".equals(c.problemKey()), "Explains the missing vent: " + c.problemKey());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionCellsShowFormedAndScorched(GameTestHelper h) {
        // One cooler loses 68 charged cells at 20C.
        var l = h.getLevel(); clear(h); var b = HyperionScenes.silo(h, MIN, 8, 1, 1);
        expectLook(h, b, Look.LOOSE, false, "Built but not yet formed, every cell is loose");
        var c = controller(h, b);
        expectLook(h, b, Look.FORMED, false, "Formed in deep cold, every cell joins the silo");
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(20.0); c.second(l);
        h.assertTrue(c.readout().stage() == HyperionPhysics.Stage.CHIRP, "Chirps first: " + c.readout().stage());
        expectLook(h, b, Look.FORMED, true, "Heat lights the alert but leaves the cells formed");
        int guard = 0; do c.second(l); while (c.stored() > 0 && guard++ < 400);
        expectLook(h, b, Look.SCORCHED, false, "A quench scorches the cells while they are warm");
        c.setSurroundingsForTest(-250.0);
        guard = 0; do { c.chargeEnergy(1_000_000_000L); c.second(l); } while (c.readout().stage() != HyperionPhysics.Stage.COLD && guard++ < 400);
        expectLook(h, b, Look.FORMED, false, "Cold again, the scorch clears");
        var small = fresh(h, 4); controller(h, small);
        l.destroyBlock(small.min().offset(1, 1, 0), false);
        expectLook(h, small, Look.LOOSE, false, "Breaking an empty silo leaves loose cells");
        h.succeed();
    }
    private static void expectLook(GameTestHelper h, HyperionScenes.Built b, Look look, boolean alert, String why) {
        var l = h.getLevel(); var seen = new java.util.HashSet<Look>();
        for (var p : BlockPos.betweenClosed(b.min(), b.max())) {
            var s = l.getBlockState(p);
            if (s.hasProperty(SuperconductingCellBlock.LOOK)) seen.add(s.getValue(SuperconductingCellBlock.LOOK));
        }
        h.assertTrue(seen.equals(java.util.Set.of(look)), why + ": " + seen);
        boolean controllerAlert = l.getBlockState(b.controller()).getValue(HyperionControllerBlock.ALERT);
        h.assertTrue(controllerAlert == alert, why + ": controller alert " + controllerAlert);
        for (var port : b.ports()) h.assertTrue(l.getBlockState(port).getValue(HyperionControllerBlock.ALERT) == alert, why + ": port alert");
    }

    /** The lab command's scene. Its own batch: the SFR-1 and two batteries fill the whole template. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion_lab", timeoutTicks = 100, skyAccess = true)
    public static void hyperionLabPlacesAValidPairAndSfr(GameTestHelper h) {
        var l = h.getLevel();
        var lab = com.frozendawn.world.HyperionLabPlacement.place(l, h.absolutePos(new BlockPos(10, 1, 20)));
        h.assertTrue(lab.failedLinks() == 0, "Every lab link is made: " + lab.failedLinks() + " failed");
        var big = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(lab.outdoor());
        var small = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(lab.heated());
        h.assertTrue(big.shell().valid() && small.shell().valid(), "Both batteries form: " + big.shell().problem() + ", " + small.shell().problem());
        h.assertTrue(big.portCount() == 2 && big.shell().coolers().size() == 2 && big.stored() == big.capacity() / 2, "The outdoor battery has 2 ports, 2 coolers and half its charge");
        h.assertTrue(big.shell().height() == 6 && big.capacity() == HyperionPhysics.capacity(48), "The outdoor battery is a 3x3x6 of 48 cells");
        h.assertTrue(small.shell().height() == 4 && small.shell().coolers().size() == 1 && small.capacity() == HyperionPhysics.capacity(32), "The heated one is a 3x3x4 with one cooler");
        h.assertTrue(big.physics().stage() == HyperionPhysics.Stage.COLD, "It starts cold, ready to pay the ignition");
        account(l);
        h.assertTrue(PowerNetworks.component(l, lab.sfrPowerPort()).stream().anyMatch(big.shell().ports()::contains), "The SFR-1 Power Port shares the battery's network");
        h.assertTrue(PowerNetworks.stores(l, PowerNetworks.component(l, lab.sfrPowerPort())).size() == 1, "No Battery Banks: the Hyperion is the only store");
        h.succeed();
    }

    /** Every state of every Hyperion block has a blockstate variant, and every variant names only real properties. */
    @GameTest(template = GameTestTemplates.EMPTY, batch = "hyperion", timeoutTicks = 20)
    public static void hyperionBlockstatesNameRealProperties(GameTestHelper h) {
        for (var block : List.of(ModBlocks.SUPERCONDUCTING_CELL.get(), ModBlocks.HYPERION_PORT.get(),
                ModBlocks.CRYOCOOLER.get(), ModBlocks.QUENCH_VENT.get(), ModBlocks.HYPERION_CONTROLLER.get())) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            com.google.gson.JsonObject variants;
            try (var in = HyperionGameTest.class.getResourceAsStream("/assets/frozendawn/blockstates/" + id + ".json")) {
                h.assertTrue(in != null, "Blockstate file for " + id);
                variants = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
            } catch (java.io.IOException e) { throw new GameTestAssertException(e.toString()); }
            var definition = block.getStateDefinition();
            for (var key : variants.keySet()) for (var pair : key.isEmpty() ? new String[0] : key.split(",")) {
                var property = definition.getProperty(pair.split("=")[0]);
                h.assertTrue(property != null && property.getValue(pair.split("=")[1]).isPresent(), id + " variant names " + pair);
            }
            for (var state : definition.getPossibleStates())
                h.assertTrue(variants.keySet().stream().anyMatch(key -> key.isEmpty() || java.util.Arrays.stream(key.split(",")).allMatch(pair ->
                        valueName(state, definition.getProperty(pair.split("=")[0])).equals(pair.split("=")[1]))), id + " has no variant for " + state);
        }
        h.succeed();
    }

    private static <T extends Comparable<T>> String valueName(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }
    static int quenches(ServerLevel l) { return com.frozendawn.block.HyperionControllerBlockEntity.quenchCount(l); }

    /** The built controller, formed by one second. */
    static com.frozendawn.block.HyperionControllerBlockEntity controller(GameTestHelper h, HyperionScenes.Built b) {
        var c = (com.frozendawn.block.HyperionControllerBlockEntity) h.getLevel().getBlockEntity(b.controller());
        c.second(h.getLevel());
        h.assertTrue(c.shell().valid(), "Formed: " + c.shell().problem());
        return c;
    }
    static BlockPos place(GameTestHelper h, BlockPos rel, net.minecraft.world.level.block.Block block) { h.setBlock(rel, block); return h.absolutePos(rel); }
    static void link(GameTestHelper h, BlockPos a, BlockPos b) { var r = PowerNetworks.link(h.getLevel(), a, b); h.assertTrue(r == PowerLinkRules.Result.OK, "Link " + a + " to " + b + ": " + r); }
    static void account(ServerLevel l) { for (int i = 0; i < 128; i++) PowerNetworks.update(l); PowerNetworks.account(l); }
    static boolean lit(ServerLevel l, BlockPos p) { return l.getBlockState(p).getValue(com.frozendawn.block.PanelLightBlock.LIT); }

    static HyperionScenes.Built fresh(GameTestHelper h, int height) { clear(h); return HyperionScenes.silo(h, MIN, height, 1, 1); }
    static void clear(GameTestHelper h) {
        var lo = h.absolutePos(MIN.offset(-1, -1, -1));
        for (var p : BlockPos.betweenClosed(lo, lo.offset(10, 10, 10))) h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }
    /** The problem, naming exactly that position (or none). */
    private static void expect(GameTestHelper h, HyperionScenes.Built b, Problem problem, BlockPos wrong) {
        var r = expectAny(h, b, problem);
        h.assertTrue(java.util.Objects.equals(wrong, r.wrong()), problem + " names " + wrong + ", got " + r.wrong());
    }
    private static HyperionMultiblock.Result expectAny(GameTestHelper h, HyperionScenes.Built b, Problem problem) {
        var r = HyperionMultiblock.discover(h.getLevel(), b.controller());
        h.assertTrue(r.problem() == problem, "Expected " + problem + ", got " + r.problem() + " at " + r.wrong());
        return r;
    }
}
