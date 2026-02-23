package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Applies freezing effects to mobs and players on the surface in cold temperatures.
 *
 * Temperature thresholds:
 *   >= 0C   : No effect (freeze ticks decay)
 *   -10C    : Slowness I
 *   -25C    : Slowness II + 1 dmg per check
 *   -45C    : Slowness III + 2 dmg per check
 *   < -70C  : Slowness IV + 3 dmg per check
 *
 * Players are skipped if Tough As Nails is loaded (TaN handles its own temperature).
 * Creative/spectator players, armor stands, and freeze-immune entities are always exempt.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class MobFreezeHandler {

    private static final int MOB_CHECK_INTERVAL = 60;    // ~3 seconds
    private static final int PLAYER_CHECK_INTERVAL = 40;  // ~2 seconds

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (!FrozenDawnConfig.ENABLE_MOB_FREEZING.get()) return;
        if (entity.level().isClientSide()) return;
        if (entity.level().dimension() != Level.OVERWORLD) return;

        // Stagger checks to avoid performance spikes
        boolean isPlayer = entity instanceof Player;
        int interval = isPlayer ? PLAYER_CHECK_INTERVAL : MOB_CHECK_INTERVAL;
        if (entity.tickCount % interval != 0) return;

        // Skip entities that shouldn't be affected
        if (entity instanceof ArmorStand) return;
        if (entity.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) return;
        if (isPlayer) {
            Player player = (Player) entity;
            if (player.isCreative() || player.isSpectator()) return;
            // TaN handles player temperature when present
            if (ModList.get().isLoaded("toughasnails")) return;
        }

        ServerLevel level = (ServerLevel) entity.level();
        ApocalypseState state = ApocalypseState.get(level.getServer());
        if (state.getPhase() < 2) return;

        float temp = TemperatureManager.getTemperatureAt(
                level, entity.blockPosition(),
                state.getCurrentDay(), state.getTotalDays());

        applyFreezeEffects(living, temp, isPlayer);
    }

    private static void applyFreezeEffects(LivingEntity entity, float temp, boolean isPlayer) {
        // Warm enough — thaw out
        if (temp >= 0f) {
            if (entity.getTicksFrozen() > 0) {
                entity.setTicksFrozen(Math.max(0, entity.getTicksFrozen() - 2));
            }
            return;
        }

        // Build up visual freeze (vanilla frost overlay on entities)
        int maxFreeze = entity.getTicksRequiredToFreeze() + 20;
        entity.setTicksFrozen(Math.min(maxFreeze, entity.getTicksFrozen() + 3));

        // Determine severity
        int slowLevel;
        float damage;

        if (temp <= -70f) {
            slowLevel = 3;   // Slowness IV
            damage = 3.0f;
        } else if (temp <= -45f) {
            slowLevel = 2;   // Slowness III
            damage = 2.0f;
        } else if (temp <= -25f) {
            slowLevel = 1;   // Slowness II
            damage = 1.0f;
        } else if (temp <= -10f) {
            slowLevel = 0;   // Slowness I
            damage = 0f;
        } else {
            // Between -10 and 0: slight chill but no effects yet
            return;
        }

        // Apply slowness (duration slightly longer than check interval to avoid flickering)
        entity.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 80, slowLevel, false, false, true));

        // Apply freezing damage
        if (damage > 0f) {
            entity.hurt(entity.damageSources().freeze(), damage);
        }

        // Action bar warning for players
        if (isPlayer && entity instanceof Player player) {
            String key;
            if (temp <= -70f) {
                key = "message.frozendawn.freeze.deadly";
            } else if (temp <= -45f) {
                key = "message.frozendawn.freeze.extreme";
            } else if (temp <= -25f) {
                key = "message.frozendawn.freeze.biting";
            } else {
                key = "message.frozendawn.freeze.cold";
            }
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
