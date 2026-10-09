package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FrozenAtmosphereBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrozenAtmospherePlacementGameTest {

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void replacesBrightAndDarkDepositInSurvival(GameTestHelper helper) {
        checkPlacement(helper, GameType.SURVIVAL);
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void replacesBrightAndDarkDepositInCreative(GameTestHelper helper) {
        checkPlacement(helper, GameType.CREATIVE);
    }

    private static void checkPlacement(GameTestHelper helper, GameType mode) {
        GameTestTemplates.placeFloor(helper);
        Player player = helper.makeMockPlayer(mode);
        // The vanilla mock overrides isCreative but leaves the actual abilities at defaults.
        mode.updatePlayerAbilities(player.getAbilities());
        player.setPos(helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
        for (boolean dark : new boolean[] {false, true}) {
            BlockPos pos = new BlockPos(dark ? 5 : 3, 1, 3);
            helper.setBlock(pos, ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState()
                    .setValue(FrozenAtmosphereBlock.DARK, dark));
            ItemStack held = new ItemStack(Items.STONE, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            BlockPos absolute = helper.absolutePos(pos);
            // Hit the layer's actual top face, exercising the same target selection as a click.
            BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(0.5, 0.125, 0.5),
                    Direction.UP, absolute, false);
            var result = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction(), "block placement was rejected");
            helper.assertBlockPresent(Blocks.STONE, pos);
            helper.assertBlockPresent(Blocks.AIR, pos.above());
            helper.assertTrue(held.getCount() == (mode == GameType.CREATIVE ? 2 : 1),
                    "placement consumed an incorrect number of held blocks");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(absolute).inflate(0.2)).isEmpty(),
                    "replacing the deposit spawned an unintended shard drop");
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void depositStillDropsShardWhenMined(GameTestHelper helper) {
        GameTestTemplates.placeFloor(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        for (boolean dark : new boolean[] {false, true}) {
            BlockPos pos = new BlockPos(dark ? 5 : 3, 1, 3);
            helper.setBlock(pos, ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState()
                    .setValue(FrozenAtmosphereBlock.DARK, dark));
            BlockPos absolute = helper.absolutePos(pos);
            helper.assertTrue(helper.getLevel().destroyBlock(absolute, true, player),
                    "deposit could not be broken");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(absolute).inflate(0.2));
            helper.assertTrue(drops.size() == 1
                            && drops.getFirst().getItem().is(ModItems.FROZEN_ATMOSPHERE_SHARD.get())
                            && drops.getFirst().getItem().getCount() == 1,
                    "mining no longer drops exactly one frozen atmosphere shard");
        }
        helper.succeed();
    }
}
