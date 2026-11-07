package com.frozendawn.labbridge;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.shepherd.ShepherdIntents;
import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTopics;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** The Aurora Advice Check buttons that no vanilla command reaches. Excluded from the distributed jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabShepherdAdvice {
    private static final List<String> MOMENTS = List.of("hologram_seen", "hologram_egg", "reacted_hunter_burn", "clovis_said");
    private static final List<String> DEEDS = List.of("offer_ready", "caught", "gag");
    private static final List<String> OFFERS = List.of("frostbitten", "frostmite", "hollow", "hunter", "mimic", "architect");

    private LabShepherdAdvice() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var advice = Commands.literal("advice");
        // Only the running game's value: it is not written to the config file.
        advice.then(Commands.literal("erased").executes(c -> {
            boolean erased = !FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get();
            FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.set(erased);
            c.getSource().sendSuccess(() -> Component.literal("erased: " + erased), false);
            return 1;
        }));
        advice.then(Commands.literal("aftert5").executes(c -> {
            var data = ShepherdSavedData.get(c.getSource().getServer());
            data.flag("t5_done", !data.flag("t5_done"));
            c.getSource().sendSuccess(() -> Component.literal("after T5: " + data.flag("t5_done")), false);
            return 1;
        }));
        advice.then(Commands.literal("forget").executes(c -> {
            var data = ShepherdSavedData.get(c.getSource().getServer());
            for (var intent : ShepherdIntents.intents()) {
                if ("orsa".equals(intent.menu())) continue;
                data.flag(ShepherdTopics.HEARD + intent.id(), false);
                data.flag(ShepherdTopics.PAID + intent.id(), false);
            }
            MOMENTS.forEach(flag -> data.flag(flag, false));
            for (String mob : OFFERS) {
                data.flag("offer_saw_" + mob, false);
                data.flag("offer_made_" + mob, false);
            }
            DEEDS.forEach(deed -> data.deedReady(deed, 0));
            c.getSource().sendSuccess(() -> Component.literal("the advice is new, and her moments can happen again"), false);
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("fdlab").requires(source -> source.hasPermission(2)).then(advice));
    }
}
