package com.frozendawn.miller;

import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.SelfModel.Stage;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Her private log, one entry when each day settles, about whoever was beside her. It holds lang keys, so her words stay
 * authored and no player's words reach it. The note is chosen by a fixed order and the day number, with nothing random
 * in it. It is no keepsake: at the cap the oldest entry goes without a word.
 */
public final class DailyLog {
    public static final int MAX = 128;
    /** How strong a feeling has to be for the punctuation to go, and for the form to break. Both are for the owner's pass. */
    static final double HIGH = 0.6, PEAK = 0.8;
    private static final int REST = 3, REUNION_DAYS = 5, KNOWS_EVERY = 4;
    private static final List<Deed> BAD_DAYS = List.of(Deed.ARCHIVIST_KILLED, Deed.AIR_FAILING, Deed.SHELTER_FROZE, Deed.LEFT_BEHIND, Deed.CALLED_SHEPHERD);
    private static final List<Feeling> OWN_WORDS_AT_HIGH = List.of(Feeling.JOY, Feeling.FEAR);

    /**
     * Everything the order reads, so the choice has no server in it. {@code time} is game time and {@code number} the
     * world's day as she writes it, which sleep moves and game time does not. {@code strongest} and {@code strength} are the feeling
     * on her face, or null and 0. {@code marks} and {@code deeds} are the subject's since the last entry, {@code backs}
     * how many times she has seen them return, {@code unseen} how long it had been, {@code read} whether they have read
     * this, and {@code nineFound} whether anyone took her to the husk since the last entry.
     */
    record Day(long time, long number, boolean signed, Stage stage, Tier tier, double coherence, Feeling strongest, double strength,
               Set<Mark> marks, Set<Deed> deeds, int backs, long unseen, boolean read, boolean nineFound) {
        boolean noticing() {
            return stage != Stage.PRODUCT;
        }

        boolean walls() {
            return stage.compareTo(Stage.FINDING_WALLS) >= 0;
        }

        String voice() {
            return walls() ? "walls" : noticing() ? "noticing" : "product";
        }

        /** Her strength of feeling as the form shows it. At Product the form is all there is. */
        boolean high() {
            return noticing() && strongest != null && strength >= HIGH;
        }
    }

    private record Note(String kind, String variant, boolean cracked) {
        Note(String kind, String variant) {
            this(kind, variant, false);
        }

        String key() {
            return LogEntry.NOTE + kind + "." + variant;
        }
    }

    /** Oldest first. */
    private final List<LogEntry> entries = new ArrayList<>();
    /** She has filed someone under Cargo, seen the husk, asked about it, and broken over a return. Each happens once a world. */
    private boolean filed, nineSeen, nineAsked, broke;

    /** Newest first. */
    List<LogEntry> entries() {
        return List.copyOf(entries.reversed());
    }

    /** When she last wrote, so what came after it is what the next entry is about. */
    long lastWritten() {
        return entries.isEmpty() ? Long.MIN_VALUE : entries.getLast().time();
    }

    LogEntry write(Day day) {
        nineSeen |= day.nineFound();
        boolean firstSigned = day.signed() && entries.stream().noneMatch(LogEntry::signed);
        var note = firstSigned ? null : choose(day);
        if (note != null) spent(note.key());
        var entry = new LogEntry(day.time(), day.number(), day.signed(), note != null && note.cracked(), note == null ? null : note.key());
        entries.add(entry);
        if (entries.size() > MAX) entries.removeFirst();
        return entry;
    }

    private Note choose(Day day) {
        for (var note : fitting(day)) if (!resting(note.kind())) return note;
        return null;
    }

    private boolean resting(String kind) {
        return entries.subList(Math.max(0, entries.size() - REST), entries.size()).stream().anyMatch(entry -> kind.equals(entry.kind()));
    }

    /** What the entry used up. */
    private void spent(String key) {
        if (key.endsWith("cargo.1") || key.endsWith("reunion.peak")) filed = true;
        if (key.endsWith("back.nine")) nineAsked = true;
        if (key.endsWith("back.walls")) broke = true;
    }

