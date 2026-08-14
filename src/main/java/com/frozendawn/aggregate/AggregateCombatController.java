package com.frozendawn.aggregate;

import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.RimeboundEncasement;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

/** Server-owned single-action combat scheduler. */
public final class AggregateCombatController {
    private final List<AggregateTrait> traits = new ArrayList<>();
    private int decisionCooldown = 30;
    private int traitCursor;

    public void configure(List<AggregateLineage> lineages) {
        traits.clear();
        for (AggregateLineage lineage : lineages) traits.add(AggregateTraits.create(lineage));
    }

    public void tick(ServerLevel level, AggregateEntity aggregate) {
        LivingEntity target = aggregate.getTarget();
        if (target == null || !target.isAlive()) return;
        if (aggregate.action() != AggregateAction.NONE) {
            tickAction(level, aggregate, target);
            return;
        }
        if (aggregate.getPersistentData().hasUUID("resonancePriority")) {
            UUID priorityId = aggregate.getPersistentData().getUUID("resonancePriority");
            aggregate.getPersistentData().remove("resonancePriority");
            if (level.getPlayerByUUID(priorityId) instanceof Player priority
                    && combatPlayer(priority)) {
                aggregate.setTarget(priority);
                aggregate.beginAction(AggregateAction.LURCH, 38);
                return;
            }
        }
        if (decisionCooldown-- > 0 || aggregate.phase().ordinal()
                < AggregatePhase.COHERENT.ordinal()) return;
        decisionCooldown = aggregate.phase() == AggregatePhase.CONVERGENCE_FAILURE ? 24 : 38;

        int activeTraits = aggregate.activeTraitCount();
        if (activeTraits > 0 && aggregate.getRandom().nextFloat() < 0.48F) {
            for (int i = 0; i < activeTraits; i++) {
                AggregateTrait trait = traits.get(Math.floorMod(traitCursor++, activeTraits));
                if (trait.canStart(aggregate, target)) {
                    trait.start(aggregate, target);
                    return;
                }
            }
        }
        chooseUniversal(aggregate, target);
    }

    private void chooseUniversal(AggregateEntity aggregate, LivingEntity target) {
        double distance = aggregate.distanceTo(target);
        float roll = aggregate.getRandom().nextFloat();
        if (distance < 6.5D && roll < 0.48F) {
            aggregate.beginAction(AggregateAction.SWEEP, 42);
        } else if (distance < 12.0D && roll < 0.78F) {
            aggregate.beginAction(AggregateAction.SLAM, 54);
        } else {
            aggregate.beginAction(AggregateAction.LURCH, 38);
        }
    }

    private void tickAction(ServerLevel level, AggregateEntity aggregate,
                            LivingEntity target) {
        int tick = aggregate.actionTick();
        switch (aggregate.action()) {
            case SWEEP -> tickSweep(level, aggregate, tick);
            case SLAM -> tickSlam(level, aggregate, tick);
            case LURCH -> tickLurch(level, aggregate, target, tick);
            default -> {
                AggregateTrait trait = traitFor(aggregate.action());
                if (trait != null) trait.tick(level, aggregate, target, tick);
            }
        }
    }

    private void tickSweep(ServerLevel level, AggregateEntity aggregate, int tick) {
        if (tick == 10) aggregate.playSound(ModSounds.AGGREGATE_SWEEP.get(), 2.2F, 0.72F);
        if (tick != 24) return;
        for (Player player : level.getEntitiesOfClass(Player.class,
                aggregate.getBoundingBox().inflate(6.5D, 2.0D, 6.5D),
                AggregateCombatController::combatPlayer)) {
            player.hurt(level.damageSources().mobAttack(aggregate), 10.0F);
            Vec3 shove = player.position().subtract(aggregate.position())
                    .multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.35D);
            player.push(shove.x, 0.38D, shove.z);
        }
    }

    private void tickSlam(ServerLevel level, AggregateEntity aggregate, int tick) {
        if (tick == 12) aggregate.playSound(ModSounds.AGGREGATE_SLAM_WINDUP.get(), 2.1F, 0.58F);
        if (tick != 32) return;
        aggregate.playSound(ModSounds.AGGREGATE_SLAM.get(), 3.2F, 0.54F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        level.getBlockState(aggregate.blockPosition().below())),
                aggregate.getX(), aggregate.getY() + 0.2D, aggregate.getZ(),
                96, 5.0D, 0.4D, 5.0D, 0.18D);
        for (Player player : level.getEntitiesOfClass(Player.class,
                aggregate.getBoundingBox().inflate(9.0D, 3.0D, 9.0D),
                AggregateCombatController::combatPlayer)) {
            double falloff = 1.0D - Mth.clamp(aggregate.distanceTo(player) / 11.0D,
                    0.0D, 0.7D);
            player.hurt(level.damageSources().mobAttack(aggregate), (float) (14.0D * falloff));
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RimeboundEncasement.apply(serverPlayer, 8.0F);
            }
            Vec3 shove = player.position().subtract(aggregate.position())
                    .multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.7D * falloff);
            player.push(shove.x, 0.55D, shove.z);
        }
    }

    private void tickLurch(ServerLevel level, AggregateEntity aggregate,
                           LivingEntity target, int tick) {
        if (tick == 5) aggregate.playSound(ModSounds.AGGREGATE_LURCH.get(), 1.8F, 0.68F);
        if (tick >= 11 && tick <= 24) {
            Vec3 direction = target.position().subtract(aggregate.position())
                    .multiply(1.0D, 0.0D, 1.0D).normalize();
            aggregate.setDeltaMovement(direction.scale(0.52D));
            if (aggregate.distanceToSqr(target) <= 16.0D && tick % 6 == 0) {
                target.hurt(level.damageSources().mobAttack(aggregate), 8.0F);
            }
        }
    }

    private AggregateTrait traitFor(AggregateAction action) {
        AggregateLineage lineage = switch (action) {
            case RIMEBOUND_RUSH, RIMEBOUND_LANCE -> AggregateLineage.RIMEBOUND;
            case RESONANCE_PULSE -> AggregateLineage.RESONANT;
            case FALSE_OPENING -> AggregateLineage.REMNANT;
            case DISASSEMBLY -> AggregateLineage.FROSTWRITHE;
            case ACCRETION_CONSTRUCTION -> AggregateLineage.ARCHITECT;
            case REALLOCATION_BEAT -> null;
            default -> null;
        };
        if (lineage == null) return null;
        return traits.stream().filter(trait -> trait.lineage() == lineage).findFirst().orElse(null);
    }

    private static boolean combatPlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }
}
