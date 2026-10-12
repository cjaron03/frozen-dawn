package com.frozendawn.mixin;
import com.frozendawn.world.VacuumFlames;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class VacuumLevelChunkMixin {
    @ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockState frozendawn$normalizeFlame(BlockState requested, BlockPos pos, BlockState original, boolean moving) {
        var chunk = (LevelChunk)(Object)this;
        return chunk.getLevel() instanceof ServerLevel level
                ? com.frozendawn.airlock.AirlockManager.normalizeDoor(level,pos,VacuumFlames.normalize(level,pos,requested)) : requested;
    }
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void frozendawn$indexFlame(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> ci) {
        var chunk = (LevelChunk)(Object)this;
        if (ci.getReturnValue() != null && chunk.getLevel() instanceof ServerLevel level
                && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            var current = chunk.getBlockState(pos);
            com.frozendawn.world.RoomAtmosphere.blockChanged(level, pos, ci.getReturnValue(), current);
            VacuumFlames.blockChanged(level, pos, current);
            com.frozendawn.airlock.AirlockManager.blockChanged(level,pos,ci.getReturnValue(),current);
        }
    }
}
