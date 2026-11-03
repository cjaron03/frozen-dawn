package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.network.TerminusLedgerPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Whether the world's wands log terminations rather than transfers, and kill rather than discard. It turns on the
 * CH-18 trigger, a player's first death after the Heart falls with Maeve erased, never in an E11 world, and never back.
 * CH-18 itself is not built yet; when it is, this should read its flag instead of keeping its own.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class TerminusLedger extends SavedData {
    /** The client's mirror, read by the tooltip. */
    private static volatile boolean clientTurned;
    private boolean turned;

    /** The one-way rule, given whether the Heart has fallen, whether Maeve escaped (E11), and whether it already turned. */
    public static boolean turnsOnPlayerDeath(boolean heartFallen, boolean maeveEscaped, boolean turned) {
        return turned || heartFallen && !maeveEscaped;
    }

    public static boolean turned(MinecraftServer server) { return get(server).turned; }
    public static boolean clientTurned() { return clientTurned; }
    public static void receive(TerminusLedgerPayload payload) { clientTurned = payload.turned(); }
    public static void forgetClient() { clientTurned = false; }

    /** GameTests and the playtest command only: in play the switch never reverts. */
    public static void setForTest(MinecraftServer server, boolean turned) {
        var data = get(server);
        data.turned = turned;
        data.setDirty();
        PacketDistributor.sendToAllPlayers(new TerminusLedgerPayload(turned));
    }

    /** E11 is not built. Its escape flag plugs in here (CH-21). */
    private static boolean maeveEscaped(MinecraftServer server) { return false; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var server = player.getServer();
        var data = get(server);
        if (data.turned || !turnsOnPlayerDeath(PostMaeveWorldState.isErased(server), maeveEscaped(server), false)) return;
        data.turned = true;
        data.setDirty();
        PacketDistributor.sendToAllPlayers(new TerminusLedgerPayload(true));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new TerminusLedgerPayload(turned(player.getServer())));
    }

    private static TerminusLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TerminusLedger::new, TerminusLedger::load), FrozenDawn.MOD_ID + "_terminus");
    }

    private static TerminusLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new TerminusLedger();
        data.turned = tag.getBoolean("turned");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("turned", turned);
        return tag;
    }
}
