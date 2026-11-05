package com.frozendawn.shepherd;

import com.frozendawn.shepherd.ShepherdAffinity.Say;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;

/** The ambient file format: what she says unasked, by trigger. Text stays in lang keys. A bad pool is logged and skipped, never fatal. */
public final class ShepherdAmbient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ShepherdAmbient() {}

    public enum Trigger {
        SILENCE,
        NIGHT,
        COLD,
        AFTER_FIGHT,
        IMAGINED_AURORA,
        LOOKED_STORAGE,
        LOOKED_HEATER,
        LOOKED_TERMINAL,
        LOOKED_ANIMAL;

        static Trigger looked(ShepherdCuriosity.Kind kind) {
            return switch (kind) {
                case STORAGE -> LOOKED_STORAGE;
                case HEATER -> LOOKED_HEATER;
                case TERMINAL -> LOOKED_TERMINAL;
                case ANIMAL -> LOOKED_ANIMAL;
            };
        }
    }

    public record Pool(Trigger trigger, int cooldownTicks, Mood mood, List<Say> variants) {}

    public static List<Pool> parse(JsonElement file) {
        var pools = new ArrayList<Pool>();
        for (JsonElement entry : file.getAsJsonObject().getAsJsonArray("pools")) {
            try {
                pools.add(pool(entry.getAsJsonObject()));
            } catch (RuntimeException e) {
                LOGGER.error("Skipping ambient pool {}", entry, e);
            }
        }
        return List.copyOf(pools);
    }

    private static Pool pool(JsonObject json) {
        var variants = new ArrayList<Say>();
        for (JsonElement variant : json.getAsJsonArray("variants")) {
            var say = variant.getAsJsonObject();
            variants.add(new Say(say.get("line").getAsString(), say.has("heavy") ? say.get("heavy").getAsString() : null));
        }
        if (variants.isEmpty()) throw new IllegalArgumentException("No variants");
        var trigger = Trigger.valueOf(json.get("trigger").getAsString().toUpperCase(Locale.ROOT));
        return new Pool(trigger, json.get("cooldown").getAsInt(), Mood.byName(json.get("mood").getAsString()), List.copyOf(variants));
    }
}
