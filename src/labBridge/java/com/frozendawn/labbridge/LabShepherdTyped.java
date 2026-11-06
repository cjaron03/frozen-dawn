package com.frozendawn.labbridge;

import com.frozendawn.shepherd.ShepherdPrompt;
import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTalk;
import com.frozendawn.shepherd.ShepherdTopics;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Every typed line and how she took it, for the owner's blind column and for what to write next. Excluded from the distributed jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabShepherdTyped {
    private static final List<String> TOPICS = List.of("what_is_orsa", "what_happened", "exodus", "anyone_out_there", "who_is_kevin");

    private LabShepherdTyped() { }

    /** The Aurora Chat Check buttons: a scene waits for a right-click, and what she has been told can be untold. */
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var chat = Commands.literal("chat");
        chat.then(act("t2", "T2 is waiting", data -> data.promptNode(ShepherdPrompt.THE_OTHERS)));
        chat.then(act("t4", "T4 is waiting", data -> data.promptNode(ShepherdPrompt.THE_SWITCH)));
        chat.then(act("forgetcrash", "she has never crashed", data -> data.flag("crash_seen", false)));
        chat.then(act("forgettopics", "the topics are new", data -> TOPICS.forEach(topic -> data.flag(ShepherdTopics.HEARD + topic, false))));
        event.getDispatcher().register(Commands.literal("fdlab").requires(source -> source.hasPermission(2)).then(chat));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> act(String name, String done, Consumer<ShepherdSavedData> change) {
        return Commands.literal(name).executes(c -> {
            change.accept(ShepherdSavedData.get(c.getSource().getServer()));
            c.getSource().sendSuccess(() -> Component.literal(done), false);
            return 1;
        });
    }

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
