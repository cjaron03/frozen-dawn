package com.frozendawn.barometer;

import com.frozendawn.cyclone.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycloneBarometerAlarmTest {
    static CycloneForecast.Reading approaching(CycloneSeverity severity) {
        return new CycloneForecast.Reading(true,990,-10,severity,120,CycloneStage.BOMBOGENESIS);
    }
    @Test void normalAndCatastrophicRemindersHaveDifferentCadenceAndDoNotSpam() {
        for(var severity:CycloneSeverity.values()) {
            var alarm=new CycloneBarometerAlarm();var forecast=approaching(severity);
            var expected=severity==CycloneSeverity.CATASTROPHIC?CycloneBarometerAlarm.Cue.CATASTROPHIC:CycloneBarometerAlarm.Cue.APPROACHING;
            int interval=severity==CycloneSeverity.CATASTROPHIC?200:600;
            assertEquals(expected,alarm.poll(forecast,42,1000));
            for(int tick=1000;tick<1000+interval;tick++)assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(forecast,42,tick));
            assertEquals(expected,alarm.poll(forecast,42,1000+interval));
        }
    }
    @Test void exactCooldownSurvivesNbtAndNewStormOrEscalationWarnsImmediately() {
        var alarm=new CycloneBarometerAlarm();alarm.poll(approaching(CycloneSeverity.MODERATE),42,1000);
        var tag=new CompoundTag();alarm.save(tag);var restored=new CycloneBarometerAlarm();restored.load(tag);
        assertEquals(CycloneBarometerAlarm.Cue.NONE,restored.poll(approaching(CycloneSeverity.MODERATE),42,1599));
        assertEquals(CycloneBarometerAlarm.Cue.CATASTROPHIC,restored.poll(approaching(CycloneSeverity.CATASTROPHIC),42,1599));
        assertEquals(CycloneBarometerAlarm.Cue.APPROACHING,restored.poll(approaching(CycloneSeverity.MODERATE),43,1600));
        var saved=new CompoundTag();restored.save(saved);var copy=new CycloneBarometerAlarm();copy.load(saved);
        assertEquals(CycloneBarometerAlarm.Cue.APPROACHING,copy.poll(approaching(CycloneSeverity.MODERATE),43,2200));
    }
    @Test void noWarningOutsideTrackBeforeCatastrophicDetectionOrAfterArrival() {
        var s=new CycloneStorm(42,CycloneSeverity.CATASTROPHIC,0,0,0,100,UUID.randomUUID(),200,1200,800,4800,3600,1200,-1);
        var alarm=new CycloneBarometerAlarm();
        assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(CycloneForecast.NONE,s.seed(),100));
        assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(CycloneForecast.read(s,0,0,299),s.seed(),299));
        assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(CycloneForecast.read(s,0,801,300),s.seed(),300));
        assertEquals(CycloneBarometerAlarm.Cue.CATASTROPHIC,alarm.poll(CycloneForecast.read(s,0,0,300),s.seed(),300));
        assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(CycloneForecast.read(s,0,0,1500),s.seed(),1500));
    }
    @Test void legacyOneShotStateUpgradesAndClockRewindCannotLockOutWarnings() {
        var tag=new CompoundTag();tag.putBoolean("CycloneAnnounced",true);tag.putLong("LastCycloneSeed",42);
        var alarm=new CycloneBarometerAlarm();alarm.load(tag);
        assertEquals(CycloneBarometerAlarm.Cue.APPROACHING,alarm.poll(approaching(CycloneSeverity.MODERATE),42,1000));
        assertEquals(CycloneBarometerAlarm.Cue.APPROACHING,alarm.poll(approaching(CycloneSeverity.MODERATE),42,900));
        assertEquals(CycloneBarometerAlarm.Cue.NONE,alarm.poll(approaching(CycloneSeverity.MODERATE),42,901));
    }
}
