package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.ScribeRecordContents;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * One slate of §9.4b field notes. Raw Thaeven without a translator; with one, each reconstruction above its source.
 * The page shows exactly what was frozen into the item and never asks the server for current beliefs.
 * Each note is scratched in with the Scribe's own writing sound, then (translated) its Thaeven fractures into the
 * reconstruction through the archive's ink. Certainty sets the pace: "Always" lands fast, "Perhaps" keeps its
 * ghosts, and "Unsettled" never stops wavering. A click finishes it; reduced ink animation shows it settled.
 * The subject's face is sketched in the corner either way; without a translator a chalk pictogram marks what each
 * line is about, never what was concluded.
 */
public final class ScribeRecordScreen extends Screen {
    private static final float SCRATCH_RATE = 2.0F;
    private static final int SCRATCH_GAP = 4, INK_DELAY = 2, STROKE = 7;
    private static final int WOOD = 0xFF6B4F35, SLATE = 0xFF3B4147, HEADING = 0xFFAEB4B8, CHALK = 0xE6E4DC,
            SOURCE_CHALK = 0x8F979D, RAW_CHALK = 0xD3D7D9, COLD_GHOST = 0x8FB6C9, WARM_GHOST = 0xC9A27E;
    private static final int PICTOGRAM = 14;
    private final boolean translated;
    private final Optional<UUID> subject;
    private final List<Note> notes = new ArrayList<>();
    private final int end;
    private int age;

    public ScribeRecordScreen(ScribeRecordContents contents, boolean translated) {
        super(Component.translatable("screen.frozendawn.scribe_record"));
        this.translated = translated;
        this.subject = contents.subject();
        int start = 0, last = 0;
        for (var line : contents.lines()) {
            int scratch = Mth.ceil(line.thaeven().length() / SCRATCH_RATE);
            int ink = start + scratch + INK_DELAY;
            notes.add(new Note(line.pattern(), line.thaeven(), line.translated().getString(), line.certainty(), start, scratch, ink,
                    inkTicks(line.certainty())));
            last = Math.max(last, translated ? ink + inkTicks(line.certainty()) : start + scratch);
            start += scratch + SCRATCH_GAP;
        }
        end = last;
        if (FrozenDawnClientConfig.REDUCED_THAEVEN_INK_ANIMATION.get()) age = end;
    }

