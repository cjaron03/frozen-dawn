package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.power.PoweredMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The AMR-1 Icarus network node. The controller publishes once per second what the reactor draws (the startup draw, or
 * the shield's deficit) and what it supplies (net output); the port pays the draw tick by tick like any powered machine.
 * Published values are transient: the controller republishes them every second. */
public final class IcarusPowerPortBlockEntity extends BlockEntity implements PoweredMachine {
    private int requestWatts, supplyWatts, ticks, paid;
    private boolean verdict = true;

    public IcarusPowerPortBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_POWER_PORT.get(), p, s); }

    public void publish(int requestWatts, int supplyWatts) {
        this.requestWatts = Math.max(0, requestWatts); this.supplyWatts = Math.max(0, supplyWatts);
    }
    public int supplyWatts() { return supplyWatts; }

    /** True when nothing was asked, or every tick since the last reading was paid. One unpaid tick is allowed:
     * a new request can tick once before the next allocation grants it. With no ticks since the last reading
     * the previous verdict stands. Reading resets the count. */
    public boolean paidFullSecond() {
        if (requestWatts <= 0) verdict = true;
        else if (ticks > 0) verdict = paid >= ticks - 1;
        ticks = 0; paid = 0;
        return verdict;
    }

    @Override public boolean wantsPower() { return requestWatts > 0; }
    @Override public int demandWatts() { return requestWatts; }

    public void serverTick() {
        if (requestWatts <= 0 || !(level instanceof ServerLevel l)) return;
        ticks++;
        if (PowerNetworks.consumeMachineTick(l, worldPosition)) paid++;
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel l) PowerNetworks.machineLoaded(l, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel l) PowerNetworks.machineRemoved(l, worldPosition); super.setRemoved(); }
}
