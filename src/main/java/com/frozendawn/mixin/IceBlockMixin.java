package com.frozendawn.mixin;

import com.frozendawn.data.ApocalypseState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(IceBlock.class)
public abstract class IceBlockMixin {

    @Inject(method = "playerDestroy", at = @At("TAIL"))
    private void frozendawn$preventLatePhaseIceFlood(Level level, Player player, BlockPos pos, BlockState state,
                                                     @Nullable BlockEntity te, ItemStack stack, CallbackInfo ci) {
        if (!frozendawn$shouldBreakDry(level)) return;
        if (level.getBlockState(pos).is(Blocks.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void frozendawn$preventLatePhaseIceMelt(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!frozendawn$shouldBreakDry(level)) return;
        // Late-phase ice is permanent terrain. Replacing vanilla melt output with
        // air punched random one-block holes through frozen lakes and oceans.
        ci.cancel();
    }

    private static boolean frozendawn$shouldBreakDry(Level level) {
        if (level.isClientSide() || level.dimension() != Level.OVERWORLD) return false;
        if (level.getServer() == null) return false;
        return ApocalypseState.get(level.getServer()).getPhase() >= 5;
    }
}
