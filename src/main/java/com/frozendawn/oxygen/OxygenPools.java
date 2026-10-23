package com.frozendawn.oxygen;
import java.util.*;
/** Rate accounting only. Sources and burners are physical identities, never per-room copies. */
public final class OxygenPools {
    private OxygenPools() {}
    public record Source(String id,double capacity,Set<Long> rooms) {public Source {rooms=Set.copyOf(rooms);}}
    public record Burner(long id,double demand,Set<Long> rooms) {public Burner {rooms=Set.copyOf(rooms);}}
    public record Budget(long pool,double supply,double demand,double localDemand,int sources,int rooms) {}
    public static Map<Long,Budget> calculate(Set<Long> rooms,List<Source> sources,List<Burner> burners,Map<Long,Double> breathing) {
        var parent=new TreeMap<Long,Long>();for(long room:rooms)parent.put(room,room);
        var unique=new TreeMap<String,Source>();
        for(var source:sources) {if(!Double.isFinite(source.capacity)||source.capacity<0)throw new IllegalArgumentException("Invalid source rate");
            if(unique.putIfAbsent(source.id,source)!=null)throw new IllegalArgumentException("Duplicate source");
            var served=source.rooms.stream().filter(rooms::contains).sorted().toList();
            if(!served.isEmpty())for(long room:served)union(parent,served.getFirst(),room);
        }
        var supply=new HashMap<Long,Double>();var counts=new HashMap<Long,Integer>();var size=new HashMap<Long,Integer>();var local=new HashMap<Long,Double>();
        for(long room:parent.keySet()) {long pool=find(parent,room);size.merge(pool,1,Integer::sum);double rate=breathing.getOrDefault(room,0.0);
            if(!Double.isFinite(rate)||rate<0)throw new IllegalArgumentException("Invalid breathing rate");local.put(room,rate);}
        for(var source:unique.values()) {var served=source.rooms.stream().filter(rooms::contains).min(Long::compare);
            if(served.isPresent()){long pool=find(parent,served.get());supply.merge(pool,source.capacity,Double::sum);counts.merge(pool,1,Integer::sum);}}
        var uniqueBurners=new TreeMap<Long,Burner>();
        for(var burner:burners){if(!Double.isFinite(burner.demand)||burner.demand<0)throw new IllegalArgumentException("Invalid burner rate");
            if(uniqueBurners.putIfAbsent(burner.id,burner)!=null)throw new IllegalArgumentException("Duplicate burner");}
        // A boundary intake shared by independent pools belongs to the lowest canonical room ID.
        // It cannot bridge otherwise independent oxygen supplies or be charged twice.
        for(var burner:uniqueBurners.values())burner.rooms.stream().filter(rooms::contains).min(Long::compare)
                .ifPresent(room->local.merge(room,burner.demand,Double::sum));
        var demand=new HashMap<Long,Double>();for(long room:parent.keySet())demand.merge(find(parent,room),local.get(room),Double::sum);
        var result=new TreeMap<Long,Budget>();for(long room:parent.keySet()){long pool=find(parent,room);
            result.put(room,new Budget(pool,supply.getOrDefault(pool,0.0),demand.getOrDefault(pool,0.0),local.get(room),counts.getOrDefault(pool,0),size.get(pool)));}
        return Map.copyOf(result);
    }
    private static long find(Map<Long,Long> parent,long id){long next=parent.get(id);if(next!=id){next=find(parent,next);parent.put(id,next);}return next;}
    private static void union(Map<Long,Long> parent,long a,long b){a=find(parent,a);b=find(parent,b);if(a!=b)parent.put(Math.max(a,b),Math.min(a,b));}
}
