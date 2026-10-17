package com.frozendawn.block;

import net.minecraft.world.level.block.Block;

/** Shell face that carries coolant to radiators. Linked with the Conduit Binder, port to radiator only. */
public final class CoolantPortBlock extends Block {
    public static final int LINK_RANGE = 16;
    public CoolantPortBlock(Properties p) { super(p); }
}
