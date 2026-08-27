package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.lore.ThaevenRecordId;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Immutable resource-driven record content with stable semantic segment IDs. */
public record ThaevenRecordDefinition(
        String title, List<String> rawPages,
        List<List<Segment>> translatedPages) {
    private static final Gson GSON = new Gson();

    public static ThaevenRecordDefinition load(ThaevenRecordId record) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "thaeven_records/"
                        + record.serializedName() + ".json");
        try (Reader reader = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(location).openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String title = root.get("title").getAsString();
            List<String> rawPages = GSON.fromJson(root.get("rawPages"),
                    new com.google.gson.reflect.TypeToken<List<String>>() { }
                            .getType());
            List<List<Segment>> pages = new ArrayList<>();
            for (var page : root.getAsJsonArray("translatedPages")) {
                List<Segment> segments = GSON.fromJson(
                        page.getAsJsonObject().get("segments"),
                        new com.google.gson.reflect.TypeToken<List<Segment>>() { }
                                .getType());
                pages.add(List.copyOf(segments));
            }
            return new ThaevenRecordDefinition(title, List.copyOf(rawPages),
                    List.copyOf(pages));
        } catch (Exception exception) {
            FrozenDawn.LOGGER.error("Unable to load Thaeven record {}",
                    record.serializedName(), exception);
            return new ThaevenRecordDefinition(record.serializedName(),
                    List.of("[record unavailable]"),
                    List.of(List.of(new Segment("error", "[record unavailable]",
                            Map.of(), 0, true))));
        }
    }

    public String translatedPage(int page, int semanticRevision) {
        if (translatedPages.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Segment segment : translatedPages.get(
                Math.floorMod(page, translatedPages.size()))) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(segment.resolve(semanticRevision));
        }
        return result.toString();
    }

    public String translatedPageMorphing(int page, int semanticRevision,
                                         float progress) {
        if (translatedPages.isEmpty()) {
            return "";
        }
        StringBuilder translated = new StringBuilder();
        for (Segment segment : translatedPages.get(
                Math.floorMod(page, translatedPages.size()))) {
            if (!translated.isEmpty()) {
                translated.append('\n');
            }
            translated.append(segment.resolve(semanticRevision));
        }
        String target = translated.toString();
        String raw = rawPages.get(Math.floorMod(page, rawPages.size()));
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

    public record Segment(String id, String baseText,
                          Map<String, String> semanticOverrides,
                          int revealTiming, boolean uncertainty) {
        public String resolve(int semanticRevision) {
            String best = baseText;
            int selected = -1;
            if (semanticOverrides != null) {
                for (Map.Entry<String, String> override
                        : semanticOverrides.entrySet()) {
                    try {
                        int revision = Integer.parseInt(override.getKey());
                        if (revision <= semanticRevision && revision > selected) {
                            selected = revision;
                            best = override.getValue();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return best;
        }
    }
}
