package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TemperatureManager;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What she sees people do. {@link #saw} is the one door a deed comes through: she only knows what her sensors saw, so
 * she has to be up and in range, and the score is the world's, so anyone's deed counts.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdReactions {
    public static final int POLL_TICKS = 100;
    private static final String NAME_SHE_HATES = "shepherd";

    private ShepherdReactions() {}

    /** Her body, if she is awake and it is loaded. */
    static ShepherdEntity loaded(MinecraftServer server) {
        var data = ShepherdSavedData.get(server);
        if (data.stage() != Stage.AWAKE || data.entity() == null || data.dimension() == null) return null;
        var level = server.getLevel(data.dimension());
        return level != null && level.getEntity(data.entity()) instanceof ShepherdEntity she ? she : null;
    }

    /** Offline she sees nothing, and an unloaded body has no sensors. */
    private static ShepherdEntity watching(MinecraftServer server) {
        var she = loaded(server);
        return she == null || she.offline() ? null : she;
    }

    public static void saw(ServerPlayer player, Deed deed) {
        var server = player.getServer();
        var she = watching(server);
        if (she == null || she.level() != player.level() || player.distanceTo(she) > ShepherdPolicy.HEARD_BLOCKS) return;
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        if (now < data.deedReady(deed.name())) return;
        data.deedReady(deed.name(), now + deed.cooldownTicks());
        data.affinity(data.affinity() + deed.score());
        if (ShepherdTalk.live()) return;
        boolean wary = data.tier() == Tier.WARY;
        if (!wary) she.react(deed.mood());
        if (deed.line() == null) return;
        var line = wary ? new ShepherdLinePayload(deed.liked() ? ShepherdAffinity.WARY_LIKED : ShepherdAffinity.WARY_DISLIKED, Mood.RECORDING)
                : new ShepherdLinePayload(deed.line(), deed.mood());
        for (var heard : near((ServerLevel) she.level(), she)) PacketDistributor.sendToPlayer(heard, line);
    }

    private static List<ServerPlayer> near(ServerLevel level, ShepherdEntity she) {
        return level.getPlayers(player -> !player.isSpectator() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % POLL_TICKS != 0) return;
        var she = watching(server);
        if (she == null) return;
        var level = (ServerLevel) she.level();
        var near = near(level, she);
        if (near.isEmpty()) return;
        comeBack(server, near.getFirst());
        for (var player : near) look(level, player);
    }

    /** Her clock on you runs only while she can see someone, so being gone a day is one gap in it. */
    private static void comeBack(MinecraftServer server, ServerPlayer player) {
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        long last = data.lastNear();
        data.lastNear(now);
        if (last != ShepherdSavedData.NOT_SET && now - last >= ShepherdPolicy.DAY_TICKS) saw(player, Deed.LEFT_BEHIND);
    }

    private static void look(ServerLevel level, ServerPlayer player) {
        if (PlayerTickHandler.airFailing(player.getUUID())) saw(player, Deed.AIR_FAILING);
        var at = player.blockPosition();
        if (ShepherdPolicy.night(level.getDayTime()) && !level.isRaining() && level.canSeeSky(at)) saw(player, Deed.CLEAR_NIGHT);
        if (!TemperatureManager.isEnclosed(level, at)) return;
        float temperature = PlayerTickHandler.resolvedTemperature(player);
        if (temperature >= ShepherdPolicy.WARM_SHELTER_C && !HeaterRegistry.nearby(level, at, ShepherdPolicy.WARM_HEATER_BLOCKS, 1).isEmpty()) saw(player, Deed.WARM_SHELTER);
        if (temperature <= ShepherdPolicy.FROZEN_C && unlitHeaterNear(level, at)) saw(player, Deed.SHELTER_FROZE);
    }

    /** The registry only holds lit ones. A shelter is somewhere with a heater in it, or the vault would count on the walk out. */
    private static boolean unlitHeaterNear(ServerLevel level, BlockPos at) {
        int reach = ShepherdPolicy.FROZEN_HEATER_BLOCKS;
        for (var pos : BlockPos.betweenClosed(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))) {
            if (pos.distSqr(at) > reach * reach) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ThermalHeaterBlock && !state.getValue(ThermalHeaterBlock.LIT)) return true;
        }
        return false;
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if (!event.getRawText().toLowerCase(Locale.ROOT).contains(NAME_SHE_HATES)) return;
        var player = event.getPlayer();
        player.getServer().execute(() -> saw(player, Deed.CALLED_SHEPHERD));
    }
}
