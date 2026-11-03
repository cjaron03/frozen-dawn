package com.frozendawn.defense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Emitter heat in half-ticks of fire, settled lazily from game time so a Lash cools in a chest
 * as well as in a hand. Firing adds two units a tick; idle ticks shed two in air and one in vacuum.
 */
public record LashHeat(int heat, long lockoutUntil, long lastFired) {
    public static final int FIRE_HEAT = 2;
    /** Six seconds of continuous fire. */
    public static final int FIRE_TICKS = 120;
    public static final int OVERHEAT = FIRE_TICKS * FIRE_HEAT;
    /** Four seconds in air; doubled in vacuum, where nothing carries the heat away. */
    public static final int LOCKOUT_TICKS = 80;
    public static final LashHeat COOL = new LashHeat(0, 0, 0);

    public static final Codec<LashHeat> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, OVERHEAT).fieldOf("heat").forGetter(LashHeat::heat),
            Codec.LONG.fieldOf("lockout_until").forGetter(LashHeat::lockoutUntil),
            Codec.LONG.fieldOf("last_fired").forGetter(LashHeat::lastFired)).apply(i, LashHeat::new));
    public static final StreamCodec<ByteBuf, LashHeat> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LashHeat::heat, ByteBufCodecs.VAR_LONG, LashHeat::lockoutUntil,
            ByteBufCodecs.VAR_LONG, LashHeat::lastFired, LashHeat::new);

    public record Shot(boolean fired, LashHeat heat) {}

    /** A lockout further off than the vacuum maximum came from another world's clock and has lapsed. */
    public boolean lockedOut(long now) { return now < lockoutUntil && lockoutUntil - now <= 2L * LOCKOUT_TICKS; }

    /** How hot the emitter looks, 0 to 1: full while locked out, otherwise its heat cooling as in air. The client has
     * no vacuum reading, so in vacuum the glow fades a little early. */
    public float glow(long now) {
        if (lockedOut(now)) return 1;
        if (lockoutUntil > 0) return 0;
        return Math.max(0, heat - 2 * Math.max(0, now - lastFired)) / (float) OVERHEAT;
    }

    /** The overheat vent: 1 for the white-hot first half of a lockout, 0.5 as it cools in the second, 0 otherwise. */
    public float vent(long now) {
        if (!lockedOut(now)) return 0;
        return 2 * (now - lastFired) < lockoutUntil - lastFired ? 1 : .5F;
    }

    public Shot fire(long now, boolean vacuum) {
        if (lockedOut(now)) return new Shot(false, this);
        int settled = heat;
        long since = lastFired;
        if (lockoutUntil > 0) { settled = 0; since = Math.max(since, lockoutUntil - 1); }
        long idle = Math.max(0, now - since - 1);
        settled = (int) Math.max(0, settled - idle * (vacuum ? 1 : 2));
        settled += FIRE_HEAT;
        if (settled < OVERHEAT) return new Shot(true, new LashHeat(settled, 0, now));
        return new Shot(true, new LashHeat(OVERHEAT, now + (vacuum ? 2L : 1L) * LOCKOUT_TICKS, now));
    }
}
