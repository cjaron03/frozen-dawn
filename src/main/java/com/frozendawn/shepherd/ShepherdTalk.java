package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.miller.SelfModel;
import com.frozendawn.miller.Heard;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.Refusal;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.world.BlastPitPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
    private static final String HUB_TALK = "hub.talk.quiet";
    private static final int UNIMPRESSED_TICKS = 40;
    private static final int TALK_TICKS_PER_LINE = 80;
    private static final String FOUND = "found:", REMEMBERS = "remembers:";
    /** The ground ref when there is no pit to give one for, as {@code TowerArchive} writes it. */
    private static final String NO_REF = "---";

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
        /** How long the line on screen waited before it started. */
        int thinkTicks;
        /** What the client was last sent. */
        ShepherdNodePayload sent;

        Live(ServerPlayer player, ShepherdEntity she) {
            this.player = player;
            this.she = she;
            she.askFor(0);
            ShepherdSavedData.get(player.getServer()).lastTalk(player.serverLevel().getGameTime());
        }
    }

    /** Whom she last said no to. It need not outlive the server: thirty seconds is all it is for. */
    private record Refused(UUID who, Refusal.Kind kind, long at) {}

    private static int nextId = 1;
    private static Live live;
    private static Refused refused;
    /** Who she told she would catch up with. Lost on a reload, which leaves her waiting as she would anyway. */
    private static UUID catchingUp;

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
        if (ShepherdSavedData.get(player.getServer()).flag(ShepherdSavedData.BOOT_WAITING)) {
            ShepherdBoot.offer(player, she);
            return;
        }
        if (she.bootDisplay() != ShepherdEntity.BOOT_NONE) return;
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
        if (prompt != null && ShepherdReactions.quiet(data, live.player.getServer().overworld().getGameTime())) return null;
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

    /** The dialogue files' conditions: her tier, how far she has got, where the wand is, what this speaker has read or she holds of them, or else a saved flag. */
    static boolean holds(ServerPlayer player, String condition) {
        var server = player.getServer();
        if (condition.startsWith(FOUND)) return found(player, condition.substring(FOUND.length()));
        if (condition.startsWith(REMEMBERS)) return MillerPattern.remembers(server, player.getUUID(), condition.substring(REMEMBERS.length()));
        var tier = ShepherdSavedData.get(server).tier();
        return switch (condition) {
            case "tier_silent" -> ShepherdSavedData.get(player.getServer()).affinity() < ShepherdAffinity.SILENT_BELOW;
            case "tier_wary" -> tier == Tier.WARY;
            case "tier_above_wary" -> tier != Tier.WARY;
            case "tier_friendly" -> tier.atLeast(Tier.FRIENDLY);
            case "tier_trusted" -> tier.atLeast(Tier.TRUSTED);
            case "tier_close" -> tier.atLeast(Tier.CLOSE);
            case "pit_open" -> ShepherdSavedData.get(server).flag(ShepherdNine.OPEN) && !ShepherdSavedData.get(server).flag(ShepherdNine.SEEN);
            case "quiet" -> ShepherdReactions.quiet(ShepherdSavedData.get(server), server.overworld().getGameTime());
            case "stage_noticing" -> MillerPattern.stage(server).compareTo(SelfModel.Stage.NOTICING) >= 0;
            case "stage_walls" -> MillerPattern.stage(server).compareTo(SelfModel.Stage.FINDING_WALLS) >= 0;
            case "wand_cradle" -> ShepherdWand.of(player) == ShepherdWand.Where.CRADLE;
            case "wand_hand" -> ShepherdWand.of(player) == ShepherdWand.Where.HAND;
            case "wand_other" -> ShepherdWand.of(player) == ShepherdWand.Where.OTHER;
            default -> ShepherdSavedData.get(player.getServer()).flag(condition);
        };
    }

    /** Whether this player has filed that document. The advancement is the record, and it is theirs alone. */
    private static boolean found(ServerPlayer player, String doc) {
        var holder = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_" + doc));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** What a line's argument comes to, for the one she is talking to. The ground ref is read fresh: the target can be rerolled until the pit is built. */
    private static String arg(ServerPlayer player, String token) {
        var pit = BlastPitPlacement.ensureBlastPitResolved(player.getServer().overworld());
        if (pit == null) return NO_REF;
        int dx = pit.getX() - player.getBlockX(), dz = pit.getZ() - player.getBlockZ();
        return switch (token) {
            case "pit_x" -> String.valueOf(pit.getX());
            case "pit_z" -> String.valueOf(pit.getZ());
            case "pit_blocks" -> String.valueOf(ShepherdPolicy.groundBlocks(dx, dz));
            case "pit_bearing" -> ShepherdPolicy.bearing(dx, dz);
            default -> NO_REF;
        };
    }

    private static boolean speaking(ServerPlayer player, int conversation) {
        return live != null && live.player == player && live.id == conversation;
    }

    public static void reply(ServerPlayer player, int conversation, int slot) {
        if (!speaking(player, conversation) || live.talk == null || !live.talk.answer(slot)) return;
        hear(slot);
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

    /** What the reply told her about the speaker, and what the node it led to has to add. */
    private static void hear(int slot) {
        var tone = slot == ShepherdConversation.CONTINUE ? null : Slot.values()[slot];
        var node = live.talk.finished() ? null : live.talk.current();
        var noted = new ArrayList<>(live.talk.said());
        if (node != null) noted.addAll(node.noted());
        String keep = node == null ? null : node.keep();
        if (tone == null && noted.isEmpty() && keep == null) return;
        MillerPattern.heard(live.player.getServer(), live.player.getUUID(), new Heard.Reply(tone, noted, keep));
    }

    /** A newer prompt raised while this one was on screen is not the one that was answered. */
    private static void spendPrompt() {
        var data = data();
        if (live.fromPrompt != null && live.fromPrompt.equals(data.promptNode())) data.promptNode(null);
        live.fromPrompt = null;
    }

    public static void action(ServerPlayer player, int conversation, Action action) {
        if (!speaking(player, conversation) || live.talk != null) return;
        MillerPattern.heard(player.getServer(), player.getUUID(), new Heard.Used(action));
        var data = data();
        switch (action) {
            case TALK -> {
                if (ShepherdDialogues.nodes().containsKey(HUB_TALK)) begin(HUB_TALK, true);
                return;
            }
            case FOLLOW -> {
                if (follows(data, player)) {
                    data.waiting(true);
                } else if (follow(data, player)) {
                    return;
                }
            }
            case LAMP -> data.lamp(!data.lamp());
            case SETTINGS -> swearing(data, !data.heavy());
        }
        showHub();
    }

    /** She objects, or goes. True if she answered with a line, which takes the hub's place until it is done. */
    private static boolean follow(ShepherdSavedData data, ServerPlayer player) {
        var kind = refusal(player, live.she);
        long now = player.getServer().overworld().getGameTime();
        boolean yields = kind != null && refused != null && refused.who().equals(player.getUUID()) && refused.kind() == kind && now - refused.at() <= Refusal.YIELD_TICKS;
        if (kind != null && !yields) {
            refused = new Refused(player.getUUID(), kind, now);
            if (kind == Refusal.Kind.CLEAR_NIGHT) catchingUp = player.getUUID();
            if (begin(refusalNode(kind), true)) return true;
        }
        refused = null;
        catchingUp = null;
        data.follower(player.getUUID());
        data.waiting(false);
        return yields && begin(refusalNode(kind) + ".yield", true);
    }

    /** "I'll catch up." When the clear night is over she does, and staying was her doing, so nobody left her. */
    static void catchUp(ServerLevel level, ShepherdEntity she) {
        if (catchingUp == null || ShepherdReactions.clearNight(level, she.blockPosition())) return;
        var data = ShepherdSavedData.get(level.getServer());
        data.follower(catchingUp);
        data.waiting(false);
        data.lastNear(level.getServer().overworld().getGameTime());
        catchingUp = null;
    }

    private static String refusalNode(Refusal.Kind kind) {
        return kind == Refusal.Kind.CLEAR_NIGHT ? "hub.refuse.night" : "hub.refuse.archivist";
    }

    /** Worked out on the press, so the Pattern still scans nothing. At Wary she is equipment, and equipment has no opinions. */
    private static Refusal.Kind refusal(ServerPlayer player, ShepherdEntity she) {
        var server = player.getServer();
        if (ShepherdSavedData.get(server).tier() == Tier.WARY) return null;
        var level = (ServerLevel) she.level();
        boolean seen = !level.getEntitiesOfClass(ArchivistEntity.class, she.getBoundingBox().inflate(ShepherdPolicy.HEARD_BLOCKS), she::hasLineOfSight).isEmpty();
        return Refusal.check(ShepherdPrompt.calm(server, level, List.of(player)), ShepherdReactions.clearNight(level, she.blockPosition()), MillerPattern.skyWanted(server),
                seen, MillerPattern.remembers(server, player.getUUID(), "archivist_killed"));
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
        boolean firstAtNine = !data.flag(ShepherdNine.SEEN) && live.talk.flags().contains(ShepherdNine.SEEN);
        for (String flag : live.talk.flags()) data.flag(flag, true);
        if (firstAtNine) ShepherdNine.arrived(live.player);
        if (live.talk.flags().contains(ShepherdNine.HAND_OVER)) ShepherdNine.handOver(live.player);
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
        live.thinkTicks = thinkTicks(logo);
        int wait = logo + live.thinkTicks;
        live.lineTicks = wait + TALK_TICKS_PER_LINE;
        showMood();
        live.she.talkFor(wait + TALK_TICKS_PER_LINE * live.lines.size());
        live.she.thinkFor(live.thinkTicks);
        var player = live.player;
        live.sent = payload(live.id, node, live.lines, data.heavy(), logo, live.thinkTicks, token -> arg(player, token));
        PacketDistributor.sendToPlayer(player, live.sent);
    }

    /** She takes a moment when her coherence is low. The logo is its own wait, and a recording has nobody in it to think. */
    private static int thinkTicks(int logo) {
        boolean recording = !live.lines.isEmpty() && live.lines.getFirst().mood() == Mood.RECORDING;
        return logo > 0 || live.lines.isEmpty() || recording ? 0 : ShepherdFace.thinkingTicks(MillerPattern.halfLaps(live.player.getServer()));
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

    static ShepherdNodePayload payload(int conversation, Node node, List<Line> said, boolean heavy, int logoTicks, int thinkTicks, UnaryOperator<String> arg) {
        var lines = said.stream().map(line -> new ShepherdNodePayload.Line(
                heavy && line.heavyKey() != null ? line.heavyKey() : line.key(), line.mood(), line.args().stream().map(arg).toList())).toList();
        var replies = node.responses().stream().map(response -> new ShepherdNodePayload.Reply(
                response.slot().ordinal(), response.label(), response.full())).toList();
        return new ShepherdNodePayload(conversation, node.id(), lines, replies, replies.isEmpty(), logoTicks, thinkTicks);
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
        catchingUp = null;
        ShepherdBoot.stop();
        data.replayFirstMeeting();
        MillerPattern.reset(server);
        return true;
    }

    static void dialoguesReloaded() {
        if (live != null) end(true);
    }

    static double nearestHostileOn(ServerPlayer player) {
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
        if (gone || ShepherdPolicy.talkMustClose(player.distanceTo(she), live.hurt, nearestHostileOn(player))) end(true);
    }

    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (live != null && event.getEntity() == live.player) live.hurt = true;
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        clearForTests();
    }

    static boolean live() {
        return live != null;
    }

    static boolean speakingTo(ServerPlayer player) {
        return live != null && live.player == player;
    }

    static int liveId() {
        return live == null ? 0 : live.id;
    }

    /** The node on screen, or null on the hub or when nobody is talking. */
    static String liveNode() {
        return live == null || live.talk == null ? null : live.talk.current().id();
    }

    /** What the client was last sent, or null. */
    static ShepherdNodePayload liveSent() {
        return live == null ? null : live.sent;
    }

    /** How long the line on screen was made to wait. */
    static int liveThinkTicks() {
        return live == null ? 0 : live.thinkTicks;
    }

    /** The keys on screen. */
    static List<String> liveLines() {
        return live == null ? List.of() : live.lines.stream().map(Line::key).toList();
    }

    /** GameTest isolation only. */
    static void clearForTests() {
        live = null;
        refused = null;
        catchingUp = null;
    }
}
