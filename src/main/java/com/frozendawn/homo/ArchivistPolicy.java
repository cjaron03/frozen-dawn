package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Pure spawn, capacity, and collection-site layout policy for the Archivist. */
public final class ArchivistPolicy {
    public static final int CHECK_INTERVAL_TICKS = 600;
    public static final int REGION_CHUNKS = 32;
    public static final int COLLECTION_RADIUS = 48;
    public static final int ITEM_PICKUP_GRACE_TICKS = 200;
    public static final int ITEM_STALL_TICKS = 600;
    public static final int CARRIED_CAPACITY = 12;
    public static final int GENERAL_SLOTS = 36;
    public static final int BADGE_SLOTS = 8;
    public static final int TOTAL_SLOTS = GENERAL_SLOTS + BADGE_SLOTS;
    public static final long REPLACEMENT_COOLDOWN_TICKS = 24_000L;
    public static final double ADJACENT_EXCLUSION_RADIUS = 512.0D;
    public static final double ATTRACTION_RADIUS = 384.0D;

    private ArchivistPolicy() {
    }

    public static long regionKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return ChunkPos.asLong(Math.floorDiv(chunkX, REGION_CHUNKS),
                Math.floorDiv(chunkZ, REGION_CHUNKS));
    }

    public static boolean canSpawn(boolean erased, boolean released,
                                   boolean occupied, boolean neighborOccupied,
                                   long gameTime, long nextSpawnGameTime) {
        return erased && released && !occupied && !neighborOccupied
                && gameTime >= nextSpawnGameTime;
    }

    public static boolean isBadgeSlot(int slot) {
        return slot >= GENERAL_SLOTS && slot < TOTAL_SLOTS;
    }

    public static int firstSlot(boolean badge, java.util.Set<Integer> occupied) {
        int start = badge ? GENERAL_SLOTS : 0;
        int end = badge ? TOTAL_SLOTS : GENERAL_SLOTS;
        for (int slot = start; slot < end; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public static Vec3 slotPosition(BlockPos anchor, int slot) {
        if (isBadgeSlot(slot)) {
            int index = slot - GENERAL_SLOTS;
            return new Vec3(anchor.getX() + 6.50D,
                    anchor.getY() + 0.12D,
                    anchor.getZ() - 7.0D + index * 2.0D);
        }
        int clamped = Mth.clamp(slot, 0, GENERAL_SLOTS - 1);
        int row = clamped / 6;
        int column = clamped % 6;
        return new Vec3(anchor.getX() - 3.125D + column * 1.25D,
                anchor.getY() + 0.12D,
                anchor.getZ() - 3.125D + row * 1.25D);
    }

    public static float slotYaw(long seed, int slot) {
        long mixed = seed ^ (slot * 0x9E3779B97F4A7C15L);
        return (float) Math.floorMod(mixed >>> 17, 360L);
    }
}
