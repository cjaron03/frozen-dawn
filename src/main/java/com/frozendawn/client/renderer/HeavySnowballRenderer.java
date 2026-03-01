package com.frozendawn.client.renderer;

import com.frozendawn.entity.HeavySnowballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class HeavySnowballRenderer extends ThrownItemRenderer<HeavySnowballEntity> {

    public HeavySnowballRenderer(EntityRendererProvider.Context context) {
        super(context, 1.5f, true);
    }

    @Override
    public void render(HeavySnowballEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
