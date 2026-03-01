package com.frozendawn.config;

/**
 * Predefined configuration presets that stomp all preset-managed fields.
 *
 * Preset-managed fields (overwritten unconditionally):
 *   TOTAL_DAYS, BASE_PHASE5_TEMP, GEOTHERMAL_STRENGTH, HEAT_SOURCE_MULTIPLIER,
 *   SNOW_ACCUMULATION_RATE, BROADCAST_TICKS, SANITY_SPEED_MULTIPLIER
 */
public enum ConfigPresets {
    DEFAULT(120, -120, 1.0, 1.0, 1.0, 120000, 1.0),
    CINEMATIC(200, -80, 1.5, 1.5, 0.5, 72000, 0.5),
    BRUTAL(50, -160, 0.5, 0.5, 2.0, 192000, 2.0);

    public final int totalDays;
    public final int basePhase5Temp;
    public final double geothermalStrength;
    public final double heatSourceMultiplier;
    public final double snowAccumulationRate;
    public final int broadcastTicks;
    public final double sanitySpeedMultiplier;

    ConfigPresets(int totalDays, int basePhase5Temp, double geothermalStrength,
                  double heatSourceMultiplier, double snowAccumulationRate, int broadcastTicks,
                  double sanitySpeedMultiplier) {
        this.totalDays = totalDays;
        this.basePhase5Temp = basePhase5Temp;
        this.geothermalStrength = geothermalStrength;
        this.heatSourceMultiplier = heatSourceMultiplier;
        this.snowAccumulationRate = snowAccumulationRate;
        this.broadcastTicks = broadcastTicks;
        this.sanitySpeedMultiplier = sanitySpeedMultiplier;
    }

    /**
     * Apply this preset by stomping all preset-managed config fields.
     */
    public void apply() {
        FrozenDawnConfig.TOTAL_DAYS.set(totalDays);
        FrozenDawnConfig.BASE_PHASE5_TEMP.set(basePhase5Temp);
        FrozenDawnConfig.GEOTHERMAL_STRENGTH.set(geothermalStrength);
        FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.set(heatSourceMultiplier);
        FrozenDawnConfig.SNOW_ACCUMULATION_RATE.set(snowAccumulationRate);
        FrozenDawnConfig.BROADCAST_TICKS.set(broadcastTicks);
        FrozenDawnConfig.SANITY_SPEED_MULTIPLIER.set(sanitySpeedMultiplier);
    }
}
