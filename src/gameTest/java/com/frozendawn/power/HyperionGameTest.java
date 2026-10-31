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
        int[][] sizes = {{3, 3, 3, 7}, {5, 5, 5, 81}, {7, 7, 7, 275}, {3, 5, 7, 61}};
        for (var s : sizes) {
            clear(h);
            var b = HyperionScenes.build(h, MIN, s[0], s[1], s[2], 1, 1);
            var r = HyperionMultiblock.discover(h.getLevel(), b.controller());
            h.assertTrue(r.valid(), s[0] + "x" + s[1] + "x" + s[2] + " is valid, got " + r.problem() + " at " + r.wrong());
            h.assertTrue(r.cells() == s[3], "Counts " + s[3] + " cells, got " + r.cells());
            h.assertTrue(r.ports().equals(b.ports()) && r.coolers().equals(b.coolers()) && r.vents().size() == 2, "Finds its fittings");
            h.assertTrue(r.min().equals(b.min()) && r.max().equals(b.max()), "Finds its box");
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionShellNamesTheWrongCell(GameTestHelper h) {
        var l = h.getLevel();
        var b = fresh(h, 3, 3, 3);
        var face = b.min().offset(1, 1, 0); l.setBlock(face, Blocks.STONE.defaultBlockState(), 3);
        expect(h, b, Problem.FACE, face);
        b = fresh(h, 3, 3, 3);
        var interior = b.min().offset(1, 1, 1); l.setBlock(interior, Blocks.AIR.defaultBlockState(), 3);
        expect(h, b, Problem.INTERIOR, interior);
        b = fresh(h, 3, 3, 3);
        var corner = b.max(); l.setBlock(corner, ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        expect(h, b, Problem.CORNER, corner);
        b = fresh(h, 3, 3, 3);
        l.setBlock(b.vents().getFirst(), ModBlocks.HYPERION_FRAME.get().defaultBlockState(), 3);
        expect(h, b, Problem.VENTS, null);
        b = fresh(h, 3, 3, 3);
        var vent = b.vents().getFirst();
        l.setBlock(vent, l.getBlockState(vent).setValue(BlockStateProperties.FACING, HyperionScenes.outward(vent, b.min(), b.max()).getOpposite()), 3);
        expect(h, b, Problem.VENTS, null);
        b = fresh(h, 3, 3, 3);
        l.setBlock(b.coolers().getFirst(), ModBlocks.HYPERION_FRAME.get().defaultBlockState(), 3);
        expect(h, b, Problem.COOLERS, null);
        b = fresh(h, 3, 3, 3);
        var spare = HyperionScenes.edges(b.min(), b.max()).getLast();
        l.setBlock(spare, ModBlocks.HYPERION_CONTROLLER.get().defaultBlockState(), 3);
        expect(h, b, Problem.CONTROLLERS, null);
        b = fresh(h, 3, 3, 8);
        expect(h, b, Problem.TOO_LARGE, null);
        b = fresh(h, 2, 3, 3);
        expect(h, b, Problem.TOO_SMALL, null);
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "hyperion", timeoutTicks = 20)
    public static void hyperionPartsRefusePistons(GameTestHelper h) {
        for (var b : List.of(ModBlocks.HYPERION_FRAME, ModBlocks.SUPERCONDUCTING_CELL, ModBlocks.HYPERION_PORT, ModBlocks.CRYOCOOLER,
                ModBlocks.QUENCH_VENT, ModBlocks.HYPERION_CONTROLLER))
            h.assertTrue(b.get().defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK, b.getId() + " refuses pistons");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionPortsCapTheRatePerInterval(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b);
        c.setStoredForTest(10_000_000);
        h.assertTrue(c.drawEnergy(1_000_000) == 500_000 && c.drawEnergy(1) == 0, "One port draws 500 kJ per interval");
        h.assertTrue(c.availableEnergy() == 0, "Nothing more is available this interval");
        h.runAfterDelay(20, () -> {
            h.assertTrue(c.drawEnergy(1_000_000) == 500_000, "The next interval draws again");
            c.setStoredForTest(HyperionPhysics.capacity(7) - 100);
            h.assertTrue(c.chargeEnergy(1_000_000) == 100 && c.storedEnergy() == HyperionPhysics.capacity(7), "Charging stops at capacity");
            clear(h); var two = HyperionScenes.build(h, MIN, 3, 3, 3, 2, 1); var d = controller(h, two);
            h.assertTrue(d.chargeEnergy(3_000_000) == 1_000_000 && d.chargeEnergy(1) == 0, "Two ports charge 1 MJ per interval");
            d.setStoredForTest(5_000_000);
            h.assertTrue(d.drawEnergy(3_000_000) == 1_000_000, "Two ports draw 1 MJ per interval");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionPowersALampAndMergesNetworks(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.build(h, MIN, 3, 3, 3, 2, 1); var c = controller(h, b);
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
        var l = h.getLevel(); clear(h); var b = HyperionScenes.build(h, MIN, 7, 7, 7, 1, 1); var c = controller(h, b);
        c.setStoredForTest(5_000_000_000L);
        var saved = c.saveWithFullMetadata(l.registryAccess());
        var restored = new com.frozendawn.block.HyperionControllerBlockEntity(b.controller(), c.getBlockState()); restored.loadWithComponents(saved, l.registryAccess());
        h.assertTrue(restored.stored() == 5_000_000_000L, "Past 2.1 GJ survives a save: " + restored.stored());
        var monitor = place(h, new BlockPos(1, 2, 1), ModBlocks.POWER_MONITOR.get());
        link(h, b.ports().getFirst(), monitor); account(l);
        var m = PowerNetworks.monitorReading(l, monitor);
        h.assertTrue(m.storedJoules() == 5_000_000_000L && m.capacityJoules() == 5_500_000_000L, "Monitor reads " + m.storedJoules() + " / " + m.capacityJoules());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionInvalidShellStopsService(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b);
        h.assertTrue(PowerNetworks.store(l, b.ports().getFirst()).isPresent(), "A formed port reaches its store");
        l.setBlock(b.min().offset(1, 1, 0), Blocks.AIR.defaultBlockState(), 3); c.second(l);
        h.assertTrue(c.availableEnergy() == 0 && !c.acceptsCharge(), "A broken shell neither gives nor takes");
        h.assertTrue(PowerNetworks.store(l, b.ports().getFirst()).isEmpty(), "Its port reaches nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionLoadFollowAsksForPortHeadroom(GameTestHelper h) {
        var l = h.getLevel();
        var empty = controller(h, HyperionScenes.build(h, MIN, 3, 3, 3, 1, 1));
        var fullBox = HyperionScenes.build(h, new BlockPos(7, 2, 11), 3, 3, 3, 2, 1); var full = controller(h, fullBox);
        var relay = place(h, new BlockPos(1, 2, 1), ModBlocks.POWER_RELAY.get());
        var bankPos = place(h, new BlockPos(1, 2, 8), ModBlocks.BATTERY_BANK.get());
        var bank = (com.frozendawn.block.PowerStorageBlockEntity) l.getBlockEntity(bankPos); bank.setItem(0, com.frozendawn.item.PowerCellItem.charged(0));
        full.setStoredForTest(HyperionPhysics.capacity(7));
        link(h, relay, empty.shell().ports().getFirst()); link(h, relay, fullBox.ports().getFirst()); link(h, relay, bankPos);
        account(l);
        long target = PowerNetworks.loadFollowTargetWatts(l, relay, 0);
        h.assertTrue(target == 5_000 + 500_000, "Asks for the empty Hyperion's one port plus the bank margin, got " + target);
        empty.setStoredForTest(HyperionPhysics.capacity(7)); for (int i = 0; i < 4; i++) bank.setItem(i, com.frozendawn.item.PowerCellItem.charged(500_000));
        h.runAfterDelay(20, () -> {
            account(l);
            long after = PowerNetworks.loadFollowTargetWatts(l, relay, 0);
            h.assertTrue(after == 0, "Everything full asks for nothing, got " + after);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionHeatQuenchFollowsTheLadder(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var c = controller(h, HyperionScenes.build(h, MIN, 5, 5, 5, 1, 1));
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(20.0); int before = quenches(l);
        for (int i = 0; i < 299; i++) c.second(l);
        h.assertTrue(c.stored() > 0 && c.readout().stage() == HyperionPhysics.Stage.FINAL, "Still holding in the final seconds: " + c.readout().stage());
        c.second(l);
        h.assertTrue(c.stored() == 0 && quenches(l) == before + 1, "Quenches at five minutes in a warm room");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionEnoughCoolingHoldsAndDrainsSlowly(GameTestHelper h) {
        var l = h.getLevel(); var c = controller(h, fresh(h, 3, 3, 3)); long full = HyperionPhysics.capacity(7);
        c.setStoredForTest(full); c.setSurroundingsForTest(20.0); int before = quenches(l);
        for (int i = 0; i < 3600; i++) c.second(l);
        long lost = full - c.stored();
        h.assertTrue(Math.abs(lost - 2_800_000) <= 28_000 && quenches(l) == before, "Loses 2% an hour in a warm room: " + lost);
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionOutageNeverQuenches(GameTestHelper h) {
        var l = h.getLevel(); var c = controller(h, fresh(h, 3, 3, 3));
        c.setStoredForTest(1_000_000); c.setSurroundingsForTest(20.0); int before = quenches(l);
        int guard = 0; while (c.stored() > 0 && guard++ < 5000) c.second(l);
        for (int i = 0; i < 400; i++) c.second(l);
        h.assertTrue(c.stored() == 0 && quenches(l) == before && c.readout().stage() == HyperionPhysics.Stage.WARM, "Drains, warms, never vents");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionWarmBuildCoolsOnGridBeforeCharging(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(b.controller());
        c.setSurroundingsForTest(20.0); c.second(l);
        h.assertTrue(c.shell().valid() && c.physics().stage() == HyperionPhysics.Stage.WARM, "A warm build forms warm");
        long grant = c.chargeEnergy(1_000_000);
        h.assertTrue(grant <= HyperionPhysics.cooldownWatts(7) && c.stored() == 0, "Warm cells only take cool-down power: " + grant);
        h.assertTrue(c.chargeHeadroomWatts() == HyperionPhysics.cooldownWatts(7), "Load-follow asks for the cool-down");
        c.second(l);
        for (int i = 1; i < 55; i++) { c.chargeEnergy(1_000_000); c.second(l); }
        h.assertTrue(c.physics().stage() == HyperionPhysics.Stage.COLD, "Cold within a minute of full grants: " + c.physics().cellC());
        h.assertTrue(c.chargeEnergy(1_000_000) > 0 && c.stored() > 0, "Then it stores");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionBreakingAChargedPartQuenches(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.min().offset(1, 1, 0), true);
        h.assertTrue(c.stored() == 0 && quenches(l) == before + 1, "A charged battery vents the moment a part breaks");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionEmptyBatteryComesApartQuietly(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); controller(h, b); int before = quenches(l);
        var cell = b.min().offset(1, 1, 0); l.destroyBlock(cell, true);
        var drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cell).inflate(2));
        h.assertTrue(quenches(l) == before && drops.stream().anyMatch(e -> e.getItem().is(com.frozendawn.init.ModItems.SUPERCONDUCTING_CELL)), "An empty battery drops its cell quietly");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionControllerRemovalQuenchesOnlyWhenCharged(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.controller(), true);
        h.assertTrue(quenches(l) == before + 1, "Breaking a charged Controller vents");
        h.assertTrue(com.frozendawn.block.HyperionVentJets.venting(l, b.vents().get(0)), "The vents jet even with the Controller gone");
        var scar = b.min().offset(1, 1, 0);
        h.assertTrue(l.getBlockState(scar).getValue(SuperconductingCellBlock.LOOK) == Look.SCARRED, "The cells it leaves behind are scarred");
        b = fresh(h, 3, 3, 3); controller(h, b); before = quenches(l);
        l.destroyBlock(b.controller(), true);
        var drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(b.controller()).inflate(2));
        h.assertTrue(quenches(l) == before && drops.stream().anyMatch(e -> e.getItem().is(com.frozendawn.init.ModItems.HYPERION_CONTROLLER)), "An empty Controller drops quietly");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionMultiBreakQuenchesOnce(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        l.destroyBlock(b.min().offset(1, 1, 0), false); l.destroyBlock(b.min().offset(0, 1, 1), false); l.destroyBlock(b.max().offset(-1, -1, 0), false);
        h.assertTrue(quenches(l) == before + 1, "Three breaks in one tick vent once: " + (quenches(l) - before));
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionNeighborBuildDoesNotQuench(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b); c.setStoredForTest(10_000_000); int before = quenches(l);
        HyperionScenes.build(h, MIN.offset(3, 0, 0), 3, 3, 3, 1, 1);
        l.setBlock(b.min().offset(1, 1, -1), ModBlocks.HYPERION_FRAME.get().defaultBlockState(), 3);
        c.second(l);
        h.assertTrue(c.shell().valid() && c.stored() == 10_000_000 && quenches(l) == before, "Building beside a charged battery leaves it alone: " + c.shell().problem());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionHotNeighborCounts(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3); var c = controller(h, b);
        var spot = b.min().offset(1, 1, -1);
        l.setBlock(spot, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState().setValue(com.frozendawn.block.ReactorRadiatorBlock.HOT, true), 3);
        h.assertTrue(c.hotNeighbors(l) == 1, "A hot radiator against a face counts");
        l.setBlock(spot, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState(), 3);
        h.assertTrue(c.hotNeighbors(l) == 0, "A cold one does not");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionSaveReloadMidWarningResumes(GameTestHelper h) {
        var l = h.getLevel(); clear(h); var b = HyperionScenes.build(h, MIN, 5, 5, 5, 1, 1); var c = controller(h, b);
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(20.0); int before = quenches(l);
        int guard = 0; do c.second(l); while (c.readout().stage() != HyperionPhysics.Stage.WARNING && guard++ < 400);
        var saved = c.saveWithFullMetadata(l.registryAccess());
        var copy = new com.frozendawn.block.HyperionControllerBlockEntity(b.controller(), c.getBlockState()); copy.setLevel(l); copy.loadWithComponents(saved, l.registryAccess());
        h.assertTrue(copy.physics().stage() == HyperionPhysics.Stage.WARNING && copy.physics().cellC() == c.physics().cellC(), "Stage and cell temperature survive");
        copy.setSurroundingsForTest(20.0); copy.second(l);
        h.assertTrue(copy.stored() > 0 && quenches(l) == before, "Reloading never quenches by itself");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionControllerExplainsAnIncompleteShell(GameTestHelper h) {
        var l = h.getLevel(); var b = fresh(h, 3, 3, 3);
        l.setBlock(b.vents().getFirst(), ModBlocks.HYPERION_FRAME.get().defaultBlockState(), 3);
        var c = (com.frozendawn.block.HyperionControllerBlockEntity) l.getBlockEntity(b.controller()); c.second(l);
        h.assertTrue("hyperion.frozendawn.problem.vents".equals(c.problemKey()), "Explains the missing vent: " + c.problemKey());
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "hyperion", timeoutTicks = 100)
    public static void hyperionCellsShowTheStage(GameTestHelper h) {
        // One cooler just holds 81 charged cells at 5C and loses at 20C; an empty battery cannot pay its cooler at all.
        var l = h.getLevel(); clear(h); var b = HyperionScenes.build(h, MIN, 5, 5, 5, 1, 1); var c = controller(h, b);
        c.second(l);
        expectLook(h, b, Look.FROSTED, false, "Deep cold frosts every cell");
        c.setStoredForTest(1_000_000_000L); c.setSurroundingsForTest(5.0); c.second(l);
        expectLook(h, b, Look.PLAIN, false, "Cold cells in a room above 0C lose the frost");
        c.setSurroundingsForTest(20.0); c.second(l);
        h.assertTrue(c.readout().stage() == HyperionPhysics.Stage.CHIRP, "Chirps first: " + c.readout().stage());
        expectLook(h, b, Look.STRESSED, true, "Heat stress cracks the cells and lights the alert");
        int guard = 0; do c.second(l); while (c.readout().stage() != HyperionPhysics.Stage.FINAL && guard++ < 400);
        expectLook(h, b, Look.CRITICAL, true, "The final seconds glow white-hot");
        guard = 0; do c.second(l); while (c.stored() > 0 && guard++ < 100);
        expectLook(h, b, Look.SCARRED, false, "A quench leaves scars while the cells are warm");
        c.setSurroundingsForTest(-250.0);
        guard = 0; do { c.chargeEnergy(1_000_000_000L); c.second(l); } while (c.readout().stage() != HyperionPhysics.Stage.COLD && guard++ < 400);
        expectLook(h, b, Look.FROSTED, false, "Cold again, the scars frost over");

        var small = fresh(h, 3, 3, 3); var d = controller(h, small);
        d.setStoredForTest(1_000_000); d.setSurroundingsForTest(20.0);
        guard = 0; do d.second(l); while (d.readout().stage() != HyperionPhysics.Stage.WARM && guard++ < 5000);
        expectLook(h, small, Look.PLAIN, false, "A battery that drained and warmed without venting stays plain");
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

    static HyperionScenes.Built fresh(GameTestHelper h, int sx, int sy, int sz) { clear(h); return HyperionScenes.build(h, MIN, sx, sy, sz, 1, 1); }
    static void clear(GameTestHelper h) {
        var lo = h.absolutePos(MIN.offset(-1, -1, -1));
        for (var p : BlockPos.betweenClosed(lo, lo.offset(10, 10, 10))) h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }
    private static void expect(GameTestHelper h, HyperionScenes.Built b, Problem problem, BlockPos wrong) {
        var r = HyperionMultiblock.discover(h.getLevel(), b.controller());
        h.assertTrue(r.problem() == problem, "Expected " + problem + ", got " + r.problem() + " at " + r.wrong());
        if (wrong != null) h.assertTrue(wrong.equals(r.wrong()), "Names " + wrong + ", got " + r.wrong());
    }
}
