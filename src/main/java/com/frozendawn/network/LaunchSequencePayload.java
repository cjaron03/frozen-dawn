package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LaunchSequencePayload(
        int entityId,
        BlockPos padCenter,
        int countdownTicks,
        int liftoffTicks,
        int ascentTicks,
        int atmosphereExitTicks,
        int fadeTicks)
        implements CustomPacketPayload {

    public static final Type<LaunchSequencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "launch_sequence"));

    public static final StreamCodec<ByteBuf, LaunchSequencePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LaunchSequencePayload decode(ByteBuf buf) {
            int entityId = ByteBufCodecs.VAR_INT.decode(buf);
            BlockPos padCenter = BlockPos.STREAM_CODEC.decode(buf);
            int countdownTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int liftoffTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int ascentTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int atmosphereExitTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int fadeTicks = ByteBufCodecs.VAR_INT.decode(buf);
            return new LaunchSequencePayload(entityId, padCenter, countdownTicks, liftoffTicks,
                    ascentTicks, atmosphereExitTicks, fadeTicks);
        }

        @Override
        public void encode(ByteBuf buf, LaunchSequencePayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
            BlockPos.STREAM_CODEC.encode(buf, payload.padCenter());
            ByteBufCodecs.VAR_INT.encode(buf, payload.countdownTicks());
            ByteBufCodecs.VAR_INT.encode(buf, payload.liftoffTicks());
            ByteBufCodecs.VAR_INT.encode(buf, payload.ascentTicks());
            ByteBufCodecs.VAR_INT.encode(buf, payload.atmosphereExitTicks());
            ByteBufCodecs.VAR_INT.encode(buf, payload.fadeTicks());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
