package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitTowerTerminalPayload(BlockPos pos, long nonce, int actionType, int actionIndex, String typedGuess)
        implements CustomPacketPayload {

    public static final int ACTION_TYPED_GUESS = 0;
    public static final int ACTION_USE_PAIR = 1;
    public static final int ACTION_ARCHIVE_PREVIOUS = 2;
    public static final int ACTION_ARCHIVE_NEXT = 3;
    public static final int ACTION_ARCHIVE_OPEN_PAGE = 4;
    public static final int ACTION_ARCHIVE_AUTH = 5;

    public static final Type<SubmitTowerTerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "submit_tower_terminal"));

    public static final StreamCodec<ByteBuf, SubmitTowerTerminalPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SubmitTowerTerminalPayload::pos,
            ByteBufCodecs.VAR_LONG, SubmitTowerTerminalPayload::nonce,
            ByteBufCodecs.VAR_INT, SubmitTowerTerminalPayload::actionType,
            ByteBufCodecs.VAR_INT, SubmitTowerTerminalPayload::actionIndex,
            ByteBufCodecs.STRING_UTF8, SubmitTowerTerminalPayload::typedGuess,
            SubmitTowerTerminalPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
