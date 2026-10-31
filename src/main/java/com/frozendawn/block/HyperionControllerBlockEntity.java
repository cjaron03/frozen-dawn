package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.EnergyStore;
import com.frozendawn.power.HyperionPhysics;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.TemperatureManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The SMS-1 Hyperion's brain and its only store: the charge is a long here, never in the cells. Once formed it re-checks
 * only its own saved box, so nothing built beside it can break it. Ports reach it as one EnergyStore, rate-capped per
 * one-second interval at PORT_WATTS per port in each direction. */
public final class HyperionControllerBlockEntity extends BlockEntity implements EnergyStore, MenuProvider {
    /** What the client shows: synced once per second when anything in it changed. */
    public record Snapshot(HyperionMultiblock.Problem problem, HyperionPhysics.Stage stage, long stored, long capacity, long flowIn, long flowOut,
                           int ports, long rateWatts, double cellC, double surroundingsC, long coolerWatts, double secondsToQuench, long lastQuenchTick) {
        public static final Snapshot EMPTY = new Snapshot(HyperionMultiblock.Problem.UNLOADED, HyperionPhysics.Stage.COLD, 0, 0, 0, 0, 0, 0,
                HyperionPhysics.OPERATING_C, 0, 0, Double.POSITIVE_INFINITY, Long.MIN_VALUE);
    }
    /** Per level, each formed controller's saved box, so a changed part finds its controller without a scan. */
    private static final Map<Level, Map<BlockPos, BlockPos[]>> BOXES = new WeakHashMap<>();
    private static final Map<Level, Integer> QUENCHES = new WeakHashMap<>();
    private long stored;
    private BlockPos min, max;
    private HyperionMultiblock.Result shell = HyperionMultiblock.Result.failed(HyperionMultiblock.Problem.UNLOADED, null, null, null);
    private boolean dirty = true, registered;
    private long lastDiscover = Long.MIN_VALUE / 2, interval = Long.MIN_VALUE, drawnThisInterval, chargedThisInterval;
    private int lastCharged, lastDrawn;
    private Double surroundingsOverride;
    private final HyperionPhysics physics = new HyperionPhysics();
    private HyperionPhysics.Readout readout;
    private long cooldownGranted, lastQuenchTick = Long.MIN_VALUE;
    private int hotNeighbors, secondsSinceScan;
    private double lastSurroundingsC;
    private long machineDrawn;
    private Snapshot snapshot = Snapshot.EMPTY, sent;
    /** Set by a quench, cleared once the cells are cold again: until then they show scars. */
    private boolean scarred;
    private SuperconductingCellBlock.Look shownLook;
    private Boolean shownAlert;
    private List<BlockPos> vents = List.of();

