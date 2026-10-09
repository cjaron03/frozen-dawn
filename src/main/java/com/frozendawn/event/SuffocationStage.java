package com.frozendawn.event;

/** Highest crossed warning threshold; independent of damage/effect application. */
public enum SuffocationStage {
    NONE(null),
    LIGHTHEADED("message.frozendawn.suffocate.lightheaded"),
    NAUSEA("message.frozendawn.suffocate.nausea"),
    FADING("message.frozendawn.suffocate.fading"),
    DYING("message.frozendawn.suffocate.dying");

    private final String translationKey;
    SuffocationStage(String translationKey) { this.translationKey = translationKey; }
    public String translationKey() { return translationKey; }

    public static SuffocationStage fromTicks(int ticks) {
        if (ticks >= 200) return DYING;
        if (ticks >= 140) return FADING;
        if (ticks >= 80) return NAUSEA;
        if (ticks >= 30) return LIGHTHEADED;
        return NONE;
    }
}
