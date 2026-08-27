package com.frozendawn.aggregate;

import com.frozendawn.entity.ArchivistEntity;
import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure tuning and deterministic classification for convergence pressure. */
public final class AggregatePressurePolicy {
    public static final double RESIDUE_THRESHOLD = 60.0D;
    public static final double DEPOSIT_THRESHOLD = 140.0D;
    public static final double OSSUARY_THRESHOLD = 240.0D;
    public static final double GESTATION_THRESHOLD = 340.0D;
    public static final double AWAKENING_THRESHOLD = 400.0D;

    private AggregatePressurePolicy() {
    }

    public static Contribution classify(LivingEntity entity) {
        if (entity instanceof AggregateEntity || entity instanceof AggregateFragmentEntity) {
            return Contribution.NONE;
        }
        if (entity instanceof ArchivistEntity) {
            return new Contribution(25.0D, AggregateLineage.UNDONE);
        }
        if (entity instanceof UndoneArchitectEntity) {
            return new Contribution(15.0D, AggregateLineage.ARCHITECT);
        }
        if (entity instanceof UndoneEntity) {
            return new Contribution(6.0D, AggregateLineage.UNDONE);
        }
        if (entity instanceof FrostwritheEntity) {
            return new Contribution(5.0D, AggregateLineage.FROSTWRITHE);
        }
        if (entity instanceof RemnantEntity) {
            return new Contribution(5.0D, AggregateLineage.REMNANT);
        }
        if (entity instanceof RimeboundEntity) {
            return new Contribution(4.0D, AggregateLineage.RIMEBOUND);
        }
        if (entity instanceof ResonantEntity) {
            return new Contribution(4.0D, AggregateLineage.RESONANT);
        }
        if (entity instanceof MimicEntity) {
            return new Contribution(2.0D, AggregateLineage.NORMAL);
        }
        if (entity instanceof FrostmiteEntity) {
            return new Contribution(0.25D, AggregateLineage.FROSTWRITHE);
        }
        if (entity instanceof ArchitectEntity) {
            return new Contribution(1.0D, AggregateLineage.ARCHITECT);
        }
        if (entity instanceof FrostbittenEntity || entity instanceof HollowEntity) {
            return new Contribution(1.0D, AggregateLineage.NORMAL);
        }
        if (entity instanceof Enemy && "frozendawn".equals(BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).getNamespace())) {
            return new Contribution(1.0D, AggregateLineage.NORMAL);
        }
        return Contribution.NONE;
    }

    public static AggregateStage stageFor(double pressure) {
        if (pressure >= AWAKENING_THRESHOLD) return AggregateStage.AWAKENING_ELIGIBLE;
        if (pressure >= GESTATION_THRESHOLD) return AggregateStage.GESTATION;
        if (pressure >= OSSUARY_THRESHOLD) return AggregateStage.OSSUARY;
        if (pressure >= DEPOSIT_THRESHOLD) return AggregateStage.DEPOSIT;
        if (pressure >= RESIDUE_THRESHOLD) return AggregateStage.RESIDUE;
        return AggregateStage.DORMANT;
    }

    public static AggregateStage configuredStageFor(double pressure) {
        if (pressure >= FrozenDawnConfig.AGGREGATE_AWAKENING_PRESSURE.get()) {
            return AggregateStage.AWAKENING_ELIGIBLE;
        }
        if (pressure >= FrozenDawnConfig.AGGREGATE_GESTATION_PRESSURE.get()) {
            return AggregateStage.GESTATION;
        }
        if (pressure >= FrozenDawnConfig.AGGREGATE_OSSUARY_PRESSURE.get()) {
            return AggregateStage.OSSUARY;
        }
        if (pressure >= FrozenDawnConfig.AGGREGATE_DEPOSIT_PRESSURE.get()) {
            return AggregateStage.DEPOSIT;
        }
        if (pressure >= FrozenDawnConfig.AGGREGATE_RESIDUE_PRESSURE.get()) {
            return AggregateStage.RESIDUE;
        }
        return AggregateStage.DORMANT;
    }

    public static AggregateStage nextGrowthStage(
            AggregateStage current, AggregateStage target,
            long currentDay, long lastAdvanceDay) {
        if (current == null || target == null
                || target.ordinal() <= current.ordinal()
                || currentDay <= lastAdvanceDay
                || current.ordinal() >= AggregateStage.AWAKENING_ELIGIBLE.ordinal()) {
            return current;
        }
        return AggregateStage.values()[current.ordinal() + 1];
    }

    public static List<AggregateLineage> lockTraits(
            Map<AggregateLineage, Double> pressures, long seed) {
        List<AggregateLineage> ordered = new ArrayList<>(List.of(AggregateLineage.values()));
        ordered.sort(Comparator
                .<AggregateLineage>comparingDouble(lineage ->
                        pressures.getOrDefault(lineage, 0.0D)).reversed()
                .thenComparingLong(lineage -> tieBreak(seed, lineage)));
        ordered.removeIf(lineage -> pressures.getOrDefault(lineage, 0.0D) <= 0.0D);
        return List.copyOf(ordered.subList(0, Math.min(3, ordered.size())));
    }

    public static AggregateLineage dominant(
            Map<AggregateLineage, Double> pressures) {
        double total = pressures.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0D) return null;
        AggregateLineage best = null;
        double bestValue = 0.0D;
        for (AggregateLineage lineage : AggregateLineage.values()) {
            double value = pressures.getOrDefault(lineage, 0.0D);
            if (value > bestValue) {
                best = lineage;
                bestValue = value;
            }
        }
        return bestValue / total > 0.5D ? best : null;
    }

    public static EnumMap<AggregateLineage, Double> emptyLineages() {
        EnumMap<AggregateLineage, Double> values = new EnumMap<>(AggregateLineage.class);
        for (AggregateLineage lineage : AggregateLineage.values()) values.put(lineage, 0.0D);
        return values;
    }

    private static long tieBreak(long seed, AggregateLineage lineage) {
        long value = seed ^ (0x9E3779B97F4A7C15L * (lineage.ordinal() + 1L));
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return value;
    }

    public record Contribution(double pressure, AggregateLineage lineage) {
        public static final Contribution NONE = new Contribution(0.0D, null);

        public boolean counts() {
            return pressure > 0.0D && lineage != null;
        }
    }
}
