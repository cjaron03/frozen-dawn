package com.frozendawn.event;

public final class SnowshoesTuning {

    private SnowshoesTuning() {
    }

    public static double getSpeedBonusForLayers(int layers) {
        if (layers >= 6) {
            return 0.18D;
        }
        if (layers >= 4) {
            return 0.15D;
        }
        if (layers >= 1) {
            return 0.12D;
        }
        return 0.0D;
    }

    public static double getSpeedBonusForSnowBlock() {
        return 0.16D;
    }

    public static double getTravelImpulseForSpeedBonus(double speedBonus) {
        if (speedBonus >= 0.18D) {
            return 0.028D;
        }
        if (speedBonus >= 0.15D) {
            return 0.023D;
        }
        if (speedBonus > 0.0D) {
            return 0.018D;
        }
        return 0.0D;
    }
}
