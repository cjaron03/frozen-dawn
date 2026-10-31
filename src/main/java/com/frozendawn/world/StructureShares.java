package com.frozendawn.world;

import com.frozendawn.data.RoomThermalState;
import java.util.*;
import net.minecraft.core.BlockPos;

/** One room's owned material shares as flat arrays. They follow the room's structure order, so every
 * sum and transfer matches the per-block map lookups bit for bit. Any change to the material map,
 * the owner counts or the structure retires the arrays before their next use. */
final class StructureShares {
    private final Set<BlockPos> structure;
    private final Map<BlockPos,Integer> owners;
    private final RoomThermalState.MaterialMap materials;
    private final long version;
    private final RoomThermalState.Material[] blocks;
    private final int[] counts;
    private final double capacity;
    private StructureShares(Set<BlockPos> structure,RoomThermalState.MaterialMap materials,Map<BlockPos,Integer> owners) {
        this.structure=structure;this.owners=owners;this.materials=materials;version=materials.version();
        var found=new ArrayList<RoomThermalState.Material>(structure.size());var shares=new int[structure.size()];
        for(var pos:structure){var material=materials.get(pos);if(material!=null){shares[found.size()]=owners.get(pos);found.add(material);}}
        blocks=found.toArray(RoomThermalState.Material[]::new);counts=Arrays.copyOf(shares,blocks.length);
        double result=0;for(int i=0;i<blocks.length;i++)result+=blocks[i].capacity/counts[i];capacity=result;
    }
    /** The cached view while still current, otherwise a fresh one. */
    static StructureShares of(StructureShares cached,Set<BlockPos> structure,RoomThermalState.MaterialMap materials,Map<BlockPos,Integer> owners) {
        return cached!=null&&cached.structure==structure&&cached.owners==owners&&cached.materials==materials&&cached.version==materials.version()
                ?cached:new StructureShares(structure,materials,owners);
    }
    double capacity(){return capacity;}
    double energy() {
        double result=0;for(int i=0;i<blocks.length;i++)result+=blocks[i].energy/counts[i];return result;
    }
    /** Distribute into the owned capacity shares, preserving a single physical reservoir and preventing negative energy. */
    double add(double amount) {
        if(capacity<=0)return 0;
        if(amount<0) {
            // An empty/cold block cannot stop the rest of the structure releasing stored heat.
            // Debit each owned energy share, so shared physical blocks are never overdrawn.
            double stored=energy(),removed=0;
            double fraction=stored>0?Math.min(1,-amount/stored):0;
            for(int i=0;i<blocks.length;i++){double debit=blocks[i].energy/counts[i]*fraction;blocks[i].energy-=debit;removed+=debit;}
            return -removed;
        }
        for(int i=0;i<blocks.length;i++)blocks[i].energy+=amount*blocks[i].capacity/counts[i]/capacity;
        return amount;
    }
}
