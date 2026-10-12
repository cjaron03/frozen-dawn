package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomIdentityGameTest {
    @BeforeBatch(batch="room_identity")
    public static void report(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityMergeRetiresAllDuplicateRoomsAndBreachRunsOnce(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel(); var center=room(h,true); var left=center.west(2); var right=center.east(2);
            long a=RoomAtmosphere.view(level,left).id(),b=RoomAtmosphere.view(level,right).id();
            h.assertTrue(a!=b,"Closed partition has distinct room identities");
            level.setBlock(center,Blocks.AIR.defaultBlockState(),3);
            var merged=RoomAtmosphere.view(level,right);
            h.assertTrue(merged.id()==Math.min(a,b),"Equal merge overlap has a deterministic survivor");
            h.assertTrue(RoomAtmosphere.view(level,left).id()==merged.id(),"Every query resolves the same merged identity");
            h.assertTrue(RoomAtmosphere.trackedRooms(level).size()==1,"Retired parents leave no duplicate live room");
            h.assertTrue(RoomAtmosphere.hasAir(level,left),"Merge retains pressure");
            level.setBlock(center.east(5),Blocks.AIR.defaultBlockState(),3);
            RoomAtmosphere.tickLevel(level);
            h.assertTrue(AtmosphericBreach.activeBursts(level)==1,"Merged shelter emits exactly one breach");
            h.assertTrue(RoomAtmosphere.trackedRooms(level).isEmpty(),"Breach removes the live sealed volume");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentitySplitRebuildsAllChildrenWithoutRefillingDepletedAir(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel(); var center=room(h,false);
            var original=RoomAtmosphere.view(level,center);
            RoomAirState.get(level).evacuate(original.geometry().cells());
            partition(h,center,true);
            var right=RoomAtmosphere.view(level,center.east(2));
            var left=RoomAtmosphere.view(level,center.west(2));
            h.assertTrue(RoomAtmosphere.trackedRooms(level).size()==2,"Both split children are rebuilt immediately");
            h.assertTrue(left.id()!=right.id(),"Siblings never share an identity");
            h.assertTrue(left.id()==original.id(),"Equal split tie follows minimum packed cell, not first query");
            h.assertTrue(Collections.disjoint(left.geometry().cells(),right.geometry().cells()),"Live child membership is disjoint");
            h.assertFalse(RoomAtmosphere.hasAir(level,center.west(2))||RoomAtmosphere.hasAir(level,center.east(2)),
                    "Splitting depleted air creates no oxygen");
            long leftId=left.id(),rightId=right.id();
            RoomAtmosphere.reset();
            h.assertTrue(RoomAtmosphere.view(level,center.east(2)).id()==rightId,"Right-first cache rebuild preserves split identity");
            h.assertTrue(RoomAtmosphere.view(level,center.west(2)).id()==leftId,"Left cache rebuild preserves split identity");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityRemovedOriginAndReloadUseMembershipOverlap(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var center=room(h,false);
            long original=RoomAtmosphere.view(level,center).id();
            // Simulate an edit made while the cache/server is absent, including removal of its query anchor.
            var state=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
            RoomAtmosphere.reset();
            level.setBlock(center,Blocks.STONE.defaultBlockState(),3);
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(state,level.registryAccess()));
            h.assertTrue(RoomAtmosphere.view(level,center)==null,"A filled origin is not an air cell");
            var rebuilt=RoomAtmosphere.view(level,center.east());
            h.assertTrue(rebuilt.id()==original,"Surviving membership preserves identity after actual NBT reload");
            h.assertFalse(rebuilt.geometry().cells().contains(center),"Removed anchor is excluded from rebuilt membership");
            RoomAtmosphere.tickLevel(level);
            h.assertTrue(RoomAtmosphere.view(level,center.west()).id()==original,"Periodic refresh does not depend on old origin");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityReloadReconcilesOfflineSplitAndMergeDeterministically(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var center=room(h,false);
            long parent=RoomAtmosphere.view(level,center).id();
            var encoded=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
            RoomAtmosphere.reset();partition(h,center,true);
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(encoded,level.registryAccess()));
            long right=RoomAtmosphere.view(level,center.east(2)).id();
            long left=RoomAtmosphere.view(level,center.west(2)).id();
            h.assertTrue(left==parent&&right!=parent,"Offline split resolves all siblings before the right-first query");
            encoded=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
            RoomAtmosphere.reset();partition(h,center,false);
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(encoded,level.registryAccess()));
            long merged=RoomAtmosphere.view(level,center.east(2)).id();
            h.assertTrue(merged==Math.min(left,right),"Offline merge chooses the same canonical survivor");
            h.assertTrue(RoomAtmosphere.view(level,center.west(2)).id()==merged&&RoomAtmosphere.trackedRooms(level).size()==1,
                    "Reloaded merge leaves one live room across every member");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityBreachResealAndAirtightReplacementPreserveIdentity(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var center=room(h,false);var wall=center.east(5);
            long id=RoomAtmosphere.view(level,center).id();
            level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(level,center).id()==id,"Airtight material replacement retains identity");
            level.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(level,center)==null,"Breach invalidates pressure immediately");
            level.setBlock(wall,Blocks.GLASS.defaultBlockState(),3);
            RoomAtmosphere.reset();
            h.assertTrue(RoomAtmosphere.view(level,center).id()==id,"Reseal after cache reset recovers dormant membership");
            h.assertFalse(RoomAtmosphere.hasAir(level,center),"Reseal identity does not restore lost air");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityUnknownTopologySuspendsAirWithoutDiscardingMembership(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var center=room(h,false);
            long id=RoomAtmosphere.view(level,center).id();
            // A closed tall extension exceeds the existing 24-block inspection bound.
            for(int y=3;y<=35;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                boolean wall=Math.abs(x)==1||Math.abs(z)==1||y==35;
                level.setBlock(center.offset(x,y,z),(wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            }
            h.assertTrue(RoomAtmosphere.inspect(level,center).seal()==RoomAtmosphere.Seal.UNKNOWN,"Fixture exceeds geometry bound");
            h.assertTrue(RoomAtmosphere.view(level,center)==null,"Unknown geometry suspends canonical queries");
            h.assertFalse(RoomAtmosphere.hasAir(level,center),"Suspended room cannot serve stale pressure on subsequent queries");
            h.assertTrue(RoomAtmosphere.trackedRooms(level).isEmpty(),"Suspended cache records are not advertised as live volumes");
            h.assertTrue(RoomIdentityState.get(level).at(center).id()==id,"Uncertain topology preserves saved membership");
            level.setBlock(center.above(3),Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(RoomAtmosphere.view(level,center).id()==id,"Repair recovers the prior canonical identity");
            h.assertTrue(RoomAtmosphere.hasAir(level,center),"Uncertainty does not fabricate a breach or destroy trapped air");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_identity",timeoutTicks=100)
    public static void roomIdentityLargestSplitChildKeepsParentEvenWhenSmallChildQueriedFirst(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var center=room(h,false);
            long id=RoomAtmosphere.view(level,center).id();
            partition(h,center.west(2),true);
            var small=RoomAtmosphere.view(level,center.west(3));
            var large=RoomAtmosphere.view(level,center.east(2));
            h.assertTrue(small.geometry().cells().size()<large.geometry().cells().size(),"Fixture has unequal split children");
            h.assertTrue(large.id()==id&&small.id()!=id,"Largest surviving overlap retains parent regardless of query order");
        });
    }

    private static BlockPos room(GameTestHelper h,boolean split) {
        var center=h.absolutePos(new BlockPos(10,3,10));var level=h.getLevel();
        for(int x=-5;x<=5;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++) {
            boolean wall=Math.abs(x)==5||Math.abs(z)==2||y==-1||y==3;
            level.setBlock(center.offset(x,y,z),(wall?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
        }
        if(split)partition(h,center,true);
        return center;
    }
    private static void partition(GameTestHelper h,BlockPos center,boolean closed) {
        for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)
            h.getLevel().setBlock(center.offset(0,y,z),(closed?Blocks.GLASS:Blocks.AIR).defaultBlockState(),3);
    }
    private static void scene(GameTestHelper h,Runnable test) {
        var level=h.getLevel();var a=ApocalypseState.get(level.getServer());long ticks=a.getApocalypseTicks();
        var identities=RoomIdentityState.get(level).save(new CompoundTag(),level.registryAccess());
        try {
            a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),level.getServer());
            RoomAtmosphere.reset();AtmosphericBreach.reset();CombustionAtmosphere.reset();
            level.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());
            test.run();h.succeed();
        } finally {
            a.setApocalypseTicks(ticks,level.getServer());RoomAtmosphere.reset();AtmosphericBreach.reset();CombustionAtmosphere.reset();
            level.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identities,level.registryAccess()));
        }
    }
}
