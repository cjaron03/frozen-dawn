package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.vault.VaultMechanismBlock.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One mechanism of the Unwatched Door. Door 1 grinds open while unwatched; the bridge lays cells while unwatched and
 * someone stands near its end; the last door ignores all of that and opens only when a rest on the cot ends unwatched.
 * Progress is in ticks and only ever goes forward, so a frozen door stands where it stopped, through a reload too.
 * Doors are drawn by {@code VaultMechanismRenderer}; their {@code vault_door_cell} blocks only stop you. A Watch Floor gate
 * answers to Argus alone, and shuts as well as opens.
 */
public final class VaultMechanismBlockEntity extends BlockEntity {
    public static final int DOOR_TICKS = 80, CELL_TICKS = 15, REST_TICKS = 100, GRAIN_TICKS = 10;
    /** From the last door's mechanism: the cot's head and the technician. */
    public static final BlockPos COT = new BlockPos(-3, -4, 3), TECHNICIAN = new BlockPos(2, -4, -3);
    /** Door cells stand in the three rows and columns under a door's mechanism. */
    public static final int CELL_TOP = -2, CELL_BOTTOM = -4;
    private int progress, rest;
    private boolean moving;
    /** Client only: progress as drawn, easing toward what the server last sent. */
    private float shown, shownBefore;

    public VaultMechanismBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_MECHANISM.get(), pos, state); }

    public Kind kind() { return getBlockState().getValue(VaultMechanismBlock.KIND); }
    public int progress() { return progress; }
    public int cellsOut() { return progress / CELL_TICKS; }
    public boolean finished() {
        return progress >= switch (kind()) { case DOOR, GATE -> DOOR_TICKS; case BRIDGE -> VaultBridge.SPAN * CELL_TICKS; case LAST_DOOR -> 1; };
    }
    public float shown(float partial) { return Mth.lerp(partial, shownBefore, shown); }

    public static void clientTick(Level level, BlockPos pos, BlockState state, VaultMechanismBlockEntity self) {
        self.shownBefore = self.shown;
        self.shown += Mth.clamp(self.progress - self.shown, -2, 2);
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, VaultMechanismBlockEntity self) {
        if (self.finished() || self.kind() == Kind.GATE || !(level instanceof ServerLevel server)) return;
        if (self.kind() == Kind.LAST_DOOR) { self.tickRest(server); return; }
        boolean move = server.hasNearbyAlivePlayer(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, VaultWatch.RANGE) && self.wants(server)
                && !self.isWatched(server);
        if (move) self.advance(server);
        else if (self.moving) self.publish();
        self.moving = move;
    }

    private boolean wants(ServerLevel level) {
        if (kind() != Kind.BRIDGE) return true;
        for (var player : level.players()) {
            if (player.isSpectator() || player.getBlockZ() != worldPosition.getZ()) continue;
            double feet = player.getY() - worldPosition.getY();
            int standing = player.getBlockX() - worldPosition.getX() - 1;
            if (feet >= 1 && feet <= 4 && standing >= -2 && standing <= VaultBridge.SPAN && VaultBridge.wants(cellsOut(), standing)) return true;
        }
        return false;
    }
    private boolean isWatched(ServerLevel level) {
        if (kind() != Kind.BRIDGE) return VaultWatch.watched(level, this::isCell, doorCentre());
        // The gap where the next cell goes: its floor, and the air at a body's eye line above it.
        var gap = Vec3.atBottomCenterOf(worldPosition.offset(cellsOut() + 1, 2, 0));
        return VaultWatch.watched(level, pos -> false, gap, gap.add(0, 1.6, 0));
    }
    private Vec3 doorCentre() { return Vec3.atCenterOf(worldPosition.below(3)); }
    private boolean isCell(BlockPos pos) {
        int dy = pos.getY() - worldPosition.getY();
        return pos.getX() == worldPosition.getX() && dy >= CELL_BOTTOM && dy <= CELL_TOP && Math.abs(pos.getZ() - worldPosition.getZ()) <= 1;
    }

    private void advance(ServerLevel level) {
        progress++;
        if (kind() == Kind.DOOR) {
            // The panel slides toward +z: each column of the doorway clears as its trailing edge passes.
            if (progress % (DOOR_TICKS / 4) == 0 || progress == DOOR_TICKS) clearColumns(level, progress * 3 / DOOR_TICKS);
        } else if (progress % CELL_TICKS == 0) {
            level.setBlock(worldPosition.offset(cellsOut(), 1, 0), ModBlocks.VAULT_WALKWAY.get().defaultBlockState(), 3);
        }
        if (progress % GRAIN_TICKS == 1) level.playSound(null, worldPosition, ModSounds.VAULT_GRIND.get(), SoundSource.BLOCKS, 1, 1);
        setChanged();
        if (progress % 2 == 0 || finished()) publish();
    }
    private void clearColumns(ServerLevel level, int columns) {
        for (int column = 0; column < columns; column++) for (int dy = CELL_BOTTOM; dy <= CELL_TOP; dy++)
            level.setBlock(worldPosition.offset(0, dy, column - 1), Blocks.AIR.defaultBlockState(), 3);
    }

    /** Lying on the cot is the only thing that opens this door, and only if no one else faces it when the rest ends. */
    private void tickRest(ServerLevel level) {
        var cot = worldPosition.offset(COT);
        ServerPlayer rester = null;
        for (var player : level.players()) if (player.getSleepingPos().filter(cot::equals).isPresent()) rester = player;
        if (rester == null) { rest = 0; return; }
        if (++rest < REST_TICKS) return;
        rest = 0;
        if (!VaultWatch.watched(level, this::isCell, doorCentre())) {
            progress = 1;
            clearColumns(level, 3);
            if (level.getBlockEntity(worldPosition.offset(TECHNICIAN)) instanceof VaultTechnicianBlockEntity technician) technician.turn();
            setChanged();
            publish();
        }
        rester.stopSleepInBed(false, true);
    }

    /** A gate, set by Argus: the blocks change at once and the panel slides after them. */
    public void gate(ServerLevel level, boolean open) {
        if (open == finished()) return;
        progress = open ? DOOR_TICKS : 0;
        if (open) clearColumns(level, 3);
        else placeCells(level);
        level.playSound(null, worldPosition.below(3), ModSounds.VAULT_GRIND.get(), SoundSource.BLOCKS, 1, 1);
        setChanged();
        publish();
    }
    private void placeCells(ServerLevel level) {
        var cell = ModBlocks.VAULT_DOOR_CELL.get().defaultBlockState().setValue(VaultDoorCellBlock.LAST, kind() == Kind.LAST_DOOR);
        for (int dz = -1; dz <= 1; dz++) for (int dy = CELL_BOTTOM; dy <= CELL_TOP; dy++) level.setBlock(worldPosition.offset(0, dy, dz), cell, 3);
    }

    private void publish() { if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }

    /** Playtest only: back to the start, cells and all. */
    public void reset(ServerLevel level) {
        progress = 0; rest = 0; moving = false;
        if (kind() == Kind.BRIDGE) for (int i = 1; i <= VaultBridge.SPAN; i++) level.setBlock(worldPosition.offset(i, 1, 0), Blocks.AIR.defaultBlockState(), 3);
        else placeCells(level);
        setChanged();
        publish();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("progress", progress);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("progress");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
