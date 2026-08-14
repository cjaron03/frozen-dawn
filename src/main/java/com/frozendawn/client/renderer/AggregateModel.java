package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.AggregateLineage;
import com.frozendawn.entity.AggregateEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class AggregateModel extends GeoModel<AggregateEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "geo/aggregate.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/aggregate.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "animations/aggregate.animation.json");

    @Override
    public ResourceLocation getModelResource(AggregateEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AggregateEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AggregateEntity entity) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(AggregateEntity entity, long instanceId,
                                    software.bernie.geckolib.animation.AnimationState<AggregateEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        setLineageVisible("lineage_rimebound", entity, AggregateLineage.RIMEBOUND);
        setLineageVisible("lineage_resonant", entity, AggregateLineage.RESONANT);
        setLineageVisible("lineage_remnant", entity, AggregateLineage.REMNANT);
        setLineageVisible("lineage_frostwrithe", entity, AggregateLineage.FROSTWRITHE);
        setLineageVisible("lineage_architect", entity, AggregateLineage.ARCHITECT);
        setLineageVisible("lineage_undone", entity, AggregateLineage.UNDONE);
        getBone("root").ifPresent(root -> {
            float scale = entity.hasDominantTrait(AggregateLineage.UNDONE) ? 1.08F : 1.0F;
            root.setScaleX(scale);
            root.setScaleY(scale);
            root.setScaleZ(scale);
        });
    }

    private void setLineageVisible(String bone, AggregateEntity entity,
                                   AggregateLineage lineage) {
        getBone(bone).ifPresent(value -> value.setHidden(!entity.traits().contains(lineage)));
        int index = entity.traits().indexOf(lineage);
        if (index >= 0 && entity.aggregateDeathTick() >= 18 + (2 - index) * 18) {
            getBone(bone).ifPresent(value -> value.setHidden(true));
        }
    }
}
