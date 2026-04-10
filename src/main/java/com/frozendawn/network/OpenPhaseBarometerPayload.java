package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.barometer.BarometerWarning;
import com.frozendawn.barometer.ForecastBand;
import com.frozendawn.barometer.PhaseBarometerForecasts;
import com.frozendawn.barometer.PhaseBarometerSnapshot;
import com.frozendawn.barometer.UpcomingState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPhaseBarometerPayload(
        BlockPos pos,
        int currentPhase,
        int forecastBand,
        int upcomingState,
        int warning,
        float severity
) implements CustomPacketPayload {

    public static final Type<OpenPhaseBarometerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_phase_barometer"));

    public static final StreamCodec<ByteBuf, OpenPhaseBarometerPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OpenPhaseBarometerPayload decode(ByteBuf buffer) {
            return new OpenPhaseBarometerPayload(
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    buffer.readFloat()
            );
        }

        @Override
        public void encode(ByteBuf buffer, OpenPhaseBarometerPayload value) {
            BlockPos.STREAM_CODEC.encode(buffer, value.pos());
            ByteBufCodecs.VAR_INT.encode(buffer, value.currentPhase());
            ByteBufCodecs.VAR_INT.encode(buffer, value.forecastBand());
            ByteBufCodecs.VAR_INT.encode(buffer, value.upcomingState());
            ByteBufCodecs.VAR_INT.encode(buffer, value.warning());
            buffer.writeFloat(value.severity());
        }
    };

    public static OpenPhaseBarometerPayload fromSnapshot(BlockPos pos, PhaseBarometerSnapshot snapshot) {
        return new OpenPhaseBarometerPayload(
                pos,
                snapshot.currentPhase(),
                snapshot.forecastBand().ordinal(),
                snapshot.upcomingState().ordinal(),
                snapshot.warning().ordinal(),
                snapshot.severity()
        );
    }

    public PhaseBarometerSnapshot toSnapshot() {
        ForecastBand[] bands = ForecastBand.values();
        UpcomingState[] states = UpcomingState.values();
        BarometerWarning[] warnings = BarometerWarning.values();
        ForecastBand resolvedBand = bands[Math.max(0, Math.min(bands.length - 1, forecastBand))];
        UpcomingState resolvedUpcoming = states[Math.max(0, Math.min(states.length - 1, upcomingState))];
        BarometerWarning resolvedWarning = warnings[Math.max(0, Math.min(warnings.length - 1, warning))];
        return new PhaseBarometerSnapshot(
                currentPhase,
                PhaseBarometerForecasts.phaseName(currentPhase),
                resolvedBand,
                resolvedUpcoming,
                resolvedWarning,
                severity
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
