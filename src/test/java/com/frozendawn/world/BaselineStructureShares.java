package com.frozendawn.world;

import com.frozendawn.data.RoomThermalState;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Frozen map-lookup reference for RoomThermalManager's structure helpers, as they were before StructureShares. */
final class BaselineStructureShares {
    private BaselineStructureShares() { }
    static double capacity(Map<BlockPos,RoomThermalState.Material> materials,Set<BlockPos> structure,Map<BlockPos,Integer> owners) {
        double result=0;for(var pos:structure){var material=materials.get(pos);if(material!=null)result+=material.capacity/owners.get(pos);}return result;
    }
    static double structureEnergy(Map<BlockPos,RoomThermalState.Material> materials,Set<BlockPos> structure,Map<BlockPos,Integer> owners) {
        double result=0;for(var pos:structure){var material=materials.get(pos);if(material!=null)result+=material.energy/owners.get(pos);}return result;
    }
    static double addStructure(Map<BlockPos,RoomThermalState.Material> materials,Set<BlockPos> structure,Map<BlockPos,Integer> owners,double amount) {
        double capacity=capacity(materials,structure,owners);if(capacity<=0)return 0;
        if(amount<0) {
            double stored=structureEnergy(materials,structure,owners),removed=0;
            double fraction=stored>0?Math.min(1,-amount/stored):0;
            for(var pos:structure){var material=materials.get(pos);if(material==null)continue;
                double debit=material.energy/owners.get(pos)*fraction;material.energy-=debit;removed+=debit;}
            return -removed;
        }
        for(var pos:structure){var material=materials.get(pos);if(material!=null)material.energy+=amount*material.capacity/owners.get(pos)/capacity;}
        return amount;
    }
}
