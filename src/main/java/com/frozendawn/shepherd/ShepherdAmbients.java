package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdAmbient.Pool;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
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

/** Every pool in {@code data/<namespace>/shepherd/ambient}. A bad file is logged and skipped, never fatal. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdAmbients extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<Trigger, List<Pool>> pools = Map.of();

    private ShepherdAmbients() {
        super(new Gson(), "shepherd/ambient");
    }

    @SubscribeEvent public static void addListener(AddReloadListenerEvent event) {
        event.addListener(new ShepherdAmbients());
    }

    static List<Pool> pools(Trigger trigger) {
        return pools.getOrDefault(trigger, List.of());
    }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Trigger, List<Pool>> loaded = new EnumMap<>(Trigger.class);
        files.forEach((id, file) -> {
            try {
                for (Pool pool : ShepherdAmbient.parse(file)) loaded.computeIfAbsent(pool.trigger(), trigger -> new ArrayList<>()).add(pool);
            } catch (RuntimeException e) {
                LOGGER.error("Skipping ambient file {}", id, e);
            }
        });
        pools = loaded;
    }
}
