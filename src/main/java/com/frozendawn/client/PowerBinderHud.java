package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.PowerBinderPayload;
import com.frozendawn.network.PowerBinderNoticePayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One instruction below the crosshair, plus targeting detail. Only visible with the Binder held. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PowerBinderHud {
    public static final int CYAN = 0x64C9CC, GREEN = 0x90D6A8, RED = 0xF39386, AMBER = 0xE5A064;
    private static PowerBinderPayload state;
    private static PowerBinderNoticePayload notice;
    private static long receivedAt, noticeAt;
    private PowerBinderHud() {}
    public static void receive(PowerBinderPayload payload) { state = payload; receivedAt = System.nanoTime(); }
    public static void receive(PowerBinderNoticePayload payload) { notice = payload; noticeAt = System.nanoTime(); }
    public static boolean holding() {
        var player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER));
    }
    public static PowerBinderPayload state() {
        var level = Minecraft.getInstance().level;
        return holding() && state != null && level != null && state.dimension().equals(level.dimension().location().toString())
                && System.nanoTime() - receivedAt < 2_000_000_000L ? state : null;
    }
    public static int color(PowerBinderPayload payload) {
        return switch (payload.status()) {
            case READY -> GREEN;
            case RANGE, FEEDTHROUGH, LIMIT, DOCK_CONNECTION -> RED;
            case WAITING, UNAVAILABLE -> AMBER;
            default -> CYAN;
        };
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!holding()) { state = null; notice = null; }
        else if (state != null && Minecraft.getInstance().level != null
                && !state.dimension().equals(Minecraft.getInstance().level.dimension().location().toString())) { state = null; notice = null; }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { state = null; notice = null; }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance(); if (!holding() || mc.options.hideGui || mc.screen != null) return;
        var payload = state();
        Component instruction = Component.translatable("hud.frozendawn.binder." + (payload == null ? "choose" : payload.status().name().toLowerCase(Locale.ROOT)));
        int color = payload == null ? CYAN : color(payload);
        if (mc.player.isShiftKeyDown() && payload != null && payload.target() != null) {
            instruction = Component.translatable("hud.frozendawn.binder.clear"); color = AMBER;
        }
        if (notice != null && System.nanoTime() - noticeAt < 1_600_000_000L) {
            instruction = Component.translatable("hud.frozendawn.binder.notice." + notice.key(), notice.count());
            color = switch (notice.key()) { case "ok" -> GREEN;
                case "range", "feedthrough", "limit", "not_node", "dock_connection" -> RED; default -> CYAN; };
        }
        Component detail = Component.translatable("hud.frozendawn.binder.start_hint");
        if (payload != null) {
            if (payload.source() != null && payload.target() != null && !payload.source().equals(payload.target()))
                detail = Component.translatable("hud.frozendawn.binder.distance", String.format(Locale.ROOT, "%.1f", payload.distance()), payload.reach());
            else if (payload.source() != null) detail = Component.translatable("hud.frozendawn.binder.cancel_hint");
            else if (payload.target() != null) detail = mc.level.getBlockState(payload.target()).getBlock().getName();
        }
        Component reading = payload != null && payload.target() != null && payload.source() == null
                ? Component.translatable("hud.frozendawn.binder.reading", String.format(Locale.ROOT, "%.2f", payload.supplyKw()), String.format(Locale.ROOT, "%.2f", payload.demandKw()), String.format(Locale.ROOT, "%.0f", payload.storedKj())) : null;
        var gui = event.getGuiGraphics(); int width = Math.max(mc.font.width(instruction), mc.font.width(detail));
        if (reading != null) width = Math.max(width, mc.font.width(reading));
        int x = (gui.guiWidth() - width) / 2, y = gui.guiHeight() / 2 + 28;
        gui.fill(x - 6, y - 5, x + width + 6, y + (reading == null ? 23 : 36), 0xB0121A20);
        gui.drawString(mc.font, instruction, (gui.guiWidth() - mc.font.width(instruction)) / 2, y, color, true);
        gui.drawString(mc.font, detail, (gui.guiWidth() - mc.font.width(detail)) / 2, y + 13, 0xBBC8CE, true);
        if (reading != null) gui.drawString(mc.font, reading, (gui.guiWidth() - mc.font.width(reading)) / 2, y + 26, 0xBBC8CE, true);
    }
}
