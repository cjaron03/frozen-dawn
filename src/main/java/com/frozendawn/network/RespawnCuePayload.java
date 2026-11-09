package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** What a respawn plays: the opening's replay for the player whose death fired the payoff, a boot cue otherwise. */
public record RespawnCuePayload(boolean replay) implements CustomPacketPayload {
    public static final Type<RespawnCuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "respawn_cue"));
    public static final StreamCodec<ByteBuf, RespawnCuePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(RespawnCuePayload::new, RespawnCuePayload::replay);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
