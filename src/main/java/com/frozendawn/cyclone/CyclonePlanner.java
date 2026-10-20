package com.frozendawn.cyclone;

import java.util.*;
public final class CyclonePlanner {
    public static final long DAY=24000, COOLDOWN=3*DAY;
    public record PlannedStorm(long startApocalypseTick,CycloneSeverity severity) {}
    private CyclonePlanner() {}
    public static List<PlannedStorm> plan(long p5,long p6,long seed,int max,boolean cat,double chance) {
        long first=p5+2*DAY,end=p6-DAY;if(end<first || max<=0)return List.of();
        var r=new Random(seed);int capacity=(int)Math.min(4,1+(end-first)/COOLDOWN);
        int count=Math.min(Math.min(max,capacity),r.nextFloat()<.3?4:3);
        var result=new ArrayList<PlannedStorm>();long prior=first-COOLDOWN;
        for(int i=0;i<count;i++) {
            long lo=i==0?first:prior+COOLDOWN,hi=end-(count-i-1)*COOLDOWN;
            if(i==0)hi=Math.min(hi,p5+4*DAY);
            long at=r.nextLong(lo,hi+1);CycloneSeverity severity=CycloneSeverity.MODERATE;
            if(i>0)severity=r.nextBoolean()?CycloneSeverity.SEVERE:CycloneSeverity.MODERATE;
            if(i>=2 && cat && r.nextDouble()<chance)severity=CycloneSeverity.CATASTROPHIC;
            result.add(new PlannedStorm(at,severity));prior=at;
        }
        return List.copyOf(result);
    }
    /** Shift the remaining schedule after a late start; preserve severity and prevent a backlog. */
    public static List<PlannedStorm> postpone(List<PlannedStorm> plan,int next,long actualStart) {
        var out=new ArrayList<>(plan);long prior=actualStart;
        for(int i=next;i<out.size();i++) {var p=out.get(i);long at=Math.max(p.startApocalypseTick(),prior+COOLDOWN);
            out.set(i,new PlannedStorm(at,p.severity()));prior=at;}
        return List.copyOf(out);
    }
}
