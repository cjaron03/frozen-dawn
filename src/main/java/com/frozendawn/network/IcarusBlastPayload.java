package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** An Icarus detonation as one player meets it: where, how big, how far away, and whether they stand in vacuum, which
 * decides between the boom and fireball or the suit thud and plasma shell. */
public record IcarusBlastPayload(Vec3 center, double radius, boolean vacuum, double distance) implements CustomPacketPayload {
    public static final Type<IcarusBlastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_blast"));
    public static final StreamCodec<ByteBuf, IcarusBlastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, p -> p.center().x, ByteBufCodecs.DOUBLE, p -> p.center().y, ByteBufCodecs.DOUBLE, p -> p.center().z,
            ByteBufCodecs.DOUBLE, IcarusBlastPayload::radius, ByteBufCodecs.BOOL, IcarusBlastPayload::vacuum, ByteBufCodecs.DOUBLE, IcarusBlastPayload::distance,
            (x, y, z, radius, vacuum, distance) -> new IcarusBlastPayload(new Vec3(x, y, z), radius, vacuum, distance));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
