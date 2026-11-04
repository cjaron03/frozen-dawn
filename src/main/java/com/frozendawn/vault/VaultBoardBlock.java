package com.frozendawn.vault;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** One glyph of the ISS-4 board on the Watch Floor's west wall. Argus sets its lamp. */
public class VaultBoardBlock extends Block {
    public enum Lamp implements StringRepresentable {
        DARK, LIT, PULLED;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, ArgusFight.GLYPHS - 1);

    public VaultBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAMP, Lamp.DARK).setValue(GLYPH, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LAMP, GLYPH); }
    public static int light(BlockState state) { return switch (state.getValue(LAMP)) { case DARK -> 0; case LIT -> 6; case PULLED -> 3; }; }
}
