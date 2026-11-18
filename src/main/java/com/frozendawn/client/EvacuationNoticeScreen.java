package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.EvacuationNotice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * CH-38's notice as ORSA printed it: one sterile sheet per page, the masthead, watermark and filing strip on every
 * printed page, the council's small seal on the cover, a single red bar. Kevin's page is the back of the last sheet,
 * with nothing on it but his pen. The paper never moves; only the page turns.
 */
public final class EvacuationNoticeScreen extends Screen {
    private static final ResourceLocation MASTHEAD = texture("masthead"), WATERMARK = texture("watermark"),
            SEAL = texture("seal");
    private static final int W = 208, H = 240, M = 14, TEXT_W = W - 2 * M, VALUE_X = M + 70, LINE = 10, GAP = 6;
    private static final int CHROME = 16, SHEET_W = W + 4, SHEET_H = H + 4;
    private static final int PAPER = 0xFFF7F8F6, BORDER = 0xFFC4CACD, SHADOW = 0x5A000000, INK = 0xFF1F2A2C,
            LABEL = 0xFF7A8488, RULE = 0xFFC9CFD2, ALERT = 0xFFC8332B, ALERT_DARK = 0xFF8F231D, WHITE = 0xFFFFFFFF,
            PEN = 0xFF263468, CHROME_TEXT = 0xFFDCE0E2, CHROME_HOVER = 0xFFFFFFFF;
    private static final int[] JITTER = {0, 1, 0, -1, 1, 0, 0, 1, -1, 0};
    private int page;
    private float scale = 1F, originX, originY;

    public EvacuationNoticeScreen() {
        super(Component.translatable("item.frozendawn.evacuation_notice"));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/gui/evacuation_notice/" + name + ".png");
    }

    private static Component text(String key) {
        return Component.translatable(EvacuationNotice.KEY + key);
    }

