package com.frozendawn.vault;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The drill hall's Two-Person Rule, without a world. ORSA staff rehearsed the key turn here against a recorded partner. Pressing
 * record takes up to twenty seconds of one body and the key it holds, until pressed again, then plays them back;
 * {@link VaultRecorderBlockEntity} shows it as a pale figure. The door wants both keys held at once, by
 * any mix of hands and the ghost, and stays open long enough to reach it: nobody can hold a key and walk through. Nothing here
 * is saved: a take lost to a reload costs what any retry costs, which is nothing.
 */
public final class TwoPersonRule {
    public enum Phase { IDLE, RECORDING, PLAYING }
    public record Pose(double x, double y, double z, float yaw, float pitch) { }
    /** One tick of a take. {@code key} is the key held, or {@link #NO_KEY}. */
    public record Frame(Pose pose, int key) { }

    public static final int TAKE_TICKS = 20 * 20, KEYS = 2, NO_KEY = -1;
    /** A press sooner than this after the first is a fumble, not an end. */
    public static final int MIN_TAKE_TICKS = 20;
    /** From the last moment both keys were held to the door shutting: a walk from the far key, with some over. */
    public static final int OPEN_TICKS = 5 * 20;
    /** A held use repeats every four ticks; a longer gap than this is a hand let go. */
    private static final int HOLD_GAP = 8;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private Phase phase = Phase.IDLE;
    private final List<Frame> take = new ArrayList<>();
    private final long[] hand = {NEVER, NEVER}, recorderHand = {NEVER, NEVER};
    private int playhead, open;

    public Phase phase() { return phase; }
    public boolean doorOpen() { return open > 0; }
    /** What is left of this half of the take. */
    public int ticksLeft() {
        return switch (phase) { case IDLE -> 0; case RECORDING -> TAKE_TICKS - take.size(); case PLAYING -> take.size() - playhead; };
    }
    /** Where the ghost stands this tick, or null when nothing is playing. */
    public @Nullable Frame ghost() { return phase == Phase.PLAYING ? take.get(playhead) : null; }

    /** The record button: starts a take, ends one when you are ready, and throws a replay away for a new take. */
    public boolean press() {
        if (phase == Phase.RECORDING) {
            if (take.size() < MIN_TAKE_TICKS) return false;
            play();
            return true;
        }
        take.clear();
        phase = Phase.RECORDING;
        return true;
    }
    private void play() {
        phase = Phase.PLAYING;
        playhead = 0;
    }

    /** A hand on a key, repeated while it stays there. {@code recorder}: it is the hand of the body being recorded. */
    public void hold(int key, long now, boolean recorder) {
        hand[key] = now;
        if (recorder) recorderHand[key] = now;
    }

    public boolean held(int key, long now) {
        var frame = ghost();
        return within(hand[key], now) || frame != null && frame.key() == key;
    }
    private static boolean within(long last, long now) { return now - last <= HOLD_GAP; }

    /** @param body the recorder as they stand now, or null when they are gone: a take cannot go on without them. */
    public void tick(long now, @Nullable Pose body) {
        if (phase == Phase.RECORDING) record(now, body);
        else if (phase == Phase.PLAYING && ++playhead >= take.size()) stop();
        open = held(0, now) && held(1, now) ? OPEN_TICKS : Math.max(0, open - 1);
    }
    private void record(long now, @Nullable Pose body) {
        if (body == null) { stop(); return; }
        int key = NO_KEY;
        for (int k = 0; k < KEYS; k++) if (within(recorderHand[k], now)) key = k;
        take.add(new Frame(body, key));
        if (take.size() >= TAKE_TICKS) play();
    }
    private void stop() {
        phase = Phase.IDLE;
        take.clear();
        playhead = 0;
    }

    /** Playtest only. */
    public void reset() {
        stop();
        open = 0;
        for (int k = 0; k < KEYS; k++) hand[k] = recorderHand[k] = NEVER;
    }
}
