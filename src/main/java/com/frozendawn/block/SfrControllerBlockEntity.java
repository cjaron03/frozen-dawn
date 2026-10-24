package com.frozendawn.block;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.particles.ParticleTypes;
import com.frozendawn.airlock.AirlockCycle;
import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.power.FusionPhysics;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.world.CombustionAtmosphere;
import com.frozendawn.world.RoomAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import com.frozendawn.init.ModSounds;
import com.frozendawn.power.SfrAudioCues;
import org.jetbrains.annotations.Nullable;

/** SFR-1 Controller: validates the torus, steps FusionPhysics once per second, feeds tritium and deuterium,
 * and publishes the reactor's draw and output to the Power Port. Readings are transient. */
public final class SfrControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 2, TRITIUM_IN = 0, TRITIUM_OUT = 1;
    // Menu data, synced as shorts: temperatures and kW x10, tritium x100, fractions x1000.
    public static final int STATE = 0, STATUS = 1, LOOP = 2, HEAT = 3, REMOVED = 4, GROSS = 5, NET = 6, REQUEST = 7, TRITIUM = 8,
            PROGRESS = 9, DEUTERIUM = 10, SHELL = 11, PRESSURE = 12, WEAR = 13, RADIATORS = 14, FLAGS = 15, DATA = 16;
    public static final int F_FOLLOW = 1, F_SHELL = 2;
    private static final FusionPhysics.Readout IDLE = new FusionPhysics.Readout(FusionPhysics.State.OFF, FusionPhysics.Status.IDLE,
            20, 0, 0, 0, 0, 0, 0, 0, 0, false);
    /** Hatch lower half to controller, per level; refreshed by every valid validation and checked on lookup. */
    private static final Map<ServerLevel, Map<BlockPos, BlockPos>> HATCHES = new WeakHashMap<>();
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final FusionPhysics physics = new FusionPhysics();
    private FusionPhysics.Readout readout = IDLE;
    private SfrMultiblock.Result shell = SfrMultiblock.Result.failed(SfrMultiblock.Problem.UNLOADED, null, BlockPos.ZERO);
    private BlockPos publishedPort;
    /** Channel pressure per second: the pump's 1/150 of nominal, the vent's 1/10. */
    private static final double PUMP_RATE = 1.0 / 150, VENT_RATE = 0.1;
    private double channelPressure = 1, wear;
    private int radiators;
    /** The vacuum stage and seal as of the last second; a sealed channel entering the stage writes its pressure into room gas. */
    private boolean vacuumStage, sealed;
    /** The First Wall and coils of the last valid shell: a disruption caused by breaking the shell still wears what is left. */
    private List<BlockPos> lastFirstWall = List.of(), lastCoils = List.of();
    /** Linked radiators this controller last marked HOT. */
    private final Set<BlockPos> hot = new HashSet<>();
    /** Ticks of quench frost left on the coils. */
    private int quenchTicks;
    /** The ghost outline: wrong cells marked with End Rod particles for GHOST_TICKS after a use on a broken shell. */
    private List<BlockPos> ghost = List.of();
    private int ghostTicks, comparator;
    /** What the client loops play; synced whenever it changes. Heat is whole kW and pressure hundredths, to bound packets. */
    public record Sound(FusionPhysics.State state, double heatKw, double pressure, boolean pumping, @Nullable BlockPos pump, boolean alarm) {}
    private static final Sound SILENT = new Sound(FusionPhysics.State.OFF, 0, 1, false, null, false);
    private Sound sound = SILENT;
    /** False until the first second after a load, which plays no one-shot cue. */
    private boolean stepped;

    public SfrControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.SFR_CONTROLLER.get(), p, s); }
    public NonNullList<ItemStack> items() { return items; }
    public FusionPhysics physics() { return physics; }
    public FusionPhysics.Readout readout() { return readout; }
    public SfrMultiblock.Result shell() { return shell; }
    public double channelPressure() { return channelPressure; }
    public void setChannelPressure(double value) { if (Double.isFinite(value)) { channelPressure = Math.clamp(value, 0, 1); setChanged(); } }
    /** Mean First Wall wear, 0 to 1, as of the last second. */
    public double wear() { return wear; }
    public int radiatorCount() { return radiators; }
    public Sound sound() { return sound; }

    public static Optional<SfrControllerBlockEntity> forHatch(ServerLevel l, BlockPos hatchLower) {
        var at = HATCHES.getOrDefault(l, Map.of()).get(hatchLower);
        if (at != null && l.isLoaded(at) && l.getBlockEntity(at) instanceof SfrControllerBlockEntity c && c.shell.valid() && hatchLower.equals(c.shell.hatch()))
            return Optional.of(c);
        return Optional.empty();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel l)) return;
        if (l.getGameTime() % 20 == 0) second(l);
        if (quenchTicks > 0 && !lastCoils.isEmpty()) { quenchTicks--; frost(l); }
        if (ghostTicks > 0 && ghostTicks-- % 5 == 0)
            for (var p : ghost) l.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.15, 0.15, 0.15, 0);
    }

    /** One second of reactor time. Also public for GameTests, which drive it directly. */
    public void second(ServerLevel l) {
        var facing = getBlockState().getValue(SfrControllerBlock.FACING);
        var result = SfrMultiblock.validate(l, worldPosition, facing);
        if (result.problem() == SfrMultiblock.Problem.UNLOADED) return;
        shell = result;
        boolean valid = shell.valid();
        var port = valid && l.getBlockEntity(shell.powerPort()) instanceof SfrPowerPortBlockEntity p ? p : null;
        var fuel = valid && l.getBlockEntity(shell.fuelPort()) instanceof SfrFuelPortBlockEntity f ? f : null;
        var portAt = port == null ? null : shell.powerPort();
        if (publishedPort != null && !publishedPort.equals(portAt)) release(l);
        publishedPort = portAt;
        if (valid) { HATCHES.computeIfAbsent(l, k -> new HashMap<>()).put(shell.hatch(), worldPosition); lastFirstWall = shell.firstWall(); lastCoils = shell.coils(); }
        var sinks = new ArrayList<Double>();
        if (valid) for (var p : HighTemperatureRadiatorBlock.sinks(l, shell.coolantPorts())) ReactorRadiatorBlock.sink(l, p).ifPresent(sinks::add);
        radiators = sinks.size();
        wear = shell.firstWall().stream().mapToInt(p -> l.getBlockState(p).getOptionalValue(FirstWallBlock.WEAR).orElse(0)).average().orElse(0) / FirstWallBlock.MAX_WEAR;
        boolean powered = port != null && port.paidFullSecond();
        boolean hatchClosed = valid && !l.getBlockState(shell.hatch()).getValue(DoorBlock.OPEN);
        boolean pumping = valid && l.getBlockState(shell.vacuumPort()).getValue(SfrVacuumPortBlock.MODE) == SfrVacuumPortBlock.Mode.PUMP;
        updateChannel(l, valid && hatchClosed, pumping, port != null && port.demandWatts() > 0 && powered);
        double target = physics.loadFollow() && port != null
                ? PowerNetworks.loadFollowTargetWatts(l, shell.powerPort(), port.supplyWatts()) / 1000.0 : Double.NaN;
        loadTritium();
        var before = stepped ? readout : null;
        stepped = true;
        readout = physics.step(new FusionPhysics.Inputs(sinks, powered, channelPressure, hatchClosed, valid, roomVacuum(l),
                fuel == null ? 0 : fuel.deuterium(), pumping, wear, l.hasNeighborSignal(worldPosition), target));
        if (fuel != null) fuel.use(readout.deuteriumUsed());
        // Coolant circulates through every linked radiator, buried or not, as on the CFU-7: a hot loop boils off their frost.
        var nowHot = valid && physics.loopC() >= com.frozendawn.power.ReactorPhysics.RADIATOR_HOT_C
                ? new HashSet<>(HighTemperatureRadiatorBlock.linked(l, shell.coolantPorts())) : new HashSet<BlockPos>();
        for (var p : hot) if (!nowHot.contains(p) && l.isLoaded(p)) ReactorRadiatorBlock.publish(l, p, false);
        for (var p : nowHot) ReactorRadiatorBlock.publish(l, p, true);
        hot.clear(); hot.addAll(nowHot);
        if (readout.disrupted()) {
            wearFirstWall(l);
            if (readout.status() == FusionPhysics.Status.MAGNET_QUENCH) quenchTicks = QUENCH_TICKS;
        }
        if (port != null) {
            boolean burning = physics.state() == FusionPhysics.State.BURNING;
            double requestKw = readout.requestKw() + (!burning && pumping && channelPressure > 0 ? FusionPhysics.PUMP_KW : 0);
            port.publish((int) Math.round(requestKw * 1000), burning ? (int) Math.round(Math.max(0, readout.netKw()) * 1000) : 0);
            port.ready(physics.state() == FusionPhysics.State.READY);
        }
        playCues(l, before);
        boolean burning = physics.state() == FusionPhysics.State.BURNING;
        var next = new Sound(physics.state(), Math.round(readout.heatKw()), Math.round(channelPressure * 100) / 100.0,
                pumping && powered && (burning || channelPressure > 0), valid ? shell.vacuumPort() : null, SfrAudioCues.alarm(readout, pumping));
        if (!next.equals(sound)) { sound = next; l.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
        int signal = comparator();
        if (signal != comparator) { comparator = signal; l.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); }
        setChanged();
    }

    /** Loop temperature against the disruption limit, 0-15. */
    public int comparator() { return Math.clamp(Math.round(physics.loopC() / FusionPhysics.DISRUPT_C * 15), 0, 15); }
    /** Marks up to 32 wrong cells for 100 ticks; returns the first, or empty when every cell is right but a port is missing. */
    public java.util.Optional<SfrMultiblock.Wrong> showGhost(ServerLevel l) {
        var wrong = SfrMultiblock.wrongCells(l, worldPosition, getBlockState().getValue(SfrControllerBlock.FACING), GHOST_CELLS);
        ghost = wrong.stream().map(SfrMultiblock.Wrong::pos).toList(); ghostTicks = GHOST_TICKS;
        return wrong.stream().findFirst();
    }

    private static final int QUENCH_TICKS = 100, GHOST_TICKS = 100, GHOST_CELLS = 32;
    private void playCues(ServerLevel l, @Nullable FusionPhysics.Readout before) {
        for (var cue : SfrAudioCues.cues(before, readout, physics.refusedStart())) {
            var event = switch (cue) {
                case READY -> ModSounds.SFR_READY; case REFUSE -> ModSounds.SFR_REFUSE;
                case DISRUPTION -> ModSounds.SFR_DISRUPTION; case QUENCH -> ModSounds.SFR_QUENCH;
            };
            float volume = switch (cue) { case READY, REFUSE -> 1f; case DISRUPTION -> 2f; case QUENCH -> 2.5f; };
            l.playSound(null, worldPosition, event.get(), SoundSource.BLOCKS, volume, 1f);
        }
    }
    /** Each disruption adds one WEAR to every First Wall still standing. */
    private void wearFirstWall(ServerLevel l) {
        for (var pos : lastFirstWall) {
            var state = l.getBlockState(pos);
            if (state.getBlock() instanceof FirstWallBlock && state.getValue(FirstWallBlock.WEAR) < FirstWallBlock.MAX_WEAR)
                l.setBlock(pos, state.setValue(FirstWallBlock.WEAR, state.getValue(FirstWallBlock.WEAR) + 1), Block.UPDATE_ALL);
        }
    }
    /** Snowflakes off one face of a random coil: the magnets warming through their superconducting limit. */
    private void frost(ServerLevel l) {
        var random = l.getRandom(); var coil = lastCoils.get(random.nextInt(lastCoils.size()));
        var face = Direction.getRandom(random); var c = coil.getCenter();
        l.sendParticles(ParticleTypes.SNOWFLAKE, c.x + face.getStepX() * 0.55, c.y + face.getStepY() * 0.55, c.z + face.getStepZ() * 0.55,
                2, face.getStepX() == 0 ? 0.3 : 0, face.getStepY() == 0 ? 0.3 : 0, face.getStepZ() == 0 ? 0.3 : 0, 0.02);
    }

    /** Outside the vacuum stage the channel exhausts to atmosphere at 1.0. During it, the pump moves gas into the room on
     * the Vacuum Port's outer face and the vent equalizes the two rooms; an unsealed channel takes the outside pressure. */
    private void updateChannel(ServerLevel l, boolean nowSealed, boolean pumping, boolean pumpPaid) {
        boolean vacuum = CombustionAtmosphere.isVacuum(l), wasSealedVacuum = vacuumStage && sealed, wasSealed = sealed;
        vacuumStage = vacuum; sealed = nowSealed;
        if (!nowSealed) { channelPressure = outsidePressure(l); return; }
        var gas = AirlockSavedState.get(l); var channel = shell.channel();
        int capacity = channel.size() * AirlockCycle.UNITS_PER_CELL;
        if (vacuum && !wasSealedVacuum) { gas.setRoomGas(l, channel, (int) Math.round(channelPressure * capacity)); return; }
        if (!vacuum) {
            if (wasSealedVacuum && wasSealed) return;
            if (pumping) { if (pumpPaid) channelPressure = Math.max(0, channelPressure - PUMP_RATE); }
            else channelPressure = toward(channelPressure, 1, VENT_RATE);
            return;
        }
        int held = gas.roomGas(l, channel);
        var room = outerRoom(l);
        if (pumping && pumpPaid) {
            int moved = Math.min(held, (int) Math.round(capacity * PUMP_RATE));
            held -= moved; gas.setRoomGas(l, channel, held);
            // receiveRoomAir caps each offer; keep offering until the room refuses. The rest is lost.
            for (int offered = moved, got; offered > 0 && !room.isEmpty() && (got = gas.receiveRoomAir(l, room, offered)) > 0; ) offered -= got;
        } else if (!pumping) {
            int roomHeld = room.isEmpty() ? 0 : gas.roomGas(l, room), roomCapacity = room.size() * AirlockCycle.UNITS_PER_CELL;
            long target = Math.round((double) (held + roomHeld) * capacity / (capacity + roomCapacity));
            int limit = (int) Math.round(capacity * VENT_RATE), step = (int) Math.clamp(target - held, -limit, limit);
            if (step != 0) { held += step; gas.setRoomGas(l, channel, held); if (!room.isEmpty()) gas.setRoomGas(l, room, roomHeld - step); }
        }
        channelPressure = held / (double) capacity;
    }
    private static double toward(double value, double target, double step) { return Math.abs(target - value) <= step + 1e-9 ? target : value + Math.copySign(step, target - value); }
    /** 1.0 outside the vacuum stage; during it, the gas fraction of the sealed room on the Vacuum Port's outer face, 0 if there is none. */
    public double outsidePressure(ServerLevel l) {
        if (!CombustionAtmosphere.isVacuum(l)) return 1;
        var room = outerRoom(l);
        return room.isEmpty() ? 0 : AirlockSavedState.get(l).roomGas(l, room) / (double) (room.size() * AirlockCycle.UNITS_PER_CELL);
    }
    /** Air cells of the sealed room beyond the Vacuum Port, away from the torus center; empty when open or unknown. */
    private Set<BlockPos> outerRoom(ServerLevel l) {
        if (!shell.valid()) return Set.of();
        var port = shell.vacuumPort(); var center = shell.center();
        var out = Direction.getNearest(port.getX() - center.getX(), 0, port.getZ() - center.getZ());
        var room = RoomAtmosphere.inspect(l, port.relative(out));
        return room.seal() == RoomAtmosphere.Seal.SEALED && !room.scanning() ? room.cells() : Set.of();
    }

    /** The controller's own room, in front of it, holds no air. */
    private boolean roomVacuum(ServerLevel l) {
        return CombustionAtmosphere.isVacuum(l) && !RoomAtmosphere.hasAir(l, worldPosition.relative(getBlockState().getValue(SfrControllerBlock.FACING)));
    }
    /** Each Tritium Canister in the input slot loads one whole unit while the tank has room for it. */
    private void loadTritium() {
        var in = items.get(TRITIUM_IN);
        while (in.is(ModItems.TRITIUM_CANISTER.get()) && physics.tritium() <= FusionPhysics.TRITIUM_TANK - 1) { physics.addTritium(1); in.shrink(1); }
    }
    /** Moves one whole unit into a Tritium Canister in the output slot. */
    public boolean extract() {
        var out = items.get(TRITIUM_OUT);
        boolean room = out.isEmpty() || out.is(ModItems.TRITIUM_CANISTER.get()) && out.getCount() < out.getMaxStackSize();
        if (!room || !physics.extractTritium()) return false;
        if (out.isEmpty()) items.set(TRITIUM_OUT, new ItemStack(ModItems.TRITIUM_CANISTER.get())); else out.grow(1);
        setChanged(); return true;
    }
    public void start() { physics.start(); setChanged(); }
    public void stop() { physics.stop(); setChanged(); }
    public void toggleLoadFollow() { physics.setLoadFollow(!physics.loadFollow()); setChanged(); }

    private void release(ServerLevel l) {
        if (l.isLoaded(publishedPort) && l.getBlockEntity(publishedPort) instanceof SfrPowerPortBlockEntity old) { old.publish(0, 0); old.ready(false); }
        publishedPort = null;
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel l) {
            if (publishedPort != null) release(l);
            for (var p : hot) if (l.isLoaded(p)) ReactorRadiatorBlock.publish(l, p, false);
        }
        super.setRemoved();
    }

    public ContainerData data() {
        return new ContainerData() {
            @Override public int get(int i) {
                var r = readout;
                return switch (i) {
                    case STATE -> physics.state().ordinal();
                    case STATUS -> physics.status().ordinal();
                    case LOOP -> clampShort(physics.loopC() * 10);
                    case HEAT -> clampShort(r.heatKw() * 10);
                    case REMOVED -> clampShort(r.removedKw() * 10);
                    case GROSS -> clampShort(r.grossKw() * 10);
                    case NET -> clampShort(r.netKw() * 10);
                    case REQUEST -> clampShort(r.requestKw() * 10);
                    case TRITIUM -> clampShort(physics.tritium() * 100);
                    case PROGRESS -> (int) Math.round(r.progress() * 1000);
                    case DEUTERIUM -> level != null && shell.valid() && level.getBlockEntity(shell.fuelPort()) instanceof SfrFuelPortBlockEntity f ? f.deuterium() : 0;
                    case SHELL -> shell.problem().ordinal();
                    case PRESSURE -> (int) Math.round(channelPressure * 1000);
                    case WEAR -> (int) Math.round(wear * 1000);
                    case RADIATORS -> radiators;
                    case FLAGS -> (physics.loadFollow() ? F_FOLLOW : 0) | (shell.valid() ? F_SHELL : 0);
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA; }
        };
    }
    private static int clampShort(double v) { return (int) Math.clamp(Math.round(v), Short.MIN_VALUE + 1, Short.MAX_VALUE); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r); ContainerHelper.saveAllItems(tag, items, r);
        var fusion = new CompoundTag(); physics.save(fusion); tag.put("Fusion", fusion);
        tag.putDouble("ChannelPressure", channelPressure);
        tag.putBoolean("VacuumStage", vacuumStage); tag.putBoolean("Sealed", sealed);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); items.clear(); ContainerHelper.loadAllItems(tag, items, r);
        physics.load(tag.getCompound("Fusion"));
        double pressure = tag.contains("ChannelPressure") ? tag.getDouble("ChannelPressure") : 1;
        channelPressure = Double.isFinite(pressure) ? Math.clamp(pressure, 0, 1) : 1;
        vacuumStage = tag.getBoolean("VacuumStage"); sealed = tag.getBoolean("Sealed");
        readout = IDLE;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        var tag = new CompoundTag();
        tag.putInt("State", sound.state().ordinal()); tag.putDouble("Heat", sound.heatKw()); tag.putDouble("Pressure", sound.pressure());
        tag.putBoolean("Pumping", sound.pumping()); tag.putBoolean("Alarm", sound.alarm());
        if (sound.pump() != null) tag.putLong("Pump", sound.pump().asLong());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider r) {
        var states = FusionPhysics.State.values();
        sound = new Sound(states[Math.clamp(tag.getInt("State"), 0, states.length - 1)], tag.getDouble("Heat"), tag.getDouble("Pressure"),
                tag.getBoolean("Pumping"), tag.contains("Pump") ? BlockPos.of(tag.getLong("Pump")) : null, tag.getBoolean("Alarm"));
    }
    @Override public void onDataPacket(Connection c, ClientboundBlockEntityDataPacket p, HolderLookup.Provider r) { handleUpdateTag(p.getTag(), r); }
    public boolean stillValid(Player p) { return !p.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.frozendawn.sfr_controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new SfrControllerMenu(id, inv, this); }
}
