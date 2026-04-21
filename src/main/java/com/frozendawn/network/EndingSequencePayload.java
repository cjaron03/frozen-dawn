package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EndingSequencePayload(
        boolean conspiracyDiscovered,
        int daysSurvived,
        int terminalsHacked,
        int mobsKilled,
        boolean showComplianceLine,
        float lowestTemperatureSurvived,
        int phasesWitnessedMask,
        long blocksFrozen,
        int structuresDiscovered,
        int orsaDocumentsRead,
        int frostbittenKilled,
        int frostmiteKilled,
        int returnedKilled,
        int mimicKilled,
        int hollowKilled,
        int architectDefeats,
        int otherMobsKilled,
        int architectBreaches,
        int heatersLit,
        int fuelBurnedTicks,
        int nightsUnderground,
        float lowestHealth,
        int architectObserved,
        int architectRetreatedToHeal,
        int architectWallBreaches,
        boolean architectKilled,
        int rocketComponentsCrafted,
        int fuelCellsProcessed,
        int daysBetweenSatelliteAndLaunch,
        float lastTemperatureBeforeLaunch,
        int finalPhaseAtLaunch,
        int playTimeTicks)
        implements CustomPacketPayload {

    public static final Type<EndingSequencePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ending_sequence"));

    public static final StreamCodec<ByteBuf, EndingSequencePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EndingSequencePayload decode(ByteBuf buf) {
            boolean conspiracyDiscovered = ByteBufCodecs.BOOL.decode(buf);
            int daysSurvived = ByteBufCodecs.VAR_INT.decode(buf);
            int terminalsHacked = ByteBufCodecs.VAR_INT.decode(buf);
            int mobsKilled = ByteBufCodecs.VAR_INT.decode(buf);
            boolean showComplianceLine = ByteBufCodecs.BOOL.decode(buf);
            float lowestTemperatureSurvived = ByteBufCodecs.FLOAT.decode(buf);
            int phasesWitnessedMask = ByteBufCodecs.VAR_INT.decode(buf);
            long blocksFrozen = ByteBufCodecs.VAR_LONG.decode(buf);
            int structuresDiscovered = ByteBufCodecs.VAR_INT.decode(buf);
            int orsaDocumentsRead = ByteBufCodecs.VAR_INT.decode(buf);
            int frostbittenKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int frostmiteKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int returnedKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int mimicKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int hollowKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int architectDefeats = ByteBufCodecs.VAR_INT.decode(buf);
            int otherMobsKilled = ByteBufCodecs.VAR_INT.decode(buf);
            int architectBreaches = ByteBufCodecs.VAR_INT.decode(buf);
            int heatersLit = ByteBufCodecs.VAR_INT.decode(buf);
            int fuelBurnedTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int nightsUnderground = ByteBufCodecs.VAR_INT.decode(buf);
            float lowestHealth = ByteBufCodecs.FLOAT.decode(buf);
            int architectObserved = ByteBufCodecs.VAR_INT.decode(buf);
            int architectRetreatedToHeal = ByteBufCodecs.VAR_INT.decode(buf);
            int architectWallBreaches = ByteBufCodecs.VAR_INT.decode(buf);
            boolean architectKilled = ByteBufCodecs.BOOL.decode(buf);
            int rocketComponentsCrafted = ByteBufCodecs.VAR_INT.decode(buf);
            int fuelCellsProcessed = ByteBufCodecs.VAR_INT.decode(buf);
            int daysBetweenSatelliteAndLaunch = ByteBufCodecs.VAR_INT.decode(buf);
            float lastTemperatureBeforeLaunch = ByteBufCodecs.FLOAT.decode(buf);
            int finalPhaseAtLaunch = ByteBufCodecs.VAR_INT.decode(buf);
            int playTimeTicks = ByteBufCodecs.VAR_INT.decode(buf);
            return new EndingSequencePayload(
                    conspiracyDiscovered,
                    daysSurvived,
                    terminalsHacked,
                    mobsKilled,
                    showComplianceLine,
                    lowestTemperatureSurvived,
                    phasesWitnessedMask,
                    blocksFrozen,
                    structuresDiscovered,
                    orsaDocumentsRead,
                    frostbittenKilled,
                    frostmiteKilled,
                    returnedKilled,
                    mimicKilled,
                    hollowKilled,
                    architectDefeats,
                    otherMobsKilled,
                    architectBreaches,
                    heatersLit,
                    fuelBurnedTicks,
                    nightsUnderground,
                    lowestHealth,
                    architectObserved,
                    architectRetreatedToHeal,
                    architectWallBreaches,
                    architectKilled,
                    rocketComponentsCrafted,
                    fuelCellsProcessed,
                    daysBetweenSatelliteAndLaunch,
                    lastTemperatureBeforeLaunch,
                    finalPhaseAtLaunch,
                    playTimeTicks);
        }

        @Override
        public void encode(ByteBuf buf, EndingSequencePayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.conspiracyDiscovered());
            ByteBufCodecs.VAR_INT.encode(buf, payload.daysSurvived());
            ByteBufCodecs.VAR_INT.encode(buf, payload.terminalsHacked());
            ByteBufCodecs.VAR_INT.encode(buf, payload.mobsKilled());
            ByteBufCodecs.BOOL.encode(buf, payload.showComplianceLine());
            ByteBufCodecs.FLOAT.encode(buf, payload.lowestTemperatureSurvived());
            ByteBufCodecs.VAR_INT.encode(buf, payload.phasesWitnessedMask());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.blocksFrozen());
            ByteBufCodecs.VAR_INT.encode(buf, payload.structuresDiscovered());
            ByteBufCodecs.VAR_INT.encode(buf, payload.orsaDocumentsRead());
            ByteBufCodecs.VAR_INT.encode(buf, payload.frostbittenKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.frostmiteKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.returnedKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.mimicKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.hollowKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.architectDefeats());
            ByteBufCodecs.VAR_INT.encode(buf, payload.otherMobsKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.architectBreaches());
            ByteBufCodecs.VAR_INT.encode(buf, payload.heatersLit());
            ByteBufCodecs.VAR_INT.encode(buf, payload.fuelBurnedTicks());
            ByteBufCodecs.VAR_INT.encode(buf, payload.nightsUnderground());
            ByteBufCodecs.FLOAT.encode(buf, payload.lowestHealth());
            ByteBufCodecs.VAR_INT.encode(buf, payload.architectObserved());
            ByteBufCodecs.VAR_INT.encode(buf, payload.architectRetreatedToHeal());
            ByteBufCodecs.VAR_INT.encode(buf, payload.architectWallBreaches());
            ByteBufCodecs.BOOL.encode(buf, payload.architectKilled());
            ByteBufCodecs.VAR_INT.encode(buf, payload.rocketComponentsCrafted());
            ByteBufCodecs.VAR_INT.encode(buf, payload.fuelCellsProcessed());
            ByteBufCodecs.VAR_INT.encode(buf, payload.daysBetweenSatelliteAndLaunch());
            ByteBufCodecs.FLOAT.encode(buf, payload.lastTemperatureBeforeLaunch());
            ByteBufCodecs.VAR_INT.encode(buf, payload.finalPhaseAtLaunch());
            ByteBufCodecs.VAR_INT.encode(buf, payload.playTimeTicks());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
