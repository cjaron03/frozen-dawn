package com.frozendawn.world;

import com.frozendawn.data.RoomThermalState;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class StructureSharesTest {
    /** Two identical worlds: one driven by the frozen map lookups, one by the cached arrays. */
    private static final class Twin {
        final RoomThermalState.MaterialMap baseline=new RoomThermalState.MaterialMap(),cached=new RoomThermalState.MaterialMap();
        final List<Set<BlockPos>> rooms=new ArrayList<>();Map<BlockPos,Integer> owners=Map.of();
        final StructureShares[] shares;
        Twin(Random random,int roomCount) {
            var union=new LinkedHashSet<BlockPos>();
            for(int r=0;r<roomCount;r++) {
                var cells=new ArrayList<BlockPos>();int x0=r*5;
                // Neighboring rooms share the x=x0..x0+2 strip, like walls between split rooms.
                for(int x=x0;x<x0+8;x++)for(int y=0;y<4;y++)for(int z=0;z<8;z++)if(random.nextInt(3)>0)cells.add(new BlockPos(x,y,z));
                Collections.shuffle(cells,random);rooms.add(RoomSets.freeze(cells));union.addAll(cells);
            }
            for(var pos:union)if(random.nextInt(8)>0)put(pos,"minecraft:glass",200+random.nextDouble()*900,random.nextDouble()*4e5);
            owners=owners(rooms);shares=new StructureShares[roomCount];
        }
        void put(BlockPos pos,String block,double capacity,double energy) {
            baseline.put(pos,new RoomThermalState.Material(block,capacity,energy));cached.put(pos,new RoomThermalState.Material(block,capacity,energy));
        }
        StructureShares view(int room){return shares[room]=StructureShares.of(shares[room],rooms.get(room),cached,owners);}
        void assertSame(String step) {
            assertEquals(baseline.keySet(),cached.keySet(),step);
            for(var entry:baseline.entrySet())assertEquals(Double.doubleToRawLongBits(entry.getValue().energy),
                    Double.doubleToRawLongBits(cached.get(entry.getKey()).energy),step+" energy at "+entry.getKey());
            for(int r=0;r<rooms.size();r++) {
                bits(BaselineStructureShares.capacity(baseline,rooms.get(r),owners),view(r).capacity(),step+" capacity of room "+r);
                bits(BaselineStructureShares.structureEnergy(baseline,rooms.get(r),owners),view(r).energy(),step+" stored energy of room "+r);
            }
        }
        void add(int room,double amount,String step) {
            bits(BaselineStructureShares.addStructure(baseline,rooms.get(room),owners,amount),view(room).add(amount),step+" transfer into room "+room);
        }
    }
    private static Map<BlockPos,Integer> owners(List<Set<BlockPos>> rooms) {
        var owners=new HashMap<BlockPos,Integer>();for(var room:rooms)for(var pos:room)owners.merge(pos,1,Integer::sum);return owners;
    }
    private static void bits(double expected,double actual,String message) {
        assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(actual),message+": "+expected+" vs "+actual);
    }
    private static void churn(Twin twin,Random random,int steps,String label) {
        for(int i=0;i<steps;i++) {
            int room=random.nextInt(twin.rooms.size());
            double amount=switch(random.nextInt(5)) {
                case 0->random.nextDouble()*5e4;
                case 1->-random.nextDouble()*5e4;
                case 2->-1e12; // drains every owned share
                case 3->0;
                default->random.nextGaussian()*300;
            };
            twin.add(room,amount,label+" step "+i);
        }
        twin.assertSame(label);
    }

    @Test void cachedSharesMatchMapLookupsBitForBitAcrossSharedWalls() {
        for(int seed=0;seed<20;seed++) {
            var random=new Random(seed);var twin=new Twin(random,3);
            twin.assertSame("seed "+seed+" initial");churn(twin,random,400,"seed "+seed);
        }
    }
    @Test void wallBrokenBetweenHeatTicksRebuildsTheView() {
        var random=new Random(41);var twin=new Twin(random,3);churn(twin,random,50,"before");
        var view=twin.view(1);assertSame(view,twin.view(1),"An unchanged room reuses its arrays");
        var wall=twin.rooms.get(1).stream().filter(twin.cached::containsKey).filter(pos->twin.owners.get(pos)>1).findFirst().orElseThrow();
        twin.baseline.remove(wall);twin.cached.remove(wall);
        assertNotSame(view,twin.view(1),"Removing a material retires the old arrays");
        twin.assertSame("after break");churn(twin,random,50,"after break");
    }
    @Test void materialSwappedMidTickIsSeenByTheNextTransfer() {
        var random=new Random(42);var twin=new Twin(random,2);churn(twin,random,20,"before");
        var wall=twin.rooms.getFirst().stream().filter(twin.cached::containsKey).findFirst().orElseThrow();
        var view=twin.view(0);
        // A replacement block arrives between two transfers of the same substep, as a power-network block update could.
        twin.put(wall,"minecraft:white_stained_glass",1234.5,777.25);
        twin.add(0,-2.5e4,"swap debit");twin.add(0,9e3,"swap credit");
        assertNotSame(view,twin.view(0),"Replacing a material retires the old arrays");
        twin.assertSame("after swap");
    }
    @Test void mergeAndSplitRecountSharedOwners() {
        var random=new Random(43);var twin=new Twin(random,3);churn(twin,random,40,"split");
        var merged=new LinkedHashSet<>(twin.rooms.get(0));merged.addAll(twin.rooms.get(1));
        var split=List.copyOf(twin.rooms);
        twin.rooms.set(0,RoomSets.freeze(merged));twin.rooms.remove(1);twin.owners=owners(twin.rooms);
        System.arraycopy(new StructureShares[]{twin.shares[0],twin.shares[2],null},0,twin.shares,0,3);
        twin.assertSame("merged");churn(twin,random,40,"merged");
        twin.rooms.clear();twin.rooms.addAll(split);twin.owners=owners(twin.rooms);
        twin.assertSame("resplit");churn(twin,random,40,"resplit");
    }
}
