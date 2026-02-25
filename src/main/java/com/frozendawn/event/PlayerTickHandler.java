package com.frozendawn.event;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.network.TemperaturePayload;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles per-player tick logic: temperature sync, heat damage, wind chill,
 * atmospheric suffocation, and armor advancement grants.
 * Called from WorldTickHandler each server tick.
 */
final class PlayerTickHandler {

    private PlayerTickHandler() {}

    private static final Map<UUID, Boolean> habitableCache = new HashMap<>();
    private static final Map<UUID, Integer> suffocationTimer = new HashMap<>();
    private static final Map<UUID, Float> playerTemperatures = new HashMap<>();

    private static final int SUFFOCATION_DURATION = 200;
    private static final String[] ARMOR_ADVANCEMENTS = {
            null, "insulated_clothing", "heavy_insulation", "eva_suit"
    };

    static void reset() {
        habitableCache.clear();
        suffocationTimer.clear();
        playerTemperatures.clear();
    }

    /** Returns the last-calculated temperature for a player (updated every 40 ticks). */
    static float getLastTemperature(UUID playerId) {
        return playerTemperatures.getOrDefault(playerId, 20f);
    }

    /**
     * Called every server tick from WorldTickHandler.
     */
    static void tick(MinecraftServer server, ApocalypseState state, int currentPhase, int currentDay, float progress) {
        // Temperature sync + heat damage + armor advancements (every 40 ticks)
        if (state.getApocalypseTicks() % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() == Level.OVERWORLD) {
                    float temp = TemperatureManager.getTemperatureAt(
                            player.level(), player.blockPosition(), currentDay, state.getTotalDays());

                    // Armor heat trapping: insulated armor amplifies heat above 20C
                    float armorHeatMult = MobFreezeHandler.getArmorHeatMultiplier(player);
                    if (temp > 20f && armorHeatMult > 0f) {
                        temp += (temp - 20f) * armorHeatMult;
                    }

                    playerTemperatures.put(player.getUUID(), temp);
                    PacketDistributor.sendToPlayer(player, new TemperaturePayload(temp));

                    applyHeatDamage(player, temp, progress);
                }
                // Grant armor tier advancements
                int armorTier = MobFreezeHandler.getFullSetTier(player);
                for (int i = 1; i <= armorTier && i < ARMOR_ADVANCEMENTS.length; i++) {
                    if (ARMOR_ADVANCEMENTS[i] != null) {
                        WorldTickHandler.grantAdvancement(player, ARMOR_ADVANCEMENTS[i]);
                    }
                }
            }
        }

        // Wind chill exhaustion (every 20 ticks, phase 5+)
        if (currentPhase >= 5 && state.getApocalypseTicks() % 20 == 0) {
            tickWindChill(server, currentPhase);
        }

        // Atmospheric suffocation (every tick, phase 6 late)
        if (currentPhase >= 6 && progress >= 0.85f) {
            tickSuffocation(server, state, progress);
        }
    }

    private static void applyHeatDamage(ServerPlayer player, float temp, float progress) {
        if (player.isCreative() || player.isSpectator() || temp <= 60f) return;

        if (temp >= 120f) {
            DamageSource src = new DamageSource(
                    player.serverLevel().registryAccess()
                            .lookupOrThrow(Registries.DAMAGE_TYPE)
                            .getOrThrow(ModDamageTypes.HYPERTHERMIA));
            Vec3 motion = player.getDeltaMovement();
            player.hurt(src, 4.0f);
            player.setDeltaMovement(motion);
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
            player.displayClientMessage(Component.translatable("message.frozendawn.heat.cooking"), true);
            WorldTickHandler.grantAdvancement(player, "too_hot_to_handle");
            int armorTier = MobFreezeHandler.getFullSetTier(player);
            if (armorTier >= 1 && armorTier <= 2) {
                WorldTickHandler.grantAdvancement(player, "insulation_both_ways");
            }
        } else if (temp >= 90f) {
            DamageSource src = new DamageSource(
                    player.serverLevel().registryAccess()
                            .lookupOrThrow(Registries.DAMAGE_TYPE)
                            .getOrThrow(ModDamageTypes.HYPERTHERMIA));
            Vec3 motion = player.getDeltaMovement();
            player.hurt(src, 2.0f);
            player.setDeltaMovement(motion);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, false, false));
            player.displayClientMessage(Component.translatable("message.frozendawn.heat.unbearable"), true);
            WorldTickHandler.grantAdvancement(player, "too_hot_to_handle");
            int armorTier = MobFreezeHandler.getFullSetTier(player);
            if (armorTier >= 1 && armorTier <= 2) {
                WorldTickHandler.grantAdvancement(player, "insulation_both_ways");
            }
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, false));
            player.displayClientMessage(Component.translatable("message.frozendawn.heat.sweating"), true);
        }
    }

    private static void tickWindChill(MinecraftServer server, int currentPhase) {
        float phaseMult = currentPhase >= 6 ? 1.5f : 1.0f;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level().dimension() != Level.OVERWORLD) continue;
            if (player.blockPosition().getY() < 50) continue;
            if (!player.level().canSeeSky(player.blockPosition().above())) continue;

            int armorTier = MobFreezeHandler.getFullSetTier(player);
            if (armorTier >= 3) continue;
            float armorReduction = armorTier >= 2 ? 0.5f : (armorTier >= 1 ? 0.75f : 1.0f);

            float exhaustion;
            if (player.isSprinting()) {
                exhaustion = 1.0f * phaseMult * armorReduction;
            } else if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                exhaustion = 0.4f * phaseMult * armorReduction;
            } else {
                exhaustion = 0.2f * phaseMult * armorReduction;
            }
            player.getFoodData().addExhaustion(exhaustion);
        }
    }

    private static void tickSuffocation(MinecraftServer server, ApocalypseState state, float progress) {
        boolean refreshCache = state.getApocalypseTicks() % 20 == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level().dimension() != Level.OVERWORLD) continue;

            UUID id = player.getUUID();
            if (refreshCache) {
                habitableCache.put(id, isInHabitableZone(player));
            }
            if (Boolean.TRUE.equals(habitableCache.get(id))) {
                suffocationTimer.put(id, 0);
                continue;
            }

            if (MobFreezeHandler.getFullSetTier(player) >= 3 && progress < 0.95f) {
                suffocationTimer.put(id, 0);
                continue;
            }

            int ticks = suffocationTimer.getOrDefault(id, 0) + 1;
            suffocationTimer.put(id, ticks);
            float suffProgress = Math.min(1.0f, (float) ticks / SUFFOCATION_DURATION);

            if (suffProgress >= 0.15f) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.suffocate.lightheaded"), true);
            }
            if (suffProgress >= 0.40f) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.suffocate.nausea"), true);
            }
            if (suffProgress >= 0.70f) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false, false));
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.suffocate.fading"), true);
            }

            if (suffProgress >= 1.0f && ticks % 20 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.suffocate.dying"), true);
                DamageSource source = new DamageSource(
                        player.serverLevel().registryAccess()
                                .lookupOrThrow(Registries.DAMAGE_TYPE)
                                .getOrThrow(ModDamageTypes.ATMOSPHERIC_SUFFOCATION));
                Vec3 motion = player.getDeltaMovement();
                player.hurt(source, 2.0f);
                player.setDeltaMovement(motion);
            }
        }
    }

    private static boolean isInHabitableZone(ServerPlayer player) {
        if (!player.level().canSeeSky(player.blockPosition().above())) return true;

        BlockPos playerPos = player.blockPosition();
        for (BlockPos corePos : GeothermalCoreRegistry.getCores(player.level())) {
            int o2Range;
            BlockEntity be = player.level().getBlockEntity(corePos);
            if (be instanceof GeothermalCoreBlockEntity core) {
                o2Range = core.getEffectiveO2Range();
            } else {
                o2Range = GeothermalCoreBlockEntity.BASE_O2_RANGE;
            }
            if (playerPos.distSqr(corePos) <= (long) o2Range * o2Range) {
                return true;
            }
        }
        return false;
    }
}
