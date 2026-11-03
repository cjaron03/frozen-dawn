package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Terminus;
import com.frozendawn.defense.TerminusCone;
import com.frozendawn.defense.TerminusLedger;
import com.frozendawn.init.ModParticles;
import com.frozendawn.item.TerminusItem;
import com.frozendawn.mixin.GameRendererAccessor;
import com.frozendawn.network.TerminusDischargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The Terminus as the client sees it. Winding up, pale specks are drawn in toward the emitter from inside the cone,
 * thickest along its edge and more as the wind-up nears its end, and the operator walks at 40%. On discharge there is
 * no beam: the operator's view goes grey for one frame, and a pale mote lifts from each stopped mind and winks out.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class TerminusClient {
    private static final ResourceLocation GREY = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shaders/post/terminus_grey.json");
    /** Frames the view stays grey after a discharge. */
    private static final int GREY_FRAMES = 1;
    /** Ticks a speck takes to reach the emitter. */
    public static final int SPECK_TICKS = 14;
    private static int greyFrames;
    private static boolean wasWinding, mustRelease;
    private static boolean greyShown;

    public static void receive(TerminusDischargePayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.player != null && mc.player.getId() == payload.operator()) greyFrames = GREY_FRAMES;
        for (var head : payload.minds())
            mc.level.addParticle(ModParticles.TERMINUS_MOTE.get(), head.x, head.y + .2, head.z, 0, 0, 0);
    }

    @SubscribeEvent public static void specks(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        var r = mc.level.random;
        for (var player : mc.level.players()) {
            if (!player.isUsingItem() || !TerminusItem.winding(player, player.getUseItem())) continue;
            float k = Mth.clamp(player.getTicksUsingItem() / (float) Terminus.WINDUP_TICKS, 0, 1);
            var emitter = emitter(mc, player);
            var look = player.getLookAngle();
            var across = look.cross(new Vec3(0, 1, 0)).normalize();
            if (across.lengthSqr() < 1e-6) across = new Vec3(1, 0, 0);
            var up = across.cross(look).normalize();
            for (int i = 0, n = 1 + (int) (k * 5); i < n; i++) {
                // Two in three ride the cone's edge, so its shape shows; the rest fill it.
                double off = Math.toRadians(TerminusCone.HALF_ANGLE_DEGREES) * (r.nextInt(3) > 0 ? .85 + .15 * r.nextDouble() : r.nextDouble());
                double around = r.nextDouble() * Mth.TWO_PI;
                double reach = 2 + r.nextDouble() * (TerminusCone.REACH - 2);
                var dir = look.scale(Math.cos(off)).add(across.scale(Math.sin(off) * Math.cos(around))).add(up.scale(Math.sin(off) * Math.sin(around)));
                var from = player.getEyePosition().add(dir.scale(reach));
                var v = emitter.subtract(from).scale(1.0 / SPECK_TICKS);
                mc.level.addParticle(ModParticles.TERMINUS_SPECK.get(), from.x, from.y, from.z, v.x, v.y, v.z);
            }
        }
    }

    /** The emitter at the end of the outstretched arm: low right of the view in first person, at the hand otherwise. */
    private static Vec3 emitter(Minecraft mc, Player player) {
        var arm = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            var camera = mc.gameRenderer.getMainCamera();
            return player.getEyePosition().add(new Vec3(camera.getLookVector()).scale(1))
                    .add(new Vec3(camera.getLeftVector()).scale(-.3 * side)).add(new Vec3(camera.getUpVector()).scale(-.12));
        }
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        var right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return player.getEyePosition().add(0, -.22 * player.getScale(), 0).add(right.scale(.33 * side))
                .add(player.getLookAngle().scale(1.2 * player.getScale()));
    }

    /**
     * Once a wind-up ends, however it ends, the use key has to come up before another can start: held down, vanilla
     * would retry use every four ticks and wind straight into the next discharge once the cooldown ran out.
     */
    @SubscribeEvent public static void releaseGate(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        boolean winding = player != null && player.isUsingItem() && TerminusItem.winding(player, player.getUseItem());
        if (wasWinding && !winding) mustRelease = true;
        if (!mc.options.keyUse.isDown()) mustRelease = false;
        wasWinding = winding;
    }

    @SubscribeEvent public static void gateUse(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (mustRelease && event.isUseItem() && player != null && player.getItemInHand(event.getHand()).getItem() instanceof TerminusItem) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Using an item already slows the walk to a fifth; winding the Terminus asks for 40%. */
    @SubscribeEvent public static void slow(MovementInputUpdateEvent event) {
        var player = event.getEntity();
        if (player.isUsingItem() && !player.isPassenger() && TerminusItem.winding(player, player.getUseItem())) {
            event.getInput().forwardImpulse *= 2;
            event.getInput().leftImpulse *= 2;
        }
    }

    @SubscribeEvent public static void grey(RenderFrameEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var accessor = (GameRendererAccessor) mc.gameRenderer;
        var current = accessor.frozendawn$getPostEffect();
        if (greyShown) {
            if (current != null && GREY.toString().equals(current.getName())) accessor.frozendawn$shutdownEffect();
            greyShown = false;
        }
        if (greyFrames <= 0) return;
        greyFrames--;
        // Another effect (a spectator's view, the Surveyor Lens) keeps the screen; the grey frame yields to it.
        if (accessor.frozendawn$getPostEffect() != null) return;
        mc.gameRenderer.loadEffect(GREY);
        greyShown = true;
    }

    @SubscribeEvent public static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        TerminusLedger.forgetClient();
        greyFrames = 0;
        wasWinding = mustRelease = false;
    }

    private TerminusClient() { }
}
