package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.vault.TwoPersonRule.Phase;
import com.frozendawn.vault.TwoPersonRule.Pose;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The drill hall, in the world: takes {@link TwoPersonRule}'s recording from a player, walks a {@link VaultRecordingEntity}
 * through it, and sets the keys, the console and the door each tick. Nothing is saved: after a reload the room stands idle.
 * The door also opens for anyone past it, so the Morgue is never a trap.
 */
public final class VaultRecorderBlockEntity extends BlockEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final TwoPersonRule rule = new TwoPersonRule();
    private @Nullable UUID recorder;
    private @Nullable VaultRecordingEntity ghost;
    private Phase shown = Phase.IDLE;
    private boolean doorWas;

    public VaultRecorderBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_RECORDER.get(), pos, state); }

    public TwoPersonRule rule() { return rule; }
    public @Nullable VaultRecordingEntity ghost() { return ghost; }
    private BlockPos hall() { return worldPosition.subtract(TerminusVaultBuilder.RECORDER); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VaultRecorderBlockEntity self) {
        if (level instanceof ServerLevel server) self.tick(server);
    }

    private void tick(ServerLevel level) {
        rule.tick(level.getGameTime(), body(level));
        if (rule.phase() != shown) changed(level);
        walk(level);
        show(level);
        if (rule.phase() == Phase.RECORDING && level.getPlayerByUUID(recorder) instanceof ServerPlayer player)
            player.displayClientMessage(Component.literal("REC " + ArgusFight.text((rule.ticksLeft() + 19) / 20)), true);
    }

    private void changed(ServerLevel level) {
        if (rule.phase() == Phase.PLAYING) LOGGER.info("[Two-Person] Take done, playing back");
        else if (shown == Phase.RECORDING) LOGGER.info("[Two-Person] Take abandoned");
        else if (shown == Phase.PLAYING) LOGGER.info("[Two-Person] Playback over");
        level.playSound(null, worldPosition, ModSounds.VAULT_FUSE.get(), SoundSource.BLOCKS, 1, rule.phase() == Phase.PLAYING ? 1.5F : .7F);
        shown = rule.phase();
    }

    /** The recorder, while there is one and they are still in the room. */
    private @Nullable Pose body(ServerLevel level) {
        if (recorder == null || rule.phase() != Phase.RECORDING || !(level.getPlayerByUUID(recorder) instanceof ServerPlayer player)) return null;
        if (!player.isAlive() || player.isSpectator() || !room().contains(player.position())) return null;
        return new Pose(player.getX(), player.getY(), player.getZ(), player.getYHeadRot(), player.getXRot());
    }
    private AABB room() {
        var hall = hall();
        return new AABB(hall.getX() + 66, hall.getY() + TerminusVaultBuilder.FLOOR, hall.getZ() - 7, hall.getX() + 83, hall.getY() + TerminusVaultBuilder.ROOM_ROOF, hall.getZ() + 8);
    }

    private void walk(ServerLevel level) {
        var frame = rule.ghost();
        if (frame == null) { dismiss(); return; }
        if (ghost == null || ghost.isRemoved()) {
            ghost = ModEntities.VAULT_RECORDING.get().create(level);
            if (ghost == null) return;
            ghost.drive(frame.pose());
            level.addFreshEntity(ghost);
        } else ghost.drive(frame.pose());
    }
    private void dismiss() {
        if (ghost != null) ghost.discard();
        ghost = null;
    }

    /** Sets what should be showing. Each write is skipped when the block already shows it. */
    private void show(ServerLevel level) {
        var hall = hall();
        long now = level.getGameTime();
        var mode = VaultRecorderBlock.Mode.of(rule.phase());
        for (int dz = -1; dz <= 1; dz++) set(level, worldPosition.offset(0, 0, dz), level.getBlockState(worldPosition.offset(0, 0, dz)).trySetValue(VaultRecorderBlock.MODE, mode));
        for (int key = 0; key < TwoPersonRule.KEYS; key++) {
            var at = hall.offset(TerminusVaultBuilder.KEYS[key]);
            set(level, at, level.getBlockState(at).trySetValue(VaultKeyBlock.HELD, rule.held(key, now)));
        }
        if (!(level.getBlockEntity(hall.offset(TerminusVaultBuilder.DRILL_DOOR)) instanceof VaultMechanismBlockEntity door)) return;
        boolean open = rule.doorOpen() || door.someoneInOrBehind(level);
        if (rule.doorOpen() && !doorWas) LOGGER.info("[Two-Person] Both keys held: the door opens");
        doorWas = rule.doorOpen();
        door.gate(level, open);
        for (int dz = -1; dz <= 1; dz++) {
            var lamp = hall.offset(TerminusVaultBuilder.DRILL_DOOR).below().offset(0, 0, dz);
            set(level, lamp, level.getBlockState(lamp).trySetValue(VaultTallyBlock.LIT, open));
        }
    }
    private static void set(ServerLevel level, BlockPos at, BlockState state) { if (level.getBlockState(at) != state) level.setBlock(at, state, 3); }

    /** The record button. */
    public void press(ServerPlayer player) {
        if (!rule.press()) return;
        recorder = player.getUUID();
        LOGGER.info("[Two-Person] {} starts a take", player.getGameProfile().getName());
    }

    /** Use held on a key. */
    public void holdKey(ServerPlayer player, int key) {
        if (level != null) rule.hold(key, level.getGameTime(), rule.phase() == Phase.RECORDING && player.getUUID().equals(recorder));
    }

    /** Playtest only: back to before anyone came. */
    public void reset() {
        rule.reset();
        recorder = null;
        dismiss();
    }

    @Override public void setRemoved() { dismiss(); super.setRemoved(); }
}
