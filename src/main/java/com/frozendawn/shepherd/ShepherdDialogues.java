package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/** Every node in {@code data/<namespace>/shepherd/dialogue}. A bad file is logged and skipped, never fatal. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdDialogues extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<String, Node> nodes = Map.of();

    private ShepherdDialogues() {
        super(new Gson(), "shepherd/dialogue");
    }

    @SubscribeEvent public static void addListener(AddReloadListenerEvent event) {
        event.addListener(new ShepherdDialogues());
    }

    public static Map<String, Node> nodes() {
        return nodes;
    }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Node> loaded = new HashMap<>();
        files.forEach((id, file) -> {
            try {
                for (Node node : ShepherdDialogue.parse(file)) loaded.put(node.id(), node);
            } catch (RuntimeException e) {
                LOGGER.error("Skipping dialogue file {}", id, e);
            }
        });
        load(loaded);
    }

    /** A live conversation holds nodes from the old set, so it ends here. */
    static void load(Map<String, Node> loaded) {
        nodes = Map.copyOf(loaded);
        ShepherdTalk.dialoguesReloaded();
    }
}
