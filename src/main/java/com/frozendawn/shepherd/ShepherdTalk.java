package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.miller.SelfModel;
import com.frozendawn.miller.Heard;
import com.frozendawn.miller.Intent;
import com.frozendawn.miller.IntentIndex;
import com.frozendawn.miller.IntentIndex.Band;
import com.frozendawn.miller.LogEntry;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.Refusal;
import com.frozendawn.miller.TypedCheck;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdCrashPayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdLogPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.world.BlastPitPlacement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final String HUB_TALK = "hub.talk.quiet", HUB_WARY = "hub.talk", LOG_READ = "hub.log.read", CRASH_LINE = "crash.wary";
    private static final int UNIMPRESSED_TICKS = 40;
    private static final int TALK_TICKS_PER_LINE = 80;
    private static final String FOUND = "found:", REMEMBERS = "remembers:", CARRYING = "carrying:", NOT = "not:", AND = "&";
    private static final String CLEAR_NIGHT = "clear_night", LEAN = "t5_lean";
    /** The ground ref when there is no pit to give one for, as {@code TowerArchive} writes it. */
    private static final String NO_REF = "---";

    public enum Action {
        TALK,
        FOLLOW,
        LAMP,
        LANGUAGE,
        LOG,
        LOG_DONE
    }

    private static final class Live {
        final int id = nextId++;
        final ServerPlayer player;
        final ShepherdEntity she;
        /** Null while the hub is showing. */
        ShepherdConversation talk;
        /** The dialogue files, with what is built for this walk laid over them. {@code talk} reads it live, so a menu can change under it. */
        Map<String, Node> nodes = Map.of();
        /** What a typed line built, kept while the walk it started lasts. */
        Map<String, Node> built = Map.of();
        /** A typed line is being answered: it runs as if from the hub, so it never touches what is waiting. */
        boolean detour;
        /** Where a detour goes when it is done, one level deep. A scene's node, {@link ShepherdTopics#AFTER} for the menu, or null for the hub. */
        String back;
        /** The next line sent answers something typed, so she works it out first. */
        boolean typed;
        /** Pressing Talk and typing are the same thing to her, and count once a conversation. */
        boolean talked;
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
        ShepherdHubPayload hub;
        /** The log it was last sent, if it has asked. */
        ShepherdLogPayload log;

        Live(ServerPlayer player, ShepherdEntity she) {
            this.player = player;
            this.she = she;
            she.askFor(0);
            ShepherdSavedData.get(player.getServer()).lastTalk(player.serverLevel().getGameTime());
        }
    }

    /** One line and how she took it. Only the lab hears these. */
    public record TypedLine(String text, String band, String first, double firstScore, String second, double secondScore) {}

    /** Whom she last said no to. It need not outlive the server: thirty seconds is all it is for. */
    private record Refused(UUID who, Refusal.Kind kind, long at) {}

    /** Null in a normal build, which keeps no record of typed text. */
    public static Consumer<TypedLine> typedListener;

    private static int nextId = 1;
    private static Live live;
    /** Who said the word, so the line after it is theirs even if they walked off. Lost on a reload, and then it is whoever is near. */
    private static UUID crashedBy;
    /** Where she is in the miss pool. It need not outlive the server. */
    private static int missTurn;
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
        if (begin(waiting, LOG_READ.equals(waiting))) return;
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
        if (owesTheRead(data, live.player)) return LOG_READ;
        String prompt = data.promptNode();
        if (prompt != null && ShepherdReactions.quiet(data, live.player.getServer().overworld().getGameTime())) return null;
        if (prompt == null || nodes.containsKey(prompt)) return prompt;
        data.promptNode(null);
        return null;
    }

    /** They read it and she has not said so. Her own voice cannot play at Wary, so there it is dropped and counts as said, and a quiet day only holds it back. */
    private static boolean owesTheRead(ShepherdSavedData data, ServerPlayer player) {
        var server = player.getServer();
        var who = player.getUUID();
        if (!MillerPattern.hasRead(server, who) || MillerPattern.toldOff(server, who)) return false;
        if (data.tier() == Tier.WARY) {
            MillerPattern.markTold(server, who);
            return false;
        }
        return !ShepherdReactions.quiet(data, server.overworld().getGameTime()) && ShepherdDialogues.nodes().containsKey(LOG_READ);
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
        return begin(node, fromHub, FIRST_MEETING.equals(node));
    }

    private static boolean begin(String node, boolean fromHub, boolean logo) {
        var player = live.player;
        Predicate<String> holds = condition -> holds(player, condition);
        live.nodes = new HashMap<>(ShepherdDialogues.nodes());
        live.nodes.putAll(live.built);
        layTopics();
        if (!ShepherdConversation.opens(live.nodes, node, holds)) return false;
        live.talk = new ShepherdConversation(live.nodes, node, holds);
        live.fromHub = fromHub;
        live.affinityApplied = 0;
        settle();
        if (logo) live.she.showLogo();
        send();
        return true;
    }

    private static Predicate<String> holds() {
        var player = live.player;
        return condition -> holds(player, condition);
    }

    /** What is on the menu for this speaker. A topic with its node gone from an edited datapack is not. */
    private static List<Intent> openTopics() {
        var holds = holds();
        return ShepherdIntents.intents().stream().filter(intent -> intent.label() != null && IntentIndex.open(intent, holds) && ShepherdDialogues.nodes().containsKey(intent.node())).toList();
    }

    /** The menu as it stands. A topic ends back on it, unless a detour has somewhere else to be. */
    private static void layTopics() {
        var holds = holds();
        var open = openTopics();
        live.nodes.putAll(ShepherdTopics.menu(open, holds, false));
        if (live.detour && !ShepherdTopics.AFTER.equals(live.back)) live.nodes.remove(ShepherdTopics.AFTER);
        else live.nodes.put(ShepherdTopics.AFTER, ShepherdTopics.menu(open, holds, true).get(ShepherdTopics.MENU));
    }

    /**
     * The dialogue files' conditions: her tier, how far she has got, where the wand is, what this speaker has read, carries or she holds of them, or else a saved flag.
     * {@code &} joins them and every part must hold, so {@code not:} reaches only its own part. An empty part fails.
     */
    static boolean holds(ServerPlayer player, String condition) {
        if (condition.contains(AND)) return Arrays.stream(condition.split(AND, -1)).allMatch(part -> holds(player, part));
        if (condition.isEmpty() || condition.equals(NOT)) return false;
        var server = player.getServer();
        if (condition.startsWith(NOT)) return !holds(player, condition.substring(NOT.length()));
        if (condition.startsWith(FOUND)) return found(player, condition.substring(FOUND.length()));
        if (condition.startsWith(CARRYING)) return carrying(player, condition.substring(CARRYING.length()));
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
            case "stars_before" -> starsBefore(player);
            case "lean_someone" -> MillerPattern.leaning(server) == SelfModel.Lean.SOMEONE;
            case "lean_left" -> MillerPattern.leaning(server) == SelfModel.Lean.LEFT;
            case "stage_noticing" -> MillerPattern.stage(server).compareTo(SelfModel.Stage.NOTICING) >= 0;
            case "stage_walls" -> MillerPattern.stage(server).compareTo(SelfModel.Stage.FINDING_WALLS) >= 0;
            case "wand_cradle" -> ShepherdWand.of(player) == ShepherdWand.Where.CRADLE;
            case "wand_hand" -> ShepherdWand.of(player) == ShepherdWand.Where.HAND;
            case "wand_other" -> ShepherdWand.of(player) == ShepherdWand.Where.OTHER;
            case "conspiracy" -> WinConditionState.get(server).isConspiracyDiscovered();
            default -> ShepherdSavedData.get(player.getServer()).flag(condition);
        };
    }

    /** A clear night she kept with them before the stars came back. Every one since is a night like this one, and proves nothing about having seen them. */
    private static boolean starsBefore(ServerPlayer player) {
        var server = player.getServer();
        var state = ApocalypseState.get(server);
        long now = server.overworld().getGameTime();
        return MillerPattern.keepsakes(server, player.getUUID()).stream().anyMatch(moment -> CLEAR_NIGHT.equals(moment.kind())
                && ShepherdPolicy.beforeTheStars(state.getApocalypseTicks() - (now - moment.time()), state.getTotalDays()));
    }

    /** Whether this player has filed that document. The advancement is the record, and it is theirs alone. */
    private static boolean found(ServerPlayer player, String doc) {
        var holder = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_" + doc));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Whether this speaker has one of our items on them. An item that does not exist must not read as air, which every empty slot is. */
    private static boolean carrying(ServerPlayer player, String id) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, id));
        return item.isPresent() && player.getInventory().contains(stack -> stack.is(item.get()));
    }

    /** What a line's argument comes to, for the one she is talking to. The ground ref is read fresh: the target can be rerolled until the pit is built. */
    private static String arg(ServerPlayer player, String token) {
        if ("offline_days".equals(token)) return String.valueOf(ShepherdPolicy.offlineDays(ShepherdSavedData.get(player.getServer()).wokeAt()));
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

    /** Nothing reaches her while she is down, though the talk waits. */
    private static boolean speaking(ServerPlayer player, int conversation) {
        return live != null && live.player == player && live.id == conversation && !live.she.offline();
    }

    public static void reply(ServerPlayer player, int conversation, int slot) {
        if (!speaking(player, conversation) || live.talk == null) return;
        var answered = live.talk.current();
        boolean leaving = ShepherdTopics.leaves(answered, slot);
        String paid = leaving ? null : ShepherdTopics.paid(answered);
        boolean spent = paid != null && data().flag(paid);
        boolean readToThem = LOG_READ.equals(answered.id()), plain = answered.plain() || leaving || spent, detour = live.detour;
        boolean scene = detour && ShepherdTopics.NONE.equals(answered.id()) && live.back != null && !ShepherdTopics.AFTER.equals(live.back);
        int owed = live.talk.affinityDelta();
        layTopics();
        if (!live.talk.answer(slot)) return;
        if (spent) live.affinityApplied += live.talk.affinityDelta() - owed;
        else if (paid != null) data().flag(paid, true);
        // Only once they have heard it: cut off partway, she says it again at the next talk.
        if (readToThem) MillerPattern.markTold(player.getServer(), player.getUUID());
        hear(plain ? ShepherdConversation.CONTINUE : slot);
        if (scene) rejoin();
        if (!detour || scene) spendPrompt();
        settle();
        if (!live.talk.finished()) {
            if (live.talk.current().id().startsWith(ShepherdTopics.MENU)) onTheMenu();
            send();
        } else if (live.detour || live.back != null) {
            goBack();
        } else if (live.fromHub) {
            showHub();
        } else {
            end(true);
        }
    }

    /** An answer on a miss in a scene is the scene's own answer, and the walk is the scene again. */
    private static void rejoin() {
        live.detour = false;
        live.back = null;
        live.fromHub = false;
        live.built = Map.of();
    }

    /** A walk that reaches the menu is a menu walk: its topics come back here. */
    private static void onTheMenu() {
        live.detour = false;
        if (ShepherdTopics.AFTER.equals(live.back)) live.back = null;
    }

    /** A detour is over: the scene says its line again, or the menu asks what else. */
    private static void goBack() {
        String back = live.back;
        live.detour = false;
        live.back = null;
        live.built = Map.of();
        if (back == null) showHub();
        else if (!begin(back, ShepherdTopics.AFTER.equals(back), false)) end(true);
    }

    /** One line from the bar, in the order Ruling 7 sets. It is held for this call and goes nowhere else. */
    public static void typed(ServerPlayer player, int conversation, String text) {
        if (!speaking(player, conversation) || live.she.thinking() || live.she.logoTicks() > 0) return;
        String line = TypedCheck.clean(text);
        if (line.isEmpty()) return;
        if (TypedCheck.crashes(line)) {
            if (typedListener != null) typedListener.accept(new TypedLine(line, "CRASH", null, 0, null, 0));
            crash();
            return;
        }
        var holds = holds();
        var ranked = ShepherdIntents.index().rank(line);
        var match = TypedCheck.guarded(line) || ranked == null ? IntentIndex.MISS : IntentIndex.judge(ranked, holds);
        if (typedListener != null) typedListener.accept(new TypedLine(line, match.band().name(), ranked == null ? null : ranked.first().id(), ranked == null ? 0 : ranked.firstScore(),
                ranked == null ? null : ranked.second().id(), ranked == null ? 0 : ranked.secondScore()));
        talked();
        leaveFor();
        if (holds.test("quiet") || holds.test("tier_silent")) {
            answerTyped(HUB_TALK);
        } else if (match.band() == Band.STRONG && ShepherdDialogues.nodes().containsKey(match.first().node())) {
            answerTyped(match.first().node());
        } else if (data().tier() == Tier.WARY) {
            answerTyped(HUB_WARY);
        } else if (match.band() == Band.CLOSE) {
            live.built = Map.of(ShepherdTopics.CLOSE, ShepherdTopics.close(match), ShepherdTopics.NONE, miss());
            answerTyped(ShepherdTopics.CLOSE);
        } else {
            live.built = Map.of(ShepherdTopics.NONE, miss());
            answerTyped(ShepherdTopics.NONE);
        }
    }

    /** She does not hear it: she goes down. Nothing reaches the Pattern, so it costs nothing and she keeps no record. */
    private static void crash() {
        crashedBy = live.player.getUUID();
        live.she.crash();
        live.lines = List.of();
        live.line = 0;
        PacketDistributor.sendToPlayer(live.player, new ShepherdCrashPayload(live.id));
    }

    /** {@code /fd shepherd crash}. False if she cannot go down. */
    public static boolean crash(ServerPlayer player) {
        var id = ShepherdSavedData.get(player.getServer()).entity();
        if (id == null || !(player.serverLevel().getEntity(id) instanceof ShepherdEntity she) || she.offline()) return false;
        if (live != null && live.she == she) {
            crash();
        } else {
            crashedBy = player.getUUID();
            she.crash();
        }
        return true;
    }

    /** The noise has stopped. The screen shows the line itself; with nobody on it, the strip does. */
    static void crashStill(ShepherdEntity she) {
        if (live == null || live.she != she) ShepherdReactions.crashLine(she, ShepherdGlitch.VERDICT, Mood.OFFLINE);
    }

    /** The logo has started. Her line follows it, over the hub or as a node before the scene she was in. With the screen gone it goes to the strip, and the talk does not reopen. */
    static void crashOver(ShepherdEntity she) {
        if (live == null || live.she != she) {
            crashLine(she);
            return;
        }
        String scene = live.detour ? live.back : live.talk != null && !live.fromHub ? live.talk.current().id() : null;
        if (scene == null || ShepherdTopics.AFTER.equals(scene)) {
            sayOnTheHub(CRASH_LINE);
            return;
        }
        live.detour = true;
        live.back = scene;
        if (!begin(CRASH_LINE, true)) goBack();
    }

    /** Whoever did it if they are still here, else whoever is near: the line depends on who it is to. A save reloaded with nobody near hears nothing. */
    private static void crashLine(ShepherdEntity she) {
        var server = she.getServer();
        var who = crashedBy == null ? null : server.getPlayerList().getPlayer(crashedBy);
        if (who == null && she.level() instanceof ServerLevel level) who = (ServerPlayer) level.getNearestPlayer(she, ShepherdPolicy.HEARD_BLOCKS);
        crashedBy = null;
        if (who == null) return;
        var player = who;
        Predicate<String> holds = condition -> holds(player, condition);
        var nodes = ShepherdDialogues.nodes();
        if (!ShepherdConversation.opens(nodes, CRASH_LINE, holds)) return;
        var walk = new ShepherdConversation(nodes, CRASH_LINE, holds);
        var data = ShepherdSavedData.get(server);
        walk.flags().forEach(flag -> data.flag(flag, true));
        var line = walk.lines().getFirst();
        ShepherdReactions.crashLine(she, line.key(), line.mood());
    }

    /** Where this detour goes back to. Typing during a detour replaces it and keeps that. */
    private static void leaveFor() {
        if (live.detour) return;
        live.detour = true;
        if (live.talk == null) live.back = null;
        else if (!live.fromHub) live.back = live.talk.current().id();
        else live.back = live.talk.current().id().startsWith(ShepherdTopics.MENU) ? ShepherdTopics.AFTER : null;
    }

    private static void answerTyped(String node) {
        live.typed = true;
        if (!begin(node, true)) live.typed = false;
    }

    /** The same pool, the same turn and the same slots, whatever was typed: a locked topic must not show. */
    private static Node miss() {
        int reach = MillerPattern.variety(live.player.getServer(), ShepherdTopics.misses());
        int turn = missTurn++ % reach;
        var scene = live.back == null || ShepherdTopics.AFTER.equals(live.back) ? null : ShepherdDialogues.nodes().get(live.back);
        return scene != null ? ShepherdTopics.none(turn, scene) : ShepherdTopics.none(turn, openTopics(), holds());
    }

    private static void talked() {
        if (live.talked) return;
        live.talked = true;
        MillerPattern.heard(live.player.getServer(), live.player.getUUID(), new Heard.Used(Action.TALK));
    }

    /** {@code /fd shepherd hear}: the band and the top two for this speaker, changing nothing. */
    public static List<String> hearing(ServerPlayer player, String text) {
        String line = TypedCheck.clean(text);
        var ranked = ShepherdIntents.index().rank(line);
        if (ranked == null) return List.of("no intents loaded");
        var match = IntentIndex.judge(ranked, condition -> holds(player, condition));
        return List.of("band=" + match.band() + (TypedCheck.crashes(line) ? " crash" : "") + (TypedCheck.guarded(line) ? " guarded" : ""),
                String.format(Locale.ROOT, "%s %.3f, %s %.3f", ranked.first().id(), ranked.firstScore(), ranked.second().id(), ranked.secondScore()));
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
        if (action == Action.TALK) talked();
        else MillerPattern.heard(player.getServer(), player.getUUID(), new Heard.Used(action));
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
            case LANGUAGE -> swearing(data, !data.heavy());
            case LOG -> {
                readLog(player);
                live.log = logPayload(live.id, MillerPattern.log(player.getServer()));
                PacketDistributor.sendToPlayer(player, live.log);
                return;
            }
            case LOG_DONE -> {
                if (owesTheRead(data, player) && begin(LOG_READ, true)) return;
            }
        }
        showHub();
    }

    /** The first read of anything costs, once. An empty log has nothing to read, and a read she could not charge is not paid for. */
    private static void readLog(ServerPlayer player) {
        var server = player.getServer();
        var who = player.getUUID();
        if (!MillerPattern.log(server).isEmpty() && !MillerPattern.hasRead(server, who) && ShepherdReactions.saw(player, ShepherdAffinity.Deed.READ_HER_LOG, false)) MillerPattern.markRead(server, who);
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
        if (live.talk.flags().contains(ShepherdPrompt.SKY_LATER)) ShepherdPrompt.askAboutTheSkyLater(data, live.player.getServer().overworld().getGameTime());
        if (live.talk.flags().contains(LEAN)) MillerPattern.lean(live.player.getServer(), live.player.getUUID());
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

    /**
     * She takes a moment when her coherence is low. The logo is its own wait, and a recording has nobody in it to think.
     * Something typed she always has to work out, even on a line that opens on a recording, unless she is at Wary.
     */
    private static int thinkTicks(int logo) {
        boolean typed = live.typed;
        live.typed = false;
        boolean recording = !live.lines.isEmpty() && live.lines.getFirst().mood() == Mood.RECORDING;
        if (logo > 0 || live.lines.isEmpty()) return 0;
        int halfLaps = MillerPattern.halfLaps(live.player.getServer());
        if (typed && data().tier() != Tier.WARY) return ShepherdFace.thinkingTicks(Math.max(1, halfLaps));
        return recording ? 0 : ShepherdFace.thinkingTicks(halfLaps);
    }

    private static void showHub() {
        showHub(List.of());
    }

    /** {@code said} plays over the hub, after any logo, and its buttons appear when she finishes. */
    private static void showHub(List<Line> said) {
        var data = data();
        live.talk = null;
        live.fromHub = false;
        live.detour = false;
        live.back = null;
        live.built = Map.of();
        live.lines = said;
        live.line = 0;
        int logo = live.she.logoTicks();
        live.lineTicks = logo + TALK_TICKS_PER_LINE;
        live.she.talkFor(said.isEmpty() ? 0 : logo + TALK_TICKS_PER_LINE * said.size());
        showMood();
        var player = live.player;
        live.hub = new ShepherdHubPayload(live.id, !follows(data, player), data.lamp(), data.heavy(), resolved(said, data.heavy(), token -> arg(player, token)), logo);
        PacketDistributor.sendToPlayer(player, live.hub);
    }

    /** A node's lines over the hub, as it stands for this speaker. */
    static void sayOnTheHub(String node) {
        var holds = holds();
        if (!ShepherdConversation.opens(ShepherdDialogues.nodes(), node, holds)) {
            showHub();
            return;
        }
        var walk = new ShepherdConversation(ShepherdDialogues.nodes(), node, holds);
        var data = data();
        walk.flags().forEach(flag -> data.flag(flag, true));
        showHub(walk.lines());
    }

    private static void showMood() {
        if (live.unimpressedTicks > 0) return;
        if (live.lines.isEmpty()) {
            live.she.composed();
        } else {
            live.she.setMood(live.lines.get(live.line).mood());
        }
    }

    static ShepherdNodePayload payload(int conversation, Node node, List<Line> said, boolean heavy, int logoTicks, int thinkTicks, UnaryOperator<String> arg) {
        var lines = resolved(said, heavy, arg);
        var replies = node.responses().stream().map(response -> new ShepherdNodePayload.Reply(
                response.slot().ordinal(), response.label(), response.full())).toList();
        return new ShepherdNodePayload(conversation, node.id(), lines, replies, replies.isEmpty(), logoTicks, thinkTicks, node.plain());
    }

    private static List<ShepherdNodePayload.Line> resolved(List<Line> said, boolean heavy, UnaryOperator<String> arg) {
        return said.stream().map(line -> new ShepherdNodePayload.Line(
                heavy && line.heavyKey() != null ? line.heavyKey() : line.key(), line.mood(), line.args().stream().map(arg).toList())).toList();
    }

    static ShepherdLogPayload logPayload(int conversation, List<LogEntry> entries) {
        return new ShepherdLogPayload(conversation, entries.stream().map(entry -> new ShepherdLogPayload.Page(entry.day(), entry.signed(), entry.cracked(), entry.note())).toList());
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
        if (live.line < live.lines.size() - 1 && --live.lineTicks <= 0) {
            live.line++;
            live.lineTicks = TALK_TICKS_PER_LINE;
            showMood();
        }
        var player = live.player;
        var she = live.she;
        boolean gone = player.isRemoved() || player.hasDisconnected() || she.isRemoved() || she.offline() && !she.crashed() || player.level() != she.level();
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

    /** The hub the client was last sent, or null. */
    static ShepherdHubPayload liveHub() {
        return live == null ? null : live.hub;
    }

    static ShepherdLogPayload liveLog() {
        return live == null ? null : live.log;
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
        crashedBy = null;
        missTurn = 0;
    }
}
