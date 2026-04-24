package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import vazkii.patchouli.api.BookDrawScreenEvent;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookLanding;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PatchouliLandingBranding {

    private static final ResourceLocation FROZEN_DAWN_GUIDE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_dawn_guide");
    private static final ResourceLocation ORSA_MASTHEAD =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/patchouli/orsa_masthead.png");
    private static final int TEXTURE_WIDTH = 817;
    private static final int TEXTURE_HEIGHT = 362;
    private static final int MASTHEAD_WIDTH = 106;
    private static final int MASTHEAD_HEIGHT = 47;

    private PatchouliLandingBranding() {
    }

    @SubscribeEvent
    public static void onBookDraw(BookDrawScreenEvent event) {
        if (!FROZEN_DAWN_GUIDE.equals(event.getBook()) || !(event.getScreen() instanceof GuiBookLanding landing)) {
            return;
        }

        int x = landing.bookLeft + GuiBook.LEFT_PAGE_X + 5;
        int y = landing.bookTop + GuiBook.TOP_PADDING + 25;
        RenderSystem.enableBlend();
        event.getGraphics().blit(ORSA_MASTHEAD, x, y, MASTHEAD_WIDTH, MASTHEAD_HEIGHT, 0.0F, 0.0F,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
        RenderSystem.disableBlend();
    }
}
