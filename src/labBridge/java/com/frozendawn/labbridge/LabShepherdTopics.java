package com.frozendawn.labbridge;

import com.frozendawn.data.WinConditionState;
import com.frozendawn.shepherd.ShepherdIntents;
import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTopics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** The Aurora Topics Check buttons that no vanilla command reaches. Excluded from the distributed jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabShepherdTopics {
    private LabShepherdTopics() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var topics = Commands.literal("topics");
        topics.then(Commands.literal("conspiracy").executes(c -> {
            var state = WinConditionState.get(c.getSource().getServer());
            state.setConspiracyDiscovered(!state.isConspiracyDiscovered());
            c.getSource().sendSuccess(() -> Component.literal("conspiracy found: " + state.isConspiracyDiscovered()), false);
            return 1;
        }));
        topics.then(Commands.literal("aftert5").executes(c -> {
            var data = ShepherdSavedData.get(c.getSource().getServer());
            data.flag("t5_done", !data.flag("t5_done"));
            c.getSource().sendSuccess(() -> Component.literal("after T5: " + data.flag("t5_done")), false);
            return 1;
        }));
        topics.then(Commands.literal("forget").executes(c -> {
            var data = ShepherdSavedData.get(c.getSource().getServer());
            for (var intent : ShepherdIntents.intents()) {
                data.flag(ShepherdTopics.HEARD + intent.id(), false);
                data.flag(ShepherdTopics.PAID + intent.id(), false);
            }
            c.getSource().sendSuccess(() -> Component.literal("the topics are new, and pay again"), false);
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("fdlab").requires(source -> source.hasPermission(2)).then(topics));
    }
}
