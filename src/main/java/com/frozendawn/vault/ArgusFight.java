package com.frozendawn.vault;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import net.minecraft.nbt.CompoundTag;

/**
 * ORSA ISS-4 "Argus", without a world: the rounds, the clock and what a fuse does. {@link VaultArgusBlockEntity} shows it
 * and keeps it. A round: someone on the upper level, the board lights, it goes dark, the fuses have shuffled; all pulled,
 * the Custodian's shell opens, then back to the board. A Custodian still standing after three openings gets round 3 again.
 */
public final class ArgusFight {
    public enum Phase { DORMANT, WAITING, READ, PULL, BURN, WON }
    public enum Pull { NOTHING, RIGHT, WRONG, COMPLETE }
    public record Round(int glyphs, int shownTicks, boolean ordered) { }

    public static final int GLYPHS = 6, ROUNDS = 3, CLOCK_TICKS = 4 * 60 * 20, BURN_TICKS = 15 * 20, PENALTY_TICKS = 20 * 20, HOLD_TICKS = 40;
    /** A held use repeats every four ticks; a longer gap than this is a hand let go. */
    private static final int HOLD_GAP = 8;
    private static final int PULSE_SLOWEST = 40, PULSE_FASTEST = 10;

    private final Random random;
    private Phase phase = Phase.DORMANT;
    private int clock = CLOCK_TICKS, won, phaseTicks, next;
    private int[] fuses = IntStream.range(0, GLYPHS).toArray(), wanted = new int[0];
    private boolean[] pulled = new boolean[GLYPHS];
    private boolean clean = true;
    private final long[] heldFrom = new long[GLYPHS], heldLast = new long[GLYPHS];

    public ArgusFight(Random random) { this.random = random; }

    public static Round round(int won) {
        return switch (Math.min(won, ROUNDS - 1)) { case 0 -> new Round(3, 8 * 20, false); case 1 -> new Round(4, 6 * 20, false); default -> new Round(4, 5 * 20, true); };
    }
    public Round currentRound() { return round(won); }
    public Phase phase() { return phase; }
    public boolean running() { return phase != Phase.DORMANT && phase != Phase.WON; }
    public int clock() { return clock; }
    public int won() { return won; }
    public int[] fuses() { return fuses; }
    public int[] wanted() { return wanted; }
    public boolean pulled(int slot) { return pulled[slot]; }
    public boolean clean() { return clean; }
    /** The share of its health the Custodian cannot be brought below, for the openings so far. */
    public float floor() { return 1 - Math.min(won, ROUNDS) / (float) ROUNDS; }

    public void start() { if (phase == Phase.DORMANT) phase = Phase.WAITING; }

