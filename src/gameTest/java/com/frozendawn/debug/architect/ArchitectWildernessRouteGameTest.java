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

    @GameTest(template = "empty_9x5x9", timeoutTicks = 200)
    public static void wildernessTargetAdvancesPastSnowRaisedWaypoint(GameTestHelper helper) {
        // Replay the failed weather waypoint: original feet Y + 1.625 from snow.
        for(int x=2;x<=6;x++)for(int z=1;z<=7;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            helper.setBlock(new BlockPos(x,1,z),Blocks.SNOW_BLOCK);
            helper.setBlock(new BlockPos(x,2,z),Blocks.SNOW.defaultBlockState()
                    .setValue(SnowLayerBlock.LAYERS,6));
        }
        var target=new ArchitectWildernessTarget(helper.getLevel());
        Vec3 oldGoal=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,4)));
        Vec3 next=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,1)));
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,7))).add(0,1.625,0));
        target.setOnGround(true);
        helper.getLevel().addFreshEntity(target);
        boolean[] advanced={false};
        helper.onEachTick(()->{
            Vec3 original=advanced[0]?next:oldGoal;
            Vec3 resolved=ArchitectWildernessWaypoint.surface(helper.getLevel(),target,original);
            helper.assertTrue(Math.abs(resolved.y-original.y-1.625)<0.001,
                    "Destination must use the snow collision surface");
            target.guideTo(resolved);
            if(ArchitectWildernessWaypoint.arrived(target.position(),resolved)){
                if(!advanced[0]){
                    helper.assertTrue(target.distanceToSqr(oldGoal)>2.25,
                            "The old 3D arrival check must reproduce the missed waypoint");
                    advanced[0]=true;
                }else{
                    helper.assertTrue(!ArchitectWildernessWaypoint.arrived(resolved.add(0,-5,0),resolved),
                            "A different floor below the waypoint must not count as arrival");
                    target.discard();helper.succeed();
                }
            }
        });
    }

    @GameTest(template = "empty_9x5x9", timeoutTicks = 500)
    public static void wildernessTargetRecoversThroughPhysicalSideDetour(GameTestHelper helper) {
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            if(z<=3)helper.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        }
        for(int z=1;z<=7;z++)for(int y=1;y<=4;y++){
            helper.setBlock(new BlockPos(3,y,z),Blocks.STONE);
            helper.setBlock(new BlockPos(5,y,z),Blocks.STONE);
        }
        BlockPos cap=new BlockPos(4,2,3);
        helper.setBlock(cap,Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,4));
        var target=new ArchitectWildernessTarget(helper.getLevel());
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,6))));
        target.setOnGround(true);
        var events=new java.util.ArrayList<String>();
        boolean[] opened={false},collided={false};
        var details=new java.util.ArrayList<String>();
        target.enableRecovery((kind,detail)->{
            events.add(kind);details.add(kind+": "+detail);
            if(kind.equals("TARGET_PATH_INEFFECTIVE")&&!opened[0]){
                opened[0]=true;
                // Expose a side route after the direct path has demonstrably failed.
                // The snow obstacle itself remains intact throughout the test.
                for(int z:new int[]{1,2,5,6})for(int y=1;y<=3;y++)
                    if(z>3||y>=2)helper.setBlock(new BlockPos(5,y,z),Blocks.AIR);
            }
        });
        helper.getLevel().addFreshEntity(target);
        Vec3 goal=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,2,1)));
        target.guideTo(goal);
        helper.onEachTick(()->{
            collided[0]|=target.horizontalCollision;
            helper.assertTrue(target.recoveryFailure()==null,"Side route should recover: "+details.stream().filter(e->e.startsWith("TARGET_DETOUR")).toList());
            if(target.distanceToSqr(goal)<1){
                helper.assertTrue(collided[0],"The direct snow step must physically block the villager");
                helper.assertTrue(events.contains("TARGET_DETOUR_ATTEMPT"),"Recovery must select a detour");
                helper.assertTrue(events.contains("TARGET_DETOUR_REACHED"),"The villager must physically complete its detour");
                helper.assertTrue(events.contains("TARGET_REJOIN_STARTED"),"The checked onward path must actually be followed");
                helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(cap)).is(Blocks.SNOW),
                        "Recovery must preserve the obstructing snow");
                target.discard();helper.succeed();
            }
        });
    }

    @GameTest(template = "empty_9x5x9", timeoutTicks = 300)
    public static void wildernessTargetStopsAfterExhaustedDetours(GameTestHelper helper) {
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        for(int y=1;y<=4;y++)for(int x=3;x<=5;x++)for(int z=3;z<=5;z++)
            if(x!=4||z!=4)helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var target=new ArchitectWildernessTarget(helper.getLevel());
        target.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,4))));
        target.setOnGround(true);
        var events=new java.util.ArrayList<String>();
        target.enableRecovery((kind,detail)->events.add(kind));
        helper.getLevel().addFreshEntity(target);
        target.guideTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7,1,7))));
        helper.onEachTick(()->{
            if(target.recoveryFailure()!=null){
                helper.assertTrue(events.stream().filter("TARGET_DETOUR_ATTEMPT"::equals).count()==3,
                        "Recovery must stop after exactly three bounded attempts");
                helper.assertTrue(events.contains("TARGET_RECOVERY_FAILED"),"Failure must be diagnosed");
                target.discard();helper.succeed();
            }
        });
    }

    @GameTest(template = "empty_9x5x9", timeoutTicks = 40)
    public static void wildernessDetourRequiresOnwardConnection(GameTestHelper helper) {
        for(int x=1;x<=7;x++)for(int z=0;z<=7;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        for(int x=3;x<=5;x++)for(int z=0;z<=2;z++)for(int y=1;y<=4;y++)
            if(x!=4||z!=1)helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var level=helper.getLevel();
        var target=new ArchitectWildernessTarget(level);
        Vec3 start=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,7)));
        Vec3 candidate=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,4)));
        Vec3 goal=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,1)));
        target.setPos(start);target.setOnGround(true);
        var outbound=target.getNavigation().createPath(BlockPos.containing(candidate),0);
        helper.assertTrue(outbound!=null&&outbound.canReach(),"The side point itself must be reachable");
        var probe=new net.minecraft.world.entity.npc.Villager(net.minecraft.world.entity.EntityType.VILLAGER,level);
        probe.setOnGround(true);
        helper.assertTrue(target.onwardRoute(probe,candidate,goal)==null,
                "A reachable side point must be rejected when its onward destination is sealed");
        helper.assertTrue(target.position().equals(start),"Planning must not move the real target");
        helper.assertTrue(level.getEntity(probe.getUUID())==null,"Planning must not spawn a probe entity");
        helper.setBlock(new BlockPos(4,1,2),Blocks.AIR);
        helper.setBlock(new BlockPos(4,2,2),Blocks.AIR);
        helper.assertTrue(target.onwardRoute(probe,candidate,goal)!=null,
                "Opening a physical connection must make the onward route usable");
        helper.succeed();
    }
}
