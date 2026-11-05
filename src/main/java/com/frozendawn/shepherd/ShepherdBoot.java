package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ShepherdBootOfferPayload;
import com.frozendawn.network.ShepherdClosePayload;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What stands between the dock and her first word: the choice, then the BIOS. She has no body and no conversation here,
 * so this is not {@link ShepherdTalk}. Only the dock's cell and timer are saved; a screen does not survive a reload, so
 * after one the choice is offered again and a first meeting past Boot waits on a right-click.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdBoot {
    public static final int LINE_COUNT = 11, LINE_TICKS = 8, HOLD_TICKS = 20;
    public static final int BIOS_TICKS = LINE_COUNT * LINE_TICKS + HOLD_TICKS;
    private static final Cells CURSOR = new Cells() {
        @Override public boolean lit(int x, int y) {
            return x == 0 && y == 0;
        }

        @Override public boolean dim(int x, int y) {
            return false;
        }
    };

    private static final class Screen {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos dock;
        /** Null on a dock. A replay has her body, with no dock to answer for. */
        final ShepherdEntity she;
        boolean booting, hurt;

        Screen(ServerPlayer player, ServerLevel level, BlockPos dock, ShepherdEntity she) {
            this.player = player;
            this.level = level;
            this.dock = dock;
            this.she = she;
        }

        boolean troubled() {
            var at = she != null ? she.position() : dock.getCenter();
            return ShepherdPolicy.talkMustClose(player.position().distanceTo(at), hurt, ShepherdTalk.nearestHostileOn(player));
        }

        /** Nothing left to answer: the dock has gone, she woke on another one, or her body has gone. */
        boolean stale() {
            if (she != null) return she.isRemoved() || she.offline();
            return !booting && (ShepherdSavedData.get(level.getServer()).stage() != Stage.DORMANT || !seated(level, dock));
        }

        boolean gone() {
            return player.isRemoved() || player.hasDisconnected() || player.level() != level;
        }
    }

    /** The one screen open on her, at the choice or on the BIOS. */
    private static Screen screen;

    private ShepherdBoot() {}

    /** How many BIOS lines show at this age, counted from Boot. */
    public static int shownLines(int age) {
        return Math.min(LINE_COUNT, age / LINE_TICKS + 1);
    }

    /** Her display while the lines run: it cannot hold them. */
    public static Cells cursor() {
        return CURSOR;
    }

    private static boolean seated(ServerLevel level, BlockPos dock) {
        var state = level.getBlockState(dock);
        return state.getBlock() instanceof ShepherdDockBlock && state.getValue(ShepherdDockBlock.STATE) == DockState.SEATED;
    }

    /** A right-click on a seated dock. One choice at a time: a second player gets the refuse click. */
    static void offer(ServerPlayer player, BlockPos dock) {
        var level = player.serverLevel();
        if (screen != null || ShepherdSavedData.get(player.getServer()).stage() != Stage.DORMANT) refuse(level, dock);
        else open(new Screen(player, level, dock.immutable(), null));
    }

    /** A right-click on her while a replay waits. */
    static void offer(ServerPlayer player, ShepherdEntity she) {
        if (screen != null) {
            refuse(player.serverLevel(), she.blockPosition());
            return;
        }
        she.bootDark();
        open(new Screen(player, player.serverLevel(), she.blockPosition(), she));
    }

    private static void refuse(ServerLevel level, BlockPos at) {
        level.playSound(null, at, ModSounds.CS1_DOCK_REFUSE.get(), SoundSource.BLOCKS, 1, 1);
    }

    private static void open(Screen opened) {
        screen = opened;
        PacketDistributor.sendToPlayer(opened.player, new ShepherdBootOfferPayload());
    }

    /** From the client. An answer from anyone not offered the choice moves nothing. */
    public static void answer(ServerPlayer player, boolean boot) {
        if (!offered() || screen.player != player) return;
        if (!boot) {
            release();
            return;
        }
        if (screen.she != null) {
            bootReplay();
            return;
        }
        if (screen.stale() || !(screen.level.getBlockEntity(screen.dock) instanceof ShepherdDockBlockEntity dock) || dock.seated().isEmpty()) {
            close();
            return;
        }
        screen.booting = true;
        dock.wake(player);
    }

    private static void bootReplay() {
        var she = screen.she;
        ShepherdSavedData.get(she.getServer()).flag(ShepherdSavedData.BOOT_WAITING, false);
        screen.booting = true;
        she.bootFor(BIOS_TICKS);
        she.level().playSound(null, she, ModSounds.CS1_DOCK_BOOT.get(), SoundSource.NEUTRAL, 1, 1);
    }

    /** Her body has run the lines out. */
    static void biosDone(ShepherdEntity she) {
        if (screen != null && screen.she == she) liftedOff(screen.player);
    }

    /** She has lifted off. {@code waker} is null if they are not in the level, and a first meeting never opens on someone in trouble. */
    static void liftedOff(Player waker) {
        var over = screen;
        screen = null;
        if (over == null) return;
        if (over.booting && over.player == waker && !over.troubled()) ShepherdTalk.start(over.player, ShepherdTalk.FIRST_MEETING);
        if (!ShepherdTalk.speakingTo(over.player)) tellClosed(over.player);
    }

    /** Past Boot her body finishes the lines whatever happens to the screen. Short of it, her display comes back. */
    private static ServerPlayer release() {
        var over = screen;
        screen = null;
        if (over.she != null && !over.booting) over.she.bootStop();
        return over.player;
    }

    private static void close() {
        tellClosed(release());
    }

    /** A replay starts everything over, a boot in progress included. */
    static void stop() {
        if (screen == null) return;
        if (screen.she != null) screen.she.bootStop();
        close();
    }

    private static void tellClosed(ServerPlayer player) {
        if (!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, new ShepherdClosePayload());
    }

    /** Nothing has closed the lines long after they ran out, so the player may. */
    public static boolean stuck(int age) {
        return age >= BIOS_TICKS * 3;
    }

    public static boolean offered() {
        return screen != null && !screen.booting;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (screen == null) return;
        if (screen.gone()) release();
        else if (screen.stale() || screen.troubled()) close();
    }

    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (screen != null && event.getEntity() == screen.player) screen.hurt = true;
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        screen = null;
    }

    /** GameTest isolation only. */
    static void clearForTests() {
        screen = null;
    }
}
