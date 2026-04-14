package com.frozendawn.world;

public enum ThermalVentState {
    DORMANT,
    ACTIVE,
    WARNING,
    ERUPTING,
    SPENT;

    public boolean contributesWarmth() {
        return this == ACTIVE || this == WARNING || this == ERUPTING;
    }
}
