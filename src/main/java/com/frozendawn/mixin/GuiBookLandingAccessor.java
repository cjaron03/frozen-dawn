package com.frozendawn.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import vazkii.patchouli.client.book.gui.BookTextRenderer;
import vazkii.patchouli.client.book.gui.GuiBookLanding;

/** Lets the Frozen Dawn guide's landing text follow the world's payoff flag. */
@Mixin(value = GuiBookLanding.class, remap = false)
public interface GuiBookLandingAccessor {
    @Accessor("text") void frozendawn$setText(BookTextRenderer text);
}
