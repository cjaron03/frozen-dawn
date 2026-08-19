package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.StillpointFieldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Client-only mirror and short ripple history for the Stillpoint renderer. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StillpointClientState {
    private static final int MAX_RIPPLES = 4;
    private static boolean present;
    private static boolean active;
    private static ResourceLocation dimension;
    private static BlockPos center = BlockPos.ZERO;
    private static int radius = 48;
    private static long chargeStartGameTime = -1L;
    private static int pulseSequence;
    private static final Deque<Ripple> ripples = new ArrayDeque<>();

    private StillpointClientState() {
    }

    public static void update(StillpointFieldPayload payload) {
        boolean wasPresent = present;
        boolean wasActive = active;
        long previousChargeStart = chargeStartGameTime;
        present = payload.present();
        active = payload.active();
        dimension = payload.dimension();
        center = payload.center();
        radius = payload.radius();
        chargeStartGameTime = payload.chargeStartGameTime();
        Minecraft minecraft = Minecraft.getInstance();
        if (present && !active && (!wasPresent
                || previousChargeStart != chargeStartGameTime)) {
            long now = minecraft.level == null ? chargeStartGameTime
                    : minecraft.level.getGameTime();
            int remaining = (int) Math.max(1L,
                    80L - Math.max(0L, now - chargeStartGameTime));
            HearthBoundaryEffects.triggerStillpointCharge(remaining);
        }
        if (active && !wasActive) {
            HearthBoundaryEffects.triggerStillpointFormation();
        }
        if (payload.pulseStrength() > 0.0F && payload.pulseSequence() != pulseSequence) {
            long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            ripples.addFirst(new Ripple(new Vec3(payload.pulseX(), payload.pulseY(),
                    payload.pulseZ()), gameTime, payload.pulseStrength()));
            while (ripples.size() > MAX_RIPPLES) ripples.removeLast();
        }
        pulseSequence = payload.pulseSequence();
        if (!present) ripples.clear();
    }

    public static boolean isPresentHere() {
        Minecraft minecraft = Minecraft.getInstance();
        return present && minecraft.level != null && dimension != null
                && dimension.equals(minecraft.level.dimension().location());
    }

    public static boolean isActiveHere() {
        return active && isPresentHere();
    }

    public static boolean isChargingHere() {
        return !active && isPresentHere();
    }

    public static boolean isListenerInside() {
        Minecraft minecraft = Minecraft.getInstance();
        return isActiveHere() && minecraft.player != null
                && minecraft.player.position().distanceToSqr(center.getCenter())
                <= (double) radius * radius;
    }

    public static boolean isOutsideSource(double x, double y, double z) {
        return new Vec3(x, y, z).distanceToSqr(center.getCenter())
                > (double) radius * radius;
    }

    public static BlockPos center() {
        return center;
    }

    public static int radius() {
        return radius;
    }

    public static long chargeStartGameTime() {
        return chargeStartGameTime;
    }

    public static float formationAgeTicks(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActiveHere() || minecraft.level == null || chargeStartGameTime < 0L) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(0.0F, minecraft.level.getGameTime() + partialTick
                - (chargeStartGameTime + 80L));
    }

    public static List<Ripple> ripples() {
        return List.copyOf(ripples);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        present = false;
        active = false;
        dimension = null;
        center = BlockPos.ZERO;
        chargeStartGameTime = -1L;
        pulseSequence = 0;
        ripples.clear();
    }

    public record Ripple(Vec3 position, long gameTime, float strength) {
    }
}
