package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDamageTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Morgue, in the world: feeds {@link MorgueGate} who stands in the A/C's blast and on the scanner's plate, bites those in the
 * blast at MAX, and sets the panel, the plate, the lamp and the door each tick. Nothing is saved but what the panel shows and the
 * dial's own setting. The door also opens for anyone past it, so nobody who took the wand is shut in with it.
 */
public final class VaultVitalsBlockEntity extends BlockEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int NO_READING = -1;
    private static final DustParticleOptions SCAN_RED = dust(.91F, .41F, .36F), SCAN_GREEN = dust(.47F, .78F, .51F);
    private final MorgueGate gate = new MorgueGate();
    /** What the panel shows, on either side: degrees in tenths, or nothing. */
    private int tenths = NO_READING;
    private boolean none;

    public VaultVitalsBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_VITALS.get(), pos, state); }

    private static DustParticleOptions dust(float r, float g, float b) { return new DustParticleOptions(new Vector3f(r, g, b), .7F); }
    public MorgueGate gate() { return gate; }
    public int tenths() { return tenths; }
    public boolean none() { return none; }
    private BlockPos hall() { return worldPosition.subtract(TerminusVaultBuilder.VITALS); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VaultVitalsBlockEntity self) {
        if (level instanceof ServerLevel server) self.tick(server);
    }

    private void tick(ServerLevel level) {
        var hall = hall();
        var plate = new AABB(hall.getX() + 94, hall.getY() + TerminusVaultBuilder.FLOOR, hall.getZ() - 1, hall.getX() + 95, hall.getY() + TerminusVaultBuilder.ROOM_ROOF, hall.getZ() + 2);
        var blast = new AABB(hall.getX() + 87, hall.getY() + TerminusVaultBuilder.FLOOR, hall.getZ() - 4, hall.getX() + 90, hall.getY() + TerminusVaultBuilder.ROOM_ROOF, hall.getZ() - 1);
        int setting = level.getBlockState(hall.offset(TerminusVaultBuilder.AC_DIAL)).getOptionalValue(VaultAcDialBlock.SETTING).orElse(VaultAcDialBlock.LOW);
        boolean bites = MorgueGate.bites(level.getGameTime());
        var cooling = new HashSet<UUID>();
        var reading = new ArrayList<UUID>();
        for (var player : level.players()) {
            if (!player.isAlive()) gate.forget(player.getUUID());
            if (!player.isAlive() || player.isSpectator()) continue;
            if (plate.contains(player.position())) reading.add(player.getUUID());
            if (setting != VaultAcDialBlock.MAX || player.isCreative() || !blast.contains(player.position())) continue;
            cooling.add(player.getUUID());
            if (bites) player.hurt(level.damageSources().source(ModDamageTypes.VAULT_COLD), MorgueGate.BITE_DAMAGE);
        }
        boolean was = gate.open();
        gate.expose(cooling);
        gate.tick(reading);
        if (gate.open() && !was) LOGGER.info("[Morgue] Reads nobody at {} degrees: the door opens", gate.reading());
        breathe(level, setting);
        if (!reading.isEmpty()) scan(level);
        show(level, !reading.isEmpty());
    }

    /** The A/C's breath across the room, in front of the unit: a little at MID, a gale at MAX. */
    private void breathe(ServerLevel level, int setting) {
        long now = level.getGameTime();
        if (setting == VaultAcDialBlock.LOW || now % (setting == VaultAcDialBlock.MAX ? 2 : 10) != 0) return;
        var hall = hall();
        var random = level.getRandom();
        for (int i = 0; i < (setting == VaultAcDialBlock.MAX ? 5 : 1); i++)
            level.sendParticles(ParticleTypes.SNOWFLAKE, hall.getX() + 87 + random.nextDouble() * 3, hall.getY() - 13 + random.nextDouble() * 2, hall.getZ() - 3.9, 0, 0, -.06, .3, 1);
    }

    /** The scanner's bar, up and down the arch. */
    private void scan(ServerLevel level) {
        if (level.getGameTime() % 2 != 0) return;
        var hall = hall();
        double y = hall.getY() + TerminusVaultBuilder.FLOOR + 1 + 2.6 * (.5 + .5 * Mth.sin(level.getGameTime() * .12F));
        for (double z = -1; z <= 2; z += .5) level.sendParticles(gate.open() ? SCAN_GREEN : SCAN_RED, hall.getX() + 94.5, y, hall.getZ() + z, 1, 0, 0, 0, 0);
    }

    /** Sets what should be showing. Each write is skipped when the block already shows it. */
    private void show(ServerLevel level, boolean read) {
        var hall = hall();
        for (int dz = -1; dz <= 1; dz++) set(level, hall.offset(94, TerminusVaultBuilder.FLOOR, dz), read);
        int shown = gate.reading() == MorgueGate.NOBODY ? NO_READING : Math.round(gate.reading() * 10);
        if (tenths != shown || none != gate.open()) {
            tenths = shown;
            none = gate.open();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        if (!(level.getBlockEntity(hall.offset(TerminusVaultBuilder.MORGUE_DOOR)) instanceof VaultMechanismBlockEntity door)) return;
        boolean open = gate.open() || door.someoneInOrBehind(level);
        door.gate(level, open);
        for (int dz = -1; dz <= 1; dz++) set(level, hall.offset(TerminusVaultBuilder.MORGUE_DOOR).below().offset(0, 0, dz), open);
    }
    private static void set(ServerLevel level, BlockPos at, boolean lit) {
        var state = level.getBlockState(at).trySetValue(VaultTallyBlock.LIT, lit);
        if (level.getBlockState(at) != state) level.setBlock(at, state, 3);
    }

    /** Playtest only: back to before anyone came, the dial with it. */
    public void reset() {
        gate.reset();
        if (level instanceof ServerLevel server) VaultAcDialBlock.set(server, hall().offset(TerminusVaultBuilder.AC_DIAL), VaultAcDialBlock.LOW);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("tenths", tenths);
        tag.putBoolean("none", none);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tenths = tag.contains("tenths") ? tag.getInt("tenths") : NO_READING;
        none = tag.getBoolean("none");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
