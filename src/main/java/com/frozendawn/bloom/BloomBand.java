package com.frozendawn.bloom;

import net.minecraft.util.StringRepresentable;

public enum BloomBand implements StringRepresentable {
    FRONTIER("frontier"),
    MID("mid"),
    CORE("core");

    private final String serializedName;

    BloomBand(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
