package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Target-only urgent suit diagnostic fired when Thermal Sever lands. */
public record MasterArchitectThermalSeverWarningPayload() implements CustomPacketPayload {

    public static final Type<MasterArchitectThermalSeverWarningPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_thermal_sever_warning"));

    public static final StreamCodec<ByteBuf, MasterArchitectThermalSeverWarningPayload>
            STREAM_CODEC = StreamCodec.unit(new MasterArchitectThermalSeverWarningPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
