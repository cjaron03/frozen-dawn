package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Silent device status: client selects the helmet HUD or action bar. */
public record AirlockStatusPayload(Status status, int reserve, int air, int capacity, int percent, int amount)
        implements CustomPacketPayload {
    public enum Status {
        DIFFERENTIAL, INVALID, CLOSE_DOORS, INSUFFICIENT, PRESSURIZING, DEPRESSURIZING,
        CYCLING, STATUS, CHARGED, VENTED, EVACUATED, READY, INTERRUPTED;
        public String key() { return "message.frozendawn.airlock." + name().toLowerCase(java.util.Locale.ROOT); }
        public boolean warning() { return this == DIFFERENTIAL || this == INVALID || this == CLOSE_DOORS
                || this == INSUFFICIENT || this == VENTED || this == INTERRUPTED; }
    }
    public AirlockStatusPayload {
        if (status == null || reserve < 0 || reserve > 6400 || capacity < 0 || capacity > 3200
                || air < 0 || air > capacity || percent < 0 || percent > 100 || amount < 0)
            throw new IllegalArgumentException("Invalid airlock status");
    }
    public static final Type<AirlockStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"airlock_status"));
    private static final StreamCodec<ByteBuf,Status> STATUS = ByteBufCodecs.VAR_INT.map(value -> {
        if (value < 0 || value >= Status.values().length) throw new IllegalArgumentException("Invalid airlock status code");
        return Status.values()[value];
    }, Status::ordinal);
    public static final StreamCodec<ByteBuf,AirlockStatusPayload> STREAM_CODEC = StreamCodec.composite(
            STATUS,AirlockStatusPayload::status,
            ByteBufCodecs.VAR_INT,AirlockStatusPayload::reserve,
            ByteBufCodecs.VAR_INT,AirlockStatusPayload::air,
            ByteBufCodecs.VAR_INT,AirlockStatusPayload::capacity,
            ByteBufCodecs.VAR_INT,AirlockStatusPayload::percent,
            ByteBufCodecs.VAR_INT,AirlockStatusPayload::amount,AirlockStatusPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
