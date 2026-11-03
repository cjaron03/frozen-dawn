package com.frozendawn.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RoomScanEquivalenceTest {
    @Test void smallRoomMatchesAtEverySliceAcrossOriginsAndInsertionOrders(){verify("small",cube(4),false);}
    @Test void lShapedRoomMatchesAtEverySliceAcrossOriginsAndInsertionOrders(){
        var cells=new ArrayList<BlockPos>();
        for(int x=0;x<9;x++)for(int y=0;y<3;y++)for(int z=0;z<9;z++)if(x<3||z<3)cells.add(new BlockPos(x,y,z));
        verify("L-shaped",cells,false);
    }
    @Test void hangarMatchesAtEverySliceAcrossOriginsAndInsertionOrders(){verify("hangar",cube(24),false);}
    @Test void breachedRoomMatchesAtEverySliceAcrossOriginsAndInsertionOrders(){
        var cells=cube(12);for(int x=12;x<=70;x++)cells.add(new BlockPos(x,5,5));verify("breached",cells,true);
    }
    private static ArrayList<BlockPos> cube(int n){
        var cells=new ArrayList<BlockPos>();for(int x=0;x<n;x++)for(int y=0;y<n;y++)for(int z=0;z<n;z++)cells.add(new BlockPos(x,y,z));return cells;
    }
    private static void verify(String name,ArrayList<BlockPos> original,boolean breached){
        var origins=List.of(original.getFirst(),original.get(original.size()/2),original.getLast());
        Set<BlockPos> terminal=null;
        for(int order=0;order<3;order++){
            var insertion=new ArrayList<>(original);if(order==1)Collections.reverse(insertion);if(order==2)Collections.shuffle(insertion,new Random(7331));
            var cells=new LinkedHashSet<>(insertion);
            RoomScan.Access access=new RoomScan.Access(){
                public boolean loaded(BlockPos p){return true;}
                public boolean passage(BlockPos p){return cells.contains(p);}
                public boolean sky(BlockPos p){return breached&&p.equals(new BlockPos(70,5,5));}
            };
            BaselineRoomScan.Access oldAccess=new BaselineRoomScan.Access(){
                public boolean loaded(BlockPos p){return access.loaded(p);}
                public boolean passage(BlockPos p){return access.passage(p);}
                public boolean sky(BlockPos p){return access.sky(p);}
            };
            for(var origin:origins){
                var old=new BaselineRoomScan(origin,false,131072);var next=new RoomScan(origin,false,131072);
                int slices=0;RoomAtmosphere.Geometry heldSnapshot=null;Set<BlockPos> heldCells=null;do{
                    int budget=slices==0?128:512;old.advance(oldAccess,budget,slices==0);next.advance(access,budget,slices==0);
                    if(heldSnapshot!=null)assertEquals(heldCells,heldSnapshot.cells(),"Earlier snapshot detached from continuing builder");
                    var before=old.geometry();var after=next.geometry();String label=name+" order="+order+" origin="+origin+" slice="+slices;
                    assertEquals(before.seal(),after.seal(),label);assertEquals(old.end.name(),next.end.name(),label);
                    assertEquals(before.cells(),after.cells(),label);assertEquals(before.walls(),after.walls(),label);assertEquals(before.boundaryFaces(),after.boundaryFaces(),label);
                    assertEquals(before.exceededBounds(),after.exceededBounds(),label);assertEquals(before.scanning(),after.scanning(),label);
                    var reversed=new LinkedHashSet<BlockPos>();var reversedCells=new ArrayList<>(after.cells());Collections.reverse(reversedCells);reversed.addAll(reversedCells);
                    var republished=new RoomAtmosphere.Geometry(after.seal(),reversed,after.walls(),after.boundaryFaces(),after.exceededBounds(),after.scanning());
                    assertEquals(before.cells(),republished.cells(),label+" reverse publish order");
                    assertSame(after,next.geometry(),label+" cached publication");
                    assertThrows(UnsupportedOperationException.class,()->after.cells().clear(),label);
                    heldSnapshot=after;heldCells=new HashSet<>(after.cells());
                    assertTrue(++slices<400,label);
                }while(next.end==RoomScan.End.RUNNING);
                if(!breached){assertEquals(cells,next.geometry().cells());if(terminal==null)terminal=next.geometry().cells();else assertEquals(terminal,next.geometry().cells());}
                else assertEquals(RoomScan.End.OPEN,next.end);
            }
        }
    }
}
