package com.frozendawn.vault;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A block of the shell painted over: hazard band, EXO arrows, the flag, the board's name, the marker warning. Sealed like the rest of it. */
public final class VaultMarkBlock extends Block {
    public enum Mark implements StringRepresentable {
        HAZARD, EXO_RIGHT_0, EXO_RIGHT_1, EXO_RIGHT_2, EXO_LEFT_0, EXO_LEFT_1, EXO_LEFT_2, FLAG_0, FLAG_1, ARGUS_0, ARGUS_1, ARGUS_2,
        WARNING_0, WARNING_1, WARNING_2, WARNING_3, WARNING_4, WARNING_5, WARNING_6, WARNING_7, WARNING_8, WARNING_9, WARNING_10, WARNING_11, WARNING_12, WARNING_13, WARNING_14;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Mark> MARK = EnumProperty.create("mark", Mark.class);

    public VaultMarkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MARK, Mark.HAZARD));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MARK); }
}
