package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The Watch Floor's purge reached this player: white out the screen. */
public record VaultPurgePayload() implements CustomPacketPayload {
    public static final Type<VaultPurgePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "vault_purge"));
    public static final StreamCodec<ByteBuf, VaultPurgePayload> STREAM_CODEC = StreamCodec.unit(new VaultPurgePayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
