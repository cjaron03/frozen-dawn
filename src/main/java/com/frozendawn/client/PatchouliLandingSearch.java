package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import vazkii.patchouli.api.BookDrawScreenEvent;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookIndex;
import vazkii.patchouli.client.book.gui.GuiBookLanding;
import vazkii.patchouli.client.book.gui.button.GuiButtonCategory;
import org.lwjgl.glfw.GLFW;

/** A search bar on the field manual's front page: typing or clicking opens the Entry Index search. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PatchouliLandingSearch {

    private static final ResourceLocation FROZEN_DAWN_GUIDE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_dawn_guide");
    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/patchouli/landing_search.png");
    private static final Component HINT = Component.translatable("screen.frozendawn.manual.search_hint");
    private static final int HINT_X = 15;
    private static final int HINT_Y = 3;
    private static final int HINT_COLOR = 0xF2E7C8;
    private static final int HINT_HOVER_COLOR = 0xFFFFFF;

    /** The landing page whose bar the mouse was over on the last frame, in Patchouli's scaled space. */
    private static WeakReference<Screen> hovered = new WeakReference<>(null);

    private PatchouliLandingSearch() {
    }

    @SubscribeEvent
    public static void onBookDraw(BookDrawScreenEvent event) {
        GuiBookLanding landing = ours(event.getScreen());
        if (landing == null) {
            return;
        }
        int icons = icons(landing);
        if (!ManualSearchBarPolicy.shown(icons)) {
            hovered = new WeakReference<>(null);
            return;
        }
        boolean over = ManualSearchBarPolicy.contains(icons,
                event.getMouseX() - landing.bookLeft, event.getMouseY() - landing.bookTop);
        hovered = new WeakReference<>(over ? landing : null);

        int x = landing.bookLeft + ManualSearchBarPolicy.LEFT;
        int y = landing.bookTop + ManualSearchBarPolicy.top(icons);
        GuiGraphics graphics = event.getGraphics();
        RenderSystem.enableBlend();
        graphics.blit(BAR, x, y, 0, 0, ManualSearchBarPolicy.WIDTH, ManualSearchBarPolicy.HEIGHT,
                ManualSearchBarPolicy.WIDTH, ManualSearchBarPolicy.HEIGHT);
        RenderSystem.disableBlend();
        graphics.drawString(Minecraft.getInstance().font, HINT, x + HINT_X, y + HINT_Y,
                over ? HINT_HOVER_COLOR : HINT_COLOR, false);
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || hovered.get() != event.getScreen()) {
            return;
        }
        GuiBookLanding landing = ours(event.getScreen());
        if (landing != null) {
            openSearch(landing);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        GuiBookLanding landing = ours(event.getScreen());
        if (landing == null || !ManualSearchBarPolicy.startsSearch(event.getCodePoint())
                || !ManualSearchBarPolicy.shown(icons(landing))) {
            return;
        }
        openSearch(landing);
        if (Minecraft.getInstance().screen instanceof GuiBookIndex index) {
            index.charTyped(event.getCodePoint(), event.getModifiers());
        }
        event.setCanceled(true);
    }

    private static void openSearch(GuiBookLanding landing) {
        hovered = new WeakReference<>(null);
        landing.handleButtonIndex(null);
        GuiBook.playBookFlipSound(landing.book);
    }

    private static GuiBookLanding ours(Screen screen) {
        return screen instanceof GuiBookLanding landing && FROZEN_DAWN_GUIDE.equals(landing.book.id) ? landing : null;
    }

    private static int icons(GuiBookLanding landing) {
        return (int) landing.children().stream().filter(GuiButtonCategory.class::isInstance).count();
    }
}
