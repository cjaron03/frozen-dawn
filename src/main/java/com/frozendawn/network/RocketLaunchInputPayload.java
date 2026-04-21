package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RocketLaunchInputPayload() implements CustomPacketPayload {

    public static final Type<RocketLaunchInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "rocket_launch_input"));

    public static final StreamCodec<ByteBuf, RocketLaunchInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RocketLaunchInputPayload decode(ByteBuf buf) {
            return new RocketLaunchInputPayload();
        }

        @Override
        public void encode(ByteBuf buf, RocketLaunchInputPayload payload) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
