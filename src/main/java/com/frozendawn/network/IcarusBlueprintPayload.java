package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Shows or hides an AMR-1 Icarus Controller's ghost-block blueprint for the receiving player; the client reads the cells itself. */
public record IcarusBlueprintPayload(BlockPos controller, boolean show) implements CustomPacketPayload {
    public static final Type<IcarusBlueprintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_blueprint"));
    public static final StreamCodec<ByteBuf, IcarusBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, IcarusBlueprintPayload::controller, ByteBufCodecs.BOOL, IcarusBlueprintPayload::show, IcarusBlueprintPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
