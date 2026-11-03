package com.frozendawn.cyclone;

/** Analytic moving front: no world, chunk, entity or block queries. */
public final class CycloneField {
    public static final double SPEED=.2, HALF_WIDTH=800, EDGE_FADE=120;
    private CycloneField() {}
    public static double along(CycloneStorm s,double x,double z) {
        return (x-s.targetX())*Math.cos(s.headingRad())+(z-s.targetZ())*Math.sin(s.headingRad());
    }
    public static double cross(CycloneStorm s,double x,double z) {
        return -(x-s.targetX())*Math.sin(s.headingRad())+(z-s.targetZ())*Math.cos(s.headingRad());
    }
    public static double localTick(CycloneStorm s,double x,double z,double time) {
        return time-s.startGameTime()-along(s,x,z)/SPEED;
    }
    public static boolean inTrack(CycloneStorm s,double x,double z) {
        return Math.abs(cross(s,x,z))<=HALF_WIDTH && along(s,x,z)>=-(s.calmTicks()+s.bombTicks())*SPEED;
    }
    public static double distanceToLeadingEdge(CycloneStorm s,double x,double z,double time) {
        return (s.offset(CycloneStage.ARRIVAL)-localTick(s,x,z,time))*SPEED;
    }
    private static float clamp(double f) { return (float)Math.clamp(f,0,1); }
    public static CycloneSample sample(CycloneStorm s,double x,double z,double time) {
        if(time<s.startGameTime() || time>=s.endGameTime() || !inTrack(s,x,z))return CycloneSample.NONE;
        double tick=localTick(s,x,z,time);
        if(tick<0 || tick>=s.totalTicks())return CycloneSample.NONE;
        CycloneStage stage=CycloneStage.NONE;double local=tick;
        for(var st:CycloneStage.values()) {if(st==CycloneStage.NONE)continue;
            if(local<s.duration(st)){stage=st;break;}local-=s.duration(st);}
        float f=clamp(local/Math.max(1,s.duration(stage))), intensity=0,calm=0,pressure=1000;
        switch(stage) {
            case FALSE_CALM -> calm=clamp(f/.2);
            case BOMBOGENESIS -> calm=1-f;
            case ARRIVAL -> intensity=f;
            case PEAK -> intensity=1;
            case DISSIPATION -> intensity=1-f;
            case AFTERMATH -> calm=clamp((1-f)/.3);
            default -> { }
        }
        if(stage==CycloneStage.BOMBOGENESIS || stage==CycloneStage.ARRIVAL)
            pressure=1000+(s.severity().pressure-1000)*clamp((tick-s.calmTicks())/(s.bombTicks()+s.arrivalTicks()));
        if(stage==CycloneStage.PEAK)pressure=s.severity().pressure;
        if(stage==CycloneStage.DISSIPATION)pressure=s.severity().pressure+(1000-s.severity().pressure)*f;
        float fade=clamp((HALF_WIDTH-Math.abs(cross(s,x,z)))/EDGE_FADE);
        fade*=clamp((s.endGameTime()-time)/1200);
        if(s.collapsing() && time>=s.collapseGameTime()) {
            if(stage!=CycloneStage.ARRIVAL && stage!=CycloneStage.PEAK && stage!=CycloneStage.DISSIPATION)
                return CycloneSample.NONE;
            stage=CycloneStage.DISSIPATION;calm=0;
            intensity=Math.min(intensity,clamp(1-(time-s.collapseGameTime())/1200));
            // The collapse envelope is already applied; don't multiply the global fade twice.
            fade=clamp((HALF_WIDTH-Math.abs(cross(s,x,z)))/EDGE_FADE);
            pressure=1000+(s.severity().pressure-1000)*intensity;
        }
        intensity*=fade;calm*=fade;pressure=1000+(pressure-1000)*fade;
        return new CycloneSample(stage,intensity,calm,calm*15+intensity*s.severity().peakOffset,pressure);
    }
}
