package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenTowerTerminalPayload(BlockPos pos, long nonce, int triesLeft, int state,
                                       long removedMask, long usedPairMask, String auditLog)
        implements CustomPacketPayload {

    public static final int STATE_ACTIVE = 0;
    public static final int STATE_SOLVED = 1;
    public static final int STATE_LOCKED_OUT = 2;

    public static final Type<OpenTowerTerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_tower_terminal"));

    public static final StreamCodec<ByteBuf, OpenTowerTerminalPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenTowerTerminalPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            long nonce = ByteBufCodecs.VAR_LONG.decode(buffer);
            int triesLeft = ByteBufCodecs.VAR_INT.decode(buffer);
            int state = ByteBufCodecs.VAR_INT.decode(buffer);
            long removedMask = ByteBufCodecs.VAR_LONG.decode(buffer);
            long usedPairMask = ByteBufCodecs.VAR_LONG.decode(buffer);
            String auditLog = ByteBufCodecs.STRING_UTF8.decode(buffer);
            return new OpenTowerTerminalPayload(pos, nonce, triesLeft, state, removedMask, usedPairMask, auditLog);
        }

        @Override
        public void encode(ByteBuf buffer, OpenTowerTerminalPayload value) {
            BlockPos.STREAM_CODEC.encode(buffer, value.pos());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.nonce());
            ByteBufCodecs.VAR_INT.encode(buffer, value.triesLeft());
            ByteBufCodecs.VAR_INT.encode(buffer, value.state());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.removedMask());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.usedPairMask());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.auditLog());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
