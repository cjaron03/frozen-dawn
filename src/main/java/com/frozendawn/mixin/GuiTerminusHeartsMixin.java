package com.frozendawn.mixin;

import com.frozendawn.client.TerminusHearts;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's own layout for the hearts, so the Terminus's taken hearts line up after them. */
@Mixin(Gui.class)
public abstract class GuiTerminusHeartsMixin {

    @Inject(method = "renderHearts", at = @At("TAIL"))
    private void frozendawn$renderTakenHearts(GuiGraphics graphics, Player player, int x, int y, int rowHeight,
                                              int regenIndex, float maxHealth, int health, int displayHealth,
                                              int absorption, boolean blinking, CallbackInfo ci) {
        TerminusHearts.render(graphics, player, x, y, rowHeight, maxHealth, absorption);
    }
}
