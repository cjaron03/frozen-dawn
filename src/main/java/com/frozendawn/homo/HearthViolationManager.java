package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts explicit protected-space interactions into persistent hive memory.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HearthViolationManager {
    private static final Map<UUID, BlockPos> previousPlayerPositions = new HashMap<>();
    private static long entriesRecorded;
    private static long doorsRecorded;
    private static long containersRecorded;
    private static long blocksRecorded;

    private HearthViolationManager() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        HearthProtectionPolicy.ProtectedTarget target = HearthProtectionPolicy
                .protectedTargetAt(data, event.getPos()).orElse(null);
        if (target == null) {
            return;
        }

        ReturnedHearthSavedData.HearthViolationReason reason = null;
        if (level.getBlockState(event.getPos()).getBlock() instanceof DoorBlock) {
            reason = ReturnedHearthSavedData.HearthViolationReason.PROTECTED_DOOR;
        } else if (level.getBlockEntity(event.getPos()) instanceof Container) {
            reason = ReturnedHearthSavedData.HearthViolationReason.PROTECTED_CONTAINER;
        }
        if (reason != null) {
            record(level, player, target.hearthId(), reason);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        HearthProtectionPolicy.ProtectedTarget target = HearthProtectionPolicy
                .protectedTargetAt(data, event.getPos()).orElse(null);
        if (target != null) {
            record(level, player, target.hearthId(),
                    ReturnedHearthSavedData.HearthViolationReason.PROTECTED_BLOCK_BREAK);
        }
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            online.add(player.getUUID());
            BlockPos current = player.blockPosition();
            BlockPos previous = previousPlayerPositions.put(player.getUUID(), current.immutable());
            if (previous == null || previous.equals(current)) {
                continue;
            }
            UUID currentHearth = HearthProtectionPolicy.protectedInteriorAt(data, current)
                    .orElse(null);
            if (currentHearth == null) {
                continue;
            }
            UUID previousHearth = HearthProtectionPolicy.protectedInteriorAt(data, previous)
                    .orElse(null);
            if (!currentHearth.equals(previousHearth)) {
                record(level, player, currentHearth,
                        ReturnedHearthSavedData.HearthViolationReason.PROTECTED_ENTRY);
            }
        }
        previousPlayerPositions.keySet().retainAll(online);
    }

    public static String statusLine() {
        return "entries=" + entriesRecorded
                + " doors=" + doorsRecorded
                + " containers=" + containersRecorded
                + " blocks=" + blocksRecorded;
    }

    public static void reset() {
        previousPlayerPositions.clear();
        entriesRecorded = 0L;
        doorsRecorded = 0L;
        containersRecorded = 0L;
        blocksRecorded = 0L;
    }

    private static void record(ServerLevel level, ServerPlayer player, UUID hearthId,
                               ReturnedHearthSavedData.HearthViolationReason reason) {
        if (!HearthMemoryManager.recordProtectedViolation(
                level, hearthId, player, reason)) {
            return;
        }
        switch (reason) {
            case PROTECTED_ENTRY -> entriesRecorded++;
            case PROTECTED_DOOR -> doorsRecorded++;
            case PROTECTED_CONTAINER -> containersRecorded++;
            case PROTECTED_BLOCK_BREAK -> blocksRecorded++;
            case ENTITY_ATTACK -> {
                // Entity attacks are counted by HearthMemoryManager.
            }
        }
    }
}
