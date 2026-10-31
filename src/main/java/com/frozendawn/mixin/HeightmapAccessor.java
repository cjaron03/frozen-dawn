package com.frozendawn.mixin;

import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Heightmap.class)
public interface HeightmapAccessor {
    @Invoker("setHeight")
    void frozendawn$setHeight(int x, int z, int value);
}
