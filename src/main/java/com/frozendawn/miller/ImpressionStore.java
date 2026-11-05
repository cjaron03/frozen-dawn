package com.frozendawn.miller;

import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * Her theories about each player, with evidence for and against. What she sees waits until the day settles, because she
 * only ever works out what someone meant afterwards, and two events already make her half sure: that is how she gets
 * people wrong. A player's death is never evidence of anything.
 */
public final class ImpressionStore {
    public enum Theory {
        KIND,
        TALKS_TO_ME,
        CALLS_ME_AURORA,
        USES_ME,
        CAREFUL,
        JOKES_WITH_ME
    }

    /** {@code changed} now lean another way, {@code flipped} did so from being sure, and {@code fromKindness} means kind words alone moved her. */
    public record Settled(Set<Theory> changed, Set<Theory> flipped, boolean fromKindness) { }

    private static final double PRIOR = 2, FADE = 0.9, SURE = 0.5;
    static final int MAX_PLAYERS = 8;

    private static final class Evidence {
        double forIt, against;
        int pendingFor, pendingAgainst, pendingKind;

        double confidence() {
            return (forIt - against) / (forIt + against + PRIOR);
        }

        void land() {
            forIt = forIt * FADE + pendingFor;
            against = against * FADE + pendingAgainst;
            pendingFor = pendingAgainst = pendingKind = 0;
        }
    }

    /** Longest unseen first. Reading must not reorder it, so only {@link #note} moves anyone. */
    private final Map<UUID, Map<Theory, Evidence>> players = new LinkedHashMap<>();

    /** Who has ever named her. It is a fact, not a theory: it does not fade, and nothing takes it back. */
    private final Set<UUID> named = new LinkedHashSet<>();

    /** The theory a dialogue file's {@code "+kind"} or {@code "-kind"} names, or null if it names none. */
    public static Theory noted(String entry) {
        if (entry.length() < 2 || entry.charAt(0) != '+' && entry.charAt(0) != '-') return null;
        for (Theory theory : Theory.values()) if (theory.name().equals(entry.substring(1).toUpperCase(Locale.ROOT))) return theory;
        return null;
    }

    private Evidence evidence(UUID who, Theory theory) {
        var theories = players.remove(who);
        if (theories == null) {
            theories = new EnumMap<>(Theory.class);
            if (players.size() == MAX_PLAYERS) players.remove(players.keySet().iterator().next());
        }
        players.put(who, theories);
        return theories.computeIfAbsent(theory, ignored -> new Evidence());
    }

    void note(UUID who, Theory theory, boolean supports) {
        var evidence = evidence(who, theory);
        if (theory == Theory.CALLS_ME_AURORA && supports) named.add(who);
        if (supports) evidence.pendingFor++;
        else evidence.pendingAgainst++;
    }

    /** Kept apart from other evidence of kindness, because kind words alone are how she finds she can be played. */
    void noteKindReply(UUID who) {
        note(who, Theory.KIND, true);
        evidence(who, Theory.KIND).pendingKind++;
    }

    void saw(UUID who, Deed deed) {
        switch (deed) {
            case WARM_SHELTER -> {
                note(who, Theory.KIND, true);
                note(who, Theory.CAREFUL, true);
            }
            case READ_LOG -> note(who, Theory.CAREFUL, true);
            case ARCHIVIST_KILLED -> note(who, Theory.KIND, false);
            case SHELTER_FROZE -> {
                note(who, Theory.KIND, false);
                note(who, Theory.CAREFUL, false);
            }
            case AIR_FAILING -> note(who, Theory.CAREFUL, false);
            case LEFT_BEHIND -> note(who, Theory.TALKS_TO_ME, false);
            case CALLED_SHEPHERD -> note(who, Theory.CALLS_ME_AURORA, false);
            case CLEAR_NIGHT, NINE_FOUND -> { }
        }
    }

    void heard(UUID who, Heard heard) {
        switch (heard) {
            case Heard.Reply reply -> {
                answered(who, reply.tone());
                for (String entry : reply.noted()) {
                    var theory = noted(entry);
                    if (theory != null) note(who, theory, entry.charAt(0) == '+');
                }
            }
            case Heard.Used used -> {
                switch (used.action()) {
                    case FOLLOW, LAMP -> note(who, Theory.USES_ME, true);
                    case TALK -> note(who, Theory.USES_ME, false);
                    case SETTINGS -> { }
                }
            }
        }
    }

