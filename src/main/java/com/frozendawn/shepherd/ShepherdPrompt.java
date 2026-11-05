package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.network.ShepherdPromptPayload;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import java.util.List;
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
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdPrompt {
    public static final String DOORS = "fm.doors", EXIT = "fm.exit";
    static final String FIRST_MEETING_DONE = "fm_done", DOORS_ASKED = "doors_asked", EXIT_ASKED = "exit_asked";
    /** A sprinter covers 1.4 blocks in this, so nobody crosses a box between looks. */
    private static final int CHECK_TICKS = 5;
    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    private static int shows;
    private static int nextShow;
    private static int repeat = ShepherdPolicy.PROMPT_REPEAT_TICKS;
    private static long lastHurt = LONG_AGO;

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

    private static void raise(ShepherdSavedData data, String asked, String node) {
        data.flag(asked, true);
        data.promptNode(node);
        shows = 0;
        nextShow = 0;
    }

    private static void show(MinecraftServer server, ServerLevel level, ShepherdSavedData data, List<ServerPlayer> near, ShepherdEntity she) {
        int now = server.getTickCount();
        if (data.promptNode() == null || shows >= ShepherdPolicy.PROMPT_SHOWS || now < nextShow || ShepherdTalk.live()) return;
        if (!ShepherdPolicy.calm(nearestHostile(level, near), now - lastHurt)) return;
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
        if (event.getEntity() instanceof ServerPlayer player) lastHurt = player.getServer().getTickCount();
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

    static void clearForTests() {
        shows = 0;
        nextShow = 0;
        repeat = ShepherdPolicy.PROMPT_REPEAT_TICKS;
        lastHurt = LONG_AGO;
    }
}
