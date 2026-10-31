package com.frozendawn.client;

import net.minecraft.util.Mth;

/** Shared Thaeven ink: raw words fracture into their reconstruction one at a time, in a stable scattered order. */
final class ThaevenInk {
    private ThaevenInk() { }

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
        String fractureGlyphs = "⟟⟐ϟ·/";
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
