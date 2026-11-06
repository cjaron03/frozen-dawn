package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TypedCheckTest {
    @Test void theWordIsCaught() {
        for (String line : List.of("continuity", "CONTINUITY", "c o n t i n u i t y", "c.o.n.t.i.n.u.i.t.y", "c0ntinuity", "continuuuity", "cöntinuity", "continuty", "contiunity", "discontinuity",
                "what is continuity", "tell me about C-O-N-T-I-N-U-I-T-Y please", "c0nt1nu1ty", "contiguity", "continuit", "continuitt", "continuityx")) {
            assertTrue(TypedCheck.crashes(line), line);
        }
    }

    @Test void nearWordsAreSafe() {
        for (String line : List.of("continue", "continuing", "continuous", "continent", "can you continue it", "continued", "community", "contingency", "", "who is kevin")) {
            assertFalse(TypedCheck.crashes(line), line);
        }
    }

    @Test void guardedWordsAreWholeWords() {
        for (String line : List.of("sophie!", "who is maeve!!", "is this a game!", "minecraft!", "game1", "who is sophie", "SOPHIE MILLER", "miller?", "project tithonus", "anderson-kind", "who is maeve", "is this minecraft", "this is a game", "will i respawn", "Söphie's file", "m4eve")) {
            assertTrue(TypedCheck.guarded(line), line);
        }
        for (String line : List.of("gamely", "gamer", "the millers", "sophisticated", "who is kevin", "")) {
            assertFalse(TypedCheck.guarded(line), line);
        }
    }

    @Test void cleanCutsAt120() {
        assertEquals(120, TypedCheck.clean("a".repeat(32000)).length());
        assertEquals("who is kevin", TypedCheck.clean("  who\u0000 is\tkevin \n"));
        assertEquals("", TypedCheck.clean("   "));
    }
}
