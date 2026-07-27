package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.SuitIntegrity;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.item.O2EfficiencyModuleItem;
import com.frozendawn.item.SuitPatchItem;
import com.frozendawn.network.SuitIntegrityPayload;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.world.TemperatureManager;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns EVA puncture rolls, O2 venting, repair state, and client synchronization. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class SuitIntegrityHandler {

    public enum EmergencyRefillResult {
        SUCCESS,
        NO_SEALED_SUIT,
        NO_CAPACITY,
        FULL
    }

    public static final TagKey<DamageType> PHYSICAL_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "suit_puncture_physical"));

    private static final int SYNC_INTERVAL_TICKS = 5;

    private SuitIntegrityHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        interruptPatchOnExternalDamage(player, source);

        if (!isVacuumExposure(player)
                || !isWearingSealedSuit(player)
                || !isPunctureEligible(source)) {
            return;
        }

        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        if (!SuitIntegrityPolicy.canPuncture(
                state.punctures(),
                state.graceTicks(),
                FrozenDawnConfig.SUIT_PUNCTURE_MAX_CONCURRENT.get())) {
            return;
        }

        SuitIntegrityPolicy.SourceKind kind = classifySource(source);
        float chance = SuitIntegrityPolicy.chance(
                kind,
                player.fallDistance,
                FrozenDawnConfig.SUIT_PUNCTURE_MASTER_CHANCE.get().floatValue(),
                FrozenDawnConfig.SUIT_PUNCTURE_ARCHITECT_CHANCE.get().floatValue(),
                FrozenDawnConfig.SUIT_PUNCTURE_MIMIC_AMBUSH_CHANCE.get().floatValue(),
                FrozenDawnConfig.SUIT_PUNCTURE_PHYSICAL_CHANCE.get().floatValue(),
                FrozenDawnConfig.SUIT_PUNCTURE_FALL_CHANCE_PER_BLOCK.get().floatValue());
        if (player.getRandom().nextFloat() >= chance) {
            return;
        }

        state.setPunctures(state.punctures() + 1);
        state.setGraceTicks(FrozenDawnConfig.SUIT_PUNCTURE_GRACE_TICKS.get());
        state.setO2Ticks(getTotalO2(player));
        FrozenDawn.LOGGER.info(
                "[SuitIntegrity] {} punctured by {} (chance={}, punctures={})",
                player.getGameProfile().getName(),
                kind,
                String.format("%.2f", chance),
                state.punctures());
        sync(player, state, SuitIntegrityPayload.PUNCTURED);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        if (state.graceTicks() > 0) {
            state.setGraceTicks(state.graceTicks() - 1);
        }

        boolean changed = updatePatchProgress(player, state);
        changed |= tickTemporarySeal(player, state);

        int beforeO2 = getTotalO2(player);
        int maxO2 = getTotalMaxO2(player);
        boolean sealed = isWearingSealedSuit(player);
        boolean vacuum = isVacuumExposure(player);
        if (sealed && vacuum && state.punctures() > 0 && beforeO2 > 0) {
            state.setVentAccumulator(
                    state.ventAccumulator() + SuitIntegrityPolicy.ventPerTick(
                    maxO2,
                    FrozenDawnConfig.SUIT_PUNCTURE_VENT_SECONDS.get(),
                    state.punctures())
                            * O2EfficiencyModuleItem.consumptionMultiplier(player));
            int vent = (int) Math.floor(state.ventAccumulator());
            if (vent > 0) {
                consumeO2(player, vent);
                state.setVentAccumulator(state.ventAccumulator() - vent);
            }
        } else if (sealed
                && !vacuum
                && state.punctures() == 0
                && TemperatureManager.hasOxygenSupport(
                        player.level(), player.blockPosition())
                && FrozenDawnConfig.SUIT_REPRESSURIZE_PER_TICK.get() > 0) {
            refillO2(player, FrozenDawnConfig.SUIT_REPRESSURIZE_PER_TICK.get());
            state.setVentAccumulator(0.0D);
        } else if (!vacuum || state.punctures() <= 0) {
            state.setVentAccumulator(0.0D);
        }

        int currentO2 = getTotalO2(player);
        state.setO2Ticks(currentO2);
        int eventId = thresholdEvent(state, currentO2, maxO2);
        changed |= beforeO2 != currentO2;

        if (eventId != SuitIntegrityPayload.NONE
                || (changed && player.tickCount % SYNC_INTERVAL_TICKS == 0)
                || (state.patchTicks() >= 0
                        && player.tickCount % SYNC_INTERVAL_TICKS == 0)) {
            sync(player, state, eventId);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
            state.setO2Ticks(getTotalO2(player));
            sync(player, state, SuitIntegrityPayload.NONE);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().removeData(ModAttachments.SUIT_INTEGRITY);
        }
    }

    public static boolean completePatch(ServerPlayer player, boolean permanent) {
        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        if (state.punctures() <= 0 || !isWearingSealedSuit(player)) {
            state.setPatchTicks(-1);
            sync(player, state, SuitIntegrityPayload.NONE);
            return false;
        }

        state.setPunctures(state.punctures() - 1);
        state.setPatchTicks(-1);
        state.clearWarnings();
        if (!permanent
                && ThreadLocalRandom.current().nextDouble()
                        < FrozenDawnConfig.IMPROVISED_PATCH_DEGRADE_CHANCE.get()) {
            state.setTemporarySeals(state.temporarySeals() + 1);
            if (state.temporarySeals() == 1) {
                state.setTemporarySealTicks(randomTemporarySealTicks());
            }
        }
        sync(player, state, SuitIntegrityPayload.PATCHED);
        return true;
    }

    public static void setPunctures(ServerPlayer player, int punctures) {
        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        state.setPunctures(Math.min(
                Math.max(0, punctures),
                FrozenDawnConfig.SUIT_PUNCTURE_MAX_CONCURRENT.get()));
        state.setGraceTicks(0);
        state.clearWarnings();
        state.setO2Ticks(getTotalO2(player));
        sync(player, state, state.punctures() > 0
                ? SuitIntegrityPayload.PUNCTURED
                : SuitIntegrityPayload.PATCHED);
    }

    public static SuitIntegrity getState(Player player) {
        return player.getData(ModAttachments.SUIT_INTEGRITY);
    }

    public static boolean hasActiveEmptyPuncture(ServerPlayer player) {
        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        return state.punctures() > 0
                && isWearingSealedSuit(player)
                && isVacuumExposure(player)
                && getTotalO2(player) <= 0;
    }

    public static boolean hasPuncture(Player player) {
        return player.getData(ModAttachments.SUIT_INTEGRITY).punctures() > 0;
    }

    public static EmergencyRefillResult useEmergencyO2Cartridge(ServerPlayer player) {
        if (!isWearingSealedSuit(player)) {
            return EmergencyRefillResult.NO_SEALED_SUIT;
        }
        int maxO2 = getTotalMaxO2(player);
        if (maxO2 <= 0) {
            return EmergencyRefillResult.NO_CAPACITY;
        }
        int beforeO2 = getTotalO2(player);
        if (beforeO2 >= maxO2) {
            return EmergencyRefillResult.FULL;
        }

        refillO2(player, SuitIntegrityPolicy.emergencyRefillAmount(maxO2));
        SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
        state.setO2Ticks(getTotalO2(player));
        state.clearWarnings();
        sync(player, state, SuitIntegrityPayload.EMERGENCY_RESERVE);
        return EmergencyRefillResult.SUCCESS;
    }

    public static boolean isWearingSealedSuit(Player player) {
        return MobFreezeHandler.getFullSetTier(player) == 3;
    }

    public static boolean isVacuumExposure(ServerPlayer player) {
        ApocalypseState apocalypse = ApocalypseState.get(player.getServer());
        return PhaseManager.isVacuumActive(
                        apocalypse.getPhase(), apocalypse.getProgress())
                && !PlayerTickHandler.isPlayerBreathable(player);
    }

    private static boolean isPunctureEligible(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker instanceof ArchitectEntity
                || attacker instanceof MimicEntity
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(PHYSICAL_DAMAGE);
    }

    private static SuitIntegrityPolicy.SourceKind classifySource(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL)) {
            return SuitIntegrityPolicy.SourceKind.FALL;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ArchitectEntity architect) {
            return architect.isMasterArchitectVisual()
                    ? SuitIntegrityPolicy.SourceKind.MASTER_ARCHITECT
                    : SuitIntegrityPolicy.SourceKind.ARCHITECT_HEAVY;
        }
        if (attacker instanceof MimicEntity mimic) {
            return mimic.hasLandedFirstHit()
                    ? SuitIntegrityPolicy.SourceKind.MIMIC_PHYSICAL
                    : SuitIntegrityPolicy.SourceKind.MIMIC_AMBUSH;
        }
        return SuitIntegrityPolicy.SourceKind.ORDINARY_PHYSICAL;
    }

    private static void interruptPatchOnExternalDamage(
            ServerPlayer player, DamageSource source) {
        if (player.isUsingItem()
                && player.getUseItem().getItem() instanceof SuitPatchItem
                && !source.is(ModDamageTypes.ATMOSPHERIC_SUFFOCATION)) {
            player.stopUsingItem();
            SuitIntegrity state = player.getData(ModAttachments.SUIT_INTEGRITY);
            state.setPatchTicks(-1);
            sync(player, state, SuitIntegrityPayload.NONE);
        }
    }

    private static boolean updatePatchProgress(ServerPlayer player, SuitIntegrity state) {
        int previousTicks = state.patchTicks();
        if (player.isUsingItem()
                && player.getUseItem().getItem() instanceof SuitPatchItem) {
            state.setPatchTicks(player.getTicksUsingItem());
        } else {
            state.setPatchTicks(-1);
        }
        return previousTicks != state.patchTicks();
    }

    private static boolean tickTemporarySeal(ServerPlayer player, SuitIntegrity state) {
        if (state.temporarySeals() <= 0
                || state.temporarySealTicks() <= 0
                || !isWearingSealedSuit(player)
                || !isVacuumExposure(player)) {
            return false;
        }
        state.setTemporarySealTicks(state.temporarySealTicks() - 1);
        if (state.temporarySealTicks() > 0) {
            return false;
        }
        state.setTemporarySeals(state.temporarySeals() - 1);
        if (state.temporarySeals() > 0) {
            state.setTemporarySealTicks(randomTemporarySealTicks());
        }
        if (state.punctures() < FrozenDawnConfig.SUIT_PUNCTURE_MAX_CONCURRENT.get()) {
            state.setPunctures(state.punctures() + 1);
            state.setGraceTicks(FrozenDawnConfig.SUIT_PUNCTURE_GRACE_TICKS.get());
            sync(player, state, SuitIntegrityPayload.PATCH_DEGRADED);
        }
        return true;
    }

    private static int randomTemporarySealTicks() {
        int min = FrozenDawnConfig.IMPROVISED_PATCH_MIN_SEAL_SECONDS.get();
        int max = Math.max(min, FrozenDawnConfig.IMPROVISED_PATCH_MAX_SEAL_SECONDS.get());
        return ThreadLocalRandom.current().nextInt(min, max + 1) * 20;
    }

    private static int thresholdEvent(SuitIntegrity state, int current, int max) {
        if (state.punctures() <= 0 || max <= 0) {
            return SuitIntegrityPayload.NONE;
        }
        float currentRatio = current / (float) max;
        if (!state.warned25() && currentRatio <= 0.25F) {
            state.setWarned25(true);
        }
        if (!state.warned10() && currentRatio <= 0.10F) {
            state.setWarned10(true);
            return SuitIntegrityPayload.OXYGEN_CRITICAL;
        }
        return SuitIntegrityPayload.NONE;
    }

    private static void sync(
            ServerPlayer player, SuitIntegrity state, int eventId) {
        int patchDuration = 0;
        if (player.getUseItem().getItem() instanceof SuitPatchItem patch) {
            patchDuration = patch.getUseDuration(player.getUseItem(), player);
        }
        PacketDistributor.sendToPlayer(
                player,
                new SuitIntegrityPayload(
                        state.punctures(),
                        getTotalO2(player),
                        getTotalMaxO2(player),
                        state.patchTicks(),
                        patchDuration,
                        eventId));
    }

    private static int getTotalO2(Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem) {
                total += stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            }
        }
        return total;
    }

    private static int getTotalMaxO2(Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem tank) {
                total += tank.getMaxO2();
            }
        }
        return total;
    }

    private static void consumeO2(Player player, int amount) {
        int remaining = Math.max(0, amount);
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof O2TankItem)) {
                continue;
            }
            int stored = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            int consumed = Math.min(stored, remaining);
            if (consumed > 0) {
                stack.set(ModDataComponents.O2_LEVEL.get(), stored - consumed);
                remaining -= consumed;
            }
        }
    }

    private static void refillO2(Player player, int amount) {
        int remaining = Math.max(0, amount);
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof O2TankItem tank)) {
                continue;
            }
            int stored = stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            int added = Math.min(tank.getMaxO2() - stored, remaining);
            if (added > 0) {
                stack.set(ModDataComponents.O2_LEVEL.get(), stored + added);
                remaining -= added;
            }
        }
    }
}
