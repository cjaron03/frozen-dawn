package com.frozendawn.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Mixin(SplashManager.class)
public class SplashManagerMixin {

    @Shadow @Final private List<String> splashes;

    @Unique
    private static final RandomSource frozendawn$random = RandomSource.create();

    @Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
    private void frozendawn$replaceVanillaSplashes(CallbackInfoReturnable<SplashRenderer> cir) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());

        if (calendar.get(Calendar.MONTH) + 1 == 12 && calendar.get(Calendar.DAY_OF_MONTH) == 24) {
            cir.setReturnValue(new SplashRenderer("Silent night. Violent dawn."));
            return;
        }

        if (calendar.get(Calendar.MONTH) + 1 == 1 && calendar.get(Calendar.DAY_OF_MONTH) == 1) {
            cir.setReturnValue(new SplashRenderer("Another year colder."));
            return;
        }

        if (calendar.get(Calendar.MONTH) + 1 == 10 && calendar.get(Calendar.DAY_OF_MONTH) == 31) {
            cir.setReturnValue(new SplashRenderer("Even the dead need shelter."));
            return;
        }

        if (this.splashes.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        cir.setReturnValue(new SplashRenderer(this.splashes.get(frozendawn$random.nextInt(this.splashes.size()))));
    }
}
