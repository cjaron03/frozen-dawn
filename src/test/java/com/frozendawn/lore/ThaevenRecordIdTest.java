package com.frozendawn.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaevenRecordIdTest {
    @Test
    void commandNamesAcceptCanonicalIdsAndCommonSeparators() {
        assertEquals(ThaevenRecordId.THE_FIRST_CROSSING,
                ThaevenRecordId.parse("the_first_crossing").orElseThrow());
        assertEquals(ThaevenRecordId.THE_FIRST_CROSSING,
                ThaevenRecordId.parse("the-first-crossing").orElseThrow());
        assertEquals(ThaevenRecordId.THE_FIRST_CROSSING,
                ThaevenRecordId.parse("THE FIRST CROSSING").orElseThrow());
        assertTrue(ThaevenRecordId.parse("not-a-record").isEmpty());
    }
}
