package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one hologram in the world, and what she says over it. The talk has closed, so her lines go to the strip, one at a time, to the
 * one who asked. Nothing here is saved: it stops for what would close a talk, and a reload simply finds it gone.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdProjection {
    /** What {@link ShepherdEntity#projection()} carries. */
    public static final int NONE = 0, HUNTER = 1, MIMIC = 2, ARCHITECT = 3, EGG_FRAME = 4, ITEM = 5, PAIR = 6;
    /** Her lines wait for the beam to land. */
    public static final int BEAM_TICKS = 20;
    /** The other shape, tears and all. */
    static final int EGG_TICKS = 36;
    static final String SEEN = "hologram_seen", EGG = "hologram_egg", EGG_LINE = "shepherd.egg.hologram";

    private static final class Live {
        final ServerPlayer player;
        final ShepherdEntity she;
        final int kind;
        final List<Line> lines;
        int next, wait = BEAM_TICKS, eggTicks;
        /** She has stopped mid-sentence, and the line she stopped on comes when she is back. */
        Line held;
        boolean hurt;

        Live(ServerPlayer player, ShepherdEntity she, int kind, List<Line> lines) {
            this.player = player;
            this.she = she;
            this.kind = kind;
            this.lines = lines;
        }
    }

    private static Live live;
    /** What this projection has said, for GameTests: a strip has nothing on the server to read back. */
    private static final List<ShepherdLinePayload> spoken = new ArrayList<>();

    private ShepherdProjection() {}

    public static void start(ServerPlayer player, ShepherdEntity she, String kind, List<Line> lines) {
        int shown = shown(kind);
        if (live != null || shown == NONE) return;
        live = new Live(player, she, shown, shown <= ARCHITECT ? withTheEgg(player, lines) : lines);
        spoken.clear();
        she.projection(shown);
        if (shown == PAIR) she.projectedPlayer(Optional.of(player.getUUID()));
        she.getNavigation().stop();
    }

    /** What she shows herself when she thinks nobody is looking. It says nothing, and it ends by itself if nobody catches her. */
    static void secretly(ServerPlayer follower, ShepherdEntity she) {
        start(follower, she, "pair", List.of());
        if (live != null) live.wait = ShepherdPolicy.CAUGHT_WINDOW_TICKS;
    }

    private static int shown(String kind) {
        return switch (kind) {
            case "hunter" -> HUNTER;
            case "mimic" -> MIMIC;
            case "architect" -> ARCHITECT;
            case "item" -> ITEM;
            case "pair" -> PAIR;
            default -> NONE;
        };
    }

    /** The second time she shows one, once she trusts them. After her first line, so there is a figure to flicker. */
    private static List<Line> withTheEgg(ServerPlayer player, List<Line> lines) {
        var data = ShepherdSavedData.get(player.getServer());
        boolean again = data.flag(SEEN);
        data.flag(SEEN, true);
        if (!again || data.flag(EGG) || !ShepherdTalk.holds(player, "tier_trusted")) return lines;
        List<Line> played = new ArrayList<>(lines);
        played.add(Math.min(1, lines.size()), new Line(EGG_LINE, null, Mood.FLUSTERED, null));
        return played;
    }

    public static boolean active() {
        return live != null;
    }

    /** It snaps off. Whatever is on the strip finishes by itself. */
    public static void stop() {
        if (live == null) return;
        var over = live;
        live = null;
        over.she.projection(NONE);
        over.she.projectedPlayer(Optional.empty());
        // Her own moments count from here: a long one outlasts the wait since the last talk.
        var server = over.player.getServer();
        ShepherdSavedData.get(server).deedReady(ShepherdEggs.MOMENT, server.overworld().getGameTime() + ShepherdPolicy.MOMENT_IDLE_TICKS);
        over.she.talkFor(0);
        over.she.rest();
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    static void tick(MinecraftServer server) {
        if (live == null) return;
        if (mustStop()) {
            stop();
            return;
        }
        if (live.kind == PAIR && ShepherdEggs.spotted(live.she)) {
            var she = live.she;
            stop();
            ShepherdEggs.caught(she);
            return;
        }
        if (live.eggTicks > 0 && --live.eggTicks == 0) live.she.projection(live.kind);
        if (--live.wait > 0) return;
        if (live.held != null) {
            var line = live.held;
            live.held = null;
            say(line, Mood.NEUTRAL);
        } else if (live.next == live.lines.size()) {
            stop();
        } else {
            next(live.lines.get(live.next++));
        }
    }

    private static boolean mustStop() {
        var player = live.player;
        var she = live.she;
        boolean gone = player.isRemoved() || player.hasDisconnected() || she.isRemoved() || she.offline() || player.level() != she.level();
        return gone || ShepherdPolicy.talkMustClose(player.distanceTo(she), live.hurt, ShepherdTalk.nearestHostileOn(player));
    }

    private static void next(Line line) {
        if (line.mood() == Mood.WORRIED) {
            live.held = line;
            live.wait = ShepherdFace.WORRY_TICKS;
            live.she.talkFor(0);
            live.she.setMood(Mood.WORRIED);
            return;
        }
        if (ShepherdEggs.GAG_LINE.equals(line.key())) {
            var server = live.player.getServer();
            ShepherdSavedData.get(server).deedReady(ShepherdEggs.GAG, server.overworld().getGameTime() + ShepherdPolicy.GAG_EVERY_TICKS);
        }
        if (EGG_LINE.equals(line.key())) {
            ShepherdSavedData.get(live.player.getServer()).flag(EGG, true);
            live.eggTicks = EGG_TICKS;
            live.she.projection(EGG_FRAME);
        }
        say(line, line.mood());
    }

    private static void say(Line line, Mood mood) {
        boolean heavy = ShepherdSavedData.get(live.player.getServer()).heavy();
        String key = heavy && line.heavyKey() != null ? line.heavyKey() : line.key();
        int typing = Language.getInstance().getOrDefault(key).length() * ShepherdPolicy.TICKS_PER_CHAR;
        live.wait = typing + ShepherdPolicy.STRIP_HOLD_TICKS;
        live.she.talkFor(typing);
        live.she.setMood(mood);
        var payload = new ShepherdLinePayload(key, mood, null);
        spoken.add(payload);
        PacketDistributor.sendToPlayer(live.player, payload);
    }

    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (live != null && event.getEntity() == live.player) live.hurt = true;
    }

    static List<ShepherdLinePayload> spoken() {
        return List.copyOf(spoken);
    }

    /** GameTest isolation only. */
    static void clearForTests() {
        live = null;
        spoken.clear();
    }
}
