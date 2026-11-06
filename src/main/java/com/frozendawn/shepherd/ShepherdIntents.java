package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.miller.Embeddings;
import com.frozendawn.miller.Intent;
import com.frozendawn.miller.IntentIndex;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/** Every intent in {@code data/<namespace>/shepherd/intents}, and the table, read once. A bad file is logged and skipped, never fatal. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdIntents extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Embeddings table;
    private static List<Intent> intents = List.of();
    private static IntentIndex index = new IntentIndex(List.of(), Embeddings.none());

    private ShepherdIntents() {
        super(new Gson(), "shepherd/intents");
    }

    @SubscribeEvent public static void addListener(AddReloadListenerEvent event) {
        event.addListener(new ShepherdIntents());
    }

    public static IntentIndex index() {
        return index;
    }

    /** In file order by id, so the same files always give the same menu. */
    public static List<Intent> intents() {
        return intents;
    }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new ArrayList<Intent>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
                loaded.addAll(Intent.parse(file.getValue()));
            } catch (RuntimeException e) {
                LOGGER.error("Skipping intent file {}", file.getKey(), e);
            }
        });
        if (table == null) table = Embeddings.load();
        intents = List.copyOf(loaded);
        index = new IntentIndex(intents, table);
        ShepherdTalk.dialoguesReloaded();
    }
}
