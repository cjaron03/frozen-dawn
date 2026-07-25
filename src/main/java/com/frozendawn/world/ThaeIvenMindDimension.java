package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Shared keys, arena construction, and player-return data for Thae Iven. */
public final class ThaeIvenMindDimension {
    public static final ResourceKey<Level> LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "thae_iven"));

    public static final int ARENA_Y = 64;
    public static final int ARENA_RADIUS = 20;
    public static final int BARRIER_RADIUS = 18;
    public static final int FAILURE_Y = 42;
    public static final int SANCTUARY_Z_OFFSET = 10;
    public static final double SANCTUARY_RADIUS = 3.25D;

    private static final String PERSISTED_ROOT = "FrozenDawnThaeIvenMind";
    private static final String ORIGIN_DIMENSION = "OriginDimension";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String ORIGIN_Z = "OriginZ";
    private static final String ORIGIN_YAW = "OriginYaw";
    private static final String ORIGIN_PITCH = "OriginPitch";
    private static final String MASTER_ID = "MasterId";

    private ThaeIvenMindDimension() {
    }

    public static boolean isMindLevel(Level level) {
        return level != null && level.dimension() == LEVEL_KEY;
    }

    /** Gives each Master a distant fixed stage without creating forced world chunks. */
    public static BlockPos arenaCenter(UUID masterId) {
        long mixed = masterId.getMostSignificantBits()
                ^ Long.rotateLeft(masterId.getLeastSignificantBits(), 23);
        int gridX = Math.floorMod((int) mixed, 96) - 48;
        int gridZ = Math.floorMod((int) (mixed >>> 32), 96) - 48;
        return new BlockPos(gridX * 192, ARENA_Y, gridZ * 192);
    }

    public static Vec3 playerEntry(BlockPos center, int index, int count) {
        double spread = count <= 1 ? 0.0D : (index - (count - 1) * 0.5D) * 1.7D;
        return new Vec3(center.getX() + spread + 0.5D, ARENA_Y + 2.0D,
                center.getZ() + 13.5D);
    }

    public static Vec3 masterPosition(BlockPos center) {
        return new Vec3(center.getX() + 0.5D, ARENA_Y + 2.0D, center.getZ() + 0.5D);
    }

    /** Builds a noninteractive memory-stage once for this Master. */
    public static void ensureArena(ServerLevel level, UUID masterId) {
        BlockPos center = arenaCenter(masterId);
        BlockPos marker = center.offset(0, -4, 0);
        if (level.getBlockState(marker).is(Blocks.BEDROCK)) {
            ensureSanctuary(level, center);
            ensureArenaBarrier(level, center);
            return;
        }

        level.setBlock(marker, Blocks.BEDROCK.defaultBlockState(), 2);
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                int edgeNoise = Math.floorMod(hash(masterId, x, z), 5) - 2;
                int localRadius = ARENA_RADIUS - 2 + edgeNoise;
                int distanceSquared = x * x + z * z;
                if (distanceSquared > localRadius * localRadius) {
                    continue;
                }
                boolean centralPath = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                boolean memoryGap = !centralPath
                        && distanceSquared > 90
                        && Math.floorMod(hash(masterId, x * 3, z * 5), 31) == 0;
                if (memoryGap) {
                    continue;
                }

                BlockState foundation = Math.floorMod(hash(masterId, x, z), 7) == 0
                        ? Blocks.PACKED_ICE.defaultBlockState()
                        : ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState();
                BlockState surface = Math.floorMod(hash(masterId, z, x), 9) < 3
                        ? Blocks.PACKED_ICE.defaultBlockState()
                        : ModBlocks.FROZEN_DIRT.get().defaultBlockState();
                level.setBlock(center.offset(x, -1, z), foundation, 2);
                level.setBlock(center.offset(x, 0, z), surface, 2);
                if (Math.floorMod(hash(masterId, x + 17, z - 9), 13) == 0) {
                    level.setBlock(center.offset(x, 1, z), Blocks.SNOW.defaultBlockState(), 2);
                }
            }
        }

        buildFoundingDoor(level, center.offset(-10, 1, -4), Direction.EAST);
        buildFoundingDoor(level, center.offset(11, 1, 5), Direction.WEST);
        buildRepeatedHearthMemory(level, center.offset(-14, 5, 6), Direction.SOUTH);
        buildRepeatedHearthMemory(level, center.offset(10, 8, -12), Direction.WEST);
        buildRepeatedHearthMemory(level, center.offset(2, 11, 15), Direction.NORTH);
        buildOrsaCrossSection(level, center.offset(13, 7, 11));
        buildStairToNothing(level, center.offset(-7, 1, 12));
        ensureSanctuary(level, center);
        ensureArenaBarrier(level, center);

        FrozenDawn.LOGGER.info(
                "Built Thae Iven memory-stage for Master {} at {},{},{}",
                shortId(masterId), center.getX(), center.getY(), center.getZ());
    }

    public static void storeOrigin(ServerPlayer player, UUID masterId) {
        CompoundTag root = persistedRoot(player, true);
        root.putString(ORIGIN_DIMENSION, player.level().dimension().location().toString());
        root.putDouble(ORIGIN_X, player.getX());
        root.putDouble(ORIGIN_Y, player.getY());
        root.putDouble(ORIGIN_Z, player.getZ());
        root.putFloat(ORIGIN_YAW, player.getYRot());
        root.putFloat(ORIGIN_PITCH, player.getXRot());
        root.putUUID(MASTER_ID, masterId);
        savePersistedRoot(player, root);
    }

    public static boolean hasStoredOrigin(ServerPlayer player) {
        CompoundTag root = persistedRoot(player, false);
        return root.contains(ORIGIN_DIMENSION) && root.hasUUID(MASTER_ID);
    }

    public static UUID storedMasterId(ServerPlayer player) {
        CompoundTag root = persistedRoot(player, false);
        return root.hasUUID(MASTER_ID) ? root.getUUID(MASTER_ID) : null;
    }

    public static BlockPos sanctuaryPosition(UUID masterId) {
        return arenaCenter(masterId).offset(0, 1, SANCTUARY_Z_OFFSET);
    }

    public static boolean isInsideSanctuary(Player player, UUID masterId) {
        if (!isMindLevel(player.level())) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(sanctuaryPosition(masterId));
        Vec3 offset = player.position().subtract(center);
        return offset.x * offset.x + offset.z * offset.z
                        <= SANCTUARY_RADIUS * SANCTUARY_RADIUS
                && Math.abs(offset.y) <= 3.0D;
    }

    public static boolean isInsideStoredSanctuary(ServerPlayer player) {
        UUID masterId = storedMasterId(player);
        return masterId != null && isInsideSanctuary(player, masterId);
    }

    public static boolean returnToOrigin(ServerPlayer player, UUID facingMasterId) {
        CompoundTag root = persistedRoot(player, false);
        if (!root.contains(ORIGIN_DIMENSION)) {
            return false;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(root.getString(ORIGIN_DIMENSION));
        MinecraftServer server = player.getServer();
        if (dimensionId == null || server == null) {
            return false;
        }
        ServerLevel origin = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (origin == null) {
            origin = server.overworld();
        }

        double x = root.getDouble(ORIGIN_X);
        double y = root.getDouble(ORIGIN_Y);
        double z = root.getDouble(ORIGIN_Z);
        float yaw = root.getFloat(ORIGIN_YAW);
        float pitch = root.getFloat(ORIGIN_PITCH);
        if (facingMasterId != null && origin.getEntity(facingMasterId) != null) {
            Vec3 delta = origin.getEntity(facingMasterId).position()
                    .add(0.0D, 1.0D, 0.0D)
                    .subtract(x, y + player.getEyeHeight(), z);
            yaw = (float) (Mth.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
            pitch = (float) (-(Mth.atan2(delta.y, delta.horizontalDistance())
                    * 180.0D / Math.PI));
        }
        player.teleportTo(origin, x, y, z, yaw, pitch);
        clearStoredOrigin(player);
        return true;
    }

    public static void clearStoredOrigin(ServerPlayer player) {
        CompoundTag forgeData = player.getPersistentData();
        CompoundTag persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(PERSISTED_ROOT);
        forgeData.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persistedRoot(ServerPlayer player, boolean create) {
        CompoundTag persisted = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PERSISTED_ROOT) && create) {
            persisted.put(PERSISTED_ROOT, new CompoundTag());
        }
        return persisted.getCompound(PERSISTED_ROOT);
    }

    private static void savePersistedRoot(ServerPlayer player, CompoundTag root) {
        CompoundTag forgeData = player.getPersistentData();
        CompoundTag persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(PERSISTED_ROOT, root);
        forgeData.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void buildFoundingDoor(ServerLevel level, BlockPos base, Direction facing) {
        BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(HorizontalDirectionalBlock.FACING, facing);
        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(base, lower, 2);
        level.setBlock(base.above(), upper, 2);
    }

    private static void buildRepeatedHearthMemory(
            ServerLevel level, BlockPos origin, Direction facing) {
        Direction side = facing.getClockWise();
        for (int width = -3; width <= 3; width++) {
            for (int height = 0; height <= 3; height++) {
                if (Math.abs(width) <= 1 && height <= 2) {
                    continue;
                }
                BlockPos pos = origin.relative(side, width).above(height);
                BlockState state = (Math.abs(width) + height) % 3 == 0
                        ? ModBlocks.FROZEN_PLANKS.get().defaultBlockState()
                        : ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState();
                level.setBlock(pos, state, 2);
            }
        }
    }

    private static void buildOrsaCrossSection(ServerLevel level, BlockPos origin) {
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 4; y++) {
                if (x == 0 || x == 5 || y == 0 || y == 3) {
                    BlockState state = (x + y) % 4 == 0
                            ? Blocks.ORANGE_CONCRETE.defaultBlockState()
                            : Blocks.IRON_BLOCK.defaultBlockState();
                    level.setBlock(origin.offset(x, y, 0), state, 2);
                } else if (y == 2) {
                    level.setBlock(origin.offset(x, y, 0),
                            ModBlocks.INSULATED_GLASS.get().defaultBlockState(), 2);
                }
            }
        }
    }

    private static void buildStairToNothing(ServerLevel level, BlockPos origin) {
        Direction facing = Direction.NORTH;
        for (int step = 0; step < 8; step++) {
            BlockState stair = Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, Half.BOTTOM)
                    .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            level.setBlock(origin.relative(facing, step).above(step), stair, 2);
        }
    }

    private static void ensureSanctuary(ServerLevel level, BlockPos center) {
        BlockPos fire = center.offset(0, 1, SANCTUARY_Z_OFFSET);
        BlockPos floor = fire.below();
        level.setBlock(floor, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(fire, Blocks.SOUL_CAMPFIRE.defaultBlockState(), 2);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos marker = floor.relative(direction, 2);
            level.setBlock(marker, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            level.setBlock(marker.above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void ensureArenaBarrier(ServerLevel level, BlockPos center) {
        int scan = BARRIER_RADIUS + 2;
        double safeFloorSquared = (BARRIER_RADIUS - 1.0D) * (BARRIER_RADIUS - 1.0D);
        double innerSquared = (BARRIER_RADIUS - 0.45D) * (BARRIER_RADIUS - 0.45D);
        double outerSquared = (BARRIER_RADIUS + 0.85D) * (BARRIER_RADIUS + 0.85D);
        for (int x = -scan; x <= scan; x++) {
            for (int z = -scan; z <= scan; z++) {
                double distanceSquared = x * x + z * z;
                if (distanceSquared <= safeFloorSquared
                        && level.getBlockState(center.offset(x, 0, z)).isAir()) {
                    level.setBlock(
                            center.offset(x, -1, z),
                            ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState(),
                            2);
                    level.setBlock(
                            center.offset(x, 0, z),
                            ModBlocks.FROZEN_DIRT.get().defaultBlockState(),
                            2);
                }
                if (distanceSquared < innerSquared || distanceSquared > outerSquared) {
                    continue;
                }
                for (int y = 1; y <= 5; y++) {
                    level.setBlock(
                            center.offset(x, y, z),
                            Blocks.BARRIER.defaultBlockState(),
                            2);
                }
            }
        }
    }

    private static int hash(UUID id, int x, int z) {
        long value = id.getLeastSignificantBits()
                ^ (long) x * 341873128712L
                ^ (long) z * 132897987541L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) value;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
