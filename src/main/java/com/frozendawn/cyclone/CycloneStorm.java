package com.frozendawn.cyclone;

import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable track and simulation-time origin. No player reference is retained or followed. */
public record CycloneStorm(long seed, CycloneSeverity severity, double targetX, double targetZ,
        float headingRad, long startGameTime, UUID target, int calmTicks, int bombTicks,
        int arrivalTicks, int peakTicks, int dissipationTicks, int aftermathTicks, long collapseGameTime) {
    public CycloneStorm {
        if (severity==null || target==null || !Double.isFinite(targetX) || !Double.isFinite(targetZ)
                || !Float.isFinite(headingRad) || calmTicks<0 || calmTicks>7200 || bombTicks<1 || bombTicks>4800
                || arrivalTicks<1 || arrivalTicks>3600 || peakTicks<1 || peakTicks>9600
                || dissipationTicks<1 || dissipationTicks>6000 || aftermathTicks<1 || aftermathTicks>2400)
            throw new IllegalArgumentException("Invalid cyclone parameters");
    }
    public static CycloneStorm roll(long seed, CycloneSeverity severity, double x, double z,
            long now, UUID target, float bearing) {
        var r=new Random(seed); boolean cat=severity==CycloneSeverity.CATASTROPHIC;
        return new CycloneStorm(seed,severity,x,z,bearing,now,target,
                cat?r.nextInt(601):3600+r.nextInt(3601),
                cat?1200+r.nextInt(601):2400+r.nextInt(2401),
                cat?600+r.nextInt(601):2400+r.nextInt(1201),
                4800+r.nextInt(4801),3600+r.nextInt(2401),1200+r.nextInt(1201),-1);
    }
    public int duration(CycloneStage stage) {
        return switch(stage) {
            case FALSE_CALM -> calmTicks; case BOMBOGENESIS -> bombTicks; case ARRIVAL -> arrivalTicks;
            case PEAK -> peakTicks; case DISSIPATION -> dissipationTicks; case AFTERMATH -> aftermathTicks;
            case NONE -> 0;
        };
    }
    public int offset(CycloneStage stage) {
        int t=0;for(var s:CycloneStage.values()){if(s==stage)return t;t+=duration(s);}return t;
    }
    public int totalTicks() { return offset(CycloneStage.AFTERMATH)+aftermathTicks; }
    public long endGameTime() { return collapsing()?collapseGameTime+1200:startGameTime+totalTicks()+6000; }
    public boolean collapsing() { return collapseGameTime>=0; }
    public CycloneStorm withCollapse(long now) { return copy(startGameTime,now); }
    public CycloneStorm atStage(CycloneStage stage, long now) { return atStage(stage,now,0); }
    public CycloneStorm atStage(CycloneStage stage, long now, float progress) {
        return copy(now-offset(stage)-(long)(duration(stage)*Math.clamp(progress,0,.999)), -1);
    }
    private CycloneStorm copy(long start,long collapse) {
        return new CycloneStorm(seed,severity,targetX,targetZ,headingRad,start,target,calmTicks,bombTicks,
                arrivalTicks,peakTicks,dissipationTicks,aftermathTicks,collapse);
    }
    public CompoundTag save() {
        var t=new CompoundTag();t.putLong("seed",seed);t.putString("severity",severity.name());
        t.putDouble("x",targetX);t.putDouble("z",targetZ);t.putFloat("heading",headingRad);
        t.putLong("start",startGameTime);t.putUUID("target",target);
        t.putIntArray("durations",new int[]{calmTicks,bombTicks,arrivalTicks,peakTicks,dissipationTicks,aftermathTicks});
        t.putLong("collapse",collapseGameTime);return t;
    }
    public static CycloneStorm load(CompoundTag t) {
        var d=t.getIntArray("durations");if(d.length!=6)throw new IllegalArgumentException("Invalid cyclone durations");
        return new CycloneStorm(t.getLong("seed"),CycloneSeverity.valueOf(t.getString("severity")),
                t.getDouble("x"),t.getDouble("z"),t.getFloat("heading"),t.getLong("start"),t.getUUID("target"),
                d[0],d[1],d[2],d[3],d[4],d[5],t.contains("collapse")?t.getLong("collapse"):-1);
    }
}
