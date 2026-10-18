package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gpr.PlumbLinePolicy;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One Plumb Line pulse's returns for the overlay. Bounded; boxes are world block coordinates (inclusive). */
public record PlumbLineReturnsPayload(String dimension, BlockPos scanner, List<Return> returns) implements CustomPacketPayload {
    public PlumbLineReturnsPayload {
        returns = List.copyOf(returns);
        if (returns.size() > PlumbLinePolicy.MAX_RETURNS) throw new IllegalArgumentException("Too many returns");
    }
    public static final Type<PlumbLineReturnsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "plumb_line_returns"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlumbLineReturnsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public PlumbLineReturnsPayload decode(RegistryFriendlyByteBuf buf) {
            String dimension = buf.readUtf(256); BlockPos scanner = buf.readBlockPos(); int count = buf.readVarInt();
            if (count < 0 || count > PlumbLinePolicy.MAX_RETURNS) throw new IllegalArgumentException("Invalid return count");
            var list = new ArrayList<Return>();
            for (int i = 0; i < count; i++) {
                int kind = buf.readVarInt();
                if (kind < 0 || kind >= Kind.values().length) throw new IllegalArgumentException("Invalid return kind");
                list.add(new Return(Kind.values()[kind], buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            }
            return new PlumbLineReturnsPayload(dimension, scanner, list);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, PlumbLineReturnsPayload p) {
            buf.writeUtf(p.dimension(), 256); buf.writeBlockPos(p.scanner()); buf.writeVarInt(p.returns().size());
            for (var r : p.returns()) {
                buf.writeVarInt(r.kind().ordinal()); buf.writeUtf(r.label(), 128);
                buf.writeVarInt(r.minX()); buf.writeVarInt(r.minY()); buf.writeVarInt(r.minZ());
                buf.writeVarInt(r.maxX()); buf.writeVarInt(r.maxY()); buf.writeVarInt(r.maxZ()); buf.writeBoolean(r.precise());
            }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
