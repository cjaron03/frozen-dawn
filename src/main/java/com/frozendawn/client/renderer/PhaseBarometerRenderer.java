package com.frozendawn.client.renderer;

import com.frozendawn.barometer.PhaseBarometerForecasts;
import com.frozendawn.barometer.PhaseBarometerSnapshot;
import com.frozendawn.block.PhaseBarometerBlock;
import com.frozendawn.block.PhaseBarometerBlockEntity;
import com.frozendawn.client.ApocalypseClientData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class PhaseBarometerRenderer implements BlockEntityRenderer<PhaseBarometerBlockEntity> {

    private static final BlockState PANEL_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState PANEL_DARK_STATE = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BORDER_STATE = Blocks.CYAN_TERRACOTTA.defaultBlockState();
    private static final BlockState GREEN_STATE = Blocks.LIME_CONCRETE.defaultBlockState();
    private static final BlockState CYAN_STATE = Blocks.CYAN_CONCRETE.defaultBlockState();
    private static final BlockState AMBER_STATE = Blocks.YELLOW_CONCRETE.defaultBlockState();
    private static final BlockState ORANGE_STATE = Blocks.ORANGE_CONCRETE.defaultBlockState();
    private static final BlockState RED_STATE = Blocks.RED_CONCRETE.defaultBlockState();
    private static final float FRONT_OVERLAY_Z = 0.96f / 16f;
    private static final float FRONT_GLOW_Z = 0.92f / 16f;
    private static final float TEXT_Z = 1.01f / 16f;

    private final Font font;
    private final BlockRenderDispatcher blockRenderer;

    public PhaseBarometerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PhaseBarometerBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        PhaseBarometerSnapshot snapshot = PhaseBarometerForecasts.evaluate(
                ApocalypseClientData.getPhase(),
                ApocalypseClientData.getProgress()
        );

        poseStack.pushPose();
        Direction facing = entity.getBlockState().getValue(PhaseBarometerBlock.FACING);
        float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.translate(-0.5, -0.5, -0.5);

        float emissiveLight = 0x00F000F0;
        boolean blinkOn = !snapshot.shouldBlink() || entity.getLevel() == null || ((entity.getLevel().getGameTime() / 8L) & 1L) == 1L;
        BlockState lampState = warningLampState(snapshot);

        renderScaledBlock(poseStack, bufferSource, BORDER_STATE, 3.2f / 16f, 10.15f / 16f, FRONT_OVERLAY_Z,
                7.1f / 16f, 0.08f / 16f, 0.022f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, CYAN_STATE, 3.45f / 16f, 2.0f / 16f, FRONT_OVERLAY_Z,
                6.6f / 16f, 0.07f / 16f, 0.022f / 16f, (int) emissiveLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, PANEL_DARK_STATE, 2.75f / 16f, 3.25f / 16f, FRONT_OVERLAY_Z,
                1.65f / 16f, 3.05f / 16f, 0.024f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, PANEL_STATE, 2.95f / 16f, 3.45f / 16f, FRONT_GLOW_Z,
                1.25f / 16f, 2.65f / 16f, 0.02f / 16f, packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, lampState, 3.18f / 16f, 4.42f / 16f, FRONT_GLOW_Z,
                0.80f / 16f, 1.22f / 16f, 0.03f / 16f, blinkOn ? (int) emissiveLight : packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, lampState, 3.02f / 16f, 4.27f / 16f, 0.88f / 16f,
                1.12f / 16f, 1.52f / 16f, 0.015f / 16f, blinkOn ? (int) emissiveLight : packedLight, packedOverlay);
        renderScaledBlock(poseStack, bufferSource, PANEL_DARK_STATE, 3.25f / 16f, 1.95f / 16f, FRONT_OVERLAY_Z,
                7.2f / 16f, 0.12f / 16f, 0.022f / 16f, packedLight, packedOverlay);
        float fillWidth = Math.max(0.45f / 16f, (7.0f * snapshot.severity()) / 16f);
        renderScaledBlock(poseStack, bufferSource, trendState(snapshot), 3.45f / 16f, 2.0f / 16f, 0.90f / 16f,
                fillWidth, 0.05f / 16f, 0.016f / 16f, packedLight, packedOverlay);

        renderPhaseText(poseStack, bufferSource, (int) emissiveLight, snapshot);
        poseStack.popPose();
    }

    private void renderPhaseText(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                 PhaseBarometerSnapshot snapshot) {
        poseStack.pushPose();
        poseStack.translate(0.228f, 0.56f, TEXT_Z);
        poseStack.scale(0.015f, -0.015f, 0.015f);
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(
                "P" + snapshot.currentPhase(),
                0.0f,
                0.0f,
                0xFFE8F7F9,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.23f, 0.395f, TEXT_Z);
        poseStack.scale(0.0066f, -0.0066f, 0.0066f);
        Matrix4f bandMatrix = poseStack.last().pose();
        font.drawInBatch(
                bandCode(snapshot),
                0.0f,
                0.0f,
                0xFF8FD9E5,
                false,
                bandMatrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );
        poseStack.popPose();
    }

    private String bandCode(PhaseBarometerSnapshot snapshot) {
        return switch (snapshot.forecastBand()) {
            case STABLE -> "STBL";
            case DETERIORATING -> "DTR";
            case TRANSITION_LIKELY_SOON -> "SOON";
            case IMMINENT -> "IMNT";
            case COLLAPSE_UNDERWAY -> "COLL";
        };
    }

    private BlockState trendState(PhaseBarometerSnapshot snapshot) {
        return switch (snapshot.forecastBand()) {
            case STABLE -> GREEN_STATE;
            case DETERIORATING -> CYAN_STATE;
            case TRANSITION_LIKELY_SOON -> AMBER_STATE;
            case IMMINENT, COLLAPSE_UNDERWAY -> ORANGE_STATE;
        };
    }

    private BlockState warningLampState(PhaseBarometerSnapshot snapshot) {
        if (snapshot.forecastBand().isHighUrgency()) {
            return RED_STATE;
        }
        if (snapshot.warning() != com.frozendawn.barometer.BarometerWarning.NONE
                || snapshot.forecastBand() == com.frozendawn.barometer.ForecastBand.TRANSITION_LIKELY_SOON) {
            return ORANGE_STATE;
        }
        if (snapshot.forecastBand() == com.frozendawn.barometer.ForecastBand.DETERIORATING) {
            return CYAN_STATE;
        }
        return GREEN_STATE;
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
}