    /** @param readerUp someone stands on the upper level. @return whether the clock ran out: the caller purges. */
    public boolean tick(boolean readerUp) {
        if (!running()) return false;
        if (--clock <= 0) { clock = 0; return true; }
        switch (phase) {
            case WAITING -> { if (readerUp) enter(Phase.READ); }
            case READ -> { if (++phaseTicks >= currentRound().shownTicks()) { shuffle(); enter(Phase.PULL); } }
            case BURN -> { if (++phaseTicks >= BURN_TICKS) enter(Phase.WAITING); }
            default -> { }
        }
        return false;
    }
    private void enter(Phase to) {
        phase = to;
        phaseTicks = 0;
        if (to == Phase.READ) choose();
    }
    private void choose() {
        var glyphs = IntStream.range(0, GLYPHS).toArray();
        mix(glyphs);
        wanted = Arrays.copyOf(glyphs, currentRound().glyphs());
        pulled = new boolean[GLYPHS];
        next = 0;
    }
    private void shuffle() {
        var was = fuses.clone();
        do mix(fuses); while (Arrays.equals(was, fuses));
    }
    private void mix(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), held = values[i];
            values[i] = values[j];
            values[j] = held;
        }
    }

    /** The glyph at {@code glyph} on the board, lit? Round 3 shows one at a time, in the order they must be pulled. */
    public boolean boardLit(int glyph) {
        if (phase != Phase.READ) return false;
        if (!currentRound().ordered()) return wants(glyph);
        return wanted[Math.min(wanted.length - 1, phaseTicks * wanted.length / currentRound().shownTicks())] == glyph;
    }
    private boolean wants(int glyph) { return Arrays.stream(wanted).anyMatch(g -> g == glyph); }

    /** Use held on a fuse at game time {@code now}: true once it has been held two unbroken seconds. */
    public boolean hold(int slot, long now) {
        if (heldLast[slot] == 0 || now - heldLast[slot] > HOLD_GAP) heldFrom[slot] = now;
        heldLast[slot] = now;
        if (now - heldFrom[slot] < HOLD_TICKS) return false;
        heldLast[slot] = 0;
        return true;
    }
    /** How far through a hold this fuse is, nought to one. */
    public float held(int slot, long now) {
        return heldLast[slot] == 0 ? 0 : Math.min(1, (now - heldFrom[slot]) / (float) HOLD_TICKS);
    }

    public Pull pull(int slot) {
        if (phase != Phase.PULL || pulled[slot]) return Pull.NOTHING;
        var round = currentRound();
        int glyph = fuses[slot];
        if (!wants(glyph) || round.ordered() && wanted[next] != glyph) {
            clock = Math.max(0, clock - PENALTY_TICKS);
            clean = false;
            return Pull.WRONG;
        }
        pulled[slot] = true;
        if (++next < wanted.length) return Pull.RIGHT;
        won++;
        enter(Phase.BURN);
        return Pull.COMPLETE;
    }

    public void fell() { if (running()) phase = Phase.WON; }
    public void purge() { clean = false; startOver(); }
    public void abandon() { startOver(); }
    /** Debug only: as though nobody had ever come. */
    public void reset() { clean = true; startOver(); }
    private void startOver() {
        phase = Phase.DORMANT;
        clock = CLOCK_TICKS;
        won = phaseTicks = next = 0;
        wanted = new int[0];
        pulled = new boolean[GLYPHS];
    }

    private float left() { return clock / (float) CLOCK_TICKS; }
    /** How many of {@code total} emergency lights still burn: falling away with the square of the clock, one to the end. */
    public int lights(int total) { return Math.max(1, (int) Math.ceil(total * left() * left())); }
    /** Ticks between bass pulses. */
    public int pulseEvery() { return Math.round(PULSE_FASTEST + (PULSE_SLOWEST - PULSE_FASTEST) * left()); }
    public int seconds() { return (clock + 19) / 20; }
    public static String text(int seconds) { return String.format("%02d:%02d", seconds / 60, seconds % 60); }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putInt("clock", clock);
        tag.putInt("won", won);
        tag.putInt("phaseTicks", phaseTicks);
        tag.putInt("next", next);
        tag.putIntArray("fuses", fuses);
        tag.putIntArray("wanted", wanted);
        tag.putIntArray("pulled", IntStream.range(0, GLYPHS).filter(s -> pulled[s]).toArray());
        tag.putBoolean("clean", clean);
        return tag;
    }
    public void load(CompoundTag tag) {
        phase = Phase.valueOf(tag.getString("phase"));
        clock = tag.getInt("clock");
        won = tag.getInt("won");
        phaseTicks = tag.getInt("phaseTicks");
        next = tag.getInt("next");
        fuses = tag.getIntArray("fuses");
        wanted = tag.getIntArray("wanted");
        pulled = new boolean[GLYPHS];
        for (int slot : tag.getIntArray("pulled")) pulled[slot] = true;
        clean = tag.getBoolean("clean");
    }

    /** Debug only, for the playtest's buttons. */
    public void jump(int rounds, Phase to, int clockTicks) {
        startOver();
        won = rounds;
        clock = clockTicks;
        phase = Phase.WAITING;
        if (to == Phase.WAITING) return;
        enter(Phase.READ);
        if (to == Phase.READ) return;
        shuffle();
        enter(Phase.PULL);
        if (to == Phase.PULL) return;
        won++;
        enter(Phase.BURN);
    }
    public void setClock(int ticks) { clock = ticks; }
}
