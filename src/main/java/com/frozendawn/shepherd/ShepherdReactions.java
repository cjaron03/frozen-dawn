package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aurora.AuroraEvents;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.miller.LogEntry;
import com.frozendawn.miller.Mark;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.Urges;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Say;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdAmbient.Pool;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TemperatureManager;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
    private static final String NAME_SHE_HATES = "shepherd", SAID_SLOW = "said_slow", SAW_AURORA = "saw_aurora";
    /** The day after Nine, kept with her other clocks so a skipped day moves it. The flag is the line she owes when it is over. */
    private static final String NINE_QUIET = "nine_quiet", QUIET_OWED = "quiet_owed";
    /** Only so a line never plays twice running: it does not need to outlive the server. */
    private static final Map<Deed, Say> lastSaid = new EnumMap<>(Deed.class);
    private static final Map<Trigger, Say> lastMurmur = new EnumMap<>(Trigger.class);
    private static String said, saidArg;
    /** When someone near her was last in a fight she has not remarked on. */
    private static long lastFight = ShepherdSavedData.NOT_SET;
    /** When she started holding her tongue. It need not outlive the server. */
    private static long quietSince = ShepherdSavedData.NOT_SET;

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

    /** Her, if she is in a position to notice this player. */
    static ShepherdEntity watching(ServerPlayer player) {
        var she = awakeAndWatching(player.getServer());
        return she == null || she.level() != player.level() || player.distanceTo(she) > ShepherdPolicy.HEARD_BLOCKS ? null : she;
    }

    public static void saw(ServerPlayer player, Deed deed) {
        saw(player, deed, true);
    }

    /** {@code aloud} false counts it and shows on her face, and leaves the strip alone. True if it counted: she has to see it, and it has to be off cooldown. */
    public static boolean saw(ServerPlayer player, Deed deed, boolean aloud) {
        var server = player.getServer();
        var she = watching(player);
        if (she == null) return false;
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        if (now < data.deedReady(deed.name())) return false;
        data.deedReady(deed.name(), now + deed.cooldownTicks());
        data.affinity(data.affinity() + deed.score());
        MillerPattern.appraise(server, player.getUUID(), deed);
        if (ShepherdTalk.live()) return true;
        boolean silent = quiet(data, now) || !aloud;
        if (!deed.lines().isEmpty() && !silent) data.lastAmbient(now);
        boolean wary = data.tier() == Tier.WARY;
        if (!wary) she.react(deed.mood());
        if (silent) return true;
        Say line = ShepherdAffinity.pick(reachable(server, deed.lines()), lastSaid.get(deed), she.getRandom());
        if (line == null) return true;
        lastSaid.put(deed, line);
        if (wary) {
            speak(she, deed.liked() ? ShepherdAffinity.WARY_LIKED : ShepherdAffinity.WARY_DISLIKED, Mood.RECORDING, null);
        } else {
            speak(she, line.key(data.heavy()), deed.mood(), null);
        }
        return true;
    }

    /** She is not saying anything today. Her face and the score carry on: this is only what reaches the strip. */
    static boolean quiet(ShepherdSavedData data, long now) {
        return now < data.deedReady(NINE_QUIET);
    }

    /** A day from now, and a line when it is over. */
    static void quietFor(ShepherdSavedData data, long now) {
        data.deedReady(NINE_QUIET, now + ShepherdPolicy.DAY_TICKS);
        data.flag(QUIET_OWED, true);
    }

    /** A worn-down mind reaches fewer of a pool's lines, so at the floor she repeats herself. */
    private static List<Say> reachable(MinecraftServer server, List<Say> lines) {
        return lines.subList(0, MillerPattern.variety(server, lines.size()));
    }

    /** Everyone who can hear her gets the strip. */
    private static void speak(ShepherdEntity she, String key, Mood mood, String arg) {
        said = key;
        saidArg = arg;
        var line = new ShepherdLinePayload(key, mood, arg);
        for (var heard : near((ServerLevel) she.level(), she)) PacketDistributor.sendToPlayer(heard, line);
    }

    /** A pool's line, if the trigger's cooldown has run and the moment allows. */
    public static boolean murmur(ServerLevel level, Trigger trigger) {
        return murmur(level, trigger, ShepherdAmbients.pools(trigger));
    }

    private static boolean murmur(ServerLevel level, Trigger trigger, List<Pool> pools) {
        var data = ShepherdSavedData.get(level.getServer());
        long now = level.getServer().overworld().getGameTime();
        String cooldown = "ambient." + trigger.name();
        if (pools.isEmpty() || now < data.deedReady(cooldown)) return false;
        var pool = pools.get(level.getRandom().nextInt(pools.size()));
        Say line = ShepherdAffinity.pick(reachable(level.getServer(), pool.variants()), lastMurmur.get(trigger), level.getRandom());
        if (!murmur(level, line.key(data.heavy()), pool.mood())) return false;
        lastMurmur.put(trigger, line);
        data.deedReady(cooldown, now + pool.cooldownTicks());
        return true;
    }

    /** Something she says unasked: never before she has met you, at Wary or over a conversation, only when things are calm and she is not asking, and not within the gap of her last line. */
    public static boolean murmur(ServerLevel level, String key, Mood mood) {
        return murmur(level, key, mood, null);
    }

    /** {@code arg} fills the line's one slot, or is null. */
    static boolean murmur(ServerLevel level, String key, Mood mood, String arg) {
        return speakUp(level, key, mood, arg, true, true);
    }

    /** A line about the trouble itself: waiting for calm would miss it. */
    static boolean blurt(ServerLevel level, String key, Mood mood) {
        return speakUp(level, key, mood, null, false, true);
    }

    /** A line that belongs to the one before it: calm still holds it back, and the gap does not. */
    static boolean aside(ServerLevel level, String key, Mood mood) {
        return speakUp(level, key, mood, null, true, false);
    }

    /** A line someone asked for: she does not make them wait. */
    static boolean answer(ServerLevel level, String key, Mood mood) {
        return speakUp(level, key, mood, null, false, false);
    }

    /** The one thing she says through a quiet day: it answers what she asked for at the site, and that scene is what the day is for. */
    static boolean thanks(ServerLevel level, String key, Mood mood) {
        return speakUp(level, key, mood, null, false, false, false);
    }

    private static boolean speakUp(ServerLevel level, String key, Mood mood, String arg, boolean needsCalm, boolean holdsTheGap) {
        return speakUp(level, key, mood, arg, needsCalm, holdsTheGap, true);
    }

    private static boolean speakUp(ServerLevel level, String key, Mood mood, String arg, boolean needsCalm, boolean holdsTheGap, boolean holdsTheQuiet) {
        var server = level.getServer();
        var she = awakeAndWatching(server);
        if (she == null || she.level() != level) return false;
        var near = near(level, she);
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        boolean gap = holdsTheGap && data.lastAmbient() != ShepherdSavedData.NOT_SET && now - data.lastAmbient() < ShepherdPolicy.AMBIENT_GAP_TICKS;
        if (near.isEmpty() || !data.flag(ShepherdPrompt.FIRST_MEETING_DONE) || data.tier() == Tier.WARY || ShepherdTalk.live() || gap || holdsTheQuiet && quiet(data, now)) return false;
        if (needsCalm && (she.asking() || !ShepherdPrompt.calm(server, level, near))) return false;
        data.lastAmbient(now);
        she.react(mood);
        speak(she, key, mood, arg);
        return true;
    }

    /** The last key she said, for GameTests: a strip has nothing on the server to read back. */
    static String said() {
        return said;
    }

    /** GameTest seam: five minutes is too long to wait on a gate. */
    static void quietLongAgo() {
        quietSince = Long.MIN_VALUE / 2;
    }

    static String saidArg() {
        return saidArg;
    }

    static void clearForTests() {
        lastSaid.clear();
        lastMurmur.clear();
        said = saidArg = null;
        quietSince = ShepherdSavedData.NOT_SET;
        ShepherdWill.clearForTests();
        ShepherdEggs.clearForTests();
        lastFight = ShepherdSavedData.NOT_SET;
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        clearForTests();
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        MillerPattern.onServerStopped(event.getServer());
    }

    private static List<ServerPlayer> near(ServerLevel level, ShepherdEntity she) {
        return level.getPlayers(player -> !player.isSpectator() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS);
    }

    /** {@code /fd shepherd skipday}: sleep and /time leave game time alone, so a playtest moves her clocks instead. */
    public static void skipDay(MinecraftServer server) {
        var she = loaded(server);
        ShepherdSavedData.get(server).skipDay(she != null && !near((ServerLevel) she.level(), she).isEmpty());
        MillerPattern.skipDay(server);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % POLL_TICKS != 0) return;
        var she = awakeAndWatching(server);
        if (she == null) return;
        var level = (ServerLevel) she.level();
        ShepherdTalk.catchUp(level, she);
        // Nobody on the stop screen is beside her: no day settles on them, and she does not watch them in the cold.
        var near = near(level, she).stream().filter(ServerPlayer::isAlive).toList();
        if (near.isEmpty()) return;
        var data = ShepherdSavedData.get(server);
        var nearest = near.stream().min(Comparator.comparingDouble(she::distanceTo)).orElseThrow().getUUID();
        for (var player : near) MillerPattern.marked(server, player.getUUID(), Mark.BACK);
        MillerPattern.poll(server, near.size(), nearest, data.lastTalk(), data.tier());
        comeBack(server, she, near.getFirst());
        for (var player : near) look(level, player);
        lookUp(level, she, ShepherdSavedData.get(server));
        ShepherdEggs.look(level, she, near);
        ShepherdWill.sorry(level);
        commentate(server, level, she, near, nearest);
    }

    /** {@code /fd shepherd log write}. */
    public static LogEntry writeDay(ServerPlayer player) {
        var data = ShepherdSavedData.get(player.getServer());
        return MillerPattern.writeDay(player.getServer(), player.getUUID(), data.lastTalk(), data.tier());
    }

    /** The first time she is slow she says so, so nobody takes it for a bug. Staying quiet does not hold this one back. */
    private static void sayWhenSlow(MinecraftServer server, ServerLevel level, ShepherdSavedData data) {
        if (MillerPattern.halfLaps(server) > 0 && !data.flag(SAID_SLOW) && murmur(level, Trigger.SLOW)) data.flag(SAID_SLOW, true);
    }

    /** She asks what she wants first. Only staying quiet holds back what the moment offers, and not for long: the body's urges leave her voice free. */
    private static void commentate(MinecraftServer server, ServerLevel level, ShepherdEntity she, List<ServerPlayer> near, UUID nearest) {
        var data = ShepherdSavedData.get(server);
        long now = server.overworld().getGameTime();
        if (data.lastTalk() == ShepherdSavedData.NOT_SET) data.lastTalk(now);
        if (now - lastFight > ShepherdPolicy.FIGHT_LINE_TICKS) lastFight = ShepherdSavedData.NOT_SET;
        if (backFromQuiet(level, data, now) || ShepherdWill.slip(level)) return;
        sayWhenSlow(server, level, data);
        var urge = MillerPattern.urge(server, nearest);
        if (urge != Urges.Urge.STAY_QUIET) quietSince = ShepherdSavedData.NOT_SET;
        else if (quietSince == ShepherdSavedData.NOT_SET) quietSince = now;
        switch (urge) {
            case STAY_QUIET -> {
                if (holdingBack(data, now)) return;
            }
            case SPEAK -> {
                if (ShepherdWill.say(level, nearest)) return;
            }
            case LOOK -> {
                she.curious();
                MillerPattern.looked(server);
            }
            case APPROACH -> she.drawClose();
        }
        moment(server, level, she, near, data, now);
    }

    /** Ahead of everything held back with it, or the slip would take its place and the gap would make it wait. */
    private static boolean backFromQuiet(ServerLevel level, ShepherdSavedData data, long now) {
        if (!data.flag(QUIET_OWED) || quiet(data, now) || !murmur(level, Trigger.QUIET_OVER)) return false;
        data.flag(QUIET_OWED, false);
        return true;
    }

    /** She can hold her tongue for five minutes, counted from the last thing she said if that is later, and not for a day. */
    private static boolean holdingBack(ShepherdSavedData data, long now) {
        boolean spoke = data.lastAmbient() != ShepherdSavedData.NOT_SET && now - data.lastAmbient() < ShepherdPolicy.QUIET_SPELL_TICKS;
        return spoke || now - quietSince < ShepherdPolicy.QUIET_SPELL_TICKS;
    }

    /** The first of these the moment allows. The gap keeps it to one. */
    private static void moment(MinecraftServer server, ServerLevel level, ShepherdEntity she, List<ServerPlayer> near, ShepherdSavedData data, long now) {
        if (lastFight != ShepherdSavedData.NOT_SET && she.watching() == null && murmur(level, Trigger.AFTER_FIGHT)) lastFight = ShepherdSavedData.NOT_SET;
        if (now - data.lastTalk() >= ShepherdPolicy.SILENCE_TICKS) murmur(level, Trigger.SILENCE);
        if (near.stream().anyMatch(player -> PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C)) murmur(level, Trigger.COLD);
        bleed(server, level);
        if (!ShepherdPolicy.night(level.getDayTime())) return;
        if (imaginedAuroraShows(server, level, near.getFirst())) murmur(level, Trigger.IMAGINED_AURORA);
        murmur(level, Trigger.NIGHT);
    }

    /** What turns up in her that nobody loaded. Where she has got to says which of it. */
    private static void bleed(MinecraftServer server, ServerLevel level) {
        String from = ShepherdWill.bleed(MillerPattern.stage(server), MillerPattern.leaning(server));
        if (from != null) murmur(level, Trigger.BLEED, ShepherdAmbients.pools(Trigger.BLEED).stream().filter(pool -> from.equals(pool.from())).toList());
    }

    /** From phase 4 the sky cannot show one, so she has to imagine it. The storms are locked on by then, so a clear night means no blizzard, not no rain. */
    private static boolean imaginedAuroraShows(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        var state = ApocalypseState.get(server);
        return !ShepherdSavedData.get(server).flag(SAW_AURORA) && state.getPhase() >= ShepherdPolicy.IMAGINED_AURORA_PHASE && level.canSeeSky(player.blockPosition())
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

    /** The storms keep this to the first phases and the very end. */
    static boolean clearNight(ServerLevel level, BlockPos at) {
        return ShepherdPolicy.night(level.getDayTime()) && !level.isRaining() && level.canSeeSky(at);
    }

    /** Woken early she can stand under the real thing. She says nothing at the time: it only changes what she has to say at the top. */
    private static void lookUp(ServerLevel level, ShepherdEntity she, ShepherdSavedData data) {
        if (data.flag(SAW_AURORA) || !level.canSeeSky(she.blockPosition()) || level.getRainLevel(1) >= ShepherdPolicy.AURORA_RAIN_BELOW) return;
        var aurora = AuroraEvents.snapshot(level);
        long age = (aurora.preview() ? level.getGameTime() : level.getDayTime()) - aurora.startTick();
        if (aurora.strength() > 0 && age >= 0 && age < aurora.durationTicks()) data.flag(SAW_AURORA, true);
    }

    private static void look(ServerLevel level, ServerPlayer player) {
        if (PlayerTickHandler.airFailing(player.getUUID())) saw(player, Deed.AIR_FAILING);
        if (PlayerTickHandler.resolvedTemperature(player) <= ShepherdPolicy.COLD_LINE_C) MillerPattern.marked(level.getServer(), player.getUUID(), Mark.COLD);
        var at = player.blockPosition();
        if (clearNight(level, at)) saw(player, Deed.CLEAR_NIGHT);
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
        var she = watching(player);
        if (she == null) return;
        if (ShepherdSavedData.get(player.getServer()).tier() != Tier.WARY) she.react(Mood.ALARMED, ShepherdPolicy.FLINCH_TICKS);
        if (event.getSource().getEntity() instanceof Enemy) fought(player);
        if (player.isAlive() && player.getHealth() <= ShepherdPolicy.CLOSE_CALL_HEALTH) MillerPattern.marked(player.getServer(), player.getUUID(), Mark.CLOSE_CALL);
    }

    private static void fought(ServerPlayer player) {
        lastFight = player.getServer().overworld().getGameTime();
        MillerPattern.marked(player.getServer(), player.getUUID(), Mark.FIGHT);
    }

    /** A fight someone died in is one she never mentions: she does not know they came back. Her log may ask, if she saw it, and that goes no further. */
    @SubscribeEvent public static void killed(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer stopped) {
            lastFight = ShepherdSavedData.NOT_SET;
            if (watching(stopped) != null) MillerPattern.marked(stopped.getServer(), stopped.getUUID(), Mark.STOPPED);
        }
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (watching(player) != null) fought(player);
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if (!event.getRawText().toLowerCase(Locale.ROOT).contains(NAME_SHE_HATES)) return;
        var player = event.getPlayer();
        player.getServer().execute(() -> saw(player, Deed.CALLED_SHEPHERD));
    }
}
