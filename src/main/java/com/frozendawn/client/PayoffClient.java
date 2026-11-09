package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The payoff as this client sees it: the CH-18 line on the death screen of the player whose death fired it. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PayoffClient {
    private static final String LINE_1 = "screen.frozendawn.payoff.death_line.1";
    private static final String LINE_2 = "screen.frozendawn.payoff.death_line.2";
    /** The death screen's own text color. */
    private static final int TEXT_COLOR = 0xFFFFFF;
    /** Between the second line's bottom and the first button's top. */
    private static final int GAP = 4;

    private static boolean lineArrived, drawLine, deathScreenSeen;

    private PayoffClient() { }

    public static void showDeathLine() {
        lineArrived = true;
        drawLine = true;
        deathScreenSeen = false;
    }

    /** True once if the line arrived but no death screen drew it, as with immediate respawn. Clears the line. */
    public static boolean takeUnseenDeathLine() {
        boolean unseen = lineArrived && !deathScreenSeen;
        lineArrived = drawLine = deathScreenSeen = false;
        return unseen;
    }

    @SubscribeEvent
    public static void draw(ScreenEvent.Render.Post event) {
        if (!drawLine || !(event.getScreen() instanceof DeathScreen screen)) return;
        int top = Integer.MAX_VALUE;
        for (var child : screen.children()) if (child instanceof AbstractWidget widget) top = Math.min(top, widget.getY());
        if (top == Integer.MAX_VALUE) return;
        deathScreenSeen = true;
        var font = Minecraft.getInstance().font;
        int second = top - GAP - font.lineHeight;
        int first = second - font.lineHeight - 1;
        var graphics = event.getGuiGraphics();
        graphics.drawCenteredString(font, Component.translatable(LINE_1), screen.width / 2, first, TEXT_COLOR);
        graphics.drawCenteredString(font, Component.translatable(LINE_2), screen.width / 2, second, TEXT_COLOR);
    }

    @SubscribeEvent
    public static void respawned(ClientPlayerNetworkEvent.Clone event) { drawLine = false; }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) { lineArrived = drawLine = deathScreenSeen = false; }
}
