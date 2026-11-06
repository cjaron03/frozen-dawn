package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ShepherdActionPayload;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdLogPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.network.ShepherdReplyPayload;
import com.frozendawn.shepherd.ShepherdConversation;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdPolicy;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
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
    static final int TEXT = 0xFFE1E6E4, GLOW = 0xFF7DF2C0, GLOW2 = 0xFF3AA984, DIM = 0xFF8C969C;
    static final int STEEL = 0xFF5D666D;
    private static final int SHELL2 = 0xFFAEB2AD, SCUFF = 0xFF8C918E, LAMP = 0xFFF6F4D8;
    static final int PANEL = 0xFF0E1216, VEIL = 0x73060809;
    static final int SLOT = 0xFF12161B, SLOT_HOVER = 0xFF1E2C2A;
    private static final int EMPTY = 0x8012161B, SAY = 0xFF0A0D10;
    static final int LINE_X = 92;
    private static final int LINE_Y = 103, LINE_W = 295, LINE_STEP = 10;
    static final int SLOT_X = 84, SLOT_Y = 144, SLOT_DX = 158, SLOT_DY = 28, SLOT_W = 153, SLOT_H = 23;
    /** Her display on the shell, and how large one texel draws. */
    static final int FACE_X = 200, FACE_Y = 18, FACE_SCALE = 10;
    private static final int SETTINGS_X = 84, SETTINGS_Y = 202, SETTINGS_W = 150, SETTINGS_H = 14;

    /** What is on the panel. Settings and the log are hers to show: the talk behind them stays on the hub. */
    private enum Page {
        TALK,
        SETTINGS,
        LOG
    }

    private Page page = Page.TALK;
    private ShepherdLogPage log;
    private int conversation;
    private boolean hub, waiting, lamp, heavy;
    private List<ShepherdNodePayload.Line> lines = List.of();
    private List<ShepherdNodePayload.Reply> replies = List.of();
    private boolean canContinue;
    private int line;
    private List<String> rows = List.of();
    private int total, typed, charTicks, age;
    private boolean closedByServer;
    private Mood shownMood;
    private int moodBeat;
    private int logoTicks;
    /** The wait this line was sent with, and how much of it is left. */
    private int thinkTicks, thinkLeft;

    public ShepherdScreen() {
        super(CommonComponents.EMPTY);
    }

    public void show(ShepherdNodePayload payload) {
        conversation = payload.conversation();
        page = Page.TALK;
        hub = false;
        lines = payload.lines();
        replies = payload.replies();
        canContinue = payload.canContinue();
        logoTicks = payload.logoTicks();
        thinkTicks = thinkLeft = payload.thinkTicks();
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
        thinkLeft = 0;
    }

    public void show(ShepherdLogPayload payload) {
        log = new ShepherdLogPage(payload.pages());
        page = Page.LOG;
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
        String text = I18n.get(lines.get(line).key(), lines.get(line).args().toArray());
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
        return !hub && !typing() && line >= lines.size() - 1;
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
        if (moodBeat > 0) moodBeat--;
        if (mood() != shownMood) {
            if (shownMood != null) moodBeat = ShepherdFace.MOOD_BEAT_TICKS;
            shownMood = mood();
        }
        if (logoTicks > 0) {
            logoTicks--;
            return;
        }
        if (thinkLeft > 0) {
            // The same blink as a change of mood, even if the mood did not change: it takes her into the line's face.
            if (--thinkLeft == 0) moodBeat = ShepherdFace.MOOD_BEAT_TICKS;
            return;
        }
        if (!typing()) return;
        if (++charTicks < ShepherdPolicy.TICKS_PER_CHAR) return;
        charTicks = 0;
        typed++;
        if (typed % ShepherdPolicy.BLIP_EVERY == 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SHEPHERD_BLIP.get(), 1, 0.5F));
    }

    /** Where the mockup's grid starts on a screen this wide. */
    static int gridX(int width) {
        return (width - GRID_W) / 2;
    }

    static int gridY(int height) {
        return Math.max(-TOP_MARGIN, (height - GRID_H) / 2);
    }

    private int ox() {
        return gridX(width);
    }

    private int oy() {
        return gridY(height);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= ox() + x && mx < ox() + x + w && my >= oy() + y && my < oy() + y + h;
    }

    static int slotX(int slot) {
        return SLOT_X + slot % 2 * SLOT_DX;
    }

    static int slotY(int slot) {
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
        if (page == Page.LOG) {
            if (hoverOlder(mx, my)) log.move(1, hasShiftDown());
            if (hoverNewer(mx, my)) log.move(-1, hasShiftDown());
            return true;
        }
        if (page == Page.SETTINGS) {
            pressSettings(slot);
            return true;
        }
        if (hub) {
            if (inside(mx, my, SETTINGS_X, SETTINGS_Y, SETTINGS_W, SETTINGS_H)) page = Page.SETTINGS;
            else pressHub(slot);
            return true;
        }
        if (spoken() && slot >= 0 && reply(slot) != null) answer(slot);
        else advance();
        return true;
    }

    /** A click finishes a typing line; the next moves on to her next line, then continues. */
    private void advance() {
        if (logoTicks > 0 || thinkLeft > 0) return;
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

    private void pressSettings(int slot) {
        switch (slot) {
            case 0 -> act(Action.LANGUAGE);
            case 1 -> act(Action.LOG);
            default -> { }
        }
    }

    private boolean hoverOlder(double mx, double my) {
        return inside(mx, my, ShepherdLogPage.OLDER_X, ShepherdLogPage.ROW_Y, ShepherdLogPage.BUTTON_W, ShepherdLogPage.ROW_H);
    }

    private boolean hoverNewer(double mx, double my) {
        return inside(mx, my, ShepherdLogPage.NEWER_X, ShepherdLogPage.ROW_Y, ShepherdLogPage.BUTTON_W, ShepherdLogPage.ROW_H);
    }

    /** Left and right a day, holding a key repeats, Shift ten at a time, and Home and End the two ends. */
    private boolean turnPage(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> log.move(1, hasShiftDown());
            case GLFW.GLFW_KEY_RIGHT -> log.move(-1, hasShiftDown());
            case GLFW.GLFW_KEY_HOME -> log.oldest();
            case GLFW.GLFW_KEY_END -> log.newest();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (page != Page.LOG || scrollY == 0) return false;
        log.move(scrollY < 0 ? 1 : -1, hasShiftDown());
        return true;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && page != Page.TALK) {
            if (page == Page.LOG) act(Action.LOG_DONE);
            page = page == Page.LOG ? Page.SETTINGS : Page.TALK;
            return true;
        }
        if (page == Page.LOG && turnPage(key)) return true;
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            int slot = key - GLFW.GLFW_KEY_1;
            if (page == Page.SETTINGS) pressSettings(slot);
            else if (page == Page.LOG) return true;
            else if (hub) pressHub(slot);
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

    /** {@code ox, oy} are the grid's corner on screen; everything else is on the grid. */
    static void panel(GuiGraphics g, int ox, int oy, int x, int y, int w, int h, int fill, int edge) {
        g.fill(ox + x, oy + y, ox + x + w, oy + y + h, fill);
        g.renderOutline(ox + x, oy + y, w, h, edge);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        panel(g, ox(), oy(), x, y, w, h, fill, edge);
    }

    /** Her body around the display, and the lamp strip over it. */
    static void shell(GuiGraphics g, int ox, int oy) {
        panel(g, ox, oy, 194, 12, 92, 70, SHELL2, SCUFF);
        g.fill(ox + 202, oy + 8, ox + 278, oy + 12, LAMP);
    }

    static void button(GuiGraphics g, Font font, int ox, int oy, int slot, String tone, String label, boolean hover) {
        int x = slotX(slot), y = slotY(slot);
        panel(g, ox, oy, x, y, SLOT_W, SLOT_H, hover ? SLOT_HOVER : SLOT, hover ? GLOW : STEEL);
        g.drawString(font, String.valueOf(slot + 1), ox + x + 5, oy + y + 7, GLOW, false);
        if (!tone.isEmpty()) g.drawString(font, tone, ox + x + 16, oy + y + 2, DIM, false);
        g.drawString(font, label, ox + x + 16, oy + y + (tone.isEmpty() ? 7 : 12), TEXT, false);
    }

    private void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, ox() + x, oy() + y, color, false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int hover = page == Page.LOG ? -1 : slotAt(mx, my);
        drawFace(g, hover);
        if (page == Page.LOG) {
            log.draw(g, font, ox(), oy(), hoverOlder(mx, my), hoverNewer(mx, my));
            text(g, I18n.get("shepherd.screen.log_hint"), SLOT_X, 240, DIM);
            return;
        }
        drawLine(g);
        if (page == Page.SETTINGS) {
            drawSettings(g, hover);
            return;
        }
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

    /** Her eyes go to the reply you are on: the grid's left column, or its right. */
    private void drawFace(GuiGraphics g, int hover) {
        shell(g, ox(), oy());
        if (logoTicks > 0) {
            ShepherdFaceRenderer.drawLogo(g, ShepherdFace.logoFrame(age), ox() + FACE_X, oy() + FACE_Y, FACE_SCALE);
            return;
        }
        if (thinkLeft > 0) {
            ShepherdFaceRenderer.draw(g, ShepherdFace.thinking(thinkTicks - thinkLeft, thinkTicks), 0, false, 0, ox() + FACE_X, oy() + FACE_Y, FACE_SCALE);
            return;
        }
        Mood mood = mood();
        var motion = ShepherdFace.motion(mood, age, conversation);
        boolean looking = hover >= 0 && (hub || spoken()) && mood != Mood.RECORDING && mood != Mood.THINKING;
        int eyes = looking ? (hover % 2 == 0 ? -1 : 1) : motion.eyeShift();
        var cells = motion.cells(mood, looking || typing());
        ShepherdFaceRenderer.draw(g, cells, mouth(), motion.blink() || moodBeat > 0, eyes, ox() + FACE_X, oy() + FACE_Y, FACE_SCALE);
    }

    private void drawLine(GuiGraphics g) {
        panel(g, 84, 88, 312, 49, PANEL, STEEL);
        boolean settings = page == Page.SETTINGS;
        text(g, I18n.get(settings ? "shepherd.screen.settings" : "shepherd.screen.name"), LINE_X, 92, settings ? DIM : GLOW);
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
        button(g, font, ox(), oy(), slot, tone, label, hover);
    }

    private void drawSaid(GuiGraphics g, String full) {
        panel(g, 242, 202, 197, 31, SAY, GLOW2);
        text(g, I18n.get("shepherd.screen.you_say"), 248, 206, DIM);
        text(g, "\"" + full + "\"", 248, 219, TEXT);
    }

    private void drawSettings(GuiGraphics g, int hover) {
        drawSlot(g, 0, "", I18n.get(heavy ? "shepherd.screen.language.heavy" : "shepherd.screen.language.mild"), hover == 0);
        drawSlot(g, 1, "", I18n.get("shepherd.screen.diagnostics"), hover == 1);
        panel(g, slotX(2), slotY(2), SLOT_W, SLOT_H, EMPTY, STEEL);
        panel(g, slotX(3), slotY(3), SLOT_W, SLOT_H, EMPTY, STEEL);
        text(g, I18n.get("shepherd.screen.settings_hint"), SLOT_X, 240, DIM);
    }

    private void drawHub(GuiGraphics g, int hover, boolean hoverSettings) {
        drawSlot(g, 0, "", I18n.get("shepherd.screen.hub.talk"), hover == 0);
        drawSlot(g, 1, "", I18n.get(waiting ? "shepherd.screen.hub.follow" : "shepherd.screen.hub.wait"), hover == 1);
        drawSlot(g, 2, "", I18n.get(lamp ? "shepherd.screen.hub.lamp_off" : "shepherd.screen.hub.lamp_on"), hover == 2);
        panel(g, slotX(3), slotY(3), SLOT_W, SLOT_H, EMPTY, STEEL);
        panel(g, SETTINGS_X, SETTINGS_Y, SETTINGS_W, SETTINGS_H, hoverSettings ? SLOT_HOVER : SLOT, hoverSettings ? GLOW : STEEL);
        text(g, I18n.get("shepherd.screen.settings"), SETTINGS_X + 5, SETTINGS_Y + 3, DIM);
    }
}
