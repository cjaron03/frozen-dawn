package com.frozendawn.client;

import com.frozendawn.client.renderer.ArchitectRenderer;
import com.frozendawn.client.renderer.MasterArchitectAdornmentLayer;
import com.frozendawn.client.renderer.MasterArchitectAdornmentModel;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.homo.MasterArchitectAuraTier;
import com.frozendawn.homo.MasterArchitectSkyFacePolicy;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Renders the Master Architect's exact entity face as the Major Hearth sky beacon. */
public final class MasterArchitectSkyFaceRenderer {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float FACE_U_MIN = 8.0F / 64.0F;
    private static final float FACE_U_MAX = 16.0F / 64.0F;
    private static final float FACE_V_MIN = 8.0F / 64.0F;
    private static final float FACE_V_MAX = 16.0F / 64.0F;
    private static final float HEAD_TOP_U_MIN = 8.0F / 64.0F;
    private static final float HEAD_TOP_U_MAX = 16.0F / 64.0F;
    private static final float HEAD_TOP_V_MIN = 0.0F;
    private static final float HEAD_TOP_V_MAX = 8.0F / 64.0F;
    private static final float HEAD_BOTTOM_U_MIN = 16.0F / 64.0F;
    private static final float HEAD_BOTTOM_U_MAX = 24.0F / 64.0F;
    private static final float HEAD_RIGHT_U_MIN = 0.0F;
    private static final float HEAD_RIGHT_U_MAX = 8.0F / 64.0F;
    private static final float HEAD_LEFT_U_MIN = 16.0F / 64.0F;
    private static final float HEAD_LEFT_U_MAX = 24.0F / 64.0F;
    private static final float HEAD_BACK_U_MIN = 24.0F / 64.0F;
    private static final float HEAD_BACK_U_MAX = 32.0F / 64.0F;
    private static final int GAZE_TURN_TICKS = 12;
    private static final int GAZE_HOLD_TICKS = 30;
    private static final int GAZE_RETURN_TICKS = 16;
    private static final int GAZE_TOTAL_TICKS =
            GAZE_TURN_TICKS + GAZE_HOLD_TICKS + GAZE_RETURN_TICKS;
    private static final int EYE_IGNITION_TICKS = 60;
    private static final int MAX_IGNITION_DELAY_TICKS = 600;

    private static BlockPos anchor = BlockPos.ZERO;
    private static boolean initialized;
    private static boolean awakened;
    private static boolean ignitionPending;
    private static float featureOpacity;
    private static float eyeIgnition;
    private static int previousTier;
    private static long ignitionDeadline;
    private static long nextGazeTick;
    private static int gazeTicks;
    private static float gazeDirection = 1.0F;
    private static MasterArchitectAdornmentModel skyCrown;

    private MasterArchitectSkyFaceRenderer() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (minecraft.isPaused() || !FrozenDawnConfig.ENABLE_MASTER_SKY_FACE.get()) {
            return;
        }
        if (!MasterArchitectWeather.hasAuraAnchor()) {
            return;
        }

        BlockPos currentAnchor = MasterArchitectWeather.getHearthCenter();
        int tier = MasterArchitectWeather.getAuraTier();
        boolean fightActive = MasterArchitectWeather.isFightActive();
        long gameTime = minecraft.level.getGameTime();
        if (!initialized || !anchor.equals(currentAnchor)) {
            initialize(currentAnchor, tier, fightActive, gameTime);
            return;
        }

