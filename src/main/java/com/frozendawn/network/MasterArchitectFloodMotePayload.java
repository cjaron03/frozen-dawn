package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-local memory mote lifecycle; each player receives their own corridor. */
public record MasterArchitectFloodMotePayload(
        int operation,
        int moteId,
        int memoryType,
        double x,
        double y,
        double z) implements CustomPacketPayload {
    public static final int SPAWN = 0;
    public static final int COLLECT = 1;
    public static final int CLEAR = 2;

    public static final Type<MasterArchitectFloodMotePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_flood_mote"));

    public static final StreamCodec<ByteBuf, MasterArchitectFloodMotePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodMotePayload::operation,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodMotePayload::moteId,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFloodMotePayload::memoryType,
                    ByteBufCodecs.DOUBLE,
                    MasterArchitectFloodMotePayload::x,
                    ByteBufCodecs.DOUBLE,
                    MasterArchitectFloodMotePayload::y,
                    ByteBufCodecs.DOUBLE,
                    MasterArchitectFloodMotePayload::z,
                    MasterArchitectFloodMotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
