package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.PowerLink;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative nearby beams; snapshots replace rather than append client state. */
public record PowerLinksPayload(String dimension, List<PowerLink> links) implements CustomPacketPayload {
    public static final int MAX_LINKS = 256;
    public PowerLinksPayload { links = List.copyOf(links); if (links.size() > MAX_LINKS) throw new IllegalArgumentException("Too many links"); }
    public static final Type<PowerLinksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "power_links"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerLinksPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public PowerLinksPayload decode(RegistryFriendlyByteBuf buffer) {
            String dimension = buffer.readUtf(256); int count = buffer.readVarInt();
            if (count < 0 || count > MAX_LINKS) throw new IllegalArgumentException("Invalid power link count");
            var links = new ArrayList<PowerLink>();
            for (int i = 0; i < count; i++) links.add(new PowerLink(buffer.readBlockPos(), buffer.readBlockPos()));
            return new PowerLinksPayload(dimension, links);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PowerLinksPayload payload) {
            buffer.writeUtf(payload.dimension(), 256); buffer.writeVarInt(payload.links().size());
            for (var link : payload.links()) { buffer.writeBlockPos(link.a()); buffer.writeBlockPos(link.b()); }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
