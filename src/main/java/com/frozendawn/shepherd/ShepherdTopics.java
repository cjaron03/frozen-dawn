package com.frozendawn.shepherd;

import com.frozendawn.miller.Intent;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Response;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Nodes built on the spot, so a menu is an ordinary walk. Nothing here is saved: a topic's node sets the flag that orders it. */
public final class ShepherdTopics {
    public static final String MENU = "orsa.menu";
    /** Where a topic goes when it is done. What stands here depends on how the walk got to the topic. */
    public static final String AFTER = "orsa.after";
    public static final String HEARD = "orsa_heard_";
    private static final int PER_PAGE = 3;
    private static final String FIRST = "shepherd.orsa.menu.first", AGAIN = "shepherd.orsa.menu.again", RECORDING = "shepherd.hub.talk.wary";
    private static final String SOMETHING_ELSE = "shepherd.talk.something_else", NEVER_MIND = "shepherd.talk.never_mind";

    private ShepherdTopics() {}

    /** Up to three topics a page, unheard first, and the fourth slot goes on or leaves. At Wary a recording does the talking. */
    public static Map<String, Node> menu(List<Intent> open, Predicate<String> holds, boolean again) {
        var topics = new ArrayList<>(open);
        topics.sort(Comparator.comparing(topic -> holds.test(HEARD + topic.id())));
        var nodes = new HashMap<String, Node>();
        int pages = Math.max(1, (topics.size() + PER_PAGE - 1) / PER_PAGE);
        for (int page = 0; page < pages; page++) {
            var replies = new ArrayList<Response>();
            var slots = topics.subList(page * PER_PAGE, Math.min(topics.size(), (page + 1) * PER_PAGE));
            for (int i = 0; i < slots.size(); i++) replies.add(reply(Slot.values()[i], slots.get(i).label(), slots.get(i).node()));
            replies.add(page + 1 < pages ? reply(Slot.SARCASTIC, SOMETHING_ELSE, id(page + 1)) : reply(Slot.SARCASTIC, NEVER_MIND, null));
            nodes.put(id(page), plain(id(page), line(holds, again || page > 0), replies));
        }
        return nodes;
    }

    private static String id(int page) {
        return page == 0 ? MENU : MENU + "." + (page + 1);
    }

    private static Line line(Predicate<String> holds, boolean again) {
        if (holds.test("tier_wary")) return new Line(RECORDING, null, Mood.RECORDING, null);
        return again ? new Line(AGAIN, null, Mood.NEUTRAL, null) : new Line(FIRST, null, Mood.THINKING, null);
    }

    private static Response reply(Slot slot, String label, String next) {
        return new Response(slot, label, label, next, 0, List.of(), List.of());
    }

    private static Node plain(String id, Line line, List<Response> replies) {
        return new Node(id, List.of(line), List.copyOf(replies), null, List.of(), null, null, List.of(), null, true);
    }
}
