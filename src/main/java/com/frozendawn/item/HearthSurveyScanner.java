package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.HearthSurveyPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads world-level Hearth records without loading or querying their chunks.
 */
public final class HearthSurveyScanner {
    private HearthSurveyScanner() {
    }

    public static Optional<HearthSignal> scan(ServerPlayer player,
                                              SurveyorLensScanner.LensProfile lensProfile) {
        if (PostMaeveWorldState.isErased(player.serverLevel())) {
            return Optional.empty();
        }
        HearthSurveyPolicy.SignalProfile signalProfile = signalProfile(lensProfile);
        if (signalProfile == null) {
            return Optional.empty();
        }

        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(player.serverLevel().getServer());
        SignalCandidate candidate = findCandidate(player, state, signalProfile).orElse(null);
        if (candidate == null) {
            return Optional.empty();
        }

        ReturnedHearthSavedData.HearthRecord hearth = candidate.hearth();
        HearthSurveyPolicy.SignalBand band = HearthSurveyPolicy.bandFor(
                candidate.distance(), signalProfile, hearth.discovered());
        boolean shouldCatalogue = band == HearthSurveyPolicy.SignalBand.LOCK;
        ReturnedHearthSavedData.DiscoveryResult result = state.recordSurveyObservation(
                hearth.id(), candidate.intrinsicStrength(), shouldCatalogue);

        if (result.newlyDiscovered()) {
            FrozenDawn.LOGGER.info(
                    "Player {} catalogued {} Hearth {} from {} blocks",
                    player.getGameProfile().getName(),
                    hearth.type().name().toLowerCase(Locale.ROOT),
                    hearth.id().toString().substring(0, 8),
                    Mth.floor(candidate.distance()));
        }

        return Optional.of(toSignal(player, candidate, band,
                result.discovered(), result.newlyDiscovered()));
    }

    public static Optional<HearthSignal> sample(ServerPlayer player,
                                                SurveyorLensScanner.LensProfile lensProfile) {
        if (PostMaeveWorldState.isErased(player.serverLevel())) {
            return Optional.empty();
        }
        HearthSurveyPolicy.SignalProfile signalProfile = signalProfile(lensProfile);
        if (signalProfile == null) {
            return Optional.empty();
        }

        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(player.serverLevel().getServer());
        return findCandidate(player, state, signalProfile).map(candidate -> {
            boolean discovered = candidate.hearth().discovered();
            HearthSurveyPolicy.SignalBand band = HearthSurveyPolicy.bandFor(
                    candidate.distance(), signalProfile, discovered);
            return toSignal(player, candidate, band, discovered, false);
        });
    }

    private static Optional<SignalCandidate> findCandidate(
            ServerPlayer player,
            ReturnedHearthSavedData state,
            HearthSurveyPolicy.SignalProfile signalProfile) {
        return state.hearths().stream()
                .filter(hearth -> HearthSurveyPolicy.emitsSignal(hearth.stage()))
                .map(hearth -> candidate(player, hearth, signalProfile))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparing((SignalCandidate value) -> value.hearth().discovered())
                        .thenComparingDouble(SignalCandidate::priorityDistance));
    }

    private static Optional<SignalCandidate> candidate(
            ServerPlayer player,
            ReturnedHearthSavedData.HearthRecord hearth,
            HearthSurveyPolicy.SignalProfile profile) {
        double dx = hearth.center().getX() + 0.5D - player.getX();
        double dz = hearth.center().getZ() + 0.5D - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > profile.maximumRange()) {
            return Optional.empty();
        }

        float intrinsicStrength = HearthSurveyPolicy.intrinsicStrength(hearth.type(), hearth.stage());
        float observedStrength = HearthSurveyPolicy.observedStrength(intrinsicStrength, distance, profile);
        if (observedStrength <= 0.0F) {
            return Optional.empty();
        }

        double priorityDistance = distance / Math.max(0.1F, intrinsicStrength);
        return Optional.of(new SignalCandidate(
                hearth, distance, intrinsicStrength, observedStrength,
                HearthSurveyPolicy.proximity(distance, profile), priorityDistance));
    }

    private static HearthSignal toSignal(
            ServerPlayer player,
            SignalCandidate candidate,
            HearthSurveyPolicy.SignalBand band,
            boolean discovered,
            boolean newlyDiscovered) {
        ReturnedHearthSavedData.HearthRecord hearth = candidate.hearth();
        return new HearthSignal(
                hearth.id(),
                hearth.type(),
                hearth.stage(),
                hearth.violationState(),
                band,
                Mth.floor(candidate.distance()),
                describeDirection(player.getX(), player.getZ(), hearth.center()),
                candidate.observedStrength(),
                candidate.proximity(),
                discovered,
                newlyDiscovered
        );
    }

    private static HearthSurveyPolicy.SignalProfile signalProfile(
            SurveyorLensScanner.LensProfile lensProfile) {
        return switch (lensProfile) {
            case STANDARD -> HearthSurveyPolicy.STANDARD;
            case CALIBRATED -> HearthSurveyPolicy.CALIBRATED;
            case VISOR -> null;
        };
    }

    private static String describeDirection(double originX, double originZ, BlockPos target) {
        double dx = target.getX() + 0.5D - originX;
        double dz = target.getZ() + 0.5D - originZ;
        if (dx * dx + dz * dz < 16.0D * 16.0D) {
            return "nearby";
        }

        String[] directions = {
                "east", "southeast", "south", "southwest",
                "west", "northwest", "north", "northeast"
        };
        double octant = Math.atan2(dz, dx) / (Math.PI / 4.0D);
        int index = Math.floorMod((int) Math.round(octant), directions.length);
        return directions[index];
    }

    private record SignalCandidate(ReturnedHearthSavedData.HearthRecord hearth,
                                   double distance, float intrinsicStrength,
                                   float observedStrength, float proximity,
                                   double priorityDistance) {
    }

    public record HearthSignal(
            java.util.UUID hearthId,
            HearthSelectionPolicy.HearthType hearthType,
            ReturnedHearthSavedData.HearthStage stage,
            ReturnedHearthSavedData.ViolationState violationState,
            HearthSurveyPolicy.SignalBand band,
            int distanceBlocks,
            String direction,
            float observedStrength,
            float proximity,
            boolean discovered,
            boolean newlyDiscovered
    ) {
        public boolean hostile() {
            return violationState == ReturnedHearthSavedData.ViolationState.VIOLATED;
        }

        public boolean suspicious() {
            return violationState == ReturnedHearthSavedData.ViolationState.SUSPICIOUS;
        }
    }
}
