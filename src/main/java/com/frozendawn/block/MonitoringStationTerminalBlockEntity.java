package com.frozendawn.block;

import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.network.OpenMonitoringTerminalPayload;
import com.frozendawn.network.SubmitMonitoringTerminalPayload;
import com.frozendawn.terminal.MonitoringStationArchive;
import com.frozendawn.terminal.MonitoringTerminalPuzzle;
import com.frozendawn.world.MonitoringStationPlacement;
import com.frozendawn.world.MonitoringStationStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MonitoringStationTerminalBlockEntity extends BlockEntity {

    private static final int LOCKOUT_TICKS_TOTAL = 20 * 45;
    private static final int TERMINAL_SESSION_TTL_TICKS = 20 * 90;
    private static final double MAX_HORIZONTAL_DISTANCE_SQ = 4.5D * 4.5D;
    private static final double MAX_VERTICAL_DISTANCE = 3.5D;
    private static final int AUDIT_LOG_LIMIT = 8;
    private static final int MODE_PUZZLE = 0;
    private static final int MODE_ARCHIVE = 1;

    private BlockPos stationCenter;

    private UUID terminalPlayerId;
    private long terminalNonce;
    private int terminalTicksRemaining;
    private int terminalTriesLeft;
    private int terminalLockoutTicksRemaining;
    private long terminalRemovedMask;
    private long terminalUsedPairMask;
    private MonitoringTerminalPuzzle.Board terminalBoard;
    private int terminalMode = MODE_PUZZLE;
    private int terminalArchivePage;
    private final List<String> terminalAuditLog = new ArrayList<>();

    public MonitoringStationTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITORING_STATION_TERMINAL.get(), pos, state);
    }

    public void setStationCenter(BlockPos stationCenter) {
        this.stationCenter = stationCenter == null ? null : stationCenter.immutable();
        setChanged();
    }

    public void openTerminal(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos resolvedStationCenter = resolveStationCenter(serverLevel);
        if (resolvedStationCenter == null) {
            player.sendSystemMessage(Component.literal("No station archive is linked to this terminal."));
            return;
        }
        MonitoringStationStructureBuilder.ensureHyraxFrame(serverLevel, resolvedStationCenter);

        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal("Move closer to the wall terminal."), true);
            return;
        }

        if (terminalPlayerId != null && (terminalTicksRemaining > 0 || terminalLockoutTicksRemaining > 0)) {
            if (!terminalPlayerId.equals(player.getUUID())) {
                player.displayClientMessage(Component.literal("Terminal is currently in use."), true);
                return;
            }
            sendSnapshot(player, currentTerminalState());
            return;
        }

        if (isStationUnlocked(serverLevel, resolvedStationCenter)) {
            MonitoringStationStructureBuilder.unlockBackRoom(serverLevel, resolvedStationCenter);
            beginArchiveSession(player, serverLevel.getRandom());
            sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ARCHIVE);
            return;
        }

        beginTerminalSession(player, serverLevel.getRandom());
        sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ACTIVE);
    }

    public void submitAction(ServerPlayer player, long nonce, int actionType, int actionIndex, String typedGuess) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos resolvedStationCenter = resolveStationCenter(serverLevel);
        if (resolvedStationCenter == null) {
            player.sendSystemMessage(Component.literal("No station archive is linked to this terminal."));
            return;
        }
        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal("Move back to the station terminal."), true);
            return;
        }
        if (isStationUnlocked(serverLevel, resolvedStationCenter)) {
            if (!hasActiveSession(player, nonce)) {
                player.displayClientMessage(Component.literal("Terminal session expired. Reopen the console."), true);
                return;
            }
            if (actionType == SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_PREVIOUS) {
                handleArchiveNavigation(serverLevel, player, -1);
            } else if (actionType == SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_NEXT) {
                handleArchiveNavigation(serverLevel, player, 1);
            } else if (actionType == SubmitMonitoringTerminalPayload.ACTION_ARCHIVE_OPEN_PAGE) {
                handleArchiveOpenPage(serverLevel, player, actionIndex);
            } else {
                sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ARCHIVE);
            }
            return;
        }
        if (!hasActiveSession(player, nonce)) {
            player.displayClientMessage(Component.literal("Terminal session expired. Reopen the console."), true);
            return;
        }
        if (terminalLockoutTicksRemaining > 0) {
            sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_LOCKED_OUT);
            return;
        }

        if (actionType == SubmitMonitoringTerminalPayload.ACTION_TYPED_GUESS) {
            handleWordSelection(serverLevel, player, resolvedStationCenter, actionIndex, typedGuess);
            return;
        }
        if (actionType == SubmitMonitoringTerminalPayload.ACTION_USE_PAIR) {
            handlePairUse(serverLevel, player, actionIndex);
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (terminalPlayerId != null && terminalLockoutTicksRemaining > 0) {
            terminalLockoutTicksRemaining--;
            if (terminalLockoutTicksRemaining <= 0) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(terminalPlayerId);
                if (player != null && !player.isDeadOrDying() && player.level() == serverLevel && isPlayerInRange(player)) {
                    beginTerminalSession(player, serverLevel.getRandom());
                    sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ACTIVE);
                } else {
                    clearTerminalSession();
                }
            }
        } else if (terminalTicksRemaining > 0) {
            terminalTicksRemaining--;
            if (terminalTicksRemaining <= 0) {
                clearTerminalSession();
            }
        }
    }

    private void handleWordSelection(ServerLevel level, ServerPlayer player, BlockPos resolvedStationCenter,
                                     int wordIndex, String typedGuess) {
        if (terminalBoard == null) {
            return;
        }

        int resolvedIndex = wordIndex;
        if (resolvedIndex < 0 || resolvedIndex >= terminalBoard.candidates().size()) {
            String normalizedGuess = typedGuess == null ? "" : typedGuess.trim().toUpperCase();
            if (normalizedGuess.isEmpty()) {
                return;
            }
            resolvedIndex = -1;
            for (int i = 0; i < terminalBoard.candidates().size(); i++) {
                if (((terminalRemovedMask >> i) & 1L) != 0L) {
                    continue;
                }
                if (terminalBoard.candidates().get(i).equals(normalizedGuess)) {
                    resolvedIndex = i;
                    break;
                }
            }
            if (resolvedIndex < 0) {
                appendAudit("> " + normalizedGuess);
                appendAudit("> Unknown token.");
                sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ACTIVE);
                return;
            }
        }
        if (((terminalRemovedMask >> resolvedIndex) & 1L) != 0L) {
            return;
        }

        String guess = terminalBoard.candidates().get(resolvedIndex);
        appendAudit("> " + guess);
        if (resolvedIndex == terminalBoard.passwordIndex()) {
            appendAudit("> Archive seal disengaged.");
            MonitoringStationState stationState = MonitoringStationState.get(level.getServer());
            stationState.markStationUnlocked(resolvedStationCenter.getX() >> 4, resolvedStationCenter.getZ() >> 4);
            MonitoringStationStructureBuilder.unlockBackRoom(level, resolvedStationCenter);
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.05f);
            player.displayClientMessage(Component.literal("Back room unsealed."), true);
            beginArchiveSession(player, level.getRandom());
            sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ARCHIVE);
            return;
        }

        int likeness = MonitoringTerminalPuzzle.likeness(guess, terminalBoard.password());
        terminalTriesLeft--;
        appendAudit("> Entry denied. " + likeness + "/" + terminalBoard.wordLength() + " correct.");
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.7f);

        if (terminalTriesLeft <= 0) {
            terminalLockoutTicksRemaining = LOCKOUT_TICKS_TOTAL;
            terminalTicksRemaining = 0;
            appendAudit("Automatic reset pending.");
            appendAudit("TERMINAL LOCKED");
            sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_LOCKED_OUT);
            return;
        }

        sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ACTIVE);
    }

    private void handlePairUse(ServerLevel level, ServerPlayer player, int pairIndex) {
        if (terminalBoard == null) {
            return;
        }
        MonitoringTerminalPuzzle.PairToken token = terminalBoard.getPair(pairIndex);
        if (token == null || ((terminalUsedPairMask >> pairIndex) & 1L) != 0L) {
            return;
        }

        terminalUsedPairMask |= (1L << pairIndex);
        if (token.reward() == MonitoringTerminalPuzzle.PairReward.RESET_ATTEMPTS) {
            terminalTriesLeft = MonitoringTerminalPuzzle.MAX_ATTEMPTS;
            appendAudit("> Allowance replenished.");
        } else {
            int removed = removeOneDud();
            if (removed >= 0) {
                appendAudit("> Dud removed.");
            } else {
                terminalTriesLeft = MonitoringTerminalPuzzle.MAX_ATTEMPTS;
                appendAudit("> Allowance replenished.");
            }
        }

        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 1.2f);
        sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ACTIVE);
    }

    private void handleArchiveNavigation(ServerLevel level, ServerPlayer player, int delta) {
        terminalMode = MODE_ARCHIVE;
        terminalArchivePage = Math.floorMod(terminalArchivePage + delta, MonitoringStationArchive.PAGE_COUNT);
        terminalTicksRemaining = TERMINAL_SESSION_TTL_TICKS;
        terminalLockoutTicksRemaining = 0;
        terminalBoard = null;
        sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ARCHIVE);
        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.35f, 1.35f);
    }

    private void handleArchiveOpenPage(ServerLevel level, ServerPlayer player, int pageIndex) {
        terminalMode = MODE_ARCHIVE;
        terminalArchivePage = Math.floorMod(pageIndex, MonitoringStationArchive.PAGE_COUNT);
        terminalTicksRemaining = TERMINAL_SESSION_TTL_TICKS;
        terminalLockoutTicksRemaining = 0;
        terminalBoard = null;
        sendSnapshot(player, OpenMonitoringTerminalPayload.STATE_ARCHIVE);
        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.35f, 1.1f);
    }

    private int removeOneDud() {
        if (terminalBoard == null) {
            return -1;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < terminalBoard.candidates().size(); i++) {
            if (i == terminalBoard.passwordIndex()) {
                continue;
            }
            if (((terminalRemovedMask >> i) & 1L) == 0L) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return -1;
        }
        int removed = candidates.get(Math.floorMod(terminalNonce, candidates.size()));
        terminalRemovedMask |= (1L << removed);
        return removed;
    }

    private void beginTerminalSession(ServerPlayer player, RandomSource random) {
        terminalPlayerId = player.getUUID();
        terminalNonce = random.nextLong();
        if (terminalNonce == 0L) {
            terminalNonce = 1L;
        }
        terminalTicksRemaining = TERMINAL_SESSION_TTL_TICKS;
        terminalTriesLeft = MonitoringTerminalPuzzle.MAX_ATTEMPTS;
        terminalLockoutTicksRemaining = 0;
        terminalRemovedMask = 0L;
        terminalUsedPairMask = 0L;
        terminalMode = MODE_PUZZLE;
        terminalArchivePage = 0;
        terminalBoard = MonitoringTerminalPuzzle.create(terminalNonce);
        terminalAuditLog.clear();
        appendAudit("STATION ARCHIVE SEAL ACTIVE");
        appendAudit("ORSA WEATHER INGEST NODE");
        setChanged();
    }

    private void beginArchiveSession(ServerPlayer player, RandomSource random) {
        terminalPlayerId = player.getUUID();
        terminalNonce = random.nextLong();
        if (terminalNonce == 0L) {
            terminalNonce = 1L;
        }
        terminalTicksRemaining = TERMINAL_SESSION_TTL_TICKS;
        terminalTriesLeft = 0;
        terminalLockoutTicksRemaining = 0;
        terminalRemovedMask = 0L;
        terminalUsedPairMask = 0L;
        terminalMode = MODE_ARCHIVE;
        terminalArchivePage = 0;
        terminalBoard = null;
        terminalAuditLog.clear();
        setChanged();
    }

    private boolean hasActiveSession(ServerPlayer player, long nonce) {
        return terminalPlayerId != null
                && (terminalTicksRemaining > 0 || terminalLockoutTicksRemaining > 0)
                && terminalPlayerId.equals(player.getUUID())
                && nonce == terminalNonce;
    }

    private void sendSnapshot(ServerPlayer player, int state) {
        String auditLog = String.join("\n", terminalAuditLog);
        String archiveTitle = "";
        String archiveBody = "";
        int archivePage = 0;
        int archivePageCount = 0;
        if (state == OpenMonitoringTerminalPayload.STATE_ARCHIVE
                && level instanceof ServerLevel serverLevel) {
            BlockPos resolvedStationCenter = resolveStationCenter(serverLevel);
            if (resolvedStationCenter != null) {
                MonitoringStationArchive.Snapshot archive = MonitoringStationArchive.create(serverLevel, resolvedStationCenter, terminalArchivePage);
                auditLog = archive.auditLog();
                archiveTitle = archive.title();
                archiveBody = archive.body();
                archivePage = archive.pageIndex();
                archivePageCount = archive.pageCount();
            }
        }
        PacketDistributor.sendToPlayer(player, new OpenMonitoringTerminalPayload(
                worldPosition,
                terminalNonce,
                terminalTriesLeft,
                state,
                terminalRemovedMask,
                terminalUsedPairMask,
                0,
                terminalLockoutTicksRemaining,
                auditLog,
                archiveTitle,
                archiveBody,
                archivePage,
                archivePageCount
        ));
    }

    private void appendAudit(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        terminalAuditLog.add(0, line);
        while (terminalAuditLog.size() > AUDIT_LOG_LIMIT) {
            terminalAuditLog.remove(terminalAuditLog.size() - 1);
        }
        setChanged();
    }

    private int currentTerminalState() {
        if (terminalMode == MODE_ARCHIVE) {
            return OpenMonitoringTerminalPayload.STATE_ARCHIVE;
        }
        return terminalLockoutTicksRemaining > 0
                ? OpenMonitoringTerminalPayload.STATE_LOCKED_OUT
                : OpenMonitoringTerminalPayload.STATE_ACTIVE;
    }

    private void clearTerminalSession() {
        terminalPlayerId = null;
        terminalNonce = 0L;
        terminalTicksRemaining = 0;
        terminalTriesLeft = 0;
        terminalLockoutTicksRemaining = 0;
        terminalRemovedMask = 0L;
        terminalUsedPairMask = 0L;
        terminalMode = MODE_PUZZLE;
        terminalArchivePage = 0;
        terminalBoard = null;
        terminalAuditLog.clear();
        setChanged();
    }

    private boolean isPlayerInRange(ServerPlayer player) {
        double dx = player.getX() - (worldPosition.getX() + 0.5D);
        double dz = player.getZ() - (worldPosition.getZ() + 0.5D);
        double dy = Math.abs(player.getY() - (worldPosition.getY() + 0.5D));
        return (dx * dx + dz * dz) <= MAX_HORIZONTAL_DISTANCE_SQ && dy <= MAX_VERTICAL_DISTANCE;
    }

    private boolean isStationUnlocked(ServerLevel level, BlockPos resolvedStationCenter) {
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        return state.isStationUnlocked(resolvedStationCenter.getX() >> 4, resolvedStationCenter.getZ() >> 4);
    }

    @Nullable
    private BlockPos resolveStationCenter(ServerLevel level) {
        if (stationCenter != null) {
            return stationCenter;
        }
        stationCenter = MonitoringStationPlacement.findBuiltStationNear(level, worldPosition, 20);
        if (stationCenter != null) {
            setChanged();
        }
        return stationCenter;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stationCenter != null) {
            tag.putInt("stationCenterX", stationCenter.getX());
            tag.putInt("stationCenterY", stationCenter.getY());
            tag.putInt("stationCenterZ", stationCenter.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stationCenterX")) {
            stationCenter = new BlockPos(tag.getInt("stationCenterX"), tag.getInt("stationCenterY"), tag.getInt("stationCenterZ"));
        } else {
            stationCenter = null;
        }
        clearTerminalSession();
    }
}
