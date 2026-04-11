package com.frozendawn.client;

import com.frozendawn.item.SurveyorLensScanner;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SurveyorLensProjectionMath {

    private SurveyorLensProjectionMath() {
    }

    static float ambientBaselineFromTemp(float ambientTemp) {
        float normalized = Mth.clamp((ambientTemp + 200.0F) / 260.0F, 0.0F, 1.0F);
        return Mth.lerp(normalized, 0.03F, 0.16F);
    }

    static float displayHeat(SurveyorLensScanner.HeatSignature signature) {
        float heatValue = signature.heatValue();
        return switch (signature.sourceType()) {
            case GEOTHERMAL_CORE -> {
                float actual = displayHeatForTemperature(Math.max(50.0F, heatValue));
                yield actual * 1.08F;
            }
            case THERMAL_HEATER -> displayHeatForTemperature(Math.max(35.0F, heatValue));
            case THERMAL_VENT_DORMANT -> displayHeatForTemperature(Math.max(8.0F, heatValue)) * 0.72F;
            case THERMAL_VENT_WARM -> displayHeatForTemperature(Math.max(18.0F, heatValue)) * 0.94F;
            case THERMAL_VENT_ACTIVE -> displayHeatForTemperature(Math.max(24.0F, heatValue)) * 1.02F;
            case THERMAL_VENT_RUPTURE -> displayHeatForTemperature(Math.max(32.0F, heatValue)) * 1.14F;
            case LAVA -> displayHeatForTemperature(Math.max(30.0F, heatValue));
            case ACHERON_FORGE -> displayHeatForTemperature(95.0F);
            case SOUL_CAMPFIRE -> displayHeatForTemperature(28.0F);
            case CAMPFIRE -> displayHeatForTemperature(25.0F);
            case SOUL_FIRE -> displayHeatForTemperature(22.0F);
            case FIRE -> displayHeatForTemperature(20.0F);
            case ACHERONITE_BLOCK -> displayHeatForTemperature(10.0F) * 0.90F;
            case TRANSPONDER -> 0.22F;
            case SOUL_LANTERN -> displayHeatForTemperature(18.0F) * 0.82F;
            case LANTERN -> displayHeatForTemperature(16.0F) * 0.76F;
            case SOUL_TORCH -> displayHeatForTemperature(17.0F) * 0.78F;
            case TORCH -> displayHeatForTemperature(15.0F) * 0.72F;
        };
    }

    static Vec3 heatSourceWorldPos(SurveyorLensScanner.HeatSignature signature) {
        double x = signature.pos().getX() + 0.5D;
        double z = signature.pos().getZ() + 0.5D;
        double y = switch (signature.sourceType()) {
            case GEOTHERMAL_CORE -> signature.pos().getY() + 1.01D;
            case THERMAL_VENT_DORMANT, THERMAL_VENT_WARM, THERMAL_VENT_ACTIVE, THERMAL_VENT_RUPTURE ->
                    signature.pos().getY() + 0.22D;
            case TRANSPONDER -> signature.pos().getY() + 0.08D;
            case ACHERON_FORGE -> signature.pos().getY() + 1.01D;
            case THERMAL_HEATER -> signature.pos().getY() + 1.01D;
            case ACHERONITE_BLOCK -> signature.pos().getY() + 1.01D;
            case LAVA -> signature.pos().getY() + 0.93D;
            case SOUL_FIRE, FIRE -> signature.pos().getY() + 0.08D;
            case SOUL_CAMPFIRE, CAMPFIRE -> signature.pos().getY() + 0.46D;
            case SOUL_LANTERN, LANTERN -> signature.pos().getY() + 0.08D;
            case SOUL_TORCH, TORCH -> signature.pos().getY() + 0.08D;
        };
        return new Vec3(x, y, z);
    }

    private static float displayHeatForTemperature(float heatValue) {
        if (heatValue <= 0.0F) {
            return 0.0F;
        }
        if (heatValue <= 10.0F) {
            return lerpHeatBand(heatValue, 0.0F, 10.0F, 0.08F, 0.20F);
        }
        if (heatValue <= 25.0F) {
            return lerpHeatBand(heatValue, 10.0F, 25.0F, 0.20F, 0.40F);
        }
        if (heatValue <= 35.0F) {
            return lerpHeatBand(heatValue, 25.0F, 35.0F, 0.40F, 0.58F);
        }
        if (heatValue <= 50.0F) {
            return lerpHeatBand(heatValue, 35.0F, 50.0F, 0.58F, 0.78F);
        }
        if (heatValue <= 65.0F) {
            return lerpHeatBand(heatValue, 50.0F, 65.0F, 0.78F, 0.98F);
        }
        if (heatValue <= 80.0F) {
            return lerpHeatBand(heatValue, 65.0F, 80.0F, 0.98F, 1.34F);
        }
        if (heatValue <= 100.0F) {
            return lerpHeatBand(heatValue, 80.0F, 100.0F, 1.34F, 1.58F);
        }
        return lerpHeatBand(Math.min(heatValue, 120.0F), 100.0F, 120.0F, 1.58F, 1.84F);
    }

    private static float lerpHeatBand(float heatValue, float startHeat, float endHeat, float startDisplay, float endDisplay) {
        float normalized = Mth.clamp((heatValue - startHeat) / Math.max(0.001F, endHeat - startHeat), 0.0F, 1.0F);
        float eased = normalized * normalized * (3.0F - 2.0F * normalized);
        return Mth.lerp(eased, startDisplay, endDisplay);
    }
}
