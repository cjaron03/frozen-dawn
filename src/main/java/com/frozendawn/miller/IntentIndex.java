package com.frozendawn.miller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** The nearest intent to a line: its best cosine to any example. Every intent ranks, open or not, since M0's thresholds were tuned with all of them present. */
public final class IntentIndex {
    public static final double STRONG = 0.56, LEAD = 0.10, CLOSE = 0.425;
    private static final Match MISS = new Match(Band.NONE, null, null);

    public enum Band {
        STRONG,
        CLOSE,
        NONE
    }

    /** {@code first} is what she answers or guesses, {@code second} a further guess: null where the band has none. */
    public record Match(Band band, Intent first, Intent second) {}

    record Ranked(Intent first, double firstScore, Intent second, double secondScore) {}

    private record Scored(Intent intent, double score) {}

    private final Embeddings table;
    private final List<Intent> intents;
    private final List<float[][]> examples = new ArrayList<>();

    public IntentIndex(List<Intent> intents, Embeddings table) {
        this.table = table;
        this.intents = List.copyOf(intents);
        for (Intent intent : this.intents) examples.add(intent.examples().stream().map(table::vector).toArray(float[][]::new));
    }

    /** The same text and the same state give the same band. */
    public Match judge(String text, Predicate<String> holds) {
        return intents.size() < 2 ? MISS : judge(rank(text), holds);
    }

    static Match judge(Ranked ranked, Predicate<String> holds) {
        var first = ranked.first();
        boolean strong = ranked.firstScore() >= STRONG && ranked.firstScore() - ranked.secondScore() >= LEAD;
        if (!open(first, holds) || !strong && (ranked.firstScore() < CLOSE || first.label() == null)) return MISS;
        if (strong) return new Match(Band.STRONG, first, null);
        boolean both = ranked.secondScore() >= CLOSE && open(ranked.second(), holds) && ranked.second().label() != null;
        return new Match(Band.CLOSE, first, both ? ranked.second() : null);
    }

    public static boolean open(Intent intent, Predicate<String> holds) {
        return intent.node() != null && (intent.when() == null || holds.test(intent.when()));
    }

    Ranked rank(String text) {
        float[] line = table.vector(text);
        var scores = new ArrayList<Scored>();
        for (int i = 0; i < intents.size(); i++) scores.add(new Scored(intents.get(i), best(line, examples.get(i))));
        scores.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(scored -> scored.intent().id()));
        return new Ranked(scores.get(0).intent(), scores.get(0).score(), scores.get(1).intent(), scores.get(1).score());
    }

    private static double best(float[] line, float[][] examples) {
        double best = 0;
        for (float[] example : examples) {
            double dot = 0;
            for (int d = 0; d < line.length; d++) dot += line[d] * example[d];
            best = Math.max(best, dot);
        }
        return best;
    }
}
