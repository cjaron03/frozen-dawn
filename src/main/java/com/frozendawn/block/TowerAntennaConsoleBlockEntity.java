package com.frozendawn.block;

import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.network.SubmitTowerTerminalPayload;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TowerAntennaConsoleBlockEntity extends BlockEntity {

    private static final int ALIGN_TICKS_TOTAL = 20 * 30;
    private static final int TERMINAL_SESSION_TTL_TICKS = 20 * 90;
    private static final double MAX_HORIZONTAL_DISTANCE_SQ = 4.5D * 4.5D;
    private static final double MAX_VERTICAL_DISTANCE = 3.5D;
    private static final int AUDIT_LOG_LIMIT = 8;

    private long towerId = Long.MIN_VALUE;
    private UUID aligningPlayerId;
    private int alignTicksRemaining;

    private UUID terminalPlayerId;
    private long terminalNonce;
    private int terminalTicksRemaining;
    private int terminalTriesLeft;
    private long terminalRemovedMask;
    private long terminalUsedPairMask;
    private TowerTerminalPuzzle.Board terminalBoard;
    private final List<String> terminalAuditLog = new ArrayList<>();

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

    public void openTerminal(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long resolvedTowerId = resolveTowerId(serverLevel);
        OrsaStructureState state = OrsaStructureState.get(serverLevel.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(resolvedTowerId);
        if (tower == null) {
            player.sendSystemMessage(Component.literal("No tower signal lock available from this console."));
            return;
        }

        if (tower.rewardGranted()) {
            TowerPlacement.sendAlignmentResults(serverLevel, tower.id(), player, false);
            return;
        }

        if (isAligning()) {
            if (player.getUUID().equals(aligningPlayerId)) {
                player.displayClientMessage(Component.literal("Alignment already in progress."), true);
            } else {
                player.displayClientMessage(Component.literal("Console is already aligning."), true);
            }
            return;
        }

        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal("Move closer to the antenna console."), true);
            return;
        }

        if (terminalPlayerId != null && terminalTicksRemaining > 0) {
            if (!terminalPlayerId.equals(player.getUUID())) {
                player.displayClientMessage(Component.literal("Terminal is currently in use."), true);
                return;
            }
            sendSnapshot(player, OpenTowerTerminalPayload.STATE_ACTIVE);
            return;
        }

        beginTerminalSession(player, serverLevel.getRandom());
        sendSnapshot(player, OpenTowerTerminalPayload.STATE_ACTIVE);
    }

    public void submitAction(ServerPlayer player, long nonce, int actionType, int actionIndex, String typedGuess) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        long resolvedTowerId = resolveTowerId(serverLevel);
        OrsaStructureState state = OrsaStructureState.get(serverLevel.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(resolvedTowerId);
        if (tower == null) {
            player.sendSystemMessage(Component.literal("No tower signal lock available from this console."));
            return;
        }
        if (tower.rewardGranted()) {
            TowerPlacement.sendAlignmentResults(serverLevel, tower.id(), player, false);
            return;
        }
        if (isAligning()) {
            player.displayClientMessage(Component.literal("Console is already aligning."), true);
            return;
        }
        if (!isPlayerInRange(player)) {
            player.displayClientMessage(Component.literal("Move back to the console terminal."), true);
            return;
        }
        if (!hasActiveSession(player, nonce)) {
            player.displayClientMessage(Component.literal("Terminal session expired. Reopen the console."), true);
            return;
        }

        if (actionType == SubmitTowerTerminalPayload.ACTION_TYPED_GUESS) {
            handleTypedGuess(serverLevel, player, typedGuess);
            return;
        }
        if (actionType == SubmitTowerTerminalPayload.ACTION_USE_PAIR) {
            handlePairUse(serverLevel, player, actionIndex);
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (terminalTicksRemaining > 0) {
            terminalTicksRemaining--;
            if (terminalTicksRemaining <= 0) {
                clearTerminalSession();
            }
        }

        if (aligningPlayerId == null || alignTicksRemaining <= 0) {
            return;
        }

        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(aligningPlayerId);
        if (player == null || player.isDeadOrDying() || player.level() != serverLevel || !isPlayerInRange(player)) {
            cancelAlignment(serverLevel);
            return;
        }

        alignTicksRemaining--;
        if (alignTicksRemaining % 20 == 0 || alignTicksRemaining <= 5) {
            int seconds = Math.max(1, (alignTicksRemaining + 19) / 20);
            player.displayClientMessage(Component.literal("Aligning antenna... " + seconds + "s"), true);
        }
        if (alignTicksRemaining % 40 == 0) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.45f, 1.55f);
        }
        if (alignTicksRemaining % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                    2, 0.18, 0.08, 0.18, 0.01);
        }

        if (alignTicksRemaining <= 0) {
            long resolvedTowerId = resolveTowerId(serverLevel);
            TowerPlacement.completeAlignment(serverLevel, resolvedTowerId, player);
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9f, 1.15f);
            aligningPlayerId = null;
            alignTicksRemaining = 0;
            setChanged();
        }
    }

    private void handleWordGuess(ServerLevel level, ServerPlayer player, int wordIndex) {
        if (terminalBoard == null || wordIndex < 0 || wordIndex >= terminalBoard.candidates().size()) {
            return;
        }
        if (((terminalRemovedMask >> wordIndex) & 1L) != 0L) {
            return;
        }

        String guess = terminalBoard.candidates().get(wordIndex);
        appendAudit("> " + guess);
        if (wordIndex == terminalBoard.passwordIndex()) {
            appendAudit("PASSWORD ACCEPTED");
            sendSnapshot(player, OpenTowerTerminalPayload.STATE_SOLVED);
            clearTerminalSession();
            aligningPlayerId = player.getUUID();
            alignTicksRemaining = ALIGN_TICKS_TOTAL;
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75f, 1.25f);
            player.displayClientMessage(Component.literal("Signal handshake accepted. Alignment started."), true);
            return;
        }

        int likeness = TowerTerminalPuzzle.likeness(guess, terminalBoard.password());
        terminalTriesLeft--;
        appendAudit("ENTRY DENIED");
        appendAudit("LIKENESS=" + likeness);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.7f);

        if (terminalTriesLeft <= 0) {
            appendAudit("LOCKOUT ENGAGED");
            sendSnapshot(player, OpenTowerTerminalPayload.STATE_LOCKED_OUT);
            clearTerminalSession();
            return;
        }

        sendSnapshot(player, OpenTowerTerminalPayload.STATE_ACTIVE);
    }

    private void handleTypedGuess(ServerLevel level, ServerPlayer player, String guess) {
        if (terminalBoard == null || guess == null) {
            return;
        }

        String normalized = guess.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != terminalBoard.wordLength()) {
            player.displayClientMessage(Component.literal("Type a " + terminalBoard.wordLength() + "-letter board word."), true);
            return;
        }

        int wordIndex = terminalBoard.candidates().indexOf(normalized);
        if (wordIndex < 0) {
            player.displayClientMessage(Component.literal("That word is not on this board."), true);
            return;
        }

        if (((terminalRemovedMask >> wordIndex) & 1L) != 0L) {
            player.displayClientMessage(Component.literal("That word has already been removed."), true);
            return;
        }

        handleWordGuess(level, player, wordIndex);
    }

    private void handlePairUse(ServerLevel level, ServerPlayer player, int pairIndex) {
        if (terminalBoard == null) {
            return;
        }
        TowerTerminalPuzzle.PairToken token = terminalBoard.getPair(pairIndex);
        if (token == null || ((terminalUsedPairMask >> pairIndex) & 1L) != 0L) {
            return;
        }

        terminalUsedPairMask |= (1L << pairIndex);
        if (token.reward() == TowerTerminalPuzzle.PairReward.RESET_ATTEMPTS) {
            terminalTriesLeft = TowerTerminalPuzzle.MAX_ATTEMPTS;
            appendAudit("ATTEMPTS RESET");
        } else {
            int removed = removeOneDud();
            if (removed >= 0) {
                appendAudit("DUD REMOVED");
            } else {
                terminalTriesLeft = TowerTerminalPuzzle.MAX_ATTEMPTS;
                appendAudit("ATTEMPTS RESET");
            }
        }

        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.65f, 1.2f);
        sendSnapshot(player, OpenTowerTerminalPayload.STATE_ACTIVE);
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

    private void sendSnapshot(ServerPlayer player, int state) {
        String audit = String.join("\n", terminalAuditLog);
        PacketDistributor.sendToPlayer(player, new OpenTowerTerminalPayload(
                worldPosition,
                terminalNonce,
                terminalTriesLeft,
                state,
                terminalRemovedMask,
                terminalUsedPairMask,
                audit
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

    private boolean isPlayerInRange(ServerPlayer player) {
        double dx = player.getX() - (worldPosition.getX() + 0.5D);
        double dz = player.getZ() - (worldPosition.getZ() + 0.5D);
        double dy = Math.abs(player.getY() - (worldPosition.getY() + 0.5D));
        return (dx * dx + dz * dz) <= MAX_HORIZONTAL_DISTANCE_SQ && dy <= MAX_VERTICAL_DISTANCE;
    }

    private void cancelAlignment(ServerLevel level) {
        aligningPlayerId = null;
        alignTicksRemaining = 0;
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.75f, 0.9f);
    }

    private void beginTerminalSession(ServerPlayer player, RandomSource random) {
        terminalPlayerId = player.getUUID();
        terminalNonce = random.nextLong();
        if (terminalNonce == 0L) {
            terminalNonce = 1L;
        }
        terminalTicksRemaining = TERMINAL_SESSION_TTL_TICKS;
        terminalTriesLeft = TowerTerminalPuzzle.MAX_ATTEMPTS;
        terminalRemovedMask = 0L;
        terminalUsedPairMask = 0L;
        terminalBoard = TowerTerminalPuzzle.create(terminalNonce);
        terminalAuditLog.clear();
        appendAudit("SIGNAL LOCKOUT ACTIVE");
        appendAudit("UNAUTHORIZED ACCESS PROHIBITED");
        setChanged();
    }

    private boolean hasActiveSession(ServerPlayer player, long nonce) {
        return terminalBoard != null
                && terminalPlayerId != null
                && terminalTicksRemaining > 0
                && terminalPlayerId.equals(player.getUUID())
                && nonce == terminalNonce;
    }

    private void clearTerminalSession() {
        terminalPlayerId = null;
        terminalNonce = 0L;
        terminalTicksRemaining = 0;
        terminalTriesLeft = 0;
        terminalRemovedMask = 0L;
        terminalUsedPairMask = 0L;
        terminalBoard = null;
        terminalAuditLog.clear();
        setChanged();
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
