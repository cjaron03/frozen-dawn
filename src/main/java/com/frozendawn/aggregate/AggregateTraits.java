package com.frozendawn.aggregate;

import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
import com.frozendawn.entity.RimeLanceEntity;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.ResonanceEventManager;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stateless trait implementations selected from the locked lineage snapshot. */
public final class AggregateTraits {
    private AggregateTraits() {
    }

    public static AggregateTrait create(AggregateLineage lineage) {
        return switch (lineage) {
            case RIMEBOUND -> new RimeboundTrait();
            case RESONANT -> new ResonantTrait();
            case REMNANT -> new RemnantTrait();
            case FROSTWRITHE -> new FrostwritheTrait();
            case ARCHITECT -> new ArchitectTrait();
            case UNDONE, NORMAL -> new UndoneTrait(lineage);
        };
    }

    private abstract static class BaseTrait implements AggregateTrait {
        private final AggregateLineage lineage;

        private BaseTrait(AggregateLineage lineage) {
            this.lineage = lineage;
        }

        @Override
        public AggregateLineage lineage() {
            return lineage;
        }

        @Override
        public boolean canStart(AggregateEntity aggregate, LivingEntity target) {
            return target != null && target.isAlive();
        }
    }

    private static final class RimeboundTrait extends BaseTrait {
        private RimeboundTrait() {
            super(AggregateLineage.RIMEBOUND);
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
            boolean lance = aggregate.hasDominantTrait(AggregateLineage.RIMEBOUND)
                    && (aggregate.distanceToSqr(target) > 81.0D
                    || aggregate.getRandom().nextFloat() < 0.42F);
            aggregate.beginAction(lance ? AggregateAction.RIMEBOUND_LANCE
                    : AggregateAction.RIMEBOUND_RUSH, lance ? 52 : 48);
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
            if (tick == 14) {
                aggregate.playSound(ModSounds.AGGREGATE_TRAIT_RIMEBOUND.get(), 2.0F, 0.65F);
            }
            if (aggregate.action() == AggregateAction.RIMEBOUND_LANCE) {
                if (tick == 26) {
                    RimeLanceEntity lance = new RimeLanceEntity(level, aggregate);
                    Vec3 aim = target.getEyePosition().subtract(lance.position()).normalize();
                    lance.shoot(aim.x, aim.y, aim.z,
                            aggregate.hasDominantTrait(AggregateLineage.RIMEBOUND)
                                    ? 1.32F : 1.12F, 1.8F);
                    level.addFreshEntity(lance);
                }
                return;
            }
            if (tick == 18) aggregate.setRimeboundSubmerged(true);
            if (tick >= 19 && tick <= 34) {
                Vec3 direction = target.position().subtract(aggregate.position())
                        .multiply(1.0D, 0.0D, 1.0D).normalize();
                BlockPos next = BlockPos.containing(aggregate.position().add(direction.scale(1.5D)));
                if (level.hasChunkAt(next) && !level.getBlockState(next.below()).isAir()) {
                    aggregate.setDeltaMovement(direction.scale(0.62D));
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                    level.getBlockState(next.below())),
                            aggregate.getX(), aggregate.getY() + 0.1D, aggregate.getZ(),
                            10, 0.8D, 0.15D, 0.8D, 0.08D);
                }
                if (aggregate.distanceToSqr(target) < 12.25D && tick % 5 == 0) {
                    target.hurt(level.damageSources().mobAttack(aggregate),
                            aggregate.hasDominantTrait(AggregateLineage.RIMEBOUND)
                                    ? 11.0F : 9.0F);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            50, 1));
                }
            }
            if (tick == 35) {
                aggregate.setRimeboundSubmerged(false);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        aggregate.getX(), aggregate.getY() + 0.4D, aggregate.getZ(),
                        46, 1.6D, 0.5D, 1.6D, 0.11D);
            }
        }
    }

    private static final class ResonantTrait extends BaseTrait {
        private ResonantTrait() {
            super(AggregateLineage.RESONANT);
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
            aggregate.beginAction(AggregateAction.RESONANCE_PULSE, 54);
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
            if (tick == 8) {
                aggregate.playSound(ModSounds.AGGREGATE_TRAIT_RESONANT.get(), 2.1F, 0.58F);
            }
            if (tick == 30) {
                level.sendParticles(ParticleTypes.SONIC_BOOM, aggregate.getX(),
                        aggregate.getY() + 1.6D, aggregate.getZ(), 12,
                        3.0D, 1.2D, 3.0D, 0.0D);
                selectNoisyTarget(level, aggregate);
            }
        }

        private void selectNoisyTarget(ServerLevel level, AggregateEntity aggregate) {
            double radius = aggregate.hasDominantTrait(AggregateLineage.RESONANT)
                    ? 26.0D : 20.0D;
            Map<UUID, Integer> movementEvents = new HashMap<>();
            for (ResonanceEventManager.Event event : ResonanceEventManager.query(
                    level, aggregate.position(), radius, 0L)) {
                if (event.sourceUuid() == null || (event.type() != ResonanceEventManager.Type.WALK
                        && event.type() != ResonanceEventManager.Type.SPRINT
                        && event.type() != ResonanceEventManager.Type.LAND)) continue;
                movementEvents.merge(event.sourceUuid(), 1, Integer::sum);
            }
            int required = aggregate.hasDominantTrait(AggregateLineage.RESONANT) ? 1 : 2;
            movementEvents.entrySet().stream()
                    .filter(entry -> entry.getValue() >= required)
                    .map(entry -> level.getPlayerByUUID(entry.getKey()))
                    .filter(java.util.Objects::nonNull)
                    .filter(AggregateTraits::combatPlayer)
                    .min(java.util.Comparator.comparingDouble(aggregate::distanceToSqr))
                    .ifPresent(player -> aggregate.getPersistentData().putUUID(
                            "resonancePriority", player.getUUID()));
        }
    }

    private static final class RemnantTrait extends BaseTrait {
        private RemnantTrait() {
            super(AggregateLineage.REMNANT);
        }

        @Override
        public boolean canStart(AggregateEntity aggregate, LivingEntity target) {
            return super.canStart(aggregate, target)
                    && !aggregate.getPersistentData().getBoolean("remnantFalseOpeningUsed");
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
            aggregate.getPersistentData().putBoolean("remnantFalseOpeningUsed", true);
            aggregate.beginAction(AggregateAction.FALSE_OPENING, 64);
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
            if (tick == 18) {
                aggregate.playSound(ModSounds.AGGREGATE_TRAIT_REMNANT.get(), 1.8F, 0.72F);
                Vec3 behind = target.getLookAngle().multiply(-3.0D, 0.0D, -3.0D);
                BlockPos center = BlockPos.containing(target.position().add(behind));
                placeTemporaryWall(level, center, aggregate,
                        aggregate.hasDominantTrait(AggregateLineage.REMNANT) ? 2 : 1);
            }
            if (tick == 38 && aggregate.distanceToSqr(target) <= 64.0D) {
                target.hurt(level.damageSources().mobAttack(aggregate),
                        aggregate.hasDominantTrait(AggregateLineage.REMNANT) ? 9.0F : 7.0F);
                Vec3 pull = aggregate.position().subtract(target.position())
                        .normalize().scale(0.75D);
                target.setDeltaMovement(pull.x, 0.18D, pull.z);
            }
        }
    }

    private static final class FrostwritheTrait extends BaseTrait {
        private FrostwritheTrait() {
            super(AggregateLineage.FROSTWRITHE);
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
            aggregate.beginAction(AggregateAction.DISASSEMBLY, 88);
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
            if (tick == 14) {
                aggregate.playSound(ModSounds.AGGREGATE_TRAIT_FROSTWRITHE.get(), 2.0F, 0.8F);
                aggregate.getPersistentData().putFloat("fragmentHealing", 0.0F);
                int active = level.getEntitiesOfClass(AggregateFragmentEntity.class,
                        aggregate.getBoundingBox().inflate(32.0D), fragment ->
                                aggregate.getUUID().equals(fragment.ownerId())).size();
                int maximum = 6;
                for (int i = active; i < maximum; i++) {
                    AggregateFragmentEntity fragment = ModEntities.AGGREGATE_FRAGMENT.get().create(level);
                    if (fragment == null) continue;
                    double angle = Math.PI * 2.0D * i / 6.0D;
                    fragment.initialize(aggregate, i);
                    fragment.moveTo(aggregate.getX() + Math.cos(angle) * 2.2D,
                            aggregate.getY() + 0.4D, aggregate.getZ() + Math.sin(angle) * 2.2D,
                            (float) Math.toDegrees(angle), 0.0F);
                    level.addFreshEntity(fragment);
                }
            }
        }
    }

    private static final class ArchitectTrait extends BaseTrait {
        private ArchitectTrait() {
            super(AggregateLineage.ARCHITECT);
        }

        @Override
        public boolean canStart(AggregateEntity aggregate, LivingEntity target) {
            return super.canStart(aggregate, target)
                    && !aggregate.getPersistentData().getBoolean("architectAccretionUsed");
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
            aggregate.getPersistentData().putBoolean("architectAccretionUsed", true);
            aggregate.beginAction(AggregateAction.ACCRETION_CONSTRUCTION, 72);
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
            if (tick == 18) {
                aggregate.playSound(ModSounds.AGGREGATE_TRAIT_ARCHITECT.get(), 2.0F, 0.62F);
                Vec3 side = target.position().subtract(aggregate.position()).normalize();
                int radius = aggregate.hasDominantTrait(AggregateLineage.ARCHITECT) ? 2 : 1;
                for (int i = -radius; i <= radius; i++) {
                    BlockPos base = BlockPos.containing(target.position().add(
                            side.z * i * 1.5D, 0.0D, -side.x * i * 1.5D));
                    placeTemporaryColumn(level, base, 2 + Math.floorMod(i, 2), aggregate);
                }
            }
        }
    }

    private static final class UndoneTrait extends BaseTrait {
        private UndoneTrait(AggregateLineage lineage) {
            super(lineage);
        }

        @Override
        public boolean canStart(AggregateEntity aggregate, LivingEntity target) {
            return false;
        }

        @Override
        public void start(AggregateEntity aggregate, LivingEntity target) {
        }

        @Override
        public void tick(ServerLevel level, AggregateEntity aggregate,
                         LivingEntity target, int tick) {
        }
    }

    private static void placeTemporaryWall(ServerLevel level, BlockPos center,
                                           AggregateEntity aggregate, int radius) {
        Vec3 look = aggregate.getLookAngle();
        Direction facing = Direction.getNearest(look.x, 0.0D, look.z);
        Direction side = facing.getClockWise();
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos pos = center.relative(side, x).above(y);
                placeTemporary(level, pos, aggregate);
            }
        }
    }

    private static void placeTemporaryColumn(ServerLevel level, BlockPos base,
                                             int height, AggregateEntity aggregate) {
        for (int y = 0; y < height; y++) placeTemporary(level, base.above(y), aggregate);
    }

    private static void placeTemporary(ServerLevel level, BlockPos pos,
                                       AggregateEntity aggregate) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()
                || !level.noCollision(new AABB(pos))
                || PlayerPlacedBlockTracker.get(level.getServer())
                .hasPlayerPlacedWithin(pos, 4, 3)
                || HearthProtectionPolicy.isEnvironmentalMutationProtected(
                ReturnedHearthSavedData.get(level.getServer()), pos)
                || ChunkCatchUpManager.isBloomOrsaProtected(level, pos)) return;
        BlockState state = ModBlocks.AGGREGATE_TEMPORARY_MASS.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        data.addTemporaryBlock(pos);
    }

    private static boolean combatPlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }
}
