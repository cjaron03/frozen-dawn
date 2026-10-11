package com.frozendawn.oxygen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.frozendawn.oxygen.OxygenAlarmPolicy.Cue.*;
import static com.frozendawn.oxygen.OxygenStatus.Mode.NO_AIR;
import static com.frozendawn.oxygen.OxygenStatus.Mode.NO_SEAL;
import static com.frozendawn.oxygen.OxygenStatus.Mode.SUPPLIED;
import static com.frozendawn.oxygen.OxygenStatus.Mode.LOW;
import static com.frozendawn.oxygen.OxygenStatus.Mode.TRAPPED_AIR;
import static com.frozendawn.oxygen.OxygenStatus.Mode.OPEN_AIR;
import static com.frozendawn.oxygen.OxygenStatus.Mode.WAITING;
final class OxygenAlarmPolicyTest {
    @Test void strainedIsAnEntryChimeAndPendingDoesNotSpamIt(){
        var p=new OxygenAlarmPolicy();assertEquals(STRAINED,p.update(OxygenStatus.Mode.STRAINED,false,0));
        assertEquals(NONE,p.update(WAITING,false,20));
        assertEquals(NONE,p.update(OxygenStatus.Mode.STRAINED,false,40));
        assertEquals(NONE,p.update(OxygenStatus.Mode.STRAINED,false,800));
    }
    @Test void personalSuffocationOverridesChimeAndUsesOneCriticalCadence(){
        var p=new OxygenAlarmPolicy();assertEquals(STRAINED,p.update(OxygenStatus.Mode.STRAINED,false,0));
        assertEquals(CRITICAL,p.update(NO_AIR,true,20));
        assertEquals(NONE,p.update(NO_SEAL,false,40));
        assertEquals(NONE,p.update(SUPPLIED,true,200));
        assertEquals(CRITICAL,p.update(SUPPLIED,true,220));
    }
    @Test void stableRecoveryRearmsButBriefFlickerDoesNot(){
        var p=new OxygenAlarmPolicy();assertEquals(STRAINED,p.update(OxygenStatus.Mode.STRAINED,false,0));
        p.update(SUPPLIED,false,650);assertEquals(NONE,p.update(OxygenStatus.Mode.STRAINED,false,670));
        p.update(SUPPLIED,false,700);p.update(SUPPLIED,false,740);
        assertEquals(STRAINED,p.update(OxygenStatus.Mode.STRAINED,false,760));
    }
    @Test void roomDangerWithoutPersonalSuffocationNeverSoundsKlaxon(){
        var p=new OxygenAlarmPolicy();for(var mode:new OxygenStatus.Mode[]{LOW,TRAPPED_AIR,OPEN_AIR,WAITING,NO_AIR,NO_SEAL})assertEquals(NONE,p.update(mode,false,0));
        assertEquals(CRITICAL,p.update(WAITING,true,20));
    }
}
