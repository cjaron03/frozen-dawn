package com.frozendawn.shepherd;

import com.frozendawn.miller.Intent;
import com.frozendawn.miller.IntentIndex;
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
    public static final String CLOSE = "typed.close", NONE = "typed.none";
    private static final int PER_PAGE = 3, MOST_ON_A_MISS = 4;
    private static final String NEITHER = "shepherd.typed.neither";
    /** In the order she takes them, as far as her coherence reaches. */
    private static final List<Line> MISSES = List.of(new Line("shepherd.typed.miss.1", null, Mood.THINKING, null), new Line("shepherd.typed.miss.2", null, Mood.UNIMPRESSED, null),
            new Line("shepherd.typed.miss.3", null, Mood.NEUTRAL, null));
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

    /** Her check. A locked or unlabelled guess never gets this far: {@code match} is already what may be offered. */
    public static Node close(IntentIndex.Match match) {
        var replies = new ArrayList<Response>();
        replies.add(reply(Slot.ASK, match.first().label(), match.first().node()));
        if (match.second() != null) replies.add(reply(Slot.KIND, match.second().label(), match.second().node()));
        replies.add(reply(Slot.SARCASTIC, NEITHER, NONE));
        return plain(CLOSE, new Line(match.first().guess(), null, Mood.THINKING, null), replies);
    }

    public static int misses() {
        return MISSES.size();
    }

    /** A dry line, and what is on her mind: unheard first. */
    public static Node none(int miss, List<Intent> open, Predicate<String> holds) {
        var topics = new ArrayList<>(open);
        topics.sort(Comparator.comparing(topic -> holds.test(HEARD + topic.id())));
        var replies = new ArrayList<Response>();
        for (int i = 0; i < Math.min(MOST_ON_A_MISS, topics.size()); i++) replies.add(reply(Slot.values()[i], topics.get(i).label(), topics.get(i).node()));
        return plain(NONE, MISSES.get(miss), replies);
    }

    /** A miss in a scene: the scene is what is on her mind, so it keeps its own replies, tones and all. */
    public static Node none(int miss, Node scene) {
        return new Node(NONE, List.of(MISSES.get(miss)), scene.responses(), scene.next(), List.of(), null, null, List.of(), null, scene.plain());
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
