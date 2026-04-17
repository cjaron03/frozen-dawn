package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EndingSequencePayload(
        boolean conspiracyDiscovered,
        int daysSurvived,
        int terminalsHacked,
        int mobsKilled,
        boolean showComplianceLine)
        implements CustomPacketPayload {

    public static final Type<EndingSequencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ending_sequence"));

    public static final StreamCodec<ByteBuf, EndingSequencePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EndingSequencePayload decode(ByteBuf buf) {
            boolean conspiracyDiscovered = ByteBufCodecs.BOOL.decode(buf);
            int daysSurvived = ByteBufCodecs.VAR_INT.decode(buf);
            int terminalsHacked = ByteBufCodecs.VAR_INT.decode(buf);
            int mobsKilled = ByteBufCodecs.VAR_INT.decode(buf);
            boolean showComplianceLine = ByteBufCodecs.BOOL.decode(buf);
            return new EndingSequencePayload(conspiracyDiscovered, daysSurvived, terminalsHacked, mobsKilled,
                    showComplianceLine);
        }

        @Override
        public void encode(ByteBuf buf, EndingSequencePayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.conspiracyDiscovered());
            ByteBufCodecs.VAR_INT.encode(buf, payload.daysSurvived());
            ByteBufCodecs.VAR_INT.encode(buf, payload.terminalsHacked());
            ByteBufCodecs.VAR_INT.encode(buf, payload.mobsKilled());
            ByteBufCodecs.BOOL.encode(buf, payload.showComplianceLine());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
