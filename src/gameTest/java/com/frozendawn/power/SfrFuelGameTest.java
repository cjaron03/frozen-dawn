package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.IsotopeColumnBlockEntity;
import com.frozendawn.block.IsotopeColumnMenu;
import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.block.ReactorControllerMenu;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.frozendawn.gametest.GameTestPacing;
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
import net.minecraft.world.item.Items;
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

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrTargetRodCostsOneRodOfHeat(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = ReactorGameTest.build(h); var be = ReactorGameTest.controller(h, controller);
            for (int i = 0; i < 3; i++) be.items().set(i, new ItemStack(ModItems.FUEL_ROD.get()));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "breeder")); player.setPos(controller.north(2).getCenter());
            var menu = new ReactorControllerMenu(1, player.getInventory(), be);
            h.assertTrue(menu.slots.get(3).mayPlace(new ItemStack(ModItems.LITHIUM_TARGET_ROD.get())), "A rod slot takes a target rod");
            be.items().set(3, new ItemStack(ModItems.LITHIUM_TARGET_ROD.get()));
            be.second(level);
            h.assertTrue(be.loadedRods() == 3 && be.data().get(ReactorControllerBlockEntity.RATED) == (int) (3 * ReactorPhysics.ROD_KW * 10),
                    "The target rod makes no heat: rated is 3 rods, " + be.data().get(ReactorControllerBlockEntity.RATED) / 10.0 + " kW");
            be.physics().setCoreC(460);
            h.assertTrue(!menu.slots.get(3).mayPickup(player) && !menu.slots.get(0).mayPlace(new ItemStack(ModItems.LITHIUM_TARGET_ROD.get())),
                    "Target rods lock with the fuel rods above 350C");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrTargetRodBreedsFourTritium(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var be = ReactorGameTest.controller(h, ReactorGameTest.build(h));
            var target = new ItemStack(ModItems.LITHIUM_TARGET_ROD.get()); target.setDamageValue(target.getMaxDamage() - 1);
            be.items().set(0, target); be.items().set(1, new ItemStack(ModItems.FUEL_ROD.get())); be.physics().setInsertion(1);
            be.second(level);
            var out = be.items().get(ReactorControllerBlockEntity.OUTPUT);
            h.assertTrue(be.items().get(0).isEmpty() && out.is(ModItems.TRITIUM_CANISTER.get()) && out.getCount() == 4,
                    "A bred-out target rod leaves 4 Tritium Canisters, got " + out);
            target = new ItemStack(ModItems.LITHIUM_TARGET_ROD.get()); target.setDamageValue(target.getMaxDamage() - 1);
            be.items().set(0, target); be.second(level);
            h.assertTrue(be.items().get(0).isEmpty() && be.items().get(ReactorControllerBlockEntity.OUTPUT).getCount() == 8, "Canisters stack in the output");
            var idle = new ItemStack(ModItems.LITHIUM_TARGET_ROD.get());
            be.items().set(0, idle); be.items().set(1, ItemStack.EMPTY); be.second(level);
            h.assertTrue(be.items().get(0).getDamageValue() == 0, "With no fuel the core makes no heat and the target rod does not cook");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrTargetRodWaitsForOutput(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var be = ReactorGameTest.controller(h, ReactorGameTest.build(h));
            var target = new ItemStack(ModItems.LITHIUM_TARGET_ROD.get()); target.setDamageValue(target.getMaxDamage() - 1);
            be.items().set(0, target); be.items().set(1, new ItemStack(ModItems.FUEL_ROD.get())); be.physics().setInsertion(1);
            be.items().set(ReactorControllerBlockEntity.OUTPUT, new ItemStack(ModItems.SPENT_FUEL_ROD.get()));
            be.second(level);
            h.assertTrue(be.items().get(0).is(ModItems.LITHIUM_TARGET_ROD.get()) && be.items().get(ReactorControllerBlockEntity.OUTPUT).is(ModItems.SPENT_FUEL_ROD.get()),
                    "A finished target rod waits while the output holds spent fuel");
            be.items().set(ReactorControllerBlockEntity.OUTPUT, new ItemStack(ModItems.TRITIUM_CANISTER.get(), 61));
            be.second(level);
            h.assertTrue(be.items().get(0).is(ModItems.LITHIUM_TARGET_ROD.get()), "It waits while the canister stack lacks room for 4");
            be.items().set(ReactorControllerBlockEntity.OUTPUT, ItemStack.EMPTY);
            be.second(level);
            h.assertTrue(be.items().get(0).isEmpty() && be.items().get(ReactorControllerBlockEntity.OUTPUT).getCount() == 4, "Emptying the output lets it finish");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrTargetRodSurvivesReload(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = ReactorGameTest.build(h); var be = ReactorGameTest.controller(h, controller);
            be.items().set(0, new ItemStack(ModItems.LITHIUM_TARGET_ROD.get())); be.items().set(1, new ItemStack(ModItems.FUEL_ROD.get()));
            be.physics().setInsertion(1);
            for (int i = 0; i < 3; i++) be.second(level);
            h.assertTrue(be.items().get(0).getDamageValue() == 3, "A target rod ages with the fuel rods: " + be.items().get(0).getDamageValue());
            var copy = new ReactorControllerBlockEntity(controller, be.getBlockState()); copy.loadWithComponents(be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            h.assertTrue(copy.items().get(0).is(ModItems.LITHIUM_TARGET_ROD.get()) && copy.items().get(0).getDamageValue() == 3, "A part-cooked target rod survives a reload");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 120)
    public static void sfrColumnMakesDeuteriumFromIce(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var level = h.getLevel(); var pos = poweredColumn(h); var be = column(level, pos);
            // Prime a job twenty ticks from done; the full 120 s run is the constant, not the test.
            prime(be, level, "DEUTERIUM", IsotopeColumnLogic.DEUTERIUM_TICKS - 20, 0);
            be.setItem(0, new ItemStack(Items.ICE, 8));
            h.runAfterDelay(60, () -> {
                h.assertTrue(be.getItem(1).is(ModItems.DEUTERIUM_CANISTER.get()) && be.getItem(1).getCount() == 1, "8 Ice make one Deuterium Canister: " + be.getItem(1));
                h.assertTrue(be.getItem(0).isEmpty() && be.buffer() == 0, "All 8 ice units went into the canister, buffer " + be.buffer());
                h.assertTrue(!be.wantsPower(), "With no ice left the Column stops drawing");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrColumnPausesWithoutPower(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(6, 2, 4)); h.setBlock(new BlockPos(6, 2, 4), ModBlocks.ISOTOPE_COLUMN.get());
            var be = column(level, pos); prime(be, level, "DEUTERIUM", 100, 8); be.setItem(0, new ItemStack(Items.ICE, 8));
            h.runAfterDelay(40, () -> {
                h.assertTrue(be.progress() == 100 && be.buffer() == 8 && be.getItem(0).getCount() == 8 && be.getItem(1).isEmpty(),
                        "Without power the job pauses: progress " + be.progress() + ", buffer " + be.buffer());
                h.assertTrue(be.wantsPower() && be.status() == IsotopeColumnBlockEntity.NO_POWER, "It still asks for power");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 120)
    public static void sfrColumnRefinesLithium(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var level = h.getLevel(); var pos = poweredColumn(h); var be = column(level, pos);
            prime(be, level, "LITHIUM", IsotopeColumnLogic.LITHIUM_TICKS - 20, 0);
            be.setItem(0, new ItemStack(ModItems.LITHIUM_CARBONATE.get(), 2));
            h.runAfterDelay(60, () -> {
                h.assertTrue(be.getItem(1).is(ModItems.LITHIUM.get()) && be.getItem(1).getCount() == 1, "1 Lithium Carbonate refines to 1 Lithium: " + be.getItem(1));
                h.assertTrue(be.getItem(0).getCount() == 1 && be.progress() > 0 && be.status() == IsotopeColumnBlockEntity.WORKING, "The second carbonate is under way");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrColumnRefusesJunk(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var level = h.getLevel();
            h.setBlock(new BlockPos(4, 2, 4), ModBlocks.ISOTOPE_COLUMN.get()); h.setBlock(new BlockPos(4, 3, 4), Blocks.HOPPER);
            h.setBlock(new BlockPos(8, 2, 4), ModBlocks.ISOTOPE_COLUMN.get()); h.setBlock(new BlockPos(8, 3, 4), Blocks.HOPPER);
            var junk = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(4, 3, 4)));
            var ice = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(8, 3, 4)));
            junk.setItem(0, new ItemStack(Blocks.COBBLESTONE, 4)); ice.setItem(0, new ItemStack(Items.ICE, 4));
            var be = column(level, h.absolutePos(new BlockPos(4, 2, 4)));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "column")); player.setPos(be.getBlockPos().north(2).getCenter());
            var menu = new IsotopeColumnMenu(1, player.getInventory(), be);
            h.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(Blocks.COBBLESTONE)) && menu.slots.get(0).mayPlace(new ItemStack(Items.PACKED_ICE))
                    && menu.slots.get(0).mayPlace(new ItemStack(ModItems.LITHIUM_CARBONATE.get())) && !menu.slots.get(1).mayPlace(new ItemStack(Items.ICE)),
                    "The menu input takes only ice, snow or Lithium Carbonate, and the output takes nothing");
            h.runAfterDelay(40, () -> {
                h.assertTrue(junk.getItem(0).getCount() == 4 && be.getItem(0).isEmpty(), "Cobblestone stays in the hopper");
                var fed = column(level, h.absolutePos(new BlockPos(8, 2, 4)));
                h.assertTrue(ice.isEmpty() && fed.buffer() == 4, "The same hopper feeds ice into the buffer: " + fed.buffer());
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "sfr_fuel", timeoutTicks = 100)
    public static void sfrColumnSurvivesReload(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var level = h.getLevel(); var pos = poweredColumn(h); var be = column(level, pos);
            be.setItem(0, new ItemStack(Items.ICE, 10));
            h.runAfterDelay(30, () -> {
                h.assertTrue(be.progress() > 0 && be.buffer() == 8 && be.getItem(0).getCount() == 2, "A job is under way: progress " + be.progress() + ", buffer " + be.buffer() + ", input " + be.getItem(0));
                var copy = new IsotopeColumnBlockEntity(pos, be.getBlockState()); copy.loadWithComponents(be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                h.assertTrue(copy.progress() == be.progress() && copy.buffer() == 8 && copy.job() == IsotopeColumnLogic.Job.DEUTERIUM && copy.getItem(0).getCount() == 2,
                        "Reload keeps the job, its progress and the ice buffer");
                h.succeed();
            });
        });
    }

    private static BlockPos poweredColumn(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 2, 4), ModBlocks.GEOTHERMAL_CORE.get()); h.setBlock(new BlockPos(6, 2, 4), ModBlocks.ISOTOPE_COLUMN.get());
        var core = h.absolutePos(new BlockPos(2, 2, 4)); var pos = h.absolutePos(new BlockPos(6, 2, 4));
        ((GeothermalCoreBlockEntity) h.getLevel().getBlockEntity(core)).setPowerShare(100);
        var result = PowerNetworks.link(h.getLevel(), core, pos); h.assertTrue(result == PowerLinkRules.Result.OK, "Column links to the core: " + result);
        return pos;
    }
    private static IsotopeColumnBlockEntity column(ServerLevel level, BlockPos pos) { return (IsotopeColumnBlockEntity) level.getBlockEntity(pos); }
    private static void prime(IsotopeColumnBlockEntity be, ServerLevel level, String job, int progress, int buffer) {
        var tag = be.saveWithFullMetadata(level.registryAccess()); tag.putString("Job", job); tag.putInt("Progress", progress); tag.putInt("Buffer", buffer);
        be.loadWithComponents(tag, level.registryAccess());
    }
    private static void scene(GameTestHelper h, Runnable body) { h.runAfterDelay(1, () -> { body.run(); h.succeed(); }); }
}
