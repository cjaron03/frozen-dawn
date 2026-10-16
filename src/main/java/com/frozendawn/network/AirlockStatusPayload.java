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
        CYCLING, STATUS, CHARGED, VENTED, EVACUATED, READY, INTERRUPTED, WAITING_FOR_POWER,
        WAITING_TO_FILL, WAITING_TO_VENT, INNER_DIFFERENTIAL, CHAMBER_VACUUM, BASE_VACUUM,
        VACUUM_DIFFERENCE, PRESSURE_UNKNOWN, CYCLE_LOCKED, VALVE_INNER_ONLY, BASE_UNSEALED,
        VALVE_EQUALIZED, DOOR_OPEN_VACUUM, DOOR_OPEN_MATCHED;
        public boolean opensDoor() { return this == DOOR_OPEN_VACUUM || this == DOOR_OPEN_MATCHED; }
        public boolean success() { return opensDoor() || this == VALVE_EQUALIZED || this == READY; }
        public boolean doorNotice() {
            return switch(this) {
                case DIFFERENTIAL, CLOSE_DOORS, INNER_DIFFERENTIAL, CHAMBER_VACUUM, BASE_VACUUM,
                    VACUUM_DIFFERENCE, PRESSURE_UNKNOWN, CYCLE_LOCKED, VALVE_INNER_ONLY, BASE_UNSEALED,
                    VALVE_EQUALIZED, DOOR_OPEN_VACUUM, DOOR_OPEN_MATCHED -> true;
                default -> false;
            };
        }
        public String key() { return "message.frozendawn.airlock." + name().toLowerCase(java.util.Locale.ROOT); }
        public boolean warning() { return this == DIFFERENTIAL || this == INVALID || this == CLOSE_DOORS
                || this == WAITING_FOR_POWER || this == WAITING_TO_FILL || this == WAITING_TO_VENT || this == INSUFFICIENT || this == VENTED || this == INTERRUPTED
                || this == INNER_DIFFERENTIAL || this == CHAMBER_VACUUM || this == BASE_VACUUM
                || this == VACUUM_DIFFERENCE || this == PRESSURE_UNKNOWN || this == CYCLE_LOCKED
                || this == VALVE_INNER_ONLY || this == BASE_UNSEALED; }
    }
    public AirlockStatusPayload {
        if (status == null || reserve < 0 || reserve > 6400 || capacity < 0 || capacity > 3200
                || air < 0 || capacity == 0 && air != 0 || percent < 0 || percent > 100 || amount < 0)
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
