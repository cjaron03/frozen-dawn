package com.frozendawn.mixin;
import com.frozendawn.world.CombustionAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class VacuumFurnaceMixin {
    @Shadow private int litTime;
    @Shadow private int litDuration;
    @Shadow private int cookingProgress;
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void frozendawn$stopCombustion(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (CombustionAtmosphere.canBurnAt(level, pos)) return;
        var fields = (VacuumFurnaceMixin)(Object)furnace;
        boolean dirty = fields.litTime > 0 || fields.cookingProgress > 0;
        fields.litTime = 0; fields.litDuration = 0;
        fields.cookingProgress = Math.max(0, fields.cookingProgress - 2);
        if (state.getValue(BlockStateProperties.LIT)) level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        if (dirty) furnace.setChanged();
        ci.cancel();
    }
}
