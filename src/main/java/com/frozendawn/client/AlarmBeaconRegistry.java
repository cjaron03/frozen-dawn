package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AlarmBeaconBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class AlarmBeaconRegistry {

    private static ClientLevel currentLevel;
    private static final Long2ObjectOpenHashMap<AlarmBeaconBlockEntity> beacons = new Long2ObjectOpenHashMap<>();

    private AlarmBeaconRegistry() {
    }

    public static void track(AlarmBeaconBlockEntity beacon) {
        if (!(beacon.getLevel() instanceof ClientLevel level)) {
            return;
        }
        if (currentLevel != level) {
            reset(level);
        }
        if (beacon.isRemoved()) {
            beacons.remove(beacon.getBlockPos().asLong());
            return;
        }
        beacons.put(beacon.getBlockPos().asLong(), beacon);
    }

    public static void untrack(AlarmBeaconBlockEntity beacon) {
        if (currentLevel == null || beacon.getLevel() != currentLevel) {
            return;
        }
        long key = beacon.getBlockPos().asLong();
        AlarmBeaconBlockEntity current = beacons.get(key);
        if (current == beacon) {
            beacons.remove(key);
        }
    }

    public static AlarmBeaconBlockEntity getBeacon(ClientLevel level, BlockPos pos) {
        if (currentLevel != level) {
            reset(level);
            return null;
        }
        AlarmBeaconBlockEntity beacon = beacons.get(pos.asLong());
        if (!isUsable(level, beacon)) {
            beacons.remove(pos.asLong());
            return null;
        }
        return beacon;
    }

    public static List<AlarmBeaconBlockEntity> findNearestActiveBeacons(ClientLevel level, Vec3 origin, float partialTick,
                                                                        int maxBeacons, double maxDistanceSqr) {
        if (currentLevel != level) {
            reset(level);
        }

        List<AlarmBeaconCandidate> candidates = new ArrayList<>(beacons.size());
        var iterator = beacons.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            AlarmBeaconBlockEntity beacon = entry.getValue();
            if (!isUsable(level, beacon)) {
                iterator.remove();
                continue;
            }

            if (!beacon.isEffectivelyRunning(partialTick)) {
                continue;
            }

            double distanceSqr = origin.distanceToSqr(beacon.getHeadWorldPos());
            if (distanceSqr <= maxDistanceSqr) {
                candidates.add(new AlarmBeaconCandidate(beacon, distanceSqr));
            }
        }

        candidates.sort(Comparator.comparingDouble(AlarmBeaconCandidate::distanceSqr));
        int resultSize = Math.min(maxBeacons, candidates.size());
        List<AlarmBeaconBlockEntity> result = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            result.add(candidates.get(index).beacon());
        }
        return result;
    }

    public static void clear() {
        currentLevel = null;
        beacons.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void reset(ClientLevel level) {
        currentLevel = level;
        beacons.clear();
    }

    private static boolean isUsable(ClientLevel level, AlarmBeaconBlockEntity beacon) {
        return beacon != null
                && !beacon.isRemoved()
                && beacon.getLevel() == level
                && level.hasChunkAt(beacon.getBlockPos());
    }

    private record AlarmBeaconCandidate(AlarmBeaconBlockEntity beacon, double distanceSqr) {
    }
}
