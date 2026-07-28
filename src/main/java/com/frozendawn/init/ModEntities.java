package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.HeavySnowballEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.entity.ReturnedEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<FrostmiteEntity>> FROSTMITE =
            ENTITIES.register("frostmite", () -> EntityType.Builder
                    .of(FrostmiteEntity::new, MobCategory.MONSTER)
                    .sized(0.45f, 0.3f)
                    .clientTrackingRange(8)
                    .build("frostmite"));

    public static final DeferredHolder<EntityType<?>, EntityType<HollowEntity>> HOLLOW =
            ENTITIES.register("hollow", () -> EntityType.Builder
                    .of(HollowEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("hollow"));

    public static final DeferredHolder<EntityType<?>, EntityType<ReturnedEntity>> RETURNED =
            ENTITIES.register("returned", () -> EntityType.Builder
                    .of(ReturnedEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("returned"));

    public static final DeferredHolder<EntityType<?>, EntityType<MimicEntity>> MIMIC =
            ENTITIES.register("mimic", () -> EntityType.Builder
                    .of(MimicEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(16)
                    .build("mimic"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArchitectEntity>> ARCHITECT =
            ENTITIES.register("architect", () -> EntityType.Builder
                    .of(ArchitectEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("architect"));

    public static final DeferredHolder<EntityType<?>, EntityType<MasterArchitectLightningEntity>>
            MASTER_ARCHITECT_LIGHTNING = ENTITIES.register(
                    "master_architect_lightning", () -> EntityType.Builder
                            .of(MasterArchitectLightningEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .noSave()
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("master_architect_lightning"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThaeIvenHeartEntity>>
            THAE_IVEN_HEART = ENTITIES.register(
                    "thae_iven_heart", () -> EntityType.Builder
                            .of(ThaeIvenHeartEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build("thae_iven_heart"));

    public static final DeferredHolder<EntityType<?>, EntityType<HeavySnowballEntity>> HEAVY_SNOWBALL =
            ENTITIES.register("heavy_snowball", () -> EntityType.Builder
                    .<HeavySnowballEntity>of(HeavySnowballEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .build("heavy_snowball"));

    public static final DeferredHolder<EntityType<?>, EntityType<RocketLaunchEntity>> ROCKET_LAUNCH =
            ENTITIES.register("rocket_launch", () -> EntityType.Builder
                    .<RocketLaunchEntity>of((type, level) -> new RocketLaunchEntity((EntityType<RocketLaunchEntity>) type, level), MobCategory.MISC)
                    .sized(3.8f, 7.8f)
                    .eyeHeight(4.7F)
                    .fireImmune()
                    .clientTrackingRange(20)
                    .build("rocket_launch"));
}
