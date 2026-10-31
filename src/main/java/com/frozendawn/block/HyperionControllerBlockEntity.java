package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.EnergyStore;
import com.frozendawn.power.HyperionPhysics;
import com.frozendawn.power.PowerNetworks;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The SMS-1 Hyperion's brain and its only store: the charge is a long here, never in the cells. Once formed it re-checks
 * only its own saved box, so nothing built beside it can break it. Ports reach it as one EnergyStore, rate-capped per
 * one-second interval at PORT_WATTS per port in each direction. */
public final class HyperionControllerBlockEntity extends BlockEntity implements EnergyStore {
    /** Per level, each formed controller's saved box, so a changed part finds its controller without a scan. */
    private static final Map<Level, Map<BlockPos, BlockPos[]>> BOXES = new WeakHashMap<>();
    private long stored;
    private BlockPos min, max;
    private HyperionMultiblock.Result shell = HyperionMultiblock.Result.failed(HyperionMultiblock.Problem.UNLOADED, null, null, null);
    private boolean dirty = true, registered;
    private long lastDiscover = Long.MIN_VALUE / 2, interval = Long.MIN_VALUE, drawnThisInterval, chargedThisInterval;
    private int lastCharged, lastDrawn;
    private Double surroundingsOverride;
    private final HyperionPhysics physics = new HyperionPhysics();

    public HyperionControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HYPERION_CONTROLLER.get(), p, s); }

    public static void partChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        for (var e : BOXES.getOrDefault(level, Map.of()).entrySet()) {
            var box = e.getValue();
            if (inside(pos, box[0].offset(-1, -1, -1), box[1].offset(1, 1, 1)) && level.isLoaded(e.getKey())
                    && level.getBlockEntity(e.getKey()) instanceof HyperionControllerBlockEntity c) c.dirty = true;
        }
    }
    static boolean inside(BlockPos p, BlockPos lo, BlockPos hi) {
        return p.getX() >= lo.getX() && p.getX() <= hi.getX() && p.getY() >= lo.getY() && p.getY() <= hi.getY() && p.getZ() >= lo.getZ() && p.getZ() <= hi.getZ();
    }

    /** One second: re-check the saved box (or look for one), then step. Nothing runs while any part is unloaded. */
    public void second(ServerLevel l) {
        if (min != null) {
            shell = HyperionMultiblock.validate(l, min, max);
            if (shell.problem() == HyperionMultiblock.Problem.UNLOADED) return;
            if (!shell.valid()) { unform(l); return; }
            if (!registered) register(l);
        } else if (dirty || l.getGameTime() - lastDiscover >= 200) {
            dirty = false; lastDiscover = l.getGameTime();
            shell = HyperionMultiblock.discover(l, worldPosition);
            if (shell.valid()) { min = shell.min(); max = shell.max(); register(l); setChanged(); }
        }
    }
    private void register(ServerLevel l) {
        registered = true;
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
    public long chargeHeadroomWatts() { return acceptsCharge() ? Math.min(rateWatts(), Math.max(0, capacity() - stored)) : 0; }
    public HyperionPhysics physics() { return physics; }

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
        long amount = Math.max(0, Math.min(offered, Math.min(capacity() - stored, rateWatts() - chargedThisInterval)));
        if (amount > 0) { stored += amount; chargedThisInterval += amount; setChanged(); }
        return amount;
    }
    @Override public boolean acceptsCharge() { return shell.valid(); }
    @Override public void publish(int charged, int drawn) { lastCharged = charged; lastDrawn = drawn; }
    @Override public void publishMachineDraw(int joules) {}
    @Override public void publishHeaterDraw(int joules) {}

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
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t, r);
        stored = Math.max(0, t.getLong("Stored"));
        min = NbtUtils.readBlockPos(t, "Min").orElse(null); max = NbtUtils.readBlockPos(t, "Max").orElse(null);
        if (min == null || max == null) min = max = null;
        physics.load(t.getCompound("Physics"));
    }
}
