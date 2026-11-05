package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Say;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TemperatureManager;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
    /** Only so a line never plays twice running: it does not need to outlive the server. */
    private static final Map<Deed, Say> lastSaid = new EnumMap<>(Deed.class);
    private static final Map<Trigger, Say> lastMurmur = new EnumMap<>(Trigger.class);
    private static String said;
    /** Someone near her has been in a fight she has not remarked on. */
    private static boolean fought;

    private ShepherdReactions() {}

    /** Her body, if she is awake and it is loaded. */
    static ShepherdEntity loaded(MinecraftServer server) {
        var data = ShepherdSavedData.get(server);
        if (data.stage() != Stage.AWAKE || data.entity() == null || data.dimension() == null) return null;
        var level = server.getLevel(data.dimension());
        return level != null && level.getEntity(data.entity()) instanceof ShepherdEntity she ? she : null;
    }

    /** Offline she sees nothing, and an unloaded body has no sensors. */
    private static ShepherdEntity awakeAndWatching(MinecraftServer server) {
        var she = loaded(server);
        return she == null || she.offline() ? null : she;
    }

    public static void saw(ServerPlayer player, Deed deed) {
        var server = player.getServer();
        var she = awakeAndWatching(server);
        if (she == null || she.level() != player.level() || player.distanceTo(she) > ShepherdPolicy.HEARD_BLOCKS) return;
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        if (now < data.deedReady(deed.name())) return;
        data.deedReady(deed.name(), now + deed.cooldownTicks());
        data.affinity(data.affinity() + deed.score());
        if (ShepherdTalk.live()) return;
        if (!deed.lines().isEmpty()) data.lastAmbient(now);
        boolean wary = data.tier() == Tier.WARY;
        if (!wary) she.react(deed.mood());
        Say line = ShepherdAffinity.pick(deed.lines(), lastSaid.get(deed), she.getRandom());
        if (line == null) return;
        lastSaid.put(deed, line);
        if (wary) {
            speak(she, deed.liked() ? ShepherdAffinity.WARY_LIKED : ShepherdAffinity.WARY_DISLIKED, Mood.RECORDING);
        } else {
            speak(she, line.key(data.heavy()), deed.mood());
        }
    }

    /** Everyone who can hear her gets the strip. */
    private static void speak(ShepherdEntity she, String key, Mood mood) {
        said = key;
        var line = new ShepherdLinePayload(key, mood);
        for (var heard : near((ServerLevel) she.level(), she)) PacketDistributor.sendToPlayer(heard, line);
    }

    /** A pool's line, if the trigger's cooldown has run and the moment allows. */
    public static boolean murmur(ServerLevel level, Trigger trigger) {
        var pools = ShepherdAmbients.pools(trigger);
        var data = ShepherdSavedData.get(level.getServer());
        long now = level.getServer().overworld().getGameTime();
        String cooldown = "ambient." + trigger.name();
        if (pools.isEmpty() || now < data.deedReady(cooldown)) return false;
        var pool = pools.get(level.getRandom().nextInt(pools.size()));
        Say line = ShepherdAffinity.pick(pool.variants(), lastMurmur.get(trigger), level.getRandom());
        if (!murmur(level, line.key(data.heavy()), pool.mood())) return false;
        lastMurmur.put(trigger, line);
        data.deedReady(cooldown, now + pool.cooldownTicks());
        return true;
    }

    /** Something she says unasked: never at Wary or over a conversation, only when things are calm, and not within the gap of her last line. */
    public static boolean murmur(ServerLevel level, String key, Mood mood) {
        var server = level.getServer();
        var she = awakeAndWatching(server);
        if (she == null || she.level() != level) return false;
        var near = near(level, she);
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        boolean gap = data.lastAmbient() != ShepherdSavedData.NOT_SET && now - data.lastAmbient() < ShepherdPolicy.AMBIENT_GAP_TICKS;
        if (near.isEmpty() || data.tier() == Tier.WARY || ShepherdTalk.live() || gap || !ShepherdPrompt.calm(server, level, near)) return false;
        data.lastAmbient(now);
        she.react(mood);
        speak(she, key, mood);
        return true;
    }

    /** The last key she said, for GameTests: a strip has nothing on the server to read back. */
    static String said() {
        return said;
    }

    static void clearForTests() {
        lastSaid.clear();
        lastMurmur.clear();
        said = null;
        fought = false;
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        clearForTests();
    }

    private static List<ServerPlayer> near(ServerLevel level, ShepherdEntity she) {
        return level.getPlayers(player -> !player.isSpectator() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS);
    }

    /** {@code /fd shepherd skipday}: sleep and /time leave game time alone, so a playtest moves her clocks instead. */
    public static void skipDay(MinecraftServer server) {
        var she = loaded(server);
        ShepherdSavedData.get(server).skipDay(she != null && !near((ServerLevel) she.level(), she).isEmpty());
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % POLL_TICKS != 0) return;
        var she = awakeAndWatching(server);
        if (she == null) return;
        var level = (ServerLevel) she.level();
        var near = near(level, she);
        if (near.isEmpty()) return;
        comeBack(server, she, near.getFirst());
        for (var player : near) look(level, player);
        commentate(server, level, she, near);
    }

    /** The first of these the moment allows. The gap keeps it to one. */
    private static void commentate(MinecraftServer server, ServerLevel level, ShepherdEntity she, List<ServerPlayer> near) {
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        if (data.lastTalk() == ShepherdSavedData.NOT_SET) data.lastTalk(now);
        if (fought && she.watching() == null && murmur(level, Trigger.AFTER_FIGHT)) fought = false;
        if (now - data.lastTalk() >= ShepherdPolicy.SILENCE_TICKS) murmur(level, Trigger.SILENCE);
        if (near.stream().anyMatch(player -> PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C)) murmur(level, Trigger.COLD);
        if (!ShepherdPolicy.night(level.getDayTime())) return;
        if (imaginedAuroraShows(server, level, near.getFirst())) murmur(level, Trigger.IMAGINED_AURORA);
        murmur(level, Trigger.NIGHT);
    }

    /** From phase 4 the sky cannot show one, so she has to imagine it. The storms are locked on by then, so a clear night means no blizzard, not no rain. */
    private static boolean imaginedAuroraShows(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        var state = ApocalypseState.get(server);
        return state.getPhase() >= ShepherdPolicy.IMAGINED_AURORA_PHASE && level.canSeeSky(player.blockPosition())
                && !PhaseManager.isBlizzardActive(state.getPhase(), state.getProgress());
    }

    /** Her clock on you runs only while she can see someone, so being gone a day is one gap in it. */
    private static void comeBack(MinecraftServer server, ShepherdEntity she, ServerPlayer player) {
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        long last = data.lastNear();
        data.lastNear(now);
        if (last == ShepherdSavedData.NOT_SET) return;
        if (now - last >= ShepherdPolicy.DAY_TICKS) {
            saw(player, Deed.LEFT_BEHIND);
        } else if (now - last >= ShepherdPolicy.GLAD_AWAY_TICKS && data.tier() != Tier.WARY) {
            she.react(Mood.HAPPY, ShepherdPolicy.GLAD_TICKS);
        }
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

    @SubscribeEvent public static void flinch(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var she = awakeAndWatching(player.getServer());
        if (she == null || she.level() != player.level() || player.distanceTo(she) > ShepherdPolicy.HEARD_BLOCKS) return;
        if (ShepherdSavedData.get(player.getServer()).tier() != Tier.WARY) she.react(Mood.ALARMED, ShepherdPolicy.FLINCH_TICKS);
        if (event.getSource().getEntity() instanceof Enemy) fought = true;
    }

    @SubscribeEvent public static void killed(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        var she = awakeAndWatching(player.getServer());
        if (she != null && she.level() == player.level() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS) fought = true;
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if (!event.getRawText().toLowerCase(Locale.ROOT).contains(NAME_SHE_HATES)) return;
        var player = event.getPlayer();
        player.getServer().execute(() -> saw(player, Deed.CALLED_SHEPHERD));
    }
}
