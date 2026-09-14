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

    @GameTest(template = "empty_9x5x9", timeoutTicks = 40)
    public static void wildernessProductionSnowAccumulatesWithinFootprint(GameTestHelper helper) {
        var level = helper.getLevel();
        // The GameTest grid is below the flat world's surface; place the weather
        // probe above it so heightmap sampling reaches this exposed floor.
        BlockPos centre=helper.absolutePos(new BlockPos(4,1,4)).atY(200);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
            level.setBlockAndUpdate(centre.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
            for(int y=0;y<=4;y++)level.setBlockAndUpdate(centre.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        var random=net.minecraft.util.RandomSource.create(1337);
        com.frozendawn.world.BlockFreezer.tickRegion(level,5,0.5f,random,centre,1);
        helper.assertTrue(level.getBlockState(centre.below()).is(com.frozendawn.init.ModBlocks.FROZEN_DIRT.get()),
                "Production freezing must transform exposed dirt");
        helper.assertTrue(level.getBlockState(centre.east(2).below()).is(Blocks.DIRT),
                "Freezer sampling must stay inside its footprint");
        int mutations=0;
        for(int tick=0;tick<=100;tick+=5)
            mutations+=com.frozendawn.world.SnowAccumulator.tickRegion(level,5,0.5f,tick,random,centre,1);
        helper.assertTrue(mutations>0,"Production snowfall must report actual successful mutations");
        helper.assertTrue(level.getBlockState(centre).is(Blocks.SNOW_BLOCK),"Repeated phase-5 snow must become a solid block");
        helper.assertTrue(level.getBlockState(centre.above(2)).is(Blocks.SNOW_BLOCK),"Production three-block drift cap must be reached");
        helper.assertTrue(level.getBlockState(centre.above(3)).isAir(),"Snow must stop at the production depth cap");
        helper.assertTrue(level.getBlockState(centre.east(2)).isAir(),"Sampling must stay inside its footprint");
        int stopped=com.frozendawn.world.SnowAccumulator.tickRegion(level,6,0.8f,200,random,centre,1);
        helper.assertTrue(stopped==0,"Late phase 6 must stop precipitation");
        helper.succeed();
    }

    @GameTest(template = "empty_9x5x9", timeoutTicks = 600)
    public static void wildernessConstructionTargetTraversesIntactCourse(GameTestHelper helper) {
        // GameTestServer creates only its test level. The live blueprint uses fixed
        // coordinates far from the randomized GameTest grid.
        var level=helper.getLevel();
        var terrain=new ArchitectWildernessTerrain(level,1337);
        for(int x=2;x<=5;x++)for(int z=3;z<=4;z++){
            if(!level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x,z))){
                level.setChunkForced(x,z,true);
                terrain.acquiredChunks.add(net.minecraft.world.level.ChunkPos.asLong(x,z));
            }
            level.getChunk(x,z);
        }
        terrain.course=new ArchitectConstructionCourse(terrain);
        while(!terrain.applyEdits(2048)){}
        // This invokes the same target route preflight as the live command.
        var run=new ArchitectWildernessRun(terrain,ArchitectWildernessRun.Scenario.CONSTRUCTION);
        run.villager.setNoAi(false);run.villager.setNoGravity(false);
        run.villager.travelSpeed(0.9);
        var goal=terrain.course.route.getLast();
        run.villager.guideTo(goal);
        helper.onEachTick(()->{
            if(run.villager.distanceToSqr(goal)<1){
                helper.assertTrue(!Boolean.TRUE.equals(terrain.course.summary().get("ascent")),
                        "A target traversal alone must not satisfy Architect construction assertions");
                run.dispose();
                for(long packed:terrain.acquiredChunks){
                    var chunk=new net.minecraft.world.level.ChunkPos(packed);
                    level.setChunkForced(chunk.x,chunk.z,false);
                }
                helper.succeed();
            }
        });
    }
}
