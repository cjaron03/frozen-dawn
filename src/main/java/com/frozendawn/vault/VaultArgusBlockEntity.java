package com.frozendawn.vault;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.VaultPurgePayload;
import com.frozendawn.radiation.ReactorAdvancements;
import com.frozendawn.vault.ArgusFight.Phase;
import com.frozendawn.vault.VaultBoardBlock.Lamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The fight, in the world: shows {@link ArgusFight} on the board, the fuses, the clocks, the lights and the gates, drives the
 * Custodian, and keeps it all through a reload. Every tick it sets what should be showing, so nothing depends on having
 * seen a change happen. Two sounds it sends only to those inside: the recording and the pulse must not reach the hall above.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class VaultArgusBlockEntity extends BlockEntity {
    /** How long a fight goes on with nobody left inside. */
    public static final int ABANDON_TICKS = 100;
    /** The recording's length and the hiss after it. */
    private static final int ANNOUNCE_TICKS = 24 * 20;
    private final ArgusFight fight = new ArgusFight(new Random());
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.frozendawn.custodian"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6);
    private @Nullable CustodianEntity custodian;
    private int abandoned, announceIn;
    private boolean announcing;
    private long nextPulse;

    public VaultArgusBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_ARGUS.get(), pos, state); }

    public ArgusFight fight() { return fight; }
    public @Nullable CustodianEntity custodian() { return custodian; }
    public boolean announcing() { return announcing; }
    public float barProgress() { return bar.getProgress(); }
    private BlockPos hall() { return worldPosition.subtract(TerminusVaultBuilder.ARGUS); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VaultArgusBlockEntity self) {
        if (level instanceof ServerLevel server) self.tick(server);
    }

    private void tick(ServerLevel level) {
        var inside = inside(level);
        if (fight.phase() == Phase.WON) { show(level); return; }
        var sublevel = sublevel(level);
        if (!sublevel.isEmpty() || fight.running()) findCustodian(level);
        if (fight.phase() == Phase.DORMANT) {
            announce(sublevel);
            if (inside.stream().anyMatch(this::pastTheWestCatwalk)) begin(sublevel);
        } else {
            abandoned = inside.isEmpty() ? abandoned + 1 : 0;
            if (abandoned >= ABANDON_TICKS) { fight.abandon(); standDown(level); }
            else if (fight.tick(inside.stream().anyMatch(this::onUpperLevel))) purge(level, inside);
            else pulse(level, inside);
        }
        if (custodian != null) custodian.drive(worldPosition, fight.running(), fight.phase() == Phase.BURN, fight.floor());
        show(level);
        updateBar(inside);
    }

    private void begin(List<ServerPlayer> sublevel) {
        fight.start();
        abandoned = 0;
        nextPulse = 0;
        stopAnnouncing(sublevel);
        setChanged();
    }

    private void findCustodian(ServerLevel level) {
        if (custodian != null && custodian.isAlive()) return;
        custodian = null;
        var found = level.getEntitiesOfClass(CustodianEntity.class, hallBox(), CustodianEntity::isAlive);
        if (!found.isEmpty()) { custodian = found.getFirst(); return; }
        if (!level.areEntitiesLoaded(ChunkPos.asLong(worldPosition))) return;
        var spawned = ModEntities.CUSTODIAN.get().create(level);
        if (spawned == null) return;
        spawned.moveTo(start());
        level.addFreshEntity(spawned);
        custodian = spawned;
    }
    private Vec3 start() { return Vec3.atBottomCenterOf(hall().offset(TerminusVaultBuilder.CUSTODIAN)); }

    /** Sets what should be showing. Each write is skipped when the block already shows it. */
    private void show(ServerLevel level) {
        var hall = hall();
        for (int glyph = 0; glyph < ArgusFight.GLYPHS; glyph++) {
            var at = hall.offset(TerminusVaultBuilder.BOARD[glyph]);
            set(level, at, level.getBlockState(at).trySetValue(VaultBoardBlock.LAMP, fight.boardLit(glyph) ? Lamp.LIT : Lamp.DARK));
        }
        for (int slot = 0; slot < ArgusFight.GLYPHS; slot++) {
            var at = hall.offset(TerminusVaultBuilder.FUSES[slot]);
            var lamp = fight.phase() != Phase.PULL ? Lamp.DARK : fight.pulled(slot) ? Lamp.PULLED : Lamp.LIT;
            set(level, at, level.getBlockState(at).trySetValue(VaultBoardBlock.LAMP, lamp).trySetValue(VaultBoardBlock.GLYPH, fight.fuses()[slot]));
        }
        for (var clock : TerminusVaultBuilder.CLOCKS)
            if (level.getBlockEntity(hall.offset(clock)) instanceof VaultFightClockBlockEntity be) be.show(fight.seconds());
        int lit = fight.phase() == Phase.DORMANT ? TerminusVaultBuilder.LIGHTS.size() : fight.lights(TerminusVaultBuilder.LIGHTS.size());
        // Once it falls the lamps stay as the clock left them.
        if (fight.phase() != Phase.WON) for (int i = 0; i < TerminusVaultBuilder.LIGHTS.size(); i++) {
            var at = hall.offset(TerminusVaultBuilder.LIGHTS.get(i));
            set(level, at, level.getBlockState(at).trySetValue(VaultLightBlock.LIT, i < lit));
        }
        gate(level, TerminusVaultBuilder.WEST_DOOR, !fight.running());
        gate(level, TerminusVaultBuilder.EAST_DOOR, fight.phase() == Phase.WON);
    }
    private static void set(ServerLevel level, BlockPos at, BlockState state) { if (level.getBlockState(at) != state) level.setBlock(at, state, 3); }
    private void gate(ServerLevel level, BlockPos gate, boolean open) {
        if (level.getBlockEntity(hall().offset(gate)) instanceof VaultMechanismBlockEntity mechanism) mechanism.gate(level, open);
    }

    private void updateBar(List<ServerPlayer> inside) {
        if (custodian != null) bar.setProgress(custodian.getHealth() / custodian.getMaxHealth());
        for (var player : List.copyOf(bar.getPlayers())) if (!fight.running() || !inside.contains(player)) bar.removePlayer(player);
        if (!fight.running()) return;
        for (var player : inside) bar.addPlayer(player);
        setChanged();
    }

    /** Use held on a fuse. */
    public void holdFuse(ServerPlayer player, int slot) {
        if (!(level instanceof ServerLevel server) || fight.phase() != Phase.PULL || fight.pulled(slot)) return;
        var at = hall().offset(TerminusVaultBuilder.FUSES[slot]);
        long now = server.getGameTime();
        if (!fight.hold(slot, now)) {
            int filled = Math.round(fight.held(slot, now) * 10);
            player.displayClientMessage(Component.literal("[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]"), true);
            server.playSound(null, at, ModSounds.VAULT_FUSE.get(), SoundSource.BLOCKS, .4f, .8f + fight.held(slot, now) * .6f);
            return;
        }
        switch (fight.pull(slot)) {
            case WRONG -> server.playSound(null, at, ModSounds.VAULT_WRONG.get(), SoundSource.BLOCKS, 1, 1);
            case RIGHT -> server.playSound(null, at, ModSounds.VAULT_FUSE.get(), SoundSource.BLOCKS, 1, 1.5f);
            case COMPLETE -> server.playSound(null, hall().offset(TerminusVaultBuilder.CUSTODIAN), ModSounds.VAULT_SHELL.get(), SoundSource.HOSTILE, 1.5f, 1);
            case NOTHING -> { }
        }
        player.displayClientMessage(Component.empty(), true);
        show(server);
        setChanged();
    }

    /** From the Custodian's own death. A clean run is every survivor's. */
    public void custodianFell() {
        if (!(level instanceof ServerLevel server) || !fight.running()) return;
        fight.fell();
        if (fight.clean()) for (var player : inside(server)) ReactorAdvancements.grant(player, "last_badge_out");
        bar.removeAllPlayers();
        show(server);
        setChanged();
    }

    /** The white-out. Half their hearts, which is never the last of them. */
    private void purge(ServerLevel level, List<ServerPlayer> inside) {
        var ledge = Vec3.atBottomCenterOf(hall().offset(TerminusVaultBuilder.LEDGE));
        for (var player : inside) {
            PacketDistributor.sendToPlayer(player, new VaultPurgePayload());
            level.playSound(null, player.blockPosition(), ModSounds.VAULT_PURGE.get(), SoundSource.BLOCKS, 1, 1);
            player.setPos(ledge);
            player.teleportTo(level, ledge.x, ledge.y, ledge.z, -90, 0);
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth() / 2));
        }
        fight.purge();
        standDown(level);
    }
    private void standDown(ServerLevel level) {
        abandoned = 0;
        if (custodian != null) custodian.standDown(start());
        setChanged();
    }

    private void pulse(ServerLevel level, List<ServerPlayer> inside) {
        long now = level.getGameTime();
        if (now < nextPulse) return;
        nextPulse = now + fight.pulseEvery();
        for (var player : inside) send(player, ModSounds.VAULT_PULSE.get(), fight.pulseVolume());
    }

    private void announce(List<ServerPlayer> sublevel) {
        if (sublevel.isEmpty()) { announcing = false; announceIn = 0; return; }
        announcing = true;
        if (--announceIn > 0) return;
        announceIn = ANNOUNCE_TICKS;
        for (var player : sublevel) send(player, ModSounds.VAULT_ANNOUNCEMENT.get(), .7f);
    }
    private void stopAnnouncing(List<ServerPlayer> sublevel) {
        announcing = false;
        announceIn = 0;
        for (var player : sublevel) player.connection.send(new ClientboundStopSoundPacket(ModSounds.VAULT_ANNOUNCEMENT.getId(), SoundSource.BLOCKS));
    }
    private static void send(ServerPlayer player, SoundEvent sound, float volume) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        player.connection.send(new ClientboundSoundEntityPacket(holder, SoundSource.BLOCKS, player, volume, 1, player.getRandom().nextLong()));
    }

    private AABB hallBox() { return hallBox(hall()); }
    private static AABB hallBox(BlockPos hall) {
        return new AABB(hall.getX() + 18, hall.getY() + TerminusVaultBuilder.LOWER_FLOOR, hall.getZ() - 11, hall.getX() + 40, hall.getY() + TerminusVaultBuilder.CEILING, hall.getZ() + 12);
    }
    private List<ServerPlayer> inside(ServerLevel level) {
        var box = hallBox();
        return alive(level).stream().filter(p -> box.contains(p.position())).toList();
    }
    /** Everyone from the landing to the vault. */
    private List<ServerPlayer> sublevel(ServerLevel level) {
        var hall = hall();
        var box = new AABB(hall.getX() - 3, hall.getY() + TerminusVaultBuilder.PIT_BOTTOM, hall.getZ() - 12, hall.getX() + TerminusVaultBuilder.REACH, hall.getY() + TerminusVaultBuilder.CEILING, hall.getZ() + 13);
        return alive(level).stream().filter(p -> box.contains(p.position())).toList();
    }
    private static List<ServerPlayer> alive(ServerLevel level) {
        var found = new ArrayList<ServerPlayer>();
        for (var player : level.players()) if (player.isAlive() && !player.isSpectator()) found.add(player);
        return found;
    }
    /** The west catwalk, with the board and the Seal Order, is where you may stand and look. Past it the fight starts. */
    private boolean pastTheWestCatwalk(ServerPlayer player) { return player.getX() >= hall().getX() + 20 || !onUpperLevel(player); }
    private boolean onUpperLevel(ServerPlayer player) { return player.getY() > hall().getY() + TerminusVaultBuilder.FLOOR; }

    /** Playtest only: back to before anyone came. */
    public void reset(ServerLevel level) {
        fight.reset();
        if (custodian != null) custodian.discard();
        for (var old : level.getEntitiesOfClass(CustodianEntity.class, hallBox())) old.discard();
        custodian = null;
        bar.removeAllPlayers();
        abandoned = 0;
        for (var at : TerminusVaultBuilder.LIGHTS) set(level, hall().offset(at), level.getBlockState(hall().offset(at)).trySetValue(VaultLightBlock.LIT, true));
        setChanged();
    }

    /** Nothing is placed on the floor until the Custodian falls: no torches against the dark, no walls against it. */
    public static boolean refuses(ServerLevel level, BlockPos pos) {
        var hall = HelionSiteState.get(level.getServer()).vault();
        return hall != null && level.dimension() == Level.OVERWORLD && hallBox(hall).contains(Vec3.atCenterOf(pos))
                && level.getBlockEntity(hall.offset(TerminusVaultBuilder.ARGUS)) instanceof VaultArgusBlockEntity argus && argus.fight.phase() != Phase.WON;
    }
    @SubscribeEvent public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && refuses(level, event.getPos())) event.setCanceled(true);
    }

    @Override public void setRemoved() { bar.removeAllPlayers(); super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fight", fight.save());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fight")) fight.load(tag.getCompound("fight"));
    }
}
