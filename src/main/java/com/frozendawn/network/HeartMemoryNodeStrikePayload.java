package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client selection request for the exact Heart memory node position rendered. */
public record HeartMemoryNodeStrikePayload(int nodeIndex, float renderedLoad)
        implements CustomPacketPayload {
    public static final Type<HeartMemoryNodeStrikePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_memory_node_strike"));
    public static final StreamCodec<ByteBuf, HeartMemoryNodeStrikePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    HeartMemoryNodeStrikePayload::nodeIndex,
                    ByteBufCodecs.FLOAT,
                    HeartMemoryNodeStrikePayload::renderedLoad,
                    HeartMemoryNodeStrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
