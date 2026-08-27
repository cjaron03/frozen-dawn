package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client strength for the local storm surrounding a living Master Architect.
 */
public record MasterArchitectWeatherPayload(
        float strength,
        int auraTier,
        boolean fightActive,
        BlockPos hearthCenter,
        boolean anchored,
        int aftermathTicks,
        int aftermathDurationTicks,
        float aftermathStrength,
        boolean hearthStormDead) implements CustomPacketPayload {

    public static final Type<MasterArchitectWeatherPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_weather"));

    public static final StreamCodec<ByteBuf, MasterArchitectWeatherPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MasterArchitectWeatherPayload decode(ByteBuf buffer) {
                    return new MasterArchitectWeatherPayload(
                            ByteBufCodecs.FLOAT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer),
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.FLOAT.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer));
                }

                @Override
                public void encode(ByteBuf buffer, MasterArchitectWeatherPayload payload) {
                    ByteBufCodecs.FLOAT.encode(buffer, payload.strength());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.auraTier());
                    ByteBufCodecs.BOOL.encode(buffer, payload.fightActive());
                    BlockPos.STREAM_CODEC.encode(buffer, payload.hearthCenter());
                    ByteBufCodecs.BOOL.encode(buffer, payload.anchored());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.aftermathTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.aftermathDurationTicks());
                    ByteBufCodecs.FLOAT.encode(buffer, payload.aftermathStrength());
                    ByteBufCodecs.BOOL.encode(buffer, payload.hearthStormDead());
                }
            };

    public static MasterArchitectWeatherPayload inactive() {
        return new MasterArchitectWeatherPayload(
                0.0F, 0, false, BlockPos.ZERO, false, 0, 0, 0.0F, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
