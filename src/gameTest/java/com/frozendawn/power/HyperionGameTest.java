package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.HyperionMultiblock;
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
