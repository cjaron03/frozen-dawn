package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.HeavySnowballEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<FrostbittenEntity>> FROSTBITTEN =
            ENTITIES.register("frostbitten", () -> EntityType.Builder
                    .of(FrostbittenEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("frostbitten"));

    public static final DeferredHolder<EntityType<?>, EntityType<HeavySnowballEntity>> HEAVY_SNOWBALL =
            ENTITIES.register("heavy_snowball", () -> EntityType.Builder
                    .<HeavySnowballEntity>of(HeavySnowballEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .build("heavy_snowball"));
}
