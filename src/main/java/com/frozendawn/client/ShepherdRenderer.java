package com.frozendawn.client;

import com.frozendawn.shepherd.ShepherdEntity;
import com.frozendawn.shepherd.ShepherdFace;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ShepherdRenderer extends MobRenderer<ShepherdEntity, ShepherdModel> {
    private static final float BOB_HEIGHT = 0.04F, BOB_SPEED = 0.12F;

    public ShepherdRenderer(EntityRendererProvider.Context context) {
        super(context, new ShepherdModel(context.bakeLayer(ShepherdModel.LAYER_LOCATION)), 0.3F);
        addLayer(new FaceLayer(this));
    }

    @Override public ResourceLocation getTextureLocation(ShepherdEntity entity) {
        return ShepherdModel.TEXTURE;
    }

    @Override public void render(ShepherdEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // She has no neck: where she looks is where the whole body points.
        entity.yBodyRot = entity.yHeadRot;
        entity.yBodyRotO = entity.yHeadRotO;
        pose.pushPose();
        if (!entity.offline()) pose.translate(0, Mth.sin((entity.tickCount + partial) * BOB_SPEED) * BOB_HEIGHT, 0);
        super.render(entity, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    private static final class FaceLayer extends RenderLayer<ShepherdEntity, ShepherdModel> {
        FaceLayer(RenderLayerParent<ShepherdEntity, ShepherdModel> parent) {
            super(parent);
        }

        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, ShepherdEntity entity, float limbSwing, float limbSwingAmount,
                float partial, float age, float headYaw, float headPitch) {
            Mood mood = entity.mood();
            var motion = ShepherdFace.motion(mood, entity.tickCount, entity.getId());
            int mouth = entity.talking() ? ShepherdFace.talkingMouth(entity.tickCount) : 0;
            ShepherdFaceRenderer.drawOnModel(pose, buffers, mood, mouth, motion.blink() || entity.moodBeat(), motion.eyeShift());
            if (entity.offline()) return;
            var flame = buffers.getBuffer(RenderType.entityCutoutNoCull(ShepherdModel.TEXTURE));
            getParentModel().renderFlame(pose, flame, ShepherdFace.flameHeight(entity.tickCount, entity.getId()));
        }
    }
}
