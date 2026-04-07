package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Observation and target-memory state that survives across behavior ticks.
 */
public final class ArchitectObservationMemory {

    private boolean hasObserved;
    private boolean observeDirty;
    private int observeTicks;
    @Nullable
    private BlockPos lastObservedPos;
    @Nullable
    private BlockPos lastKnownPlayerPos;
    private int lastSeenTick;
    @Nullable
    private UUID pendingSpawnCuePlayerId;
    private boolean pendingSpawnCuePlayed;
    private final List<BlockPos> entrancePositions = new ArrayList<>();

    public boolean hasObserved() {
        return hasObserved;
    }

    public void setHasObserved(boolean hasObserved) {
        this.hasObserved = hasObserved;
    }

    public boolean isObserveDirty() {
        return observeDirty;
    }

    public void setObserveDirty(boolean observeDirty) {
        this.observeDirty = observeDirty;
    }

    public int getObserveTicks() {
        return observeTicks;
    }

    public void setObserveTicks(int observeTicks) {
        this.observeTicks = observeTicks;
    }

    public void incrementObserveTicks() {
        observeTicks++;
    }

    @Nullable
    public BlockPos getLastObservedPos() {
        return lastObservedPos;
    }

    public void setLastObservedPos(@Nullable BlockPos lastObservedPos) {
        this.lastObservedPos = lastObservedPos != null ? lastObservedPos.immutable() : null;
    }

    @Nullable
    public BlockPos getLastKnownPlayerPos() {
        return lastKnownPlayerPos;
    }

    public void setLastKnownPlayerPos(@Nullable BlockPos lastKnownPlayerPos) {
        this.lastKnownPlayerPos = lastKnownPlayerPos != null ? lastKnownPlayerPos.immutable() : null;
    }

    public int getLastSeenTick() {
        return lastSeenTick;
    }

    public void setLastSeenTick(int lastSeenTick) {
        this.lastSeenTick = lastSeenTick;
    }

    @Nullable
    public UUID getPendingSpawnCuePlayerId() {
        return pendingSpawnCuePlayerId;
    }

    public void setPendingSpawnCuePlayerId(@Nullable UUID pendingSpawnCuePlayerId) {
        this.pendingSpawnCuePlayerId = pendingSpawnCuePlayerId;
    }

    public boolean isPendingSpawnCuePlayed() {
        return pendingSpawnCuePlayed;
    }

    public void setPendingSpawnCuePlayed(boolean pendingSpawnCuePlayed) {
        this.pendingSpawnCuePlayed = pendingSpawnCuePlayed;
    }

    public List<BlockPos> entrancePositions() {
        return entrancePositions;
    }
}
