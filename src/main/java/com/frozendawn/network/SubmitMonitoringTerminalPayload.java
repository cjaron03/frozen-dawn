package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitMonitoringTerminalPayload(BlockPos pos, long nonce, int actionType, int actionIndex, String typedGuess)
        implements CustomPacketPayload {

    public static final int ACTION_TYPED_GUESS = 0;
    public static final int ACTION_USE_PAIR = 1;

    public static final Type<SubmitMonitoringTerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "submit_monitoring_terminal"));

    public static final StreamCodec<ByteBuf, SubmitMonitoringTerminalPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SubmitMonitoringTerminalPayload::pos,
            ByteBufCodecs.VAR_LONG, SubmitMonitoringTerminalPayload::nonce,
            ByteBufCodecs.VAR_INT, SubmitMonitoringTerminalPayload::actionType,
            ByteBufCodecs.VAR_INT, SubmitMonitoringTerminalPayload::actionIndex,
            ByteBufCodecs.STRING_UTF8, SubmitMonitoringTerminalPayload::typedGuess,
            SubmitMonitoringTerminalPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
