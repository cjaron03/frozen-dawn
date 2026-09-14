package com.frozendawn.debug.architect;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectWildernessRouteGameTest {
    @GameTest(template = "empty_9x5x9", timeoutTicks = 360)
    public static void wildernessSnowStepReplaysBlockedJumpAndShoulderRepair(GameTestHelper helper) {
        // Seed 1337's west trail: z40 at y65, z39 rises one block, with four snow
        // layers added above it. Reproduce those relative collision heights exactly.
        for (int z = 1; z <= 7; z++) {
            helper.setBlock(new BlockPos(4, 0, z), Blocks.STONE);
            if (z <= 3) helper.setBlock(new BlockPos(4, 1, z), Blocks.STONE);
            for (int y = 0; y <= 4; y++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        BlockPos centre = new BlockPos(4, 2, 3);
        var snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4);
        helper.setBlock(centre, snow);
        var target = new ArchitectWildernessTarget(helper.getLevel());
        target.getRandom().setSeed(1337);
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 6))));
        target.setOnGround(true);
        helper.getLevel().addFreshEntity(target);
        Vec3 goal = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 1)));
        target.guideTo(goal);
        var progress = new ArchitectWildernessProgress();
        final boolean[] repaired = {false};
        helper.onEachTick(() -> {
            long still = progress.update(target.position());
            if (!repaired[0] && still >= 100) {
                helper.assertTrue(target.horizontalCollision, "Original cap must reproduce the blocked jump");
                helper.assertTrue(target.distanceToSqr(goal) > 2.25, "Original target must not complete the blocked leg");
                helper.setBlock(centre, Blocks.AIR);
                BlockPos shoulder = ArchitectWildernessTerrain.decorationColumn(centre, centre.north());
                helper.setBlock(shoulder.below(), Blocks.STONE);
                helper.setBlock(shoulder, snow);
                target.guideTo(goal);
                repaired[0] = true;
            }
            if (repaired[0] && target.distanceToSqr(goal) < 1) {
                target.discard();
                helper.succeed();
            }
        });
    }
}
