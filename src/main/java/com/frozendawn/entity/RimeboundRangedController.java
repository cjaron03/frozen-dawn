package com.frozendawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/** Owns lance caps, launch behavior, and embedded-anchor scoring. */
public final class RimeboundRangedController {
    public static final int MAX_ANCHORS = 3;

    public void fire(ServerLevel level, RimeboundEntity owner, LivingEntity target) {
        List<RimeLanceEntity> existing = ownedLances(level, owner);
        if (existing.size() >= MAX_ANCHORS) {
            existing.stream().min(Comparator.comparingInt(entity -> entity.tickCount))
                    .ifPresent(RimeLanceEntity::discard);
        }
        RimeLanceEntity lance = new RimeLanceEntity(level, owner);
        lance.setPos(owner.getX(), owner.getEyeY() - 0.25D, owner.getZ());
        double dy = target.getY(0.45D) - lance.getY();
        lance.shoot(target.getX() - owner.getX(), dy,
                target.getZ() - owner.getZ(), 1.05F, 0.8F);
        level.addFreshEntity(lance);
    }

    @Nullable
    public BlockPos preferredAnchor(ServerLevel level, RimeboundEntity owner) {
        return ownedLances(level, owner).stream()
                .filter(RimeLanceEntity::isEmbedded)
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .map(RimeLanceEntity::blockPosition)
                .orElse(null);
    }

    public void discardOwned(ServerLevel level, RimeboundEntity owner) {
        ownedLances(level, owner).forEach(RimeLanceEntity::discard);
    }

    private static List<RimeLanceEntity> ownedLances(
            ServerLevel level, RimeboundEntity owner) {
        return level.getEntitiesOfClass(RimeLanceEntity.class,
                owner.getBoundingBox().inflate(96.0D),
                lance -> lance.getOwner() == owner);
    }
}
