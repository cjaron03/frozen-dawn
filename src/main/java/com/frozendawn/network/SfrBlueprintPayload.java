package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Shows or hides an SFR-1 Controller's ghost-block blueprint for the receiving player; the client reads the cells itself. */
public record SfrBlueprintPayload(BlockPos controller, boolean show) implements CustomPacketPayload {
    public static final Type<SfrBlueprintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "sfr_blueprint"));
    public static final StreamCodec<ByteBuf, SfrBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SfrBlueprintPayload::controller, ByteBufCodecs.BOOL, SfrBlueprintPayload::show, SfrBlueprintPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
