package com.frozendawn.event;

final class SnowshoesTuning {

    private SnowshoesTuning() {
    }

    static double getSpeedBonusForLayers(int layers) {
        if (layers >= 6) {
            return 0.20D;
        }
        if (layers >= 4) {
            return 0.15D;
        }
        if (layers >= 1) {
            return 0.10D;
        }
        return 0.0D;
    }
}
