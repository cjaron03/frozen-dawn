package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.FoodFrostPresentation;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.RationWarmerItem;
import com.frozendawn.recipe.RationWarmerChargingRecipe;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RationWarmerGameTest {
    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerWorldFunctionsLoad(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals("ration_warmer_check") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(functions.size() == 10, "All ten generated live-world functions included");
        functions.forEach((file, resource) -> {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ration_warmer_check",
                    file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Actual function loaded: " + id);
            try (var reader = resource.openAsReader()) {
                var lines = reader.lines().toList();
                for (var line : lines) {
                    if (!line.startsWith("give @s patchouli:guide_book")) continue;
                    var book = net.minecraft.commands.arguments.item.ItemArgument.item(net.minecraft.commands.CommandBuildContext.simple(
                                    server.registryAccess(), server.getWorldData().enabledFeatures()))
                            .parse(new com.mojang.brigadier.StringReader(line.substring("give @s ".length())))
                            .createItemStack(1, false);
                    helper.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                            book, StarterBooks.createGuideBook()),
                            "Setup and repair commands supply the actual ORSA Field Manual, not an undefined book");
                }
                net.minecraft.commands.functions.CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), lines);
            } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ration_warmer"));
    }
    private static ItemStack warmer(int charges) {
        var stack = new ItemStack(ModItems.RATION_WARMER.get());
        stack.set(ModDataComponents.RATION_WARMER_CHARGES, charges);
        return stack;
    }
    private static ItemStack food(int ticks) {
        var stack = new ItemStack(Items.BREAD, 64);
        if (ticks > 0) stack.set(ModDataComponents.FROST_TICKS, ticks);
        return stack;
    }
    private static long now(FakePlayer player) { return player.getServer().overworld().getGameTime(); }
    private static void equip(FakePlayer player, ItemStack warmer, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, warmer);
        player.setItemInHand(InteractionHand.OFF_HAND, food);
    }
    private static boolean activate(FakePlayer player) {
        return player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerWholeStackTenSecondsAndWarmExpiry(GameTestHelper helper) {
        var player = player(helper);
        for (int frost : new int[] {600, 2400, 5900}) {
            var device = warmer(4); var food = food(frost); equip(player, device, food);
            long start = now(player);
            helper.assertTrue(activate(player), "Real main-hand use starts thaw");
            helper.assertTrue(RationWarmerItem.charges(device) == 3, "One charge consumed immediately");
            helper.assertFalse(activate(player), "Repeated activation cannot debit twice");
            player.setPos(player.position().add(100, 0, 0));
            for (int second = 1; second < 10; second++) FoodFrostHandler.updateFood(food, -200, 1, start + second * 20);
            helper.assertTrue(!FoodFrostHandler.isFrostRuined(food), "Active thaw prevents new damage even near the ruined threshold");
            RationWarmerHandler.tickPlayer(player, start + 199);
            helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == frost, "Nothing thawed before ten seconds");
            RationWarmerHandler.tickPlayer(player, start + 200);
            helper.assertTrue(food.getCount() == 64 && !food.has(ModDataComponents.FROST_TICKS), "Entire stack fresh without changing count");
            helper.assertTrue(food.getOrDefault(ModDataComponents.FOOD_WARM_UNTIL, 0L) == start + 800, "Thirty-second window after completion");
            FoodFrostHandler.updateFood(food, -200, 1, start + 799);
            helper.assertTrue(!food.has(ModDataComponents.FROST_TICKS), "Extreme ambient cold cannot bypass the window");
            FoodFrostHandler.updateFood(food, -200, 1, start + 800);
            helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 60 && !food.has(ModDataComponents.FOOD_WARM_UNTIL),
                    "Normal exposure resumes exactly at expiry");
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 260)
    public static void rationWarmerActualServerTickCompletes(GameTestHelper helper) {
        var player = player(helper); var device = warmer(1); var food = food(2400); equip(player, device, food);
        long start = now(player);
        helper.assertTrue(activate(player), "Start actual tick-driven job");
        helper.runAfterDelay(205, () -> {
            helper.assertTrue(!RationWarmerHandler.isActive(player) && !food.has(ModDataComponents.FROST_TICKS),
                    "Real registered ServerTick subscriber finishes the job");
            helper.assertTrue(food.getOrDefault(ModDataComponents.FOOD_WARM_UNTIL, 0L) == start + 800,
                    "Real server completion occurred at 200 ticks");
            helper.assertTrue(RationWarmerItem.charges(device) == 0, "Final charge reaches zero without negative debit");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerFreshResistantEmptyAndWrongHandNoDebit(GameTestHelper helper) {
        var player = player(helper); var device = warmer(4);
        for (var food : List.of(food(0), food(599), new ItemStack(Items.DRIED_KELP), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.STONE))) {
            food.set(ModDataComponents.FROST_TICKS, food.is(Items.BREAD) ? food.getOrDefault(ModDataComponents.FROST_TICKS, 0) : 6000);
            equip(player, device, food);
            helper.assertFalse(activate(player), "Fresh, resistant or non-food rejected");
            helper.assertTrue(RationWarmerItem.charges(device) == 4 && !RationWarmerHandler.isActive(player), "No charge or job created");
        }
        equip(player, warmer(0), food(2400));
        helper.assertFalse(activate(player), "Empty warmer cannot start");
        equip(player, food(2400), device);
        helper.assertFalse(device.use(player.level(), player, InteractionHand.OFF_HAND).getResult().consumesAction(), "Offhand device cannot activate");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerRuinedDamageRetainedAndNotBodyProtection(GameTestHelper helper) {
        var player = player(helper); var food = food(5990);
        FoodFrostHandler.updateFood(food, -90, 1, now(player));
        helper.assertTrue(FoodFrostHandler.isFrostRuined(food), "Fixture earns actual permanent frost damage");
        equip(player, warmer(2), food); float health = player.getHealth();
        var attachments = player.getData(com.frozendawn.init.ModAttachments.SUIT_INTEGRITY).serializeNBT(player.registryAccess());
        long start = now(player); helper.assertTrue(activate(player), "Ruined food can recover to damaged Frozen");
        RationWarmerHandler.tickPlayer(player, start + 200);
        helper.assertTrue(FoodFrostHandler.isFrostRuined(food) && food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 2400,
                "Permanent marker and damaged Frozen floor retained");
        for (int second = 1; second <= 100; second++) FoodFrostHandler.updateFood(food, 50, 1, start + 200 + second * 20);
        helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 2400, "Heater cannot cure permanent damage afterward");
        helper.assertTrue(player.getHealth() == health && player.getData(com.frozendawn.init.ModAttachments.SUIT_INTEGRITY).serializeNBT(player.registryAccess()).equals(attachments),
                "Warmer never alters body support or suit state");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerCancellationCannotTransferOrRefund(GameTestHelper helper) {
        var player = player(helper);
        for (int action = 0; action < 7; action++) {
            player.getInventory().selected = 0;
            var device = warmer(4); var food = food(2400); equip(player, device, food);
            long start = now(player); helper.assertTrue(activate(player), "Cancellation case starts");
            ItemStack replacement = food;
            switch (action) {
                case 0 -> { replacement = food.copy(); player.setItemInHand(InteractionHand.OFF_HAND, replacement); }
                case 1 -> food.split(1);
                case 2 -> { player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); player.getInventory().setItem(5, food); }
                case 3 -> food.grow(1);
                case 4 -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                case 5 -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player,
                        net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER));
                case 6 -> NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic()));
            }
            RationWarmerHandler.tickPlayer(player, start + 200);
            helper.assertFalse(RationWarmerHandler.isActive(player), "Invalid job cancelled");
            helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 2400
                    && replacement.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 2400
                    && !replacement.has(ModDataComponents.FOOD_WARM_UNTIL), "No replacement or split receives completed thaw");
            helper.assertTrue(RationWarmerItem.charges(device) == 3, "Cancellation never refunds or debits twice");
            player.getInventory().selected = 0;
            if (action == 4) {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
                RationWarmerHandler.tickPlayer(player, start + 10000);
                helper.assertFalse(food.has(ModDataComponents.FOOD_WARM_UNTIL), "Reconnect cannot complete a lost job");
            }
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerStowedDeviceKeepsHeating(GameTestHelper helper) {
        var player = player(helper);
        for (int action = 0; action < 3; action++) {
            player.getInventory().selected = 0;
            var device = warmer(2); var food = food(2400); equip(player, device, food);
            long start = now(player);
            helper.assertTrue(activate(player), "Thaw starts with hands equipped");
            switch (action) {
                case 0 -> player.getInventory().selected = 1;
                case 1 -> { player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player.getInventory().setItem(6, device); }
                case 2 -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            var secondDevice = warmer(4);
            player.setItemInHand(InteractionHand.MAIN_HAND, secondDevice);
            helper.assertFalse(activate(player), "Changing devices cannot start or charge a second concurrent job");
            RationWarmerHandler.tickPlayer(player, start + 100);
            helper.assertTrue(RationWarmerHandler.isActive(player) && RationWarmerHandler.isThawing(food),
                    "Food keeps warming after the main hand changes");
            FoodFrostHandler.updateFood(food, -200, 1, start + 100);
            helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 2400, "Cold remains suppressed during stowed service");
            RationWarmerHandler.tickPlayer(player, start + 200);
            helper.assertTrue(!food.has(ModDataComponents.FROST_TICKS) && food.getCount() == 64,
                    "Original whole offhand stack finishes at ten seconds");
            helper.assertTrue(RationWarmerItem.charges(device) == 1 && RationWarmerItem.charges(secondDevice) == 4,
                    "Only the original activation charge was spent");
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerBulkChargingNativeMenus(GameTestHelper helper) {
        var player = player(helper);
        var access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)));
        var configurations = List.of(new int[] {1}, new int[] {2}, new int[] {3}, new int[] {4}, new int[] {8}, new int[] {64},
                new int[] {1, 1}, new int[] {1, 1, 1}, new int[] {1, 1, 1, 1}, new int[] {3, 1, 1},
                new int[] {1, 1, 64, 1}, new int[] {2, 2});
        for (boolean small : new boolean[] {false, true}) {
            for (boolean shift : new boolean[] {false, true}) {
                for (int charge = 0; charge < 4; charge++) {
                    for (int[] dust : configurations) {
                        if (small && dust.length > 3) continue;
                        player.getInventory().clearContent();
                        net.minecraft.world.inventory.AbstractContainerMenu menu = small
                                ? new net.minecraft.world.inventory.InventoryMenu(player.getInventory(), true, player)
                                : new CraftingMenu(1, player.getInventory(), access);
                        int deviceSlot = small ? 4 : 9;
                        var device = warmer(charge);
                        device.set(DataComponents.CUSTOM_NAME, Component.literal("Bulk warmer"));
                        menu.getSlot(deviceSlot).set(device);
                        for (int i = 0; i < dust.length; i++) menu.getSlot(i + 1).set(new ItemStack(Items.REDSTONE, dust[i]));
                        int total = java.util.Arrays.stream(dust).sum();
                        int used = Math.min(4 - charge, total);
                        var preview = menu.getSlot(0).getItem();
                        helper.assertTrue(preview.is(ModItems.RATION_WARMER) && RationWarmerItem.charges(preview) == charge + used,
                                "Native output previews exactly the available charge gain");
                        helper.assertTrue(device.getCount() == 1 && RationWarmerItem.charges(device) == charge,
                                "Preview never consumes or changes the input device");
                        for (int i = 0; i < dust.length; i++) helper.assertTrue(menu.getSlot(i + 1).getItem().getCount() == dust[i],
                                "Preview leaves every redstone stack intact");
                        ItemStack result;
                        if (shift) result = menu.quickMoveStack(player, 0);
                        else { menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player); result = menu.getCarried(); }
                        helper.assertTrue(result.is(ModItems.RATION_WARMER) && result.getCount() == 1
                                        && RationWarmerItem.charges(result) == charge + used
                                        && result.getHoverName().getString().equals("Bulk warmer"),
                                "Native click/shift-click returns exactly one charged original device with metadata");
                        helper.assertTrue(menu.getSlot(deviceSlot).getItem().isEmpty(), "Input device consumed once");
                        int leftover = 0;
                        for (int i = 0; i < dust.length; i++) leftover += menu.getSlot(i + 1).getItem().getCount();
                        helper.assertTrue(leftover == total - used, "Exactly one redstone per gained charge; excess stays in the grid");
                        long devices = player.getInventory().items.stream().filter(stack -> stack.is(ModItems.RATION_WARMER)).count();
                        helper.assertTrue(devices == (shift ? 1 : 0), "No duplicate device escapes through native take accounting");
                        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Repeated output take cannot charge or duplicate again");
                    }
                }
            }
        }
        player.getInventory().clearContent();
        var menu = new CraftingMenu(1, player.getInventory(), access);
        menu.getSlot(9).set(warmer(4)); menu.getSlot(5).set(new ItemStack(Items.REDSTONE, 64));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Full warmer accepts no dust and leaves all items untouched");
        menu.getSlot(9).set(warmer(0)); menu.getSlot(2).set(new ItemStack(Items.DIRT));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Unrelated ingredients reject charging");
        menu.getSlot(2).set(warmer(0));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Two warmers cannot become one or duplicate a charge");
        menu.getSlot(2).set(ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && menu.getSlot(5).getItem().getCount() == 64
                && RationWarmerItem.charges(menu.getSlot(9).getItem()) == 0,
                "Blocked shift-click with full inventory consumes neither device nor dust");
        player.getInventory().clearContent();
        var ordinary = new net.minecraft.world.inventory.InventoryMenu(player.getInventory(), true, player);
        ordinary.getSlot(4).set(new ItemStack(Items.OAK_LOG, 2));
        ordinary.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(ordinary.getCarried().is(Items.OAK_PLANKS) && ordinary.getCarried().getCount() == 4
                && ordinary.getSlot(4).getItem().getCount() == 1, "Ordinary crafting keeps its vanilla per-item cost");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerNativeRecipesShiftClickCapacityAndPreservation(GameTestHelper helper) {
        var player = player(helper);
        var access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)));
        var menu = new CraftingMenu(1, player.getInventory(), access);
        var ingredients = List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.IRON_INGOT,
                Items.COPPER_INGOT, Items.REDSTONE, Items.COPPER_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT, Items.IRON_INGOT);
        for (int i = 0; i < 9; i++) menu.getSlot(i + 1).set(new ItemStack(ingredients.get(i)));
        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.RATION_WARMER), "Native shaped crafting result appears");
        var crafted = menu.quickMoveStack(player, 0);
        helper.assertTrue(crafted.is(ModItems.RATION_WARMER) && RationWarmerItem.charges(crafted) == 0, "Shift-click crafts exactly one empty device");
        for (int i = 1; i <= 9; i++) helper.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Assembly consumes exactly the nine ingredient units");
        var device = player.getInventory().getItem(8);
        helper.assertTrue(device.is(ModItems.RATION_WARMER), "Only output device moved into native player inventory");
        device.set(DataComponents.CUSTOM_NAME, Component.literal("Owner's warmer"));
        player.getInventory().setItem(8, ItemStack.EMPTY);
        for (int charge = 1; charge <= 4; charge++) {
            menu.getSlot(1).set(device); menu.getSlot(2).set(new ItemStack(Items.REDSTONE));
            var result = menu.quickMoveStack(player, 0);
            helper.assertTrue(result.is(ModItems.RATION_WARMER) && RationWarmerItem.charges(result) == charge,
                    "Actual shift-click adds exactly one charge, retaining prior balance");
            helper.assertTrue(result.getHoverName().getString().equals("Owner's warmer"), "Custom device metadata preserved");
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Existing device consumed once; no duplicate remainder; one dust consumed");
            device = player.getInventory().getItem(8); player.getInventory().setItem(8, ItemStack.EMPTY);
            menu.getSlot(2).set(ItemStack.EMPTY);
        }
        menu.getSlot(1).set(device); menu.getSlot(2).set(new ItemStack(Items.REDSTONE));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.quickMoveStack(player, 0).isEmpty(), "Full warmer rejects recharge without consuming dust");
        var input = CraftingInput.of(2, 1, List.of(warmer(2), new ItemStack(Items.REDSTONE)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertTrue(recipe.value() instanceof RationWarmerChargingRecipe && !recipe.value().isSpecial()
                && recipe.value().getIngredients().size() == 2, "Native shapeless subclass visible to JEI and Patchouli");
        var chargingIngredient = recipe.value().getIngredients().getFirst();
        for (int charge = 0; charge < 4; charge++) helper.assertTrue(chargingIngredient.test(warmer(charge)),
                "Recipe ingredient accepts each available charge level");
        helper.assertFalse(chargingIngredient.test(warmer(4)), "Recipe transfer ingredient itself excludes a full device");
        helper.assertTrue(chargingIngredient.getItems().length == 4, "Four distinct ingredient previews are available to JEI");
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var serializer = (RationWarmerChargingRecipe.Serializer) recipe.value().getSerializer();
            serializer.streamCodec().encode(bytes, (RationWarmerChargingRecipe) recipe.value());
            var decoded = serializer.streamCodec().decode(bytes);
            helper.assertTrue(decoded.matches(input, helper.getLevel()) && RationWarmerItem.charges(decoded.assemble(input, helper.getLevel().registryAccess())) == 3,
                    "Client recipe wire round-trip retains charging behavior");
        } finally { bytes.release(); }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void rationWarmerItemSaveAndWirePersistence(GameTestHelper helper) {
        var device = warmer(3); device.set(DataComponents.CUSTOM_NAME, Component.literal("Persistent device"));
        var food = food(5990); FoodFrostHandler.updateFood(food, -90, 1, 0); FoodFrostHandler.portableThaw(food, 1234);
        for (var stack : List.of(device, food)) {
            var provider = helper.getLevel().registryAccess();
            var saved = ItemStack.parse(provider, stack.save(provider)).orElseThrow();
            helper.assertTrue(ItemStack.matches(stack, saved), "Charges, window and permanent damage survive item save/reload");
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), provider);
            try {
                ItemStack.STREAM_CODEC.encode(bytes, stack);
                helper.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(bytes)), "Item components survive actual server/client codec");
            } finally { bytes.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void foodFrostThawMovementAndReequipPreservation(GameTestHelper helper) {
        var player = player(helper); var food = food(1000); player.getInventory().setItem(0, food);
        for (int second = 1; second <= 5; second++) FoodFrostHandler.tickInventory(player, 20);
        helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 800, "Five warm seconds retain all 200 thaw ticks");
        player.getInventory().setItem(0, ItemStack.EMPTY); player.getInventory().setItem(4, food);
        FoodFrostHandler.tickInventory(player, -90);
        helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 850, "Food carries its own frost across slots");
        var changed = food.copy(); changed.set(ModDataComponents.FROST_TICKS, 900);
        helper.assertTrue(FoodFrostPresentation.onlyFrostChanged(food, changed, false), "Frost-only sync does not bob held food");
        helper.assertFalse(FoodFrostPresentation.onlyFrostChanged(food, changed, true), "Changing actual slots still re-equips");
        changed.setCount(32);
        helper.assertFalse(FoodFrostPresentation.onlyFrostChanged(food, changed, false), "Stack-count changes retain normal animation");
        changed = food.copy(); changed.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        helper.assertFalse(FoodFrostPresentation.onlyFrostChanged(food, changed, false), "Other metadata changes retain normal animation");
        helper.succeed();
    }
}
