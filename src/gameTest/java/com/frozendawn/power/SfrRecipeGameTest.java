package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeTypes;
import com.frozendawn.recipe.FabricatorRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.gametest.*;

/** Every SFR-1 block is obtainable in survival except the Confinement Core, which only Helion sites hold. The controller and
 * ports stay on the crafting table; precision parts and the rocket's engine and hull are printed on the ORSA Fabricator.
 * Magnet Coils need Superconducting Coil Scrap, so each reactor's coils come from a site; First Wall replacements never do. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SfrRecipeGameTest {
    @BeforeBatch(batch = "sfr_recipes")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    private record Craft(ItemLike result, int count, String[] rows, Map<Character, ItemLike> key) {}
    private static Craft craft(ItemLike result, int count, Map<Character, ItemLike> key, String... rows) { return new Craft(result, count, rows, key); }

    private static List<Craft> crafts() {
        var i = Items.IRON_INGOT;
        return List.of(
                craft(ModItems.SFR_CONTROLLER.get(), 1, Map.of('G', Items.GOLD_INGOT, 'D', Items.DIAMOND, 'S', ModItems.SIGNAL_PROCESSOR.get(),
                        'C', ModItems.REACTOR_CONTROLLER.get(), 'B', Items.REDSTONE_BLOCK), "GDG", "SCB", "GDG"),
                craft(ModItems.SFR_POWER_PORT.get(), 1, Map.of('I', i, 'C', Items.COPPER_INGOT, 'L', ModItems.LARGE_POWER_RELAY.get()), "ICI", "CLC", "ICI"),
                craft(ModItems.SFR_FUEL_PORT.get(), 1, Map.of('I', i, 'H', Items.HOPPER, 'P', ModItems.COOLANT_PORT.get()), "IHI", "IPI", "I I"),
                craft(ModItems.SFR_VACUUM_PORT.get(), 1, Map.of('I', i, 'V', ModItems.MANUAL_VENT_VALVE.get(), 'F', ModItems.BULKHEAD_FEEDTHROUGH.get()), "IVI", "IFI"),
                craft(ModItems.SFR_MAINTENANCE_HATCH.get(), 1, Map.of('I', i, 'A', ModItems.AIRLOCK_DOOR.get(), 'W', ModItems.FIRST_WALL.get()), "IAI", "W W"),
                craft(ModItems.FABRICATOR.get(), 1, Map.of('I', i, 'C', Items.COPPER_INGOT, 'T', Items.CRAFTING_TABLE, 'F', ModItems.INDUCTION_FURNACE.get(),
                        'R', Items.REDSTONE_BLOCK), "ICI", "TFR", "ICI"));
    }

    private static ItemStack assemble(ServerLevel level, Craft c) {
        int width = c.rows()[0].length();
        var items = new ArrayList<ItemStack>();
        for (var row : c.rows()) for (char ch : row.toCharArray()) items.add(ch == ' ' ? ItemStack.EMPTY : new ItemStack(c.key().get(ch)));
        var input = CraftingInput.of(width, c.rows().length, items);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_recipes", timeoutTicks = 100)
    public static void sfrPartsAreCraftableInSurvival(GameTestHelper h) {
        for (var c : crafts()) {
            var out = assemble(h.getLevel(), c);
            h.assertTrue(out.is(c.result().asItem()) && out.getCount() == c.count(),
                    "Recipe " + String.join("/", c.rows()) + " gives " + c.count() + " " + c.result().asItem() + ", got " + out);
        }
        h.succeed();
    }

    /** Printed part, blueprint, count per job. */
    private static final List<Object[]> PRINTED = List.of(
            new Object[]{ModItems.MAGNET_COIL, "magnet_coil", 8}, new Object[]{ModItems.FIRST_WALL, "first_wall", 8},
            new Object[]{ModItems.LITHIUM_BLANKET, "lithium_blanket", 8}, new Object[]{ModItems.HIGH_TEMPERATURE_RADIATOR, "high_temperature_radiator", 1},
            new Object[]{ModItems.ISOTOPE_COLUMN, "isotope_column", 1}, new Object[]{ModItems.ROCKET_ENGINE, "rocket", 1},
            new Object[]{ModItems.ROCKET_HULL, "rocket", 1});

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_recipes", timeoutTicks = 100)
    public static void precisionPartsArePrintedNotCrafted(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager(); var registries = h.getLevel().registryAccess();
        for (var row : PRINTED) {
            Item part = ((ItemLike) ((java.util.function.Supplier<?>) row[0]).get()).asItem();
            h.assertTrue(rm.getAllRecipesFor(RecipeType.CRAFTING).stream().noneMatch(r -> r.value().getResultItem(registries).is(part)), part + " has no crafting-table recipe");
            var jobs = rm.getAllRecipesFor(ModRecipeTypes.FABRICATING.get()).stream().map(r -> r.value()).filter(r -> r.result().is(part)).toList();
            h.assertTrue(!jobs.isEmpty() && jobs.stream().allMatch(r -> r.blueprint().equals(row[1])), part + " is printed under the " + row[1] + " blueprint");
            h.assertTrue(jobs.stream().anyMatch(r -> r.result().getCount() == (int) row[2]), part + " prints " + row[2] + " per job");
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_recipes", timeoutTicks = 100)
    public static void magnetCoilsNeedHelionScrap(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        var coil = rm.getAllRecipesFor(ModRecipeTypes.FABRICATING.get()).stream().map(r -> r.value()).filter(r -> r.result().is(ModItems.MAGNET_COIL.get())).findFirst().orElseThrow();
        var without = new FabricatorRecipe.Input(List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(ModItems.REFINED_ACHERONITE.get(), 64)));
        var with = new FabricatorRecipe.Input(List.of(new ItemStack(Items.COPPER_INGOT, 4), new ItemStack(ModItems.REFINED_ACHERONITE.get()), new ItemStack(ModItems.SUPERCONDUCTING_COIL_SCRAP.get(), 2)));
        h.assertTrue(!coil.matches(without, h.getLevel()) && coil.matches(with, h.getLevel()), "No coil without Superconducting Coil Scrap");
        for (var recipe : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            Item result = recipe.value().getResultItem(h.getLevel().registryAccess()).getItem();
            h.assertTrue(result != ModItems.CONFINEMENT_CORE.get(), "The Confinement Core is never craftable: " + recipe.id());
        }
        h.assertTrue(rm.getAllRecipesFor(ModRecipeTypes.FABRICATING.get()).stream().noneMatch(r -> r.value().result().is(ModItems.CONFINEMENT_CORE.get())), "The Confinement Core is never printed");
        h.succeed();
    }
}
