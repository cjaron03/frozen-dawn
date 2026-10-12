package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authoritative nearby-panel shortage notice; no speech or pressure mutation. */
public record RoomOxygenWarningPayload(boolean shortage) implements CustomPacketPayload {

    public static final Type<RoomOxygenWarningPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "room_oxygen_warning"));

    public static final StreamCodec<ByteBuf, RoomOxygenWarningPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RoomOxygenWarningPayload::shortage,
            RoomOxygenWarningPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
