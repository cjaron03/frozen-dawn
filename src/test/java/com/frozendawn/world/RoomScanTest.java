package com.frozendawn.world;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RoomScanTest {
    private static RoomScan.Access cube(int size){return new RoomScan.Access(){
        public boolean loaded(BlockPos p){return true;}
        public boolean sky(BlockPos p){return false;}
        public boolean passage(BlockPos p){return p.getX()>=0&&p.getY()>=0&&p.getZ()>=0&&p.getX()<size&&p.getY()<size&&p.getZ()<size;}
    };}
    @Test void finiteRoomContinuesAcrossSlicesWithoutLosingFrontier(){
        var s=new RoomScan(BlockPos.ZERO,false,32768);s.advance(cube(24),128,true);
        assertEquals(RoomScan.End.RUNNING,s.end);assertTrue(s.geometry().scanning());
        for(int i=0;i<100&&s.end==RoomScan.End.RUNNING;i++)s.advance(cube(24),256,false);
        assertEquals(RoomScan.End.SEALED,s.end);assertEquals(24*24*24,s.cells.size());
    }
    @Test void hardCapIsUnknownAndNeverExteriorEvidence(){
        var s=new RoomScan(BlockPos.ZERO,false,64);for(int i=0;i<20&&s.end==RoomScan.End.RUNNING;i++)s.advance(cube(100),8,false);
        assertEquals(RoomScan.End.LIMIT,s.end);assertEquals(RoomAtmosphere.Seal.UNKNOWN,s.geometry().seal());
        assertTrue(s.geometry().exceededBounds());assertFalse(s.geometry().scanning());
    }
    @Test void unloadedFrontierSuspendsInsteadOfBecomingReservoir(){
        var s=new RoomScan(BlockPos.ZERO,false,1024);s.advance(new RoomScan.Access(){
            public boolean loaded(BlockPos p){return p.getX()<4;}
            public boolean sky(BlockPos p){return false;}
            public boolean passage(BlockPos p){return p.getX()>=0&&p.getY()==0&&p.getZ()==0;}
        },64,false);
        assertEquals(RoomScan.End.UNLOADED,s.end);assertFalse(s.geometry().exceededBounds());
    }
    @Test void skyEvidenceCanFinishBeforeTheBudget(){
        var s=new RoomScan(BlockPos.ZERO,false,1024);s.advance(new RoomScan.Access(){
            public boolean loaded(BlockPos p){return true;}
            public boolean sky(BlockPos p){return p.getX()==10;}
            public boolean passage(BlockPos p){return p.getX()>=0&&p.getY()==0&&p.getZ()==0;}
        },64,false);assertEquals(RoomScan.End.OPEN,s.end);
    }
}
