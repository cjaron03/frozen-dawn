package com.frozendawn.block;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TerminalSessionState {

    private static final int AUDIT_LOG_LIMIT = 8;
    private static final int MODE_PUZZLE = 0;
    private static final int MODE_ARCHIVE = 1;

    private UUID ownerId;
    private long nonce;
    private int ticksRemaining;
    private int triesLeft;
    private int lockoutTicksRemaining;
    private long removedMask;
    private long usedPairMask;
    private int mode = MODE_PUZZLE;
    private final List<String> auditLog = new ArrayList<>();

    void startPuzzleSession(UUID ownerId, RandomSource random, int sessionTtlTicks, int maxAttempts) {
        this.ownerId = ownerId;
        nonce = nextNonce(random);
        ticksRemaining = sessionTtlTicks;
        triesLeft = maxAttempts;
        lockoutTicksRemaining = 0;
        removedMask = 0L;
        usedPairMask = 0L;
        mode = MODE_PUZZLE;
        auditLog.clear();
    }

    void startArchiveSession(UUID ownerId, RandomSource random, int sessionTtlTicks) {
        this.ownerId = ownerId;
        nonce = nextNonce(random);
        ticksRemaining = sessionTtlTicks;
        triesLeft = 0;
        lockoutTicksRemaining = 0;
        removedMask = 0L;
        usedPairMask = 0L;
        mode = MODE_ARCHIVE;
        auditLog.clear();
    }

    void enterArchiveMode(int sessionTtlTicks) {
        mode = MODE_ARCHIVE;
        ticksRemaining = sessionTtlTicks;
        lockoutTicksRemaining = 0;
    }

    boolean hasReopenableSession() {
        return ownerId != null && (ticksRemaining > 0 || lockoutTicksRemaining > 0);
    }

    boolean matchesActiveSession(UUID playerId, long nonce) {
        return ownerId != null
                && (ticksRemaining > 0 || lockoutTicksRemaining > 0)
                && ownerId.equals(playerId)
                && this.nonce == nonce;
    }

    boolean isOwnedBy(UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    UUID ownerId() {
        return ownerId;
    }

    long nonce() {
        return nonce;
    }

    int ticksRemaining() {
        return ticksRemaining;
    }

    void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    void decrementTicksRemaining() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    int triesLeft() {
        return triesLeft;
    }

    void setTriesLeft(int triesLeft) {
        this.triesLeft = triesLeft;
    }

    void decrementTriesLeft() {
        triesLeft--;
    }

    int lockoutTicksRemaining() {
        return lockoutTicksRemaining;
    }

    void decrementLockoutTicksRemaining() {
        if (lockoutTicksRemaining > 0) {
            lockoutTicksRemaining--;
        }
    }

    void startLockout(int lockoutTicks) {
        lockoutTicksRemaining = lockoutTicks;
        ticksRemaining = 0;
    }

    long removedMask() {
        return removedMask;
    }

    boolean isWordRemoved(int wordIndex) {
        return ((removedMask >> wordIndex) & 1L) != 0L;
    }

    void markWordRemoved(int wordIndex) {
        removedMask |= (1L << wordIndex);
    }

    long usedPairMask() {
        return usedPairMask;
    }

    boolean isPairUsed(int pairIndex) {
        return ((usedPairMask >> pairIndex) & 1L) != 0L;
    }

    void markPairUsed(int pairIndex) {
        usedPairMask |= (1L << pairIndex);
    }

    boolean isArchiveMode() {
        return mode == MODE_ARCHIVE;
    }

    void appendAudit(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        auditLog.add(0, line);
        while (auditLog.size() > AUDIT_LOG_LIMIT) {
            auditLog.remove(auditLog.size() - 1);
        }
    }

    String auditLogText() {
        return String.join("\n", auditLog);
    }

    void clear() {
        ownerId = null;
        nonce = 0L;
        ticksRemaining = 0;
        triesLeft = 0;
        lockoutTicksRemaining = 0;
        removedMask = 0L;
        usedPairMask = 0L;
        mode = MODE_PUZZLE;
        auditLog.clear();
    }

    private static long nextNonce(RandomSource random) {
        long next = random.nextLong();
        return next == 0L ? 1L : next;
    }
}
