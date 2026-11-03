package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * One firing tick of a Sever Lash, for the shooter and everyone watching: where the beam runs, how far, whether it
 * lands on a block, whether it is in vacuum, and the emitter's heat (0 to 1) for the hum. Drawing only; the server cuts.
 */
public record LashBeamPayload(int shooter, Vector3f direction, float length, float heat, byte flags)
        implements CustomPacketPayload {
    private static final byte LANDED = 1, VACUUM = 2;
    public static final Type<LashBeamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "lash_beam"));
    public static final StreamCodec<ByteBuf, LashBeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LashBeamPayload::shooter, ByteBufCodecs.VECTOR3F, LashBeamPayload::direction,
            ByteBufCodecs.FLOAT, LashBeamPayload::length, ByteBufCodecs.FLOAT, LashBeamPayload::heat,
            ByteBufCodecs.BYTE, LashBeamPayload::flags, LashBeamPayload::new);

    public static LashBeamPayload of(int shooter, Vector3f direction, float length, float heat, boolean landed, boolean vacuum) {
        return new LashBeamPayload(shooter, direction, length, heat, (byte) ((landed ? LANDED : 0) | (vacuum ? VACUUM : 0)));
    }
    public boolean landed() { return (flags & LANDED) != 0; }
    public boolean vacuum() { return (flags & VACUUM) != 0; }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
