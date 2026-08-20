package com.frozendawn.lore;

import java.util.Locale;
import java.util.Optional;

/** Stable identifiers for the six reconstructed Thaeven Memory Records. */
public enum ThaevenRecordId {
    VEL_AN("vel_an"),
    THE_PASSAGE("the_passage"),
    PATTERN_RESIDUE("pattern_residue"),
    THE_HEART_BENEATH("the_heart_beneath"),
    THE_FIRST_CROSSING("the_first_crossing"),
    THE_UNTHREADING("the_unthreading");

    private final String serializedName;

    ThaevenRecordId(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public long bit() {
        return 1L << ordinal();
    }

    public static Optional<ThaevenRecordId> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        for (ThaevenRecordId record : values()) {
            if (record.serializedName.equals(normalized)
                    || record.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }
}
