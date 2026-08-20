package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.AggregateFragmentEntity;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AggregateFragmentRenderer extends MobRenderer<AggregateFragmentEntity,
        SilverfishModel<AggregateFragmentEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/aggregate_fragment.png");

    public AggregateFragmentRenderer(EntityRendererProvider.Context context) {
        super(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.28F);
    }

    @Override
    public ResourceLocation getTextureLocation(AggregateFragmentEntity entity) {
        return TEXTURE;
    }
}
