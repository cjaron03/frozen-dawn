package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ShepherdActionPayload;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.network.ShepherdReplyPayload;
import com.frozendawn.shepherd.ShepherdConversation;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Her talk screen and hub, as talk-screen-mockup.png draws them: everything is placed on the mockup's 480 by 270 grid. */
public final class ShepherdScreen extends Screen {
    private static final int GRID_W = 480, GRID_H = 270, TOP_MARGIN = 8;
    private static final int TEXT = 0xFFE1E6E4, GLOW = 0xFF7DF2C0, GLOW2 = 0xFF3AA984, DIM = 0xFF8C969C;
    private static final int STEEL = 0xFF5D666D, SHELL2 = 0xFFAEB2AD, SCUFF = 0xFF8C918E, LAMP = 0xFFF6F4D8;
    private static final int PANEL = 0xFF0E1216, SLOT = 0xFF12161B, SLOT_HOVER = 0xFF1E2C2A, EMPTY = 0x8012161B, SAY = 0xFF0A0D10, VEIL = 0x73060809;
    private static final int LINE_X = 92, LINE_Y = 103, LINE_W = 295, LINE_STEP = 10;
    private static final int SLOT_X = 84, SLOT_Y = 144, SLOT_DX = 158, SLOT_DY = 28, SLOT_W = 153, SLOT_H = 23;
    private static final int SETTINGS_X = 84, SETTINGS_Y = 202, SETTINGS_W = 150, SETTINGS_H = 14;
    private static final int BLINK_EVERY = 90, BLINK_TICKS = 3;

    private int conversation;
    private boolean hub, waiting, lamp, heavy;
    private List<ShepherdNodePayload.Line> lines = List.of();
    private List<ShepherdNodePayload.Reply> replies = List.of();
    private boolean canContinue;
    private int line;
    private List<String> rows = List.of();
    private int total, typed, charTicks, age;
    private boolean closedByServer;

    public ShepherdScreen() {
        super(CommonComponents.EMPTY);
    }

    public void show(ShepherdNodePayload payload) {
        conversation = payload.conversation();
        hub = false;
        lines = payload.lines();
        replies = payload.replies();
        canContinue = payload.canContinue();
        line = 0;
        startLine();
    }

    public void show(ShepherdHubPayload payload) {
        conversation = payload.conversation();
        hub = true;
        waiting = payload.waiting();
        lamp = payload.lamp();
        heavy = payload.heavy();
        lines = List.of();
        replies = List.of();
        canContinue = false;
        rows = List.of();
        total = typed = 0;
    }

    /** The server ended it, so there is nothing to tell the server back. */
    public void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override protected void init() {
        if (!lines.isEmpty()) startLine(typed);
    }

    private void startLine() {
        startLine(0);
    }

    /** Wraps the whole line first, so a word never jumps down a row halfway through typing. */
    private void startLine(int alreadyTyped) {
        if (font == null) return;
        String text = I18n.get(lines.get(line).key());
        rows = new ArrayList<>();
        for (var row : font.getSplitter().splitLines(text, LINE_W, Style.EMPTY)) rows.add(row.getString());
        total = rows.stream().mapToInt(String::length).sum();
        typed = Math.min(alreadyTyped, total);
        charTicks = 0;
    }

    private boolean typing() {
        return typed < total;
    }

    private boolean spoken() {
        return !hub && !typing() && line == lines.size() - 1;
    }

    private Mood mood() {
        return hub || lines.isEmpty() ? Mood.NEUTRAL : lines.get(line).mood();
    }

    private char typedChar() {
        int left = Math.max(0, typed - 1);
        for (String row : rows) {
            if (left < row.length()) return row.charAt(left);
            left -= row.length();
        }
        return ' ';
    }

    /** Steps through shut, open, wide while typing, and shuts on punctuation. */
    private int mouth() {
        if (!typing() || ".,?!;:'\"".indexOf(typedChar()) >= 0) return 0;
        return typed / 2 % 3;
    }

