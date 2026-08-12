package com.frozendawn.world;

import com.frozendawn.entity.ResonantPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Short-lived, server-only structural vibration authority. */
public final class ResonanceEventManager {
    private static final Map<ServerLevel, Buffer> BUFFERS = new WeakHashMap<>();

    private ResonanceEventManager() {
    }

    public static void emit(ServerLevel level, Vec3 position, float strength,
                            Type type, UUID sourceUuid) {
        if (strength <= 0.0F || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(position))) {
            return;
        }
        Buffer buffer = BUFFERS.computeIfAbsent(level, ignored -> new Buffer());
        prune(buffer, level.getGameTime());
        while (buffer.events.size() >= ResonantPolicy.EVENT_CAP) {
            buffer.events.removeFirst();
        }
        buffer.events.addLast(new Event(++buffer.sequence, position, strength, type,
                sourceUuid, level.getGameTime()));
    }

    public static List<Event> query(ServerLevel level, Vec3 center, double radius,
                                    long afterSequence) {
        Buffer buffer = BUFFERS.get(level);
        if (buffer == null) return List.of();
        prune(buffer, level.getGameTime());
        double radiusSqr = radius * radius;
        List<Event> result = new ArrayList<>();
        for (Event event : buffer.events) {
            if (event.sequence() > afterSequence
                    && event.position().distanceToSqr(center) <= radiusSqr) {
                result.add(event);
            }
        }
        return result;
    }

    public static void clear(ServerLevel level) {
        BUFFERS.remove(level);
    }

    public static int activeEventCount(ServerLevel level) {
        Buffer buffer = BUFFERS.get(level);
        if (buffer == null) return 0;
        prune(buffer, level.getGameTime());
        return buffer.events.size();
    }

    private static void prune(Buffer buffer, long now) {
        while (!buffer.events.isEmpty()
                && now - buffer.events.peekFirst().gameTime()
                > ResonantPolicy.EVENT_LIFETIME_TICKS) {
            buffer.events.removeFirst();
        }
    }

    public enum Type {
        WALK,
        SPRINT,
        LAND,
        DOOR,
        PLACE,
        STONE_MINE,
        METAL_MINE,
        PISTON,
        MACHINERY,
        EXPLOSION,
        PROJECTILE_IMPACT,
        ITEM_IMPACT,
        RESPIRATORY;

        public static Type byName(String name) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(name)) return type;
            }
            return null;
        }
    }

    public record Event(long sequence, Vec3 position, float strength, Type type,
                        UUID sourceUuid, long gameTime) {
    }

    private static final class Buffer {
        private final Deque<Event> events = new ArrayDeque<>();
        private long sequence;
    }
}
