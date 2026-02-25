package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModArmorMaterials;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
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
                state.getCurrentDay(), state.getTotalDays(), !isPlayer);

        // Apply cold resistance from insulated armor (players only)
        if (isPlayer) {
            temp += getArmorColdResistance((Player) entity);
        }

        applyFreezeEffects(living, temp, isPlayer, state);
    }

    private static void applyFreezeEffects(LivingEntity entity, float temp, boolean isPlayer, ApocalypseState state) {
        // Phase 6 late, exposed to sky: skip freeze — suffocation replaces freezing
        // Under a roof/underground there's trapped air, so cold still applies
        if (isPlayer && state.getPhase() >= 6 && state.getProgress() >= 0.85f
                && entity.level().canSeeSky(entity.blockPosition().above())) return;

        // Warm enough — thaw out
        if (temp >= 0f) {
            if (entity.getTicksFrozen() > 0) {
                // Instant thaw when armor is protecting the player
                if (isPlayer && getArmorColdResistance((Player) entity) > 0) {
                    entity.setTicksFrozen(0);
                } else {
                    entity.setTicksFrozen(Math.max(0, entity.getTicksFrozen() - 2));
                }
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

    /**
     * Calculates cold resistance from insulated armor.
     * Each piece of a tier contributes 1/4 of that tier's total bonus.
     * Higher tiers include lower tier bonuses (tier 2 piece counts as at least tier 1).
     */
    public static float getArmorColdResistance(Player player) {
        float total = 0f;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ArmorItem armorItem)) continue;
            Holder<ArmorMaterial> mat = armorItem.getMaterial();
            if (mat == ModArmorMaterials.INSULATED) {
                total += 6.25f;   // 25°C / 4
            } else if (mat == ModArmorMaterials.REINFORCED) {
                total += 11.25f;  // 45°C / 4
            } else if (mat == ModArmorMaterials.EVA) {
                total += 30.0f;   // 120°C / 4
            }
        }
        return total;
    }

    /**
     * Returns the heat trapping multiplier from insulated armor.
     * Each piece adds to the multiplier. In hot environments (above 20C),
     * the player's effective temperature increases by (temp - 20) * multiplier.
     * Insulated = 5% per piece, Reinforced = 10%, EVA = 2%.
     */
    public static float getArmorHeatMultiplier(Player player) {
        float mult = 0f;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ArmorItem armorItem)) continue;
            Holder<ArmorMaterial> mat = armorItem.getMaterial();
            if (mat == ModArmorMaterials.INSULATED) {
                mult += 0.05f;
            } else if (mat == ModArmorMaterials.REINFORCED) {
                mult += 0.10f;
            } else if (mat == ModArmorMaterials.EVA) {
                mult += 0.02f;
            }
        }
        return mult;
    }

    /**
     * Returns the highest insulation tier the player is wearing a full set of.
     * 0 = no insulation, 1 = insulated, 2 = reinforced, 3 = EVA.
     */
    public static int getFullSetTier(Player player) {
        int tier1 = 0, tier2 = 0, tier3 = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ArmorItem armorItem)) continue;
            Holder<ArmorMaterial> mat = armorItem.getMaterial();
            if (mat == ModArmorMaterials.INSULATED) tier1++;
            else if (mat == ModArmorMaterials.REINFORCED) tier2++;
            else if (mat == ModArmorMaterials.EVA) tier3++;
        }
        if (tier3 == 4) return 3;
        if (tier2 + tier3 >= 4) return 2;
        if (tier1 + tier2 + tier3 >= 4) return 1;
        return 0;
    }
}
