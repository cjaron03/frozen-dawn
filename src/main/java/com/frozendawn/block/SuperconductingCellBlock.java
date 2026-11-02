package com.frozendawn.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A superconducting storage cell; capacity scales with how many the silo holds. Its Controller sets how it looks:
 * a loose block until formed, seamless while formed, scorched after a quench until cold again. Never lit. */
public final class SuperconductingCellBlock extends HyperionPartBlock {
    public enum Look implements StringRepresentable {
        LOOSE, FORMED, SCORCHED;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);
    public SuperconductingCellBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LOOK, Look.LOOSE));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LOOK); }
}