    private void layout() {
        scale = Math.min(1F, (height - 8F) / (SHEET_H + CHROME));
        originX = (width - SHEET_W * scale) / 2F;
        originY = (height - (SHEET_H + CHROME) * scale) / 2F;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        layout();
        double sx = (mouseX - originX) / scale, sy = (mouseY - originY) / scale;
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0F);
        g.pose().scale(scale, scale, 1F);
        g.fill(3, 3, W + 3, H + 3, SHADOW);
        g.fill(0, 0, W, H, PAPER);
        g.renderOutline(0, 0, W, H, BORDER);
        if (EvacuationNotice.printed(page)) {
            blit(g, WATERMARK, (W - 150) / 2, page == 0 ? 112 : (H - 66) / 2 - 10, 150, 66, 150, 66);
            controlStrip(g);
        }
        if (page == 0) {
            cover(g);
        } else if (EvacuationNotice.printed(page)) {
            printedPage(g, EvacuationNotice.PAGES.get(page - 1));
        } else {
            handwritten(g, EvacuationNotice.PAGES.get(page - 1));
        }
        chrome(g, sx, sy);
        g.pose().popPose();
    }

    private void cover(GuiGraphics g) {
        blit(g, MASTHEAD, M - 4, 8, 64, 28, 128, 57);
        blit(g, SEAL, W - M - 13, 10, 14, 14, 14, 14);
        for (int i = 0; i < 2; i++) {
            Component line = text("council." + (i + 1));
            small(g, line, W - M - 17 - font.width(line) / 2, 11 + i * 6, LABEL);
        }
        int y = 36;
        g.fill(M, y, W - M, y + 1, INK);
        y += 6;
        g.drawString(font, text("title").copy().withStyle(ChatFormatting.BOLD), M, y, INK, false);
        y += 14;
        for (String field : List.of("classification", "date", "point")) {
            g.drawString(font, text(field + ".label"), M, y, LABEL, false);
            g.drawString(font, text(field), VALUE_X, y, INK, false);
            y += LINE;
        }
        y += 4;
        g.fill(M, y, W - M, y + 14, ALERT);
        g.fill(M, y + 14, W - M, y + 15, ALERT_DARK);
        Component alert = text("alert").copy().withStyle(ChatFormatting.BOLD);
        g.drawString(font, alert, M + (TEXT_W - font.width(alert)) / 2, y + 3, WHITE, false);
        y += 22;
        for (FormattedCharSequence line : font.split(text("order"), TEXT_W)) {
            g.drawString(font, line, M, y, INK, false);
            y += LINE;
        }
    }

    private void printedPage(GuiGraphics g, List<EvacuationNotice.Line> lines) {
        int y = 16;
        for (EvacuationNotice.Line line : lines) {
            if (line.kind() == EvacuationNotice.Kind.GAP) {
                y += GAP;
                continue;
            }
            Component component = Component.translatable(line.translationKey());
            if (line.kind() == EvacuationNotice.Kind.HEADING) component = component.copy().withStyle(ChatFormatting.BOLD);
            for (FormattedCharSequence wrapped : font.split(component, TEXT_W)) {
                g.drawString(font, wrapped, M, y, INK, false);
                y += LINE;
            }
        }
    }

    /** His pen: italic, blue-black, every line a little off the line before it and some words off the line. */
    private void handwritten(GuiGraphics g, List<EvacuationNotice.Line> lines) {
        Style pen = Style.EMPTY.withItalic(true);
        int y = 18;
        for (EvacuationNotice.Line line : lines) {
            if (line.kind() == EvacuationNotice.Kind.GAP) {
                y += GAP;
                continue;
            }
            String words = Component.translatable(line.translationKey()).getString();
            for (FormattedText wrapped : font.getSplitter().splitLines(words, TEXT_W - 6, pen)) {
                int x = M + 2 + JITTER[(y / LINE) % JITTER.length];
                String[] split = wrapped.getString().split(" ");
                for (int k = 0; k < split.length; k++) {
                    Component word = Component.literal(split[k]).withStyle(pen);
                    int lift = k % 3 == 2 ? JITTER[(k + y) % JITTER.length] : 0;
                    g.drawString(font, word, x, y + lift, PEN, false);
                    x += font.width(word) + font.width(Component.literal(" ").withStyle(pen));
                }
                y += LINE + 1;
            }
        }
    }

    /** The filing strip at the foot of a printed page: code, revision and copy number in half-size print, a barcode. */
    private void controlStrip(GuiGraphics g) {
        int y = H - 30;
        g.fill(M, y, W - M, y + 1, RULE);
        small(g, text("code"), M, y + 4, LABEL);
        small(g, text("revision"), M, y + 13, LABEL);
        String bars = "1101001110100110110101";
        int x = W - M - 30;
        for (int i = 0; i < bars.length(); i++) {
            if (bars.charAt(i) == '1') g.fill(x + i, y + 4, x + i + 1, y + 21, INK);
        }
    }

    /** Screen controls under the sheet, not print on it: the arrows and the page count. */
    private void chrome(GuiGraphics g, double sx, double sy) {
        int y = SHEET_H + 6;
        String count = (page + 1) + " / " + EvacuationNotice.pageCount();
        g.drawString(font, count, W / 2 - font.width(count) / 2, y, CHROME_TEXT, true);
        if (page > 0) arrow(g, W / 2 - 30, y, false, overArrow(sx, sy, false));
        if (page < EvacuationNotice.pageCount() - 1) arrow(g, W / 2 + 24, y, true, overArrow(sx, sy, true));
    }

    private void arrow(GuiGraphics g, int x, int y, boolean forward, boolean hover) {
        int colour = hover ? CHROME_HOVER : CHROME_TEXT;
        for (int i = 0; i < 4; i++) {
            int span = 4 - i;
            int column = forward ? x + i : x + 6 - i;
            g.fill(column, y + 4 - span, column + 1, y + 4 + span, colour);
        }
    }

    private boolean overArrow(double sx, double sy, boolean forward) {
        int x = forward ? W / 2 + 22 : W / 2 - 32, y = SHEET_H + 4;
        return sx >= x && sx < x + 11 && sy >= y && sy < y + 12;
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        g.blit(texture, x, y, w, h, 0, 0, texW, texH, texW, texH);
        RenderSystem.disableBlend();
    }

    private void turn(int delta) {
        int next = Math.max(0, Math.min(EvacuationNotice.pageCount() - 1, page + delta));
        if (next == page) return;
        page = next;
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        double sx = (mouseX - originX) / scale, sy = (mouseY - originY) / scale;
        if (button == 0 && page > 0 && overArrow(sx, sy, false)) {
            turn(-1);
            return true;
        }
        if (button == 0 && page < EvacuationNotice.pageCount() - 1 && overArrow(sx, sy, true)) {
            turn(1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> { turn(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> { turn(1); return true; }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }
}
