package com.frozendawn.labbridge;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmergencyEvaTerrainGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaBaseAcceptsOrdinaryThreeBlockSlope(GameTestHelper helper) {
        for (int x = 4; x <= 16; x++) for (int z = 4; z <= 16; z++) {
            for (int y = 1; y <= 1 + (x % 4); y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        var center = helper.absolutePos(new BlockPos(10, 1, 10));
        var base = LabEmergencyEvaReplay.findGround(helper.getLevel(), center.getX(), center.getZ());
        helper.assertTrue(base != null && base.getX() == center.getX() && base.getZ() == center.getZ(),
                "An ordinary three-block slope must qualify at the requested site instead of failing or moving elsewhere");
        helper.assertTrue(base.getY() == helper.absolutePos(new BlockPos(10, 5, 10)).getY(),
                "Room floor must cover the highest sampled ground, with a supported foundation beneath it");
        helper.succeed();
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaTerrainSkipsCanopyAndRejectsWater(GameTestHelper helper) {
        helper.setBlock(new BlockPos(10, 1, 10), Blocks.STONE);
        helper.setBlock(new BlockPos(10, 3, 10), ModBlocks.DEAD_LOG.get());
        helper.setBlock(new BlockPos(10, 4, 10), ModBlocks.FROZEN_LEAVES.get());
        helper.setBlock(new BlockPos(10, 5, 10), Blocks.SNOW_BLOCK);
        var floor = helper.absolutePos(new BlockPos(10, 1, 10));
        helper.assertTrue(floor.above().equals(LabEmergencyEvaReplay.groundFeet(helper.getLevel(), floor.getX(), floor.getZ())),
                "Canopy and snow must not be selected as supporting terrain");
        helper.setBlock(new BlockPos(10, 2, 10), Blocks.WATER);
        helper.assertTrue(LabEmergencyEvaReplay.groundFeet(helper.getLevel(), floor.getX(), floor.getZ()) == null,
                "Water above ground must reject a drowning-prone spawn");
        helper.succeed();
    }
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaBaseExistsBeforePlacementReturns(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(10, 3, 10));
        LabEmergencyEvaReplay.placeBase(helper.getLevel(), base);
        helper.assertTrue(helper.getLevel().getBlockState(base.below()).is(Blocks.STONE)
                && helper.getLevel().getBlockState(base.above(5)).is(Blocks.STONE),
                "Placement must finish synchronously before the caller verifies and teleports");
        var heater = (com.frozendawn.block.ThermalHeaterBlockEntity) helper.getLevel().getBlockEntity(base.west(3));
        helper.assertTrue(heater != null && heater.isLit(), "Recovery base heater must contain fuel");
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(base.east(3));
        helper.assertTrue(chest != null && !chest.getItem(0).isEmpty(), "Chest must exist with recovery supplies");
        helper.succeed();
    }
}
