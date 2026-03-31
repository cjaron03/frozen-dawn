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

        int haloAlpha = Math.max(32, Math.min(150, Math.round(beamIntensity * 124.0f)));
        int coreAlpha = Math.max(18, Math.min(220, Math.round(beamIntensity * 188.0f)));

        // Keep the source hot and local to the siren head. No projected bar or beam geometry.
        addFlatQuad(glow, poseEntry, pose, -0.18f, 0.78f, -0.02f, 0.18f, 1.02f, -0.02f,
                255, 60, 44, haloAlpha);
        addFlatQuad(glow, poseEntry, pose, -0.10f, 0.84f, -0.005f, 0.10f, 0.96f, -0.005f,
                255, 108, 80, coreAlpha);
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
