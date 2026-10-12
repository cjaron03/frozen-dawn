package com.frozendawn.mixin;
import com.frozendawn.world.CombustionAtmosphere;
import com.frozendawn.world.RoomOxygenManager;
import com.frozendawn.oxygen.OxygenControlledFurnace;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Unique;
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
public abstract class VacuumFurnaceMixin implements OxygenControlledFurnace {
    @Shadow private int litTime;
    @Shadow private int litDuration;
    @Shadow private int cookingProgress;
    @Unique private boolean frozendawn$oxygenStarved;
    @Unique private int frozendawn$pausedBurnTime, frozendawn$pausedBurnDuration;

    @Override public boolean frozendawn$isOxygenStarved() { return frozendawn$oxygenStarved; }
    @Override public void frozendawn$starveForOxygen() {
        if (frozendawn$oxygenStarved) return;
        frozendawn$oxygenStarved = true;
        frozendawn$pausedBurnTime = litTime;
        frozendawn$pausedBurnDuration = litDuration;
        litTime = 0; // GUI flame also goes out; prepaid burn time is stored separately.
        frozendawn$setLit(false);
    }
    @Override public void frozendawn$resumeForOxygen() {
        if (!frozendawn$oxygenStarved) return;
        frozendawn$oxygenStarved = false;
        litTime = frozendawn$pausedBurnTime;
        litDuration = frozendawn$pausedBurnDuration;
        frozendawn$pausedBurnTime = 0; frozendawn$pausedBurnDuration = 0;
        frozendawn$setLit(litTime > 0);
    }
    @Unique private void frozendawn$setLit(boolean lit) {
        var furnace = (AbstractFurnaceBlockEntity)(Object)this;
        var level = furnace.getLevel();
        if (level instanceof ServerLevel) {
            var state = furnace.getBlockState();
            if (state.getValue(BlockStateProperties.LIT) != lit)
                level.setBlock(furnace.getBlockPos(), state.setValue(BlockStateProperties.LIT, lit), 3);
        }
        furnace.setChanged();
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void frozendawn$saveOxygenPause(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!frozendawn$oxygenStarved) return;
        tag.putBoolean("FrozenDawnOxygenStarved", true);
        tag.putInt("FrozenDawnPausedBurnTime", frozendawn$pausedBurnTime);
        tag.putInt("FrozenDawnPausedBurnDuration", frozendawn$pausedBurnDuration);
    }
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void frozendawn$loadOxygenPause(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        frozendawn$oxygenStarved = tag.getBoolean("FrozenDawnOxygenStarved");
        frozendawn$pausedBurnTime = Math.max(0, tag.getInt("FrozenDawnPausedBurnTime"));
        frozendawn$pausedBurnDuration = Math.max(0, tag.getInt("FrozenDawnPausedBurnDuration"));
        if (frozendawn$oxygenStarved) litTime = 0;
    }
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void frozendawn$stopCombustion(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        var fields = (VacuumFurnaceMixin)(Object)furnace;
        if (fields.frozendawn$oxygenStarved) {
            if (level instanceof ServerLevel server) RoomOxygenManager.registerStarvedFurnace(server, pos);
            if (state.getValue(BlockStateProperties.LIT)) fields.frozendawn$setLit(false);
            ci.cancel();
            return;
        }
        if (CombustionAtmosphere.canBurnAt(level, pos)) return;
        boolean dirty = fields.litTime > 0 || fields.cookingProgress > 0;
        fields.litTime = 0; fields.litDuration = 0;
        fields.cookingProgress = Math.max(0, fields.cookingProgress - 2);
        if (state.getValue(BlockStateProperties.LIT)) level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
        if (dirty) furnace.setChanged();
        ci.cancel();
    }
}
