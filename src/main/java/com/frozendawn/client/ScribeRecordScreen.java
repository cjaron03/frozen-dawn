package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.item.ScribeRecordContents;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * One page of §9.4b field notes. Raw Thaeven without a translator; with one, each reconstruction above its source.
 * The page shows exactly what was frozen into the item and never asks the server for current beliefs.
 */
public final class ScribeRecordScreen extends Screen {
    private static final int FADE_TICKS = 14, LINE_DELAY = 8;
    private final ScribeRecordContents contents;
    private final boolean translated;
    private int age;

    public ScribeRecordScreen(ScribeRecordContents contents, boolean translated) {
        super(Component.translatable("screen.frozendawn.scribe_record"));
        this.contents = contents;
        this.translated = translated;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int w = Math.min(320, width - 24), textWidth = w - 24;
        List<Entry> entries = entries(textWidth);
        int h = Math.min(height - 24, 52 + entries.stream().mapToInt(e -> e.lines().size() * 10 + 4).sum());
        int x = (width - w) / 2, y = (height - h) / 2;
        graphics.fill(x, y, x + w, y + h, 0xFFF0E9D7);
        graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFDBD0B7);
        graphics.drawString(font, Component.translatable(translated
                ? "screen.frozendawn.thaeven_archive.reconstruction" : "screen.frozendawn.thaeven_archive.raw"),
                x + 12, y + 10, 0xFF625947, false);
        graphics.drawString(font, Component.literal(ScribeRecordContents.SUBJECT), x + 12, y + 26, 0xFF28231D, false);
        int lineY = y + 42;
        for (int i = 0; i < entries.size(); i++) {
            int alpha = alpha(i, partialTick);
            for (var line : entries.get(i).lines()) {
                if (lineY + 10 <= y + h - 6) graphics.drawString(font, line.text(), x + 12, lineY, alpha << 24 | line.color(), false);
                lineY += 10;
            }
            lineY += 4;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same flat dim as the archive keeps the Minecraft font pixel-sharp.
        graphics.fill(0, 0, width, height, 0x78000000);
    }

    /** Lines surface one at a time unless reduced ink animation is enabled. */
    private int alpha(int index, float partialTick) {
        if (FrozenDawnClientConfig.REDUCED_THAEVEN_INK_ANIMATION.get()) return 0xFF;
        float local = (age + partialTick - index * LINE_DELAY) / FADE_TICKS;
        return Math.max(8, Math.round(Mth.clamp(local, 0, 1) * 0xFF));
    }

    private List<Entry> entries(int textWidth) {
        var result = new ArrayList<Entry>();
        for (var note : contents.lines()) {
            var lines = new ArrayList<Line>();
            if (translated) font.split(note.translated(), textWidth).forEach(l -> lines.add(new Line(l, 0x28231D)));
            font.split(Component.literal(note.thaeven()), textWidth).forEach(l -> lines.add(new Line(l, translated ? 0x8A806C : 0x4B5260)));
            result.add(new Entry(lines));
        }
        return result;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Line(FormattedCharSequence text, int color) { }
    private record Entry(List<Line> lines) { }
}
