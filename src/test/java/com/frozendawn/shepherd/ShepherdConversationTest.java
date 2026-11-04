package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShepherdConversationTest {
    private static final int ASK = 0, KIND = 1, BLUNT = 2, CONTINUE = -1;

    private static Map<String, Node> tree() throws IOException {
        try (var in = ShepherdConversationTest.class.getResourceAsStream("/data/frozendawn/shepherd/dialogue/first_meeting.json")) {
            assertNotNull(in);
            Map<String, Node> nodes = new HashMap<>();
            for (Node node : ShepherdDialogue.parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))) {
                nodes.put(node.id(), node);
            }
            return nodes;
        }
    }

    @Test
    void answerMovesToTheNextNode() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.boot");
        assertTrue(talk.answer(ASK));
        assertEquals("fm.boot.who", talk.current().id());
    }

    @Test
    void slotNotOfferedChangesNothing() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.filter");
        assertFalse(talk.answer(BLUNT), "the filter offers no Blunt reply");
        assertFalse(talk.answer(7));
        assertEquals("fm.filter", talk.current().id());
        assertEquals(0, talk.affinityDelta());
        assertTrue(talk.flags().isEmpty());
    }

    @Test
    void continueOnlyWorksOnANodeWithoutResponses() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.boot");
        assertFalse(talk.answer(CONTINUE));
        assertEquals("fm.boot", talk.current().id());
        talk.answer(ASK);
        assertFalse(talk.answer(KIND), "a reply on a node that offers none");
        assertTrue(talk.answer(CONTINUE));
        assertEquals("fm.filter", talk.current().id());
    }

    @Test
    void theLastContinueFinishes() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.end.deal");
        assertFalse(talk.finished());
        assertTrue(talk.answer(CONTINUE));
        assertTrue(talk.finished());
        assertFalse(talk.answer(CONTINUE), "nothing moves a finished conversation");
    }

    @Test
    void affinityAddsAcrossAPath() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.boot");
        talk.answer(KIND);
        talk.answer(CONTINUE);
        talk.answer(KIND);
        assertTrue(talk.flags().contains("profanity_mild"));
        talk.answer(CONTINUE);
        talk.answer(KIND);
        assertEquals("fm.name.aurora", talk.current().id());
        assertEquals(25, talk.affinityDelta());
    }
}
