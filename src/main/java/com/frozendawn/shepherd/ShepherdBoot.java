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
        boolean booting, hurt;

        Screen(ServerPlayer player, ServerLevel level, BlockPos dock) {
            this.player = player;
            this.level = level;
            this.dock = dock;
        }

        boolean troubled() {
            return ShepherdPolicy.talkMustClose(player.position().distanceTo(dock.getCenter()), hurt, ShepherdTalk.nearestHostileOn(player));
        }

        /** Nothing left to answer: the dock has gone, or she woke on another one. */
        boolean stale() {
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
        if (screen != null || ShepherdSavedData.get(player.getServer()).stage() != Stage.DORMANT) {
            level.playSound(null, dock, ModSounds.CS1_DOCK_REFUSE.get(), SoundSource.BLOCKS, 1, 1);
            return;
        }
        screen = new Screen(player, level, dock.immutable());
        PacketDistributor.sendToPlayer(player, new ShepherdBootOfferPayload());
    }

    /** From the client. An answer from anyone not offered the choice moves nothing. */
    public static void answer(ServerPlayer player, boolean boot) {
        if (!offered() || screen.player != player) return;
        if (!boot) {
            screen = null;
            return;
        }
        if (screen.stale() || !(screen.level.getBlockEntity(screen.dock) instanceof ShepherdDockBlockEntity dock) || dock.seated().isEmpty()) {
            close();
            return;
        }
        screen.booting = true;
        dock.wake(player);
    }

    /** She has lifted off. {@code waker} is null if they are not in the level, and a first meeting never opens on someone in trouble. */
    static void liftedOff(Player waker) {
        var over = screen;
        screen = null;
        if (over == null) return;
        if (over.booting && over.player == waker && !over.troubled()) ShepherdTalk.start(over.player, ShepherdTalk.FIRST_MEETING);
        if (!ShepherdTalk.live()) tellClosed(over.player);
    }

    private static void close() {
        var over = screen;
        screen = null;
        tellClosed(over.player);
    }

    private static void tellClosed(ServerPlayer player) {
        if (!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, new ShepherdClosePayload());
    }

    public static boolean offered() {
        return screen != null && !screen.booting;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (screen == null) return;
        if (screen.gone()) screen = null;
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
