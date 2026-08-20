package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ThaevenLoreViewedPayload(int record, int revision)
        implements CustomPacketPayload {
    public static final Type<ThaevenLoreViewedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "thaeven_lore_viewed"));
    public static final StreamCodec<ByteBuf, ThaevenLoreViewedPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ThaevenLoreViewedPayload::record,
                    ByteBufCodecs.VAR_INT, ThaevenLoreViewedPayload::revision,
                    ThaevenLoreViewedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
