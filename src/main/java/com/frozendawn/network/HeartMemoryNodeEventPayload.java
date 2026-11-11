package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HeartMemoryLedger;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Node impact feedback plus the striking player's ledger, which node five reads back. */
public record HeartMemoryNodeEventPayload(
        int entityId,
        int nodeIndex,
        int hitProgress,
        int destroyedMask,
        HeartMemoryLedger ledger,
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
                            new HeartMemoryLedger(
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.VAR_INT.decode(buffer),
                                    ByteBufCodecs.BOOL.decode(buffer)),
                            ByteBufCodecs.BOOL.decode(buffer));
                }

                @Override
                public void encode(ByteBuf buffer, HeartMemoryNodeEventPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.nodeIndex());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.hitProgress());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.destroyedMask());
                    HeartMemoryLedger ledger = payload.ledger();
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.contactDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.cameBack());
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.violationDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.casualties());
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.casualtyDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, ledger.deaths());
                    ByteBufCodecs.BOOL.encode(buffer, ledger.massacre());
                    ByteBufCodecs.BOOL.encode(buffer, payload.showMemory());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
