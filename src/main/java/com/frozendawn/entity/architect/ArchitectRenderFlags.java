package com.frozendawn.entity.architect;

public final class ArchitectRenderFlags {

    public static final int MINING = 1;
    public static final int QUEUED_SCAFFOLD = 1 << 1;
    public static final int RETREAT_RECOVERING = 1 << 2;

    private ArchitectRenderFlags() {
    }

    public static int set(int flags, int flag, boolean enabled) {
        return enabled ? (flags | flag) : (flags & ~flag);
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
