package com.frozendawn.labbridge;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Exact-world guard for the disposable Blind fixture; excluded from release jars. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabBlindReplay {
    private static final java.util.Map<String, String> WORLDS = java.util.Map.of(
            "first", "Blind Architect Lab",
            "appearance", "Blind Architect 01 Appearance",
            "audio", "Blind Architect 02 Idle Audio",
            "echo_open", "Blind Architect 03 Open Echo",
            "echo_sealed", "Blind Architect 04 Sealed Echo",
            "echo_wool", "Blind Architect 05 Wool Echo",
            "vibration_connected", "Blind Architect 08 Connected Supports",
            "vibration_disconnected", "Blind Architect 09 Disconnected Supports",
            "vibration_lab", "Blind Architect Reusable Lab",
            "natural", LabBlindNatural.WORLD);
    private LabBlindReplay() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("erased_access").executes(c -> LabBlindErased.access(c.getSource())))
                .then(Commands.literal("erased_start").executes(c -> LabBlindErased.start(c.getSource())))
                .then(Commands.literal("natural_access").executes(c -> LabBlindNatural.access(c.getSource())))
                .then(Commands.literal("natural_prepare").executes(c -> LabBlindNatural.prepare(c.getSource())))
                .then(Commands.literal("natural_start").executes(c -> LabBlindNatural.start(c.getSource())))
                .then(Commands.literal("natural_stop").executes(c -> LabBlindNatural.stop(c.getSource())))
                .then(Commands.literal("natural_fast_wait").executes(c -> LabBlindNatural.fastWait(c.getSource())))
                .then(Commands.literal("natural_fast_repeat").executes(c -> LabBlindNatural.fastRepeat(c.getSource())))
                .then(Commands.literal("natural_fast_play").executes(c -> LabBlindNatural.fastPlay(c.getSource())))
                .then(Commands.literal("natural_arrival_play").executes(c -> LabBlindNatural.arrivalPlay(c.getSource())))
                .then(Commands.literal("blind_base_start").executes(c -> LabBlindBase.start(c.getSource())))
                .then(Commands.literal("blind_base_tick").executes(c -> LabBlindBase.tick(c.getSource())))
                .then(Commands.literal("blind_base_archived").executes(c -> LabBlindBase.archived(c.getSource())))
                .then(Commands.literal("cave_archived").executes(c -> LabBlindCave.archived(c.getSource())))
                .then(Commands.literal("cave_load").executes(c -> LabBlindCave.load(c.getSource())))
                .then(Commands.literal("cave_start").executes(c -> LabBlindCave.start(c.getSource())))
                .then(Commands.literal("cave_tick").executes(c -> LabBlindCave.tick(c.getSource())))
                .then(Commands.literal("cave_stop").executes(c -> LabBlindCave.stop(c.getSource())))
                .then(Commands.literal("channels_start").executes(c -> LabBlindChannels.start(c.getSource())))
                .then(Commands.literal("channels_tick").executes(c -> LabBlindChannels.tick(c.getSource())))
                .then(Commands.literal("channels_archived").executes(c -> LabBlindChannels.archived(c.getSource())))
                .then(Commands.literal("channels_retry_ready").executes(c -> LabBlindChannels.retryReady(c.getSource())))
                .then(Commands.literal("channels_input_retry_ready").executes(c -> LabBlindChannels.inputRetryReady(c.getSource())))
                .then(Commands.literal("channels_fixed_retry_ready").executes(c -> LabBlindChannels.fixedRetryReady(c.getSource())))
                .then(Commands.literal("channels_crouch_ready_retry").executes(c -> LabBlindChannels.crouchReadyRetry(c.getSource())))
                .then(Commands.literal("channels_crouch_repeat_ready").executes(c -> LabBlindChannels.crouchRepeatReady(c.getSource())))
                .then(Commands.literal("channels_material_ready").executes(c -> LabBlindChannels.materialReady(c.getSource())))
                .then(Commands.literal("channels_hearing_qualified_ready").executes(c -> LabBlindChannels.hearingQualifiedReady(c.getSource())))
                .then(Commands.literal("breach_ready").executes(c -> LabBlindDecoy.breachReady(c.getSource())))
                .then(Commands.literal("protected_wall_ready").executes(c -> LabBlindDecoy.protectedWallReady(c.getSource())))
                .then(Commands.literal("griefing_ready").executes(c -> LabBlindDecoy.griefingReady(c.getSource())))
                .then(Commands.literal("melee_ready").executes(c -> LabBlindDecoy.meleeReady(c.getSource())))
                .then(Commands.literal("melee_range_ready").executes(c -> LabBlindDecoy.meleeRangeReady(c.getSource())))
                .then(Commands.literal("melee_range_repeat_ready").executes(c -> LabBlindDecoy.meleeRangeRepeatReady(c.getSource())))
                .then(Commands.literal("unload_repeat_prepare").executes(c -> LabBlindUnload.repeatPrepare(c.getSource())))
                .then(Commands.literal("reconnect_clock_prepare").executes(c -> LabBlindReconnect.clockPrepare(c.getSource())))
                .then(Commands.literal("reconnect_repeat_prepare").executes(c -> LabBlindReconnect.repeatPrepare(c.getSource())))
                .then(Commands.literal("reconnect_prepare").executes(c -> LabBlindReconnect.prepare(c.getSource())))
                .then(Commands.literal("reconnect_publish").executes(c -> LabBlindReconnect.publish(c.getSource())))
                .then(Commands.literal("reconnect_stage").executes(c -> LabBlindReconnect.stage(c.getSource())))
                .then(Commands.literal("reconnect_start").executes(c -> LabBlindReconnect.start(c.getSource())))
                .then(Commands.literal("reconnect_restage").executes(c -> LabBlindReconnect.restage(c.getSource())))
                .then(Commands.literal("reconnect_resume").executes(c -> LabBlindReconnect.resume(c.getSource())))
                .then(Commands.literal("attention_prepare").executes(c -> LabBlindAttention.prepare(c.getSource())))
                .then(Commands.literal("attention_repeat_prepare").executes(c -> LabBlindAttention.repeatPrepare(c.getSource())))
                .then(Commands.literal("attention_safe_prepare").executes(c -> LabBlindAttention.safePrepare(c.getSource())))
                .then(Commands.literal("attention_publish").executes(c -> LabBlindAttention.publish(c.getSource())))
                .then(Commands.literal("attention_stage").executes(c -> LabBlindAttention.stage(c.getSource())))
                .then(Commands.literal("attention_start").executes(c -> LabBlindAttention.start(c.getSource())))
                .then(Commands.literal("lan_repeat_prepare").executes(c -> LabBlindLan.repeatPrepare(c.getSource())))
                .then(Commands.literal("lan_prepare").executes(c -> LabBlindLan.prepare(c.getSource())))
                .then(Commands.literal("lan_publish").executes(c -> LabBlindLan.publish(c.getSource())))
                .then(Commands.literal("lan_stage").executes(c -> LabBlindLan.stage(c.getSource())))
                .then(Commands.literal("lan_start").executes(c -> LabBlindLan.start(c.getSource())))
                .then(Commands.literal("save_prepare").executes(c -> LabBlindPersistence.prepare(c.getSource())))
                .then(Commands.literal("save_resume").executes(c -> LabBlindPersistence.resume(c.getSource())))
                .then(Commands.literal("save_start").executes(c -> LabBlindPersistence.start(c.getSource())))
                .then(Commands.literal("stale_prepare").executes(c -> LabBlindStale.prepare(c.getSource())))
                .then(Commands.literal("stale_start").executes(c -> LabBlindStale.start(c.getSource())))
                .then(Commands.literal("unload_prepare").executes(c -> LabBlindUnload.prepare(c.getSource())))
                .then(Commands.literal("unload_start").executes(c -> LabBlindUnload.start(c.getSource())))
                .then(Commands.literal("melee_obstruction_ready").executes(c -> LabBlindDecoy.meleeObstructionReady(c.getSource())))
                .then(Commands.literal("heat_ready").executes(c -> LabBlindDecoy.heatReady(c.getSource())))
                .then(Commands.literal("explosion_ready").executes(c -> LabBlindDecoy.explosionReady(c.getSource())))
                .then(Commands.literal("bell_ready").executes(c -> LabBlindDecoy.bellReady(c.getSource())))
                .then(Commands.literal("decoy_equip").executes(c -> LabBlindDecoy.equip(c.getSource())))
                .then(Commands.literal("door_contact_repeat_ready").executes(c -> LabBlindDecoy.doorContactRepeatReady(c.getSource())))
                .then(Commands.literal("door_contact_ready").executes(c -> LabBlindDecoy.doorContactReady(c.getSource())))
                .then(Commands.literal("machine_ready").executes(c -> LabBlindDecoy.machineReady(c.getSource())))
                .then(Commands.literal("door_repeat_ready").executes(c -> LabBlindDecoy.doorRepeatReady(c.getSource())))
                .then(Commands.literal("door_ready").executes(c -> LabBlindDecoy.doorReady(c.getSource())))
                .then(Commands.literal("decoy_contact_ready").executes(c -> LabBlindDecoy.contactReady(c.getSource())))
                .then(Commands.literal("decoy_repeat_ready").executes(c -> LabBlindDecoy.repeatReady(c.getSource())))
                .then(Commands.literal("decoy_ready").executes(c -> LabBlindChannels.decoyReady(c.getSource())))
                .then(Commands.literal("decoy_start").executes(c -> LabBlindDecoy.start(c.getSource())))
                .then(Commands.literal("decoy_tick").executes(c -> LabBlindDecoy.tick(c.getSource())))
                .then(Commands.literal("channels_mining_ready").executes(c -> LabBlindChannels.miningReady(c.getSource())))
                .then(Commands.literal("channels_hearing_ready").executes(c -> LabBlindChannels.hearingReady(c.getSource())))
                .then(Commands.literal("channels_echo_motion_ready").executes(c -> LabBlindChannels.echoMotionReady(c.getSource())))
                .then(Commands.literal("behavior_isolate").executes(c -> LabBlindIsolation.prepare(c.getSource())))
                .then(Commands.literal("behavior_start").executes(c -> LabBlindBehavior.start(c.getSource())))
                .then(Commands.literal("behavior_record").executes(c -> LabBlindBehavior.record(c.getSource())))
                .then(Commands.literal("behavior_finish").executes(c -> LabBlindBehavior.finish(c.getSource())))
                .then(Commands.literal("behavior_archived").executes(c -> LabBlindBehavior.archived(c.getSource())))
                .then(Commands.literal("workbench_access").executes(c -> LabBlindWorkbench.access(c.getSource())))
                .then(Commands.literal("workbench_check").executes(c -> LabBlindWorkbench.check(c.getSource())))
                .then(Commands.literal("workbench_clear").executes(c -> LabBlindWorkbench.clearScene(c.getSource())))
                .then(Commands.literal("workbench_sample").executes(c -> LabBlindWorkbench.sample(c.getSource())))
                .then(Commands.literal("workbench_archive").executes(c -> LabBlindWorkbench.archive(c.getSource())))
                .then(Commands.literal("workbench_archived").executes(c -> LabBlindWorkbench.archived(c.getSource())))
                .then(Commands.literal("blind_vibration").executes(c -> LabBlindVibration.sample(c.getSource())))
                .then(Commands.literal("blind_access").executes(c -> allowed(c.getSource(), "first") ? 1 : 0)
                        .then(Commands.argument("fixture", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> allowed(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "fixture")) ? 1 : 0)))
                .then(Commands.literal("blind_load").executes(c -> load(c.getSource(), "first"))
                        .then(Commands.argument("fixture", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> load(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "fixture"))))));
    }
    static boolean allowed(CommandSourceStack source, String fixture) {
        // A tagged native fixture exercises the actual commands without relaxing integrated-world guards.
        if (fixture.equals("vibration_lab") && source.getServer() instanceof net.minecraft.gametest.framework.GameTestServer
                && source.getEntity() instanceof ServerPlayer nativePlayer && nativePlayer.getTags().contains("blind_workbench_fixture")) return true;
        return !FMLEnvironment.production && Boolean.getBoolean("frozendawn.labBridge")
                && source.getServer().getWorldData().getLevelName().equals(WORLDS.get(fixture))
                && source.getServer().getPlayerList().getPlayerCount() == 1
                && source.getEntity() instanceof ServerPlayer player
                && player.serverLevel() == source.getServer().overworld();
    }
    private static int load(CommandSourceStack source, String fixture) {
        if (!allowed(source, fixture) || !(source.getEntity() instanceof ServerPlayer player) || !player.isCreative()) return 0;
        var board = source.getServer().getScoreboard(); var objective = board.getObjective(fixture.equals("vibration_lab") ? "blind_vib" : "blind_lab");
        if (objective == null) return 0;
        var stage = board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#stage"), objective);
        if (stage == null || stage.value() != 0) return 0;
        // Only two fresh fixture chunks; never retain them or load encounter terrain during a view.
        source.getServer().overworld().getChunk(0, 0);
        source.getServer().overworld().getChunk(1, 0);
        if (fixture.equals("vibration_lab")) {
            source.getLevel().getChunk(0, -1); source.getLevel().getChunk(1, -1);
        }
        return 1;
    }
}
