package com.frozendawn.event;

public final class SnowshoesTuning {

    private SnowshoesTuning() {
    }

    public static double getSpeedBonusForLayers(int layers) {
        if (layers >= 6) {
            return 0.14D;
        }
        if (layers >= 4) {
            return 0.12D;
        }
        if (layers >= 1) {
            return 0.10D;
        }
        return 0.0D;
    }

    public static double getSpeedBonusForSnowBlock() {
        return 0.12D;
    }

    public static double getTravelImpulseForSpeedBonus(double speedBonus) {
        if (speedBonus >= 0.14D) {
            return 0.019D;
        }
        if (speedBonus >= 0.12D) {
            return 0.016D;
        }
        if (speedBonus > 0.0D) {
            return 0.012D;
        }
        return 0.0D;
    }
}
