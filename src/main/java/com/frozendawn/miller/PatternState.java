package com.frozendawn.miller;

import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Everything she carries, with no server in it. The same events at the same game times give the same state, to the bit. */
final class PatternState {
    static final int VERSION = 2;
    /** Bounded: she holds this many reasons and loses the oldest. */
    static final int MAX_HISTORY = 16;
    static final long NOT_SET = -1;
    static final long NIGHT_TICKS = ShepherdPolicy.NIGHT_ENDS - ShepherdPolicy.NIGHT_STARTS;
    private static final double PRESENCE_FEEDS = 0.01, TALK_FEEDS = 0.10;
    /** Not {@link #NOT_SET}: a clock that {@link #age} has moved can land on -1. */
    private static final long UNARMED = Long.MIN_VALUE;

    private Drives drives = new Drives();
    private Feelings feelings = new Feelings();
    private final List<Appraised> history = new ArrayList<>();
    /** The last conversation already counted, so a poll or a reload cannot count it twice. */
    private long heardTalk = NOT_SET;
    private ImpressionStore impressions = new ImpressionStore();
    private Keepsakes keepsakes = new Keepsakes();
    /** When the day's evidence next lands. It runs on game time, because the poll knows no sky. */
    private long settleAt = UNARMED;
    private SelfModel self = new SelfModel();
    private Coherence coherence = new Coherence();
    /** When anyone was last beside her. */
    private long seenAt = UNARMED;
    /** When the recording next cuts in over her. */
    private long nextSlip = UNARMED;
    /** What she wanted of the sky when a clear night last fed it, and when. Null until one has. */
    private Fading nightSky;

    Drives drives() { return drives; }
    Feelings feelings() { return feelings; }
    List<Appraised> history() { return List.copyOf(history); }
    ImpressionStore impressions() { return impressions; }
    Keepsakes keepsakes() { return keepsakes; }
    SelfModel self() { return self; }
    Coherence coherence() { return coherence; }

    void appraise(UUID who, Deed deed, long now) {
        drives.begin(now);
        arm(now);
        impressions.saw(who, deed);
        var rule = Appraisal.rule(deed);
        var raised = Appraisal.intensities(rule, Appraisal.scale(rule, drives, now));
        if (deed == Deed.CLEAR_NIGHT) nightSky = new Fading(drives.value(Drive.SKY, now), now);
        raised.forEach((feeling, intensity) -> feelings.raise(feeling, intensity, now));
        if (rule.drive() != null && rule.feeds() != 0) drives.feed(rule.drive(), rule.feeds(), now);
        keep(who, deed.name().toLowerCase(Locale.ROOT), raised.values().stream().max(Double::compare).orElse(0.0), now);
        history.add(new Appraised(now, who, deed.name(), raised));
        if (history.size() > MAX_HISTORY) history.removeFirst();
    }

    /** What someone said or asked of her. It reaches only the speaker, and only what they are to her. */
    void heard(UUID who, Heard heard, long now) {
        arm(now);
        impressions.heard(who, heard);
        if (heard instanceof Heard.Reply reply && reply.keep() != null) keep(who, reply.keep(), Keepsakes.NAMED_SALIENCE, now);
    }

    private void keep(UUID who, String kind, double salience, long now) {
        if (keepsakes.keep(who, kind, salience, now) == Keepsakes.Outcome.DISPLACED) self.witness(PatternLaws.BOUNDED);
    }

    private void arm(long now) {
        if (settleAt == UNARMED) settleAt = now + ShepherdPolicy.DAY_TICKS;
        coherence.begin(now);
        if (nextSlip == UNARMED) slipped(now);
    }

    /** {@code near}, {@code lastTalk} and {@code trusted} are what the poll already knows: nothing here looks at the world. */
    void poll(int near, long lastTalk, boolean trusted, long now) {
        drives.begin(now);
        arm(now);
        if (near > 0) drives.feed(Drive.COMPANY, PRESENCE_FEEDS, now);
        if (heardTalk == NOT_SET) {
            // The first look only sets the line: a talk from before this state, or the clock seeded this tick, is no conversation.
            heardTalk = Math.max(lastTalk, now);
        } else if (lastTalk > heardTalk) {
            heardTalk = lastTalk;
            drives.feed(Drive.COMPANY, TALK_FEEDS, now);
            coherence.spoken(now);
            if (feelings.strongest(now) != null) self.witness(PatternLaws.LEGIBLE);
        }
        if (near > 0) noticed(now);
        self.look(impressions.known(), trusted);
    }

