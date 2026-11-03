package com.frozendawn.network;
import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record AtmosphericBreachPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AtmosphericBreachPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "atmospheric_breach"));
    public static final StreamCodec<ByteBuf, AtmosphericBreachPayload> STREAM_CODEC = BlockPos.STREAM_CODEC.map(AtmosphericBreachPayload::new, AtmosphericBreachPayload::pos);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
