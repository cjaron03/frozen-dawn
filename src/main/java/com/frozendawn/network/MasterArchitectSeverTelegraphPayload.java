package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Target-only Thermal Sever cast cue, authoritative from the server. */
public record MasterArchitectSeverTelegraphPayload(int entityId, int durationTicks)
        implements CustomPacketPayload {

    public static final Type<MasterArchitectSeverTelegraphPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_sever_telegraph"));

    public static final StreamCodec<ByteBuf, MasterArchitectSeverTelegraphPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectSeverTelegraphPayload::entityId,
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectSeverTelegraphPayload::durationTicks,
                    MasterArchitectSeverTelegraphPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
