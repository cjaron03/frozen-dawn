package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModItems;
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

/** Every SFR-1 block is craftable in survival except the Confinement Core, which only Helion sites hold. Magnet Coils need
 * Superconducting Coil Scrap, so each reactor's coils come from a site; First Wall replacements never do. */
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
                craft(ModItems.MAGNET_COIL.get(), 8, Map.of('I', i, 'C', Items.COPPER_INGOT, 'S', ModItems.SUPERCONDUCTING_COIL_SCRAP.get()), "ICI", "CSC", "ICI"),
                craft(ModItems.FIRST_WALL.get(), 8, Map.of('O', Items.OBSIDIAN, 'I', i, 'A', ModItems.REFINED_ACHERONITE.get()), "OIO", "IAI", "OIO"),
                craft(ModItems.FIRST_WALL.get(), 1, Map.of('T', ModItems.FIRST_WALL_TILE.get()), "TT", "TT"),
                craft(ModItems.LITHIUM_BLANKET.get(), 8, Map.of('L', ModItems.LEAD_INGOT.get(), 'P', ModItems.LITHIUM.get(), 'B', Items.IRON_BLOCK), "LPL", "PBP", "LPL"),
                craft(ModItems.HIGH_TEMPERATURE_RADIATOR.get(), 1, Map.of('O', Items.OBSIDIAN, 'I', i, 'R', ModItems.REACTOR_RADIATOR.get()), "OIO", "IRI", "OIO"),
                craft(ModItems.SFR_CONTROLLER.get(), 1, Map.of('G', Items.GOLD_INGOT, 'D', Items.DIAMOND, 'S', ModItems.SIGNAL_PROCESSOR.get(),
                        'C', ModItems.REACTOR_CONTROLLER.get(), 'B', Items.REDSTONE_BLOCK), "GDG", "SCB", "GDG"),
                craft(ModItems.SFR_POWER_PORT.get(), 1, Map.of('I', i, 'C', Items.COPPER_INGOT, 'L', ModItems.LARGE_POWER_RELAY.get()), "ICI", "CLC", "ICI"),
                craft(ModItems.SFR_FUEL_PORT.get(), 1, Map.of('I', i, 'H', Items.HOPPER, 'P', ModItems.COOLANT_PORT.get()), "IHI", "IPI", "I I"),
                craft(ModItems.SFR_VACUUM_PORT.get(), 1, Map.of('I', i, 'V', ModItems.MANUAL_VENT_VALVE.get(), 'F', ModItems.BULKHEAD_FEEDTHROUGH.get()), "IVI", "IFI"),
                craft(ModItems.SFR_MAINTENANCE_HATCH.get(), 1, Map.of('I', i, 'A', ModItems.AIRLOCK_DOOR.get(), 'W', ModItems.FIRST_WALL.get()), "IAI", "W W"),
                craft(ModItems.ISOTOPE_COLUMN.get(), 1, Map.of('G', ModItems.INSULATED_GLASS.get(), 'I', i, 'P', ModItems.COOLANT_PORT.get(),
                        'R', Items.REDSTONE_BLOCK), "GIG", "IPI", "IRI"));
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

    @GameTest(template = GameTestTemplates.EMPTY, batch = "sfr_recipes", timeoutTicks = 100)
    public static void magnetCoilsNeedHelionScrap(GameTestHelper h) {
        var coil = crafts().get(0);
        var key = new java.util.HashMap<>(coil.key()); key.put('S', Items.COPPER_INGOT);
        h.assertTrue(assemble(h.getLevel(), new Craft(coil.result(), coil.count(), coil.rows(), key)).isEmpty(), "No coil without Superconducting Coil Scrap");
        for (var recipe : h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            Item result = recipe.value().getResultItem(h.getLevel().registryAccess()).getItem();
            h.assertTrue(result != ModItems.CONFINEMENT_CORE.get(), "The Confinement Core is never craftable: " + recipe.id());
        }
        h.succeed();
    }
}
