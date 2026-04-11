package com.frozendawn.world;

import net.minecraft.core.BlockPos;

public record ThermalVentSnapshot(
        BlockPos anchorPos,
        BlockPos poolPos,
        ThermalVentArchetype archetype,
        ThermalVentState state,
        int warmthRadius,
        float warmthFloor,
        int rimRadius,
        float rimOverheatBonus,
        int eruptionRadius,
        float eruptionHeatBonus
) {
    public boolean contributesWarmth() {
        return warmthRadius > 0 && state.contributesWarmth();
    }

    public boolean isPoolLethal() {
        return state != ThermalVentState.DORMANT && state != ThermalVentState.SPENT;
    }

    public boolean isWarning() {
        return state == ThermalVentState.WARNING;
    }

    public boolean isErupting() {
        return state == ThermalVentState.ERUPTING;
    }
}
