package com.frozendawn.power;

import net.minecraft.core.BlockPos;

/** A dimension-local, undirected connection; order never changes its identity. */
public record PowerLink(BlockPos a, BlockPos b) implements Comparable<PowerLink> {
    public PowerLink {
        a = a.immutable(); b = b.immutable();
        if (a.asLong() > b.asLong()) { var swap = a; a = b; b = swap; }
        if (a.equals(b)) throw new IllegalArgumentException("Self link");
    }
    public boolean touches(BlockPos pos) { return a.equals(pos) || b.equals(pos); }
    @Override public int compareTo(PowerLink other) {
        int first = Long.compare(a.asLong(), other.a.asLong());
        return first == 0 ? Long.compare(b.asLong(), other.b.asLong()) : first;
    }
}
