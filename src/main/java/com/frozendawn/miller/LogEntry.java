package com.frozendawn.miller;

import com.frozendawn.shepherd.ShepherdPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** One day of her log. It holds a lang key and never text. {@code note} may be null, {@code signed} fills the blank field, and {@code cracked} is the only thing that moves Status. */
public record LogEntry(long time, boolean signed, boolean cracked, String note) {
    static final String NOTE = "shepherd.log.note.";

    public long day() {
        return ShepherdPolicy.day(time);
    }

    /** The kind of note this is, or null for a blank. The key is all an entry keeps, so it has to say. */
    String kind() {
        return note == null ? null : note.substring(NOTE.length(), note.indexOf('.', NOTE.length()));
    }

    LogEntry aged(long ticks) {
        return new LogEntry(time - ticks, signed, cracked, note);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putBoolean("signed", signed);
        tag.putBoolean("cracked", cracked);
        if (note != null) tag.putString("note", note);
        return tag;
    }

    /** Null if it is damaged: no time, or a note that is not one of hers. */
    static LogEntry load(CompoundTag tag) {
        if (!tag.contains("time", Tag.TAG_LONG)) return null;
        String note = tag.contains("note") ? tag.getString("note") : null;
        if (note != null && (!note.startsWith(NOTE) || note.indexOf('.', NOTE.length()) < 0)) return null;
        return new LogEntry(tag.getLong("time"), tag.getBoolean("signed"), tag.getBoolean("cracked"), note);
    }
}
