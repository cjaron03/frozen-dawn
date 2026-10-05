package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Persistent lease mirror; inventory packets carry the matching piece identifiers. */
public record EmergencyEvaPayload(UUID issue, int remainingTicks, int exertionLoad,
                                 int wornTicks, int thermalLoad) implements CustomPacketPayload {
    public static final Type<EmergencyEvaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "emergency_eva"));
    public static final StreamCodec<ByteBuf, EmergencyEvaPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EmergencyEvaPayload::issue,
            ByteBufCodecs.VAR_INT, EmergencyEvaPayload::remainingTicks,
            ByteBufCodecs.VAR_INT, EmergencyEvaPayload::exertionLoad,
            ByteBufCodecs.VAR_INT, EmergencyEvaPayload::wornTicks,
            ByteBufCodecs.VAR_INT, EmergencyEvaPayload::thermalLoad, EmergencyEvaPayload::new);
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
