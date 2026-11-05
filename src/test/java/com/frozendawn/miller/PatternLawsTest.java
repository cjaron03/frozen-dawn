package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternLawsTest {
    @Test
    void theLawsAreMacsSix() {
        assertEquals(List.of("LAGGED", "LEGIBLE", "EXPLOITABLE", "FALLIBLE", "BOUNDED", "CAUSAL"), Arrays.stream(PatternLaws.values()).map(Enum::name).toList());
    }

    @Test
    void everyLawSaysWhatItMeans() {
        for (PatternLaws law : PatternLaws.values()) assertFalse(law.meaning().isBlank(), law.name());
    }
}
