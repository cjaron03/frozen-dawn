package com.frozendawn.cyclone;
public final class CycloneForecast {
    public record Reading(boolean visible,float pressureHpa,float trendHpaPerMin,CycloneSeverity severity,int etaSeconds,CycloneStage stage) {}
    public static final Reading NONE=new Reading(false,1000,0,CycloneSeverity.MODERATE,0,CycloneStage.NONE);
    private CycloneForecast() {}
    public static int earPopTick(CycloneStorm s) {
        return s.calmTicks()+(s.severity()==CycloneSeverity.CATASTROPHIC?0:s.bombTicks()*3/4);
    }
    public static Reading read(CycloneStorm s,double x,double z,double time) {
        double local=CycloneField.localTick(s,x,z,time);
        if(time<s.startGameTime() || time>=s.endGameTime() || s.collapsing() || !CycloneField.inTrack(s,x,z)
                || local>=s.totalTicks() || (s.severity()==CycloneSeverity.CATASTROPHIC && local<earPopTick(s)))return NONE;
        var a=CycloneField.sample(s,x,z,time);var b=CycloneField.sample(s,x,z,Math.max(s.startGameTime(),time-20));
        return new Reading(true,a.pressureHpa(),(a.pressureHpa()-b.pressureHpa())*60,s.severity(),
                Math.max(0,(int)Math.ceil((s.offset(CycloneStage.ARRIVAL)-local)/20)),a.stage());
    }
}
