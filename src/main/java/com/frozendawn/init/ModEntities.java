package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ShadowFigureEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowFigureEntity>> SHADOW_FIGURE =
            ENTITIES.register("shadow_figure", () -> EntityType.Builder
                    .of(ShadowFigureEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("shadow_figure"));
}
