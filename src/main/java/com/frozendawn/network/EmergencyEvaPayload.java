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
public record EmergencyEvaPayload(UUID issue, int remainingTicks, int oxygenTicks, int exertionLoad,
                                 int wornTicks, int thermalLoad, boolean ambient, int retirement,
                                 int notice) implements CustomPacketPayload {
    public static final int NO_NOTICE = 0, AMBIENT_NOTICE = 1, HANDOFF_NOTICE = 2, EXPIRED_NOTICE = 3;
    public static final Type<EmergencyEvaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "emergency_eva"));
    public static final StreamCodec<ByteBuf, EmergencyEvaPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public EmergencyEvaPayload decode(ByteBuf buffer) {
            return new EmergencyEvaPayload(UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
        }
        @Override public void encode(ByteBuf buffer, EmergencyEvaPayload payload) {
            UUIDUtil.STREAM_CODEC.encode(buffer, payload.issue());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.remainingTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.oxygenTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.exertionLoad());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.wornTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.thermalLoad());
            ByteBufCodecs.BOOL.encode(buffer, payload.ambient());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.retirement());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.notice());
        }
    };
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
