package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Raw movement magnitude used to resist an Undone grasp. */
public record UndoneStrugglePayload(float input) implements CustomPacketPayload {
    public static final Type<UndoneStrugglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "undone_struggle"));
    public static final StreamCodec<ByteBuf, UndoneStrugglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    UndoneStrugglePayload::input,
                    UndoneStrugglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
