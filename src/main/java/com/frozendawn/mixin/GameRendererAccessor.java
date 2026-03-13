package com.frozendawn.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("postEffect")
    PostChain frozendawn$getPostEffect();

    @Invoker("shutdownEffect")
    void frozendawn$shutdownEffect();
}
