package com.frozendawn.oxygen;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OxygenPoolsTest {
    @Test void sharedAndOverlappingSourcesCountOnceWithExactPoolDemand(){
        var sources=List.of(new OxygenPools.Source("a",8,Set.of(1L,2L)),new OxygenPools.Source("b",8,Set.of(2L,3L)));
        var burners=List.of(new OxygenPools.Burner(10,3,Set.of(1L,2L)),new OxygenPools.Burner(11,1,Set.of(3L)));
        var budget=OxygenPools.calculate(Set.of(1L,2L,3L),sources,burners,Map.of(2L,1.0,3L,5.0));
        for(var b:budget.values()){assertEquals(16,b.supply());assertEquals(10,b.demand());assertEquals(2,b.sources());assertEquals(3,b.rooms());assertEquals(1,b.pool());}
        assertEquals(3,budget.get(1L).localDemand());assertEquals(1,budget.get(2L).localDemand());assertEquals(6,budget.get(3L).localDemand());
        var reversed=new ArrayList<>(sources);Collections.reverse(reversed);assertEquals(budget,OxygenPools.calculate(Set.of(3L,2L,1L),reversed,burners,Map.of(2L,1.0,3L,5.0)));
    }
    @Test void boundaryBurnerDoesNotBridgeIndependentPoolsOrDuplicateDemand(){
        var budget=OxygenPools.calculate(Set.of(4L,2L),List.of(new OxygenPools.Source("a",8,Set.of(2L)),new OxygenPools.Source("b",8,Set.of(4L))),
                List.of(new OxygenPools.Burner(10,3,Set.of(2L,4L))),Map.of());
        assertEquals(3,budget.get(2L).demand());assertEquals(0,budget.get(4L).demand());assertEquals(8,budget.get(2L).supply());assertEquals(8,budget.get(4L).supply());assertNotEquals(budget.get(2L).pool(),budget.get(4L).pool());
    }
    @Test void removalAndReconstructionCannotRetainSupplyOrBurners(){
        var first=OxygenPools.calculate(Set.of(1L,2L),List.of(new OxygenPools.Source("a",8,Set.of(1L,2L))),List.of(new OxygenPools.Burner(10,3,Set.of(1L,2L))),Map.of(1L,1.0));
        assertEquals(4,first.get(1L).demand());
        var after=OxygenPools.calculate(Set.of(2L),List.of(),List.of(),Map.of(2L,1.0));assertEquals(0,after.get(2L).supply());assertEquals(1,after.get(2L).demand());
    }
    @Test void rejectDuplicatedPhysicalSourcesAndInvalidRates(){
        var source=new OxygenPools.Source("a",8,Set.of(1L));
        assertThrows(IllegalArgumentException.class,()->OxygenPools.calculate(Set.of(1L),List.of(source,source),List.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->OxygenPools.calculate(Set.of(1L),List.of(new OxygenPools.Source("a",Double.NaN,Set.of(1L))),List.of(),Map.of()));
        var burner=new OxygenPools.Burner(1,3,Set.of(1L));assertThrows(IllegalArgumentException.class,()->OxygenPools.calculate(Set.of(1L),List.of(),List.of(burner,burner),Map.of()));
    }
    @Test void statusDoesNotPretendThatOverloadIsLostPressure(){
        assertEquals(OxygenStatus.Mode.SUPPLIED,OxygenStatus.room(true,true,8,6));assertEquals(OxygenStatus.Mode.LOW,OxygenStatus.room(true,true,8,6.4));
        assertEquals(OxygenStatus.Mode.STRAINED,OxygenStatus.room(true,true,8,9));assertEquals(OxygenStatus.Mode.NO_AIR,OxygenStatus.room(true,false,8,1));
        assertEquals(OxygenStatus.Mode.WAITING,OxygenStatus.room(false,true,8,1));assertEquals(OxygenStatus.Mode.TRAPPED_AIR,OxygenStatus.room(true,true,0,0));
        assertNotEquals(OxygenStatus.color(OxygenStatus.Mode.STRAINED,0),OxygenStatus.color(OxygenStatus.Mode.STRAINED,15));
        assertEquals(OxygenStatus.color(OxygenStatus.Mode.SUPPLIED,0),OxygenStatus.color(OxygenStatus.Mode.SUPPLIED,15));
    }
}
