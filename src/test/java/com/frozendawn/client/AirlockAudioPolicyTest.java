package com.frozendawn.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirlockAudioPolicyTest {
    @Test void pumpAndCompletionFeedbackStayLocalInVacuum() {
        for (String cue : new String[]{"pump_fill", "pump_recover", "ready", "evacuated"}) {
            assertTrue(AirlockAudioPolicy.isMachinery("frozendawn", "block.airlock." + cue));
        }
        assertTrue(AirlockAudioPolicy.vacuumGain(9) > 0);
        assertEquals(0, AirlockAudioPolicy.vacuumGain(16.01));
        assertEquals(0, AirlockAudioPolicy.vacuumGain(Double.NaN));
        assertFalse(AirlockAudioPolicy.isMachinery("minecraft", "block.airlock.ready"));
        assertFalse(AirlockAudioPolicy.isMachinery("frozendawn", "block.geothermal_core.hum"));
    }
}
