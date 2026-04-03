package com.frozendawn.client;

import com.frozendawn.event.MobFreezeHandler;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Resolves the player's current air-survival state without implying a deeper
 * atmospheric simulation than the game currently models.
 */
public final class AirStatusTelemetry {

    private AirStatusTelemetry() {
    }

    public enum State {
        BREATHABLE("BREATHABLE", 0xFF4C968C, 0xFFD5EFE9, 0xFF82C8BC),
        EVA_SUPPLY("EVA SUPPLY", 0xFF4C92B8, 0xFFD9F0FF, 0xFF88CBEA),
        VACUUM("VACUUM", 0xFFC85757, 0xFFFFD7D7, 0xFFE38F8F);

        private final String label;
        private final int accentColor;
        private final int textColor;
        private final int badgeColor;

        State(String label, int accentColor, int textColor, int badgeColor) {
            this.label = label;
            this.accentColor = accentColor;
            this.textColor = textColor;
            this.badgeColor = badgeColor;
        }

        public String label() {
            return label;
        }

        public int accentColor() {
            return accentColor;
        }

        public int textColor() {
            return textColor;
        }

        public int badgeColor() {
            return badgeColor;
        }
    }

    public record TankTelemetry(int totalO2, int maxO2, int bestTier) {
        public boolean hasAnyTank() {
            return bestTier > 0 && maxO2 > 0;
        }

        public boolean hasUsableO2() {
            return totalO2 > 0;
        }

        public float fillRatio() {
            if (maxO2 <= 0) {
                return 0.0F;
            }
            return Mth.clamp((float) totalO2 / (float) maxO2, 0.0F, 1.0F);
        }

        public int fillPercent() {
            return Math.round(fillRatio() * 100.0F);
        }
    }

    public record Reading(State state, TankTelemetry tankTelemetry) {
    }

    @Nullable
    public static State resolve(@Nullable Player player) {
        Reading reading = resolveReading(player);
        return reading != null ? reading.state() : null;
    }

    @Nullable
    public static Reading resolveReading(@Nullable Player player) {
        if (!shouldRenderFor(player)) {
            return null;
        }

        TankTelemetry tankTelemetry = getTankTelemetry(player);
        State state;
        if (!PhaseManager.isVacuumActive(ApocalypseClientData.getPhase(), ApocalypseClientData.getProgress())) {
            state = State.BREATHABLE;
        } else if (ApocalypseClientData.isBreathable()) {
            state = State.BREATHABLE;
        } else {
            state = tankTelemetry.hasUsableO2() ? State.EVA_SUPPLY : State.VACUUM;
        }

        return new Reading(state, tankTelemetry);
    }

    public static boolean shouldRenderFor(@Nullable Player player) {
        return player != null
                && !player.isCreative()
                && !player.isSpectator()
                && player.level().dimension() == Level.OVERWORLD
                && MobFreezeHandler.getFullSetTier(player) == 3;
    }

    public static TankTelemetry getTankTelemetry(Player player) {
        int totalO2 = 0;
        int totalMaxO2 = 0;
        int bestTier = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem tankItem) {
                totalO2 += stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
                totalMaxO2 += tankItem.getMaxO2();
                bestTier = Math.max(bestTier, tankItem.getTier());
            }
        }
        return new TankTelemetry(totalO2, totalMaxO2, bestTier);
    }

    public static boolean hasUsableO2Tank(Player player) {
        return getTankTelemetry(player).hasUsableO2();
    }
}
