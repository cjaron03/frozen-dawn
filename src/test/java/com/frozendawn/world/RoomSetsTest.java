package com.frozendawn.world;

import com.google.common.collect.ImmutableSet;
import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.data.RoomThermalState;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoomSetsTest {
    @Test void freezeOwnsItsStorageRejectsNullsAndReusesOnlyImmutableInput(){
        var builder=new HashSet<>(Set.of(BlockPos.ZERO));var frozen=RoomSets.freeze(builder);
        builder.add(BlockPos.ZERO.east());assertEquals(Set.of(BlockPos.ZERO),frozen);
        assertThrows(UnsupportedOperationException.class,()->frozen.remove(BlockPos.ZERO));
        assertThrows(NullPointerException.class,()->RoomSets.freeze(Arrays.asList(BlockPos.ZERO,null)));
        assertSame(frozen,RoomSets.freeze(frozen));assertSame(ImmutableSet.of(),RoomSets.freeze(List.of()));
    }
    @Test void standardSetEqualityAndMapKeysRemainCompatibleInBothDirections(){
        var positions=List.of(BlockPos.ZERO,BlockPos.ZERO.east(),BlockPos.ZERO.above());
        var old=Set.copyOf(positions);var next=RoomSets.freeze(positions);
        assertEquals(old,next);assertEquals(next,old);assertEquals(old.hashCode(),next.hashCode());
        var oldMap=new HashMap<Set<BlockPos>,String>();oldMap.put(old,"same");assertEquals("same",oldMap.get(next));
        var nextMap=new HashMap<Set<BlockPos>,String>();nextMap.put(next,"same");assertEquals("same",nextMap.get(old));
    }
    @Test void geometryIdentityAndThermalFreezeShareTheImmutableMembership(){
        var input=new HashSet<>(Set.of(BlockPos.ZERO,BlockPos.ZERO.east()));
        var geometry=new RoomAtmosphere.Geometry(RoomAtmosphere.Seal.SEALED,input,Set.of(),Set.of());
        var identity=new RoomIdentityState.Membership(1,geometry.cells());var thermal=new RoomThermalState.Room(1,geometry.cells(),geometry.boundaryFaces());
        assertInstanceOf(ImmutableSet.class,geometry.cells());assertSame(geometry.cells(),identity.cells());assertSame(geometry.cells(),thermal.cells);
        input.clear();assertEquals(2,geometry.cells().size());
    }
}
