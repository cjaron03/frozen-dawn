package com.frozendawn.client;

import com.frozendawn.miller.TypedCheck;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** The line under her slots, as chat-bar-mockup.png draws it. What is typed shows here and nowhere else. */
final class ShepherdChatBar {
    static final int X = 84, Y = 202, W = 312, H = 14;
    private static final int TEXT_X = 5, TEXT_Y = 3, PLACEHOLDER = 0xFF79838A, OFF = 0xFF3A4149, OPEN_FILL = 0xFF0A0D10;

    enum State {
        CLOSED,
        OPEN,
        /** Dim, while she works out what was sent. */
        SENT
    }

    private final EditBox box;
    private State state = State.CLOSED;
    private String sent = "";

    ShepherdChatBar(Font font) {
        box = new EditBox(font, 0, 0, W - 2 * TEXT_X, 10, Component.empty());
        box.setBordered(false);
        box.setMaxLength(TypedCheck.MAX_CHARS);
        box.setTextColor(ShepherdScreen.TEXT);
    }

    State state() {
        return state;
    }

    boolean open() {
        return state == State.OPEN;
    }

    void show() {
        state = State.OPEN;
        box.setFocused(true);
    }

    void close() {
        state = State.CLOSED;
        sent = "";
        box.setValue("");
        box.setFocused(false);
    }

    String value() {
        return box.getValue().strip();
    }

    void sent() {
        sent = box.getValue();
        state = State.SENT;
        box.setValue("");
        box.setFocused(false);
    }

    boolean keyPressed(int key, int scanCode, int modifiers) {
        return box.keyPressed(key, scanCode, modifiers);
    }

    boolean charTyped(char c, int modifiers) {
        return box.charTyped(c, modifiers);
    }

    void click(double mx, double my) {
        box.mouseClicked(mx, my, 0);
        box.setFocused(true);
    }

    /** {@code dead} is the crash: she cannot hear it, whatever is on it. */
    void draw(GuiGraphics g, Font font, int ox, int oy, int mx, int my, float partial, boolean hover, boolean dead) {
        boolean lit = open() || hover && !dead;
        ShepherdScreen.panel(g, ox, oy, X, Y, W, H, open() ? OPEN_FILL : ShepherdScreen.SLOT, lit ? ShepherdScreen.GLOW : ShepherdScreen.STEEL);
        if (open()) {
            box.setPosition(ox + X + TEXT_X, oy + Y + TEXT_Y);
            box.render(g, mx, my, partial);
            return;
        }
        int color = dead ? OFF : state == State.SENT || hover ? ShepherdScreen.DIM : PLACEHOLDER;
        String shown = state == State.SENT ? font.plainSubstrByWidth(sent, W - 2 * TEXT_X, true) : I18n.get("shepherd.screen.say");
        g.drawString(font, shown, ox + X + TEXT_X, oy + Y + TEXT_Y, color, false);
    }
}
