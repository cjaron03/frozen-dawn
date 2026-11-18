package com.frozendawn.item;

import java.util.List;

/**
 * The UN Emergency Evacuation Notice's layout (CH-38): a cover, three printed pages and Kevin's page. Every word is a
 * lang key under {@link #KEY}; the screen draws them and nothing else.
 */
public final class EvacuationNotice {
    public static final String KEY = "notice.frozendawn.evacuation.";

    /** How a line is set: a printed heading, printed body text, Kevin's pen, or a paragraph gap. */
    public enum Kind { HEADING, BODY, HAND, GAP }

    public record Line(Kind kind, String key) {
        static Line heading(String key) { return new Line(Kind.HEADING, key); }
        static Line body(String key) { return new Line(Kind.BODY, key); }
        static Line hand(String key) { return new Line(Kind.HAND, key); }
        static final Line GAP = new Line(Kind.GAP, null);

        public String translationKey() { return KEY + key; }
    }

    /**
     * The cover's keys, drawn in the screen's fixed layout: the council's mark, ORSA's order and the council's
     * adoption of it, then the title, fields, the alert and the instruction.
     */
    public static final List<String> COVER = List.of("council.1", "council.2", "authority", "adopted", "title",
            "classification.label", "classification", "date.label", "date", "point.label", "point", "alert", "order");

    /** The filing strip printed at the foot of every printed page; Kevin's page has none. */
    public static final List<String> CONTROL = List.of("code", "revision");

    /** The pages after the cover, in order. */
    public static final List<List<Line>> PAGES = List.of(
            List.of(Line.heading("briefing.heading"), Line.GAP, Line.body("briefing.1"), Line.GAP,
                    Line.body("briefing.2"), Line.GAP, Line.body("briefing.3")),
            List.of(Line.heading("protocol.heading"), Line.GAP, Line.body("protocol.1"), Line.body("protocol.2"),
                    Line.body("protocol.3"), Line.body("protocol.4"), Line.body("protocol.5"), Line.GAP,
                    Line.body("protocol.exodus"), Line.body("protocol.scale")),
            List.of(Line.heading("destination.heading"), Line.GAP, Line.body("destination.1"), Line.GAP,
                    Line.body("destination.2"), Line.GAP, Line.body("destination.3"), Line.body("destination.4"),
                    Line.body("destination.5"), Line.GAP, Line.body("sign.council"), Line.body("sign.orsa")),
            List.of(Line.hand("kevin.1"), Line.GAP, Line.hand("kevin.2"), Line.GAP, Line.hand("kevin.3"), Line.GAP,
                    Line.hand("kevin.4")));

    private EvacuationNotice() {}

    public static int pageCount() {
        return 1 + PAGES.size();
    }

    /** Whether a page carries ORSA's print (watermark and filing strip): all but Kevin's. */
    public static boolean printed(int page) {
        return page < pageCount() - 1;
    }
}
