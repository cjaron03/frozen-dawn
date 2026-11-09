package com.frozendawn.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Shared Thaeven ink: raw words fracture into their reconstruction one at a time, in a stable scattered order. */
final class ThaevenInk {
    /** The glyphs a word passes through between its raw and its reconstruction. */
    static final String FRACTURE_GLYPHS = "⟟⟐ϟ·/";
    /** The overlays' ink (CH-37 mockups, 2026-11-09): the band's aqua and an ember for the ghosts, the line white. */
    private static final int COLD_GHOST = 0x60D8DF;
    private static final int WARM_GHOST = 0xE08A4E;
    private static final int LINE = 0xE7F7F7;

    private ThaevenInk() { }

    /** Every letter caught mid-fracture, in a stable order for the seed; spaces stay. */
    static String fracture(String text, int seed) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            result.append(Character.isWhitespace(c) ? c : FRACTURE_GLYPHS.charAt(
                    Math.floorMod(seed * 31 + index * 7, FRACTURE_GLYPHS.length())));
        }
        return result.toString();
    }

    /**
     * The archive's ghost warp on a dark overlay: a cold and a warm copy drift apart around the line, as
     * ThaevenLoreScreen draws a page re-inking. Unrest 1 is the full drift; the overlays never let it reach 0.
     */
    static void drawInked(GuiGraphics graphics, Font font, String text, int centerX, int y,
                          int age, int line, float unrest, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        int warpX = Math.round((Mth.sin(age * 0.105F + line * 1.37F) * 3.0F
                + Mth.sin(age * 0.31F + line * 1.73F) * 1.5F) * unrest);
        int warpY = Math.round(Mth.sin(age * 0.16F + line * 0.81F) * 1.25F * unrest);
        Component styled = Component.literal(text).withStyle(ChatFormatting.ITALIC);
        int x = centerX - font.width(styled) / 2;
        float ghost = alpha * (0.22F + 0.40F * unrest);
        graphics.drawString(font, styled, x + warpX - 1, y + warpY, argb(ghost, COLD_GHOST), false);
        graphics.drawString(font, styled, x - warpX + 1, y - warpY, argb(ghost, WARM_GHOST), false);
        graphics.drawString(font, styled, x + warpX / 3, y, argb(alpha, LINE), true);
    }

    private static int argb(float alpha, int rgb) {
        return Mth.clamp(Math.round(alpha * 255.0F), 4, 255) << 24 | rgb;
    }

    /** progress 0 shows the raw words, 1 the target; page varies the word order between pages or lines. */
    static String morph(String raw, String target, float progress, int page) {
        String[] sourceWords = raw.trim().split("\\s+");
        if (sourceWords.length == 0 || sourceWords[0].isEmpty()) {
            return target;
        }

        StringBuilder result = new StringBuilder(target.length());
        int wordIndex = 0;
        int cursor = 0;
        while (cursor < target.length()) {
            if (Character.isWhitespace(target.charAt(cursor))) {
                result.append(target.charAt(cursor++));
                continue;
            }
            int end = cursor + 1;
            while (end < target.length()
                    && !Character.isWhitespace(target.charAt(end))) {
                end++;
            }
            String targetWord = target.substring(cursor, end);
            String sourceWord = sourceWords[wordIndex % sourceWords.length];
            result.append(morphWord(sourceWord, targetWord, progress,
                    page, wordIndex));
            wordIndex++;
            cursor = end;
        }
        return result.toString();
    }

    private static String morphWord(String source, String target,
                                    float progress, int page, int wordIndex) {
        int hash = Integer.rotateLeft(
                (wordIndex + 1) * 0x45D9F3B, wordIndex & 15)
                ^ page * 0x9E3779B9;
        float wordStart = 0.10F
                + (Math.floorMod(hash, 1000) / 1000.0F) * 0.58F;
        float local = Mth.clamp((progress - wordStart) / 0.30F,
                0.0F, 1.0F);
        if (local <= 0.0F) {
            return source;
        }
        if (local >= 1.0F) {
            return target;
        }

        int length = Math.max(1, (int) Math.round(Mth.lerp(
                Mth.smoothstep(local), (double) source.length(),
                target.length())));
        String fractureGlyphs = FRACTURE_GLYPHS;
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            float characterStart = index / (float) Math.max(1, length) * 0.42F;
            float characterProgress = Mth.clamp(
                    (local - characterStart) / 0.58F, 0.0F, 1.0F);
            char sourceGlyph = source.charAt(Math.min(source.length() - 1,
                    Math.round(index * (source.length() - 1.0F)
                            / Math.max(1.0F, length - 1.0F))));
            char targetGlyph = target.charAt(Math.min(target.length() - 1,
                    Math.round(index * (target.length() - 1.0F)
                            / Math.max(1.0F, length - 1.0F))));
            if (characterProgress < 0.38F) {
                result.append(sourceGlyph);
            } else if (characterProgress < 0.72F) {
                result.append(fractureGlyphs.charAt(Math.floorMod(
                        hash + index * 7, fractureGlyphs.length())));
            } else {
                result.append(targetGlyph);
            }
        }
        return result.toString();
    }
}
