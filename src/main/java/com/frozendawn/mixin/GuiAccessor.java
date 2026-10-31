package com.frozendawn.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows oxygen feedback to clear its own text without erasing unrelated notices. */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageString") Component frozendawn$getOverlayMessage();
}
