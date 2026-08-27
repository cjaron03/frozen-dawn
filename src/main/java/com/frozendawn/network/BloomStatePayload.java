package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Local post-Maeve Bloom density sampled by the server for one player. */
public record BloomStatePayload(float density, int band) implements CustomPacketPayload {
    public static final Type<BloomStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "bloom_state"));
    public static final StreamCodec<ByteBuf, BloomStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, BloomStatePayload::density,
                    ByteBufCodecs.VAR_INT, BloomStatePayload::band,
                    BloomStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
