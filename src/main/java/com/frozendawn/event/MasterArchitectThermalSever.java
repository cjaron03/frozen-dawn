package com.frozendawn.event;

import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectThermalSeverWarningPayload;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative thermal loop failure caused only by the Master Architect. */
public final class MasterArchitectThermalSever {
    private static final Map<UUID, SeverState> ACTIVE = new HashMap<>();

    private MasterArchitectThermalSever() {
    }

    public static void apply(ServerPlayer player, UUID casterId) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        ACTIVE.put(player.getUUID(), new SeverState(
                player.serverLevel().getGameTime(), casterId));
        PacketDistributor.sendToPlayer(
                player, new MasterArchitectThermalSeverWarningPayload());
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, SeverState>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SeverState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            SeverState state = entry.getValue();
            int elapsed = elapsedTicks(player, state);
            int totalTicks = MasterArchitectCombatPolicy.THERMAL_ACTIVE_TICKS
                    + MasterArchitectCombatPolicy.THERMAL_RECOVERY_TICKS;
            if (elapsed >= totalTicks) {
                iterator.remove();
                continue;
            }

            int amplifier = MasterArchitectCombatPolicy
                    .thermalSlownessAmplifierAt(elapsed);
            if (amplifier >= 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        12,
                        amplifier,
                        false,
                        false,
                        true));
                player.setTicksFrozen(Math.max(
                        player.getTicksFrozen(), player.getTicksRequiredToFreeze() + 20));
            }

            if (!state.pulsesCancelled
                    && shouldCancelRemainingPulses(player, state)) {
                state.pulsesCancelled = true;
                player.serverLevel().playSound(
                        null,
                        player.blockPosition(),
                        ModSounds.MASTER_ARCHITECT_THERMAL_CANCEL.get(),
                        SoundSource.HOSTILE,
                        1.2F,
                        1.08F);
            }

            int duePulses = MasterArchitectCombatPolicy.thermalPulseCountAt(elapsed);
            while (!state.pulsesCancelled
                    && state.pulsesApplied < duePulses
                    && player.isAlive()) {
                state.pulsesApplied++;
                player.serverLevel().playSound(
                        null,
                        player.blockPosition(),
                        ModSounds.MASTER_ARCHITECT_THERMAL_PULSE.get(),
                        SoundSource.HOSTILE,
                        1.25F,
                        0.94F + state.pulsesApplied * 0.025F);
                player.hurt(createDamageSource(player, state.casterId),
                        MasterArchitectCombatPolicy.THERMAL_PULSE_DAMAGE);
            }
        }
    }

    public static float adjustTemperature(ServerPlayer player, float temperature) {
        SeverState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return temperature;
        }
        return MasterArchitectCombatPolicy.adjustedTemperature(
                temperature, elapsedTicks(player, state));
    }

    public static boolean isSevering(ServerPlayer player) {
        SeverState state = ACTIVE.get(player.getUUID());
        return state != null
                && elapsedTicks(player, state)
                < MasterArchitectCombatPolicy.THERMAL_ACTIVE_TICKS;
    }

    static void onPlayerLogout(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    static void reset() {
        ACTIVE.clear();
    }

    private static int elapsedTicks(ServerPlayer player, SeverState state) {
        long elapsed = player.serverLevel().getGameTime() - state.startGameTime;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, elapsed));
    }

    private static boolean shouldCancelRemainingPulses(
            ServerPlayer player, SeverState state) {
        Entity caster = player.serverLevel().getEntity(state.casterId);
        boolean hasLineOfSight = caster instanceof LivingEntity living
                && living.hasLineOfSight(player);
        boolean withinHeatSource = isNearActivePlayerHeater(player);
        return MasterArchitectCombatPolicy.shouldCancelThermalPulses(
                hasLineOfSight, withinHeatSource);
    }

    private static boolean isNearActivePlayerHeater(ServerPlayer player) {
        PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(
                player.getServer());
        return HeaterRegistry.getHeaters(player.serverLevel()).stream()
                .filter(tracker::isPlayerPlaced)
                .filter(pos -> player.serverLevel().getBlockEntity(pos)
                        instanceof ThermalHeaterBlockEntity heater && heater.isLit())
                .anyMatch(pos -> player.distanceToSqr(pos.getCenter()) <= 5.5D * 5.5D);
    }

    private static DamageSource createDamageSource(
            ServerPlayer player, UUID casterId) {
        Entity caster = player.serverLevel().getEntity(casterId);
        return new DamageSource(
                player.serverLevel().registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.THERMAL_SEVER),
                caster);
    }

    private static final class SeverState {
        private final long startGameTime;
        private final UUID casterId;
        private int pulsesApplied;
        private boolean pulsesCancelled;

        private SeverState(long startGameTime, UUID casterId) {
            this.startGameTime = startGameTime;
            this.casterId = casterId;
        }
    }
}
