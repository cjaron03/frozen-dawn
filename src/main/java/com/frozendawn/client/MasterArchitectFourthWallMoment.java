package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import com.frozendawn.homo.HearthMasterArchitectPolicy;
import com.frozendawn.homo.MasterArchitectFourthWallPolicy;
import com.frozendawn.network.MasterArchitectFourthWallRequestPayload;
import com.frozendawn.network.MasterArchitectFourthWallStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Comparator;

/**
 * Client-local camera perception for the Master Architect. Camera position and
 * head rotation are deliberately never synchronized to the server or other clients.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectFourthWallMoment {
    private static final int UNKNOWN = 0;
    private static final int WAITING = 1;
    private static final int ELIGIBLE = 2;
    private static final int QUERY_RETRY_TICKS = 40;

    private static ClientLevel trackedLevel;
    private static int trackedMasterId = -1;
    private static int eligibility = UNKNOWN;
    private static int queryCooldown;
    private static int eyeContactTicks;
    private static boolean contactLatched;
    private static boolean completionRequested;
    private static boolean completedForWorld;
    private static int overlayTicks = -1;

    private MasterArchitectFourthWallMoment() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != trackedLevel) {
            resetForLevel(minecraft.level);
        }
        tickOverlay();
        if (minecraft.level == null || minecraft.player == null) {
            resetContactSequence();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        ArchitectEntity master = findNearestMaster(minecraft);
        if (master == null) {
            clearCandidate();
            return;
        }
        if (completedForWorld) {
            trackedMasterId = master.getId();
            resetContactSequence();
            return;
        }
        if (trackedMasterId != master.getId()) {
            trackedMasterId = master.getId();
            eligibility = UNKNOWN;
            queryCooldown = 0;
            completionRequested = false;
            resetContactSequence();
        }

        if (eligibility == WAITING) {
            if (queryCooldown > 0) {
                queryCooldown--;
            } else {
                eligibility = UNKNOWN;
            }
        }
        if (eligibility == UNKNOWN && isThirdPerson(minecraft)) {
            PacketDistributor.sendToServer(
                    new MasterArchitectFourthWallRequestPayload(master.getId(), false));
            eligibility = WAITING;
            queryCooldown = QUERY_RETRY_TICKS;
            resetContactSequence();
            return;
        }

        if (eligibility != ELIGIBLE || completionRequested) {
            if (!contactLatched) {
                resetEyeContact();
            }
            return;
        }

        if (!contactLatched) {
            if (!isThirdPerson(minecraft)
                    || !isPeacefulClientWatch(master)
                    || minecraft.screen != null
                    || ThaevenTransmissionOverlay.isActive()) {
                resetEyeContact();
                return;
            }
            if (!cameraHasEyeContact(minecraft, master)) {
                return;
            }
            contactLatched = true;
            resetEyeContact();
        }

        eyeContactTicks++;
        if (MasterArchitectFourthWallPolicy.contactComplete(eyeContactTicks)) {
            completionRequested = true;
            eligibility = WAITING;
            queryCooldown = QUERY_RETRY_TICKS;
            PacketDistributor.sendToServer(
                    new MasterArchitectFourthWallRequestPayload(master.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetForLevel(null);
    }

    public static void handleState(MasterArchitectFourthWallStatePayload payload) {
        if (payload.state() == MasterArchitectFourthWallStatePayload.COMPLETED) {
            completedForWorld = true;
            trackedMasterId = payload.entityId();
            eligibility = UNKNOWN;
            completionRequested = false;
            resetContactSequence();
            return;
        }
        if (payload.state() == MasterArchitectFourthWallStatePayload.TRIGGERED) {
            completedForWorld = true;
            trackedMasterId = payload.entityId();
            eligibility = UNKNOWN;
            overlayTicks = 0;
            resetContactSequence();
            return;
        }
        if (payload.state() == MasterArchitectFourthWallStatePayload.ELIGIBLE
                && payload.entityId() == trackedMasterId) {
            eligibility = ELIGIBLE;
            completionRequested = false;
            queryCooldown = 0;
        }
    }

    public static CameraHeadAngles cameraHeadAngles(
            ArchitectEntity master, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MasterArchitectFourthWallPolicy.shouldTrackCamera(
                completedForWorld,
                eligibility == ELIGIBLE,
                master.getId() == trackedMasterId,
                isThirdPerson(minecraft),
                isPeacefulClientWatch(master))) {
            return null;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 delta = camera.getPosition().subtract(master.getEyePosition(partialTick));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-4D) {
            return null;
        }

        float targetYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG)
                - 90.0F;
        float bodyYaw = Mth.rotLerp(
                partialTick, master.yBodyRotO, master.yBodyRot);
        float relativeYaw = Mth.clamp(
                Mth.wrapDegrees(targetYaw - bodyYaw), -85.0F, 85.0F);
        float pitch = Mth.clamp(
                (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG)),
                -65.0F,
                65.0F);
        return new CameraHeadAngles(
                relativeYaw * Mth.DEG_TO_RAD,
                pitch * Mth.DEG_TO_RAD);
    }

    public static float weatherAudioMultiplier() {
        return MasterArchitectFourthWallPolicy.weatherAudioMultiplier(overlayTicks);
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (overlayTicks < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        float fadeIn = Mth.clamp(overlayTicks / 12.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(
                (MasterArchitectFourthWallPolicy.OVERLAY_TICKS - overlayTicks) / 22.0F,
                0.0F,
                1.0F);
        float alpha = Math.min(fadeIn, fadeOut);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerY = Math.round(height * 0.68F);
        int bandHalfHeight = Math.max(22, height / 28);
        graphics.fill(
                0,
                centerY - bandHalfHeight,
                width,
                centerY + bandHalfHeight,
                argb(Math.round(132.0F * alpha), 3, 8, 12));

        Font font = minecraft.font;
        graphics.drawCenteredString(
                font,
                net.minecraft.network.chat.Component
                        .translatable("overlay.frozendawn.thaeven.label")
                        .withStyle(ChatFormatting.DARK_AQUA),
                width / 2,
                centerY - 13,
                withAlpha(0xFF60D8DF, alpha * 0.72F));
        graphics.drawCenteredString(
                font,
                net.minecraft.network.chat.Component
                        .translatable("overlay.frozendawn.thaeven.host_found")
                        .withStyle(ChatFormatting.ITALIC),
                width / 2,
                centerY + 4,
                withAlpha(0xFFE7F7F7, alpha));
    }

    private static ArchitectEntity findNearestMaster(Minecraft minecraft) {
        double radius = HearthMasterArchitectPolicy.WATCH_DISTANCE;
        AABB area = minecraft.player.getBoundingBox().inflate(radius);
        return minecraft.level.getEntitiesOfClass(
                        ArchitectEntity.class,
                        area,
                        candidate -> candidate.isHearthMasterArchitect()
                                && candidate.isAlive()
                                && candidate.getDeathTicks() <= 0)
                .stream()
                .filter(candidate -> candidate.distanceToSqr(minecraft.player)
                        <= radius * radius)
                .min(Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(minecraft.player)))
                .orElse(null);
    }

    private static boolean isPeacefulClientWatch(ArchitectEntity master) {
        return master.getMasterCombatAction() == MasterArchitectCombatAction.IDLE
                && MasterArchitectWeather.getStrength() <= 0.05F;
    }

    private static boolean cameraHasEyeContact(
            Minecraft minecraft, ArchitectEntity master) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 masterEye = master.getEyePosition(1.0F);
        Vec3 towardMaster = masterEye.subtract(cameraPos);
        if (towardMaster.lengthSqr() < 1.0E-4D) {
            return false;
        }

        Vector3f forward = camera.getLookVector();
        double dot = new Vec3(forward.x(), forward.y(), forward.z())
                .dot(towardMaster.normalize());
        HitResult hit = minecraft.level.clip(new ClipContext(
                cameraPos,
                masterEye,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                minecraft.player));
        boolean unobstructed = hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(masterEye) < 0.25D;
        return MasterArchitectFourthWallPolicy.hasCameraEyeContact(dot, unobstructed);
    }

    private static boolean isThirdPerson(Minecraft minecraft) {
        return !minecraft.options.getCameraType().isFirstPerson();
    }

    private static void tickOverlay() {
        if (overlayTicks < 0) {
            return;
        }
        overlayTicks++;
        if (overlayTicks >= MasterArchitectFourthWallPolicy.OVERLAY_TICKS) {
            overlayTicks = -1;
        }
    }

    private static void resetEyeContact() {
        eyeContactTicks = 0;
    }

    private static void resetContactSequence() {
        contactLatched = false;
        resetEyeContact();
    }

    private static void clearCandidate() {
        trackedMasterId = -1;
        eligibility = UNKNOWN;
        queryCooldown = 0;
        completionRequested = false;
        resetContactSequence();
    }

    private static void resetForLevel(ClientLevel level) {
        trackedLevel = level;
        completedForWorld = false;
        overlayTicks = -1;
        clearCandidate();
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp(Math.round(((color >>> 24) & 0xFF) * alpha), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Mth.clamp(alpha, 0, 255) << 24)
                | (Mth.clamp(red, 0, 255) << 16)
                | (Mth.clamp(green, 0, 255) << 8)
                | Mth.clamp(blue, 0, 255);
    }

    public record CameraHeadAngles(float yawRadians, float pitchRadians) {
    }
}
