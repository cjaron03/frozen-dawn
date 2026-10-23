package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.architect.ArchitectFelledDeath;
import com.frozendawn.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Comparator;

/**
 * Screen warp for a felled Architect's death: space pinches toward the falling body,
 * then snaps back out in one ripple ring on the impact thud. Yields to any other post
 * effect already running, such as the Orsa thermal view.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ArchitectGravityWell {

    private static final ResourceLocation POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shaders/post/architect_gravity_well.json");
    private static final double WATCH_DISTANCE = 48.0;
    private static final float WELL_RADIUS_BLOCKS = 3.5F;
    private static final float MAX_SCREEN_RADIUS = 0.6F;
    private static final float PINCH_MAX = 0.55F;
    private static final float RIPPLE_TICKS = 12.0F;

    private static int trackedId = -1;
    private static double impactTime;
    private static Vec3 center = Vec3.ZERO;
    private static boolean managed;

    private ArchitectGravityWell() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            trackedId = -1;
            return;
        }
        if (trackedId != -1 && mc.level.getGameTime() > impactTime + RIPPLE_TICKS) {
            trackedId = -1;
        }
        if (trackedId != -1) {
            return;
        }
        mc.level.getEntitiesOfClass(ArchitectEntity.class,
                        mc.player.getBoundingBox().inflate(WATCH_DISTANCE),
                        ArchitectEntity::isFelledDying)
                .stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(mc.player)))
                .ifPresent(architect -> {
                    trackedId = architect.getId();
                    track(architect, mc.level.getGameTime(), 0.0F);
                });
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || trackedId == -1) {
            release(mc);
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partialTick;
        Entity entity = mc.level.getEntity(trackedId);
        if (entity instanceof ArchitectEntity architect && architect.isFelledDying()) {
            track(architect, mc.level.getGameTime(), partialTick);
        }

        double sinceImpact = now - impactTime;
        float pinch = pinch(sinceImpact);
        float rippleProgress = (float) Mth.clamp(sinceImpact / RIPPLE_TICKS, 0.0, 1.0);
        float rippleAmount = sinceImpact < 0.0 ? 0.0F : 1.0F - rippleProgress * rippleProgress;
        if (pinch <= 0.0F && rippleAmount <= 0.0F) {
            release(mc);
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Vector4f clip = new Vector4f((float) (center.x - camera.x), (float) (center.y - camera.y),
                (float) (center.z - camera.z), 1.0F);
        event.getModelViewMatrix().transform(clip);
        Matrix4f projection = event.getProjectionMatrix();
        projection.transform(clip);
        if (clip.w <= 0.05F) {
            release(mc);
            return;
        }

        PostChain effect = acquire(mc);
        if (effect == null) {
            return;
        }
        effect.setUniform("CenterX", clip.x / clip.w * 0.5F + 0.5F);
        effect.setUniform("CenterY", clip.y / clip.w * 0.5F + 0.5F);
        effect.setUniform("Radius", Math.min(WELL_RADIUS_BLOCKS * projection.m11() / clip.w * 0.5F, MAX_SCREEN_RADIUS));
        effect.setUniform("Pinch", pinch);
        effect.setUniform("RippleProgress", rippleProgress);
        effect.setUniform("RippleAmount", rippleAmount);
    }

    /** Follows the falling body's midpoint and pins the impact to its synced death clock. */
    private static void track(ArchitectEntity architect, long gameTime, float partialTick) {
        float elapsed = architect.getDeathTicks() + partialTick;
        impactTime = gameTime + partialTick - elapsed + ArchitectFelledDeath.IMPACT_TICK;
        float fall = ArchitectFelledDeath.fallDegrees(elapsed) * Mth.DEG_TO_RAD;
        float yaw = architect.yBodyRot * Mth.DEG_TO_RAD;
        double half = architect.getBbHeight() * 0.5;
        center = architect.position().add(
                -Mth.sin(yaw) * half * Mth.sin(fall),
                half * Mth.cos(fall) + 0.15,
                Mth.cos(yaw) * half * Mth.sin(fall));
    }

    /** Space drawn in harder as the fall accelerates, released the instant it lands. */
    private static float pinch(double sinceImpact) {
        if (sinceImpact >= 0.0) {
            return 0.0F;
        }
        double span = ArchitectFelledDeath.IMPACT_TICK - ArchitectFelledDeath.TOPPLE_START;
        float t = (float) Mth.clamp(1.0 + sinceImpact / span, 0.0, 1.0);
        return PINCH_MAX * t * t;
    }

    private static PostChain acquire(Minecraft mc) {
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain current = accessor.frozendawn$getPostEffect();
        if (current != null && POST_EFFECT.toString().equals(current.getName())) {
            return current;
        }
        if (current != null) {
            return null;
        }
        mc.gameRenderer.loadEffect(POST_EFFECT);
        current = accessor.frozendawn$getPostEffect();
        managed = current != null && POST_EFFECT.toString().equals(current.getName());
        return managed ? current : null;
    }

    private static void release(Minecraft mc) {
        if (!managed) {
            return;
        }
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain current = accessor.frozendawn$getPostEffect();
        if (current != null && POST_EFFECT.toString().equals(current.getName())) {
            accessor.frozendawn$shutdownEffect();
        }
        managed = false;
    }
}
