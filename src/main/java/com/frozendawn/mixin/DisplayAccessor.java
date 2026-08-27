package com.frozendawn.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void frozendawn$setTransformation(Transformation transformation);

    @Invoker("setPosRotInterpolationDuration")
    void frozendawn$setPosRotInterpolationDuration(int duration);
}