        if (tier >= MasterArchitectAuraTier.NOTICED) {
            awakened = true;
        }
        float targetOpacity = MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                tier, fightActive, awakened);
        int transitionSeconds = targetOpacity >= featureOpacity
                ? FrozenDawnConfig.MASTER_SKY_FACE_FADE_IN_SECONDS.get()
                : FrozenDawnConfig.MASTER_SKY_FACE_FADE_OUT_SECONDS.get();
        featureOpacity = moveToward(
                featureOpacity,
                targetOpacity,
                1.0F / Math.max(1, transitionSeconds * 20));

        if (tier >= MasterArchitectAuraTier.FIGHT
                && previousTier < MasterArchitectAuraTier.FIGHT) {
            ignitionPending = true;
            ignitionDeadline = gameTime + MAX_IGNITION_DELAY_TICKS;
        }
        if (ignitionPending
                && (gameTime >= ignitionDeadline || cameraLooksTowardFace(currentAnchor))) {
            ignitionPending = false;
        }
        if (tier >= MasterArchitectAuraTier.FIGHT && !ignitionPending) {
            eyeIgnition = Math.min(1.0F,
                    eyeIgnition + 1.0F / EYE_IGNITION_TICKS);
        }

        tickGaze(gameTime, tier);
        previousTier = tier;
    }

    public static void render(
            RenderLevelStageEvent event,
            BlockPos center,
            int auraTier,
            boolean fightActive,
            int aftermathTicks,
            float aftermathStrength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!FrozenDawnConfig.ENABLE_MASTER_SKY_FACE.get()
                || minecraft.level == null
                || minecraft.player == null
                || center.equals(BlockPos.ZERO)) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Vec3 actualCenter = center.getCenter().add(
                0.0D, MasterArchitectSkyFacePolicy.ALTITUDE, 0.0D);
        Vec3 relative = actualCenter.subtract(camera);
        double horizontalDistance = Math.sqrt(
                relative.x * relative.x + relative.z * relative.z);
        double maximumDistance = FrozenDawnConfig.MASTER_SKY_FACE_RENDER_DISTANCE.get();
        float rangeVisibility = MasterArchitectSkyFacePolicy.rangeVisibility(
                horizontalDistance, maximumDistance);
        if (rangeVisibility <= 0.001F) {
            return;
        }
        float proximity = MasterArchitectSkyFacePolicy.proximityVisibility(
                horizontalDistance);
        if (proximity <= 0.001F) {
            return;
        }

        boolean aftermath = aftermathTicks > 0;
        MasterArchitectSkyFacePolicy.AftermathFace death = aftermath
                ? MasterArchitectSkyFacePolicy.aftermath(
                aftermathTicks, aftermathStrength)
                : MasterArchitectSkyFacePolicy.aftermath(0, 1.0F);
        float baseOpacity = aftermath
                ? 1.0F
                : featureOpacity;
        float opacity = baseOpacity
                * death.opacity()
                * proximity
                * rangeVisibility
                * FrozenDawnConfig.MASTER_SKY_FACE_OPACITY.get().floatValue();
        if (opacity <= 0.003F) {
            return;
        }

        Vec3 renderCenter = relative;
        double anchoredDistance = MasterArchitectSkyFacePolicy.renderedHorizontalDistance(
                horizontalDistance);
        if (horizontalDistance > anchoredDistance) {
            Vec3 direction = new Vec3(relative.x, 0.0D, relative.z).normalize();
            renderCenter = new Vec3(
                    direction.x * anchoredDistance,
                    relative.y,
                    direction.z * anchoredDistance);
        }
        double renderedDistance = Math.sqrt(
                renderCenter.x * renderCenter.x + renderCenter.z * renderCenter.z);
        float size = MasterArchitectSkyFacePolicy.apparentSize(
                horizontalDistance,
                renderedDistance,
                FrozenDawnConfig.MASTER_SKY_FACE_SCALE.get().floatValue())
                * death.scale();
        renderCenter = renderCenter.add(0.0D, death.verticalOffset(), 0.0D);

        double invDistance = 1.0D / Math.max(0.001D, renderedDistance);
        Vec3 normal = new Vec3(
                -renderCenter.x * invDistance,
                0.0D,
                -renderCenter.z * invDistance);
        float yawOffset = aftermath ? 0.0F : currentGazeYaw(minecraft.level.getGameTime());
        Vec3 turnedNormal = rotateY(normal, yawOffset * Mth.DEG_TO_RAD);
        Vec3 right = new Vec3(turnedNormal.z, 0.0D, -turnedNormal.x);
        double time = minecraft.level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float distortionX = death.distortion() <= 0.0F
                ? 0.0F
                : Mth.sin((float) time * 0.37F) * death.distortion() * 0.34F;
        Vec3 drawCenter = renderCenter.add(right.scale(distortionX));

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        ResourceLocation headTexture = ArchitectRenderer.textureForBlinkCycle(
                Mth.floor(time), Long.hashCode(center.asLong()));
        RenderSystem.setShaderTexture(0, headTexture);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        drawHead(event.getPoseStack(), drawCenter, right, turnedNormal,
                size, opacity,
                death.distortion(), death.tearProgress(), time,
                0.70F, 0.82F, 0.90F);

        float ignition = aftermath ? 1.0F : eyeIgnition;
        float eyeStrength = MasterArchitectSkyFacePolicy.targetEyeStrength(
                auraTier, fightActive, ignition)
                * death.opacity()
                * proximity
                * rangeVisibility;
        float hostilePulse = 0.84F + 0.22F * Mth.sin(
                (float) time * (fightActive ? 0.32F : 0.15F));
        eyeStrength = Mth.clamp(eyeStrength * hostilePulse * 1.28F, 0.0F, 1.0F);
        renderCrown(
                event.getPoseStack(),
                drawCenter,
                turnedNormal,
                size,
                opacity * (1.0F - death.tearProgress()),
                eyeStrength,
                auraTier,
                fightActive,
                time);
        if (eyeStrength > 0.002F) {
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, headTexture);
            // Re-rendering the same exact face with additive blend makes only its
            // cyan eye pixels burn; the black face pixels contribute almost nothing.
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE);
            drawFace(event.getPoseStack(),
                    drawCenter.add(turnedNormal.scale(size * 0.5F + 0.02F)),
                    right, size, eyeStrength,
                    death.distortion(), death.tearProgress(), time,
                    0.82F, 0.96F, 1.0F);
            drawFace(event.getPoseStack(),
                    drawCenter.add(turnedNormal.scale(size * 0.5F + 0.035F)),
                    right, size * 1.055F, eyeStrength * 0.52F,
                    death.distortion(), death.tearProgress(), time,
                    0.72F, 0.94F, 1.0F);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static void clear() {
        anchor = BlockPos.ZERO;
        initialized = false;
        awakened = false;
        ignitionPending = false;
        featureOpacity = 0.0F;
        eyeIgnition = 0.0F;
        previousTier = MasterArchitectAuraTier.NONE;
        ignitionDeadline = 0L;
        nextGazeTick = 0L;
        gazeTicks = 0;
        gazeDirection = 1.0F;
        skyCrown = null;
    }

    private static void initialize(
            BlockPos currentAnchor, int tier, boolean fightActive, long gameTime) {
        anchor = currentAnchor.immutable();
        awakened = tier >= MasterArchitectAuraTier.NOTICED;
        featureOpacity = MasterArchitectSkyFacePolicy.targetFeatureOpacity(
                tier, fightActive, awakened);
        eyeIgnition = tier >= MasterArchitectAuraTier.FIGHT ? 1.0F : 0.0F;
        ignitionPending = false;
        previousTier = tier;
        gazeTicks = 0;
        gazeDirection = signedHash(currentAnchor.asLong());
        nextGazeTick = gameTime + gazeInterval(currentAnchor, gameTime);
        initialized = true;
    }

    private static void tickGaze(long gameTime, int tier) {
        if (gazeTicks > 0) {
            gazeTicks--;
            if (gazeTicks == 0) {
                gazeDirection = -gazeDirection;
                nextGazeTick = gameTime + gazeInterval(anchor, gameTime);
            }
            return;
        }
        if (tier >= MasterArchitectAuraTier.FIGHT && gameTime >= nextGazeTick) {
            gazeTicks = GAZE_TOTAL_TICKS;
        }
    }

    private static float currentGazeYaw(long gameTime) {
        float drift = Mth.sin((float) gameTime * 0.0037F) * 4.0F;
        float away = gazeDirection * (34.0F + drift);
        if (gazeTicks <= 0) {
            return away;
        }
        int elapsed = GAZE_TOTAL_TICKS - gazeTicks;
        float blend;
        if (elapsed < GAZE_TURN_TICKS) {
            blend = smooth(elapsed / (float) GAZE_TURN_TICKS);
        } else if (elapsed < GAZE_TURN_TICKS + GAZE_HOLD_TICKS) {
            blend = 1.0F;
        } else {
            blend = 1.0F - smooth((elapsed - GAZE_TURN_TICKS - GAZE_HOLD_TICKS)
                    / (float) GAZE_RETURN_TICKS);
        }
        return Mth.lerp(blend, away, 0.0F);
    }

    private static boolean cameraLooksTowardFace(BlockPos center) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 towardFace = center.getCenter()
                .add(0.0D, MasterArchitectSkyFacePolicy.ALTITUDE, 0.0D)
                .subtract(camera.getPosition());
        if (towardFace.lengthSqr() < 1.0E-4D) {
            return true;
        }
        Vector3f look = camera.getLookVector();
        return new Vec3(look.x(), look.y(), look.z())
                .dot(towardFace.normalize()) >= 0.965D;
    }

    private static long gazeInterval(BlockPos center, long gameTime) {
        int average = FrozenDawnConfig.MASTER_SKY_FACE_GAZE_SECONDS.get() * 20;
        long hash = mix64(center.asLong() ^ gameTime ^ 0x59C34A7D1B2E6F08L);
        float range = (float) ((hash >>> 11) * 0x1.0p-53);
        return Math.max(80L, Math.round(average * Mth.lerp(range, 0.55F, 1.45F)));
    }

    private static float signedHash(long seed) {
        return (mix64(seed) & 1L) == 0L ? -1.0F : 1.0F;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static void drawFace(
            PoseStack poses,
            Vec3 center,
            Vec3 right,
            float size,
            float alpha,
            float distortion,
            float tear,
            double time,
            float red,
            float green,
            float blue) {
        poses.pushPose();
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        if (tear <= 0.001F) {
            addFacePart(buffer, matrix, center, right, size,
                    -1.0F, 1.0F, -1.0F, 1.0F,
                    FACE_U_MIN, FACE_U_MAX, FACE_V_MIN, FACE_V_MAX,
                    Vec3.ZERO, alpha, red, green, blue);
        } else {
            for (int yPart = 0; yPart < 2; yPart++) {
                for (int xPart = 0; xPart < 2; xPart++) {
                    float xMin = xPart == 0 ? -1.0F : 0.0F;
                    float xMax = xPart == 0 ? 0.0F : 1.0F;
                    float yMin = yPart == 0 ? -1.0F : 0.0F;
                    float yMax = yPart == 0 ? 0.0F : 1.0F;
                    float uMin = xPart == 0 ? FACE_U_MIN
                            : (FACE_U_MIN + FACE_U_MAX) * 0.5F;
                    float uMax = xPart == 0 ? (FACE_U_MIN + FACE_U_MAX) * 0.5F
                            : FACE_U_MAX;
                    float vMin = yPart == 0 ? (FACE_V_MIN + FACE_V_MAX) * 0.5F
                            : FACE_V_MIN;
                    float vMax = yPart == 0 ? FACE_V_MAX
                            : (FACE_V_MIN + FACE_V_MAX) * 0.5F;
                    float xSign = xPart == 0 ? -1.0F : 1.0F;
                    float ySign = yPart == 0 ? -1.0F : 1.0F;
                    int index = yPart * 2 + xPart;
                    float pulse = Mth.sin((float) time * 0.11F + index * 1.9F);
                    Vec3 offset = right.scale(xSign * size * tear * (0.20F + index * 0.035F))
                            .add(0.0D,
                                    ySign * size * tear * (0.13F + index * 0.025F),
                                    pulse * distortion * 0.08F);
                    addFacePart(buffer, matrix, center, right, size,
                            xMin, xMax, yMin, yMax,
                            uMin, uMax, vMin, vMax,
                            offset, alpha, red, green, blue);
                }
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
    }

    private static void drawHead(
            PoseStack poses,
            Vec3 center,
            Vec3 right,
            Vec3 front,
            float size,
            float alpha,
            float distortion,
            float tear,
            double time,
            float red,
            float green,
            float blue) {
        if (tear > 0.001F) {
            drawFace(poses, center.add(front.scale(size * 0.5F)), right,
                    size, alpha, distortion, tear, time, red, green, blue);
            return;
        }

        poses.pushPose();
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        float half = size * 0.5F;
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        addOrientedFace(buffer, matrix, center.add(front.scale(half)), right, up, half,
                FACE_U_MIN, FACE_U_MAX, FACE_V_MIN, FACE_V_MAX,
                alpha, red, green, blue);
        addOrientedFace(buffer, matrix, center.add(front.scale(-half)),
                right.scale(-1.0D), up, half,
                HEAD_BACK_U_MIN, HEAD_BACK_U_MAX, FACE_V_MIN, FACE_V_MAX,
                alpha, red, green, blue);
        addOrientedFace(buffer, matrix, center.add(right.scale(half)),
                front.scale(-1.0D), up, half,
                HEAD_RIGHT_U_MIN, HEAD_RIGHT_U_MAX, FACE_V_MIN, FACE_V_MAX,
                alpha, red, green, blue);
        addOrientedFace(buffer, matrix, center.add(right.scale(-half)), front, up, half,
                HEAD_LEFT_U_MIN, HEAD_LEFT_U_MAX, FACE_V_MIN, FACE_V_MAX,
                alpha, red, green, blue);
        addOrientedFace(buffer, matrix, center.add(0.0D, half, 0.0D),
                right, front.scale(-1.0D), half,
                HEAD_TOP_U_MIN, HEAD_TOP_U_MAX, HEAD_TOP_V_MIN, HEAD_TOP_V_MAX,
                alpha, red, green, blue);
        addOrientedFace(buffer, matrix, center.add(0.0D, -half, 0.0D),
                right, front, half,
                HEAD_BOTTOM_U_MIN, HEAD_BOTTOM_U_MAX,
                HEAD_TOP_V_MIN, HEAD_TOP_V_MAX,
                alpha, red, green, blue);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        poses.popPose();
    }

    private static void renderCrown(
            PoseStack poses,
            Vec3 center,
            Vec3 front,
            float headSize,
            float alpha,
            float glowStrength,
            int auraTier,
            boolean fightActive,
            double time) {
        if (alpha <= 0.005F) {
            return;
        }
        MasterArchitectAdornmentModel crown = skyCrown();
        if (crown == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(center.x, center.y, center.z);
        float yaw = (float) Math.atan2(-front.x, -front.z);
        poses.mulPose(Axis.YP.rotation(yaw));
        float modelScale = headSize * 2.0F;
        poses.scale(-modelScale, -modelScale, modelScale);
        poses.translate(0.0F, 4.0F / 16.0F, 0.0F);

        RenderType baseType = RenderType.entityTranslucent(WHITE_TEXTURE);
        var base = buffers.getBuffer(baseType);
        int baseAlpha = Mth.floor(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        crown.renderCrownDark(
                poses,
                base,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(
                        baseAlpha,
                        MasterArchitectAdornmentLayer.CROWN_DARK_RED,
                        MasterArchitectAdornmentLayer.CROWN_DARK_GREEN,
                        MasterArchitectAdornmentLayer.CROWN_DARK_BLUE));
        crown.renderCrownFrost(
                poses,
                base,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(
                        baseAlpha,
                        MasterArchitectAdornmentLayer.CROWN_FROST_RED,
                        MasterArchitectAdornmentLayer.CROWN_FROST_GREEN,
                        MasterArchitectAdornmentLayer.CROWN_FROST_BLUE));
        buffers.endBatch(baseType);

        float pulse = 0.5F + 0.5F * Mth.sin(
                (float) time * (fightActive ? 0.32F : 0.15F));
        float tierBoost = Mth.clamp((auraTier - 1) * 0.12F, 0.0F, 0.24F);
        float crownGlow = Mth.clamp((fightActive
                ? 0.30F + pulse * 0.38F
                : 0.10F + pulse * 0.10F) + tierBoost,
                0.0F,
                1.0F) * glowStrength;
        if (crownGlow > 0.002F) {
            RenderType glowType = RenderType.entityTranslucentEmissive(WHITE_TEXTURE);
            crown.renderCrownFrost(
                    poses,
                    buffers.getBuffer(glowType),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(
                            Mth.floor(crownGlow * 255.0F),
                            MasterArchitectAdornmentLayer.CROWN_GLOW_RED,
                            MasterArchitectAdornmentLayer.CROWN_GLOW_GREEN,
                            MasterArchitectAdornmentLayer.CROWN_GLOW_BLUE));
            buffers.endBatch(glowType);
        }
        poses.popPose();
    }

    private static MasterArchitectAdornmentModel skyCrown() {
        if (skyCrown == null) {
            skyCrown = new MasterArchitectAdornmentModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(
                            MasterArchitectAdornmentModel.LAYER_LOCATION));
        }
        return skyCrown;
    }

    private static void addOrientedFace(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            Vec3 horizontal,
            Vec3 vertical,
            float half,
            float uMin,
            float uMax,
            float vMin,
            float vMax,
            float alpha,
            float red,
            float green,
            float blue) {
        Vec3 bottomRight = center.add(horizontal.scale(half))
                .add(vertical.scale(-half));
        Vec3 topRight = center.add(horizontal.scale(half))
                .add(vertical.scale(half));
        Vec3 topLeft = center.add(horizontal.scale(-half))
                .add(vertical.scale(half));
        Vec3 bottomLeft = center.add(horizontal.scale(-half))
                .add(vertical.scale(-half));
        addVertex(buffer, matrix, bottomRight, uMax, vMax, red, green, blue, alpha);
        addVertex(buffer, matrix, topRight, uMax, vMin, red, green, blue, alpha);
        addVertex(buffer, matrix, topLeft, uMin, vMin, red, green, blue, alpha);
        addVertex(buffer, matrix, bottomLeft, uMin, vMax, red, green, blue, alpha);
    }

    private static void addFacePart(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 center,
            Vec3 right,
            float size,
            float xMin,
            float xMax,
            float yMin,
            float yMax,
            float uMin,
            float uMax,
            float vMin,
            float vMax,
            Vec3 offset,
            float alpha,
            float red,
            float green,
            float blue) {
        float half = size * 0.5F;
        Vec3 bottomRight = center.add(right.scale(xMax * half))
                .add(0.0D, yMin * half, 0.0D).add(offset);
        Vec3 topRight = center.add(right.scale(xMax * half))
                .add(0.0D, yMax * half, 0.0D).add(offset);
        Vec3 topLeft = center.add(right.scale(xMin * half))
                .add(0.0D, yMax * half, 0.0D).add(offset);
        Vec3 bottomLeft = center.add(right.scale(xMin * half))
                .add(0.0D, yMin * half, 0.0D).add(offset);
        addVertex(buffer, matrix, bottomRight, uMax, vMax, red, green, blue, alpha);
        addVertex(buffer, matrix, topRight, uMax, vMin, red, green, blue, alpha);
        addVertex(buffer, matrix, topLeft, uMin, vMin, red, green, blue, alpha);
        addVertex(buffer, matrix, bottomLeft, uMin, vMax, red, green, blue, alpha);
    }

    private static void addVertex(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 position,
            float u,
            float v,
            float red,
            float green,
            float blue,
            float alpha) {
        buffer.addVertex(matrix,
                        (float) position.x,
                        (float) position.y,
                        (float) position.z)
                .setUv(u, v)
                .setColor(red, green, blue, Mth.clamp(alpha, 0.0F, 1.0F));
    }

    private static Vec3 rotateY(Vec3 vector, float radians) {
        float cosine = Mth.cos(radians);
        float sine = Mth.sin(radians);
        return new Vec3(
                vector.x * cosine - vector.z * sine,
                0.0D,
                vector.x * sine + vector.z * cosine);
    }

    private static float moveToward(float current, float target, float step) {
        return current < target
                ? Math.min(target, current + step)
                : Math.max(target, current - step);
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
