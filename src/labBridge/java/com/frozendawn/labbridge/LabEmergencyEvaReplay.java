package com.frozendawn.labbridge;

import com.frozendawn.data.EmergencyEvaState;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Disposable emergency EVA preview controls. Excluded from the release jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabEmergencyEvaReplay {
    private LabEmergencyEvaReplay() {}
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("emergency_eva")
                        .then(Commands.literal("access").executes(c -> allowed(c.getSource()) ? 1 : 0))
                        .then(Commands.literal("reserve")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                        .executes(c -> shorten(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))));
    }
    private static boolean allowed(CommandSourceStack source) {
        return !FMLEnvironment.production && Boolean.getBoolean("frozendawn.labBridge")
                && source.getServer().getWorldData().getLevelName().equals("Emergency EVA Respawn Lab")
                && source.getEntity() instanceof ServerPlayer;
    }
    private static int shorten(CommandSourceStack source, int seconds) {
        if (!allowed(source) || !(source.getEntity() instanceof ServerPlayer player)
                || !player.getTags().contains("emergency_eva_lab")
                || !EmergencyEvaHandler.isWearingIssuedPiece(player)) return 0;
        var current = player.getData(ModAttachments.EMERGENCY_EVA);
        player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(
                current.issue(), Math.min(current.remainingTicks(), seconds * 20)));
        return 1;
    }
}