    public HyperionControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HYPERION_CONTROLLER.get(), p, s); }

    /** A part changed: controllers near it re-check, and a charged battery that lost a part of its own box quenches now. */
    public static void partChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel l)) return;
        var hit = new ArrayList<HyperionControllerBlockEntity>();
        for (var e : BOXES.getOrDefault(level, Map.of()).entrySet()) {
            var box = e.getValue();
            if (!inside(pos, box[0].offset(-1, -1, -1), box[1].offset(1, 1, 1)) || !level.isLoaded(e.getKey())
                    || !(level.getBlockEntity(e.getKey()) instanceof HyperionControllerBlockEntity c)) continue;
            c.dirty = true;
            if (inside(pos, box[0], box[1])) hit.add(c);
        }
        for (var c : hit) if (!(l.getBlockState(pos).getBlock() instanceof HyperionPartBlock) || !c.stillValid(l)) c.broken(l);
    }
    private boolean stillValid(ServerLevel l) { return min != null && HyperionMultiblock.validate(l, min, max).valid(); }
    /** The box lost a part: vent any charge at once, then come apart. */
    void broken(ServerLevel l) {
        if (stored > 0) { quench(); if (min != null) show(l, SuperconductingCellBlock.Look.SCARRED, false); }
        if (min != null) unform(l);
    }
    public static int quenchCount(ServerLevel l) { return QUENCHES.getOrDefault(l, 0); }
    static boolean inside(BlockPos p, BlockPos lo, BlockPos hi) {
        return p.getX() >= lo.getX() && p.getX() <= hi.getX() && p.getY() >= lo.getY() && p.getY() <= hi.getY() && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ();
    }

    /** One second: re-check the saved box (or look for one), then step. Nothing runs while any part is unloaded. */
    public void second(ServerLevel l) {
        if (min != null) {
            shell = HyperionMultiblock.validate(l, min, max);
            if (shell.problem() == HyperionMultiblock.Problem.UNLOADED) return;
            if (!shell.valid()) { broken(l); return; }
            if (!registered) register(l);
        } else if (dirty || l.getGameTime() - lastDiscover >= 200) {
            dirty = false; lastDiscover = l.getGameTime();
            shell = HyperionMultiblock.discover(l, worldPosition);
            if (!shell.valid()) return;
            min = shell.min(); max = shell.max(); register(l);
            lastSurroundingsC = surroundingsC(l); physics.reset(lastSurroundingsC); readout = null; setChanged();
            show(l); sync(l);
            return;
        } else { sync(l); return; }
        step(l);
        sync(l);
    }
    public Snapshot snapshot() { return snapshot; }
    private Snapshot current() {
        var r = readout();
        return new Snapshot(shell.problem(), r.stage(), stored, capacity(), lastCharged, lastDrawn + machineDrawn, portCount(), rateWatts(), r.cellC(),
                lastSurroundingsC, r.coolerJoules(), r.secondsToQuench(), lastQuenchTick);
    }
    private void sync(ServerLevel l) {
        snapshot = current(); machineDrawn = 0;
        if (!snapshot.equals(sent)) { sent = snapshot; l.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }
    }
    private void step(ServerLevel l) {
        if (secondsSinceScan++ % 5 == 0) hotNeighbors = hotNeighbors(l);
        lastSurroundingsC = surroundingsC(l);
        readout = physics.step(new HyperionPhysics.Inputs(shell.cells(), shell.coolers().size(), lastSurroundingsC, hotNeighbors, cooldownGranted), stored);
        cooldownGranted = 0;
        stored -= Math.min(stored, readout.coolerJoules());
        if (readout.quenched()) quench();
        if (readout.stage() == HyperionPhysics.Stage.COLD) scarred = false;
        show(l);
        setChanged();
    }
    /** How the cells look for the current stage: frost, heat stress, white-hot cracks or scars. Never the charge. */
    SuperconductingCellBlock.Look look() {
        return switch (physics.stage()) {
            case COLD -> lastSurroundingsC < 0 ? SuperconductingCellBlock.Look.FROSTED : SuperconductingCellBlock.Look.PLAIN;
            case CHIRP, WARNING -> SuperconductingCellBlock.Look.STRESSED;
            case FINAL -> SuperconductingCellBlock.Look.CRITICAL;
            case WARM -> scarred ? SuperconductingCellBlock.Look.SCARRED : SuperconductingCellBlock.Look.PLAIN;
        };
    }
    private void show(ServerLevel l) {
        var stage = physics.stage();
        show(l, look(), stage == HyperionPhysics.Stage.CHIRP || stage == HyperionPhysics.Stage.WARNING || stage == HyperionPhysics.Stage.FINAL);
    }
    /** Repaint only on a change: cells, then the alert lamps on this Controller and its Ports. Client updates only. */
    private void show(ServerLevel l, SuperconductingCellBlock.Look look, boolean alert) {
        if (look != shownLook) {
            shownLook = look;
            for (var p : BlockPos.betweenClosed(min, max)) {
                var s = l.getBlockState(p);
                if (s.getBlock() instanceof SuperconductingCellBlock && s.getValue(SuperconductingCellBlock.LOOK) != look)
                    l.setBlock(p, s.setValue(SuperconductingCellBlock.LOOK, look), Block.UPDATE_CLIENTS);
            }
        }
        if (shownAlert == null || shownAlert != alert) {
            shownAlert = alert;
            var lamps = new ArrayList<BlockPos>(shell.ports()); lamps.add(worldPosition);
            for (var p : lamps) {
                var s = l.getBlockState(p);
                if (s.hasProperty(HyperionControllerBlock.ALERT) && s.getValue(HyperionControllerBlock.ALERT) != alert)
                    l.setBlock(p, s.setValue(HyperionControllerBlock.ALERT, alert), Block.UPDATE_CLIENTS);
            }
        }
    }
    /** Vent the charge. The cells are already warm (heat) or the box is coming apart (a break). */
    public void quench() {
        stored = 0; scarred = true; lastQuenchTick = level == null ? 0 : level.getGameTime();
        if (level instanceof ServerLevel l) { QUENCHES.merge(l, 1, Integer::sum); HyperionVentJets.start(l, vents); }
        setChanged();
    }
    /** The warmest of the six spots just outside the face centers, from the mod's own loaded-only temperature. */
    public double surroundingsC(ServerLevel l) {
        if (surroundingsOverride != null) return surroundingsOverride;
        if (min == null) return TemperatureManager.getLoadedTemperatureAt(l, worldPosition, day(l), total(l));
        int cx = (min.getX() + max.getX()) / 2, cy = (min.getY() + max.getY()) / 2, cz = (min.getZ() + max.getZ()) / 2;
        BlockPos[] spots = {new BlockPos(min.getX() - 1, cy, cz), new BlockPos(max.getX() + 1, cy, cz), new BlockPos(cx, min.getY() - 1, cz),
                new BlockPos(cx, max.getY() + 1, cz), new BlockPos(cx, cy, min.getZ() - 1), new BlockPos(cx, cy, max.getZ() + 1)};
        double warmest = Double.NEGATIVE_INFINITY;
        for (var p : spots) if (l.isLoaded(p)) warmest = Math.max(warmest, TemperatureManager.getLoadedTemperatureAt(l, p, day(l), total(l)));
        return Double.isFinite(warmest) ? warmest : TemperatureManager.getLoadedTemperatureAt(l, worldPosition, day(l), total(l));
    }
    private static int day(ServerLevel l) { return ApocalypseState.get(l.getServer()).getCurrentDay(); }
    private static int total(ServerLevel l) { return ApocalypseState.get(l.getServer()).getTotalDays(); }
    /** Hot things touching a face (not edges or corners): hot radiators, fire-like blocks, running CFU-7 controllers. */
    public int hotNeighbors(ServerLevel l) {
        if (min == null) return 0;
        int count = 0;
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= max.getY(); y++) for (int z = min.getZ(); z <= max.getZ(); z++) {
            boolean ex = x == min.getX() || x == max.getX(), ey = y == min.getY() || y == max.getY(), ez = z == min.getZ() || z == max.getZ();
            if ((ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0) != 1) continue;
            var p = new BlockPos(ex ? (x == min.getX() ? x - 1 : x + 1) : x, ey ? (y == min.getY() ? y - 1 : y + 1) : y, ez ? (z == min.getZ() ? z - 1 : z + 1) : z);
            if (l.isLoaded(p) && hot(l, p)) count++;
        }
        return count;
    }
    private static boolean hot(ServerLevel l, BlockPos p) {
        var state = l.getBlockState(p);
        if (state.getBlock() instanceof ReactorRadiatorBlock && state.getValue(ReactorRadiatorBlock.HOT)) return true;
        if (TemperatureManager.getAmbientSignatureHeat(state) > 0) return true;
        return l.getBlockEntity(p) instanceof ReactorControllerBlockEntity reactor && reactor.powerWatts() > 0;
    }
    private void register(ServerLevel l) {
        registered = true; vents = shell.vents(); shownLook = null; shownAlert = null;
        BOXES.computeIfAbsent(l, k -> new HashMap<>()).put(worldPosition, new BlockPos[]{min, max});
        for (var port : shell.ports()) if (l.getBlockEntity(port) instanceof HyperionPortBlockEntity p) p.setController(worldPosition);
        PowerNetworks.hyperionFormed(l, worldPosition, shell.ports());
    }
    private void unform(ServerLevel l) {
        min = max = null; registered = false; dirty = true;
        var boxes = BOXES.get(l); if (boxes != null) boxes.remove(worldPosition);
        PowerNetworks.hyperionUnformed(l, worldPosition);
        setChanged();
    }

    public HyperionMultiblock.Result shell() { return shell; }
    public long stored() { return stored; }
    public void setStoredForTest(long joules) { stored = Math.max(0, joules); setChanged(); }
    public void setSurroundingsForTest(Double celsius) { surroundingsOverride = celsius; }
    public int portCount() { return shell.valid() ? shell.ports().size() : 0; }
    public int rateWatts() { return portCount() * HyperionPhysics.PORT_WATTS; }
    public long capacity() { return shell.valid() ? HyperionPhysics.capacity(shell.cells()) : 0; }
    public long chargeHeadroomWatts() {
        if (!acceptsCharge()) return 0;
        return warm() ? HyperionPhysics.cooldownWatts(shell.cells()) : Math.min(rateWatts(), Math.max(0, capacity() - stored));
    }
    public HyperionPhysics physics() { return physics; }
    public HyperionPhysics.Readout readout() {
        return readout != null ? readout : new HyperionPhysics.Readout(physics.stage(), physics.cellC(), 0, Double.POSITIVE_INFINITY, false, false, 0);
    }
    public long lastQuenchTick() { return lastQuenchTick; }
    public String problemKey() { return "hyperion.frozendawn.problem." + shell.problem().name().toLowerCase(java.util.Locale.ROOT); }
    private boolean warm() { return physics.stage() == HyperionPhysics.Stage.WARM; }

    private void interval() {
        long now = level == null ? 0 : level.getGameTime() / 20;
        if (now != interval) { interval = now; drawnThisInterval = chargedThisInterval = 0; }
    }
    @Override public BlockPos storeKey() { return worldPosition; }
    @Override public long storedEnergy() { return stored; }
    @Override public long energyCapacity() { return capacity(); }
    @Override public long availableEnergy() { interval(); return shell.valid() ? Math.max(0, Math.min(stored, rateWatts() - drawnThisInterval)) : 0; }
    @Override public long drawEnergy(long requested) {
        long amount = Math.max(0, Math.min(requested, availableEnergy()));
        if (amount > 0) { stored -= amount; drawnThisInterval += amount; setChanged(); }
        return amount;
    }
    @Override public long chargeEnergy(long offered) {
        interval();
        if (!acceptsCharge()) return 0;
        if (warm()) {
            // Warm cells hold nothing: what the grid sends runs the cooler down to temperature instead.
            long grant = Math.max(0, Math.min(offered, HyperionPhysics.cooldownWatts(shell.cells()) - cooldownGranted));
            cooldownGranted += grant; return grant;
        }
        long amount = Math.max(0, Math.min(offered, Math.min(capacity() - stored, rateWatts() - chargedThisInterval)));
        if (amount > 0) { stored += amount; chargedThisInterval += amount; setChanged(); }
        return amount;
    }
    @Override public boolean acceptsCharge() { return shell.valid(); }
    @Override public void publish(int charged, int drawn) { lastCharged = charged; lastDrawn = drawn; }
    @Override public void publishMachineDraw(int joules) { machineDrawn += Math.max(0, joules); }
    @Override public void publishHeaterDraw(int joules) { machineDrawn += Math.max(0, joules); }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        var t = new CompoundTag(); var s = snapshot;
        t.putInt("Problem", s.problem().ordinal()); t.putInt("Stage", s.stage().ordinal()); t.putLong("Stored", s.stored()); t.putLong("Capacity", s.capacity());
        t.putLong("In", s.flowIn()); t.putLong("Out", s.flowOut()); t.putInt("Ports", s.ports()); t.putLong("Rate", s.rateWatts());
        t.putDouble("CellC", s.cellC()); t.putDouble("AroundC", s.surroundingsC()); t.putLong("Cooler", s.coolerWatts());
        t.putDouble("ToQuench", s.secondsToQuench()); t.putLong("LastQuench", s.lastQuenchTick());
        return t;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag t, HolderLookup.Provider r) {
        var problems = HyperionMultiblock.Problem.values(); var stages = HyperionPhysics.Stage.values();
        snapshot = new Snapshot(problems[Math.clamp(t.getInt("Problem"), 0, problems.length - 1)], stages[Math.clamp(t.getInt("Stage"), 0, stages.length - 1)],
                t.getLong("Stored"), t.getLong("Capacity"), t.getLong("In"), t.getLong("Out"), t.getInt("Ports"), t.getLong("Rate"),
                t.getDouble("CellC"), t.getDouble("AroundC"), t.getLong("Cooler"), t.getDouble("ToQuench"), t.getLong("LastQuench"));
    }
    @Override public void onDataPacket(Connection c, ClientboundBlockEntityDataPacket p, HolderLookup.Provider r) { handleUpdateTag(p.getTag(), r); }
    @Override public Component getDisplayName() { return Component.translatable("block.frozendawn.hyperion_controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new HyperionControllerMenu(id, worldPosition); }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel l && registered) {
            var boxes = BOXES.get(l); if (boxes != null) boxes.remove(worldPosition);
            PowerNetworks.hyperionUnformed(l, worldPosition); registered = false;
        }
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t, r);
        t.putLong("Stored", stored);
        if (min != null) { t.put("Min", NbtUtils.writeBlockPos(min)); t.put("Max", NbtUtils.writeBlockPos(max)); }
        var p = new CompoundTag(); physics.save(p); t.put("Physics", p);
        t.putLong("LastQuench", lastQuenchTick);
        t.putBoolean("Scarred", scarred);
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t, r);
        stored = Math.max(0, t.getLong("Stored"));
        min = NbtUtils.readBlockPos(t, "Min").orElse(null); max = NbtUtils.readBlockPos(t, "Max").orElse(null);
        if (min == null || max == null) min = max = null;
        physics.load(t.getCompound("Physics"));
        lastQuenchTick = t.contains("LastQuench") ? t.getLong("LastQuench") : Long.MIN_VALUE;
        scarred = t.getBoolean("Scarred");
    }
}
