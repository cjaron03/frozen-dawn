package com.frozendawn.airlock;
import com.frozendawn.world.RoomSets;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ChamberRoomSetsTest {
    @Test void chamberReusesFrozenCellsAndDetachesMutableDoorBuilder(){
        var cells=RoomSets.freeze(Set.of(BlockPos.ZERO));var doors=new HashSet<>(Set.of(BlockPos.ZERO.east()));
        var c=new AirlockSavedState.Chamber(cells,doors,BlockPos.ZERO.west(),BlockPos.ZERO.east(2),new AirlockCycle(1,100));
        assertSame(cells,c.cells);doors.clear();assertEquals(1,c.doors.size());
        assertThrows(UnsupportedOperationException.class,()->c.doors.clear());
    }
}
