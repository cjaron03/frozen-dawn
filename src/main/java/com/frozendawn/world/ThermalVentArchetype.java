package com.frozendawn.world;

import net.minecraft.util.StringRepresentable;

public enum ThermalVentArchetype implements StringRepresentable {
    WARM("warm"),
    ACTIVE("active"),
    RUPTURE("rupture");

    private final String serializedName;

    ThermalVentArchetype(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
