package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.RimeLanceEntity;
import com.frozendawn.entity.HeavySnowballEntity;
import com.frozendawn.entity.HeartSuccessorEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.entity.BloomSporeCorpseEntity;
import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.entity.ArchivistRelicEntity;
import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<AggregateEntity>> AGGREGATE =
            ENTITIES.register("aggregate", () -> EntityType.Builder
                    .of(AggregateEntity::new, MobCategory.MONSTER)
                    .sized(3.2F, 3.15F)
                    .fireImmune()
                    .clientTrackingRange(20)
                    .updateInterval(1)
                    .build("aggregate"));

    public static final DeferredHolder<EntityType<?>, EntityType<AggregateFragmentEntity>>
            AGGREGATE_FRAGMENT = ENTITIES.register("aggregate_fragment", () -> EntityType.Builder
                    .of(AggregateFragmentEntity::new, MobCategory.MONSTER)
                    .sized(0.72F, 0.48F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("aggregate_fragment"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<RimeboundEntity>> RIMEBOUND =
            ENTITIES.register("rimebound", () -> EntityType.Builder
                    .of(RimeboundEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("rimebound"));

    public static final DeferredHolder<EntityType<?>, EntityType<RimeLanceEntity>> RIME_LANCE =
            ENTITIES.register("rime_lance", () -> EntityType.Builder
                    .<RimeLanceEntity>of(RimeLanceEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("rime_lance"));

    public static final DeferredHolder<EntityType<?>, EntityType<ResonantEntity>> RESONANT =
            ENTITIES.register("resonant", () -> EntityType.Builder
                    .of(ResonantEntity::new, MobCategory.MONSTER)
                    .sized(0.68F, 2.28F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("resonant"));

    public static final DeferredHolder<EntityType<?>, EntityType<RemnantEntity>> REMNANT =
            ENTITIES.register("remnant", () -> EntityType.Builder
                    .of(RemnantEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(14)
                    .updateInterval(2)
                    .build("remnant"));

    public static final DeferredHolder<EntityType<?>, EntityType<FrostmiteEntity>> FROSTMITE =
            ENTITIES.register("frostmite", () -> EntityType.Builder
                    .of(FrostmiteEntity::new, MobCategory.MONSTER)
                    .sized(0.45f, 0.3f)
                    .clientTrackingRange(8)
                    .build("frostmite"));

    public static final DeferredHolder<EntityType<?>, EntityType<FrostwritheEntity>> FROSTWRITHE =
            ENTITIES.register("frostwrithe", () -> EntityType.Builder
                    .of(FrostwritheEntity::new, MobCategory.MONSTER)
                    .sized(2.25F, 0.72F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("frostwrithe"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<UndoneEntity>> UNDONE =
            ENTITIES.register("undone", () -> EntityType.Builder
                    .of(UndoneEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("undone"));

    public static final DeferredHolder<EntityType<?>, EntityType<UndoneEntity>> BLOOMBOUND_UNDONE =
            ENTITIES.register("bloombound_undone", () -> EntityType.Builder
                    .of(UndoneEntity::new, MobCategory.MONSTER)
                    .sized(0.68F, 2.05F)
                    .clientTrackingRange(14)
                    .build("bloombound_undone"));

    public static final DeferredHolder<EntityType<?>, EntityType<UndoneArchitectEntity>>
            UNDONE_ARCHITECT = ENTITIES.register("undone_architect", () -> EntityType.Builder
                    .of(UndoneArchitectEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("undone_architect"));

    public static final DeferredHolder<EntityType<?>, EntityType<BloomSporeEntity>>
            BLOOM_SPORE = ENTITIES.register("bloom_spore", () -> EntityType.Builder
                    .of(BloomSporeEntity::new, MobCategory.MISC)
                    .sized(0.68F, 2.05F)
                    .clientTrackingRange(14)
                    .updateInterval(2)
                    .build("bloom_spore"));

    public static final DeferredHolder<EntityType<?>, EntityType<BloomSporeCorpseEntity>>
            BLOOM_SPORE_CORPSE = ENTITIES.register("bloom_spore_corpse",
                    () -> EntityType.Builder
                            .of(BloomSporeCorpseEntity::new, MobCategory.MISC)
                            .sized(1.65F, 0.55F)
                            .clientTrackingRange(14)
                            .updateInterval(10)
                            .build("bloom_spore_corpse"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArchivistEntity>>
            ARCHIVIST = ENTITIES.register("archivist", () -> EntityType.Builder
                    .of(ArchivistEntity::new, MobCategory.MISC)
                    .sized(0.68F, 1.95F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("archivist"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArchivistRelicEntity>>
            ARCHIVIST_RELIC = ENTITIES.register("archivist_relic", () -> EntityType.Builder
                    .of(ArchivistRelicEntity::new, MobCategory.MISC)
                    .sized(0.55F, 0.24F)
                    .clientTrackingRange(12)
                    .updateInterval(10)
                    .build("archivist_relic"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<HeartSuccessorEntity>>
            HEART_SUCCESSOR = ENTITIES.register(
                    "heart_successor", () -> EntityType.Builder
                            .of(HeartSuccessorEntity::new, MobCategory.MISC)
                            .sized(0.9F, 2.9F)
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build("heart_successor"));

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
