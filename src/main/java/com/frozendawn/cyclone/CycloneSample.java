package com.frozendawn.cyclone;
public record CycloneSample(CycloneStage stage, float intensity, float calm, float temperatureOffset, float pressureHpa) {
    public static final CycloneSample NONE=new CycloneSample(CycloneStage.NONE,0,0,0,1000);
}
