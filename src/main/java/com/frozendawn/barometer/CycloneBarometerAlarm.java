package com.frozendawn.barometer;

import com.frozendawn.cyclone.CycloneForecast;
import com.frozendawn.cyclone.CycloneSeverity;
import net.minecraft.nbt.CompoundTag;

/** Per-instrument reminder state, driven only by the existing local forecast. */
public final class CycloneBarometerAlarm {
    public enum Cue { NONE, APPROACHING, CATASTROPHIC }
    private boolean announced;
    private long lastSeed,lastAlertTick=Long.MIN_VALUE;
    private String lastSeverity="";

    public Cue poll(CycloneForecast.Reading forecast,long seed,long now) {
        if(!forecast.visible() || forecast.etaSeconds()<=0)return Cue.NONE;
        boolean catastrophic=forecast.severity()==CycloneSeverity.CATASTROPHIC;
        long interval=catastrophic?200:600;
        if(announced && lastAlertTick!=Long.MIN_VALUE && lastSeed==seed && lastSeverity.equals(forecast.severity().name())
                && now>=lastAlertTick && now-lastAlertTick<interval)return Cue.NONE;
        announced=true;lastSeed=seed;lastAlertTick=now;lastSeverity=forecast.severity().name();
        return catastrophic?Cue.CATASTROPHIC:Cue.APPROACHING;
    }
    public void save(CompoundTag tag) {
        tag.putBoolean("CycloneAnnounced",announced);tag.putLong("LastCycloneSeed",lastSeed);
        tag.putLong("LastCycloneAlertTick",lastAlertTick);tag.putString("LastCycloneSeverity",lastSeverity);
    }
    public void load(CompoundTag tag) {
        announced=tag.getBoolean("CycloneAnnounced");lastSeed=tag.getLong("LastCycloneSeed");
        lastAlertTick=tag.contains("LastCycloneAlertTick")?tag.getLong("LastCycloneAlertTick"):Long.MIN_VALUE;
        lastSeverity=tag.getString("LastCycloneSeverity");
    }
}
