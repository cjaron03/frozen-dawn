package com.frozendawn.vision;

public final class VisionModeResolver {

    private VisionModeResolver() {
    }

    public static VisionMode resolveActiveMode(boolean thermalVisible, boolean blizzardVisible, VisionMode preferredMode) {
        if (preferredMode == VisionMode.THERMAL && thermalVisible) {
            return VisionMode.THERMAL;
        }
        if (preferredMode == VisionMode.BLIZZARD && blizzardVisible) {
            return VisionMode.BLIZZARD;
        }
        if (blizzardVisible) {
            return VisionMode.BLIZZARD;
        }
        if (thermalVisible) {
            return VisionMode.THERMAL;
        }
        return VisionMode.NONE;
    }
}
