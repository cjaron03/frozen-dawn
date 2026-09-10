package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world smoke tests: a block and a mob from the mod are placed into a real server level
 * and ticked. These are deliberately thin — the behavioural depth lives in the JUnit policy
 * suite. What they prove is the part JUnit cannot: that this content survives contact with
 * an actual {@code ServerLevel}.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrozenDawnWorldSmokeTest {

    private static final BlockPos HEATER_POS = new BlockPos(4, 1, 4);
    private static final BlockPos SPAWN_POS = new BlockPos(4, 1, 4);

    /**
     * Fuelling a thermal heater is the mod's core survival loop. This exercises the block, its
     * block entity, the item interaction path, and the lit-state transition that registers the
     * heater as a heat source.
     */
    @GameTest(template = GameTestTemplates.EMPTY)
    public static void thermalHeaterLightsWhenFuelled(GameTestHelper helper) {
        GameTestTemplates.placeFloor(helper);
        helper.setBlock(HEATER_POS, ModBlocks.THERMAL_HEATER.get());

        ThermalHeaterBlockEntity heater = helper.getBlockEntity(HEATER_POS);
        helper.assertTrue(heater != null, "thermal heater did not create its block entity");
        helper.assertFalse(heater.isLit(), "thermal heater was lit before being fuelled");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL));
        helper.useBlock(HEATER_POS, player);

        helper.assertTrue(heater.isLit(), "thermal heater did not light after being given coal");
        helper.assertBlockProperty(HEATER_POS, ThermalHeaterBlock.LIT, true);

        // Let it burn: proves serverTick runs without blowing up and the heater stays lit.
        helper.runAfterDelay(40L, () -> {
            helper.assertTrue(heater.isLit(), "thermal heater went out while still holding fuel");
            helper.assertBlockProperty(HEATER_POS, ThermalHeaterBlock.LIT, true);
            helper.succeed();
        });
    }

    /**
     * Spawns a Frostbitten and ticks it. Entity registration, the attribute supplier, and goal
     * construction all run during the spawn itself, so they are proven either way; the mob is
     * spawned without free will so it cannot pathfind off the platform and turn a healthy build
     * red. A gate that cries wolf gets ignored, which is its own kind of false green.
     */
    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 200)
    public static void frostbittenSpawnsAndTicks(GameTestHelper helper) {
        GameTestTemplates.placeFloor(helper);

        FrostbittenEntity frostbitten = helper.spawnWithNoFreeWill(ModEntities.FROSTBITTEN.get(), SPAWN_POS);

        helper.runAfterDelay(60L, () -> {
            helper.assertTrue(frostbitten.isAlive(), "frostbitten died while idling on a stone floor");
            helper.assertEntityPresent(ModEntities.FROSTBITTEN.get());
            helper.succeed();
        });
    }
}
