package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client packet carrying whether room gas/pressure is present, independently of
 * breathable oxygen. Scarcity must never be presented as a physical vacuum.
 */
public record RoomPressureStatePayload(boolean pressurized) implements CustomPacketPayload {

    public static final Type<RoomPressureStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "room_pressure_state"));

    public static final StreamCodec<ByteBuf, RoomPressureStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RoomPressureStatePayload::pressurized,
            RoomPressureStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
