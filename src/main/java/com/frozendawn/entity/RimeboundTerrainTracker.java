package com.frozendawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Per-entity transient terrain tells; never persisted and never force-loaded. */
public final class RimeboundTerrainTracker {
    private static final int MAX_BRITTLE = 3;
    private final List<TimedPosition> brittleGround = new ArrayList<>();
    private final List<TimedPosition> permafrostTrail = new ArrayList<>();

    public void addBrittle(BlockPos pos, long now) {
        prune(now);
        brittleGround.removeIf(entry -> entry.pos.equals(pos));
        while (brittleGround.size() >= MAX_BRITTLE) {
            brittleGround.removeFirst();
        }
        brittleGround.add(new TimedPosition(pos.immutable(), now + 1_800L));
    }

    public void addTrail(BlockPos pos, long now) {
        prune(now);
        if (permafrostTrail.stream().noneMatch(entry -> entry.pos.equals(pos))) {
            permafrostTrail.add(new TimedPosition(pos.immutable(), now + 160L));
        }
        while (permafrostTrail.size() > 32) {
            permafrostTrail.removeFirst();
        }
    }

    public boolean isBrittle(BlockPos pos, long now) {
        prune(now);
        return brittleGround.stream().anyMatch(entry -> entry.pos.equals(pos));
    }

    public boolean consumeBrittle(BlockPos pos, long now) {
        prune(now);
        return brittleGround.removeIf(entry -> entry.pos.equals(pos));
    }

    public boolean isTrail(BlockPos pos, long now) {
        prune(now);
        return permafrostTrail.stream().anyMatch(entry -> entry.pos.equals(pos));
    }

    public List<BlockPos> brittlePositions(long now) {
        prune(now);
        return brittleGround.stream().map(TimedPosition::pos).toList();
    }

    public List<BlockPos> trailPositions(long now) {
        prune(now);
        return permafrostTrail.stream().map(TimedPosition::pos).toList();
    }

    public void pruneUnloaded(ServerLevel level) {
        brittleGround.removeIf(entry -> !level.isLoaded(entry.pos));
        permafrostTrail.removeIf(entry -> !level.isLoaded(entry.pos));
    }

    private void prune(long now) {
        pruneList(brittleGround, now);
        pruneList(permafrostTrail, now);
    }

    private static void pruneList(List<TimedPosition> positions, long now) {
        Iterator<TimedPosition> iterator = positions.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expiresAt <= now) {
                iterator.remove();
            }
        }
    }

    private record TimedPosition(BlockPos pos, long expiresAt) {
    }
}
