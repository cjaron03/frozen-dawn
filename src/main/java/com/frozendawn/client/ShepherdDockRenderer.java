package com.frozendawn.client;

import com.frozendawn.shepherd.ShepherdBoot;
import com.frozendawn.shepherd.ShepherdDockBlock;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdDockBlockEntity;
import com.frozendawn.shepherd.ShepherdFace.Cells;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Her at rest on the dock: Offline until Boot, then a blinking cursor while the BIOS runs. */
public final class ShepherdDockRenderer implements BlockEntityRenderer<ShepherdDockBlockEntity> {
    /** How far above the block's floor she sits, in voxels, from make_dock_mockup.seated(). */
    private static final float SEAT_Y = 5 / 16F;
    private static final float MODEL_HEIGHT = 1.501F;

    private final ShepherdModel model;

    public ShepherdDockRenderer(BlockEntityRendererProvider.Context context) {
        model = new ShepherdModel(context.bakeLayer(ShepherdModel.LAYER_LOCATION));
    }

    @Override public void render(ShepherdDockBlockEntity dock, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = dock.getBlockState();
        if (!state.getValue(ShepherdDockBlock.STATE).holdsHer()) return;
        pose.pushPose();
        pose.translate(0.5, SEAT_Y, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(ShepherdDockBlock.FACING).toYRot()));
        pose.scale(-1, -1, 1);
        pose.translate(0, -MODEL_HEIGHT, 0);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(ShepherdModel.TEXTURE)), light, overlay);
        boolean cursor = state.getValue(ShepherdDockBlock.STATE) == DockState.BOOT && dock.getLevel().getGameTime() / ShepherdBoot.LINE_TICKS % 2 == 0;
        Cells face = cursor ? ShepherdBoot.cursor() : Mood.OFFLINE;
        ShepherdFaceRenderer.drawOnModel(pose, buffers, face, 0, false, 0);
        pose.popPose();
    }
}
