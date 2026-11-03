package com.frozendawn.defense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A Terminus's own record: discharges left (three, ever) and minds logged, which a found wand starts at one. */
public record TerminusLog(int discharges, int logged) {
    public static final TerminusLog FOUND = new TerminusLog(3, 1);
    public static final Codec<TerminusLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("discharges").forGetter(TerminusLog::discharges),
            Codec.INT.fieldOf("logged").forGetter(TerminusLog::logged)).apply(i, TerminusLog::new));
    public static final StreamCodec<ByteBuf, TerminusLog> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminusLog::discharges, ByteBufCodecs.VAR_INT, TerminusLog::logged, TerminusLog::new);

    public boolean spent() { return discharges <= 0; }
    public TerminusLog spend() { return new TerminusLog(Math.max(0, discharges - 1), logged); }
    public TerminusLog log(int minds) { return new TerminusLog(discharges, logged + minds); }
}
