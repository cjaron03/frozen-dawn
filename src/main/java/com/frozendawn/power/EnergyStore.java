package com.frozendawn.power;

import net.minecraft.core.BlockPos;

/** Anything the network charges from surplus and draws for shortfalls: a Battery Bank or Cell Dock, or a formed
 * Hyperion reached through any of its ports. Energy is long joules; one store answers once however many nodes reach it. */
public interface EnergyStore {
    /** Identity for de-duplication and the once-per-tick accounting guard. */
    BlockPos storeKey();
    long storedEnergy();
    long energyCapacity();
    /** What may still be drawn this interval; a rate-capped store reports less than it holds. */
    long availableEnergy();
    long chargeEnergy(long offered);
    long drawEnergy(long requested);
    boolean acceptsCharge();
    void publish(int charged, int drawn);
    void publishMachineDraw(int joules);
    void publishHeaterDraw(int joules);
}
