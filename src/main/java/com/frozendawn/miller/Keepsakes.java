package com.frozendawn.miller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The moments she kept, by how much they mattered. Silence takes none of them: the only way one goes is the cap, when
 * something that mattered more needs its place. Nothing came across with her, so everything here is her own.
 */
public final class Keepsakes {
    public enum Outcome {
        KEPT,
        STRENGTHENED,
        DISPLACED,
        TOO_SLIGHT
    }

    static final int MAX_KEPT = 64;
    static final double RECALL_GAIN = 0.2, NAMED_SALIENCE = 0.8;
    static final long REPEAT_TICKS = 72000;
    private static final double KEPT_AT = 0.30;

    private final Map<UUID, List<Keepsake>> players = new LinkedHashMap<>();

    /** A warm hut every night strengthens one memory of it rather than filling her up. */
    Outcome keep(UUID who, String kind, double salience, long now) {
        if (salience < KEPT_AT) return Outcome.TOO_SLIGHT;
        var kept = players.computeIfAbsent(who, ignored -> new ArrayList<>());
        for (int i = 0; i < kept.size(); i++) {
            var moment = kept.get(i);
            if (moment.kind().equals(kind) && now - moment.time() < REPEAT_TICKS) {
                kept.set(i, moment.strengthened(RECALL_GAIN));
                return Outcome.STRENGTHENED;
            }
        }
        var fresh = new Keepsake(now, kind, salience, 0, 0);
        if (kept.size() < MAX_KEPT) {
            kept.add(fresh);
            return Outcome.KEPT;
        }
        int least = least(kept);
        if (salience <= kept.get(least).salience()) return Outcome.TOO_SLIGHT;
        kept.remove(least);
        kept.add(fresh);
        return Outcome.DISPLACED;
    }

    /** The slightest, and of those the oldest. They are held oldest first, so the first found. */
    private static int least(List<Keepsake> kept) {
        int least = 0;
        for (int i = 1; i < kept.size(); i++) if (kept.get(i).salience() < kept.get(least).salience()) least = i;
        return least;
    }

    /** The one that matters most among those she has not gone back to in three days, or null. Going back strengthens it. */
    Keepsake recall(UUID who, long now) {
        var kept = players.get(who);
        if (kept == null) return null;
        int best = -1;
        for (int i = 0; i < kept.size(); i++) {
            var moment = kept.get(i);
            if (moment.recalled() > 0 && now - moment.recalledAt() < REPEAT_TICKS) continue;
            if (best < 0 || moment.salience() > kept.get(best).salience()) best = i;
        }
        if (best < 0) return null;
        var recalled = kept.get(best).recalled(RECALL_GAIN, now);
        kept.set(best, recalled);
        return recalled;
    }

    public List<Keepsake> of(UUID who) {
        return List.copyOf(players.getOrDefault(who, List.of()));
    }

    /** How many people she holds something of, for diagnostics. */
    Map<UUID, Integer> counts() {
        var counts = new LinkedHashMap<UUID, Integer>();
        players.forEach((who, kept) -> counts.put(who, kept.size()));
        return counts;
    }

    void age(long ticks) {
        for (var kept : players.values()) kept.replaceAll(moment -> moment.aged(ticks));
    }

    void clear() {
        players.clear();
    }

    CompoundTag save() {
        var list = new ListTag();
        players.forEach((who, kept) -> {
            var player = new CompoundTag();
            player.putUUID("who", who);
            var moments = new ListTag();
            for (var moment : kept) moments.add(moment.save());
            player.put("kept", moments);
            list.add(player);
        });
        var tag = new CompoundTag();
        tag.put("players", list);
        return tag;
    }

    static Keepsakes load(CompoundTag tag) {
        var keepsakes = new Keepsakes();
        for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) entry;
            if (!player.hasUUID("who")) continue;
            var kept = new ArrayList<Keepsake>();
            for (Tag saved : player.getList("kept", Tag.TAG_COMPOUND)) {
                var moment = Keepsake.load((CompoundTag) saved);
                if (moment != null) kept.add(moment);
            }
            while (kept.size() > MAX_KEPT) kept.remove(least(kept));
            keepsakes.players.put(player.getUUID("who"), kept);
        }
        return keepsakes;
    }
}
