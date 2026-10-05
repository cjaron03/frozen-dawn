package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModSounds;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Local emergency reserve alarms; the shared EVA HUD renders its telemetry. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EmergencyEvaClient {
    private static UUID announcedIssue;
    private static UUID pendingVoiceIssue;
    private static int activationVoiceDelay;
    private static UUID pendingShelterNotice;
    private static int shelterNoticeDelay;
    private static int beepCooldown;
    private static int creakCooldown;
    private static int previousReserve = -1;
    private static final EmergencyEvaDiagnostics diagnostics = new EmergencyEvaDiagnostics();
    private EmergencyEvaClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        announcedIssue = null;
        pendingVoiceIssue = null;
        activationVoiceDelay = 0;
        pendingShelterNotice = null;
        beepCooldown = 0;
        creakCooldown = 0;
        previousReserve = -1;
        diagnostics.reset(0);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ContinuityRecoveryHud.tick();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()
                || !mc.player.isAlive() || !EmergencyEvaHandler.isWearingIssuedPiece(mc.player)) {
            pendingVoiceIssue = null;
            pendingShelterNotice = null;
            return;
        }
        var state = mc.player.getData(com.frozendawn.init.ModAttachments.EMERGENCY_EVA);
        if (!state.issue().equals(announcedIssue)) {
            announcedIssue = state.issue();
            beepCooldown = 0;
            creakCooldown = 0;
            previousReserve = state.remainingTicks();
            diagnostics.reset(state.remainingTicks());
            // Only a fresh reserve can truthfully announce ten minutes. Returning
            // to a partly spent issue after login must not replay that promise.
            pendingVoiceIssue = state.remainingTicks() >= com.frozendawn.data.EmergencyEvaState.SERVICE_TICKS - 40
                    ? state.issue() : null;
            activationVoiceDelay = 20;
            if (EmergencyEvaHandler.hasLifeSupport(mc.player)) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        ModSounds.EVA_EMERGENCY_REGULATOR.get(), 1.0F, 0.25F));
            }
            beep(0.65F, 0.85F);
        }
        if (pendingVoiceIssue != null && --activationVoiceDelay <= 0) {
            if (pendingVoiceIssue.equals(state.issue()) && EmergencyEvaHandler.hasLifeSupport(mc.player)) {
                MasterArchitectFloodClient.showSuitDialogue("ui.frozendawn.suit.emergency_eva_active");
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        ModSounds.SUIT_EMERGENCY_EVA_ACTIVE.get(), 1.0F, 1.0F));
                pendingShelterNotice = state.issue();
                shelterNoticeDelay = 210;
            }
            pendingVoiceIssue = null;
        }
        if (pendingShelterNotice != null && --shelterNoticeDelay <= 0) {
            if (!pendingShelterNotice.equals(state.issue()) || state.remainingTicks() == 0) {
                pendingShelterNotice = null;
            } else if (MasterArchitectFloodClient.showSuitDialogueIfIdle(
                    mc.player.getData(com.frozendawn.init.ModAttachments.CONTINUITY_RECOVERY).shelterEstimate() == null
                            ? "ui.frozendawn.suit.continuity_no_shelter" : "ui.frozendawn.suit.continuity_shelter_degraded")) {
                pendingShelterNotice = null;
            }
        }
        boolean sealed = EmergencyEvaHandler.hasLifeSupport(mc.player);
        diagnostics.tick(sealed);
        var diagnostic = diagnostics.pending(state.remainingTicks(), sealed,
                EmergencyEvaVisor.hasVisibleCondensation());
        if (diagnostic != null && pendingVoiceIssue == null && pendingShelterNotice == null
                && !mc.options.hideGui && !OrsaAwakeningIntro.shouldSuppressSurvivalHud()
                && MasterArchitectFloodClient.showWarningSuitDialogueIfIdle(diagnostic.key())) {
            diagnostics.acknowledge(diagnostic);
        }
        if (beepCooldown > 0) beepCooldown--;
        if (creakCooldown > 0) creakCooldown--;
        if (previousReserve > 0 && state.remainingTicks() == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.EVA_EMERGENCY_SHUTDOWN.get(), 1.0F, 0.35F));
            MasterArchitectFloodClient.showWarningSuitDialogue("ui.frozendawn.suit.emergency_eva_shutdown");
            pendingVoiceIssue = null;
            pendingShelterNotice = null;
        }
        previousReserve = state.remainingTicks();
        int seconds = (state.remainingTicks() + 19) / 20;
        if (seconds > 0 && seconds <= 120 && beepCooldown == 0) {
            beep(seconds <= 15 ? 0.85F : 0.65F, seconds <= 60 ? 1.16F : 1.0F);
            beepCooldown = seconds <= 15 ? 20 : seconds <= 60 ? 60 : 200;
        }
        if (seconds > 0 && seconds <= 300 && creakCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, 0.07F, 0.55F));
            creakCooldown = seconds <= 120 ? 600 : 1200;
        }
    }

    private static void beep(float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.SUIT_OXYGEN_BEEP.get(), volume, pitch));
    }
}
