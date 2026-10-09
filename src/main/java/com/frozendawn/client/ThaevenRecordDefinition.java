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
        return ThaevenInk.morph(raw, target, progress, page);
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