    private static int inkTicks(String certainty) {
        return switch (certainty) {
            case "ALWAYS" -> 30;
            case "HEDGED", "INCONCLUSIVE" -> 100;
            default -> 60;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (age < end) {
            for (var note : notes) {
                int into = age - note.scratchStart();
                if (into >= 0 && into < note.scratchTicks() && into % STROKE == 0) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SCRIBE_WRITE.get(),
                            0.9F + (float) Math.random() * 0.15F, 1.0F));
                }
            }
        }
        age++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (age < end) {
            age = end;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int w = Math.min(320, width - 24), textWidth = w - 24 - (translated ? 0 : PICTOGRAM);
        int lineCount = 0;
        for (var note : notes) lineCount += slots(note, textWidth);
        int h = Math.min(height - 24, 52 + lineCount * 10 + notes.size() * 4);
        int x = (width - w) / 2, y = (height - h) / 2;
        graphics.fill(x, y, x + w, y + h, WOOD);
        graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, SLATE);
        graphics.drawString(font, Component.translatable(translated
                ? "screen.frozendawn.thaeven_archive.reconstruction" : "screen.frozendawn.thaeven_archive.raw"),
                x + 12, y + 10, HEADING, false);
        graphics.drawString(font, Component.literal(ScribeRecordContents.SUBJECT), x + 12, y + 26, 0xFF000000 | CHALK, false);
        ScribeChalk.portrait(graphics, subject, x + w - 36, y + 10, 3);
        float t = Math.min(age + partialTick, end);
        int lineY = y + 42, bottom = y + h - 6;
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            if (translated) lineY = drawInk(graphics, note, i, t, x + 12, lineY, textWidth, bottom, partialTick);
            if (!translated && t >= note.scratchStart() && lineY + 10 <= bottom) ScribeChalk.pictogram(graphics, note.pattern(), x + 12, lineY);
            lineY = drawScratch(graphics, note, t, x + 12 + (translated ? 0 : PICTOGRAM), lineY, textWidth, bottom) + 4;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Same flat dim as the archive keeps the Minecraft font pixel-sharp.
        graphics.fill(0, 0, width, height, 0x78000000);
    }

    private int slots(Note note, int textWidth) {
        int raw = font.split(Component.literal(note.thaeven()), textWidth).size();
        return translated ? raw + font.split(Component.literal(note.english()), textWidth).size() : raw;
    }

    /** The Thaeven written left to right, with a chalk nub at the stylus while it is still writing. */
    private int drawScratch(GuiGraphics graphics, Note note, float t, int x, int y, int textWidth, int bottom) {
        int full = font.split(Component.literal(note.thaeven()), textWidth).size();
        int shown = Mth.clamp(Mth.floor((t - note.scratchStart()) * SCRATCH_RATE), 0, note.thaeven().length());
        List<FormattedCharSequence> lines = font.split(Component.literal(note.thaeven().substring(0, shown)), textWidth);
        int color = 0xFF000000 | (translated ? SOURCE_CHALK : RAW_CHALK);
        for (int i = 0; i < Math.min(full, lines.size()); i++) {
            if (y + i * 10 + 10 <= bottom) graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        if (shown > 0 && shown < note.thaeven().length() && !lines.isEmpty()) {
            int row = Math.min(full, lines.size()) - 1, nubX = x + font.width(lines.get(row)) + 1, nubY = y + row * 10;
            if (nubY + 10 <= bottom) graphics.fill(nubX, nubY, nubX + 1, nubY + 8, 0xFF000000 | CHALK);
        }
        return y + full * 10;
    }

    /** The archive's ink, paced by certainty. "Perhaps" keeps faint ghosts; "Unsettled" keeps wavering. */
    private int drawInk(GuiGraphics graphics, Note note, int index, float t, int x, int y, int textWidth, int bottom,
                        float partialTick) {
        int full = font.split(Component.literal(note.english()), textWidth).size();
        if (t < note.inkStart()) return y + full * 10;
        float progress = Mth.clamp((t - note.inkStart()) / note.inkTicks(), 0.0F, 1.0F);
        String text = progress >= 1.0F ? note.english() : ThaevenInk.morph(note.thaeven(), note.english(), progress, index);
        boolean reduced = FrozenDawnClientConfig.REDUCED_THAEVEN_INK_ANIMATION.get();
        float unrest = (float) (1.0D - Mth.smoothstep(progress));
        if (!reduced && note.certainty().equals("INCONCLUSIVE")) unrest = Math.max(unrest, 0.4F);
        float ghosts = !reduced && note.certainty().equals("HEDGED") ? Math.max(unrest, 0.3F) : unrest;
        float wave = age + partialTick;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), textWidth);
        for (int i = 0; i < Math.min(full, lines.size()); i++) {
            int drawY = y + i * 10, row = index * 3 + i;
            if (drawY + 10 > bottom) break;
            int warpX = Math.round((Mth.sin(wave * 0.105F + row * 1.37F) * 3.0F
                    + Mth.sin(wave * 0.31F + row * 1.73F) * 1.5F) * unrest);
            int warpY = Math.round(Mth.sin(wave * 0.16F + row * 0.81F) * 1.25F * unrest);
            int spread = Math.max(1, Math.abs(warpX));
            int alpha = Math.round(90.0F * ghosts);
            if (alpha > 0) {
                graphics.drawString(font, lines.get(i), x - spread, drawY + warpY, alpha << 24 | COLD_GHOST, false);
                graphics.drawString(font, lines.get(i), x + spread, drawY - warpY, alpha << 24 | WARM_GHOST, false);
            }
            graphics.drawString(font, lines.get(i), x + warpX / 3, drawY, 0xFF000000 | CHALK, false);
        }
        return y + full * 10;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Note(String pattern, String thaeven, String english, String certainty, int scratchStart, int scratchTicks,
                        int inkStart, int inkTicks) { }
}
