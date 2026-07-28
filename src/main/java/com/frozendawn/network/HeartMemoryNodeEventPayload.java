package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Node impact feedback plus the striking player's memory-fragment context. */
public record HeartMemoryNodeEventPayload(
        int entityId,
        int nodeIndex,
        int hitProgress,
        int destroyedMask,
        int memoryVariant,
        int visits,
        int casualties,
        boolean showMemory) implements CustomPacketPayload {
    public static final Type<HeartMemoryNodeEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "heart_memory_node_event"));
    public static final StreamCodec<ByteBuf, HeartMemoryNodeEventPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public HeartMemoryNodeEventPayload decode(ByteBuf buffer) {
                    return new HeartMemoryNodeEventPayload(
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer));
                }

                @Override
                public void encode(ByteBuf buffer, HeartMemoryNodeEventPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.nodeIndex());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.hitProgress());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.destroyedMask());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.memoryVariant());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.visits());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.casualties());
                    ByteBufCodecs.BOOL.encode(buffer, payload.showMemory());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