    /** Every note that fits the day, in the owner's order. */
    private List<Note> fitting(Day day) {
        var notes = new ArrayList<Note>();
        var marks = day.marks();
        if (marks.contains(Mark.BACK)) notes.add(back(day));
        if (day.noticing() && day.tier().atLeast(Tier.FRIENDLY) && day.unseen() >= REUNION_DAYS * (long) ShepherdPolicy.DAY_TICKS) {
            notes.add(day.walls() ? new Note("reunion", "peak", true) : new Note("reunion", "high"));
        }
        if (!entries.isEmpty() && entries.getLast().cracked()) notes.add(variant(day, "after", "1", "2"));
        if (day.nineFound()) notes.add(new Note("nine_found", "1"));
        if (!filed && day.tier().atLeast(Tier.FRIENDLY)) notes.add(new Note("cargo", "1"));
        if (day.high()) notes.add(feeling(day));
        if (marks.contains(Mark.CLOSE_CALL) && !marks.contains(Mark.STOPPED)) notes.add(new Note("close_call", day.high() ? "high" : "1"));
        if (day.read() && day.number() % KNOWS_EVERY == 0) notes.add(variant(day, "knows", "1", "2", "3"));
        for (Deed deed : BAD_DAYS) if (day.deeds().contains(deed)) notes.add(new Note(deed.name().toLowerCase(Locale.ROOT), "1"));
        if (marks.contains(Mark.FIGHT)) notes.add(new Note("fight", day.voice()));
        if (marks.contains(Mark.COLD)) notes.add(filed && day.noticing() ? variant(day, "cold", day.voice(), "cargo") : new Note("cold", day.voice()));
        if (day.deeds().contains(Deed.WARM_SHELTER)) notes.add(new Note("warm_shelter", day.voice()));
        if (day.deeds().contains(Deed.CLEAR_NIGHT)) notes.add(new Note("clear_night", "1"));
        if (day.deeds().contains(Deed.READ_LOG)) notes.add(new Note("read_log", "1"));
        if (day.coherence() < Coherence.SLOW_BELOW) notes.add(variant(day, "slow", "1", "2"));
        if (day.tier() == Tier.WARY) notes.add(new Note("wary", "1"));
        if (day.tier().atLeast(Tier.TRUSTED)) notes.add(filed ? variant(day, "trusted", "1", "cargo") : new Note("trusted", "1"));
        if (day.noticing() && day.strongest() != null && !day.high()) notes.add(feeling(day));
        return notes;
    }

    /** The first return she saw, then the one after Nine, then once the form breaks over it, then the name. At Product it is only ever a reading. */
    private Note back(Day day) {
        if (!day.noticing()) return new Note("back", "product");
        if (nineSeen && !nineAsked) return new Note("back", "nine");
        if (day.walls() && !broke) return new Note("back", "walls", true);
        return new Note("back", filed && day.backs() > 1 ? "again" : "noticing");
    }

    private static Note feeling(Day day) {
        String kind = day.strongest().name().toLowerCase(Locale.ROOT);
        if (day.strength() >= PEAK) return new Note(kind, "peak", true);
        return new Note(kind, day.high() && OWN_WORDS_AT_HIGH.contains(day.strongest()) ? "high" : "1");
    }

    /** Which variant plays is the day number over the ones her coherence can reach. */
    private static Note variant(Day day, String kind, String... variants) {
        return new Note(kind, variants[(int) (day.number() % Coherence.reach(variants.length, day.coherence()))]);
    }

    void age(long ticks) {
        entries.replaceAll(entry -> entry.aged(ticks));
    }

    void clear() {
        entries.clear();
        filed = nineSeen = nineAsked = broke = false;
    }

    CompoundTag save() {
        var list = new ListTag();
        for (var entry : entries) list.add(entry.save());
        var tag = new CompoundTag();
        tag.put("entries", list);
        tag.putBoolean("filed", filed);
        tag.putBoolean("nineSeen", nineSeen);
        tag.putBoolean("nineAsked", nineAsked);
        tag.putBoolean("broke", broke);
        return tag;
    }

    static DailyLog load(CompoundTag tag) {
        var log = new DailyLog();
        for (Tag saved : tag.getList("entries", Tag.TAG_COMPOUND)) {
            var entry = LogEntry.load((CompoundTag) saved);
            if (entry != null) log.entries.add(entry);
        }
        while (log.entries.size() > MAX) log.entries.removeFirst();
        log.filed = tag.getBoolean("filed");
        log.nineSeen = tag.getBoolean("nineSeen");
        log.nineAsked = tag.getBoolean("nineAsked");
        log.broke = tag.getBoolean("broke");
        return log;
    }
}
