package com.frozendawn.mixin;

import com.frozendawn.event.BlindArchitectEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ongoing, server-accepted mining; packet-provided start positions are not trusted emissions. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected ServerPlayer player;
    @Inject(method = "incrementDestroyProgress", at = @At("HEAD"))
    private void frozenDawnMining(BlockState state, BlockPos pos, int started, CallbackInfoReturnable<Float> callback) {
        BlindArchitectEvents.mining(level, player, pos);
    }
}
