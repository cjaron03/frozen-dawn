package com.frozendawn.mixin;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge forgets an unloading chunk's cached type just before saving it, so that the cache does not outlive the
 * chunk. But saving a chunk that is not yet full asks whether the one on disk is full, and with the entry gone that
 * is a synchronous disk read, queued behind every pending write, on the server thread. Forgetting the entry right
 * after the save instead gives the same answer from the cache, which holds the type last read or written for the
 * chunk, and still leaves nothing behind. The save writes the entry back anyway.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkUnloadTypeCacheMixin {
    @Shadow @Final private Long2ByteMap chunkTypeCache;

    @Redirect(method = "lambda$scheduleUnload$12", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;remove(J)B"))
    private byte frozendawn$keepTypeUntilSaved(Long2ByteMap cache, long chunk) {
        return 0;
    }

    @Inject(method = "lambda$scheduleUnload$12", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", shift = At.Shift.AFTER))
    private void frozendawn$forgetTypeAfterSave(ChunkHolder holder, long chunk, CallbackInfo ci) {
        chunkTypeCache.remove(chunk);
    }
}
