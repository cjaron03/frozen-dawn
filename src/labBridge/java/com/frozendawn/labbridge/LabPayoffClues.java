package com.frozendawn.labbridge;

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

/** Payoff Clues Check: shortens the live Emergency EVA issue so its service toasts show. Excluded from the release jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabPayoffClues {
    public static final String WORLD = "Payoff Clues Check";
    private LabPayoffClues() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("payoff_clues")
                        .then(Commands.literal("eva_service")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 900))
                                        .executes(c -> service(c.getSource(),
                                                IntegerArgumentType.getInteger(c, "seconds")))))));
    }

    private static int service(CommandSourceStack source, int seconds) {
        if (FMLEnvironment.production || !Boolean.getBoolean("frozendawn.labBridge")
                || !source.getServer().getWorldData().getLevelName().equals(WORLD)
                || !(source.getEntity() instanceof ServerPlayer player)
                || !EmergencyEvaHandler.isWearingIssuedPiece(player)) return 0;
        var current = player.getData(ModAttachments.EMERGENCY_EVA);
        var shortened = current.copy();
        var tag = shortened.serializeNBT(player.registryAccess());
        tag.putInt("remainingTicks", Math.min(current.remainingTicks(), seconds * 20));
        tag.putInt("oxygenTicks", Math.min(current.oxygenTicks(), seconds * 20));
        shortened.deserializeNBT(player.registryAccess(), tag);
        player.setData(ModAttachments.EMERGENCY_EVA, shortened);
        return 1;
    }
}
