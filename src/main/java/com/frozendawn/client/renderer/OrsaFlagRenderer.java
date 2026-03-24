package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.OrsaFlagBlock;
import com.frozendawn.block.OrsaFlagBlockEntity;
import com.frozendawn.client.FlagPhysicsHelper;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Renders the ORSA flag with animated cloth segments.
 * Pole is rendered from a simple brown color; cloth uses phase-appropriate
 * 32x64 textures that degrade from clean to frozen across the apocalypse.
 * Each flag deterministically picks intact or torn variant based on position.
 */
public class OrsaFlagRenderer implements BlockEntityRenderer<OrsaFlagBlockEntity> {

    // Phase-based textures: intact variants
    private static final ResourceLocation TEX_CLEAN =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_clean.png");
    private static final ResourceLocation TEX_WEATHERED =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_weathered.png");
    private static final ResourceLocation TEX_FROSTED =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_frosted.png");
    private static final ResourceLocation TEX_FROZEN =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_frozen.png");

    // Phase-based textures: torn variants
    private static final ResourceLocation TEX_TORN_CLEAN =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_torn_clean.png");
    private static final ResourceLocation TEX_TORN_WEATHERED =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_torn_weathered.png");
    private static final ResourceLocation TEX_TORN_FROSTED =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_torn_frosted.png");
    private static final ResourceLocation TEX_TORN_FROZEN =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/block/orsa_flag_torn_frozen.png");

    // Pole dimensions (in block units, 1.0 = 16 pixels)
    private static final float POLE_WIDTH = 1.0f / 16.0f;
    private static final float POLE_HEIGHT = 1.0f;
    private static final float POLE_X = 7.5f / 16.0f;

    // Cloth dimensions
    private static final float CLOTH_ATTACH_Y = 15.0f / 16.0f;
    private static final float SEGMENT_LENGTH = 3.0f / 16.0f;
    private static final float CLOTH_HEIGHT = 12.0f / 16.0f;
    private static final float CLOTH_THICKNESS = 0.15f / 16.0f;
    private static final float CLOTH_HOIST_OVERLAP = 0.75f / 16.0f;

    // Pole color (dark wood brown)
    private static final int POLE_R = 90, POLE_G = 65, POLE_B = 45;

    public OrsaFlagRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OrsaFlagBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        poseStack.pushPose();

        // Rotate whole model by block facing
        Direction facing = entity.getBlockState().getValue(OrsaFlagBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.translate(-0.5, 0.0, -0.5);

        // Render pole with solid color (uses white texture tinted by vertex color)
        ResourceLocation clothTexture = getTextureForPhase(entity.getBlockPos());
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(clothTexture));

        renderPole(poseStack, consumer, packedLight, packedOverlay);

        // Render cloth segments
        long gameTime = entity.getLevel() != null ? entity.getLevel().getGameTime() : 0L;
        renderCloth(poseStack, consumer, packedLight, packedOverlay, entity, partialTick, gameTime);

