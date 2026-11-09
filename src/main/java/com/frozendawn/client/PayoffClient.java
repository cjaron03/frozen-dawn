package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DeathScreen;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import vazkii.patchouli.client.base.PersistentData;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * The payoff as this client sees it: the CH-18 line on the death screen of the player whose death fired it, and the
 * guide's two newly opened entries marked unread.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PayoffClient {
    private static final String LINE_1 = "screen.frozendawn.payoff.death_line.1";
    private static final String LINE_2 = "screen.frozendawn.payoff.death_line.2";
    /** The death screen's own text color. */
    private static final int TEXT_COLOR = 0xFFFFFF;
    /** Between the second line's bottom and the first button's top. */
    private static final int GAP = 4;

    private static final ResourceLocation GUIDE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_dawn_guide");
    private static final List<ResourceLocation> OPENED_ENTRIES = List.of(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "orsa_intel/what_is_orsa"),
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "orsa_intel/projected_timeline"));

    /** The second line starts typing this long after the death screen opens, then drops out once and holds. */
    private static final int TYPE_START = 20;
    private static final int DROP_AFTER = 6, DROP_TICKS = 2;

    private static boolean lineArrived, drawLine, deathScreenSeen;
    private static int screenTicks;

    private PayoffClient() { }

    public static void showDeathLine() {
        lineArrived = true;
        drawLine = true;
        deathScreenSeen = false;
        screenTicks = 0;
    }

    /**
     * How much of the second line shows this many ticks after the death screen opened: 0 before it starts, one
     * letter a tick, then -1 for its one dropout, then all of it (owner mockup, 2026-11-09).
     */
    static int typedChars(int ticks, int length) {
        if (ticks < TYPE_START) return 0;
        int done = TYPE_START + length;
        if (ticks < done) return ticks - TYPE_START + 1;
        if (ticks >= done + DROP_AFTER && ticks < done + DROP_AFTER + DROP_TICKS) return -1;
        return length;
    }

    /**
     * Patchouli marks an entry unread only while it is missing from the viewed list, and a page unlocking does not
     * touch that list, so the two entries that gained a page come off it.
     */
    public static void markPagesNew() {
        var book = BookRegistry.INSTANCE.books.get(GUIDE);
        if (book == null) return;
        var viewed = PersistentData.data.getBookData(book).viewedEntries;
        if (!viewed.removeAll(OPENED_ENTRIES)) return;
        PersistentData.save();
        for (var id : OPENED_ENTRIES) {
            var entry = book.getContents().entries.get(id);
            if (entry != null) entry.markReadStateDirty();
        }
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
        String line = Component.translatable(LINE_2).getString();
        int shown = typedChars(screenTicks, line.length());
        if (shown <= 0) return;
        // Typed from the finished line's left edge, so it doesn't slide as it grows.
        int left = screen.width / 2 - font.width(line) / 2;
        String part = line.substring(0, shown);
        graphics.drawString(font, part, left, second, TEXT_COLOR);
        if (shown < line.length() && ((screenTicks >> 1) & 1) == 0) {
            graphics.drawString(font, "_", left + font.width(part), second, TEXT_COLOR);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (drawLine && Minecraft.getInstance().screen instanceof DeathScreen) screenTicks++;
    }

    @SubscribeEvent
    public static void respawned(ClientPlayerNetworkEvent.Clone event) { drawLine = false; }

    @SubscribeEvent
    public static void forget(ClientPlayerNetworkEvent.LoggingOut event) { lineArrived = drawLine = deathScreenSeen = false; }
}
