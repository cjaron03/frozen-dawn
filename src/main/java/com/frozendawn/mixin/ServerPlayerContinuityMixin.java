package com.frozendawn.mixin;

import com.frozendawn.event.ContinuityRecoveryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerContinuityMixin {
    @Inject(method = "setRespawnPosition", at = @At("TAIL"))
    private void frozendawn$recordShelter(ResourceKey<Level> dimension, BlockPos position, float angle,
            boolean forced, boolean sendMessage, CallbackInfo ci) {
        ContinuityRecoveryHandler.onSpawnRecorded((ServerPlayer) (Object) this, dimension, position, forced);
    }
}
