package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.compat.curios.CuriosCompat;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.mixin.LivingEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IceClawsHandler {

    private static final TagKey<Block> ICE_CLIMBABLE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ice_climbable"));

    private static final Map<UUID, Double> CLIMBED_DISTANCE = new HashMap<>();
    private static final Map<UUID, Double> LAST_CLIMB_Y = new HashMap<>();

    private IceClawsHandler() {
    }

    static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean climbing = isClimbing(player);
        if (!climbing) {
            CLIMBED_DISTANCE.remove(id);
            LAST_CLIMB_Y.remove(id);
            return;
        }

        double currentY = player.getY();
        double lastY = LAST_CLIMB_Y.getOrDefault(id, currentY);
        if (currentY > lastY) {
            double climbed = CLIMBED_DISTANCE.getOrDefault(id, 0.0D) + (currentY - lastY);
            CLIMBED_DISTANCE.put(id, climbed);
            if (climbed >= 10.0D) {
                WorldTickHandler.grantAdvancement(player, "claw_your_way_up");
            }
        }

        LAST_CLIMB_Y.put(id, currentY);
    }

    public static boolean shouldTreatAsClimbable(LivingEntity entity) {
        return hasFunctionalIceClaws(entity)
                && entity.horizontalCollision
                && hasAdjacentClimbableSurface(entity)
                && (entity.isShiftKeyDown() || isClimbJumpActive(entity));
    }

    public static boolean isClimbing(LivingEntity entity) {
        return hasFunctionalIceClaws(entity)
                && entity.horizontalCollision
                && hasAdjacentClimbableSurface(entity)
                && (entity.isShiftKeyDown() || isClimbJumpActive(entity) || entity.getDeltaMovement().y > 0.02D);
    }

    public static boolean isClimbJumpActive(LivingEntity entity) {
        return ((LivingEntityAccessor) entity).frozendawn$isJumping();
    }

    public static double getClimbVelocity(LivingEntity entity) {
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return IceClawsLogic.getClimbVelocity(movementSpeed);
    }

    private static boolean hasFunctionalIceClaws(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }

        return CuriosCompat.hasIceClawsEquipped(player)
                && entity.isAlive()
                && !entity.isSpectator()
                && !entity.isFallFlying()
                && !entity.isInWaterOrBubble()
                && !entity.isInLava()
                && !isGrabbedByHollow(entity);
    }

    private static boolean isGrabbedByHollow(LivingEntity entity) {
        for (var passenger : entity.getPassengers()) {
            if (passenger instanceof HollowEntity) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAdjacentClimbableSurface(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        int minY = Mth.floor(box.minY + 0.001D);
        int maxY = Mth.floor(box.maxY - 0.001D);

        return hasClimbableFace(entity, Direction.EAST, Mth.floor(box.maxX + 0.001D), minY, maxY)
                || hasClimbableFace(entity, Direction.WEST, Mth.floor(box.minX - 0.001D), minY, maxY)
                || hasClimbableFace(entity, Direction.SOUTH, Mth.floor(box.maxZ + 0.001D), minY, maxY)
                || hasClimbableFace(entity, Direction.NORTH, Mth.floor(box.minZ - 0.001D), minY, maxY);
    }

    private static boolean hasClimbableFace(LivingEntity entity, Direction direction, int axisPos, int minY, int maxY) {
        AABB box = entity.getBoundingBox();
        int minOrtho = direction.getAxis() == Direction.Axis.X
                ? Mth.floor(box.minZ + 0.001D)
                : Mth.floor(box.minX + 0.001D);
        int maxOrtho = direction.getAxis() == Direction.Axis.X
                ? Mth.floor(box.maxZ - 0.001D)
                : Mth.floor(box.maxX - 0.001D);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int ortho = minOrtho; ortho <= maxOrtho; ortho++) {
                if (direction.getAxis() == Direction.Axis.X) {
                    pos.set(axisPos, y, ortho);
                } else {
                    pos.set(ortho, y, axisPos);
                }

                if (entity.level().getBlockState(pos).is(ICE_CLIMBABLE)) {
                    return true;
                }
            }
        }

        return false;
    }
}
