package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.lore.ThaevenLoopBeat;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.network.ThaevenLoopPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * CH-37's loop lines during the Heart, in the Master Architect band. Each line inks in from its Record III raw line
 * with the archive's fracture and ghosting, and the ghosts never fully settle (owner mockup, 2026-11-09). A beat
 * waits while the opening, the replay or the boot cue runs, so the broken lines land after the boot cue's blink.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ThaevenLoopOverlay {
    static final int FADE_TICKS = 10;
    static final int HOLD_TICKS = 80;
    static final int TOTAL_TICKS = HOLD_TICKS + 2 * FADE_TICKS;
    /** How long a line takes to ink from raw to English. */
    static final int INK_TICKS = 50;
    /** Quiet ticks after the intro or boot cue before a beat starts, so a cue arriving with it goes first. */
    static final int SETTLE_TICKS = 5;
    private static final float RESTING_UNREST = 0.30F;

    private static final Deque<ThaevenLoopBeat> QUEUE = new ArrayDeque<>();
    private static ThaevenLoopBeat current;
    private static String[] rawLines = new String[0];
    private static int ticks;
    private static int quietTicks;

    private ThaevenLoopOverlay() {
    }

    public static void receive(ThaevenLoopPayload payload) {
        ThaevenLoopBeat beat = ThaevenLoopBeat.byId(payload.beat());
        if (beat != null) {
            QUEUE.add(beat);
        }
    }

    /** The band's alpha on a tick of the beat: 10-tick fades around an 80-tick hold. */
    static float bandAlpha(int tick) {
        return Mth.clamp(Math.min(tick / (float) FADE_TICKS, (TOTAL_TICKS - tick) / (float) FADE_TICKS), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (current != null) {
            if (++ticks >= TOTAL_TICKS) {
                current = null;
            }
            return;
        }
        if (QUEUE.isEmpty()) {
            return;
        }
        if (FirstBootEpigraph.active() || OrsaAwakeningIntro.busy()) {
            quietTicks = 0;
            return;
        }
        if (++quietTicks < SETTLE_TICKS) {
            return;
        }
        start(QUEUE.poll());
    }

    private static void start(ThaevenLoopBeat beat) {
        ThaevenRecordDefinition three = ThaevenRecordDefinition.load(ThaevenRecordId.PATTERN_RESIDUE);
        rawLines = new String[beat.lines().size()];
        for (int index = 0; index < rawLines.length; index++) {
            ThaevenLoopBeat.Line line = beat.lines().get(index);
            String[] page = three.rawPages().get(Math.min(line.page(), three.rawPages().size() - 1)).split("\n");
            rawLines[index] = page[Math.min(line.line(), page.length - 1)];
        }
        current = beat;
        ticks = 0;
        quietTicks = 0;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ThaevenLoopBeat beat = current;
        Minecraft minecraft = Minecraft.getInstance();
        if (beat == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        float alpha = bandAlpha(ticks);
        int width = graphics.guiWidth();
        int centerY = Math.round(graphics.guiHeight() * 0.68F);
        int bandHalfHeight = Math.max(22, graphics.guiHeight() / 28);
        graphics.fill(0, centerY - bandHalfHeight, width, centerY + bandHalfHeight,
                Math.round(132.0F * alpha) << 24 | 0x03080C);

        Font font = minecraft.font;
        String label = Component.translatable("overlay.frozendawn.thaeven.label").getString();
        boolean labelFractures = ticks % 23 == 5 || ticks % 23 == 6;
        graphics.drawCenteredString(font,
                Component.literal(labelFractures ? ThaevenInk.fracture(label, ticks) : label)
                        .withStyle(ChatFormatting.DARK_AQUA),
                width / 2, centerY - 13, withAlpha(0xFF60D8DF, alpha * 0.72F));

        for (int index = 0; index < beat.lines().size() && index < rawLines.length; index++) {
            int local = ticks - index * ThaevenLoopBeat.SECOND_LINE_DELAY;
            if (local < 0) {
                continue;
            }
            float lineAlpha = alpha * Mth.clamp(local / (float) FADE_TICKS, 0.0F, 1.0F);
            float progress = Mth.clamp(local / (float) INK_TICKS, 0.0F, 1.0F);
            String english = Component.translatable(beat.lines().get(index).key()).getString();
            String text = ThaevenInk.morph(rawLines[index], english, progress, beat.ordinal() * 4 + index);
            float unrest = Math.max(RESTING_UNREST, 1.0F - (float) Mth.smoothstep(progress));
            ThaevenInk.drawInked(graphics, font, text, width / 2, centerY + 4 + index * 10,
                    ticks, index, unrest, lineAlpha);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        QUEUE.clear();
        current = null;
        ticks = 0;
        quietTicks = 0;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp(Math.round(((color >>> 24) & 0xFF) * alpha), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
