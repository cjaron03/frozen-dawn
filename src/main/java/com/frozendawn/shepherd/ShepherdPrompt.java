package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.event.PlayerTickHandler;
import com.frozendawn.network.ShepherdPromptPayload;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.BlastPitPlacement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Got a minute?": a conversation she wants to have, waiting for a right-click. Only {@link ShepherdSavedData#promptNode()}
 * is kept; how often the strip has shown is not, so a reload shows it afresh. She asks at the last door and at the vault's
 * exit, once each, only if she was woken in the vault, the first meeting is over and she is there to say it.
 * A tier-up is not an event: at a calm moment she looks at the score as it stands and asks the first {@link #PERSONAL}
 * conversation she has not asked, so a reload or a falling score needs no bookkeeping.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdPrompt {
    public static final String DOORS = "fm.doors", EXIT = "fm.exit";
    public static final String THE_NAME = "t1.name", THE_OTHERS = "t2.others", AT_NINE = "t2.site";
    static final String PERSONAL_GAP = "personal_gap";
    static final String FIRST_MEETING_DONE = "fm_done", DOORS_ASKED = "doors_asked", EXIT_ASKED = "exit_asked", NAME_ASKED = "t1_asked", ASKED_NAME = "asked_name",
            OTHERS_ASKED = "t2_asked";
    /** What each tier opens, in the order she brings it up. */
    private static final List<Personal> PERSONAL = List.of(new Personal(Tier.FRIENDLY, THE_NAME, NAME_ASKED, (server, level, data, near) -> true),
            new Personal(Tier.FRIENDLY, THE_OTHERS, OTHERS_ASKED, ShepherdPrompt::awayFromThePit));
    /** A sprinter covers 1.4 blocks in this, so nobody crosses a box between looks. */
    private static final int CHECK_TICKS = 5;
    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    /** Whether a conversation can land now, whatever the tier says. One that cannot is passed over, not given up on. */
    @FunctionalInterface
    interface Ready {
        boolean test(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near);
    }

    record Personal(Tier tier, String node, String asked, Ready ready) {}

    private static List<Personal> personal = PERSONAL;
    private static int shows;
    private static int nextShow;
    private static int repeat = ShepherdPolicy.PROMPT_REPEAT_TICKS;
    private static final Map<UUID, Long> lastHurt = new HashMap<>();

    private ShepherdPrompt() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) return;
        var data = ShepherdSavedData.get(server);
        if (data.stage() != Stage.AWAKE || data.entity() == null || data.waiting() || !Level.OVERWORLD.equals(data.dimension())) return;
        var level = server.overworld();
        if (!(level.getEntity(data.entity()) instanceof ShepherdEntity she) || she.offline()) return;
        var near = level.getPlayers(player -> !player.isSpectator() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS);
        if (near.isEmpty()) return;
        ask(server, data, near);
        atNine(server, level, data, near, she);
        if (ShepherdReactions.quiet(data, server.overworld().getGameTime())) return;
        offer(server, level, data, near);
        show(server, level, data, near, she);
    }

    private static void ask(MinecraftServer server, ShepherdSavedData data, List<ServerPlayer> near) {
        var hall = HelionSiteState.get(server).vault();
        if (hall == null || !data.flag(ShepherdSavedData.WOKE_IN_VAULT) || !data.flag(FIRST_MEETING_DONE)) return;
        for (var player : near) {
            var at = player.blockPosition().subtract(hall);
            if (!data.flag(DOORS_ASKED) && ShepherdPolicy.atLastDoor(at.getX(), at.getY(), at.getZ())) raise(data, DOORS_ASKED, DOORS);
            if (!data.flag(EXIT_ASKED) && ShepherdPolicy.atVaultExit(at.getX(), at.getY(), at.getZ())) raise(data, EXIT_ASKED, EXIT);
        }
    }

    /** Only with her there, and only if she would stand for it. Until then the husk is a dead unit and nothing else. */
    private static void atNine(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near, ShepherdEntity she) {
        var husk = data.huskPos();
        if (husk == null || data.flag(ShepherdNine.SEEN) || ShepherdTalk.live()) return;
        // Unanswered it is still only a thought at the site: it goes if she stops standing for it, or stops standing there.
        if (AT_NINE.equals(data.promptNode())) {
            if (data.tier() == Tier.WARY || !she.blockPosition().closerThan(husk, ShepherdPolicy.NINE_LEFT_BLOCKS)) data.promptNode(null);
            return;
        }
        if (data.tier() != Tier.WARY && she.blockPosition().closerThan(husk, ShepherdPolicy.NINE_SEEN_BLOCKS) && calm(server, level, near)) raise(data, AT_NINE);
    }

    /** T2 sends you there, so the pit has to be somewhere, and not where you are standing. */
    private static boolean awayFromThePit(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near) {
        var pit = BlastPitPlacement.ensureBlastPitResolved(level);
        return pit != null && near.stream().allMatch(player ->
                ShepherdPolicy.groundBlocks(pit.getX() - player.getBlockX(), pit.getZ() - player.getBlockZ()) > ShepherdPolicy.PIT_PROMPT_BLOCKS);
    }

    /** A place prompt takes over from a waiting personal one, which is then not asked yet. */
    private static void raise(ShepherdSavedData data, String asked, String node) {
        raise(data, node);
        data.flag(asked, true);
    }

    private static void raise(ShepherdSavedData data, String node) {
        withdraw(data);
        data.promptNode(node);
        shows = 0;
        nextShow = 0;
    }

    private static void offer(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near) {
        if (ShepherdTalk.live()) return;
        var tier = data.tier();
        long now = server.overworld().getGameTime();
        var waiting = waitingPersonal(data);
        if (waiting != null && !(tier.atLeast(waiting.tier()) && waiting.ready().test(server, level, data, near))) withdraw(data);
        // While one waits the day has not started: it runs from when she is answered.
        if (waitingPersonal(data) != null) data.deedReady(PERSONAL_GAP, now + ShepherdPolicy.PERSONAL_GAP_TICKS);
        if (data.promptNode() != null || data.resumeNode() != null || !data.flag(FIRST_MEETING_DONE) || now < data.deedReady(PERSONAL_GAP)) return;
        if (!calm(server, level, near)) return;
        if (!ShepherdPolicy.comfortable(coldest(near), near.stream().anyMatch(player -> PlayerTickHandler.airFailing(player.getUUID())))) return;
        for (var entry : personal) {
            if (!tier.atLeast(entry.tier()) || data.flag(entry.asked()) || !entry.ready().test(server, level, data, near)) continue;
            raise(data, entry.asked(), entry.node());
            data.deedReady(PERSONAL_GAP, now + ShepherdPolicy.PERSONAL_GAP_TICKS);
            return;
        }
    }

    private static Personal waitingPersonal(ShepherdSavedData data) {
        return personal.stream().filter(entry -> entry.node().equals(data.promptNode())).findFirst().orElse(null);
    }

    /** Not while it is on someone's screen: it is being said, so it has been asked. */
    private static void withdraw(ShepherdSavedData data) {
        var waiting = waitingPersonal(data);
        if (waiting == null || ShepherdTalk.live()) return;
        data.flag(waiting.asked(), false);
        data.promptNode(null);
        data.deedReady(PERSONAL_GAP, 0);
        if (waiting.node().equals(data.resumeNode())) data.resumeNode(null);
    }

    /** Nothing hostile near them, and none of them hurt lately. */
    static boolean calm(MinecraftServer server, ServerLevel level, List<ServerPlayer> near) {
        return ShepherdPolicy.calm(nearestHostile(level, near), sinceHurt(server, near));
    }

    /** Nobody near is being hunted or was hurt lately. A hostile about its own business does not count: at a Hearth there is always one in sight. */
    static boolean unhunted(MinecraftServer server, ServerLevel level, List<ServerPlayer> near) {
        return ShepherdPolicy.calm(Double.MAX_VALUE, sinceHurt(server, near)) && near.stream().allMatch(player -> level.getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(ShepherdPolicy.CALM_HOSTILE_BLOCKS), mob -> mob instanceof Enemy && mob.getTarget() == player).isEmpty());
    }

    private static float coldest(List<ServerPlayer> near) {
        float coldest = Float.MAX_VALUE;
        for (var player : near) coldest = Math.min(coldest, PlayerTickHandler.resolvedTemperature(player));
        return coldest;
    }

    /** Only someone she can see counts: a fight across the map is none of her business. */
    private static long sinceHurt(MinecraftServer server, List<ServerPlayer> near) {
        long latest = LONG_AGO;
        for (var player : near) latest = Math.max(latest, lastHurt.getOrDefault(player.getUUID(), LONG_AGO));
        return server.getTickCount() - latest;
    }

    private static void show(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near, ShepherdEntity she) {
        int now = server.getTickCount();
        if (data.promptNode() == null || shows >= ShepherdPolicy.PROMPT_SHOWS || now < nextShow || ShepherdTalk.live()) return;
        if (!calm(server, level, near)) return;
        for (var player : near) PacketDistributor.sendToPlayer(player, new ShepherdPromptPayload());
        if (near.contains(ShepherdEntity.followerIn(level))) she.askFor(ShepherdPolicy.ASK_TICKS);
        shows++;
        nextShow = now + repeat;
    }

    private static double nearestHostile(ServerLevel level, List<ServerPlayer> near) {
        double nearest = Double.MAX_VALUE;
        for (var player : near) {
            for (var mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ShepherdPolicy.CALM_HOSTILE_BLOCKS), mob -> mob instanceof Enemy)) {
                nearest = Math.min(nearest, mob.distanceTo(player));
            }
        }
        return nearest;
    }

    @SubscribeEvent public static void hurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) lastHurt.put(player.getUUID(), (long) player.getServer().getTickCount());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        clearForTests();
    }

    static int shows() {
        return shows;
    }

    /** GameTest seam: a minute between showings is too long to wait on a gate. */
    static void repeatEvery(int ticks) {
        repeat = ticks;
    }

    /** GameTest seam: the list as it will one day stand. */
    static void personal(List<Personal> entries) {
        personal = entries;
    }

    static void clearForTests() {
        personal = PERSONAL;
        shows = 0;
        nextShow = 0;
        repeat = ShepherdPolicy.PROMPT_REPEAT_TICKS;
        lastHurt.clear();
    }
}
