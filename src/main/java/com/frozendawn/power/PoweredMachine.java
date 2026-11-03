package com.frozendawn.power;

/** A processing load that requires its full allocation and pays per working tick. */
public interface PoweredMachine {
    boolean wantsPower();
    int demandWatts();
}
