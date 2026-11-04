package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Morgue, in the world: reads the plate into {@link MorgueGate}, bites whoever stands in the vent, and sets the panel, the
 * plate, the lamp and the door each tick. Nothing is saved but what the panel shows. The door also opens for anyone past it,
 * so nobody who took the wand is shut in with it.
 */
public final class VaultVitalsBlockEntity extends BlockEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final MorgueGate gate = new MorgueGate();
    /** What the panel shows, on either side. */
    private int hearts;
    private boolean none;

    public VaultVitalsBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_VITALS.get(), pos, state); }

    public MorgueGate gate() { return gate; }
    public int hearts() { return hearts; }
    public boolean none() { return none; }
    private BlockPos hall() { return worldPosition.subtract(TerminusVaultBuilder.VITALS); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VaultVitalsBlockEntity self) {
        if (level instanceof ServerLevel server) self.tick(server);
    }

    private void tick(ServerLevel level) {
        var hall = hall();
        var plate = column(hall, 94, 95);
        var vent = column(hall, 88, 91);
        boolean bites = MorgueGate.bites(level.getGameTime());
        float healthiest = MorgueGate.NOBODY;
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (plate.contains(player.position())) healthiest = Math.max(healthiest, player.getHealth() + player.getAbsorptionAmount());
            if (bites && vent.contains(player.position())) player.hurt(level.damageSources().source(ModDamageTypes.VAULT_COLD), MorgueGate.VENT_DAMAGE);
        }
        boolean was = gate.open();
        gate.tick(healthiest);
        if (gate.open() && !was) LOGGER.info("[Morgue] Reads nobody at {}: the door opens", healthiest);
        show(level, healthiest != MorgueGate.NOBODY);
    }
    /** Three blocks wide about the room's middle, floor to roof. */
    private static AABB column(BlockPos hall, int x0, int x1) {
        return new AABB(hall.getX() + x0, hall.getY() + TerminusVaultBuilder.FLOOR, hall.getZ() - 1, hall.getX() + x1, hall.getY() + TerminusVaultBuilder.ROOM_ROOF, hall.getZ() + 2);
    }

    /** Sets what should be showing. Each write is skipped when the block already shows it. */
    private void show(ServerLevel level, boolean read) {
        var hall = hall();
        for (int dz = -1; dz <= 1; dz++) set(level, hall.offset(94, TerminusVaultBuilder.FLOOR, dz), VaultTallyBlock.LIT, read);
        if (hearts != gate.hearts() || none != gate.open()) {
            hearts = gate.hearts();
            none = gate.open();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        if (!(level.getBlockEntity(hall.offset(TerminusVaultBuilder.MORGUE_DOOR)) instanceof VaultMechanismBlockEntity door)) return;
        boolean open = gate.open() || door.someoneInOrBehind(level);
        door.gate(level, open);
        for (int dz = -1; dz <= 1; dz++) set(level, hall.offset(TerminusVaultBuilder.MORGUE_DOOR).below().offset(0, 0, dz), VaultTallyBlock.LIT, open);
    }
    private static void set(ServerLevel level, BlockPos at, net.minecraft.world.level.block.state.properties.BooleanProperty property, boolean value) {
        var state = level.getBlockState(at).trySetValue(property, value);
        if (level.getBlockState(at) != state) level.setBlock(at, state, 3);
    }

    /** Playtest only: back to before anyone came. */
    public void reset() { gate.reset(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("hearts", hearts);
        tag.putBoolean("none", none);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hearts = tag.getInt("hearts");
        none = tag.getBoolean("none");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
