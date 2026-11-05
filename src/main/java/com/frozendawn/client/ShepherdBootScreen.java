package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ShepherdBootAnswerPayload;
import com.frozendawn.shepherd.ShepherdBoot;
import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The choice and the BIOS, as boot-sequence-mockup.png draws them on the talk screen's grid. The lines run on this
 * screen's own count from Boot; the first node to arrive replaces it, and the server closes it if nothing will.
 */
public final class ShepherdBootScreen extends Screen {
    private static final int BOOT = 0, LEAVE = 1;
    private static final int BIOS_X = 84, BIOS_Y = 88, BIOS_W = 312, BIOS_H = 108;
    private static final int LEFT_X = 92, RIGHT_X = 232, ROW_Y = 92, ROW_STEP = 9;
    private static final int UNIT_Y = 92, HINT_Y = 240;

    private boolean booted, closedByServer;
    private int age;

    public ShepherdBootScreen() {
        super(CommonComponents.EMPTY);
    }

    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    private int ox() {
        return ShepherdScreen.gridX(width);
    }

    private int oy() {
        return ShepherdScreen.gridY(height);
    }

    private void answer(boolean boot) {
        if (boot) {
            booted = true;
            PacketDistributor.sendToServer(new ShepherdBootAnswerPayload(true));
        } else {
            onClose();
        }
    }

    private int slotAt(double mx, double my) {
        for (int slot = BOOT; slot <= LEAVE; slot++) {
            int x = ox() + ShepherdScreen.slotX(slot), y = oy() + ShepherdScreen.slotY(slot);
            if (mx >= x && mx < x + ShepherdScreen.SLOT_W && my >= y && my < y + ShepherdScreen.SLOT_H) return slot;
        }
        return -1;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || booted) return false;
        int slot = slotAt(mx, my);
        if (slot >= 0) answer(slot == BOOT);
        return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!booted && (key == GLFW.GLFW_KEY_1 || key == GLFW.GLFW_KEY_2)) {
            answer(key == GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** Everything after Boot is committed, and like the logo the lines cannot be skipped. */
    @Override public boolean shouldCloseOnEsc() {
        return !booted || ShepherdBoot.stuck(age);
    }

    @Override public void tick() {
        if (!booted) return;
        boolean newLine = age < ShepherdBoot.LINE_COUNT * ShepherdBoot.LINE_TICKS && age % ShepherdBoot.LINE_TICKS == 0;
        if (newLine) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHEPHERD_BLIP.get(), 1, 0.5F));
        age++;
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    /** Esc is Leave. Past Boot, or when the server closed this, there is nothing to tell it. */
    @Override public void removed() {
        if (!closedByServer && !booted) PacketDistributor.sendToServer(new ShepherdBootAnswerPayload(false));
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, ShepherdScreen.VEIL);
    }

    private void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, ox() + x, oy() + y, color, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        ShepherdScreen.shell(g, ox(), oy());
        boolean cursor = booted && age / ShepherdBoot.LINE_TICKS % 2 == 0;
        Cells display = cursor ? ShepherdBoot.cursor() : Mood.OFFLINE;
        ShepherdFaceRenderer.draw(g, display, 0, false, 0, ox() + ShepherdScreen.FACE_X, oy() + ShepherdScreen.FACE_Y, ShepherdScreen.FACE_SCALE);
        if (booted) drawBios(g);
        else drawChoice(g, slotAt(mx, my));
    }

    private void drawChoice(GuiGraphics g, int hover) {
        ShepherdScreen.panel(g, ox(), oy(), 84, 88, 312, 49, ShepherdScreen.PANEL, ShepherdScreen.STEEL);
        text(g, I18n.get("shepherd.boot.unit"), ShepherdScreen.LINE_X, UNIT_Y, ShepherdScreen.DIM);
        ShepherdScreen.button(g, font, ox(), oy(), BOOT, "", I18n.get("shepherd.boot.boot"), hover != LEAVE);
        ShepherdScreen.button(g, font, ox(), oy(), LEAVE, "", I18n.get("shepherd.boot.leave"), hover == LEAVE);
        text(g, I18n.get("shepherd.boot.hint"), ShepherdScreen.SLOT_X, HINT_Y, ShepherdScreen.DIM);
    }

    /** Two columns, since the font is not monospaced. A row with no right side is a heading, and runs across. */
    private void drawBios(GuiGraphics g) {
        ShepherdScreen.panel(g, ox(), oy(), BIOS_X, BIOS_Y, BIOS_W, BIOS_H, ShepherdScreen.PANEL, ShepherdScreen.STEEL);
        if (age >= ShepherdBoot.BIOS_TICKS) return;
        int shown = ShepherdBoot.shownLines(age);
        for (int line = 0; line < shown; line++) {
            String right = I18n.get("shepherd.boot.line." + line + ".right");
            int y = ROW_Y + line * ROW_STEP;
            text(g, I18n.get("shepherd.boot.line." + line + ".left"), LEFT_X, y, right.isEmpty() ? ShepherdScreen.GLOW : ShepherdScreen.GLOW2);
            text(g, right, RIGHT_X, y, ShepherdScreen.GLOW);
        }
        if (shown < ShepherdBoot.LINE_COUNT) g.fill(ox() + LEFT_X, oy() + ROW_Y + shown * ROW_STEP, ox() + LEFT_X + 4, oy() + ROW_Y + shown * ROW_STEP + 8, ShepherdScreen.GLOW);
    }
}
