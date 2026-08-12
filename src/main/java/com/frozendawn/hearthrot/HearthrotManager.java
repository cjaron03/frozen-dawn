package com.frozendawn.hearthrot;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.HearthrotState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEffects;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.HearthrotPayload;
import com.frozendawn.network.HearthrotSalvationPayload;
import com.frozendawn.world.TemperatureManager;
import com.frozendawn.world.ResonanceEventManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for exterior colonization, infection, symptoms, and progression. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HearthrotManager {
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearthrot_max_health");
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearthrot_movement");
    private static final int LOGIC_INTERVAL_TICKS = 20;
    private static final int SYNC_INTERVAL_TICKS = 100;
    private static final int CONTAMINATION_WARNING_DELAY_TICKS = 50;

    private HearthrotManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        tickDelayedWarning(player, state);
        tickSalvationEpisode(player, state);
        ensureHearthrotEffect(player, state.stage());
        if (player.tickCount % LOGIC_INTERVAL_TICKS != 0) {
            return;
        }

        int previousColonization = effectiveColonization(player);
        updateRigColonization(player, state);
        int colonization = effectiveColonization(player);
        boolean stageChanged = tickDisease(player, state, colonization);
        applySymptoms(player, state.stage());
        tickCough(player, state);
        tickWheeze(player, state);

        if (stageChanged || previousColonization != colonization
                || player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            sync(player, state, stageChanged
                    ? HearthrotPayload.STAGE_ADVANCED : HearthrotPayload.NONE);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthrotState state = player.getData(ModAttachments.HEARTHROT);
            applySymptoms(player, state.stage());
            sync(player, state, HearthrotPayload.NONE);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthrotState state = player.getData(ModAttachments.HEARTHROT);
            applySymptoms(player, state.stage());
            sync(player, state, HearthrotPayload.DEATH_ROLLBACK);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || !(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer replacement)) {
            return;
        }
        HearthrotState oldState = original.getData(ModAttachments.HEARTHROT);
        HearthrotState newState = replacement.getData(ModAttachments.HEARTHROT);
        newState.copyAfterDeath(oldState);
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (stage(player) < 5) {
            return;
        }
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.frozendawn.hearthrot.sleep_refused"), true);
    }

    /** Called after the puncture state is created, preserving the warning order. */
    public static boolean onPuncture(ServerPlayer player) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        if (state.stage() > 0
                || !PostMaeveWorldState.isErased(player.getServer())
                || !SuitIntegrityHandler.isWearingSealedSuit(player)
                || !HearthrotPolicy.isInfectable(effectiveColonization(player))) {
            return false;
        }
        infect(player, state, false);
        return true;
    }

    public static void infectForDebug(ServerPlayer player) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        if (state.stage() <= 0) {
            infect(player, state, true);
        }
    }

    public static int stage(Player player) {
        return player.getData(ModAttachments.HEARTHROT).stage();
    }

    public static boolean isLateStage(Player player) {
        return stage(player) >= 4;
    }

    public static HearthrotState state(Player player) {
        return player.getData(ModAttachments.HEARTHROT);
    }

    public static float hiddenColdPenalty(Player player) {
        return HearthrotPolicy.hiddenColdPenalty(stage(player));
    }

    public static int foodFreezeMultiplier(Player player) {
        return HearthrotPolicy.foodFreezeMultiplier(stage(player));
    }

    public static int effectiveColonization(Player player) {
        int maximum = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            if (isColonizablePiece(stack)) {
                maximum = Math.max(maximum, stack.getOrDefault(
                        ModDataComponents.HEARTHROT_COLONIZATION.get(), 0));
            }
        }
        return Mth.clamp(maximum, 0, HearthrotPolicy.MAX_COLONIZATION);
    }

    public static int visualColonizationStage(Player player) {
        return HearthrotPolicy.visualStage(effectiveColonization(player));
    }

    public static boolean isColonizablePiece(ItemStack stack) {
        return stack.is(ModItems.EVA_HELMET.get())
                || stack.is(ModItems.EVA_CHESTPLATE.get())
                || stack.is(ModItems.EVA_LEGGINGS.get())
                || stack.is(ModItems.EVA_BOOTS.get())
                || stack.is(ModItems.LINED_EVA_CHESTPLATE.get())
                || stack.is(ModItems.ORSA_THERMAL_VISOR.get());
    }

    /** Returns baseline tank units to consume; puncture venting never calls this. */
    public static int baselineO2Units(ServerPlayer player) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        double multiplier = HearthrotPolicy.externalO2Multiplier(
                visualColonizationStage(player))
                * HearthrotPolicy.diseaseO2Multiplier(state.stage());
        state.setBaselineO2Remainder(state.baselineO2Remainder() + multiplier);
        int units = Math.max(1, (int) Math.floor(state.baselineO2Remainder()));
        state.setBaselineO2Remainder(state.baselineO2Remainder() - units);
        return units;
    }

    public static boolean improvisedPatchMustDegrade(Player player) {
        return stage(player) >= 5;
    }

    public static double temporarySealLifetimeMultiplier(Player player) {
        return HearthrotPolicy.temporarySealLifetimeMultiplier(
                visualColonizationStage(player));
    }

    public static void setStageForDebug(ServerPlayer player, int stage) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        state.setStage(stage);
        state.setProgressTicks(0.0D);
        state.setCoughTicks(nextCoughDelay(player, stage));
        state.setWheezeTicks(nextWheezeDelay(player, stage));
        if (stage > 0) {
            state.setTransitionMask(state.transitionMask() | (1 << stage));
        }
        applySymptoms(player, state.stage());
        sync(player, state, HearthrotPayload.STAGE_ADVANCED);
    }

    public static void setProgressForDebug(ServerPlayer player, int percent) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        int duration = stageDuration(player, state.stage());
        state.setProgressTicks(duration * Mth.clamp(percent, 0, 100) / 100.0D);
        sync(player, state, HearthrotPayload.NONE);
    }

    public static void setColonizationForDebug(ServerPlayer player, int colonization) {
        setEquippedColonization(player, Mth.clamp(
                colonization, 0, HearthrotPolicy.MAX_COLONIZATION));
        sync(player, player.getData(ModAttachments.HEARTHROT), HearthrotPayload.NONE);
    }

    public static void clearForDebug(ServerPlayer player, boolean clearColonization) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        state.clearForDebug();
        if (clearColonization) {
            for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                ItemStack stack = player.getInventory().getItem(index);
                if (isColonizablePiece(stack)) {
                    stack.remove(ModDataComponents.HEARTHROT_COLONIZATION.get());
                }
            }
        }
        removeSymptoms(player);
        sync(player, state, HearthrotPayload.NONE);
    }

    public static void coughForDebug(ServerPlayer player) {
        sync(player, player.getData(ModAttachments.HEARTHROT), HearthrotPayload.COUGH);
        attractRespiratoryThreats(player, 24.0D);
    }

    public static void wheezeForDebug(ServerPlayer player) {
        sync(player, player.getData(ModAttachments.HEARTHROT), HearthrotPayload.WHEEZE);
        attractRespiratoryThreats(player, 30.0D);
    }

    public static void breathCatchForDebug(ServerPlayer player) {
        sync(player, player.getData(ModAttachments.HEARTHROT), HearthrotPayload.BREATH_CATCH);
    }

    public static boolean resetSalvationForDebug(ServerPlayer player) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        resetStillness(state);
        return ReturnedHearthSavedData.get(player.getServer())
                .resetHearthrotSalvationForDebug();
    }

    public static boolean fireSalvationForDebug(ServerPlayer player) {
        ReturnedHearthSavedData world = ReturnedHearthSavedData.get(
                player.getServer());
        if (!world.markHearthrotSalvationFired()) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new HearthrotSalvationPayload());
        return true;
    }

    public static float progressRatio(ServerPlayer player) {
        HearthrotState state = player.getData(ModAttachments.HEARTHROT);
        int duration = stageDuration(player, state.stage());
        return duration <= 0 ? 1.0F : Mth.clamp(
                (float) (state.progressTicks() / duration), 0.0F, 1.0F);
    }

    private static void infect(
            ServerPlayer player, HearthrotState state, boolean debug) {
        state.setStage(1);
        state.setProgressTicks(0.0D);
        state.setTransitionMask(state.transitionMask() | (1 << 1));
        state.setCoughTicks(nextCoughDelay(player, 1));
        state.setWheezeTicks(0);
        if (!state.contaminationWarned()) {
            state.setContaminationWarningTicks(debug
                    ? 1 : CONTAMINATION_WARNING_DELAY_TICKS);
        }
        applySymptoms(player, 1);
        sync(player, state, HearthrotPayload.INFECTED);
    }

    private static void tickDelayedWarning(
            ServerPlayer player, HearthrotState state) {
        if (state.contaminationWarningTicks() < 0) {
            return;
        }
        if (state.contaminationWarningTicks() > 0) {
            state.setContaminationWarningTicks(
                    state.contaminationWarningTicks() - 1);
            return;
        }
        state.setContaminationWarningTicks(-1);
        state.setContaminationWarned(true);
        sync(player, state, HearthrotPayload.CONTAMINATION_WARNING);
    }

    private static void updateRigColonization(
            ServerPlayer player, HearthrotState state) {
        int current = effectiveColonization(player);
        boolean hasPiece = hasEquippedColonizablePiece(player);
        if (!hasPiece) {
            state.setColonizationRemainder(0.0D);
            return;
        }

        double delta = 0.0D;
        if (player.level() instanceof ServerLevel level) {
            ApocalypseState apocalypse = ApocalypseState.get(player.getServer());
            float temperature = TemperatureManager.getTemperatureAt(
                    level, player.blockPosition(), apocalypse.getCurrentDay(),
                    apocalypse.getTotalDays());
            float heat = TemperatureManager.getHeatSourceModifier(
                    level, player.blockPosition(), apocalypse.getCurrentDay(),
                    apocalypse.getTotalDays(), true);
            if (heat > 0.0F) {
                delta = -HearthrotPolicy.activeHeatCleaningPerTick()
                        * LOGIC_INTERVAL_TICKS;
            } else if (temperature > 10.0F
                    && TemperatureManager.isEnclosed(level, player.blockPosition())) {
                delta = -HearthrotPolicy.warmInteriorCleaningPerTick()
                        * LOGIC_INTERVAL_TICKS;
            } else if (PostMaeveWorldState.isErased(player.getServer())
                    && level.dimension() == Level.OVERWORLD) {
                int band = BloomGrowthManager.localBandOrdinal(
                        level, player.blockPosition());
                if (band >= 0) {
                    delta = HearthrotPolicy.coreColonizationPerTick()
                            * HearthrotPolicy.exposureMultiplier(band)
                            * LOGIC_INTERVAL_TICKS;
                }
            }
        }

        double accumulated = state.colonizationRemainder() + delta;
        int whole = accumulated >= 0.0D
                ? (int) Math.floor(accumulated) : (int) Math.ceil(accumulated);
        state.setColonizationRemainder(accumulated - whole);
        if (whole != 0) {
            setEquippedColonization(player, Mth.clamp(
                    current + whole, 0, HearthrotPolicy.MAX_COLONIZATION));
        } else if (current > 0) {
            setEquippedColonization(player, current);
        }
    }

    private static boolean tickDisease(
            ServerPlayer player, HearthrotState state, int colonization) {
        if (state.stage() <= 0 || state.stage() >= 6) {
            return false;
        }
        int band = player.level() instanceof ServerLevel level
                ? BloomGrowthManager.localBandOrdinal(level, player.blockPosition()) : -1;
        float temperature = WorldTickHandler.getLastTemperature(player.getUUID());
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;
        double rate = HearthrotPolicy.progressionRate(
                band, temperature, moving, colonization);
        state.setProgressTicks(state.progressTicks() + rate * LOGIC_INTERVAL_TICKS);

        boolean advanced = false;
        int duration = stageDuration(player, state.stage());
        while (state.stage() < 6 && duration > 0
                && state.progressTicks() >= duration) {
            state.setProgressTicks(state.progressTicks() - duration);
            state.setStage(state.stage() + 1);
            state.setTransitionMask(state.transitionMask() | (1 << state.stage()));
            state.setCoughTicks(nextCoughDelay(player, state.stage()));
            state.setWheezeTicks(nextWheezeDelay(player, state.stage()));
            advanced = true;
            duration = stageDuration(player, state.stage());
        }
        return advanced;
    }

    private static void applySymptoms(ServerPlayer player, int stage) {
        if (stage <= 0) {
            removeSymptoms(player);
            return;
        }
        ensureHearthrotEffect(player, stage);
        if (stage >= 3) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 240, 0,
                    false, false, true));
        }
        replaceTransientModifier(
                player.getAttribute(Attributes.MAX_HEALTH),
                MAX_HEALTH_MODIFIER_ID,
                -2.0D * HearthrotPolicy.maxHealthPenaltyHearts(stage),
                AttributeModifier.Operation.ADD_VALUE);
        replaceTransientModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                MOVEMENT_MODIFIER_ID,
                HearthrotPolicy.movementPenalty(
                        stage, SuitIntegrityHandler.isWearingSealedSuit(player)),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeSymptoms(ServerPlayer player) {
        player.removeEffect(ModEffects.HEARTHROT);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (health != null) {
            health.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        if (movement != null) {
            movement.removeModifier(MOVEMENT_MODIFIER_ID);
        }
    }

    private static void ensureHearthrotEffect(ServerPlayer player, int stage) {
        if (stage <= 0) {
            return;
        }
        MobEffectInstance current = player.getEffect(ModEffects.HEARTHROT);
        if (current != null && current.getAmplifier() == stage - 1
                && current.isInfiniteDuration()) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                ModEffects.HEARTHROT, MobEffectInstance.INFINITE_DURATION, stage - 1,
                false, false, true));
    }

    private static void replaceTransientModifier(
            AttributeInstance instance,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null
                && existing.amount() == amount
                && existing.operation() == operation) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    id, amount, operation));
        }
    }

    private static void tickCough(ServerPlayer player, HearthrotState state) {
        if (state.stage() < 3) {
            state.setCoughTicks(0);
            return;
        }
        if (state.coughTicks() <= 0) {
            state.setCoughTicks(nextCoughDelay(player, state.stage()));
            return;
        }
        if (state.coughTicks() > LOGIC_INTERVAL_TICKS) {
            state.setCoughTicks(state.coughTicks() - LOGIC_INTERVAL_TICKS);
            return;
        }
        state.setCoughTicks(nextCoughDelay(player, state.stage()));
        sync(player, state, HearthrotPayload.COUGH);
        attractRespiratoryThreats(player, 24.0D);
    }

    private static int nextCoughDelay(ServerPlayer player, int stage) {
        int minimum = HearthrotPolicy.coughMinimumSeconds(stage) * 20;
        int maximum = HearthrotPolicy.coughMaximumSeconds(stage) * 20;
        if (minimum <= 0 || maximum <= 0) {
            return 0;
        }
        return player.getRandom().nextInt(minimum, maximum + 1);
    }

    private static void tickWheeze(ServerPlayer player, HearthrotState state) {
        if (state.stage() < 4) {
            state.setWheezeTicks(0);
            return;
        }
        if (state.wheezeTicks() <= 0) {
            state.setWheezeTicks(nextWheezeDelay(player, state.stage()));
            return;
        }
        if (state.wheezeTicks() > LOGIC_INTERVAL_TICKS) {
            state.setWheezeTicks(state.wheezeTicks() - LOGIC_INTERVAL_TICKS);
            return;
        }
        state.setWheezeTicks(nextWheezeDelay(player, state.stage()));
        state.setCoughTicks(Math.max(state.coughTicks(), 12 * 20));
        sync(player, state, HearthrotPayload.WHEEZE);
        attractRespiratoryThreats(player, 30.0D);
    }

    private static int nextWheezeDelay(ServerPlayer player, int stage) {
        int minimum = HearthrotPolicy.wheezeMinimumSeconds(stage) * 20;
        int maximum = HearthrotPolicy.wheezeMaximumSeconds(stage) * 20;
        if (minimum <= 0 || maximum <= 0) {
            return 0;
        }
        return player.getRandom().nextInt(minimum, maximum + 1);
    }

    private static void attractRespiratoryThreats(
            ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        level.gameEvent(player, GameEvent.ENTITY_ACTION, player.position());
        ResonanceEventManager.emit(level, player.position(), 6.0F,
                ResonanceEventManager.Type.RESPIRATORY, player.getUUID());
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                player.getX(), player.getEyeY() - 0.12D, player.getZ(),
                9, 0.20D, 0.12D, 0.20D, 0.015D);
        AABB area = player.getBoundingBox().inflate(radius);
        for (PathfinderMob mob : level.getEntitiesOfClass(
                PathfinderMob.class, area, HearthrotManager::isRespiratoryThreat)) {
            if (mob instanceof ResonantEntity) {
                continue;
            }
            if (mob.getTarget() != null || mob.isPassenger()) {
                continue;
            }
            mob.getLookControl().setLookAt(
                    player.getX(), player.getEyeY(), player.getZ(), 30.0F, 30.0F);
            mob.getNavigation().moveTo(
                    player.getX(), player.getY(), player.getZ(), 1.08D);
        }
    }

    private static boolean isRespiratoryThreat(PathfinderMob mob) {
        if (!mob.isAlive()) {
            return false;
        }
        if (mob instanceof MimicEntity mimic) {
            return !mimic.isHearthPopulationResident();
        }
        return mob instanceof FrostbittenEntity
                || mob instanceof RimeboundEntity
                || mob instanceof ResonantEntity
                || mob instanceof HollowEntity
                || mob instanceof UndoneEntity;
    }

    private static void tickSalvationEpisode(
            ServerPlayer player, HearthrotState state) {
        if (state.stage() < 4
                || ReturnedHearthSavedData.get(player.getServer())
                        .hearthrotSalvationFired()
                || !(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            resetStillness(state);
            return;
        }

        if (BloomGrowthManager.localBandOrdinal(
                level, player.blockPosition()) != BloomBand.CORE.ordinal()) {
            resetStillness(state);
            return;
        }
        long currentPosition = player.blockPosition().asLong();
        boolean samePosition = state.hasLastPosition()
                && state.lastPosition() == currentPosition;
        state.rememberPosition(currentPosition);
        boolean stationary = samePosition
                && player.getDeltaMovement().horizontalDistanceSqr() < 0.0004D
                && Math.abs(player.getDeltaMovement().y) < 0.01D;
        if (!stationary) {
            resetStillnessProgress(state);
            return;
        }
        state.setStationaryTicks(state.stationaryTicks() + 1);
        if (!HearthrotPolicy.shouldRollSalvation(
                state.stationaryTicks(), state.stillnessEpisodeRolled())) {
            return;
        }
        state.setStillnessEpisodeRolled(true);
        if (!hasBloomCoreWithin(level, player.blockPosition(), 12)
                || !isAlone(level, player, 32.0D)
                || player.getRandom().nextFloat() >= 0.05F) {
            return;
        }
        ReturnedHearthSavedData world = ReturnedHearthSavedData.get(
                player.getServer());
        if (world.markHearthrotSalvationFired()) {
            PacketDistributor.sendToPlayer(
                    player, new HearthrotSalvationPayload());
        }
    }

    private static void resetStillness(HearthrotState state) {
        resetStillnessProgress(state);
        state.clearPosition();
    }

    private static void resetStillnessProgress(HearthrotState state) {
        state.setStationaryTicks(0);
        state.setStillnessEpisodeRolled(false);
    }

    private static boolean isAlone(
            ServerLevel level, ServerPlayer player, double radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(
                LivingEntity.class, area,
                entity -> entity != player && entity.isAlive()).isEmpty();
    }

    private static boolean hasBloomCoreWithin(
            ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x * x + y * y + z * z > radius * radius) {
                        continue;
                    }
                    cursor.set(center.getX() + x, center.getY() + y,
                            center.getZ() + z);
                    if (level.getBlockState(cursor).is(ModBlocks.BLOOM_CORE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasEquippedColonizablePiece(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                    && isColonizablePiece(player.getItemBySlot(slot))) {
                return true;
            }
        }
        return false;
    }

    private static void setEquippedColonization(Player player, int value) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            if (!isColonizablePiece(stack)) {
                continue;
            }
            if (value <= 0) {
                stack.remove(ModDataComponents.HEARTHROT_COLONIZATION.get());
            } else {
                stack.set(ModDataComponents.HEARTHROT_COLONIZATION.get(), value);
            }
        }
    }

    private static int stageDuration(ServerPlayer player, int stage) {
        String preset = ApocalypseState.get(player.getServer()).getPresetName();
        HearthrotPolicy.Preset policyPreset = switch (preset.toLowerCase()) {
            case "cinematic" -> HearthrotPolicy.Preset.CINEMATIC;
            case "brutal" -> HearthrotPolicy.Preset.BRUTAL;
            default -> HearthrotPolicy.Preset.NORMAL;
        };
        return HearthrotPolicy.stageDurationTicks(stage, policyPreset);
    }

    private static void sync(
            ServerPlayer player, HearthrotState state, int eventId) {
        PacketDistributor.sendToPlayer(
                player,
                new HearthrotPayload(
                        state.stage(),
                        progressRatio(player),
                        effectiveColonization(player),
                        eventId));
    }
}
