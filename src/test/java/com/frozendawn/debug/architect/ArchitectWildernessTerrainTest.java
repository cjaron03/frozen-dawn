package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectWildernessTerrainTest {
    @Test void surfaceCircuitClosesWithoutOverlappingOrReversing() {
        var loop=ArchitectWildernessTerrain.surfaceLoopColumns();
        assertEquals(new BlockPos(22,0,22),loop.getFirst());
        assertEquals(loop.size(),new HashSet<>(loop).size(),
                "Each column must have exactly one trail height");
        for(int i=0;i<loop.size();i++){
            var previous=loop.get(Math.floorMod(i-1,loop.size()));
            var current=loop.get(i);
            var next=loop.get((i+1)%loop.size());
            assertTrue(Math.abs(next.getX()-current.getX())<=1
                    &&Math.abs(next.getZ()-current.getZ())<=1,"No gap at any edge, including the seam");
            assertNotEquals(previous,next,"No reversing spur at the loop junction");
        }
        assertEquals(new BlockPos(22,0,23),loop.getLast());
        assertEquals(new BlockPos(23,0,22),loop.get(1));
    }
}
