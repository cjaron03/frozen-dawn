package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Everything she carries, with no server in it. The same events at the same game times give the same state, to the bit. */
final class PatternState {
    static final int VERSION = 1;
    /** Bounded: she holds this many reasons and loses the oldest. */
    static final int MAX_HISTORY = 16;
    static final long NOT_SET = -1;
    private static final double PRESENCE_FEEDS = 0.01, TALK_FEEDS = 0.10;

    private Drives drives = new Drives();
    private Feelings feelings = new Feelings();
    private final List<Appraised> history = new ArrayList<>();
    /** The last conversation already counted, so a poll or a reload cannot count it twice. */
    private long heardTalk = NOT_SET;

    Drives drives() { return drives; }
    Feelings feelings() { return feelings; }
    List<Appraised> history() { return List.copyOf(history); }

    void appraise(UUID who, Deed deed, long now) {
        drives.begin(now);
        var rule = Appraisal.rule(deed);
        var raised = Appraisal.intensities(rule, Appraisal.scale(rule, drives, now));
        raised.forEach((feeling, intensity) -> feelings.raise(feeling, intensity, now));
        if (rule.drive() != null && rule.feeds() != 0) drives.feed(rule.drive(), rule.feeds(), now);
        history.add(new Appraised(now, who, deed.name(), raised));
        if (history.size() > MAX_HISTORY) history.removeFirst();
    }

    /** {@code near} and {@code lastTalk} are what the poll already knows: nothing here looks at the world. */
    void poll(int near, long lastTalk, long now) {
        drives.begin(now);
        if (near > 0) drives.feed(Drive.COMPANY, PRESENCE_FEEDS, now);
        if (lastTalk > heardTalk) {
            heardTalk = lastTalk;
            drives.feed(Drive.COMPANY, TALK_FEEDS, now);
        }
    }

    Mood mood(long now) {
        return feelings.mood(now);
    }

    void age(long ticks) {
        drives.age(ticks);
        feelings.age(ticks);
    }

    void clear() {
        drives.clear();
        feelings.clear();
        history.clear();
        heardTalk = NOT_SET;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("dataVersion", VERSION);
        tag.put("drives", drives.save());
        tag.put("feelings", feelings.save());
        var list = new ListTag();
        for (var entry : history) list.add(entry.save());
        tag.put("history", list);
        tag.putLong("heardTalk", heardTalk);
        return tag;
    }

    static PatternState load(CompoundTag tag) {
        if (tag.getInt("dataVersion") > VERSION) throw new IllegalStateException("Unsupported future CS-1 state version");
        var state = new PatternState();
        state.drives = Drives.load(tag.getCompound("drives"));
        state.feelings = Feelings.load(tag.getCompound("feelings"));
        for (Tag entry : tag.getList("history", Tag.TAG_COMPOUND)) {
            var appraised = Appraised.load((CompoundTag) entry);
            if (appraised != null) state.history.add(appraised);
        }
        while (state.history.size() > MAX_HISTORY) state.history.removeFirst();
        if (tag.contains("heardTalk")) state.heardTalk = tag.getLong("heardTalk");
        return state;
    }
}
