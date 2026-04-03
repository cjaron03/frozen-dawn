package com.frozendawn.block;

import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.terminal.TowerArchive;
import com.frozendawn.terminal.TowerTerminalPuzzle;
import com.frozendawn.world.TowerPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import java.util.UUID;

public class TowerAntennaConsoleBlockEntity extends AbstractPuzzleTerminalBlockEntity<OrsaStructureState.TowerRecord> {

    private static final int ALIGN_TICKS_TOTAL = 20 * 30;
    private static final int LOCKOUT_TICKS_TOTAL = 20 * 60;

    private long towerId = Long.MIN_VALUE;
    private UUID aligningPlayerId;
    private int alignTicksRemaining;
    private TowerTerminalPuzzle.Board terminalBoard;

    public TowerAntennaConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWER_ANTENNA_CONSOLE.get(), pos, state);
    }

    public void setTowerId(long towerId) {
        this.towerId = towerId;
        setChanged();
    }

    public boolean isAligning() {
        return aligningPlayerId != null && alignTicksRemaining > 0;
    }

    @Override
    protected @Nullable OrsaStructureState.TowerRecord resolveContext(ServerLevel level) {
        long resolvedTowerId = resolveTowerId(level);
        return OrsaStructureState.get(level.getServer()).getTowerById(resolvedTowerId);
    }

    @Override
    protected String missingContextMessage() {
        return "No tower signal lock available from this console.";
    }

    @Override
    protected String moveCloserMessage() {
        return "Move closer to the antenna console.";
    }

    @Override
    protected String moveBackMessage() {
        return "Move back to the console terminal.";
    }

    @Override
    protected boolean handleOpenWhileLeafSpecialState(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower) {
        if (!isAligning()) {
            return false;
        }
        if (player.getUUID().equals(aligningPlayerId)) {
            player.displayClientMessage(Component.literal("Alignment already in progress."), true);
        } else {
            player.displayClientMessage(Component.literal("Console is already aligning."), true);
        }
        return true;
    }

    @Override
    protected boolean isArchiveUnlocked(ServerLevel level, OrsaStructureState.TowerRecord tower) {
        return tower.rewardGranted();
    }

    @Override
    protected void configureNewPuzzleSession(long nonce) {
        terminalBoard = TowerTerminalPuzzle.create(nonce);
        sessionState().appendAudit("SIGNAL LOCKOUT ACTIVE");
        sessionState().appendAudit("UNAUTHORIZED ACCESS PROHIBITED");
    }

    @Override
    protected void clearLeafPuzzleState() {
        terminalBoard = null;
    }

    @Override
    protected int maxAttempts() {
        return TowerTerminalPuzzle.MAX_ATTEMPTS;
    }

    @Override
    protected void handlePuzzleAction(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower,
                                      int actionType, int actionIndex, String typedGuess) {
        if (actionType == ACTION_TYPED_GUESS) {
            handleWordSelection(level, player, tower, actionIndex, typedGuess);
            return;
        }
        if (actionType == ACTION_USE_PAIR) {
            handlePairUse(level, player, tower, actionIndex);
        }
    }

    @Override
    protected int archivePageCount() {
        return TowerArchive.PAGE_COUNT;
    }

    @Override
    protected int protectedArchivePageIndex() {
        return TowerArchive.COMMAND_PAGE;
    }

    @Override
    protected boolean acceptArchivePassword(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower,
                                            String normalizedGuess) {
        return TowerArchive.COMMAND_ARCHIVE_PASSWORD.equals(normalizedGuess);
    }

    @Override
    protected String emptyArchiveAuthMessage() {
        return "AUTHORIZATION STRING REQUIRED";
    }

    @Override
    protected String deniedArchiveAuthMessage() {
        return "ACCESS DENIED / COMMAND PERSONNEL ONLY";
    }

    @Override
    protected void sendSnapshot(ServerPlayer player, int state, OrsaStructureState.TowerRecord tower) {
        TerminalSessionState session = sessionState();
        TerminalArchiveState archiveState = archiveState();
        String audit = session.auditLogText();
        String archiveTitle = "";
        String archiveBody = "";
        int archivePage = 0;
        int archivePageCount = 0;
        boolean archivePasswordPrompt = false;

        if (state == STATE_ARCHIVE && level instanceof ServerLevel serverLevel && tower != null) {
            TowerArchive.Snapshot archive = TowerArchive.create(
                    serverLevel,
                    tower,
                    archiveState.pageIndex(),
                    archiveState.unlocked(),
                    archiveState.authStatus()
            );
            audit = archive.auditLog();
            archiveTitle = archive.title();
            archiveBody = archive.body();
            archivePage = archive.pageIndex();
            archivePageCount = archive.pageCount();
            archivePasswordPrompt = archive.passwordPrompt();
        }

        PacketDistributor.sendToPlayer(player, new OpenTowerTerminalPayload(
                worldPosition,
                session.nonce(),
                session.triesLeft(),
                state,
                session.removedMask(),
                session.usedPairMask(),
                alignTicksRemaining,
                session.lockoutTicksRemaining(),
                audit,
                archiveTitle,
                archiveBody,
                archivePage,
                archivePageCount,
                archivePasswordPrompt
        ));
    }

    @Override
    protected int resolveLeafSpecialState(ServerLevel level, OrsaStructureState.TowerRecord tower) {
        return isAligning() ? STATE_ALIGNING : -1;
    }

    @Override
    protected boolean handleActionWhileLeafSpecialState(ServerLevel level, ServerPlayer player,
                                                        OrsaStructureState.TowerRecord tower) {
        if (!isAligning()) {
            return false;
        }
        player.displayClientMessage(Component.literal("Console is already aligning."), true);
        return true;
    }

    @Override
    protected void tickLeafSpecialState(ServerLevel level) {
        if (!isAligning()) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(aligningPlayerId);
        if (player == null || player.isDeadOrDying() || player.level() != level || !isPlayerInRange(player)) {
            cancelAlignment(level);
            return;
        }

        alignTicksRemaining--;
        if (alignTicksRemaining % 20 == 0 || alignTicksRemaining <= 5) {
            int seconds = Math.max(1, (alignTicksRemaining + 19) / 20);
            player.displayClientMessage(Component.literal("Aligning antenna... " + seconds + "s"), true);
        }
        if (alignTicksRemaining % 40 == 0) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.45f, 1.55f);
        }
        if (alignTicksRemaining % 5 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                    2, 0.18, 0.08, 0.18, 0.01);
        }

        if (alignTicksRemaining <= 0) {
            OrsaStructureState.TowerRecord tower = resolveContext(level);
            if (tower != null) {
                sendSnapshot(player, STATE_COMPLETE, tower);
                TowerPlacement.completeAlignment(level, tower.id(), player);
            } else {
                TowerPlacement.completeAlignment(level, resolveTowerId(level), player);
            }
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.15f);
            aligningPlayerId = null;
            alignTicksRemaining = 0;
            clearTerminalSession();
            setChanged();
        }
    }

    private void handleWordSelection(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower,
                                     int wordIndex, String typedGuess) {
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
                sendSnapshot(player, STATE_ACTIVE, tower);
                return;
            }
        }
        if (session.isWordRemoved(resolvedIndex)) {
            return;
        }
        handleWordGuess(level, player, tower, resolvedIndex);
    }

    private void handleWordGuess(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower, int wordIndex) {
        if (terminalBoard == null || wordIndex < 0 || wordIndex >= terminalBoard.candidates().size()) {
            return;
        }

        TerminalSessionState session = sessionState();
        if (session.isWordRemoved(wordIndex)) {
            return;
        }

        String guess = terminalBoard.candidates().get(wordIndex);
        session.appendAudit("> " + guess);
        if (wordIndex == terminalBoard.passwordIndex()) {
            session.appendAudit("> Exact match. Signal uplink accepted.");
            aligningPlayerId = player.getUUID();
            alignTicksRemaining = ALIGN_TICKS_TOTAL;
            session.setTicksRemaining(0);
            setChanged();
            sendSnapshot(player, STATE_ALIGNING, tower);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75f, 1.25f);
            player.displayClientMessage(Component.literal("Signal handshake accepted. Alignment started."), true);
            return;
        }

        int likeness = TowerTerminalPuzzle.likeness(guess, terminalBoard.password());
        session.decrementTriesLeft();
        session.appendAudit("> Entry denied. " + likeness + "/" + terminalBoard.wordLength() + " correct.");
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.7f);

        if (session.triesLeft() <= 0) {
            session.startLockout(LOCKOUT_TICKS_TOTAL);
            session.appendAudit("Please contact an administrator.");
            session.appendAudit("TERMINAL LOCKED");
            sendSnapshot(player, STATE_LOCKED_OUT, tower);
            return;
        }

        sendSnapshot(player, STATE_ACTIVE, tower);
    }

    private void handlePairUse(ServerLevel level, ServerPlayer player, OrsaStructureState.TowerRecord tower, int pairIndex) {
        if (terminalBoard == null) {
            return;
        }

        TerminalSessionState session = sessionState();
        TowerTerminalPuzzle.PairToken token = terminalBoard.getPair(pairIndex);
        if (token == null || session.isPairUsed(pairIndex)) {
            return;
        }

        session.markPairUsed(pairIndex);
        if (token.reward() == TowerTerminalPuzzle.PairReward.RESET_ATTEMPTS) {
            session.setTriesLeft(TowerTerminalPuzzle.MAX_ATTEMPTS);
            session.appendAudit("> Allowance replenished.");
        } else {
            int removed = removeOneDud();
            if (removed >= 0) {
                session.appendAudit("> Dud removed.");
            } else {
                session.setTriesLeft(TowerTerminalPuzzle.MAX_ATTEMPTS);
                session.appendAudit("> Allowance replenished.");
            }
        }

        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 1.2f);
        sendSnapshot(player, STATE_ACTIVE, tower);
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

    private long resolveTowerId(ServerLevel level) {
        if (towerId != Long.MIN_VALUE) {
            return towerId;
        }
        OrsaStructureState.TowerRecord tower = OrsaStructureState.get(level.getServer()).findTowerNear(worldPosition, 18);
        if (tower != null) {
            towerId = tower.id();
            setChanged();
            return towerId;
        }
        return Long.MIN_VALUE;
    }

    private void cancelAlignment(ServerLevel level) {
        ServerPlayer player = aligningPlayerId == null ? null : level.getServer().getPlayerList().getPlayer(aligningPlayerId);
        aligningPlayerId = null;
        alignTicksRemaining = 0;
        if (player != null) {
            clearTerminalSession();
            player.displayClientMessage(Component.literal("Signal uplink lost."), true);
        }
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.75f, 0.9f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("towerId", towerId);
        if (aligningPlayerId != null) {
            tag.putUUID("aligningPlayer", aligningPlayerId);
            tag.putInt("alignTicksRemaining", alignTicksRemaining);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        towerId = tag.contains("towerId") ? tag.getLong("towerId") : Long.MIN_VALUE;
        aligningPlayerId = tag.hasUUID("aligningPlayer") ? tag.getUUID("aligningPlayer") : null;
        alignTicksRemaining = tag.getInt("alignTicksRemaining");
        clearTerminalSession();
    }
}
