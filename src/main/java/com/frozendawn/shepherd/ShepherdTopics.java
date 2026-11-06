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
    public static final String MENU = "orsa.menu", ADVICE = "advice.menu";
    private static final String ADVICE_TOPICS = "advice";
    /** Where a topic goes when it is done. What stands here depends on how the walk got to the topic. */
    public static final String AFTER = "orsa.after";
    public static final String HEARD = "orsa_heard_";
    /** A topic can be asked again, and its answer pays the first time. A world flag, as affinity is the world's. */
    public static final String PAID = "orsa_paid_";
    private static final String TOPIC = "orsa.";
    public static final String CLOSE = "typed.close", NONE = "typed.none";
    private static final int PER_PAGE = 3, MOST_ON_A_MISS = 4;
    private static final String NEITHER = "shepherd.typed.neither";
    /** In the order she takes them, as far as her coherence reaches. */
    private static final List<Line> MISSES = List.of(new Line("shepherd.typed.miss.1", null, Mood.THINKING, null), new Line("shepherd.typed.miss.2", null, Mood.UNIMPRESSED, null),
            new Line("shepherd.typed.miss.3", null, Mood.NEUTRAL, null), new Line("shepherd.typed.miss.4", null, Mood.NEUTRAL, null), new Line("shepherd.typed.miss.5", null, Mood.THINKING, null),
            new Line("shepherd.typed.miss.6", null, Mood.UNIMPRESSED, null), new Line("shepherd.typed.miss.7", null, Mood.UNIMPRESSED, null), new Line("shepherd.typed.miss.8", null, Mood.NEUTRAL, null));
    private static final String RECORDING = "shepherd.hub.talk.wary";
    private static final String SOMETHING_ELSE = "shepherd.talk.something_else", NEVER_MIND = "shepherd.talk.never_mind";

    private ShepherdTopics() {}

    /** Up to three topics a page, in {@link #ordered} order, and the fourth slot goes on or leaves. At Wary a recording does the talking. */
    public static Map<String, Node> menu(String root, List<Intent> open, Predicate<String> holds, String focus, boolean again) {
        var topics = ordered(open, holds, focus);
        var nodes = new HashMap<String, Node>();
        int pages = Math.max(1, (topics.size() + PER_PAGE - 1) / PER_PAGE);
        for (int page = 0; page < pages; page++) {
            var replies = new ArrayList<Response>();
            var slots = topics.subList(page * PER_PAGE, Math.min(topics.size(), (page + 1) * PER_PAGE));
            for (int i = 0; i < slots.size(); i++) replies.add(reply(Slot.values()[i], slots.get(i).label(), slots.get(i).node()));
            replies.add(page + 1 < pages ? reply(Slot.SARCASTIC, SOMETHING_ELSE, id(root, page + 1)) : reply(Slot.SARCASTIC, NEVER_MIND, null));
            nodes.put(id(root, page), plain(id(root, page), line(root, holds, again || page > 0), replies));
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

    /** A dry line, and what is on her mind, in the menu's order. */
    public static Node none(int miss, List<Intent> open, Predicate<String> holds, String focus) {
        var topics = ordered(open, holds, focus);
        var replies = new ArrayList<Response>();
        for (int i = 0; i < Math.min(MOST_ON_A_MISS, topics.size()); i++) replies.add(reply(Slot.values()[i], topics.get(i).label(), topics.get(i).node()));
        return plain(NONE, MISSES.get(miss), replies);
    }

    /** A miss in a scene: the scene is what is on her mind, so it keeps its own replies, tones and all. */
    public static Node none(int miss, Node scene) {
        return new Node(NONE, List.of(MISSES.get(miss)), scene.responses(), scene.next(), List.of(), null, null, List.of(), null, scene.plain());
    }

    /** The flag a reply on this node spends. Null if it has none to spend: a scene pays each time it plays, and a menu never pays. */
    public static String paid(Node node) {
        return node.id().startsWith(TOPIC) && !node.plain() && !node.responses().isEmpty() ? PAID + node.id().substring(TOPIC.length()) : null;
    }

    /** Never mind in a topic is walking away from the question, with no tone in it. */
    public static boolean leaves(Node node, int slot) {
        return node.responses().stream().anyMatch(response -> response.slot().ordinal() == slot && NEVER_MIND.equals(response.label()));
    }

    /** Unheard first, then whatever answers her focus. The sort is stable, so the file settles the rest, and the same state gives the same order. */
    private static List<Intent> ordered(List<Intent> open, Predicate<String> holds, String focus) {
        var topics = new ArrayList<>(open);
        topics.sort(Comparator.<Intent, Boolean>comparing(topic -> holds.test(HEARD + topic.id())).thenComparing(topic -> !topic.mind().contains(focus)));
        return topics;
    }

    private static String id(String root, int page) {
        return page == 0 ? root : root + "." + (page + 1);
    }

    /** Whether this node is a page of a menu. */
    public static boolean onMenu(String node) {
        return node.startsWith(MENU) || node.startsWith(ADVICE);
    }

    /** The menu this page belongs to. */
    public static String root(String node) {
        return node.startsWith(ADVICE) ? ADVICE : MENU;
    }

    /** Which topics sit on this menu, as {@link Intent#menu} names them. */
    public static boolean sitsOn(Intent topic, String root) {
        return topic.menu().equals(ADVICE.equals(root) ? ADVICE_TOPICS : Intent.ORSA);
    }

    private static Line line(String root, Predicate<String> holds, boolean again) {
        if (holds.test("tier_wary")) return new Line(RECORDING, null, Mood.RECORDING, null);
        return again ? new Line("shepherd." + root + ".again", null, Mood.NEUTRAL, null) : new Line("shepherd." + root + ".first", null, Mood.THINKING, null);
    }

    private static Response reply(Slot slot, String label, String next) {
        return new Response(slot, label, label, next, 0, List.of(), List.of());
    }

    private static Node plain(String id, Line line, List<Response> replies) {
        return new Node(id, List.of(line), List.copyOf(replies), null, List.of(), null, null, List.of(), null, true);
    }
}
