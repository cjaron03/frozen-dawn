package com.frozendawn.block;

import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.IcarusAudioCues;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusAdvancements;
import com.frozendawn.power.IcarusPhysics;
import com.frozendawn.world.IcarusDetonation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** AMR-1 Icarus Controller: validates the open structure, steps IcarusPhysics once per second, moves capsules and matter
 * between the ports and the core, keeps the shield space clear and publishes the reactor's draw and output to the Power Port. */
public final class IcarusControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BUTTON_START = 0, BUTTON_COLE = 1, BUTTON_CLIP = 2, BUTTON_BLUEPRINT = 3;
    // Menu data, synced as shorts: power in kW, shield in hundredths of a percent, reserve in tenths of a MJ, charge in
    // hundredths of a capsule, settings in kW and permille. ETAs are whole seconds, -1 when nothing is running out.
    public static final int STATE = 0, STATUS = 1, STEP_LEFT = 2, GROSS = 3, SHIELD_DRAW = 4, NET = 5, REQUEST = 6, CORE_C = 7, SHIELD = 8,
            RESERVE = 9, CHARGE = 10, NEXT_CHARGE = 11, MATTER = 12, EMPTIES = 13, ETA_SHIELD = 14, ETA_RED_LINE = 15, ETA_RESERVE = 16,
            ETA_ANTIMATTER = 17, ALARM = 18, CASCADE = 19, STRUCTURE = 20, FLAGS = 21, OUTPUT_KW = 22, SHIELD_SET = 23, FEED = 24, DATA = 25;
    public static final int F_VALID = 1, F_COMMISSIONED = 2;
    /** Fire damage per second to anything inside the shield. */
    private static final float SHIELD_DAMAGE = 4;
    private static final double SHIELD_RADIUS = 4.5;
    /** One-shots carry about 32 blocks, across the whole structure and some way beyond. */
    private static final float CUE_VOLUME = 2;
    private static final IcarusPhysics.Readout IDLE = new IcarusPhysics.Readout(IcarusPhysics.State.COLD, IcarusPhysics.Status.OK, 0, 0, 0, 0, 0,
            IcarusPhysics.AMBIENT_C, 0, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY, IcarusPhysics.Alarm.NONE, IcarusPhysics.Cascade.STABLE, false);
    private final IcarusPhysics physics = new IcarusPhysics();
    private IcarusPhysics.Readout readout = IDLE;
    private IcarusStructure.Problem problem = IcarusStructure.Problem.UNLOADED;
    @Nullable private BlockPos publishedPort;
    /** The redstone signal as of the last second; a rising edge is a CLIP. Saved, so a held lever does not CLIP on reload. */
    private boolean signal;
    /** What the dock and matter port held at the last second, for the screen. */
    private long nextCharge;
    private int matter, empties;
    /** Players who opened this Controller, for the advancements and the blast's witnesses; saved. */
    private final Set<UUID> crew = new HashSet<>();
    /** Players shown this Controller's ghost-block blueprint; transient, like the client's own copy. */
    private final Set<UUID> blueprintViewers = new HashSet<>();
    /** The readout the last cues were taken from; null after a load, so the first second plays nothing. */
    @Nullable private IcarusPhysics.Readout heard;
    /** Seconds the alarm has stayed critical since it rose, for the suit's repeat. */
    private int criticalSeconds;
    /** A full Cole Protocol has finished since the core last ignited, for Wake Me When You Need Me. */
    private boolean coleDone;
    public IcarusControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_CONTROLLER.get(), p, s); }

    public IcarusPhysics physics() { return physics; }
    public IcarusPhysics.Readout readout() { return readout; }
    public Set<UUID> crew() { return crew; }
    public IcarusStructure.Problem problem() { return problem; }

    public List<GhostCell> blueprint(Level l) {
        return IcarusStructure.wrongCells(l, worldPosition, getBlockState().getValue(IcarusControllerBlock.FACING), Integer.MAX_VALUE);
    }
    /** Shows or hides the blueprint for one player; returns whether it is now shown. */
    public boolean toggleBlueprint(UUID viewer) { return blueprintViewers.remove(viewer) ? false : blueprintViewers.add(viewer); }
    public boolean showsBlueprintTo(UUID viewer) { return blueprintViewers.contains(viewer); }

    public void serverTick() { if (level instanceof ServerLevel l && l.getGameTime() % 20 == 0) second(l); }

    /** One second of reactor time. Also public for GameTests, which drive it directly. */
    public void second(ServerLevel l) {
        var phase = physics.state();
        boolean checkEnvelope = phase == IcarusPhysics.State.COLD || phase == IcarusPhysics.State.POWER_CHECK;
        var result = IcarusStructure.validate(l, worldPosition, getBlockState().getValue(IcarusControllerBlock.FACING), checkEnvelope);
        if (result.problem() == IcarusStructure.Problem.UNLOADED) return;
        problem = result.problem();
        boolean valid = result.valid();
        if (valid) blueprintViewers.clear();
        var port = valid && l.getBlockEntity(result.powerPort()) instanceof IcarusPowerPortBlockEntity p ? p : null;
        var dock = valid && l.getBlockEntity(result.capsuleDock()) instanceof IcarusCapsuleDockBlockEntity d ? d : null;
        var feed = valid && l.getBlockEntity(result.matterPort()) instanceof IcarusMatterPortBlockEntity m ? m : null;
        var portAt = port == null ? null : result.powerPort();
        if (publishedPort != null && !publishedPort.equals(portAt)) release(l);
        publishedPort = portAt;
        if (valid && readout.shieldPct() > 0) shield(l, result);
        // CLIP before the step, so the readout this second already shows the cut stream.
        boolean powered = l.hasNeighborSignal(worldPosition);
        if (powered && !signal) physics.clip();
        signal = powered;
        long room = IcarusPhysics.MAX_CAPSULES * IcarusPhysics.CAPSULE - physics.coreCharge();
        long injectable = dock == null ? 0 : dock.injectable(room);
        readout = physics.step(new IcarusPhysics.Inputs(valid, port != null && port.paidFullSecond(), injectable,
                dock == null ? 0 : dock.empties(), feed == null ? 0 : feed.count()));
        if (readout.detonate() && physics.coreCharge() > 0) { detonate(l); return; }
        if (phase == IcarusPhysics.State.COLE_DISCHARGE && readout.state() == IcarusPhysics.State.COLD) coleDone = true;
        var before = heard;
        sound(l, valid ? result.core() : worldPosition);
        advancements(l, before, valid ? result.core() : worldPosition, portAt);
        if (dock != null) { dock.consume(readout.injected()); dock.recover(readout.recovered()); }
        if (feed != null) feed.use(readout.matterUsed());
        if (port != null) port.publish((int) Math.round(readout.requestKw() * 1000), (int) Math.round(Math.max(0, readout.netMw()) * 1e6));
        nextCharge = physics.coreCharge() + (dock == null ? 0 : dock.injectable(IcarusPhysics.MAX_CAPSULES * IcarusPhysics.CAPSULE - physics.coreCharge()));
        empties = dock == null ? 0 : dock.empties();
        matter = feed == null ? 0 : feed.count();
        // Published even while the frame is incomplete, so a core never stays hidden after its reactor goes cold.
        if (l.getBlockEntity(result.core()) instanceof HalseyCoreBlockEntity core)
            core.publish(l, new HalseyCoreBlockEntity.Visual(physics.state(), (int) Math.round(readout.coreC()), Math.round(readout.shieldPct() * 100) / 100.0,
                    Math.round(readout.grossMw() * 100) / 100.0, readout.alarm(), readout.stepSecondsLeft()));
        setChanged();
    }

    /** This second's one-shots at the core, and the suit's alarm line to crew in sealed suits in this level. */
    private void sound(ServerLevel l, BlockPos at) {
        for (var cue : IcarusAudioCues.cues(heard, readout)) l.playSound(null, at, cueSound(cue), SoundSource.BLOCKS, CUE_VOLUME, 1f);
        boolean stillCritical = heard != null && heard.alarm() == IcarusPhysics.Alarm.CRITICAL && readout.alarm() == IcarusPhysics.Alarm.CRITICAL;
        criticalSeconds = stillCritical ? criticalSeconds + 1 : 0;
        var line = IcarusAudioCues.suitLine(heard, readout, criticalSeconds);
        heard = readout;
        if (line == null) return;
        var event = switch (line) {
            case CAUTION -> ModSounds.SUIT_ICARUS_CAUTION.get();
            case WARNING -> ModSounds.SUIT_ICARUS_WARNING.get();
            default -> ModSounds.SUIT_ICARUS_CRITICAL.get();
        };
        for (var id : crew)
            if (l.getPlayerByUUID(id) instanceof ServerPlayer p && SuitIntegrityHandler.isWearingSealedSuit(p)) p.playNotifySound(event, SoundSource.PLAYERS, 1f, 1f);
    }

    private void advancements(ServerLevel l, @Nullable IcarusPhysics.Readout before, BlockPos core, @Nullable BlockPos port) {
        var earned = new ArrayList<>(IcarusAdvancements.earned(before, readout, coleDone));
        if (earned.contains(IcarusAdvancements.E_EQUALS_MC2)) coleDone = false;
        if (readout.state() == IcarusPhysics.State.RUNNING && port != null && IcarusAdvancements.continuity(l, port)) earned.add(IcarusAdvancements.CONTINUITY);
        IcarusAdvancements.grantCrewNear(l, core, crew, earned);
    }

    private static SoundEvent cueSound(IcarusAudioCues.Cue cue) {
        return switch (cue) {
            case FRAME_HUM_ON -> ModSounds.ICARUS_POWER_ON.get();
            case STABILIZER_SPINUP -> ModSounds.ICARUS_STABILIZER_SPINUP.get();
            case SHIELD_FORM -> ModSounds.ICARUS_SHIELD_FORM.get();
            case CAPSULE_DOCK -> ModSounds.ICARUS_CAPSULE_DOCK.get();
            case INJECTOR_FEED -> ModSounds.ICARUS_INJECTOR_FEED.get();
            case IGNITION -> ModSounds.ICARUS_IGNITION.get();
            case CLIP_SLAM -> ModSounds.ICARUS_CLIP_SLAM.get();
            case WIND_DOWN -> ModSounds.ICARUS_WIND_DOWN.get();
            case CORE_COLLAPSE -> ModSounds.ICARUS_CORE_COLLAPSE.get();
            case COLE_COMPLETE -> ModSounds.ICARUS_COLE_COMPLETE.get();
            case ALARM_CAUTION -> ModSounds.ICARUS_ALARM_CAUTION.get();
            case ALARM_WARNING -> ModSounds.ICARUS_ALARM_WARNING.get();
            case ALARM_CRITICAL -> ModSounds.ICARUS_ALARM_CRITICAL.get();
        };
    }

    /** The shield fills its space: blocks in it are vaporized without drops, and anything alive inside burns. */
    private void shield(ServerLevel l, IcarusStructure.Result result) {
        for (var pos : result.envelope()) {
            if (l.getBlockState(pos).isAir()) continue;
            Clearable.tryClear(l.getBlockEntity(pos));
            l.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        var center = result.core().getCenter();
        for (var e : l.getEntitiesOfClass(LivingEntity.class, new AABB(result.core()).inflate(SHIELD_RADIUS),
                e -> e.distanceToSqr(center) <= SHIELD_RADIUS * SHIELD_RADIUS))
            e.hurt(l.damageSources().inFire(), SHIELD_DAMAGE);
    }

    /** Start, Cole Protocol or CLIP; returns whether the reactor took it. */
    public boolean press(int button) {
        boolean taken = switch (button) {
            case BUTTON_START -> physics.start();
            case BUTTON_COLE -> physics.cole();
            case BUTTON_CLIP -> physics.clip();
            default -> false;
        };
        if (taken) setChanged();
        return taken;
    }
    /** Settings from the screen, in the payload's units; the physics clamps them. */
    public void settings(int outputKw, int shieldPermille, int feedPerMinute) {
        physics.settings(new IcarusPhysics.Settings(outputKw / 1000.0, shieldPermille / 1000.0, feedPerMinute));
        setChanged();
    }

    /** One of this reactor's parts was removed by any means. A charged core goes up; an empty one is only invalid. */
    public void partRemoved(ServerLevel l) { if (physics.coreCharge() > 0) detonate(l); }

    /** The charge is cleared before the blast, so the reactor's own parts falling to it never set it off again. */
    private void detonate(ServerLevel l) {
        double radius = IcarusBlast.radius(physics.coreCharge());
        physics.detonated();
        coleDone = false;
        if (publishedPort != null) release(l);
        setChanged();
        IcarusDetonation.detonate(l, IcarusStructure.core(worldPosition, getBlockState().getValue(IcarusControllerBlock.FACING)), radius, Set.copyOf(crew));
    }

    private void release(ServerLevel l) {
        if (l.isLoaded(publishedPort) && l.getBlockEntity(publishedPort) instanceof IcarusPowerPortBlockEntity old) old.publish(0, 0);
        publishedPort = null;
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel l && publishedPort != null) release(l);
        super.setRemoved();
    }

    private static int clampShort(double v) { return (int) Math.clamp(Math.round(v), Short.MIN_VALUE, Short.MAX_VALUE); }
    private static int eta(double seconds) { return Double.isFinite(seconds) ? clampShort(Math.ceil(Math.max(0, seconds))) : -1; }
    public ContainerData data() {
        return new ContainerData() {
            @Override public int get(int i) {
                var r = readout; var s = physics.settings();
                return switch (i) {
                    case STATE -> physics.state().ordinal();
                    case STATUS -> r.status().ordinal();
                    case STEP_LEFT -> r.stepSecondsLeft();
                    case GROSS -> clampShort(r.grossMw() * 1000);
                    case SHIELD_DRAW -> clampShort(r.shieldDrawMw() * 1000);
                    case NET -> clampShort(r.netMw() * 1000);
                    case REQUEST -> clampShort(r.requestKw());
                    case CORE_C -> clampShort(r.coreC());
                    case SHIELD -> clampShort(r.shieldPct() * 100);
                    case RESERVE -> clampShort(r.reserveMJ() * 10);
                    case CHARGE -> clampShort(physics.coreCharge() * 100.0 / IcarusPhysics.CAPSULE);
                    case NEXT_CHARGE -> clampShort(nextCharge * 100.0 / IcarusPhysics.CAPSULE);
                    case MATTER -> matter;
                    case EMPTIES -> empties;
                    case ETA_SHIELD -> eta(r.shieldEtaS());
                    case ETA_RED_LINE -> eta(r.redLineEtaS());
                    case ETA_RESERVE -> eta(r.reserveEtaS());
                    case ETA_ANTIMATTER -> eta(r.antimatterEtaS());
                    case ALARM -> r.alarm().ordinal();
                    case CASCADE -> r.cascade().ordinal();
                    case STRUCTURE -> problem.ordinal();
                    case FLAGS -> (problem == IcarusStructure.Problem.OK ? F_VALID : 0) | (physics.commissioned() ? F_COMMISSIONED : 0);
                    case OUTPUT_KW -> clampShort(s.outputMw() * 1000);
                    case SHIELD_SET -> clampShort(s.shield() * 1000);
                    case FEED -> s.feedPerMinute();
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA; }
        };
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        var icarus = new CompoundTag(); physics.save(icarus); tag.put("Icarus", icarus);
        tag.putBoolean("Signal", signal);
        tag.putBoolean("ColeDone", coleDone);
        var list = new ListTag(); for (var id : crew) list.add(NbtUtils.createUUID(id)); tag.put("Crew", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        physics.load(tag.getCompound("Icarus"));
        signal = tag.getBoolean("Signal");
        coleDone = tag.getBoolean("ColeDone");
        crew.clear(); for (var id : tag.getList("Crew", Tag.TAG_INT_ARRAY)) crew.add(NbtUtils.loadUUID(id));
        readout = IDLE; heard = null; criticalSeconds = 0;
    }
    public boolean stillValid(Player p) { return !p.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.frozendawn.icarus_controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        if (crew.add(p.getUUID())) setChanged();
        return new IcarusControllerMenu(id, inv, this);
    }
}
