package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Whether this world's Terminus wands log terminations, for the tooltip. */
public record TerminusLedgerPayload(boolean turned) implements CustomPacketPayload {
    public static final Type<TerminusLedgerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "terminus_ledger"));
    public static final StreamCodec<ByteBuf, TerminusLedgerPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(TerminusLedgerPayload::new, TerminusLedgerPayload::turned);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
