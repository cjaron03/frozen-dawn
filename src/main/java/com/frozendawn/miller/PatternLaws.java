package com.frozendawn.miller;

/** The six laws she runs under, felt from the inside. MACS's names, so the two can be set side by side. */
public enum PatternLaws {
    LAGGED("A feeling answers what already happened; it never gets ahead of it."),
    LEGIBLE("Her face shows what she feels. It does not hide."),
    EXPLOITABLE("The same deed moves her the same way every time."),
    FALLIBLE("What she feels comes from what she saw, not from what was true."),
    BOUNDED("One face at a time, and only her latest reasons are kept."),
    CAUSAL("Only what her sensors saw counts.");

    private final String meaning;

    PatternLaws(String meaning) {
        this.meaning = meaning;
    }

    public String meaning() { return meaning; }
}
