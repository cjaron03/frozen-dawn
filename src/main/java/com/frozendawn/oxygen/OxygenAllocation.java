package com.frozendawn.oxygen;
import java.util.*;
/** Max-min fair grants in existing item units; one breathing-second equals ten item units. */
public final class OxygenAllocation {
    public static final long MICRO_PER_ITEM=1000, MICRO_PER_SECOND=10000;
    private OxygenAllocation() {}
    public static long micro(double seconds){if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid oxygen");return (long)Math.ceil(seconds*MICRO_PER_SECOND-1e-8);}
    public static Map<String,Integer> allocate(int available,Map<String,Integer> requests,long turn){
        if(available<0||requests.values().stream().anyMatch(v->v<0))throw new IllegalArgumentException("Negative budget");
        var result=new TreeMap<String,Integer>();requests.forEach((k,v)->result.put(k,0));
        var active=new ArrayList<>(requests.keySet().stream().filter(k->requests.get(k)>0).sorted().toList());
        if(!active.isEmpty())Collections.rotate(active,-(int)Math.floorMod(turn,active.size()));
        while(available>0&&!active.isEmpty()){
            int share=Math.max(1,available/active.size());
            for(var id:List.copyOf(active)){
                int grant=Math.min(Math.min(share,available),requests.get(id)-result.get(id));
                result.put(id,result.get(id)+grant);available-=grant;
                if(result.get(id).equals(requests.get(id)))active.remove(id);
                if(available==0)break;
            }
        }
        return Map.copyOf(result);
    }
}