        poseStack.popPose();
    }

    private static ResourceLocation getTextureForPhase(BlockPos pos) {
        int phase = FrozenDawnPhaseTracker.getPhase();
        // ~30% of flags are torn, determined by position hash
        long hash = pos.asLong() * 6364136223846793005L + 1442695040888963407L;
        boolean torn = Math.floorMod(hash >> 16, 100) < 30;

        if (phase >= 5) return torn ? TEX_TORN_FROZEN : TEX_FROZEN;       // phase 5-6: frozen solid
        if (phase >= 4) return torn ? TEX_TORN_FROSTED : TEX_FROSTED;    // phase 4: frosted
        if (phase >= 3) return torn ? TEX_TORN_WEATHERED : TEX_WEATHERED; // phase 3: weathered
        return torn ? TEX_TORN_CLEAN : TEX_CLEAN;                        // phase 0-2: clean
    }

    private void renderPole(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        poseStack.pushPose();

        float x0 = POLE_X;
        float x1 = POLE_X + POLE_WIDTH;
        float z0 = 8.0f / 16.0f - POLE_WIDTH / 2.0f;
        float z1 = z0 + POLE_WIDTH;

        // Use a tiny corner of the texture for the pole, tinted brown via vertex color
        float pu0 = 0.0f, pu1 = 1.0f / 32.0f;
        float pv0 = 0.0f, pv1 = 1.0f / 64.0f;

        Matrix4f mat = poseStack.last().pose();
        PoseStack.Pose pose = poseStack.last();

        // North face
        poleVertex(consumer, mat, pose, x0, 0, z0, pu0, pv1, 0, 0, -1, light, overlay);
        poleVertex(consumer, mat, pose, x1, 0, z0, pu1, pv1, 0, 0, -1, light, overlay);
        poleVertex(consumer, mat, pose, x1, POLE_HEIGHT, z0, pu1, pv0, 0, 0, -1, light, overlay);
        poleVertex(consumer, mat, pose, x0, POLE_HEIGHT, z0, pu0, pv0, 0, 0, -1, light, overlay);

        // South face
        poleVertex(consumer, mat, pose, x1, 0, z1, pu0, pv1, 0, 0, 1, light, overlay);
        poleVertex(consumer, mat, pose, x0, 0, z1, pu1, pv1, 0, 0, 1, light, overlay);
        poleVertex(consumer, mat, pose, x0, POLE_HEIGHT, z1, pu1, pv0, 0, 0, 1, light, overlay);
        poleVertex(consumer, mat, pose, x1, POLE_HEIGHT, z1, pu0, pv0, 0, 0, 1, light, overlay);

        // West face
        poleVertex(consumer, mat, pose, x0, 0, z1, pu0, pv1, -1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x0, 0, z0, pu1, pv1, -1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x0, POLE_HEIGHT, z0, pu1, pv0, -1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x0, POLE_HEIGHT, z1, pu0, pv0, -1, 0, 0, light, overlay);

        // East face
        poleVertex(consumer, mat, pose, x1, 0, z0, pu0, pv1, 1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x1, 0, z1, pu1, pv1, 1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x1, POLE_HEIGHT, z1, pu1, pv0, 1, 0, 0, light, overlay);
        poleVertex(consumer, mat, pose, x1, POLE_HEIGHT, z0, pu0, pv0, 1, 0, 0, light, overlay);

        poseStack.popPose();
    }

    /**
     * Computes the blizzard wind angle in degrees, matching the formula in
     * {@link com.frozendawn.client.WeatherParticles}. Returns 0 for phases below 5.
     */
    private static float getWindYaw(long gameTime, int phase) {
        if (phase < 5) return 0.0f;
        // Same rotating wind angle as WeatherParticles: gameTime * 0.005
        float windAngleRad = gameTime * 0.005f;
        return (float) Math.toDegrees(windAngleRad);
    }

    private void renderCloth(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                             OrsaFlagBlockEntity entity, float partialTick, long gameTime) {
        poseStack.pushPose();

        // Move to cloth attachment point on the pole
        poseStack.translate(POLE_X + POLE_WIDTH - CLOTH_HOIST_OVERLAP, CLOTH_ATTACH_Y, 8.0f / 16.0f);

        // In phase 5+, pivot cloth to follow blizzard wind direction
        int phase = FrozenDawnPhaseTracker.getPhase();
        if (phase >= 5) {
            float windYaw = getWindYaw(gameTime, phase);
            poseStack.mulPose(Axis.YP.rotationDegrees(windYaw));
        }

        float[] curveX = new float[FlagPhysicsHelper.SEGMENTS + 1];
        float[] curveZ = new float[FlagPhysicsHelper.SEGMENTS + 1];
        float[] sag = new float[FlagPhysicsHelper.SEGMENTS + 1];
        float tipSag = FlagPhysicsHelper.getTipSag(phase);

        for (int i = 0; i < FlagPhysicsHelper.SEGMENTS; i++) {
            curveX[i + 1] = curveX[i] + SEGMENT_LENGTH;
            float angleRad = entity.getRenderAngle(i, partialTick) * Mth.DEG_TO_RAD;
            curveZ[i + 1] = curveZ[i] + Mth.sin(angleRad) * SEGMENT_LENGTH;
        }

        for (int i = 0; i <= FlagPhysicsHelper.SEGMENTS; i++) {
            float t = i / (float) FlagPhysicsHelper.SEGMENTS;
            sag[i] = tipSag * t * t;
        }

        // UV spans the full 32x64 texture horizontally across all segments.
        // Keeping x->u mapping stable ensures the ORSA logo stays correctly oriented on the front face.
        float uvStep = 1.0f / FlagPhysicsHelper.SEGMENTS;

        for (int i = 0; i < FlagPhysicsHelper.SEGMENTS; i++) {
            Matrix4f mat = poseStack.last().pose();
            PoseStack.Pose pose = poseStack.last();

            float x0 = curveX[i];
            float x1 = curveX[i + 1];
            float z0 = curveZ[i];
            float z1 = curveZ[i + 1];
            float yTop0 = -sag[i];
            float yTop1 = -sag[i + 1];
            float yBottom0 = yTop0 - CLOTH_HEIGHT;
            float yBottom1 = yTop1 - CLOTH_HEIGHT;

            float dx = x1 - x0;
            float dz = z1 - z0;
            float normalLen = Mth.sqrt(dx * dx + dz * dz);
            float nx = normalLen > 0.0f ? dz / normalLen : 0.0f;
            float nz = normalLen > 0.0f ? -dx / normalLen : -1.0f;
            float thicknessOffset = CLOTH_THICKNESS * 0.5f;
            float offsetX = nx * thicknessOffset;
            float offsetZ = nz * thicknessOffset;

            float u0 = i * uvStep;
            float u1 = u0 + uvStep;

            // Front face
            clothVertex(consumer, mat, pose, x0 + offsetX, yTop0, z0 + offsetZ, u0, 0.0f, nx, 0, nz, light, overlay);
            clothVertex(consumer, mat, pose, x1 + offsetX, yTop1, z1 + offsetZ, u1, 0.0f, nx, 0, nz, light, overlay);
            clothVertex(consumer, mat, pose, x1 + offsetX, yBottom1, z1 + offsetZ, u1, 1.0f, nx, 0, nz, light, overlay);
            clothVertex(consumer, mat, pose, x0 + offsetX, yBottom0, z0 + offsetZ, u0, 1.0f, nx, 0, nz, light, overlay);

            // Back face
            clothVertex(consumer, mat, pose, x1 - offsetX, yTop1, z1 - offsetZ, u1, 0.0f, -nx, 0, -nz, light, overlay);
            clothVertex(consumer, mat, pose, x0 - offsetX, yTop0, z0 - offsetZ, u0, 0.0f, -nx, 0, -nz, light, overlay);
            clothVertex(consumer, mat, pose, x0 - offsetX, yBottom0, z0 - offsetZ, u0, 1.0f, -nx, 0, -nz, light, overlay);
            clothVertex(consumer, mat, pose, x1 - offsetX, yBottom1, z1 - offsetZ, u1, 1.0f, -nx, 0, -nz, light, overlay);

            if (i == FlagPhysicsHelper.SEGMENTS - 1) {
                float edgeNx = normalLen > 0.0f ? dx / normalLen : 1.0f;
                float edgeNz = normalLen > 0.0f ? dz / normalLen : 0.0f;
                clothVertex(consumer, mat, pose, x1 + offsetX, yTop1, z1 + offsetZ, u1, 0.0f, edgeNx, 0, edgeNz, light, overlay);
                clothVertex(consumer, mat, pose, x1 - offsetX, yTop1, z1 - offsetZ, u1, 0.0f, edgeNx, 0, edgeNz, light, overlay);
                clothVertex(consumer, mat, pose, x1 - offsetX, yBottom1, z1 - offsetZ, u1, 1.0f, edgeNx, 0, edgeNz, light, overlay);
                clothVertex(consumer, mat, pose, x1 + offsetX, yBottom1, z1 + offsetZ, u1, 1.0f, edgeNx, 0, edgeNz, light, overlay);
            }
        }

        poseStack.popPose();
    }

    private static void clothVertex(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose,
                                    float x, float y, float z, float u, float v,
                                    float nx, float ny, float nz, int light, int overlay) {
        consumer.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static void poleVertex(VertexConsumer consumer, Matrix4f mat, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v,
                                   float nx, float ny, float nz, int light, int overlay) {
        consumer.addVertex(mat, x, y, z)
                .setColor(POLE_R, POLE_G, POLE_B, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
