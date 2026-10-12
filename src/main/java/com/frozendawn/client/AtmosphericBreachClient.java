package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.SuffocationStage;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.RoomRecoveryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Event warnings routed to the worn helmet or action bar, never chat history. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class AtmosphericBreachClient {
    private static int speechDelay = -1, warningTicks;
    private static SimpleSoundInstance speech;
    private static String noticeKey = "ui.frozendawn.suit.atmospheric_breach";
    private static ChatFormatting noticeColor = ChatFormatting.RED;
    private static boolean atmosphericLoss, deviceNotice;
    private static Object[] noticeArgs = new Object[0];
    private static Boolean lastSuitHud;
    private static SuffocationStage suffocationStage = SuffocationStage.NONE;
    private static Component lastActionBar;
    private static boolean writingActionBar;

    public static boolean usesSuitHud(Player player) {
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(ModItems.EVA_HELMET.get()) || helmet.is(ModItems.EMERGENCY_EVA_HELMET.get())
                || com.frozendawn.event.MobFreezeHandler.hasThermalVisorRig(player);
    }

    public static void receive() {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        if (speech != null) mc.getSoundManager().stop(speech);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ATMOSPHERIC_BREACH_WHOOSH.get(), 1, 0.85F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ATMOSPHERIC_BREACH_ALARM.get(), 1, 1));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.65F));
        deviceNotice = false; noticeArgs = new Object[0];
        noticeKey = "ui.frozendawn.suit.atmospheric_breach";
        noticeColor = ChatFormatting.RED;
        atmosphericLoss = true;
        warningTicks = 80; speechDelay = 24;
        lastSuitHud = null;
        routeNotice();
    }

    public static void receiveRecovery(RoomRecoveryPayload.Stage stage) {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        // A repaired room must not play stale alarm speech afterward.
        if (speech != null) mc.getSoundManager().stop(speech);
        speech = null; speechDelay = -1;
        deviceNotice = false; noticeArgs = new Object[0];
        noticeKey = switch (stage) {
            case WAITING_FOR_OXYGEN -> "ui.frozendawn.room.sealed_no_supply";
            case RESTORING_AIR -> "ui.frozendawn.room.sealed_restoring";
            case AIR_RESTORED -> "ui.frozendawn.room.air_restored";
        };
        noticeColor = stage == RoomRecoveryPayload.Stage.AIR_RESTORED ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        atmosphericLoss = false;
        warningTicks = stage == RoomRecoveryPayload.Stage.RESTORING_AIR ? 120 : 80;
        lastSuitHud = null;
        routeNotice();
        if (stage == RoomRecoveryPayload.Stage.AIR_RESTORED)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F, 0.35F));
    }

    public static void receiveAirlock(com.frozendawn.network.AirlockStatusPayload payload) {
        if (Minecraft.getInstance().player == null || !AtmosphericActionBarPolicy.allowsDeviceNotice(
                warningTicks > 0 && !deviceNotice,suffocationStage)) return;
        // No sound or speech is scheduled for routine device messages.
        deviceNotice = true; atmosphericLoss = false;
        noticeKey = payload.status().key();
        noticeArgs = new Object[]{payload.reserve(),6400,payload.air(),payload.capacity(),payload.percent(),payload.amount()};
        noticeColor = payload.status().warning() ? ChatFormatting.RED
                : payload.status() == com.frozendawn.network.AirlockStatusPayload.Status.READY ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        warningTicks = 60; lastSuitHud = null;
        routeNotice();
    }
    private static String activeNoticeKey() {
        return warningTicks > 0 && (!deviceNotice || suffocationStage == SuffocationStage.NONE) ? noticeKey : null;
    }
    private static void routeNotice() {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        boolean suitHud = usesSuitHud(mc.player);
        if (lastSuitHud == null || lastSuitHud != suitHud) {
            if (suitHud && activeNoticeKey() != null) {
                if (deviceNotice) MasterArchitectFloodClient.showAirlockSuitStatus(Component.translatable(noticeKey,noticeArgs),noticeColor.getColor());
                else MasterArchitectFloodClient.showAtmosphericSuitDialogue(noticeKey, atmosphericLoss);
            } else MasterArchitectFloodClient.clearAtmosphericSuitDialogue();
            lastSuitHud = suitHud;
        }
        var notice = AtmosphericActionBarPolicy.select(activeNoticeKey(), suitHud, suffocationStage);
        Component message = null;
        if (notice != null) {
            message = Component.translatable(notice.primaryKey(), notice.primaryKey().equals(noticeKey) ? noticeArgs : new Object[0]);
            if (notice.secondaryKey() != null) message = Component.translatable(
                    "ui.frozendawn.room.combined_warning", message, Component.translatable(notice.secondaryKey()));
            message = message.copy().withStyle(notice.danger() ? ChatFormatting.RED : noticeColor);
        }
        if (message != null) {
            writeActionBar(message);
            lastActionBar = message;
        } else if (lastActionBar != null) {
            // Clear only our own previous text, preserving a newer unrelated notice.
            if (lastActionBar.equals(((com.frozendawn.mixin.GuiAccessor) mc.gui).frozendawn$getOverlayMessage())) writeActionBar(Component.empty());
            lastActionBar = null;
        }
    }

    /** Used by Gui's action-bar setter to enforce oxygen priority across other writers. */
    public static boolean suppressCompetingActionBar() {
        var player = Minecraft.getInstance().player;
        return !writingActionBar && player != null && AtmosphericActionBarPolicy.select(
                activeNoticeKey(), usesSuitHud(player), suffocationStage) != null;
    }

    private static void writeActionBar(Component message) {
        writingActionBar = true;
        try { Minecraft.getInstance().player.displayClientMessage(message, true); }
        finally { writingActionBar = false; }
    }

    public static void receiveSuffocation(SuffocationStage stage) {
        suffocationStage = stage;
        if (deviceNotice && stage != SuffocationStage.NONE) {MasterArchitectFloodClient.clearAtmosphericSuitDialogue();lastSuitHud=null;}
        routeNotice();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.isPaused()) return;
        routeNotice();
        if (warningTicks > 0 && --warningTicks == 0) {
            lastSuitHud = null;
            MasterArchitectFloodClient.clearAtmosphericSuitDialogue();
        }
        if (speechDelay >= 0 && speechDelay-- == 0) {
            speech = SimpleSoundInstance.forUI(ModSounds.SUIT_ATMOSPHERIC_BREACH.get(), 1, 1);
            mc.getSoundManager().play(speech);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (speech != null) Minecraft.getInstance().getSoundManager().stop(speech);
        MasterArchitectFloodClient.clearAtmosphericSuitDialogue();
        speech = null; speechDelay = -1; warningTicks = 0; lastSuitHud = null;
        deviceNotice=false;noticeArgs=new Object[0];
        suffocationStage = SuffocationStage.NONE; lastActionBar = null; writingActionBar = false;
    }
}