    private void answered(UUID who, Slot tone) {
        if (tone == null) return;
        note(who, Theory.TALKS_TO_ME, true);
        switch (tone) {
            case KIND -> noteKindReply(who);
            case SARCASTIC -> note(who, Theory.JOKES_WITH_ME, true);
            case BLUNT -> note(who, Theory.JOKES_WITH_ME, false);
            case ASK -> { }
        }
    }

    /** The day's evidence lands, and what was already there fades a little. */
    Settled settle() {
        Set<Theory> changed = EnumSet.noneOf(Theory.class), flipped = EnumSet.noneOf(Theory.class);
        boolean fromKindness = false;
        for (var theories : players.values()) {
            for (var entry : theories.entrySet()) {
                var evidence = entry.getValue();
                boolean kindOnly = evidence.pendingKind > 0 && evidence.pendingKind == evidence.pendingFor;
                double before = evidence.confidence();
                evidence.land();
                double after = evidence.confidence();
                if (Math.signum(before) != Math.signum(after)) changed.add(entry.getKey());
                if (Math.abs(before) >= SURE && before * after < 0) flipped.add(entry.getKey());
                if (kindOnly && after > before) fromKindness = true;
            }
        }
        return new Settled(changed, flipped, fromKindness);
    }

    /** From -1 to 1, on settled evidence only. Nobody she has no theory on is simply 0. */
    public double confidence(UUID who, Theory theory) {
        var theories = players.get(who);
        var evidence = theories == null ? null : theories.get(theory);
        return evidence == null ? 0 : evidence.confidence();
    }

    /** Someone talks to her and once gave her her name: enough that she starts to notice what she says. */
    boolean known() {
        for (UUID who : named) {
            if (confidence(who, Theory.TALKS_TO_ME) >= SURE) return true;
        }
        return false;
    }

    /** Everyone she has a theory on, and what she makes of them. */
    Map<UUID, Map<Theory, Double>> all() {
        var all = new LinkedHashMap<UUID, Map<Theory, Double>>();
        players.forEach((who, theories) -> {
            var leans = new EnumMap<Theory, Double>(Theory.class);
            theories.forEach((theory, evidence) -> leans.put(theory, evidence.confidence()));
            all.put(who, leans);
        });
        return all;
    }

    double evidenceFor(UUID who, Theory theory) {
        var theories = players.get(who);
        var evidence = theories == null ? null : theories.get(theory);
        return evidence == null ? 0 : evidence.forIt;
    }

    void clear() {
        players.clear();
        named.clear();
    }

    CompoundTag save() {
        var list = new ListTag();
        players.forEach((who, theories) -> {
            var player = new CompoundTag();
            player.putUUID("who", who);
            var saved = new CompoundTag();
            theories.forEach((theory, evidence) -> {
                var tag = new CompoundTag();
                tag.putDouble("for", evidence.forIt);
                tag.putDouble("against", evidence.against);
                tag.putInt("pendingFor", evidence.pendingFor);
                tag.putInt("pendingAgainst", evidence.pendingAgainst);
                tag.putInt("pendingKind", evidence.pendingKind);
                saved.put(theory.name(), tag);
            });
            player.put("theories", saved);
            list.add(player);
        });
        var tag = new CompoundTag();
        tag.put("players", list);
        var names = new ListTag();
        named.forEach(who -> names.add(NbtUtils.createUUID(who)));
        tag.put("named", names);
        return tag;
    }

    private static double count(CompoundTag tag, String name) {
        double value = tag.getDouble(name);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    /** A damaged save still loads with nothing below zero. */
    static ImpressionStore load(CompoundTag tag) {
        var store = new ImpressionStore();
        for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) entry;
            if (!player.hasUUID("who")) continue;
            Map<Theory, Evidence> theories = new EnumMap<>(Theory.class);
            var saved = player.getCompound("theories");
            for (Theory theory : Theory.values()) {
                if (!saved.contains(theory.name())) continue;
                var values = saved.getCompound(theory.name());
                var evidence = new Evidence();
                evidence.forIt = count(values, "for");
                evidence.against = count(values, "against");
                evidence.pendingFor = Math.max(0, values.getInt("pendingFor"));
                evidence.pendingAgainst = Math.max(0, values.getInt("pendingAgainst"));
                evidence.pendingKind = Math.max(0, values.getInt("pendingKind"));
                theories.put(theory, evidence);
            }
            store.players.put(player.getUUID("who"), theories);
            if (store.players.size() > MAX_PLAYERS) store.players.remove(store.players.keySet().iterator().next());
        }
        for (Tag entry : tag.getList("named", Tag.TAG_INT_ARRAY)) {
            if (((IntArrayTag) entry).size() == 4) store.named.add(NbtUtils.loadUUID(entry));
        }
        return store;
    }
}
