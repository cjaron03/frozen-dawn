package com.frozendawn.cyclone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Transient surface weather; the long-term geothermal diffusion model is unchanged. */
public final class CycloneThermal {
    public static final double CALM_FILM_RESISTANCE=.5;
    private CycloneThermal() {}
    public static float surfaceWeight(double y) {
        double f=Math.clamp((y-48)/16,0,1);
        return (float)(f*f*(3-2*f));
    }
    public static CycloneSample sampleAt(Level level,BlockPos pos) {
        float weight=surfaceWeight(pos.getY());
        if(weight==0 || !(level instanceof ServerLevel server))return CycloneSample.NONE;
        var s=CycloneController.sampleAt(server,pos.getX()+.5,pos.getZ()+.5);
        if(s.stage()==CycloneStage.NONE || weight==1)return s;
        return new CycloneSample(s.stage(),s.intensity()*weight,s.calm()*weight,s.temperatureOffset()*weight,s.pressureHpa());
    }
    public static CycloneSeverity severity(Level level) {
        return level instanceof ServerLevel server?CycloneSavedData.get(server.getServer()).active()
                .map(CycloneStorm::severity).orElse(CycloneSeverity.MODERATE):CycloneSeverity.MODERATE;
    }
    public static float windChillMultiplier(CycloneSample s,CycloneSeverity severity) {
        return 1+(severity.windChill-1)*s.intensity();
    }
    public static double windFilmConductance(double faceK,float wind) {
        if(wind<=0 || faceK<=0)return faceK;
        return .25/(.25/faceK-CALM_FILM_RESISTANCE*Math.clamp(wind,0,1));
    }
    public static float exposedHeaterRadiusFactor(CycloneSample s,CycloneSeverity severity) {
        float peak=switch(severity){case MODERATE->.5f;case SEVERE->.4f;case CATASTROPHIC->.3f;};
        return .6f+(peak-.6f)*s.intensity();
    }
    public static float exposedHeaterRadiusFactor(Level level,BlockPos pos) {
        var s=sampleAt(level,pos);return s.stage()==CycloneStage.NONE?.6f:exposedHeaterRadiusFactor(s,severity(level));
    }
    public static float windbreakFactor(Level level,BlockPos head,double windX,double windZ) {
        double length=Math.hypot(windX,windZ);if(length<1e-6)return 1;
        for(int distance=1;distance<=3;distance++) {
            var sample=BlockPos.containing(head.getX()+.5-windX/length*distance,head.getY(),head.getZ()+.5-windZ/length*distance);
            for(var pos:new BlockPos[]{sample,sample.below()})
                if(level.isLoaded(pos)&&level.getBlockState(pos).isCollisionShapeFullBlock(level,pos))return .5f;
        }
        return 1;
    }
}
