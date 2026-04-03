package com.frozendawn.block;

import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.network.OpenMonitoringTerminalPayload;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MonitoringStationTerminalBlockEntity extends AbstractPuzzleTerminalBlockEntity<BlockPos> {

    private static final int LOCKOUT_TICKS_TOTAL = 20 * 45;

    private BlockPos stationCenter;
    private MonitoringTerminalPuzzle.Board terminalBoard;

    public MonitoringStationTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITORING_STATION_TERMINAL.get(), pos, state);
    }

    public void setStationCenter(BlockPos stationCenter) {
        this.stationCenter = stationCenter == null ? null : stationCenter.immutable();
        setChanged();
    }

    @Override
    protected @Nullable BlockPos resolveContext(ServerLevel level) {
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
    protected String missingContextMessage() {
        return "No station archive is linked to this terminal.";
    }

    @Override
    protected String moveCloserMessage() {
        return "Move closer to the wall terminal.";
    }

    @Override
    protected String moveBackMessage() {
        return "Move back to the station terminal.";
    }

    @Override
    protected void beforeOpen(ServerLevel level, ServerPlayer player, BlockPos stationCenter) {
        MonitoringStationStructureBuilder.refreshMapFrames(level, stationCenter);
    }

    @Override
    protected void onArchiveSessionOpened(ServerLevel level, ServerPlayer player, BlockPos stationCenter) {
        MonitoringStationStructureBuilder.unlockBackRoom(level, stationCenter);
    }

    @Override
    protected boolean isArchiveUnlocked(ServerLevel level, BlockPos stationCenter) {
        MonitoringStationState state = MonitoringStationState.get(level.getServer());
        return state.isStationUnlocked(stationCenter.getX() >> 4, stationCenter.getZ() >> 4);
    }

    @Override
    protected void configureNewPuzzleSession(long nonce) {
        terminalBoard = MonitoringTerminalPuzzle.create(nonce);
        sessionState().appendAudit("STATION ARCHIVE SEAL ACTIVE");
        sessionState().appendAudit("ORSA WEATHER INGEST NODE");
    }

    @Override
    protected void clearLeafPuzzleState() {
        terminalBoard = null;
    }

    @Override
    protected int maxAttempts() {
        return MonitoringTerminalPuzzle.MAX_ATTEMPTS;
    }

    @Override
    protected void handlePuzzleAction(ServerLevel level, ServerPlayer player, BlockPos stationCenter, int actionType,
                                      int actionIndex, String typedGuess) {
        if (actionType == ACTION_TYPED_GUESS) {
            handleWordSelection(level, player, stationCenter, actionIndex, typedGuess);
            return;
        }
        if (actionType == ACTION_USE_PAIR) {
            handlePairUse(level, player, actionIndex, stationCenter);
        }
    }

    @Override
    protected int archivePageCount() {
        return MonitoringStationArchive.PAGE_COUNT;
    }

    @Override
    protected int protectedArchivePageIndex() {
        return MonitoringStationArchive.EMPLOYEE_ONLY_PAGE;
    }

    @Override
    protected boolean acceptArchivePassword(ServerLevel level, ServerPlayer player, BlockPos stationCenter,
                                            String normalizedGuess) {
        return MonitoringStationArchive.EMPLOYEE_ARCHIVE_PASSWORD.equals(normalizedGuess);
    }

    @Override
    protected String emptyArchiveAuthMessage() {
        return "ACCESS PHRASE REQUIRED";
    }

    @Override
    protected String deniedArchiveAuthMessage() {
        return "ACCESS DENIED / RELAY PERSONNEL ONLY";
    }

    @Override
    protected void sendSnapshot(ServerPlayer player, int state, BlockPos stationCenter) {
        TerminalSessionState session = sessionState();
        TerminalArchiveState archiveState = archiveState();
        String auditLog = session.auditLogText();
        String archiveTitle = "";
        String archiveBody = "";
        int archivePage = 0;
        int archivePageCount = 0;
        boolean archivePasswordPrompt = false;

        if (state == STATE_ARCHIVE && level instanceof ServerLevel serverLevel) {
            MonitoringStationArchive.Snapshot archive = MonitoringStationArchive.create(
                    serverLevel,
                    stationCenter,
                    archiveState.pageIndex(),
                    archiveState.unlocked(),
                    archiveState.authStatus()
            );
            auditLog = archive.auditLog();
            archiveTitle = archive.title();
            archiveBody = archive.body();
            archivePage = archive.pageIndex();
            archivePageCount = archive.pageCount();
            archivePasswordPrompt = archive.passwordPrompt();
        }

        PacketDistributor.sendToPlayer(player, new OpenMonitoringTerminalPayload(
                worldPosition,
                session.nonce(),
                session.triesLeft(),
                state,
                session.removedMask(),
                session.usedPairMask(),
                0,
                session.lockoutTicksRemaining(),
                auditLog,
                archiveTitle,
                archiveBody,
                archivePage,
                archivePageCount,
                archivePasswordPrompt
        ));
    }

    private void handleWordSelection(ServerLevel level, ServerPlayer player, BlockPos stationCenter, int wordIndex,
                                     String typedGuess) {
        if (terminalBoard == null) {
            return;
        }

        TerminalSessionState session = sessionState();
        int resolvedIndex = wordIndex;
        if (resolvedIndex < 0 || resolvedIndex >= terminalBoard.candidates().size()) {
            String normalizedGuess = typedGuess == null ? "" : typedGuess.trim().toUpperCase();
            if (normalizedGuess.isEmpty()) {
                return;
            }
            resolvedIndex = -1;
            for (int i = 0; i < terminalBoard.candidates().size(); i++) {
                if (session.isWordRemoved(i)) {
                    continue;
                }
                if (terminalBoard.candidates().get(i).equals(normalizedGuess)) {
                    resolvedIndex = i;
                    break;
                }
            }
            if (resolvedIndex < 0) {
                session.appendAudit("> " + normalizedGuess);
                session.appendAudit("> Unknown token.");
                sendSnapshot(player, STATE_ACTIVE, stationCenter);
                return;
            }
        }
        if (session.isWordRemoved(resolvedIndex)) {
            return;
        }

        String guess = terminalBoard.candidates().get(resolvedIndex);
        session.appendAudit("> " + guess);
        if (resolvedIndex == terminalBoard.passwordIndex()) {
            session.appendAudit("> Archive seal disengaged.");
            MonitoringStationState stationState = MonitoringStationState.get(level.getServer());
            stationState.markStationUnlocked(stationCenter.getX() >> 4, stationCenter.getZ() >> 4);
            MonitoringStationStructureBuilder.unlockBackRoom(level, stationCenter);
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.05f);
            player.displayClientMessage(Component.literal("Back room unsealed."), true);
            beginArchiveSession(player, level.getRandom());
            sendSnapshot(player, STATE_ARCHIVE, stationCenter);
            return;
        }

        int likeness = MonitoringTerminalPuzzle.likeness(guess, terminalBoard.password());
        session.decrementTriesLeft();
        session.appendAudit("> Entry denied. " + likeness + "/" + terminalBoard.wordLength() + " correct.");
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.7f);

        if (session.triesLeft() <= 0) {
            session.startLockout(LOCKOUT_TICKS_TOTAL);
            session.appendAudit("Automatic reset pending.");
            session.appendAudit("TERMINAL LOCKED");
            sendSnapshot(player, STATE_LOCKED_OUT, stationCenter);
            return;
        }

        sendSnapshot(player, STATE_ACTIVE, stationCenter);
    }

    private void handlePairUse(ServerLevel level, ServerPlayer player, int pairIndex, BlockPos stationCenter) {
        if (terminalBoard == null) {
            return;
        }

        TerminalSessionState session = sessionState();
        MonitoringTerminalPuzzle.PairToken token = terminalBoard.getPair(pairIndex);
        if (token == null || session.isPairUsed(pairIndex)) {
            return;
        }

        session.markPairUsed(pairIndex);
        if (token.reward() == MonitoringTerminalPuzzle.PairReward.RESET_ATTEMPTS) {
            session.setTriesLeft(MonitoringTerminalPuzzle.MAX_ATTEMPTS);
            session.appendAudit("> Allowance replenished.");
        } else {
            int removed = removeOneDud();
            if (removed >= 0) {
                session.appendAudit("> Dud removed.");
            } else {
                session.setTriesLeft(MonitoringTerminalPuzzle.MAX_ATTEMPTS);
                session.appendAudit("> Allowance replenished.");
            }
        }

        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 1.2f);
        sendSnapshot(player, STATE_ACTIVE, stationCenter);
    }

    private int removeOneDud() {
        if (terminalBoard == null) {
            return -1;
        }

        TerminalSessionState session = sessionState();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < terminalBoard.candidates().size(); i++) {
            if (i == terminalBoard.passwordIndex()) {
                continue;
            }
            if (!session.isWordRemoved(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return -1;
        }

        int removed = candidates.get(Math.floorMod(session.nonce(), candidates.size()));
        session.markWordRemoved(removed);
        return removed;
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
