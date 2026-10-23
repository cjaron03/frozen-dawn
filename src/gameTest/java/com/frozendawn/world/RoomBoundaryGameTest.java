package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomBoundaryGameTest {
    @BeforeBatch(batch="room_boundary")
    public static void report(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_boundary",timeoutTicks=100)
    public static void roomBoundaryCuboidHasExactAreaAndOutwardDirections(GameTestHelper h) {
        scene(h, () -> {
            var center=box(h,3,2,4);var level=h.getLevel();var g=RoomAtmosphere.inspect(level,center);
            h.assertTrue(g.seal()==RoomAtmosphere.Seal.SEALED&&g.cells().size()==24,"3 by 2 by 4 room has 24 air cells");
            h.assertTrue(g.boundaryFaces().size()==52,"Surface area is 2*(3*2+2*4+3*4) = 52 faces");
            for(var direction:Direction.values()) {
                long count=g.boundaryFaces().stream().filter(face->face.outwardDirection()==direction).count();
                int expected=switch(direction) {case DOWN,UP->12;case EAST,WEST->8;case NORTH,SOUTH->6;};
                h.assertTrue(count==expected,"Exact face area in direction "+direction);
            }
            validate(h,g);
            var other=RoomAtmosphere.inspect(level,center.offset(2,1,3));
            h.assertTrue(g.boundaryFaces().equals(other.boundaryFaces()),"Faces do not depend on flood-fill origin");
            try {g.boundaryFaces().clear();h.fail("Boundary snapshots must be immutable");}
            catch(UnsupportedOperationException expected) { }
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_boundary",timeoutTicks=100)
    public static void roomBoundaryInteriorBlockExposesAllSixContactsWithoutWallDeduplication(GameTestHelper h) {
        scene(h, () -> {
            var center=box(h,3,3,3);var level=h.getLevel();var before=RoomAtmosphere.inspect(level,center);
            h.assertTrue(before.boundaryFaces().size()==54,"Unobstructed cube starts with 54 faces");
            var pillar=center.offset(1,1,1);level.setBlock(pillar,Blocks.STONE.defaultBlockState(),2);
            var g=RoomAtmosphere.inspect(level,center);
            h.assertTrue(g.cells().size()==26&&g.boundaryFaces().size()==60,"Interior block replaces one cell and adds six contacts");
            h.assertTrue(g.walls().size()==55,"Physical wall-block count remains deduplicated");
            h.assertTrue(g.boundaryFaces().stream().filter(face->face.wallCell().equals(pillar)).count()==6,
                    "Same physical block exposes six distinct air-to-wall faces");
            for(var direction:Direction.values())h.assertTrue(g.boundaryFaces().contains(
                    new RoomAtmosphere.BoundaryFace(pillar.relative(direction.getOpposite()),direction)),
                    "Oriented pillar contact is preserved for "+direction);
            validate(h,g);
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_boundary",timeoutTicks=100)
    public static void roomBoundaryMergeAndSplitRebuildSharedWallContacts(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var origin=box(h,9,3,3);var partition=origin.east(4);
            partition(h,partition,true);
            var left=RoomAtmosphere.view(level,origin);var right=RoomAtmosphere.view(level,origin.east(8));
            h.assertTrue(left.geometry().boundaryFaces().size()==66&&right.geometry().boundaryFaces().size()==66,
                    "Two 4 by 3 by 3 rooms each expose 66 faces");
            var shared=new HashSet<BlockPos>(left.geometry().walls());shared.retainAll(right.geometry().walls());
            h.assertTrue(shared.size()==9,"Both sides share nine physical partition blocks");
            for(var wall:shared) {
                h.assertTrue(left.geometry().boundaryFaces().contains(new RoomAtmosphere.BoundaryFace(wall.west(),Direction.EAST)),
                        "Left partition contact points east");
                h.assertTrue(right.geometry().boundaryFaces().contains(new RoomAtmosphere.BoundaryFace(wall.east(),Direction.WEST)),
                        "Right partition contact points west");
            }
            partition(h,partition,false);
            var merged=RoomAtmosphere.view(level,origin.east(8));
            h.assertTrue(merged.geometry().cells().size()==81&&merged.geometry().boundaryFaces().size()==126,
                    "Merged 9 by 3 by 3 volume has 126 outer faces");
            h.assertTrue(merged.geometry().boundaryFaces().stream().noneMatch(face->shared.contains(face.wallCell())),
                    "Removed partition contributes no obsolete thermal contacts");
            partition(h,partition,true);
            left=RoomAtmosphere.view(level,origin);right=RoomAtmosphere.view(level,origin.east(8));
            h.assertTrue(left.geometry().boundaryFaces().size()==66&&right.geometry().boundaryFaces().size()==66,
                    "Split restores the correct contacts on both sides");
            validate(h,left.geometry());validate(h,right.geometry());
            var encoded=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
            long leftId=left.id(),rightId=right.id();RoomAtmosphere.reset();
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(encoded,level.registryAccess()));
            h.assertTrue(RoomAtmosphere.view(level,origin).id()==leftId&&RoomAtmosphere.view(level,origin.east(8)).id()==rightId,
                    "Face reconstruction retains saved room identities");
            h.assertTrue(RoomAtmosphere.view(level,origin).geometry().boundaryFaces().equals(left.geometry().boundaryFaces()),
                    "Reload reconstructs the same left boundary faces");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_boundary",timeoutTicks=100)
    public static void roomBoundaryOpenAndUnknownGeometryExposeNoPartialFaceSet(GameTestHelper h) {
        scene(h, () -> {
            var center=box(h,3,3,3);var level=h.getLevel();
            var bounded=RoomAtmosphere.inspectAirlockPartition(level,center,1);
            h.assertTrue(bounded.seal()==RoomAtmosphere.Seal.UNKNOWN&&bounded.boundaryFaces().isEmpty(),
                    "Incomplete inspection cannot advertise partial conductance faces");
            level.setBlock(center.above(3),Blocks.AIR.defaultBlockState(),2);
            var opened=RoomAtmosphere.inspect(level,center);
            h.assertTrue(opened.seal()==RoomAtmosphere.Seal.OPEN&&opened.boundaryFaces().isEmpty(),
                    "Sky-connected breach has no complete sealed-room face set");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_boundary",timeoutTicks=100)
    public static void roomBoundaryAirlockPartitionPreservesDoorContactsWhenOpen(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var origin=box(h,9,3,3);var partition=origin.east(4);
            partition(h,partition,true);var door=partition.south();
            var state=ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
            level.setBlock(door,state,2);level.setBlock(door.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
            var closed=RoomAtmosphere.inspect(level,origin);
            h.assertTrue(closed.boundaryFaces().size()==66,"Closed door is ordinary room boundary");
            for(var pos:List.of(door,door.above()))level.setBlock(pos,level.getBlockState(pos).setValue(DoorBlock.OPEN,true),2);
            h.assertTrue(level.getBlockState(door).getValue(DoorBlock.OPEN),"Fixture door really opens");
            var connected=RoomAtmosphere.inspect(level,origin);
            h.assertTrue(connected.cells().contains(door)&&connected.cells().contains(door.above()),
                    "Ordinary room geometry traverses an open door");
            h.assertTrue(connected.boundaryFaces().stream().noneMatch(face->face.wallCell().equals(door)||face.wallCell().equals(door.above())),
                    "Open door is not an ordinary air-to-wall face");
            var chamber=RoomAtmosphere.inspectAirlockPartition(level,origin,100);
            h.assertTrue(chamber.cells().size()==36&&chamber.boundaryFaces().size()==66,
                    "Controlled partition keeps the same chamber boundary even while its door opens");
            for(var pos:List.of(door,door.above()))h.assertTrue(chamber.boundaryFaces().contains(
                    new RoomAtmosphere.BoundaryFace(pos.west(),Direction.EAST)),"Partition exposes door-half boundary contact");
            validate(h,chamber);
        });
    }

    private static void validate(GameTestHelper h,RoomAtmosphere.Geometry g) {
        var wallCells=new HashSet<BlockPos>();
        for(var face:g.boundaryFaces()) {
            h.assertTrue(g.cells().contains(face.airCell()),"Face starts at an actual room air cell");
            h.assertTrue(!g.cells().contains(face.wallCell())&&g.walls().contains(face.wallCell()),"Face ends at the adjacent boundary block");
            wallCells.add(face.wallCell());
        }
        h.assertTrue(wallCells.equals(g.walls()),"Physical-wall projection is exactly the existing pressure boundary");
    }
    /** Interior starts at origin and spans the given three dimensions. */
    private static BlockPos box(GameTestHelper h,int width,int height,int depth) {
        var origin=h.absolutePos(new BlockPos(6,3,6));var level=h.getLevel();
        for(int x=-1;x<=width;x++)for(int y=-1;y<=height;y++)for(int z=-1;z<=depth;z++) {
            boolean wall=x==-1||x==width||y==-1||y==height||z==-1||z==depth;
            level.setBlock(origin.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        return origin;
    }
    private static void partition(GameTestHelper h,BlockPos start,boolean closed) {
        for(int y=0;y<3;y++)for(int z=0;z<3;z++)h.getLevel().setBlock(start.offset(0,y,z),
                (closed?Blocks.GLASS:Blocks.AIR).defaultBlockState(),3);
    }
    private static void scene(GameTestHelper h,Runnable test) {
        var level=h.getLevel();var a=ApocalypseState.get(level.getServer());long ticks=a.getApocalypseTicks();
        var identities=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
        try {
            a.setApocalypseTicks(0,level.getServer());CombustionAtmosphere.reset();RoomAtmosphere.reset();
            level.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());
            test.run();h.succeed();
        } finally {
            a.setApocalypseTicks(ticks,level.getServer());RoomAtmosphere.reset();CombustionAtmosphere.reset();
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,level.registryAccess()));
        }
    }
}
