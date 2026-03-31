package com.frozendawn.client.renderer;

import com.frozendawn.block.AlarmBeaconBlock;
import com.frozendawn.block.AlarmBeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class AlarmBeaconRenderer implements BlockEntityRenderer<AlarmBeaconBlockEntity> {

    private static final BlockState BASE_STATE = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState TRIM_STATE = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState DARK_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState ACTIVE_LENS_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState INACTIVE_LENS_STATE = Blocks.TINTED_GLASS.defaultBlockState();
    private static final BlockState ACTIVE_CAP_STATE = Blocks.REDSTONE_BLOCK.defaultBlockState();
    private static final BlockState INACTIVE_CAP_STATE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final ResourceLocation GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("neoforge", "textures/white.png");

    private final BlockRenderDispatcher blockRenderer;

    public AlarmBeaconRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(AlarmBeaconBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        float beamIntensity = entity.getBeamIntensity(partialTick);

        poseStack.pushPose();
        Direction facing = entity.getBlockState().getValue(AlarmBeaconBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.translate(-0.5, 0.0, -0.5);

        renderScaledBlock(poseStack, bufferSource, BASE_STATE, 3f / 16f, 0.0f, 3f / 16f,
                10f / 16f, 4f / 16f, 10f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, TRIM_STATE, 4f / 16f, 4f / 16f, 4f / 16f,
                8f / 16f, 1f / 16f, 8f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, DARK_STATE, 5f / 16f, 5f / 16f, 5f / 16f,
                6f / 16f, 5f / 16f, 6f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, TRIM_STATE, 4f / 16f, 10f / 16f, 4f / 16f,
                8f / 16f, 1f / 16f, 8f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, DARK_STATE, 7f / 16f, 4f / 16f, 1f / 16f,
                2f / 16f, 3f / 16f, 2f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, DARK_STATE, 6f / 16f, 4f / 16f, 2f / 16f,
                4f / 16f, 2f / 16f, 2f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, DARK_STATE, 7f / 16f, 6f / 16f, 7f / 16f,
                2f / 16f, 5f / 16f, 2f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, TRIM_STATE, 6f / 16f, 11f / 16f, 6f / 16f,
                4f / 16f, 1f / 16f, 4f / 16f, packedLight, packedOverlay);

        int emissiveLight = beamIntensity > 0.04f ? 0x00F000F0 : packedLight;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        float spin = entity.getCombinedYawDegrees(partialTick) - baseYaw(facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.translate(-0.5, 0.0, -0.5);

        renderScaledBlock(poseStack, bufferSource, TRIM_STATE, 6f / 16f, 11f / 16f, 1f / 16f,
                4f / 16f, 1f / 16f, 7f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, beamIntensity > 0.08f ? ACTIVE_LENS_STATE : INACTIVE_LENS_STATE, 6f / 16f, 11f / 16f, 0f,
                4f / 16f, 2f / 16f, 3f / 16f, emissiveLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, beamIntensity > 0.12f ? ACTIVE_CAP_STATE : INACTIVE_CAP_STATE, 6f / 16f, 13f / 16f, 1f / 16f,
                4f / 16f, 1f / 16f, 4f / 16f, emissiveLight, packedOverlay);

        if (beamIntensity > 0.03f) {
            renderLensGlow(poseStack, bufferSource, beamIntensity);
        }
        poseStack.popPose();

        poseStack.popPose();
        poseStack.popPose();
    }

    private static float baseYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    private void renderScaledBlock(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state,
                                   float x, float y, float z, float scaleX, float scaleY, float scaleZ,
                                   int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scaleX, scaleY, scaleZ);
        blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private void renderLensGlow(PoseStack poseStack, MultiBufferSource bufferSource, float beamIntensity) {
        VertexConsumer glow = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(GLOW_TEXTURE));
        PoseStack.Pose poseEntry = poseStack.last();
        Matrix4f pose = poseEntry.pose();

        float lensLeft = 6.0f / 16.0f;
        float lensRight = 10.0f / 16.0f;
        float lensBottom = 11.0f / 16.0f;
        float lensTop = 13.0f / 16.0f;
        float lensCenterX = (lensLeft + lensRight) * 0.5f;
        float lensCenterY = (lensBottom + lensTop) * 0.5f;
        float lensFrontZ = -0.01f;

        int haloAlpha = Math.max(18, Math.min(96, Math.round(beamIntensity * 74.0f)));
        int coreAlpha = Math.max(40, Math.min(210, Math.round(beamIntensity * 174.0f)));
        int beamAlpha = Math.max(0, Math.min(68, Math.round(beamIntensity * 54.0f)));
        int beamCoreAlpha = Math.max(0, Math.min(104, Math.round(beamIntensity * 76.0f)));

        // Keep the source hot and local to the siren head, then add a very short tapered beam.
        addFlatQuad(glow, poseEntry, pose, lensCenterX - 0.15f, lensCenterY - 0.07f, lensFrontZ,
                lensCenterX + 0.15f, lensCenterY + 0.07f, lensFrontZ,
                180, 18, 18, haloAlpha);
        addFlatQuad(glow, poseEntry, pose, lensCenterX - 0.075f, lensCenterY - 0.035f, lensFrontZ + 0.006f,
                lensCenterX + 0.075f, lensCenterY + 0.035f, lensFrontZ + 0.006f,
                255, 72, 56, coreAlpha);

        if (beamAlpha > 0) {
            addTaperedBeamSlice(glow, poseEntry, pose,
                    lensCenterX - 0.045f, lensCenterY - 0.04f, lensFrontZ - 0.02f,
                    lensCenterX + 0.045f, lensCenterY + 0.04f, lensFrontZ - 0.02f,
                    lensCenterX - 0.11f, lensCenterY - 0.10f, lensFrontZ - 0.24f,
                    lensCenterX + 0.11f, lensCenterY + 0.10f, lensFrontZ - 0.24f,
                    164, 16, 16, beamAlpha);
        }
        if (beamCoreAlpha > 0) {
            addTaperedBeamSlice(glow, poseEntry, pose,
                    lensCenterX - 0.018f, lensCenterY - 0.03f, lensFrontZ - 0.015f,
                    lensCenterX + 0.018f, lensCenterY + 0.03f, lensFrontZ - 0.015f,
                    lensCenterX - 0.05f, lensCenterY - 0.07f, lensFrontZ - 0.18f,
                    lensCenterX + 0.05f, lensCenterY + 0.07f, lensFrontZ - 0.18f,
                    230, 54, 42, beamCoreAlpha);
        }
    }

    private void addFlatQuad(VertexConsumer consumer, PoseStack.Pose poseEntry, Matrix4f pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             int r, int g, int b, int a) {
        if (a <= 0) {
            return;
        }
        addVertex(consumer, poseEntry, pose, x0, y0, z0, r, g, b, a, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f);
        addVertex(consumer, poseEntry, pose, x1, y0, z0, r, g, b, a, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        addVertex(consumer, poseEntry, pose, x1, y1, z1, r, g, b, a, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        addVertex(consumer, poseEntry, pose, x0, y1, z1, r, g, b, a, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        addVertex(consumer, poseEntry, pose, x0, y1, z1, r, g, b, a, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f);
        addVertex(consumer, poseEntry, pose, x1, y1, z1, r, g, b, a, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f);
        addVertex(consumer, poseEntry, pose, x1, y0, z0, r, g, b, a, 1.0f, 1.0f, 0.0f, -1.0f, 0.0f);
        addVertex(consumer, poseEntry, pose, x0, y0, z0, r, g, b, a, 0.0f, 1.0f, 0.0f, -1.0f, 0.0f);
    }

    private void addTaperedBeamSlice(VertexConsumer consumer, PoseStack.Pose poseEntry, Matrix4f pose,
                                     float nearX0, float nearY0, float nearZ,
                                     float nearX1, float nearY1, float nearZ1,
                                     float farX0, float farY0, float farZ,
                                     float farX1, float farY1, float farZ1,
                                     int r, int g, int b, int a) {
        if (a <= 0) {
            return;
        }

        addVertex(consumer, poseEntry, pose, nearX0, nearY0, nearZ, r, g, b, a, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f);
        addVertex(consumer, poseEntry, pose, nearX1, nearY1, nearZ1, r, g, b, a, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f);
        addVertex(consumer, poseEntry, pose, farX1, farY1, farZ1, r, g, b, 0, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        addVertex(consumer, poseEntry, pose, farX0, farY0, farZ, r, g, b, 0, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);

        addVertex(consumer, poseEntry, pose, farX0, farY0, farZ, r, g, b, 0, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f);
        addVertex(consumer, poseEntry, pose, farX1, farY1, farZ1, r, g, b, 0, 1.0f, 0.0f, 0.0f, 0.0f, -1.0f);
        addVertex(consumer, poseEntry, pose, nearX1, nearY1, nearZ1, r, g, b, a, 1.0f, 1.0f, 0.0f, 0.0f, -1.0f);
        addVertex(consumer, poseEntry, pose, nearX0, nearY0, nearZ, r, g, b, a, 0.0f, 1.0f, 0.0f, 0.0f, -1.0f);
    }

    private void addVertex(VertexConsumer consumer, PoseStack.Pose poseEntry, Matrix4f pose,
                           float x, float y, float z,
                           int r, int g, int b, int a,
                           float u, float v,
                           float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(poseEntry, nx, ny, nz);
    }
}
