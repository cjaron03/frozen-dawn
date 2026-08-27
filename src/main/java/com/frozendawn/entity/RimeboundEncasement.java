package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rimebound-grown ice on the body; EVA climate control cannot prevent it. */
public final class RimeboundEncasement {
    public static final float MAX_ENCASEMENT = 100.0F;
    private static final int SOLID_TICKS = 70;
    private static final float PASSIVE_DECAY = 0.085F;
    private static final float FIRE_DECAY = 1.5F;
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,
                    "rimebound_encasement");
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private RimeboundEncasement() {
    }

    public static void apply(ServerPlayer player, float amount) {
        if (player.isCreative() || player.isSpectator() || amount <= 0.0F) {
            return;
        }
        State state = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new State());
        int oldStage = stage(state.amount);
        state.amount = Math.min(MAX_ENCASEMENT, state.amount + amount);
        int newStage = stage(state.amount);
        if (newStage > oldStage && newStage < 4) {
            player.playSound(ModSounds.RIMEBOUND_ENCASE.get(),
                    0.9F + newStage * 0.12F, 1.08F - newStage * 0.08F);
        }
        if (state.amount >= MAX_ENCASEMENT && state.solidArmed) {
            state.solidTicks = SOLID_TICKS;
            state.solidArmed = false;
            player.stopUsingItem();
            player.playSound(ModSounds.RIMEBOUND_SOLIDIFY.get(), 1.55F, 0.82F);
            player.serverLevel().sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    46, 0.55D, 0.9D, 0.55D, 0.075D);
        }
    }

    public static void tick(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            clear(player);
            return;
        }

        if (state.solidTicks > 0) {
            state.solidTicks--;
            state.amount = MAX_ENCASEMENT;
            player.setSprinting(false);
            player.stopUsingItem();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
            player.hurtMarked = true;
            if (state.solidTicks == 0) {
                state.amount = 68.0F;
                player.playSound(ModSounds.RIMEBOUND_BREAK_FREE.get(), 1.3F, 0.94F);
                player.serverLevel().sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        34, 0.5D, 0.8D, 0.5D, 0.095D);
            }
        } else {
            state.amount = Math.max(0.0F, state.amount
                    - (player.isOnFire() ? FIRE_DECAY : PASSIVE_DECAY));
            if (state.amount <= 60.0F) {
                state.solidArmed = true;
            }
        }

        applyMovementPenalty(player, state);
        syncVisualState(player, state);
        int required = player.getTicksRequiredToFreeze() + 20;
        player.setTicksFrozen(Math.round(required * state.amount / MAX_ENCASEMENT));
        if (state.amount >= 50.0F && player.tickCount % 5 == 0) {
            int particles = state.solidTicks > 0 ? 5 : 2;
            player.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE,
                    player.getX(), player.getY() + 0.9D, player.getZ(),
                    particles, 0.38D, 0.72D, 0.38D, 0.012D);
        }
        if (state.amount <= 0.0F) {
            clear(player);
        }
    }

    public static float amount(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        return state == null ? 0.0F : state.amount;
    }

    public static boolean isFrozenSolid(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        return state != null && state.solidTicks > 0;
    }

    public static void clear(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(MOVEMENT_MODIFIER_ID);
        }
        player.removeEffect(ModEffects.RIMEBOUND_ENCASEMENT);
        player.setTicksFrozen(0);
    }

    public static void reset() {
        ACTIVE.clear();
    }

    static int stage(float amount) {
        if (amount >= MAX_ENCASEMENT) {
            return 4;
        }
        if (amount >= 75.0F) {
            return 3;
        }
        if (amount >= 50.0F) {
            return 2;
        }
        return amount >= 25.0F ? 1 : 0;
    }

    private static void applyMovementPenalty(ServerPlayer player, State state) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        movement.removeModifier(MOVEMENT_MODIFIER_ID);
        double penalty;
        if (state.solidTicks > 0) {
            penalty = -1.0D;
        } else {
            penalty = switch (stage(state.amount)) {
                case 1 -> -0.08D;
                case 2 -> -0.22D;
                case 3, 4 -> -0.45D;
                default -> 0.0D;
            };
        }
        if (penalty != 0.0D) {
            movement.addTransientModifier(new AttributeModifier(
                    MOVEMENT_MODIFIER_ID, penalty,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void syncVisualState(ServerPlayer player, State state) {
        int amplifier = state.solidTicks > 0
                ? 3 : Math.min(2, Math.max(0, stage(state.amount) - 1));
        MobEffectInstance current = player.getEffect(ModEffects.RIMEBOUND_ENCASEMENT);
        if (current == null || current.getAmplifier() != amplifier
                || current.getDuration() <= 10) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.RIMEBOUND_ENCASEMENT, 30, amplifier,
                    false, false, true));
        }
    }

    private static final class State {
        private float amount;
        private int solidTicks;
        private boolean solidArmed = true;
    }
}
