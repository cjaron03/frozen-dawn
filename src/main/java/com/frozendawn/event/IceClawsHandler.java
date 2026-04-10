package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.compat.curios.CuriosCompat;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.network.IceClawsInputState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IceClawsHandler {

    public static final byte FACE_NONE = -1;

    private static final TagKey<Block> ICE_CLIMBABLE = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ice_climbable"));
    private static final double[] LOCAL_PROBE_DEPTHS = {0.02D, 0.16D, 0.30D, 0.46D};
    private static final double PROBE_VERTICAL_MARGIN = 0.10D;
    private static final double WALL_SNAP_OFFSET = 0.03D;
    private static final double MAX_SNAP_PER_TICK = 0.18D;
    private static final double MIN_VERTICAL_OVERLAP = 0.12D;
    private static final double MIN_LATERAL_OVERLAP = 0.12D;
    private static final double MAX_ANCHOR_GAP = 0.28D;

    private static final Map<UUID, Double> CLIMBED_DISTANCE = new HashMap<>();
    private static final Map<UUID, Double> LAST_CLIMB_Y = new HashMap<>();
    private static final Map<UUID, Boolean> CLIMB_JUMP_HELD = new HashMap<>();
    private static final Map<UUID, ClimbAnchor> CLIMB_ANCHORS = new HashMap<>();

    private IceClawsHandler() {
    }

    static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!isCustomClimbActive(player)) {
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

    public static void onPlayerLogout(ServerPlayer player) {
        UUID id = player.getUUID();
        CLIMBED_DISTANCE.remove(id);
        LAST_CLIMB_Y.remove(id);
        CLIMB_JUMP_HELD.remove(id);
        CLIMB_ANCHORS.remove(id);
    }

    public static void setClimbInput(ServerPlayer player, boolean climbJumpHeld, BlockPos anchorPos, Direction wallSide) {
        UUID id = player.getUUID();
        if (climbJumpHeld) {
            CLIMB_JUMP_HELD.put(id, true);
        } else {
            CLIMB_JUMP_HELD.remove(id);
        }

        if (anchorPos != null && wallSide != null && wallSide.getAxis().isHorizontal()) {
            CLIMB_ANCHORS.put(id, new ClimbAnchor(anchorPos.immutable(), wallSide));
        } else {
            CLIMB_ANCHORS.remove(id);
        }
    }

    public static boolean isClimbJumpActive(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return IceClawsInputState.isClientJumpHeld();
        }
        if (entity instanceof ServerPlayer player) {
            return CLIMB_JUMP_HELD.getOrDefault(player.getUUID(), false);
        }
        return false;
    }

    public static double getClimbVelocity(LivingEntity entity) {
        double movementSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return IceClawsLogic.getClimbVelocity(movementSpeed);
    }

    public static ClimbAnchor detectLocalAnchor(Player player) {
        if (!hasFunctionalIceClaws(player)) {
            return null;
        }
        return findClosestAnchor(player, player.getBoundingBox(), true);
    }

    public static boolean handleCustomTravel(LivingEntity entity) {
        if (!(entity instanceof Player player) || !hasFunctionalIceClaws(player)) {
            return false;
        }

        boolean jumpHeld = isClimbJumpActive(player);
        boolean holdingPosition = player.isShiftKeyDown();
        if (!jumpHeld && !holdingPosition) {
            return false;
        }

        ClimbAnchor anchor = getResolvedAnchor(player);
        if (anchor == null) {
            return false;
        }

        AABB wallBox = getAnchorCollisionBox(player, anchor);
        if (wallBox == null) {
            return false;
        }

        Vec2 pinned = getPinnedHorizontalPosition(player, wallBox, anchor.wallSide());
        double correctionX = Mth.clamp(pinned.x - (float) player.getX(), -MAX_SNAP_PER_TICK, MAX_SNAP_PER_TICK);
        double correctionZ = Mth.clamp(pinned.y - (float) player.getZ(), -MAX_SNAP_PER_TICK, MAX_SNAP_PER_TICK);
        double climbVelocity = jumpHeld ? getClimbVelocity(player) : 0.0D;

        Vec3 requested = new Vec3(correctionX, climbVelocity, correctionZ);
        Vec3 startPos = player.position();
        player.setDeltaMovement(requested);
        player.move(MoverType.SELF, requested);
        Vec3 actual = player.position().subtract(startPos);
        player.setDeltaMovement(actual.x, actual.y, actual.z);
        player.resetFallDistance();
        player.calculateEntityAnimation(false);
        return true;
    }

    public static boolean isCustomClimbActive(LivingEntity entity) {
        if (!(entity instanceof Player player) || !hasFunctionalIceClaws(player)) {
            return false;
        }
        if (!isClimbJumpActive(player) && !player.isShiftKeyDown()) {
            return false;
        }
        return getResolvedAnchor(player) != null;
    }

    public static Direction decodeWallSide(byte wallSide2d) {
        return wallSide2d == FACE_NONE ? null : Direction.from2DDataValue(wallSide2d);
    }

    private static boolean hasFunctionalIceClaws(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return CuriosCompat.hasIceClawsEquipped(player)
                && entity.isAlive()
                && !entity.isSpectator()
                && !entity.isFallFlying()
                && !entity.isInWaterOrBubble()
                && !entity.isInLava()
                && !player.isPassenger()
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

    private static ClimbAnchor getResolvedAnchor(LivingEntity entity) {
        ClimbAnchor anchor = getStoredAnchor(entity);
        if (anchor == null) {
            return null;
        }
        return isAnchorValid(entity, anchor) ? anchor : null;
    }

    private static ClimbAnchor getStoredAnchor(LivingEntity entity) {
        if (entity.level().isClientSide && entity instanceof Player) {
            return IceClawsInputState.getClientAnchor();
        }
        if (entity instanceof ServerPlayer player) {
            return CLIMB_ANCHORS.get(player.getUUID());
        }
        return null;
    }

    private static ClimbAnchor findClosestAnchor(LivingEntity entity, AABB entityBox, boolean localProbe) {
        AnchorCandidate best = null;
        double[] ySamples = uniqueSamples(
                entityBox.minY + 0.15D,
                (entityBox.minY + entityBox.maxY) * 0.5D,
                entityBox.maxY - 0.15D
        );
        double[] xSamples = uniqueSamples(
                entityBox.minX + 0.15D,
                entity.getX(),
                entityBox.maxX - 0.15D
        );
        double[] zSamples = uniqueSamples(
                entityBox.minZ + 0.15D,
                entity.getZ(),
                entityBox.maxZ - 0.15D
        );

        for (Direction wallSide : Direction.Plane.HORIZONTAL) {
            double[] lateralSamples = wallSide.getAxis() == Direction.Axis.X ? zSamples : xSamples;
            double[] probeDepths = localProbe ? LOCAL_PROBE_DEPTHS : new double[] {0.02D, 0.16D, 0.30D, 0.46D, 0.62D};
            for (double depth : probeDepths) {
                for (double y : ySamples) {
                    for (double lateral : lateralSamples) {
                        BlockPos pos = sampleBlock(entityBox, wallSide, depth, y, lateral);
                        AABB wallBox = getCollisionBox(entity, pos);
                        if (wallBox == null) {
                            continue;
                        }

                        double verticalOverlap = overlap(
                                entityBox.minY + PROBE_VERTICAL_MARGIN,
                                entityBox.maxY - PROBE_VERTICAL_MARGIN,
                                wallBox.minY,
                                wallBox.maxY
                        );
                        if (verticalOverlap < MIN_VERTICAL_OVERLAP) {
                            continue;
                        }

                        double lateralOverlap = getLateralOverlap(entityBox, wallBox, wallSide);
                        if (lateralOverlap < MIN_LATERAL_OVERLAP) {
                            continue;
                        }

                        double wallGap = getWallGap(entityBox, wallBox, wallSide);
                        double maxGap = localProbe ? depth + 0.08D : MAX_ANCHOR_GAP;
                        if (wallGap < -0.08D || wallGap > maxGap) {
                            continue;
                        }

                        AnchorCandidate candidate = new AnchorCandidate(
                                new ClimbAnchor(pos.immutable(), wallSide),
                                Math.max(0.0D, wallGap)
                        );
                        if (best == null || candidate.distance() < best.distance()) {
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best == null ? null : best.anchor();
    }

    private static boolean isAnchorValid(LivingEntity entity, ClimbAnchor anchor) {
        AABB wallBox = getAnchorCollisionBox(entity, anchor);
        if (wallBox == null) {
            return false;
        }
        AABB entityBox = entity.getBoundingBox();
        double verticalOverlap = overlap(
                entityBox.minY + PROBE_VERTICAL_MARGIN,
                entityBox.maxY - PROBE_VERTICAL_MARGIN,
                wallBox.minY,
                wallBox.maxY
        );
        if (verticalOverlap < MIN_VERTICAL_OVERLAP) {
            return false;
        }
        if (getLateralOverlap(entityBox, wallBox, anchor.wallSide()) < MIN_LATERAL_OVERLAP) {
            return false;
        }
        double wallGap = getWallGap(entityBox, wallBox, anchor.wallSide());
        return wallGap >= -0.08D && wallGap <= MAX_ANCHOR_GAP;
    }

    private static AABB getAnchorCollisionBox(LivingEntity entity, ClimbAnchor anchor) {
        return getCollisionBox(entity, anchor.pos());
    }

    private static AABB getCollisionBox(LivingEntity entity, BlockPos pos) {
        var level = entity.level();
        var state = level.getBlockState(pos);
        if (!state.is(ICE_CLIMBABLE)) {
            return null;
        }
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
    }

    private static double getWallGap(AABB entityBox, AABB wallBox, Direction wallSide) {
        return switch (wallSide) {
            case EAST -> wallBox.minX - entityBox.maxX;
            case WEST -> entityBox.minX - wallBox.maxX;
            case SOUTH -> wallBox.minZ - entityBox.maxZ;
            case NORTH -> entityBox.minZ - wallBox.maxZ;
            default -> Double.MAX_VALUE;
        };
    }

    private static double getLateralOverlap(AABB entityBox, AABB wallBox, Direction wallSide) {
        return switch (wallSide.getAxis()) {
            case X -> overlap(entityBox.minZ, entityBox.maxZ, wallBox.minZ, wallBox.maxZ);
            case Z -> overlap(entityBox.minX, entityBox.maxX, wallBox.minX, wallBox.maxX);
            default -> 0.0D;
        };
    }

    private static double overlap(double minA, double maxA, double minB, double maxB) {
        return Math.max(0.0D, Math.min(maxA, maxB) - Math.max(minA, minB));
    }

    private static BlockPos sampleBlock(AABB entityBox, Direction wallSide, double depth, double y, double lateral) {
        return switch (wallSide) {
            case EAST -> BlockPos.containing(entityBox.maxX + depth, y, lateral);
            case WEST -> BlockPos.containing(entityBox.minX - depth, y, lateral);
            case SOUTH -> BlockPos.containing(lateral, y, entityBox.maxZ + depth);
            case NORTH -> BlockPos.containing(lateral, y, entityBox.minZ - depth);
            default -> BlockPos.ZERO;
        };
    }

    private static double[] uniqueSamples(double first, double second, double third) {
        double[] values = new double[] { first, second, third };
        java.util.Arrays.sort(values);
        int count = 0;
        double[] deduped = new double[3];
        for (double value : values) {
            if (count == 0 || Math.abs(deduped[count - 1] - value) > 1.0E-4D) {
                deduped[count++] = value;
            }
        }
        return java.util.Arrays.copyOf(deduped, count);
    }

    private static Vec2 getPinnedHorizontalPosition(Player player, AABB wallBox, Direction wallSide) {
        double halfWidth = player.getBbWidth() * 0.5D;
        double targetX = player.getX();
        double targetZ = player.getZ();

        switch (wallSide) {
            case EAST -> {
                targetX = wallBox.minX - halfWidth - WALL_SNAP_OFFSET;
                targetZ = Mth.clamp(player.getZ(), wallBox.minZ + halfWidth, wallBox.maxZ - halfWidth);
            }
            case WEST -> {
                targetX = wallBox.maxX + halfWidth + WALL_SNAP_OFFSET;
                targetZ = Mth.clamp(player.getZ(), wallBox.minZ + halfWidth, wallBox.maxZ - halfWidth);
            }
            case SOUTH -> {
                targetZ = wallBox.minZ - halfWidth - WALL_SNAP_OFFSET;
                targetX = Mth.clamp(player.getX(), wallBox.minX + halfWidth, wallBox.maxX - halfWidth);
            }
            case NORTH -> {
                targetZ = wallBox.maxZ + halfWidth + WALL_SNAP_OFFSET;
                targetX = Mth.clamp(player.getX(), wallBox.minX + halfWidth, wallBox.maxX - halfWidth);
            }
            default -> {
            }
        }
        return new Vec2((float) targetX, (float) targetZ);
    }

    public record ClimbAnchor(BlockPos pos, Direction wallSide) {
        public byte encodedWallSide() {
            return (byte) wallSide.get2DDataValue();
        }
    }

    private record AnchorCandidate(ClimbAnchor anchor, double distance) {
    }
}
