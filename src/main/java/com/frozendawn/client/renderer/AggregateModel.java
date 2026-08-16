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
            float baseScale = entity.hasDominantTrait(AggregateLineage.UNDONE) ? 1.08F : 1.0F;
            float deathProgress = Math.min(1.0F, entity.aggregateDeathTick() / 100.0F);
            float collapse = deathProgress * deathProgress * (3.0F - 2.0F * deathProgress);
            root.setScaleX(baseScale * (1.0F - collapse * 0.68F));
            root.setScaleY(baseScale * (1.0F - collapse * 0.38F));
            root.setScaleZ(baseScale * (1.0F - collapse * 0.72F));
            if (entity.aggregateDeathTick() > 0 && entity.aggregateDeathTick() < 100) {
                float tremor = (float)Math.sin(entity.aggregateDeathTick() * 1.73F)
                        * (0.015F + deathProgress * 0.11F);
                root.setRotY(root.getRotY() + tremor);
                root.setRotZ(root.getRotZ() - tremor * 0.72F);
                root.setPosY(root.getPosY() - collapse * 2.8F);
            }
            root.setHidden(entity.aggregateDeathTick() >= 100);
        });
        float massScale = com.frozendawn.aggregate.AggregateDischargePolicy
                .massScaleForScars(entity.dischargeScars());
        float deathLoss = entity.aggregateDeathTick() <= 0 ? 1.0F
                : 1.0F - Math.min(0.68F, entity.aggregateDeathTick() / 100.0F * 0.68F);
        setMassScale("dragged_residue", massScale * deathLoss);
        setMassScale("shoulder_mass",
                (0.94F + (massScale - 0.79F) * 0.29F) * deathLoss);
        setMassScale("pelvis_mass",
                (0.92F + (massScale - 0.79F) * 0.38F) * deathLoss);
    }

    private void setLineageVisible(String bone, AggregateEntity entity,
                                   AggregateLineage lineage) {
        getBone(bone).ifPresent(value -> value.setHidden(!entity.traits().contains(lineage)));
        int index = entity.traits().indexOf(lineage);
        if (index >= 0 && entity.aggregateDeathTick() >= 18 + (2 - index) * 18) {
            getBone(bone).ifPresent(value -> value.setHidden(true));
        }
    }

    private void setMassScale(String bone, float scale) {
        getBone(bone).ifPresent(value -> {
            value.setScaleX(scale);
            value.setScaleY(scale);
            value.setScaleZ(scale);
        });
    }
}
