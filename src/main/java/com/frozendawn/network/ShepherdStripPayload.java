package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** They skipped: the strip shows the rest of the line at once, or with {@code clear}, goes. */
public record ShepherdStripPayload(boolean clear) implements CustomPacketPayload {
    public static final Type<ShepherdStripPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shepherd_strip"));
    public static final StreamCodec<ByteBuf, ShepherdStripPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(ShepherdStripPayload::new, ShepherdStripPayload::clear);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