    /** Someone is beside her. What she has been turning over lands, and being away shows. */
    private void noticed(long now) {
        if (now >= settleAt) {
            var settled = impressions.settle();
            settleAt = now + ShepherdPolicy.DAY_TICKS;
            if (!settled.changed().isEmpty()) self.witness(PatternLaws.LAGGED);
            if (!settled.flipped().isEmpty()) self.witness(PatternLaws.FALLIBLE);
            if (settled.fromKindness()) self.witness(PatternLaws.EXPLOITABLE);
        }
        if (seenAt != UNARMED && now - seenAt >= ShepherdPolicy.DAY_TICKS) self.witness(PatternLaws.CAUSAL);
        seenAt = now;
    }

    Mood mood(long now) {
        return feelings.mood(now);
    }

    /** Looking about feeds what wanted to, as being beside someone feeds Company. */
    void looked(long now) {
        drives.feed(Drive.CURIOSITY, PRESENCE_FEEDS, now);
    }

    boolean slipDue(long now) {
        return nextSlip != UNARMED && now >= nextSlip;
    }

    /** Five days to the next, down to three at the coherence floor, as she is at the moment it plays. */
    void slipped(long now) {
        nextSlip = now + slipInterval(now);
    }

    void slipNow(long now) {
        nextSlip = now;
    }

    private long slipInterval(long now) {
        return (long) (ShepherdPolicy.DAY_TICKS * (3 + 2 * (coherence.value(now) - Coherence.FLOOR) / (1 - Coherence.FLOOR)));
    }

    /** What she wants of the sky. One look at a clear night feeds it, but she goes on wanting that night until it is over. */
    double skyWanted(long now) {
        double wanted = drives.value(Drive.SKY, now);
        return nightSky != null && now - nightSky.at() < NIGHT_TICKS ? Math.max(wanted, nightSky.value()) : wanted;
    }

    /** What she wants to do with {@code nearest} beside her. Only reads. */
    Urges.Urge urge(UUID nearest, long now) {
        return Urges.pick(Attention.on(this, nearest, now), drives.value(Drive.CURIOSITY, now), drives.value(Drive.COMPANY, now));
    }

    void age(long ticks) {
        drives.age(ticks);
        feelings.age(ticks);
        history.replaceAll(event -> event.aged(ticks));
        keepsakes.age(ticks);
        coherence.age(ticks);
        if (settleAt != UNARMED) settleAt -= ticks;
        if (seenAt != UNARMED) seenAt -= ticks;
        if (nightSky != null) nightSky = nightSky.aged(ticks);
        if (nextSlip != UNARMED) nextSlip -= ticks;
    }

    void clear() {
        drives.clear();
        feelings.clear();
        history.clear();
        heardTalk = NOT_SET;
        impressions.clear();
        keepsakes.clear();
        self.clear();
        coherence.clear();
        settleAt = seenAt = nextSlip = UNARMED;
        nightSky = null;
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
        tag.put("impressions", impressions.save());
        tag.put("keepsakes", keepsakes.save());
        tag.put("self", self.save());
        tag.put("coherence", coherence.save());
        if (settleAt != UNARMED) tag.putLong("settleAt", settleAt);
        if (seenAt != UNARMED) tag.putLong("seenAt", seenAt);
        if (nightSky != null) tag.put("nightSky", nightSky.save());
        if (nextSlip != UNARMED) tag.putLong("nextSlip", nextSlip);
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
        state.impressions = ImpressionStore.load(tag.getCompound("impressions"));
        state.keepsakes = Keepsakes.load(tag.getCompound("keepsakes"));
        state.self = SelfModel.load(tag.getCompound("self"));
        state.coherence = Coherence.load(tag.getCompound("coherence"));
        if (tag.contains("settleAt")) state.settleAt = tag.getLong("settleAt");
        if (tag.contains("seenAt")) state.seenAt = tag.getLong("seenAt");
        if (tag.contains("nightSky")) state.nightSky = Fading.load(tag.getCompound("nightSky"));
        if (tag.contains("nextSlip")) state.nextSlip = tag.getLong("nextSlip");
        return state;
    }
}
