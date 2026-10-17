package com.frozendawn.oxygen;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OxygenAllocationTest {
    @Test void saturatedRecipientsReleaseTheirShareAndIntegerGrantsNeverCreateAir(){
        var grants=OxygenAllocation.allocate(70,Map.of("tank",40,"core",200,"suit",20,"airlock",40),0);
        assertEquals(70,grants.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(Collections.max(grants.values())-Collections.min(grants.values())<=1);
        assertEquals(Map.of("a",2,"b",48),OxygenAllocation.allocate(50,Map.of("a",2,"b",100),0));
        assertEquals(Map.of("a",0,"b",0),OxygenAllocation.allocate(0,Map.of("a",40,"b",40),0));
    }
    @Test void remainderRotatesSoSmallSurplusCannotStarveADifferentDevice(){
        var requests=Map.of("a",20,"b",20,"c",20,"d",20);var totals=new HashMap<String,Integer>();
        for(int i=0;i<400;i++)OxygenAllocation.allocate(1,requests,i).forEach((k,v)->totals.merge(k,v,Integer::sum));
        for(int v:totals.values())assertEquals(100,v);
    }
    @Test void seededAllocationMatrixConservesBudgetAndCapsEveryRequest(){
        var random=new Random(51011);
        for(int i=0;i<2000;i++){var requests=new HashMap<String,Integer>();for(int n=0;n<random.nextInt(1,21);n++)requests.put("device"+n,random.nextInt(400));
            int budget=random.nextInt(3000);var grants=OxygenAllocation.allocate(budget,requests,i);
            assertEquals(Math.min(budget,requests.values().stream().mapToInt(Integer::intValue).sum()),grants.values().stream().mapToInt(Integer::intValue).sum());
            grants.forEach((k,v)->assertTrue(v>=0&&v<=requests.get(k)));}
    }
    @Test void fractionalAnimalConsumptionIsConservativelyQuantizedAndInvalidRatesRejected(){
        assertEquals(700,OxygenAllocation.micro(.07));assertEquals(12000000,OxygenAllocation.micro(1200));
        assertTrue(OxygenAllocation.micro(.00012345)/10000.0>=.00012345);
        assertThrows(IllegalArgumentException.class,()->OxygenAllocation.micro(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->OxygenAllocation.allocate(-1,Map.of(),0));
        assertThrows(IllegalArgumentException.class,()->OxygenAllocation.allocate(1,Map.of("x",-1),0));
    }
}
