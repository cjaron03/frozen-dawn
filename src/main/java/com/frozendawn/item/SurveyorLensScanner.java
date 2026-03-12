package com.frozendawn.item;

import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.AcheronForgeRegistry;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TransponderRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SurveyorLensScanner {

    private static final Comparator<HeatSignature> SIGNATURE_ORDER =
            Comparator.comparingInt((HeatSignature signature) -> signature.sourceType().priority())
                    .thenComparingInt(HeatSignature::distanceBlocks);

    private SurveyorLensScanner() {}

    public static LensProfile activeProfile(ItemStack stack) {
        if (stack.getItem() instanceof SurveyorLensItem lensItem) {
            return lensItem.lensProfile();
        }
        return null;
    }

    public static LensProfile heldProfile(ItemStack mainHand, ItemStack offHand) {
        LensProfile main = activeProfile(mainHand);
        LensProfile off = activeProfile(offHand);

        if (main == LensProfile.CALIBRATED || off == LensProfile.CALIBRATED) {
            return LensProfile.CALIBRATED;
        }
        if (main == LensProfile.STANDARD || off == LensProfile.STANDARD) {
            return LensProfile.STANDARD;
        }
        return null;
    }

    public static LensProfile passiveProfile(ItemStack mainHand, ItemStack offHand, ItemStack headArmor) {
        if (headArmor.is(ModItems.ORSA_THERMAL_VISOR.get())) {
            return LensProfile.VISOR;
        }
        return heldProfile(mainHand, offHand);
    }

    public static List<HeatSignature> collectHeatSignatures(Level level, Vec3 origin, BlockPos playerPos, LensProfile profile) {
        List<HeatSignature> signatures = new ArrayList<>();

        for (BlockPos pos : HeaterRegistry.getHeaters(level)) {
            addSignature(signatures, origin, pos, HeatSourceType.THERMAL_HEATER, profile.infrastructureRangeSqr());
        }
        for (BlockPos pos : GeothermalCoreRegistry.getCores(level)) {
            addSignature(signatures, origin, pos, HeatSourceType.GEOTHERMAL_CORE, profile.infrastructureRangeSqr());
        }
        for (BlockPos pos : TransponderRegistry.getTransponders(level)) {
            addSignature(signatures, origin, pos, HeatSourceType.TRANSPONDER, profile.infrastructureRangeSqr());
        }
        for (BlockPos pos : AcheronForgeRegistry.getForges(level)) {
            addSignature(signatures, origin, pos, HeatSourceType.ACHERON_FORGE, profile.infrastructureRangeSqr());
        }

        if (profile.detectsAmbientHeat()) {
            collectAmbientHeat(level, origin, playerPos, profile, signatures);
        }

        signatures.sort(SIGNATURE_ORDER);
        return signatures;
    }

    private static void collectAmbientHeat(Level level, Vec3 origin, BlockPos playerPos, LensProfile profile, List<HeatSignature> signatures) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int dy = -profile.ambientVerticalRange(); dy <= profile.ambientVerticalRange(); dy++) {
            int y = playerPos.getY() + dy;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }

            for (int dz = -profile.ambientHorizontalRange(); dz <= profile.ambientHorizontalRange(); dz++) {
                for (int dx = -profile.ambientHorizontalRange(); dx <= profile.ambientHorizontalRange(); dx++) {
                    mutablePos.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!level.hasChunkAt(mutablePos)) {
                        continue;
                    }

                    HeatSourceType sourceType = HeatSourceType.fromAmbientState(level.getBlockState(mutablePos));
                    if (sourceType == null) {
                        continue;
                    }

                    addSignature(signatures, origin, mutablePos, sourceType, profile.ambientRangeSqr());
                }
            }
        }
    }

    private static void addSignature(List<HeatSignature> signatures, Vec3 origin, BlockPos pos, HeatSourceType sourceType, double maxDistanceSqr) {
        double distanceSqr = origin.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distanceSqr > maxDistanceSqr) {
            return;
        }

        HeatSignature newSignature = new HeatSignature(
                pos.immutable(),
                sourceType,
                distanceSqr,
                Mth.floor(Math.sqrt(distanceSqr)),
                describeDirection(origin, pos)
        );

        int clusterRadius = sourceType.clusterRadius();
        if (clusterRadius <= 0) {
            signatures.add(newSignature);
            return;
        }

        int clusterRadiusSqr = clusterRadius * clusterRadius;
        for (int i = 0; i < signatures.size(); i++) {
            HeatSignature existing = signatures.get(i);
            if (existing.sourceType() != sourceType) {
                continue;
            }

            if (existing.pos().distSqr(pos) <= clusterRadiusSqr) {
                if (newSignature.distanceSqr() < existing.distanceSqr()) {
                    signatures.set(i, newSignature);
                }
                return;
            }
        }

        signatures.add(newSignature);
    }

    private static Component describeDirection(Vec3 origin, BlockPos pos) {
        double dx = pos.getX() + 0.5D - origin.x;
        double dy = pos.getY() + 0.5D - origin.y;
        double dz = pos.getZ() + 0.5D - origin.z;

        String horizontal = horizontalDirection(dx, dz);
        String vertical = "";
        if (dy >= 6.0D) {
            vertical = ", above";
        } else if (dy <= -6.0D) {
            vertical = ", below";
        }

        return Component.literal(horizontal + vertical);
    }

    private static String horizontalDirection(double dx, double dz) {
        String northSouth = dz < -2.0D ? "north" : dz > 2.0D ? "south" : "";
        String eastWest = dx > 2.0D ? "east" : dx < -2.0D ? "west" : "";

        if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
            return northSouth + eastWest;
        }
        if (!northSouth.isEmpty()) {
            return northSouth;
        }
        if (!eastWest.isEmpty()) {
            return eastWest;
        }
        return "nearby";
    }

    public enum LensProfile {
        STANDARD(48.0D, 0, 0, 0.0D, 8,
                "tooltip.frozendawn.surveyor_lens",
                "tooltip.frozendawn.surveyor_lens.use"),
        CALIBRATED(80.0D, 20, 10, 20.0D, 12,
                "tooltip.frozendawn.calibrated_surveyor_lens",
                "tooltip.frozendawn.calibrated_surveyor_lens.use"),
        VISOR(96.0D, 24, 12, 24.0D, 14,
                "tooltip.frozendawn.orsa_thermal_visor",
                "tooltip.frozendawn.orsa_thermal_visor.use");

        private final double infrastructureRange;
        private final int ambientHorizontalRange;
        private final int ambientVerticalRange;
        private final double ambientRange;
        private final int maxMarkers;
        private final String tooltipKey;
        private final String tooltipUseKey;

        LensProfile(double infrastructureRange, int ambientHorizontalRange, int ambientVerticalRange,
                    double ambientRange, int maxMarkers, String tooltipKey, String tooltipUseKey) {
            this.infrastructureRange = infrastructureRange;
            this.ambientHorizontalRange = ambientHorizontalRange;
            this.ambientVerticalRange = ambientVerticalRange;
            this.ambientRange = ambientRange;
            this.maxMarkers = maxMarkers;
            this.tooltipKey = tooltipKey;
            this.tooltipUseKey = tooltipUseKey;
        }

        public double infrastructureRangeSqr() {
            return infrastructureRange * infrastructureRange;
        }

        public boolean detectsAmbientHeat() {
            return ambientRange > 0.0D;
        }

        public int ambientHorizontalRange() {
            return ambientHorizontalRange;
        }

        public int ambientVerticalRange() {
            return ambientVerticalRange;
        }

        public double ambientRangeSqr() {
            return ambientRange * ambientRange;
        }

        public int maxMarkers() {
            return maxMarkers;
        }

        public String tooltipKey() {
            return tooltipKey;
        }

        public String tooltipUseKey() {
            return tooltipUseKey;
        }
    }

    public enum HeatSourceType {
        GEOTHERMAL_CORE("block.frozendawn.geothermal_core", ParticleTypes.SOUL_FIRE_FLAME, 0, 0),
        TRANSPONDER("block.frozendawn.transponder", ParticleTypes.END_ROD, 1, 0),
        ACHERON_FORGE("block.frozendawn.acheron_forge", ParticleTypes.ENCHANT, 2, 0),
        THERMAL_HEATER("block.frozendawn.thermal_heater", ParticleTypes.FLAME, 3, 0),
        ACHERONITE_BLOCK("block.frozendawn.acheronite_block", ParticleTypes.SCULK_SOUL, 4, 4),
        LAVA("block.minecraft.lava", ParticleTypes.LAVA, 5, 5),
        SOUL_FIRE("block.minecraft.soul_fire", ParticleTypes.SOUL_FIRE_FLAME, 6, 4),
        FIRE("block.minecraft.fire", ParticleTypes.FLAME, 7, 4),
        SOUL_CAMPFIRE("block.minecraft.soul_campfire", ParticleTypes.SOUL_FIRE_FLAME, 8, 4),
        CAMPFIRE("block.minecraft.campfire", ParticleTypes.CAMPFIRE_COSY_SMOKE, 9, 4),
        SOUL_LANTERN("block.minecraft.soul_lantern", ParticleTypes.SOUL_FIRE_FLAME, 10, 4),
        LANTERN("block.minecraft.lantern", ParticleTypes.FLAME, 11, 4),
        SOUL_TORCH("block.minecraft.soul_torch", ParticleTypes.SOUL_FIRE_FLAME, 12, 4),
        TORCH("block.minecraft.torch", ParticleTypes.FLAME, 13, 4);

        private final String translationKey;
        private final ParticleOptions markerParticle;
        private final int priority;
        private final int clusterRadius;

        HeatSourceType(String translationKey, ParticleOptions markerParticle, int priority, int clusterRadius) {
            this.translationKey = translationKey;
            this.markerParticle = markerParticle;
            this.priority = priority;
            this.clusterRadius = clusterRadius;
        }

        public Component displayName() {
            return Component.translatable(translationKey);
        }

        public ParticleOptions markerParticle() {
            return markerParticle;
        }

        public int priority() {
            return priority;
        }

        public int clusterRadius() {
            return clusterRadius;
        }

        public static HeatSourceType fromAmbientState(BlockState state) {
            if (state.is(ModBlocks.ACHERONITE_BLOCK.get())) {
                return ACHERONITE_BLOCK;
            }
            if (state.is(Blocks.LAVA)) {
                return LAVA;
            }
            if (state.is(Blocks.SOUL_FIRE)) {
                return SOUL_FIRE;
            }
            if (state.is(Blocks.FIRE)) {
                return FIRE;
            }
            if (state.is(Blocks.SOUL_CAMPFIRE) && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
                return SOUL_CAMPFIRE;
            }
            if (state.is(Blocks.CAMPFIRE) && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
                return CAMPFIRE;
            }
            if (state.is(Blocks.SOUL_LANTERN)) {
                return SOUL_LANTERN;
            }
            if (state.is(Blocks.LANTERN)) {
                return LANTERN;
            }
            if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                return SOUL_TORCH;
            }
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) {
                return TORCH;
            }
            if (state.getBlock() instanceof ThermalHeaterBlock && state.hasProperty(ThermalHeaterBlock.LIT) && state.getValue(ThermalHeaterBlock.LIT)) {
                return THERMAL_HEATER;
            }
            return null;
        }
    }

    public record HeatSignature(BlockPos pos, HeatSourceType sourceType, double distanceSqr,
                                int distanceBlocks, Component direction) {
        public Component displayName() {
            return sourceType.displayName();
        }
    }
}
