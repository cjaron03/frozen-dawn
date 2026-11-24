package com.frozendawn.descent;

import com.frozendawn.init.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;

/**
 * The descent's eras, top to bottom (CH-44): the 1816 house, then one per cold period. Each era holds its
 * standing count and its instrument's clip (CH-46). Chamber zero is not an era (CH-45).
 */
public enum DescentEra {
    HOUSE_1816(2, "music_box", 172),
    MAUNDER(3, "maunder", 283),
    FAMINE(4, "famine", 264),
    LATE_ANTIQUE(5, "late_antique", 230),
    BRONZE_AGE(6, "bronze_age", 206),
    FOUR_KILOYEAR(7, "four_kiloyear", 239),
    EIGHT_KILOYEAR(8, "eight_kiloyear", 195),
    YOUNGER_DRYAS(9, "younger_dryas", 238),
    GLACIAL_MAXIMUM(10, "glacial_maximum", 280);

    private final int standing;
    private final String clip;
    private final int clipTicks;

    DescentEra(int standing, String clip, int clipTicks) {
        this.standing = standing;
        this.clip = clip;
        this.clipTicks = clipTicks;
    }

    /** Lower-case name, used in ids and commands. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The number of figures standing in this era's motif. */
    public int standing() {
        return standing;
    }

    /** The sounds.json event that plays this era's instrument. */
    public String soundId() {
        return "block.descent." + clip;
    }

    /** The command word for this era's clip: the era's id, or {@code music_box} for the house. */
    public String clipName() {
        return clip;
    }

    /** The clip's length in ticks, rounded up; the instrument stays silent this long after it plays. */
    public int clipTicks() {
        return clipTicks;
    }

    public Holder<SoundEvent> sound() {
        return switch (this) {
            case HOUSE_1816 -> ModSounds.DESCENT_MUSIC_BOX;
            case MAUNDER -> ModSounds.DESCENT_MAUNDER;
            case FAMINE -> ModSounds.DESCENT_FAMINE;
            case LATE_ANTIQUE -> ModSounds.DESCENT_LATE_ANTIQUE;
            case BRONZE_AGE -> ModSounds.DESCENT_BRONZE_AGE;
            case FOUR_KILOYEAR -> ModSounds.DESCENT_FOUR_KILOYEAR;
            case EIGHT_KILOYEAR -> ModSounds.DESCENT_EIGHT_KILOYEAR;
            case YOUNGER_DRYAS -> ModSounds.DESCENT_YOUNGER_DRYAS;
            case GLACIAL_MAXIMUM -> ModSounds.DESCENT_GLACIAL_MAXIMUM;
        };
    }
}
