package com.frozendawn.mixin;
import com.frozendawn.world.CombustionAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FireBlock.class)
public abstract class VacuumFireMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void frozendawn$extinguishBeforeSpread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!CombustionAtmosphere.canBurnAt(level, pos)) { level.removeBlock(pos, false); ci.cancel(); }
    }
}
