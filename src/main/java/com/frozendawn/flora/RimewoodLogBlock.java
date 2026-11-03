package com.frozendawn.flora;

import com.frozendawn.init.ModItems;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** A Rimewood log or wood block: an axe strips it and the sealed bark gives up one Rimewood Resin. */
public final class RimewoodLogBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> stripped;

    public RimewoodLogBlock(Properties properties, Supplier<? extends Block> stripped) {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability != ItemAbilities.AXE_STRIP) return super.getToolModifiedState(state, context, ability, simulate);
        if (!simulate && !context.getLevel().isClientSide())
            Block.popResourceFromFace(context.getLevel(), context.getClickedPos(), context.getClickedFace(), new ItemStack(ModItems.RIMEWOOD_RESIN.get()));
        return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
    }
}
