package com.frozendawn.labbridge;

import com.frozendawn.shepherd.ShepherdTalk;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Every typed line and how she took it, for the owner's blind column and for what to write next. Excluded from the distributed jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabShepherdTyped {
    private LabShepherdTyped() { }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("frozendawn.labBridge")) return;
        Path file = event.getServer().getServerDirectory().resolve("lab-bridge/aurora-typed.jsonl");
        ShepherdTalk.typedListener = line -> {
            var json = new JsonObject();
            json.addProperty("text", line.text());
            json.addProperty("band", line.band());
            json.addProperty("first", line.first());
            json.addProperty("firstScore", line.firstScore());
            json.addProperty("second", line.second());
            json.addProperty("secondScore", line.secondScore());
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("[LabShepherdTyped] Could not write " + file + ": " + e);
            }
        };
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        ShepherdTalk.typedListener = null;
    }
}
