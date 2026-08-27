package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.MasterArchitectMusicStage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client score movement for the Master Architect encounter. */
public record MasterArchitectFightMusicPayload(int stageId)
        implements CustomPacketPayload {

    public static final Type<MasterArchitectFightMusicPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_fight_music"));

    public static final StreamCodec<ByteBuf, MasterArchitectFightMusicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MasterArchitectFightMusicPayload::stageId,
                    MasterArchitectFightMusicPayload::new);

    public static MasterArchitectFightMusicPayload inactive() {
        return new MasterArchitectFightMusicPayload(MasterArchitectMusicStage.OFF.id());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
