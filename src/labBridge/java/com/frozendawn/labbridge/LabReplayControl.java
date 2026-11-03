package com.frozendawn.labbridge;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Only the development base replay may pause at its handoff. Never included in the release jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabReplayControl {
    private LabReplayControl() { }
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("base_pause").executes(c -> control(c.getSource(), true)))
                .then(Commands.literal("base_resume").executes(c -> control(c.getSource(), false))));
    }
    private static int control(CommandSourceStack source, boolean pause) {
        var server = source.getServer();
        boolean fixture = server instanceof GameTestServer;
        if (!fixture && (!Boolean.getBoolean("frozendawn.labBridge")
                || !server.getWorldData().getLevelName().equals("MACS Base Bait"))) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player) || !player.getTags().contains("macs_base_player")) return 0;
        var board = server.getScoreboard(); var objective = board.getObjective("mpc");
        if (objective == null) return 0;
        var score = board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#stage"), objective);
        if (score == null || (pause ? score.value() != 112 && score.value() != 113 : score.value() != 102)) return 0;
        var manager = server.tickRateManager();
        if (pause) { manager.stopSprinting(); manager.stopStepping(); }
        else if (manager.isSprinting() || manager.isSteppingForward()) return 0;
        manager.setFrozen(pause);
        return 1;
    }
}
