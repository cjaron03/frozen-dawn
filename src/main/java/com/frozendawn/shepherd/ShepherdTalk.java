package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one live conversation in the world. The server walks the tree and the client only ever sees the current node
 * and answers with a slot. An id that rises with each conversation makes anything in flight from an old one harmless.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdTalk {
    public static final String FIRST_MEETING = "fm.boot";
    private static final String HUB_TALK = "hub.talk";
    private static final int UNIMPRESSED_TICKS = 40;
    private static final int TALK_TICKS_PER_LINE = 80;

    public enum Action {
        TALK,
        FOLLOW,
        LAMP,
        SETTINGS
    }

    private static final class Live {
        final int id = nextId++;
        final ServerPlayer player;
        final ShepherdEntity she;
        /** Null while the hub is showing. */
        ShepherdConversation talk;
        boolean fromHub;
        /** The prompt this was opened from, which stays waiting until an answer shows it was heard. */
        String fromPrompt;
        boolean hurt;
        int affinityApplied;
        int unimpressedTicks;
        /** What the client was sent for this node. Conditions can change while it is on screen; her face must not. */
        List<Line> lines = List.of();
        /** Her body has no typed text to follow, so it moves through a node's lines on a fixed count. */
        int line, lineTicks;

        Live(ServerPlayer player, ShepherdEntity she) {
            this.player = player;
            this.she = she;
            she.askFor(0);
            ShepherdSavedData.get(player.getServer()).lastTalk(player.serverLevel().getGameTime());
        }
    }

    private static int nextId = 1;
    private static Live live;

    private ShepherdTalk() {}

    /** A right-click. She picks an unfinished conversation back up, then takes one she has been waiting to have; otherwise this is the hub. */
    public static void openHub(ServerPlayer player, ShepherdEntity she) {
        if (she.offline()) return;
        if (live != null) {
            if (live.player != player) {
                live.unimpressedTicks = UNIMPRESSED_TICKS;
                she.setMood(Mood.UNIMPRESSED);
            }
            return;
        }
        live = new Live(player, she);
        var data = data();
        String waiting = waitingNode(data);
        if (waiting == null) {
            showHub();
            return;
        }
        if (waiting.equals(data.promptNode())) live.fromPrompt = waiting;
        if (begin(waiting, false)) return;
        if (waiting.equals(data.resumeNode())) data.resumeNode(null);
        if (waiting.equals(data.promptNode())) data.promptNode(null);
        showHub();
    }

    /** A node that has gone missing, say from an edited datapack, is dropped here. */
    private static String waitingNode(ShepherdSavedData data) {
        var nodes = ShepherdDialogues.nodes();
        if (data.resumeNode() != null) {
            if (nodes.containsKey(data.resumeNode())) return data.resumeNode();
            data.resumeNode(null);
        }
        String prompt = data.promptNode();
        if (prompt == null || nodes.containsKey(prompt)) return prompt;
        data.promptNode(null);
        return null;
    }

    public static void start(ServerPlayer player, String node) {
        if (live != null || !ShepherdDialogues.nodes().containsKey(node)) return;
        var id = ShepherdSavedData.get(player.getServer()).entity();
        if (id == null || !(player.serverLevel().getEntity(id) instanceof ShepherdEntity she) || she.offline()) return;
        live = new Live(player, she);
        if (!begin(node, false)) live = null;
    }

    /** False, with nothing changed, if an edited datapack leaves the walk nowhere to stand. */
    private static boolean begin(String node, boolean fromHub) {
        var player = live.player;
        Predicate<String> holds = condition -> holds(player, condition);
        if (!ShepherdConversation.opens(ShepherdDialogues.nodes(), node, holds)) return false;
        live.talk = new ShepherdConversation(ShepherdDialogues.nodes(), node, holds);
        live.fromHub = fromHub;
        live.affinityApplied = 0;
        settle();
        if (FIRST_MEETING.equals(node)) live.she.showLogo();
        send();
        return true;
    }

    /** The dialogue files' conditions: her tier, where the wand is, or else a saved flag. */
    private static boolean holds(ServerPlayer player, String condition) {
        var tier = ShepherdSavedData.get(player.getServer()).tier();
        return switch (condition) {
            case "tier_wary" -> tier == Tier.WARY;
            case "tier_friendly" -> tier.atLeast(Tier.FRIENDLY);
            case "tier_trusted" -> tier.atLeast(Tier.TRUSTED);
            case "tier_close" -> tier.atLeast(Tier.CLOSE);
            case "wand_cradle" -> ShepherdWand.of(player) == ShepherdWand.Where.CRADLE;
            case "wand_hand" -> ShepherdWand.of(player) == ShepherdWand.Where.HAND;
            case "wand_other" -> ShepherdWand.of(player) == ShepherdWand.Where.OTHER;
            default -> ShepherdSavedData.get(player.getServer()).flag(condition);
        };
    }

    private static boolean speaking(ServerPlayer player, int conversation) {
        return live != null && live.player == player && live.id == conversation;
    }

    public static void reply(ServerPlayer player, int conversation, int slot) {
        if (!speaking(player, conversation) || live.talk == null || !live.talk.answer(slot)) return;
        spendPrompt();
        settle();
        if (!live.talk.finished()) {
            send();
        } else if (live.fromHub) {
            showHub();
        } else {
            end(true);
        }
    }

    /** A newer prompt raised while this one was on screen is not the one that was answered. */
    private static void spendPrompt() {
        var data = data();
        if (live.fromPrompt != null && live.fromPrompt.equals(data.promptNode())) data.promptNode(null);
        live.fromPrompt = null;
    }

    public static void action(ServerPlayer player, int conversation, Action action) {
        if (!speaking(player, conversation) || live.talk != null) return;
        var data = data();
        switch (action) {
            case TALK -> {
                if (ShepherdDialogues.nodes().containsKey(HUB_TALK)) begin(HUB_TALK, true);
                return;
            }
            case FOLLOW -> {
                if (follows(data, player)) {
                    data.waiting(true);
                } else {
                    data.follower(player.getUUID());
                    data.waiting(false);
                }
            }
            case LAMP -> data.lamp(!data.lamp());
            case SETTINGS -> swearing(data, !data.heavy());
        }
        showHub();
    }

    /** From the client: its screen has already gone. */
    public static void close(ServerPlayer player) {
        if (live != null && live.player == player) end(false);
    }

    private static boolean follows(ShepherdSavedData data, ServerPlayer player) {
        return !data.waiting() && player.getUUID().equals(data.follower());
    }

    private static void swearing(ShepherdSavedData data, boolean heavy) {
        data.heavy(heavy);
        data.flag("profanity_heavy", heavy);
        data.flag("profanity_mild", !heavy);
    }

    private static ShepherdSavedData data() {
        return ShepherdSavedData.get(live.player.getServer());
    }

    /** Scores and flags land as they are earned, so walking away halfway keeps them and a resume cannot earn them twice. */
    private static void settle() {
        var data = data();
        int earned = live.talk.affinityDelta() - live.affinityApplied;
        if (earned != 0) data.affinity(data.affinity() + earned);
        live.affinityApplied = live.talk.affinityDelta();
        for (String flag : live.talk.flags()) data.flag(flag, true);
        if (live.talk.flags().contains("profanity_heavy")) swearing(data, true);
        if (live.talk.flags().contains("profanity_mild")) swearing(data, false);
    }

    private static void send() {
        var data = data();
        var node = live.talk.current();
        // An unspent prompt resumes by its own id, whichever node it opened on, or it would stop being that prompt.
        if (!live.fromHub) data.resumeNode(node.responses().isEmpty() && node.next() == null ? null : live.fromPrompt != null ? live.fromPrompt : node.id());
        int logo = live.she.logoTicks();
        live.lines = live.talk.lines();
        live.line = 0;
        live.lineTicks = logo + TALK_TICKS_PER_LINE;
        showMood();
        live.she.talkFor(logo + TALK_TICKS_PER_LINE * live.lines.size());
        PacketDistributor.sendToPlayer(live.player, payload(live.id, node, live.lines, data.heavy(), logo));
    }

    private static void showHub() {
        var data = data();
        live.talk = null;
        live.fromHub = false;
        live.she.talkFor(0);
        showMood();
        PacketDistributor.sendToPlayer(live.player, new ShepherdHubPayload(live.id, !follows(data, live.player), data.lamp(), data.heavy()));
    }

    private static void showMood() {
        if (live.unimpressedTicks > 0) return;
        if (live.talk == null || live.lines.isEmpty()) {
            live.she.composed();
        } else {
            live.she.setMood(live.lines.get(live.line).mood());
        }
    }

    static ShepherdNodePayload payload(int conversation, Node node, List<Line> said, boolean heavy, int logoTicks) {
        var lines = said.stream().map(line -> new ShepherdNodePayload.Line(
                heavy && line.heavyKey() != null ? line.heavyKey() : line.key(), line.mood())).toList();
        var replies = node.responses().stream().map(response -> new ShepherdNodePayload.Reply(
                response.slot().ordinal(), response.label(), response.full())).toList();
        return new ShepherdNodePayload(conversation, node.id(), lines, replies, replies.isEmpty(), logoTicks);
    }

    private static void end(boolean tellClient) {
        var over = live;
        live = null;
        over.she.talkFor(0);
        over.she.rest();
        if (tellClient && !over.player.hasDisconnected()) PacketDistributor.sendToPlayer(over.player, new ShepherdClosePayload());
    }

    /** {@code /fd shepherd replay}, for trying the first meeting's other branches. False while she sleeps. */
    public static boolean replay(MinecraftServer server) {
        var data = ShepherdSavedData.get(server);
        if (data.stage() != ShepherdSavedData.Stage.AWAKE) return false;
        if (live != null) end(true);
        data.replayFirstMeeting();
        MillerPattern.reset(server);
        return true;
    }

    static void dialoguesReloaded() {
        if (live != null) end(true);
    }

    private static double nearestHostileOnSpeaker() {
        var player = live.player;
        double nearest = Double.MAX_VALUE;
        for (var mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ShepherdPolicy.DANGER_RANGE), mob -> mob.getTarget() == player)) {
            nearest = Math.min(nearest, mob.distanceTo(player));
        }
        return nearest;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (live == null) return;
        if (live.unimpressedTicks > 0 && --live.unimpressedTicks == 0) showMood();
        if (live.talk != null && live.line < live.lines.size() - 1 && --live.lineTicks <= 0) {
            live.line++;
            live.lineTicks = TALK_TICKS_PER_LINE;
            showMood();
        }
        var player = live.player;
        var she = live.she;
        boolean gone = player.isRemoved() || player.hasDisconnected() || she.isRemoved() || she.offline() || player.level() != she.level();
        if (gone || ShepherdPolicy.talkMustClose(player.distanceTo(she), live.hurt, nearestHostileOnSpeaker())) end(true);
    }

    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (live != null && event.getEntity() == live.player) live.hurt = true;
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        live = null;
    }

    static boolean live() {
        return live != null;
    }

    static int liveId() {
        return live == null ? 0 : live.id;
    }

    /** The node on screen, or null on the hub or when nobody is talking. */
    static String liveNode() {
        return live == null || live.talk == null ? null : live.talk.current().id();
    }

    /** The keys on screen. */
    static List<String> liveLines() {
        return live == null ? List.of() : live.lines.stream().map(Line::key).toList();
    }

    /** GameTest isolation only. */
    static void clearForTests() {
        live = null;
    }
}
