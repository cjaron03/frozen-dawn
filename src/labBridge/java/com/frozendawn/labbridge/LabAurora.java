package com.frozendawn.labbridge;

import com.frozendawn.aurora.AuroraEvents;
import com.frozendawn.data.ApocalypseState;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Disposable sky review only. This command is excluded from the distributed jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabAurora {
    private LabAurora() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        var root = Commands.literal("aurora");
        root.then(Commands.literal("preview").executes(c -> control(c.getSource(), "preview")));
        root.then(Commands.literal("natural").executes(c -> control(c.getSource(), "natural")));
        root.then(Commands.literal("status").executes(c -> control(c.getSource(), "status")));
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2)).then(root));
    }
    private static int control(CommandSourceStack source, String action) {
        if (!Boolean.getBoolean("frozendawn.labBridge")
                || !java.util.Set.of("Aurora Sky Check", "Aurora Terrain Check").contains(source.getServer().getWorldData().getLevelName())
                || !(source.getEntity() instanceof ServerPlayer player)
                || !player.getTags().contains("aurora_lab")) return 0;
        var level = source.getLevel();
        if (!action.equals("status")) AuroraEvents.preview(level, action.equals("preview"));
        var event = AuroraEvents.snapshot(level);
        var state = ApocalypseState.get(source.getServer());
        player.sendSystemMessage(Component.literal("Aurora: phase=" + state.getPhase()
                + " preview=" + event.preview() + " seed=" + event.seed()
                + " start=" + event.startTick() + " duration=" + event.durationTicks()
                + " strength=" + event.strength() + " gameTime=" + level.getGameTime()
                + " dayTime=" + level.getDayTime() + " rain=" + level.getRainLevel(1)
                + " thunder=" + level.getThunderLevel(1)));
        return 1;
    }
}
