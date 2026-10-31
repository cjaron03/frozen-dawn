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
