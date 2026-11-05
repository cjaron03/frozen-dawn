package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ShepherdConversationTest {
    private static final int ASK = 0, KIND = 1, BLUNT = 2, CONTINUE = -1;
    private static final Predicate<String> NOTHING = condition -> false;

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
        var talk = new ShepherdConversation(tree(), "fm.boot", NOTHING);
        assertTrue(talk.answer(ASK));
        assertEquals("fm.boot.who", talk.current().id());
    }

    @Test
    void slotNotOfferedChangesNothing() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.filter", NOTHING);
        assertFalse(talk.answer(BLUNT), "the filter offers no Blunt reply");
        assertFalse(talk.answer(7));
        assertEquals("fm.filter", talk.current().id());
        assertEquals(0, talk.affinityDelta());
        assertTrue(talk.flags().isEmpty());
    }

    @Test
    void continueOnlyWorksOnANodeWithoutResponses() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.boot", NOTHING);
        assertFalse(talk.answer(CONTINUE));
        assertEquals("fm.boot", talk.current().id());
        talk.answer(ASK);
        assertFalse(talk.answer(KIND), "a reply on a node that offers none");
        assertTrue(talk.answer(CONTINUE));
        assertEquals("fm.filter", talk.current().id());
    }

    @Test
    void theLastContinueFinishes() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.end.deal", NOTHING);
        assertFalse(talk.finished());
        assertTrue(talk.answer(CONTINUE));
        assertTrue(talk.finished());
        assertFalse(talk.answer(CONTINUE), "nothing moves a finished conversation");
    }

    @Test
    void affinityAddsAcrossAPath() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.boot", NOTHING);
        talk.answer(KIND);
        talk.answer(CONTINUE);
        talk.answer(KIND);
        assertTrue(talk.flags().contains("profanity_mild"));
        talk.answer(CONTINUE);
        talk.answer(KIND);
        assertEquals("fm.name.aurora", talk.current().id());
        assertEquals(25, talk.affinityDelta());
    }

    private static Node step(String id, String next, String when, String otherwise, String... set) {
        return new Node(id, List.of(new Line("x", null, Mood.NEUTRAL, null)), List.of(), next, List.of(set), when, otherwise);
    }

    @Test
    void aNodeThatDoesNotHoldGivesWayToItsElse() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.name.earn", NOTHING);
        talk.answer(CONTINUE);
        assertEquals("fm.end", talk.current().id());
    }

    @Test
    void aNodeThatHoldsIsEntered() throws IOException {
        var talk = new ShepherdConversation(tree(), "fm.name.earn", "woke_in_vault"::equals);
        talk.answer(CONTINUE);
        assertEquals("fm.room", talk.current().id());
    }

    @Test
    void aStartThatDoesNotHoldGivesWayToo() throws IOException {
        assertEquals("fm.end", new ShepherdConversation(tree(), "fm.room", NOTHING).current().id());
    }

    @Test
    void lineConditionsPickOneLogLine() throws IOException {
        for (String wand : List.of("cradle", "hand", "other")) {
            Set<String> holding = Set.of("woke_in_vault", "wand_" + wand);
            var lines = new ShepherdConversation(tree(), "fm.room", holding::contains).lines();
            assertEquals(2, lines.size(), wand);
            assertEquals("wand_" + wand, lines.get(1).condition());
        }
    }

    @Test
    void anElseIsNotEarned() {
        var nodes = Map.of("a", step("a", "b", null, null), "b", step("b", null, "never", "c", "earned"), "c", step("c", null, null, null));
        var talk = new ShepherdConversation(nodes, "a", NOTHING);
        talk.answer(CONTINUE);
        assertEquals("c", talk.current().id());
        assertFalse(talk.flags().contains("earned"));
    }

    @Test
    void aLoopOfElsesEndsTheTalk() {
        var nodes = Map.of("a", step("a", "b", null, null), "b", step("b", null, "never", "c"), "c", step("c", null, "never", "b"));
        var talk = new ShepherdConversation(nodes, "a", NOTHING);
        assertTrue(talk.answer(CONTINUE));
        assertTrue(talk.finished(), "an edited datapack must not hang the server");
    }
}
