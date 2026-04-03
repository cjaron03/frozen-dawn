package com.frozendawn.block;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

abstract class AbstractPuzzleTerminalBlockEntity<C> extends BlockEntity {

    protected static final int STATE_ACTIVE = 0;
    protected static final int STATE_ALIGNING = 1;
    protected static final int STATE_LOCKED_OUT = 2;
    protected static final int STATE_COMPLETE = 3;
    protected static final int STATE_ARCHIVE = 4;

    protected static final int ACTION_TYPED_GUESS = 0;
    protected static final int ACTION_USE_PAIR = 1;
    protected static final int ACTION_ARCHIVE_PREVIOUS = 2;
    protected static final int ACTION_ARCHIVE_NEXT = 3;
    protected static final int ACTION_ARCHIVE_OPEN_PAGE = 4;
    protected static final int ACTION_ARCHIVE_AUTH = 5;

    private static final int TERMINAL_SESSION_TTL_TICKS = 20 * 90;

    private final TerminalAccessValidator accessValidator = new TerminalAccessValidator();
    private final TerminalSessionState sessionState = new TerminalSessionState();
    private final TerminalArchiveState archiveState = new TerminalArchiveState();

    protected AbstractPuzzleTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public final void openTerminal(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        C context = resolveContext(serverLevel);
        if (context == null) {
            player.sendSystemMessage(Component.literal(missingContextMessage()));
            return;
        }
        if (handleOpenWhileLeafSpecialState(serverLevel, player, context)) {
            return;
        }

        beforeOpen(serverLevel, player, context);

        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal(moveCloserMessage()), true);
            return;
        }

        if (sessionState.hasReopenableSession()) {
            if (!sessionState.isOwnedBy(player.getUUID())) {
                player.displayClientMessage(Component.literal("Terminal is currently in use."), true);
                return;
            }
            sendSnapshot(player, currentTerminalState(serverLevel, context), context);
            return;
        }

        if (isArchiveUnlocked(serverLevel, context)) {
            onArchiveSessionOpened(serverLevel, player, context);
            beginArchiveSession(player, serverLevel.getRandom());
            sendSnapshot(player, STATE_ARCHIVE, context);
            return;
        }

        beginPuzzleSession(player, serverLevel.getRandom());
        sendSnapshot(player, STATE_ACTIVE, context);
    }

    public final void submitAction(ServerPlayer player, long nonce, int actionType, int actionIndex, String typedGuess) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        C context = resolveContext(serverLevel);
        if (context == null) {
            player.sendSystemMessage(Component.literal(missingContextMessage()));
            return;
        }
        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal(moveBackMessage()), true);
            return;
        }

        if (isArchiveUnlocked(serverLevel, context)) {
            handleArchiveAction(serverLevel, player, context, nonce, actionType, actionIndex, typedGuess);
            return;
        }
        if (handleActionWhileLeafSpecialState(serverLevel, player, context)) {
            return;
        }
        if (!sessionState.matchesActiveSession(player.getUUID(), nonce)) {
            player.displayClientMessage(Component.literal("Terminal session expired. Reopen the console."), true);
            return;
        }
        if (sessionState.lockoutTicksRemaining() > 0) {
            sendSnapshot(player, STATE_LOCKED_OUT, context);
            return;
        }

        handlePuzzleAction(serverLevel, player, context, actionType, actionIndex, typedGuess);
    }

    public final void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        tickSharedSession(serverLevel);
        tickLeafSpecialState(serverLevel);
    }

    protected final TerminalSessionState sessionState() {
        return sessionState;
    }

    protected final TerminalArchiveState archiveState() {
        return archiveState;
    }

    protected final boolean isPlayerInRange(ServerPlayer player) {
        return accessValidator.isPlayerInRange(player, worldPosition);
    }

    protected final void beginPuzzleSession(ServerPlayer player, RandomSource random) {
        sessionState.startPuzzleSession(player.getUUID(), random, TERMINAL_SESSION_TTL_TICKS, maxAttempts());
        archiveState.resetForPuzzleSession();
        clearLeafPuzzleState();
        configureNewPuzzleSession(sessionState.nonce());
        setChanged();
    }

    protected final void beginArchiveSession(ServerPlayer player, RandomSource random) {
        sessionState.startArchiveSession(player.getUUID(), random, TERMINAL_SESSION_TTL_TICKS);
        archiveState.resetForArchiveSession();
        clearLeafPuzzleState();
        setChanged();
    }

    protected final void clearTerminalSession() {
        sessionState.clear();
        archiveState.resetForPuzzleSession();
        clearLeafPuzzleState();
        setChanged();
    }

    protected abstract @Nullable C resolveContext(ServerLevel level);

    protected abstract String missingContextMessage();

    protected abstract String moveCloserMessage();

    protected abstract String moveBackMessage();

    protected void beforeOpen(ServerLevel level, ServerPlayer player, C context) {
    }

    protected boolean handleOpenWhileLeafSpecialState(ServerLevel level, ServerPlayer player, C context) {
        return false;
    }

    protected void onArchiveSessionOpened(ServerLevel level, ServerPlayer player, C context) {
    }

    protected abstract boolean isArchiveUnlocked(ServerLevel level, C context);

    protected abstract void configureNewPuzzleSession(long nonce);

    protected abstract void clearLeafPuzzleState();

    protected abstract int maxAttempts();

    protected abstract void handlePuzzleAction(ServerLevel level, ServerPlayer player, C context, int actionType, int actionIndex, String typedGuess);

    protected abstract int archivePageCount();

    protected abstract int protectedArchivePageIndex();

    protected abstract boolean acceptArchivePassword(ServerLevel level, ServerPlayer player, C context, String normalizedGuess);

    protected abstract String emptyArchiveAuthMessage();

    protected abstract String deniedArchiveAuthMessage();

    protected abstract void sendSnapshot(ServerPlayer player, int state, C context);

    protected int resolveLeafSpecialState(ServerLevel level, C context) {
        return -1;
    }

    protected boolean handleActionWhileLeafSpecialState(ServerLevel level, ServerPlayer player, C context) {
        return false;
    }

    protected void tickLeafSpecialState(ServerLevel level) {
    }

    private int currentTerminalState(ServerLevel level, C context) {
        if (sessionState.isArchiveMode()) {
            return STATE_ARCHIVE;
        }
        int specialState = resolveLeafSpecialState(level, context);
        if (specialState >= 0) {
            return specialState;
        }
        if (sessionState.lockoutTicksRemaining() > 0) {
            return STATE_LOCKED_OUT;
        }
        return STATE_ACTIVE;
    }

    private void handleArchiveAction(ServerLevel level, ServerPlayer player, C context, long nonce, int actionType,
                                     int actionIndex, String typedGuess) {
        if (!sessionState.matchesActiveSession(player.getUUID(), nonce)) {
            player.displayClientMessage(Component.literal("Terminal session expired. Reopen the console."), true);
            return;
        }

        if (actionType == ACTION_ARCHIVE_PREVIOUS) {
            navigateArchive(level, player, context, -1, 1.35f);
            return;
        }
        if (actionType == ACTION_ARCHIVE_NEXT) {
            navigateArchive(level, player, context, 1, 1.35f);
            return;
        }
        if (actionType == ACTION_ARCHIVE_OPEN_PAGE) {
            openArchivePage(level, player, context, actionIndex);
            return;
        }
        if (actionType == ACTION_ARCHIVE_AUTH) {
            authenticateArchive(level, player, context, typedGuess);
            return;
        }
        sendSnapshot(player, STATE_ARCHIVE, context);
    }

    private void navigateArchive(ServerLevel level, ServerPlayer player, C context, int delta, float pitch) {
        sessionState.enterArchiveMode(TERMINAL_SESSION_TTL_TICKS);
        archiveState.setPageIndex(Math.floorMod(archiveState.pageIndex() + delta, archivePageCount()));
        archiveState.clearAuthStatus();
        clearLeafPuzzleState();
        sendSnapshot(player, STATE_ARCHIVE, context);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, pitch);
    }

    private void openArchivePage(ServerLevel level, ServerPlayer player, C context, int pageIndex) {
        sessionState.enterArchiveMode(TERMINAL_SESSION_TTL_TICKS);
        archiveState.setPageIndex(Math.floorMod(pageIndex, archivePageCount()));
        if (archiveState.pageIndex() != protectedArchivePageIndex()) {
            archiveState.clearAuthStatus();
        }
        clearLeafPuzzleState();
        sendSnapshot(player, STATE_ARCHIVE, context);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.1f);
    }

    private void authenticateArchive(ServerLevel level, ServerPlayer player, C context, String typedGuess) {
        sessionState.enterArchiveMode(TERMINAL_SESSION_TTL_TICKS);
        archiveState.setPageIndex(protectedArchivePageIndex());
        clearLeafPuzzleState();

        String normalizedGuess = typedGuess == null ? "" : typedGuess.trim().toUpperCase();
        if (normalizedGuess.isEmpty()) {
            archiveState.setAuthStatus(emptyArchiveAuthMessage());
            sendSnapshot(player, STATE_ARCHIVE, context);
            return;
        }

        if (acceptArchivePassword(level, player, context, normalizedGuess)) {
            archiveState.setUnlocked(true);
            archiveState.clearAuthStatus();
            sendSnapshot(player, STATE_ARCHIVE, context);
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 1.35f);
            return;
        }

        archiveState.setAuthStatus(deniedArchiveAuthMessage());
        sendSnapshot(player, STATE_ARCHIVE, context);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.7f);
    }

    private void tickSharedSession(ServerLevel level) {
        if (sessionState.ownerId() != null && sessionState.lockoutTicksRemaining() > 0) {
            sessionState.decrementLockoutTicksRemaining();
            if (sessionState.lockoutTicksRemaining() <= 0) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(sessionState.ownerId());
                if (player != null && !player.isDeadOrDying() && player.level() == level && isPlayerInRange(player)) {
                    beginPuzzleSession(player, level.getRandom());
                    C context = resolveContext(level);
                    if (context != null) {
                        sendSnapshot(player, STATE_ACTIVE, context);
                    }
                } else {
                    clearTerminalSession();
                }
            }
            return;
        }

        if (sessionState.ticksRemaining() > 0) {
            sessionState.decrementTicksRemaining();
            if (sessionState.ticksRemaining() <= 0) {
                clearTerminalSession();
            }
        }
    }
}
