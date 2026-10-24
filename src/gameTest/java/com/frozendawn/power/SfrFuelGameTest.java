package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.PegmatitePodFeature;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** The SFR-1 fuel chain: lithium from pegmatite and salvage, tritium bred in a CFU-7, deuterium from the Isotope Column. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SfrFuelGameTest {
    @BeforeBatch(batch = "sfr_fuel")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 200)
    public static void sfrPegmatitePodHostsSpodumeneOnlyInPegmatite(GameTestHelper h) {
        var level = h.getLevel(); var lo = h.absolutePos(new BlockPos(4, 1, 4)); var hi = lo.offset(12, 12, 12);
        var center = lo.offset(6, 6, 6); var pegmatite = ModBlocks.PEGMATITE.get(); var ore = ModBlocks.DEEPSLATE_SPODUMENE_ORE.get();
        for (long seed = 1; seed <= 4; seed++) {
            for (var p : BlockPos.betweenClosed(lo, hi)) level.setBlock(p, Blocks.DEEPSLATE.defaultBlockState(), 2);
            // A stone seam through the middle: pods replace deepslate only.
            for (int x = 0; x <= 12; x++) level.setBlock(lo.offset(x, 6, 6), Blocks.STONE.defaultBlockState(), 2);
            int placed = PegmatitePodFeature.place(level, center, RandomSource.create(seed));
            var pod = new HashSet<BlockPos>(); int ores = 0;
            for (var p : BlockPos.betweenClosed(lo.offset(-4, -1, -4), hi.offset(4, 3, 4))) {
                var s = level.getBlockState(p); boolean inside = p.getX() >= lo.getX() && p.getX() <= hi.getX()
                        && p.getY() >= lo.getY() && p.getY() <= hi.getY() && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ();
                if (s.is(pegmatite) || s.is(ore)) {
                    h.assertTrue(inside, "A pod never grows outside deepslate: " + p);
                    pod.add(p.immutable()); if (s.is(ore)) ores++;
                }
            }
            for (int x = 0; x <= 12; x++) h.assertTrue(level.getBlockState(lo.offset(x, 6, 6)).is(Blocks.STONE), "Stone is left alone");
            h.assertTrue(placed == pod.size() && placed >= 60, "Seed " + seed + " placed a pod of " + placed + ", found " + pod.size());
            h.assertTrue(ores >= placed / 10 && ores <= placed * 3 / 10, "About a fifth of the pod is spodumene: " + ores + "/" + placed);
            // One pocket: every spodumene block is part of a single connected pegmatite body.
            var seen = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>(); var first = pod.iterator().next();
            queue.add(first); seen.add(first);
            while (!queue.isEmpty()) {
                var p = queue.poll();
                for (var d : Direction.values()) { var n = p.relative(d); if (pod.contains(n) && seen.add(n)) queue.add(n); }
            }
            h.assertTrue(seen.size() == pod.size(), "The pod is one connected body, so no spodumene sits loose in deepslate");
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrBatterySalvageGivesLithium(GameTestHelper h) {
        var level = h.getLevel();
        for (var c : List.of(new Object[] {ModItems.POWER_CELL.get(), 1}, new Object[] {ModItems.BATTERY_BANK.get(), 4})) {
            var input = CraftingInput.of(1, 1, List.of(new ItemStack((net.minecraft.world.level.ItemLike) c[0])));
            var output = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .orElseThrow(() -> new GameTestAssertException("No salvage recipe for " + c[0])).value().assemble(input, level.registryAccess());
            h.assertTrue(output.is(ModItems.LITHIUM.get()) && output.getCount() == (int) c[1], "Salvage gives " + c[1] + " Lithium, got " + output);
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrSpodumeneSmeltsToLithiumCarbonate(GameTestHelper h) {
        var level = h.getLevel(); var input = new SingleRecipeInput(new ItemStack(ModItems.RAW_SPODUMENE.get()));
        for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
            var recipe = level.getRecipeManager().getRecipeFor(type, input, level)
                    .orElseThrow(() -> new GameTestAssertException("No " + type + " recipe for Raw Spodumene")).value();
            h.assertTrue(recipe.assemble(input, level.registryAccess()).is(ModItems.LITHIUM_CARBONATE.get()), "Raw Spodumene gives Lithium Carbonate");
            h.assertTrue(Math.abs(recipe.getExperience() - 0.7f) < 1e-6, "0.7 experience");
        }
        h.succeed();
    }
}
