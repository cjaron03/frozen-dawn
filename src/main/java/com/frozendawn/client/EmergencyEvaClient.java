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
    private static UUID pendingConditionNotice;
    private static int conditionNoticeDelay;
    private static SimpleSoundInstance conditionVoice;
    private static final int CONDITION_NOTICE_TICKS = 20 * 20;
    private static UUID pendingShelterNotice;
    private static int shelterNoticeDelay;
    private static int beepCooldown;
    private static int creakCooldown;
    private static int previousReserve = -1;
    private static UUID pendingTransitionIssue;
    private static int pendingTransition;
    private static SimpleSoundInstance transitionVoice;
    private static UUID transitionVoiceIssue;
    private static boolean transitionVoiceAmbient;
    private static int ambientNoticeCooldown;
    private static final EmergencyEvaDiagnostics diagnostics = new EmergencyEvaDiagnostics();
    private EmergencyEvaClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        announcedIssue = null;
        pendingVoiceIssue = null;
        activationVoiceDelay = 0;
        pendingConditionNotice = null;
        stopConditionVoice();
        pendingShelterNotice = null;
        beepCooldown = 0;
        creakCooldown = 0;
        previousReserve = -1;
        pendingTransitionIssue = null;
        pendingTransition = 0;
        ambientNoticeCooldown = 0;
        stopTransitionVoice();
        diagnostics.reset(0);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ContinuityRecoveryHud.tick();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()
                || !mc.player.isAlive()) {
            pendingTransitionIssue = null;
            pendingTransition = 0;
            stopTransitionVoice();
            clearAnnouncements();
            return;
        }
        var state = mc.player.getData(com.frozendawn.init.ModAttachments.EMERGENCY_EVA);
        if (ambientNoticeCooldown > 0) ambientNoticeCooldown--;
        if (transitionVoiceIssue != null && (!transitionVoiceIssue.equals(state.issue())
                || transitionVoiceAmbient && !state.ambientIntake())) stopTransitionVoice();
        if (!EmergencyEvaHandler.isWearingIssuedPiece(mc.player)) {
            pendingVoiceIssue = null;
            pendingConditionNotice = null;
            stopConditionVoice();
            pendingShelterNotice = null;
            processTransitionNotice();
            return;
        }
        if (!state.issue().equals(announcedIssue)) {
            stopConditionVoice();
            pendingConditionNotice = null;
            announcedIssue = state.issue();
            beepCooldown = 0;
            creakCooldown = 0;
            previousReserve = state.oxygenTicks();
            ambientNoticeCooldown = 0;
            diagnostics.reset(state.oxygenTicks(), state.wornTicks(), state.remainingTicks());
            // Only a fresh reserve can truthfully announce ten minutes. Returning
            // to a partly spent issue after login must not replay that promise.
            pendingVoiceIssue = state.remainingTicks() >= com.frozendawn.data.EmergencyEvaState.SERVICE_TICKS - 40
                    ? state.issue() : null;
            activationVoiceDelay = 20;
            if (EmergencyEvaHandler.hasThermalSupport(mc.player)) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        ModSounds.EVA_EMERGENCY_REGULATOR.get(), 1.0F, 0.25F));
            }
            beep(0.65F, 0.85F);
        }
        processTransitionNotice();
        if (pendingVoiceIssue != null && --activationVoiceDelay <= 0) {
            if (pendingVoiceIssue.equals(state.issue()) && EmergencyEvaHandler.hasThermalSupport(mc.player)) {
                MasterArchitectFloodClient.showSuitDialogue("ui.frozendawn.suit.emergency_eva_active");
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        ModSounds.SUIT_EMERGENCY_EVA_ACTIVE.get(), 1.0F, 1.0F));
                pendingConditionNotice = state.issue();
                conditionNoticeDelay = 210;
            }
            pendingVoiceIssue = null;
        }
        if (pendingConditionNotice != null && --conditionNoticeDelay <= 0) {
            if (!pendingConditionNotice.equals(state.issue()) || state.remainingTicks() == 0) {
                pendingConditionNotice = null;
            } else if (EmergencyEvaHandler.hasThermalSupport(mc.player) && !mc.options.hideGui
                    && !OrsaAwakeningIntro.shouldSuppressSurvivalHud()
                    && MasterArchitectFloodClient.showWarningSuitDialogueIfIdle(
                            "ui.frozendawn.suit.emergency_eva_condition", CONDITION_NOTICE_TICKS)) {
                conditionVoice = SimpleSoundInstance.forUI(ModSounds.SUIT_EMERGENCY_EVA_CONDITION.get(), 1.0F, 1.0F);
                mc.getSoundManager().play(conditionVoice);
                pendingConditionNotice = null;
                pendingShelterNotice = state.issue();
                shelterNoticeDelay = CONDITION_NOTICE_TICKS + 10;
            }
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
        boolean sealed = EmergencyEvaHandler.hasThermalSupport(mc.player);
        diagnostics.tick(sealed, state.thermalLoad());
        var diagnostic = diagnostics.pending(state.oxygenTicks(), sealed,
                EmergencyEvaVisor.hasVisibleCondensation(), state.exertionIntensity() >= 0.25F,
                state.coolingDegraded(), state.highThermalLoad(), state.remainingTicks(), state.ambientIntake());
        if (diagnostic != null && pendingVoiceIssue == null && pendingConditionNotice == null && pendingShelterNotice == null
                && !mc.options.hideGui && !OrsaAwakeningIntro.shouldSuppressSurvivalHud()
                && MasterArchitectFloodClient.showWarningSuitDialogueIfIdle(diagnostic.key())) {
            diagnostics.acknowledge(diagnostic);
            if (diagnostic == EmergencyEvaDiagnostics.Message.THERMAL
                    || diagnostic == EmergencyEvaDiagnostics.Message.COOLING) beep(0.65F, 0.85F);
        }
        if (beepCooldown > 0) beepCooldown--;
        if (creakCooldown > 0) creakCooldown--;
        if (previousReserve > 0 && state.oxygenTicks() == 0 && state.remainingTicks() > 0) {
            clearAnnouncements();
            stopTransitionVoice();
            MasterArchitectFloodClient.showWarningSuitDialogue("ui.frozendawn.suit.emergency_eva_oxygen_depleted");
            beep(0.85F, 1.16F);
        }
        previousReserve = state.oxygenTicks();
        int alarmTicks = state.ambientIntake() || state.oxygenTicks() == 0 ? state.remainingTicks()
                : Math.min(state.remainingTicks(), state.oxygenTicks());
        int seconds = (alarmTicks + 19) / 20;
        if (seconds > 0 && seconds <= 120 && beepCooldown == 0) {
            beep(seconds <= 15 ? 0.85F : 0.65F, seconds <= 60 ? 1.16F : 1.0F);
            beepCooldown = seconds <= 15 ? 20 : seconds <= 60 ? 60 : 200;
        }
        if (state.remainingTicks() > 0 && state.remainingTicks() <= 6000 && creakCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, 0.07F, 0.55F));
            creakCooldown = seconds <= 120 ? 600 : 1200;
        }
    }

    private static void beep(float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.SUIT_OXYGEN_BEEP.get(), volume, pitch));
    }

    private static void stopConditionVoice() {
        if (conditionVoice != null) Minecraft.getInstance().getSoundManager().stop(conditionVoice);
        conditionVoice = null;
    }

    public static void onNotice(UUID issue, int notice) {
        var mc = Minecraft.getInstance();
        if (notice == com.frozendawn.network.EmergencyEvaPayload.EXPIRED_NOTICE) {
            clearAnnouncements();
            pendingTransitionIssue = null;
            pendingTransition = 0;
            stopTransitionVoice();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EVA_EMERGENCY_SHUTDOWN.get(), 1.0F, 0.35F));
            MasterArchitectFloodClient.showWarningSuitDialogue("ui.frozendawn.suit.emergency_eva_shutdown");
        } else if (notice == com.frozendawn.network.EmergencyEvaPayload.HANDOFF_NOTICE) {
            clearAnnouncements();
            stopTransitionVoice();
            pendingTransitionIssue = issue;
            pendingTransition = notice;
        } else if (notice == com.frozendawn.network.EmergencyEvaPayload.AMBIENT_NOTICE && ambientNoticeCooldown == 0
                && pendingTransition != com.frozendawn.network.EmergencyEvaPayload.HANDOFF_NOTICE) {
            pendingTransitionIssue = issue;
            pendingTransition = notice;
        }
    }

    private static void processTransitionNotice() {
        var mc = Minecraft.getInstance();
        var state = mc.player.getData(com.frozendawn.init.ModAttachments.EMERGENCY_EVA);
        if (pendingTransitionIssue == null) return;
        if (!pendingTransitionIssue.equals(state.issue())) { pendingTransitionIssue = null; pendingTransition = 0; return; }
        boolean handoff = pendingTransition == com.frozendawn.network.EmergencyEvaPayload.HANDOFF_NOTICE;
        if (handoff) {
            // Inventory/equipment packets may arrive after the lease mirror.
            if (state.retirement() != com.frozendawn.data.EmergencyEvaState.HANDOFF
                    || !EmergencyEvaHandler.hasOrdinaryRigWithAir(mc.player) || SuitIntegrityClient.punctures() > 0) return;
        } else {
            if (!state.ambientIntake() || !EmergencyEvaHandler.hasThermalSupport(mc.player)) {
                pendingTransitionIssue = null; pendingTransition = 0; return;
            }
            if (pendingVoiceIssue != null || pendingConditionNotice != null || pendingShelterNotice != null) return;
        }
        if (mc.options.hideGui || OrsaAwakeningIntro.shouldSuppressSurvivalHud()) return;
        String key = handoff ? "ui.frozendawn.suit.emergency_eva_handoff" : "ui.frozendawn.suit.emergency_eva_ambient";
        if (!MasterArchitectFloodClient.showSuitDialogueIfIdle(key, handoff ? 200 : 320)) return;
        stopTransitionVoice();
        transitionVoiceIssue = state.issue();
        transitionVoiceAmbient = !handoff;
        transitionVoice = SimpleSoundInstance.forUI(handoff ? ModSounds.SUIT_EMERGENCY_EVA_HANDOFF.get()
                : ModSounds.SUIT_EMERGENCY_EVA_AMBIENT.get(), 1.0F, 1.0F);
        mc.getSoundManager().play(transitionVoice);
        if (!handoff) ambientNoticeCooldown = 45 * 20;
        pendingTransitionIssue = null;
        pendingTransition = 0;
    }

    private static void clearAnnouncements() {
        pendingVoiceIssue = null;
        pendingConditionNotice = null;
        pendingShelterNotice = null;
        stopConditionVoice();
    }

    private static void stopTransitionVoice() {
        if (transitionVoice != null) Minecraft.getInstance().getSoundManager().stop(transitionVoice);
        transitionVoice = null;
        transitionVoiceIssue = null;
        transitionVoiceAmbient = false;
    }
}
