package com.frozendawn.world;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;

/** Independently owned room snapshots with standard Set membership and hash semantics. */
public final class RoomSets {
    private RoomSets() { }
    public static <E> ImmutableSet<E> freeze(Collection<? extends E> values) {
        return ImmutableSet.copyOf(values);
    }
}
