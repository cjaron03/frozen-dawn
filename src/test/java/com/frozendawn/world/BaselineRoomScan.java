package com.frozendawn.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Frozen pre-Guava scanner reference. Only the class/snapshot type names differ from the archived source. */
final class BaselineRoomScan {
    record Geometry(RoomAtmosphere.Seal seal, Set<BlockPos> cells, Set<BlockPos> walls,
                    Set<RoomAtmosphere.BoundaryFace> boundaryFaces, boolean exceededBounds, boolean scanning) {
        Geometry { cells=Set.copyOf(cells);walls=Set.copyOf(walls);boundaryFaces=Set.copyOf(boundaryFaces); }
    }
    enum End { RUNNING, SEALED, OPEN, UNLOADED, LIMIT }
    interface Access {
        boolean loaded(BlockPos pos);
        boolean passage(BlockPos pos);
        boolean sky(BlockPos pos);
    }
    final BlockPos origin;
    final boolean partition;
    final int limit;
    final Set<BlockPos> cells=new HashSet<>(),walls=new HashSet<>();
    final Set<RoomAtmosphere.BoundaryFace> faces=new HashSet<>();
    final ArrayDeque<BlockPos> queue=new ArrayDeque<>();
    End end=End.RUNNING;
    long lastUsed;
    private Geometry snapshot;
    BaselineRoomScan(BlockPos origin,boolean partition,int limit) {
        this.origin=origin.immutable();this.partition=partition;this.limit=limit;
        cells.add(this.origin);queue.add(this.origin);
    }
    void advance(Access access,int work,boolean quick) {
        snapshot=null;
        for(int done=0;end==End.RUNNING&&!queue.isEmpty()&&done<work;done++) {
            var pos=queue.removeFirst();
            if(!access.loaded(pos)){end=End.UNLOADED;break;}
            if(access.sky(pos)){end=End.OPEN;break;}
            boolean yield=false;
            for(var dir:Direction.values()) {
                var next=pos.relative(dir);
                if(cells.contains(next))continue;
                if(walls.contains(next)){faces.add(new RoomAtmosphere.BoundaryFace(pos,dir));continue;}
                if(!access.loaded(next)){end=End.UNLOADED;break;}
                if(!access.passage(next)){walls.add(next);faces.add(new RoomAtmosphere.BoundaryFace(pos,dir));continue;}
                if(cells.size()>=limit){end=End.LIMIT;break;}
                cells.add(next);queue.addLast(next);
                if(quick&&(Math.abs(next.getX()-origin.getX())>48||Math.abs(next.getZ()-origin.getZ())>48||Math.abs(next.getY()-origin.getY())>24))yield=true;
            }
            if(yield)break;
        }
        if(end==End.RUNNING&&queue.isEmpty())end=End.SEALED;
    }
    Geometry geometry() {
        if(snapshot==null) {
            var seal=end==End.SEALED?RoomAtmosphere.Seal.SEALED:end==End.OPEN?RoomAtmosphere.Seal.OPEN:RoomAtmosphere.Seal.UNKNOWN;
            snapshot=new Geometry(seal,cells,walls,end==End.SEALED?faces:Set.of(),
                    end==End.RUNNING||end==End.LIMIT,end==End.RUNNING);
        }
        return snapshot;
    }
}
