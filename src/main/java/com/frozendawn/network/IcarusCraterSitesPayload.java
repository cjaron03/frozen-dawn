package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.IcarusCraterData;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Every still-hot Icarus crater in the player's level, for the smoke, embers and ash drawn over it. Sent on joining,
 * changing dimension or respawning, and to everyone in the level when a crater is made or forgotten. */
public record IcarusCraterSitesPayload(List<IcarusCraterData.Site> sites) implements CustomPacketPayload {
    public static final Type<IcarusCraterSitesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_crater_sites"));
    private static final StreamCodec<ByteBuf, IcarusCraterData.Site> SITE = StreamCodec.composite(
            BlockPos.STREAM_CODEC, IcarusCraterData.Site::center, ByteBufCodecs.DOUBLE, IcarusCraterData.Site::radius,
            ByteBufCodecs.VAR_LONG, IcarusCraterData.Site::blastTick, IcarusCraterData.Site::new);
    public static final StreamCodec<ByteBuf, IcarusCraterSitesPayload> STREAM_CODEC =
            SITE.apply(ByteBufCodecs.list()).map(IcarusCraterSitesPayload::new, IcarusCraterSitesPayload::sites);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
