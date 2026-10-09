package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * §9.4b field notes on vel-thae: short Thaeven lines, verb last, no tense, no numbers.
 * Each line is authored per stored pattern; a pattern without an authored line is omitted rather than guessed.
 * Wrong beliefs are written exactly as held. The roots added for these notes are listed in docs/macs-scribe.md.
 */
final class ScribeRecordWriter {
    static final String KEY = "record.frozendawn.scribe.";
    // Quarters compound the proposed root sorr ("side, the way a thing faces") with existing roots.
    private static final Map<String, String> QUARTERS = Map.of("N", "Maeve-sorr", "E", "Vel-sorr", "S", "Vesh-sorr", "W", "Eth-sorr");
    private static final Map<String, String> DIRECTIONS = Map.of("N", "north", "E", "east", "S", "south", "W", "west");

    private ScribeRecordWriter() { }

    static List<MaeveDirector.ScribeNote> notes(List<MaeveDirector.BeliefSnapshot> beliefs) {
        // Split beliefs are written even below the floor: seen both ways is something she knows. Unseen ones never are.
        return beliefs.stream().filter(b -> b.confidence() >= ScribePolicy.FLOOR || ScribePolicy.split(b))
                .sorted(Comparator.comparingDouble(MaeveDirector.BeliefSnapshot::confidence).reversed()
                        .thenComparing(MaeveDirector.BeliefSnapshot::pattern))
                .map(ScribeRecordWriter::note).filter(Objects::nonNull).limit(ScribePolicy.MAX_NOTES).toList();
    }

    static MaeveDirector.ScribeNote note(MaeveDirector.BeliefSnapshot belief) {
        String pattern = belief.pattern(), certainty = ScribePolicy.certainty(belief);
        var exit = ExitPrediction.parse(pattern);
        if (exit != null) {
            String from = quarter(exit.from()), to = quarter(exit.to());
            return note(pattern, QUARTERS.get(from) + " aren vaen. " + QUARTERS.get(to) + " aren", "thaeven", "exit",
                    List.of(direction(from), direction(to)), certainty);
        }
        if (WorldModel.BEARINGS.contains(pattern)) {
            String to = quarter(pattern);
            return note(pattern, QUARTERS.get(to) + " aren", "thaeven", "bearing", List.of(direction(to)), certainty);
        }
        return switch (pattern) {
            case BeliefStore.SWORD -> note(pattern, "Ka", "vel-an", "sword", List.of(), certainty);
            case BeliefStore.RANGED -> note(pattern, "Eth", "orren", "ranged", List.of(), certainty);
            case BeliefStore.PURSUIT -> note(pattern, "Vesh-thae", "senn", "pursuit", List.of(), certainty);
            case BeliefStore.RECOVERY -> note(pattern, "Mor", "vel-thaeven", "recovery", List.of(), certainty);
            default -> null;
        };
    }

    /** Only marks that exist in the world model: openings, losses and heat, strongest first. */
    static List<MaeveDirector.ScribeMark> marks(List<MaeveDirector.WorldPointSnapshot> points, String dimension) {
        var result = new ArrayList<MaeveDirector.ScribeMark>();
        for (String label : List.of("ACCESS_POINT", "DANGER_ZONE", "HEAT_SOURCE")) {
            points.stream().filter(p -> p.label().equals(label) && p.dimension().equals(dimension) && held(p))
                    .sorted(Comparator.comparingDouble(MaeveDirector.WorldPointSnapshot::confidence).reversed()
                            .thenComparing(p -> p.position().asLong()))
                    .limit(ScribePolicy.MAX_MARKS_PER_LABEL)
                    .forEach(p -> result.add(new MaeveDirector.ScribeMark(label, p.position())));
        }
        return List.copyOf(result);
    }

    private static boolean held(MaeveDirector.WorldPointSnapshot point) {
        // Same floors her own executors use: open crossings resolve above 0.05, dangers count from 0.2.
        return point.label().equals("ACCESS_POINT") ? point.state().equals("OPEN") && point.inside() != null && point.confidence() > .05
                : point.confidence() >= .2;
    }

    private static MaeveDirector.ScribeNote note(String pattern, String head, String verb, String kind,
                                                 List<String> arguments, String certainty) {
        return new MaeveDirector.ScribeNote(pattern, thaeven(head, verb, certainty), KEY + kind, arguments, certainty);
    }

    /** Verb last. Certainty repeats the verb; uncertainty leaves the transmission open; a split belief is marked liss (proposed). */
    static String thaeven(String head, String verb, String certainty) {
        String line = head + " " + verb;
        return switch (certainty) {
            case "ALWAYS" -> line + ". " + Character.toUpperCase(verb.charAt(0)) + verb.substring(1) + ".";
            case "HEDGED" -> line + "…";
            case "INCONCLUSIVE" -> line + ". Liss.";
            default -> line + ".";
        };
    }

    private static String quarter(String bearing) { return bearing.substring(bearing.length() - 1); }
    private static String direction(String quarter) { return KEY + "direction." + DIRECTIONS.get(quarter); }
}
