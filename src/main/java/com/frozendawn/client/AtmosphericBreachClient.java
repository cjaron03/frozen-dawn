package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
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
    private static boolean atmosphericLoss;
    private static Boolean lastSuitHud;

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
        noticeKey = switch (stage) {
            case WAITING_FOR_OXYGEN -> "ui.frozendawn.room.sealed_no_supply";
            case RESTORING_AIR -> "ui.frozendawn.room.sealed_restoring";
            case AIR_RESTORED -> "ui.frozendawn.room.air_restored";
        };
        noticeColor = stage == RoomRecoveryPayload.Stage.AIR_RESTORED ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        atmosphericLoss = false;
        warningTicks = 80;
        lastSuitHud = null;
        routeNotice();
        if (stage == RoomRecoveryPayload.Stage.AIR_RESTORED)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F, 0.35F));
    }

    private static void routeNotice() {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        boolean suitHud = usesSuitHud(mc.player);
        if (lastSuitHud == null || lastSuitHud != suitHud) {
            if (suitHud) {
                mc.player.displayClientMessage(Component.empty(), true);
                MasterArchitectFloodClient.showAtmosphericSuitDialogue(noticeKey, atmosphericLoss);
            } else MasterArchitectFloodClient.clearAtmosphericSuitDialogue();
            lastSuitHud = suitHud;
        }
        if (!suitHud) mc.player.displayClientMessage(Component.translatable(noticeKey).withStyle(noticeColor), true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.isPaused()) return;
        if (warningTicks > 0) {
            routeNotice();
            warningTicks--;
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
    }
}
