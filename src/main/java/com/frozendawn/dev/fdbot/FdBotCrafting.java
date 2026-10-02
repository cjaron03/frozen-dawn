package com.frozendawn.dev.fdbot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Crafts with the player's inventory and the server recipe manager.
 *
 * <p>2x2 recipes use the inventory grid. 3x3 recipes require a placed crafting table within
 * {@link FdBotActions#CRAFTING_TABLE_RADIUS} blocks (Euclidean, loaded chunks only). A crafting
 * table item in the inventory does not count; place it first. Shaped and shapeless recipes are
 * assembled, including Frozen Dawn recipes that extend {@link ShapedRecipe}. Other custom grids,
 * such as the caloric-lined EVA upgrade, are refused by name. {@code count} is result items;
 * a recipe that yields four stops on the craft that meets or passes the request.
 */
final class FdBotCrafting {
    private FdBotCrafting() {
    }

    static FdBotActions.Outcome craft(ServerPlayer player, Item item, int requested) {
        List<RecipeHolder<CraftingRecipe>> recipes = recipesFor(player, item);
        if (recipes.isEmpty()) {
            return FdBotActions.Outcome.fail("no shaped or shapeless crafting recipe outputs "
                    + BuiltInRegistries.ITEM.getKey(item)
                    + " (custom grids such as the caloric-lined EVA upgrade are not assembled)");
        }
        int made = 0;
        String shortfall = null;
        for (int guard = 0; guard < FdBotActions.MAX_COUNT && made < requested; guard++) {
            Attempt attempt = best(player, recipes);
            if (attempt == null || !attempt.complete) {
                shortfall = attempt == null ? "missing ingredients" : attempt.problem;
                break;
            }
            if (attempt.needsTable && !hasCraftingTable(player)) {
                shortfall = "need a crafting table within " + FdBotActions.CRAFTING_TABLE_RADIUS + " blocks";
                break;
            }
            String error = apply(player, attempt);
            if (error != null) {
                shortfall = error;
                break;
            }
            made += attempt.result.getCount();
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (made <= 0) {
            return FdBotActions.Outcome.fail("could not craft " + id + ": " + shortfall);
        }
        String message = "crafted " + made + " " + id;
        if (made != requested || shortfall != null) {
            message += " (requested " + requested + (shortfall == null ? "" : "; " + shortfall) + ")";
        }
        return FdBotActions.Outcome.ok(message, made);
    }

    private static List<RecipeHolder<CraftingRecipe>> recipesFor(ServerPlayer player, Item item) {
        var access = player.registryAccess();
        List<RecipeHolder<CraftingRecipe>> matching = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : player.getServer().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty() && result.is(item)) {
                matching.add(holder);
            }
        }
        matching.sort(Comparator.comparing(holder -> !holder.value().canCraftInDimensions(2, 2)));
        return matching;
    }

    private static Attempt best(ServerPlayer player, List<RecipeHolder<CraftingRecipe>> recipes) {
        Attempt closest = null;
        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            Attempt attempt = plan(player, holder);
            if (attempt.complete) {
                return attempt;
            }
            if (closest == null || attempt.matched > closest.matched) {
                closest = attempt;
            }
        }
        return closest;
    }

    private static Attempt plan(ServerPlayer player, RecipeHolder<CraftingRecipe> holder) {
        CraftingRecipe recipe = holder.value();
        boolean twoByTwo = recipe.canCraftInDimensions(2, 2);
        int grid = twoByTwo ? 2 : 3;
        List<ItemStack> cells = new ArrayList<>();
        for (int i = 0; i < grid * grid; i++) {
            cells.add(ItemStack.EMPTY);
        }
        int[] cellSlots = new int[grid * grid];
        for (int i = 0; i < cellSlots.length; i++) {
            cellSlots[i] = -1;
        }
        int[] reserved = new int[36];
        List<String> missing = new ArrayList<>();
        int matched = 0;
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            List<Ingredient> ingredients = shaped.getIngredients();
            if (width > grid || height > grid || ingredients.size() != width * height) {
                return Attempt.problem(holder.id().toString() + " has an unsupported shaped pattern", 0, grid == 3);
            }
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    Ingredient ingredient = ingredients.get(row * width + col);
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    int slot = findSlot(player, ingredient, reserved);
                    int cell = row * grid + col;
                    if (slot < 0) {
                        missing.add(describe(ingredient));
                        continue;
                    }
                    reserved[slot]++;
                    cellSlots[cell] = slot;
                    cells.set(cell, player.getInventory().getItem(slot).copyWithCount(1));
                    matched++;
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            int cell = 0;
            for (Ingredient ingredient : shapeless.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (cell >= cells.size()) {
                    return Attempt.problem(holder.id().toString() + " does not fit a crafting grid", matched, true);
                }
                int slot = findSlot(player, ingredient, reserved);
                if (slot < 0) {
                    missing.add(describe(ingredient));
                    continue;
                }
                reserved[slot]++;
                cellSlots[cell] = slot;
                cells.set(cell, player.getInventory().getItem(slot).copyWithCount(1));
                matched++;
                cell++;
            }
        }
        if (!missing.isEmpty()) {
            return Attempt.problem("missing " + summarize(missing), matched, grid == 3);
        }
        CraftingInput input = CraftingInput.of(grid, grid, cells);
        Level level = player.level();
        if (!recipe.matches(input, level)) {
            return Attempt.problem("recipe " + holder.id()
                    + " rejected the grid (missing blueprint or other condition)", matched, grid == 3);
        }
        ItemStack result = recipe.assemble(input, player.registryAccess());
        if (result.isEmpty()) {
            return Attempt.problem("recipe " + holder.id() + " produced nothing", matched, grid == 3);
        }
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        int[] consume = new int[36];
        List<ItemStack> refunds = new ArrayList<>();
        for (int cell = 0; cell < cells.size(); cell++) {
            ItemStack placed = cells.get(cell);
            if (placed.isEmpty() || cellSlots[cell] < 0) {
                continue;
            }
            ItemStack left = cell < remaining.size() ? remaining.get(cell) : ItemStack.EMPTY;
            if (!left.isEmpty()
                    && ItemStack.isSameItemSameComponents(left, placed)
                    && left.getCount() == placed.getCount()) {
                continue;
            }
            consume[cellSlots[cell]]++;
            if (!left.isEmpty()) {
                refunds.add(left.copy());
            }
        }
        return new Attempt(true, grid == 3, matched, null, consume, refunds, result.copy());
    }

    private static String apply(ServerPlayer player, Attempt attempt) {
        Inventory inventory = player.getInventory();
        ItemStack[] snapshots = new ItemStack[36];
        for (int slot = 0; slot < 36; slot++) {
            if (attempt.consume[slot] <= 0) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.getCount() < attempt.consume[slot]) {
                return "missing ingredients";
            }
            snapshots[slot] = stack.copy();
        }
        for (int slot = 0; slot < 36; slot++) {
            if (snapshots[slot] != null) {
                inventory.getItem(slot).shrink(attempt.consume[slot]);
            }
        }
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(attempt.result.copy());
        for (ItemStack refund : attempt.refunds) {
            outputs.add(refund.copy());
        }
        String error = give(player, outputs);
        if (error != null) {
            extract(player, outputs);
            for (int slot = 0; slot < 36; slot++) {
                if (snapshots[slot] != null) {
                    inventory.setItem(slot, snapshots[slot]);
                }
            }
        }
        return error;
    }

    private static String give(ServerPlayer player, List<ItemStack> outputs) {
        List<ItemStack> added = new ArrayList<>();
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            int before = copy.getCount();
            player.getInventory().add(copy);
            int moved = before - copy.getCount();
            if (moved > 0) {
                added.add(stack.copyWithCount(moved));
            }
            if (!copy.isEmpty()) {
                extract(player, added);
                return "inventory full";
            }
        }
        return null;
    }

    private static void extract(ServerPlayer player, List<ItemStack> stacks) {
        Inventory inventory = player.getInventory();
        for (ItemStack wanted : stacks) {
            int left = wanted.getCount();
            for (int slot = 0; slot < 36 && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!ItemStack.isSameItemSameComponents(stack, wanted)) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private static int findSlot(ServerPlayer player, Ingredient ingredient, int[] reserved) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getCount() - reserved[slot] <= 0) {
                continue;
            }
            if (ingredient.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean hasCraftingTable(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int radius = FdBotActions.CRAFTING_TABLE_RADIUS;
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (cursor.distSqr(origin) > (double) radius * radius || !level.hasChunkAt(cursor)) {
                continue;
            }
            if (level.getBlockState(cursor).is(Blocks.CRAFTING_TABLE)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return "unknown ingredient";
        }
        String id = BuiltInRegistries.ITEM.getKey(stacks[0].getItem()).toString();
        return stacks.length == 1 ? id : id + " (or similar)";
    }

    private static String summarize(List<String> missing) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : missing) {
            counts.merge(name, 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return text.toString();
    }

    private record Attempt(
            boolean complete,
            boolean needsTable,
            int matched,
            String problem,
            int[] consume,
            List<ItemStack> refunds,
            ItemStack result) {

        static Attempt problem(String problem, int matched, boolean needsTable) {
            return new Attempt(false, needsTable, matched, problem, null, List.of(), ItemStack.EMPTY);
        }
    }
}
