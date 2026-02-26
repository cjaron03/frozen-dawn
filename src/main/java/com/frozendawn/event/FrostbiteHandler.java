package com.frozendawn.event;

import com.frozendawn.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frostbite mechanic: acheronite shards drain the player's internal temperature.
 *
 * Holding (mainhand/offhand): full drain rate.
 *   Stage 1 (0-10s past grace): Slowness I, "fingers going numb"
 *   Stage 2 (10-25s past grace): Slowness II, "cold is spreading"
 *   Stage 3 (25s+ past grace): Slowness II + damage, "frostbite!"
 *   Mining Fatigue II after 30s of frostbite.
 *
 * Inventory: 3x slower drain.
 *   Stage 1: "something very cold in your inventory"
 *   Stage 2: Slowness I, "pack is radiating cold"
 *   Stage 3: Slowness II + damage, "cold from your pack is unbearable"
 *
 * Grace periods (armor tier): No armor 10s, T1 25s, T2 45s, EVA 90s, Lined/Acheronite immune.
 * Anti-exploit: cooling recovers at 1/3 rate when not carrying shards.
 */
final class FrostbiteHandler {

    private FrostbiteHandler() {}

    private static final Map<UUID, Integer> coolingTicks = new HashMap<>();
    private static final Map<UUID, Integer> frostbiteTicks = new HashMap<>();

    // Frostbite stages (ticks past grace period)
    private static final int STAGE_2_TICKS = 200;  // 10s
    private static final int STAGE_3_TICKS = 500;   // 25s
    private static final int FATIGUE_THRESHOLD = 600; // 30s

    // Temperature drain shown on the HUD
    private static final float HAND_TEMP_DRAIN = 15f;      // -15°C while holding
    private static final float INVENTORY_TEMP_DRAIN = 5f;   // -5°C while in inventory

    static void reset() {
        coolingTicks.clear();
        frostbiteTicks.clear();
    }

    /**
     * Returns the temperature penalty from frostbite for the HUD gauge.
     */
    static float getTemperatureDrain(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return 0f;
        if (MobFreezeHandler.hasFrostbiteImmunity(player)) return 0f;

        boolean holding = isHoldingShard(player);
        boolean inInventory = !holding && hasShardInInventory(player);

        if (holding) return HAND_TEMP_DRAIN;
        if (inInventory) return INVENTORY_TEMP_DRAIN;
        // Lingering drain based on remaining cooling ticks
        int cooling = coolingTicks.getOrDefault(player.getUUID(), 0);
        if (cooling > 0) return INVENTORY_TEMP_DRAIN * (Math.min(cooling, 200) / 200f);
        return 0f;
    }

    static void tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;

        UUID id = player.getUUID();
        boolean holding = isHoldingShard(player);
        boolean inInventory = !holding && hasShardInInventory(player);

        if (MobFreezeHandler.hasFrostbiteImmunity(player)) {
            recover(id);
            frostbiteTicks.remove(id);
            return;
        }

        int graceLimit = getGracePeriod(player);

        if (holding) {
            int cooling = coolingTicks.getOrDefault(id, 0) + 1;
            coolingTicks.put(id, cooling);

            if (cooling > graceLimit) {
                int fbTicks = frostbiteTicks.getOrDefault(id, 0) + 1;
                frostbiteTicks.put(id, fbTicks);
                applyHandFrostbite(player, cooling - graceLimit, fbTicks);
            }
        } else if (inInventory) {
            int cooling = coolingTicks.getOrDefault(id, 0);
            if (player.tickCount % 3 == 0) {
                cooling++;
                coolingTicks.put(id, cooling);
            }

            if (cooling > graceLimit) {
                applyInventoryFrostbite(player, cooling - graceLimit);
            } else if (player.tickCount % 80 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.inventory"), true);
            }
        } else {
            recover(id);
            int fbTicks = frostbiteTicks.getOrDefault(id, 0);
            if (fbTicks > 0) {
                frostbiteTicks.put(id, Math.max(0, fbTicks - 1));
            }
        }
    }

    private static void applyHandFrostbite(ServerPlayer player, int overGrace, int fbTicks) {
        // Freeze overlay
        int maxFreeze = player.getTicksRequiredToFreeze() + 20;
        player.setTicksFrozen(Math.min(maxFreeze, player.getTicksFrozen() + 1));

        if (overGrace >= STAGE_3_TICKS) {
            // Stage 3: Slowness II + damage
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
            if (player.tickCount % 40 == 0) {
                player.hurt(player.damageSources().freeze(), 1.0f);
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.severe"), true);
                WorldTickHandler.grantAdvancement(player, "frostbitten");
            }
        } else if (overGrace >= STAGE_2_TICKS) {
            // Stage 2: Slowness II
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.spreading"), true);
            }
        } else {
            // Stage 1: Slowness I
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.cold"), true);
            }
        }

        // Mining Fatigue II after 30s
        if (fbTicks >= FATIGUE_THRESHOLD) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, 200, 1, false, false, true));
        }
    }

    private static void applyInventoryFrostbite(ServerPlayer player, int overGrace) {
        // Freeze overlay
        int maxFreeze = player.getTicksRequiredToFreeze() + 20;
        player.setTicksFrozen(Math.min(maxFreeze, player.getTicksFrozen() + 1));

        if (overGrace >= STAGE_3_TICKS) {
            // Stage 3: Slowness II + damage
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
            if (player.tickCount % 60 == 0) {
                player.hurt(player.damageSources().freeze(), 1.0f);
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.inventory_severe"), true);
            }
        } else if (overGrace >= STAGE_2_TICKS) {
            // Stage 2: Slowness I
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            if (player.tickCount % 80 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.inventory_spreading"), true);
            }
        } else {
            // Stage 1: warning only
            if (player.tickCount % 80 == 0) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.frostbite.inventory"), true);
            }
        }
    }

    private static void recover(UUID id) {
        int cooling = coolingTicks.getOrDefault(id, 0);
        if (cooling > 0) {
            coolingTicks.put(id, cooling - 1);
        } else {
            coolingTicks.remove(id);
        }
    }

    private static boolean isHoldingShard(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(ModItems.ACHERONITE_SHARD.get()) || off.is(ModItems.ACHERONITE_SHARD.get());
    }

    private static boolean hasShardInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.ACHERONITE_SHARD.get())) return true;
        }
        return false;
    }

    private static int getGracePeriod(ServerPlayer player) {
        int tier = MobFreezeHandler.getFullSetTier(player);
        return switch (tier) {
            case 1 -> 500;   // 25s
            case 2 -> 900;   // 45s
            case 3 -> 1800;  // 90s
            default -> 200;  // 10s
        };
    }
}
