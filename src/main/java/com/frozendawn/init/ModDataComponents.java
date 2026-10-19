package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HEADLAMP_ENABLED = DATA_COMPONENTS.register("headlamp_enabled", () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEADLAMP_CREDIT = DATA_COMPONENTS.register("headlamp_credit", () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0,1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LANTERN_ENABLED = DATA_COMPONENTS.register("lantern_enabled", () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    // A prepaid half-joule travels with the lantern across switches, cell swaps and pickup.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LANTERN_CREDIT = DATA_COMPONENTS.register("lantern_credit", () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0,1)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> PIXIE_WATER = DATA_COMPONENTS.register("pixie_water", () -> DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    /** A printed Plumb Line Survey Sheet's pulse; synced so the holder's client can draw it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.frozendawn.gpr.PlumbLineSurvey>> PLUMB_LINE_SURVEY =
            DATA_COMPONENTS.register("plumb_line_survey", () -> DataComponentType.<com.frozendawn.gpr.PlumbLineSurvey>builder()
                    .persistent(com.frozendawn.gpr.PlumbLineSurvey.CODEC).networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(com.frozendawn.gpr.PlumbLineSurvey.CODEC)).build());

    /** Seconds a Frostcap stack has spent above 0C toward its next spoil step. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FROSTCAP_WARMTH =
            DATA_COMPONENTS.register("frostcap_warmth", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 100_000)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CASK_RODS =
            DATA_COMPONENTS.register("cask_rods", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 4)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CELL_JOULES =
            DATA_COMPONENTS.register("cell_joules", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 500_000)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.GlobalPos>> CONDUIT_ANCHOR =
            DATA_COMPONENTS.register("conduit_anchor", () -> DataComponentType.<net.minecraft.core.GlobalPos>builder()
                    .persistent(net.minecraft.core.GlobalPos.CODEC).networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EMERGENCY_EVA_SERVICE =
            DATA_COMPONENTS.register("emergency_eva_service", () ->
                    DataComponentType.<Integer>builder().persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> EMERGENCY_EVA_ISSUE =
            DATA_COMPONENTS.register("emergency_eva_issue", () ->
                    DataComponentType.<java.util.UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FROST_TICKS =
            DATA_COMPONENTS.register("frost_ticks", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RATION_WARMER_CHARGES =
            DATA_COMPONENTS.register("ration_warmer_charges", () ->
                    DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 4))
                            .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> FOOD_WARM_UNTIL =
            DATA_COMPONENTS.register("food_warm_until", () ->
                    DataComponentType.<Long>builder().persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SUBLIMATION_TICKS =
            DATA_COMPONENTS.register("sublimation_ticks", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> RESERVOIR_OXYGEN = DATA_COMPONENTS.register("reservoir_oxygen", () -> DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> O2_LEVEL =
            DATA_COMPONENTS.register("o2_level", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WARMTH_REMAINING =
            DATA_COMPONENTS.register("warmth_remaining", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MITEAWAY_BURN_TICKS =
            DATA_COMPONENTS.register("miteaway_burn_ticks", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CALORIC_RESISTANCE =
            DATA_COMPONENTS.register("caloric_resistance", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LAST_WITNESS_READY_TIME =
            DATA_COMPONENTS.register("last_witness_ready_time", () ->
                    DataComponentType.<Long>builder()
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG)
                            .build());

    /** §9.4b frozen Scribe notes; written once at the Scribe's death. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.frozendawn.item.ScribeRecordContents>> SCRIBE_RECORD =
            DATA_COMPONENTS.register("scribe_record", () ->
                    DataComponentType.<com.frozendawn.item.ScribeRecordContents>builder()
                            .persistent(com.frozendawn.item.ScribeRecordContents.CODEC)
                            .networkSynchronized(com.frozendawn.item.ScribeRecordContents.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEARTHROT_COLONIZATION =
            DATA_COMPONENTS.register("hearthrot_colonization", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());
}
