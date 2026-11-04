package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.network.ShepherdHubPayload;
import com.frozendawn.network.ShepherdNodePayload;
import com.frozendawn.shepherd.ShepherdDialogue.Node;
import com.frozendawn.shepherd.ShepherdFace.Mood;
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
        boolean hurt;
        int affinityApplied;
        int unimpressedTicks;

        Live(ServerPlayer player, ShepherdEntity she) {
            this.player = player;
            this.she = she;
        }
    }

    private static int nextId = 1;
    private static Live live;

    private ShepherdTalk() {}

    /** A right-click. She picks an unfinished first meeting back up; otherwise this is the hub. */
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
        String resume = data.resumeNode();
        if (resume == null) {
            showHub();
        } else if (ShepherdDialogues.nodes().containsKey(resume)) {
            begin(resume, false);
        } else {
            data.resumeNode(null);
            showHub();
        }
    }

    public static void start(ServerPlayer player, String node) {
        if (live != null || !ShepherdDialogues.nodes().containsKey(node)) return;
        var id = ShepherdSavedData.get(player.getServer()).entity();
        if (id == null || !(player.serverLevel().getEntity(id) instanceof ShepherdEntity she) || she.offline()) return;
        live = new Live(player, she);
        begin(node, false);
    }

    private static void begin(String node, boolean fromHub) {
        live.talk = new ShepherdConversation(ShepherdDialogues.nodes(), node);
        live.fromHub = fromHub;
        live.affinityApplied = 0;
        settle();
        send();
    }

    private static boolean speaking(ServerPlayer player, int conversation) {
        return live != null && live.player == player && live.id == conversation;
    }

    public static void reply(ServerPlayer player, int conversation, int slot) {
        if (!speaking(player, conversation) || live.talk == null || !live.talk.answer(slot)) return;
        settle();
        if (!live.talk.finished()) {
            send();
        } else if (live.fromHub) {
            showHub();
        } else {
            end(true);
        }
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
        if (!live.fromHub) data.resumeNode(node.responses().isEmpty() && node.next() == null ? null : node.id());
        showMood();
        PacketDistributor.sendToPlayer(live.player, payload(live.id, node, data.heavy()));
    }

    private static void showHub() {
        var data = data();
        live.talk = null;
        live.fromHub = false;
        showMood();
        PacketDistributor.sendToPlayer(live.player, new ShepherdHubPayload(live.id, !follows(data, live.player), data.lamp(), data.heavy()));
    }

    private static void showMood() {
        if (live.unimpressedTicks > 0) return;
        live.she.setMood(live.talk == null ? Mood.NEUTRAL : live.talk.current().lines().getLast().mood());
    }

    static ShepherdNodePayload payload(int conversation, Node node, boolean heavy) {
        var lines = node.lines().stream().map(line -> new ShepherdNodePayload.Line(
                heavy && line.heavyKey() != null ? line.heavyKey() : line.key(), line.mood())).toList();
        var replies = node.responses().stream().map(response -> new ShepherdNodePayload.Reply(
                response.slot().ordinal(), response.label(), response.full())).toList();
        return new ShepherdNodePayload(conversation, node.id(), lines, replies, replies.isEmpty());
    }

    private static void end(boolean tellClient) {
        var over = live;
        live = null;
        over.she.setMood(Mood.NEUTRAL);
        if (tellClient && !over.player.hasDisconnected()) PacketDistributor.sendToPlayer(over.player, new ShepherdClosePayload());
    }

    /** {@code /fd shepherd replay}, for trying the first meeting's other branches. False while she sleeps. */
    public static boolean replay(MinecraftServer server) {
        var data = ShepherdSavedData.get(server);
        if (data.stage() != ShepherdSavedData.Stage.AWAKE) return false;
        if (live != null) end(true);
        data.replayFirstMeeting();
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

    /** GameTest isolation only. */
    static void clearForTests() {
        live = null;
    }
}