    @Override public void tick() {
        age++;
        if (!typing()) return;
        if (++charTicks < ShepherdPolicy.TICKS_PER_CHAR) return;
        charTicks = 0;
        typed++;
        if (typed % ShepherdPolicy.BLIP_EVERY == 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHEPHERD_BLIP.get(), 1, 0.5F));
    }

    private int ox() {
        return (width - GRID_W) / 2;
    }

    private int oy() {
        return Math.max(-TOP_MARGIN, (height - GRID_H) / 2);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= ox() + x && mx < ox() + x + w && my >= oy() + y && my < oy() + y + h;
    }

    private static int slotX(int slot) {
        return SLOT_X + slot % 2 * SLOT_DX;
    }

    private static int slotY(int slot) {
        return SLOT_Y + slot / 2 * SLOT_DY;
    }

    private int slotAt(double mx, double my) {
        for (int slot = 0; slot < 4; slot++) if (inside(mx, my, slotX(slot), slotY(slot), SLOT_W, SLOT_H)) return slot;
        return -1;
    }

    private ShepherdNodePayload.Reply reply(int slot) {
        for (var reply : replies) if (reply.slot() == slot) return reply;
        return null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int slot = slotAt(mx, my);
        if (hub) {
            if (inside(mx, my, SETTINGS_X, SETTINGS_Y, SETTINGS_W, SETTINGS_H)) act(Action.SETTINGS);
            else pressHub(slot);
            return true;
        }
        if (spoken() && slot >= 0 && reply(slot) != null) answer(slot);
        else advance();
        return true;
    }

    /** A click finishes a typing line; the next moves on to her next line, then continues. */
    private void advance() {
        if (typing()) {
            typed = total;
        } else if (line < lines.size() - 1) {
            line++;
            startLine();
        } else if (canContinue) {
            answer(ShepherdConversation.CONTINUE);
        }
    }

    private void answer(int slot) {
        PacketDistributor.sendToServer(new ShepherdReplyPayload(conversation, slot));
    }

    private void act(Action action) {
        PacketDistributor.sendToServer(new ShepherdActionPayload(conversation, action));
    }

    private void pressHub(int slot) {
        switch (slot) {
            case 0 -> act(Action.TALK);
            case 1 -> act(Action.FOLLOW);
            case 2 -> act(Action.LAMP);
            default -> { }
        }
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            int slot = key - GLFW.GLFW_KEY_1;
            if (hub) pressHub(slot);
            else if (spoken() && reply(slot) != null) answer(slot);
            else if (!spoken()) advance();
            return true;
        }
        if (!hub && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) {
            advance();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    @Override public void removed() {
        if (!closedByServer) PacketDistributor.sendToServer(new ShepherdClosePayload());
    }

    /** The world keeps running behind her, so a veil and no blur. */
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, VEIL);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(ox() + x, oy() + y, ox() + x + w, oy() + y + h, fill);
        g.renderOutline(ox() + x, oy() + y, w, h, edge);
    }

    private void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, ox() + x, oy() + y, color, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        drawFace(g);
        drawLine(g);
        int hover = slotAt(mx, my);
        if (hub) {
            drawHub(g, hover, inside(mx, my, SETTINGS_X, SETTINGS_Y, SETTINGS_W, SETTINGS_H));
        } else {
            for (int slot = 0; slot < 4; slot++) drawReply(g, slot, spoken() ? reply(slot) : null, hover == slot);
            var said = spoken() && hover >= 0 ? reply(hover) : null;
            if (said != null) drawSaid(g, I18n.get(said.full()));
        }
        String hint = !hub && spoken() && canContinue ? "shepherd.screen.continue" : "shepherd.screen.hint";
        text(g, I18n.get(hint), SLOT_X, 240, DIM);
    }

    private void drawFace(GuiGraphics g) {
        panel(g, 194, 12, 92, 70, SHELL2, SCUFF);
        g.fill(ox() + 202, oy() + 8, ox() + 278, oy() + 12, LAMP);
        boolean blink = age % BLINK_EVERY < BLINK_TICKS;
        ShepherdFaceRenderer.draw(g, mood(), mouth(), blink, ox() + 200, oy() + 18, 10);
    }

    private void drawLine(GuiGraphics g) {
        panel(g, 84, 88, 312, 49, PANEL, STEEL);
        text(g, I18n.get("shepherd.screen.name"), LINE_X, 92, GLOW);
        int left = typed, y = LINE_Y, cursorX = LINE_X, cursorY = LINE_Y;
        for (String row : rows) {
            String shown = row.substring(0, Math.min(left, row.length()));
            text(g, shown, LINE_X, y, TEXT);
            left -= shown.length();
            if (!shown.isEmpty()) {
                cursorX = LINE_X + font.width(shown) + 1;
                cursorY = y;
            }
            y += LINE_STEP;
        }
        if (!rows.isEmpty()) g.fill(ox() + cursorX, oy() + cursorY, ox() + cursorX + 4, oy() + cursorY + 8, GLOW);
    }

    private void drawReply(GuiGraphics g, int slot, ShepherdNodePayload.Reply reply, boolean hover) {
        int x = slotX(slot), y = slotY(slot);
        if (reply == null) {
            panel(g, x, y, SLOT_W, SLOT_H, EMPTY, STEEL);
            return;
        }
        drawSlot(g, slot, I18n.get("shepherd.screen.tone." + Slot.values()[slot].name().toLowerCase(Locale.ROOT)), I18n.get(reply.label()), hover);
    }

    private void drawSlot(GuiGraphics g, int slot, String tone, String label, boolean hover) {
        int x = slotX(slot), y = slotY(slot);
        panel(g, x, y, SLOT_W, SLOT_H, hover ? SLOT_HOVER : SLOT, hover ? GLOW : STEEL);
        text(g, String.valueOf(slot + 1), x + 5, y + 7, GLOW);
        if (!tone.isEmpty()) text(g, tone, x + 16, y + 2, DIM);
        text(g, label, x + 16, tone.isEmpty() ? y + 7 : y + 12, TEXT);
    }

    private void drawSaid(GuiGraphics g, String full) {
        panel(g, 242, 202, 197, 31, SAY, GLOW2);
        text(g, I18n.get("shepherd.screen.you_say"), 248, 206, DIM);
        text(g, "\"" + full + "\"", 248, 219, TEXT);
    }

    private void drawHub(GuiGraphics g, int hover, boolean hoverSettings) {
        drawSlot(g, 0, "", I18n.get("shepherd.screen.hub.talk"), hover == 0);
        drawSlot(g, 1, "", I18n.get(waiting ? "shepherd.screen.hub.follow" : "shepherd.screen.hub.wait"), hover == 1);
        drawSlot(g, 2, "", I18n.get(lamp ? "shepherd.screen.hub.lamp_off" : "shepherd.screen.hub.lamp_on"), hover == 2);
        panel(g, slotX(3), slotY(3), SLOT_W, SLOT_H, EMPTY, STEEL);
        panel(g, SETTINGS_X, SETTINGS_Y, SETTINGS_W, SETTINGS_H, hoverSettings ? SLOT_HOVER : SLOT, hoverSettings ? GLOW : STEEL);
        text(g, I18n.get(heavy ? "shepherd.screen.language.heavy" : "shepherd.screen.language.mild"), SETTINGS_X + 5, SETTINGS_Y + 3, DIM);
    }
}
