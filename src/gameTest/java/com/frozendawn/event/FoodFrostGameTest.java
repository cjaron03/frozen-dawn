package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModDataComponents;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class FoodFrostGameTest {
    @GameTest(template = GameTestTemplates.EMPTY)
    public static void foodFrostFiveSecondAccumulation(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "food_frost"));
        var food = new ItemStack(Items.BREAD, 32);
        player.getInventory().setItem(0, food);
        for (int second = 1; second <= 5; second++) FoodFrostHandler.tickInventory(player, -90);
        helper.assertTrue(food.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 250,
                "Five seconds at -90C must preserve all 250 frost ticks, actual=" + food.getOrDefault(ModDataComponents.FROST_TICKS, 0));
        helper.succeed();
    }
    @GameTest(template = GameTestTemplates.EMPTY)
    public static void foodFrostSlotReplacementDoesNotTransfer(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "food_swap"));
        var original = new ItemStack(Items.BREAD);
        original.set(ModDataComponents.FROST_TICKS, 550);
        player.getInventory().setItem(0, original);
        FoodFrostHandler.tickInventory(player, -90);
        var replacement = new ItemStack(Items.CARROT);
        player.getInventory().setItem(0, replacement);
        FoodFrostHandler.tickInventory(player, -90);
        helper.assertTrue(replacement.getOrDefault(ModDataComponents.FROST_TICKS, 0) == 50,
                "Fresh replacement must gain its own 50 ticks, never the prior slot's 600 ticks");
        helper.succeed();
    }
}
