package com.frozendawn.maeve;

import java.util.Comparator;
import java.util.List;

/** Authored alternatives, including the value of resolving uncertainty (§9.5). */
final class StrategySelector {
    record Score(String action, double tactical, double survival, double objective,
                 double information, double curiosity, double exposure, double attention) {
        double total() { return tactical + survival + objective + information * curiosity - exposure - attention; }
        String describe() {
            return action + " utility=" + total() + " tactical=" + tactical + " survival=" + survival
                    + " objective=" + objective + " information=" + information + " curiosity=" + curiosity
                    + " exposure=" + exposure + " attention=" + attention;
        }
    }

    static List<Score> options(double confidence, double distance, double health, boolean threatened,
                               boolean strongBet, int occupied, int capacity) {
        double uncertainty = 1 - BeliefPolicy.clamp(confidence);
        double exposure = .08 + Math.clamp(distance, 0, 24) / 240 + (threatened ? 1 : 0);
        return List.of(
                new Score("SURVEY_ACCESS", .05, .05, .2, uncertainty, 1.5, exposure,
                        .1 + .1 * Math.clamp((double) occupied / Math.max(1, capacity), 0, 1)),
                new Score("LOCAL_ENGAGEMENT", strongBet ? 1.3 : threatened ? 1.2 : .55, 0, 0, 0, 0, 0, 0),
                new Score("WITHDRAW", 0, health < .6 ? 2 : .1, 0, 0, 0, 0, 0));
    }

    static Score best(List<Score> options) {
        return options.stream().filter(s -> Double.isFinite(s.total()))
                .max(Comparator.comparingDouble(Score::total).thenComparing(Score::action)).orElseThrow();
    }
}
