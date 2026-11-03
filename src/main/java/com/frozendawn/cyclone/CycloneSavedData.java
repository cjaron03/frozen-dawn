package com.frozendawn.cyclone;

import com.frozendawn.FrozenDawn;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class CycloneSavedData extends SavedData {
    private long plannedFor=-1;
    private List<CyclonePlanner.PlannedStorm> plan=List.of();
    private int nextIndex;
    private CycloneStorm active;
    private final Set<UUID> warned=new HashSet<>();
    public static CycloneSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CycloneSavedData::new,CycloneSavedData::load,DataFixTypes.LEVEL),"frozendawn_cyclones");
    }
    public long plannedFor(){return plannedFor;}
    public List<CyclonePlanner.PlannedStorm> plan(){return plan;}
    public int nextIndex(){return nextIndex;}
    public Optional<CycloneStorm> active(){return Optional.ofNullable(active);}
    public void setPlan(long phase5,List<CyclonePlanner.PlannedStorm> plan) {
        plannedFor=phase5;this.plan=List.copyOf(plan);nextIndex=0;setDirty();
    }
    public void startPlanned(CycloneStorm storm,long apocalypseTime) {
        nextIndex++;plan=CyclonePlanner.postpone(plan,nextIndex,apocalypseTime);startForced(storm);
    }
    public void startForced(CycloneStorm storm){active=storm;warned.clear();setDirty();}
    public void replaceActive(CycloneStorm storm){active=storm;setDirty();}
    public void end(){active=null;warned.clear();setDirty();}
    public boolean warnOnce(UUID id){if(!warned.add(id))return false;setDirty();return true;}
    public boolean due(long apocalypse,long cutoff) {
        return nextIndex<plan.size() && apocalypse>=plan.get(nextIndex).startApocalypseTick() && apocalypse<cutoff
                && plan.get(nextIndex).startApocalypseTick()<cutoff;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putLong("plannedFor",plannedFor);tag.putInt("nextIndex",nextIndex);var list=new ListTag();
        for(var p:plan){var t=new CompoundTag();t.putLong("start",p.startApocalypseTick());t.putString("severity",p.severity().name());list.add(t);}
        tag.put("plan",list);if(active!=null)tag.put("active",active.save());
        var ids=new ListTag();for(var id:warned){var t=new CompoundTag();t.putUUID("id",id);ids.add(t);}tag.put("warned",ids);return tag;
    }
    public static CycloneSavedData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new CycloneSavedData();data.plannedFor=tag.contains("plannedFor")?tag.getLong("plannedFor"):-1;
        try {
            var list=tag.getList("plan",Tag.TAG_COMPOUND);var plan=new ArrayList<CyclonePlanner.PlannedStorm>();
            if(list.size()>4)throw new IllegalArgumentException("Too many planned storms");
            for(int i=0;i<list.size();i++){var t=list.getCompound(i);plan.add(new CyclonePlanner.PlannedStorm(t.getLong("start"),CycloneSeverity.valueOf(t.getString("severity"))));}
            data.plan=List.copyOf(plan);data.nextIndex=Math.clamp(tag.getInt("nextIndex"),0,plan.size());
            if(tag.contains("active"))data.active=CycloneStorm.load(tag.getCompound("active"));
            var ids=tag.getList("warned",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(ids.size(),4096);i++)data.warned.add(ids.getCompound(i).getUUID("id"));
        } catch(IllegalArgumentException ex) {
            FrozenDawn.LOGGER.warn("Invalid cyclone saved data; disabling its active storm and remaining schedule",ex);
            data.active=null;data.plan=List.of();data.nextIndex=0;
        }
        return data;
    }
}
