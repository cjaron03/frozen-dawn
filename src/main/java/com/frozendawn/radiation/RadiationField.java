package com.frozendawn.radiation;

import com.frozendawn.block.ReactorCasingBlock;
import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Dose rate at a player: sources within 16 blocks, inverse-square, attenuated block by block along one ray each.
 * Bounded: nearby chunks only, at most 48 rays per player per second, never loads chunks. */
public final class RadiationField {
    public static final int RANGE = 16, MAX_RAYS = 48;
    private record Source(Vec3 pos, double strength, BlockPos block) {}
    private RadiationField() {}

    /** Unshielded strength of one stack at one block. */
    public static double strength(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (s.is(ModItems.SPENT_FUEL_ROD.get())) return RadiationMath.SPENT_ROD * s.getCount();
        if (s.is(ModItems.SHIELDED_CASK.get())) return RadiationMath.SPENT_ROD * s.getOrDefault(ModDataComponents.CASK_RODS, 0) * RadiationMath.CASK_SHIELD;
        if (s.is(ModItems.RADIOACTIVE_ASH.get())) return RadiationMath.RADIOACTIVE_ASH * s.getCount();
        if (s.is(ModItems.FUEL_ROD.get())) return RadiationMath.FRESH_ROD * s.getCount();
        if (s.is(ModItems.URANIUM_PELLET.get())) return RadiationMath.PELLET * s.getCount();
        if (s.is(ModItems.RAW_URANIUM.get()) || s.is(ModItems.URANIUM_CONCENTRATE.get())) return RadiationMath.RAW_URANIUM * s.getCount();
        if (s.is(ModItems.CORIUM.get())) return RadiationMath.CORIUM * s.getCount();
        if (s.is(ModItems.REACTOR_DEBRIS.get())) return RadiationMath.DEBRIS * s.getCount();
        if (s.is(ModItems.DEEPSLATE_URANIUM_ORE.get())) return RadiationMath.ORE * s.getCount();
        return 0;
    }
    public static double strength(Container c) {
        double sum = 0; for (int i = 0; i < c.getContainerSize(); i++) sum += strength(c.getItem(i)); return sum;
    }
    public static double inventoryStrength(ServerPlayer p) {
        double sum = 0; var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) sum += strength(inv.getItem(i));
        return sum;
    }

    public static double rate(ServerPlayer player) {
        var level = player.serverLevel(); var eye = player.getEyePosition(); var at = BlockPos.containing(eye);
        double rate = RadiationMath.rate(inventoryStrength(player), 1, 1); // carried: no distance, no shielding
        var sources = new ArrayList<Source>();
        for (var other : level.players()) if (other != player && other.distanceToSqr(eye) <= RANGE * RANGE) {
            double s = inventoryStrength(other); if (s > 0) sources.add(new Source(other.position().add(0, 1, 0), s, null));
        }
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(RANGE))) {
            double s = strength(item.getItem()); if (s > 0) sources.add(new Source(item.position(), s, null));
        }
        for (int cx = (at.getX() - RANGE) >> 4; cx <= (at.getX() + RANGE) >> 4; cx++)
            for (int cz = (at.getZ() - RANGE) >> 4; cz <= (at.getZ() + RANGE) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be.getBlockPos().distSqr(at) > RANGE * RANGE) continue;
                    double s = be instanceof Container c ? strength(c) : 0;
                    if (be instanceof ReactorControllerBlockEntity rc) {
                        for (var stack : rc.items()) s += strength(stack);
                        var core = rc.shell().valid() ? rc.shell().core() : null;
                        double heat = rc.readout().heatInKw();
                        if (core != null && heat > 0) sources.add(new Source(Vec3.atCenterOf(core), RadiationMath.CORE_PER_KW * heat, core));
                    }
                    if (s > 0) sources.add(new Source(Vec3.atCenterOf(be.getBlockPos()), s, be.getBlockPos()));
                }
            }
        RadiationSourceData.get(level).near(at, RANGE, (pos, kind) -> {
            if (!level.isLoaded(pos)) return;
            double s = switch (kind) { case CORIUM -> RadiationMath.CORIUM; case MOLTEN_CORIUM -> RadiationMath.MOLTEN_CORIUM; case FALLOUT -> RadiationMath.FALLOUT;
                case FALLOUT_SEVERE -> RadiationMath.FALLOUT_SEVERE; case DEBRIS -> RadiationMath.DEBRIS; };
            sources.add(new Source(Vec3.atCenterOf(pos), s, pos));
        });
        sources.sort(Comparator.comparingDouble((Source s) -> -RadiationMath.rate(s.strength(), s.pos().distanceTo(eye), 1)));
        for (int i = 0; i < sources.size(); i++) {
            var s = sources.get(i); double d = s.pos().distanceTo(eye);
            rate += i < MAX_RAYS ? RadiationMath.rate(s.strength(), d, shielding(level, s.pos(), eye, s.block(), at))
                    : RadiationMath.rate(s.strength(), d, 0.5); // beyond the ray budget: assume one wall
        }
        return (rate + oreRate(player) + com.frozendawn.world.IcarusCraterHazards.radiation(player))
                * com.frozendawn.item.LeadLiningModuleItem.rateMultiplier(player);
    }

    /** Product of per-block factors between source and eye, excluding the source's own block and the eye cell. */
    public static double shielding(ServerLevel level, Vec3 from, Vec3 to, BlockPos source, BlockPos eye) {
        double d = from.distanceTo(to), shield = 1; int steps = (int) Math.ceil(d * 3);
        BlockPos last = null;
        for (int i = 1; i < steps && shield > 1e-4; i++) {
            var p = BlockPos.containing(from.lerp(to, i / (double) steps));
            if (p.equals(last) || p.equals(source) || p.equals(eye)) continue;
            last = p;
            if (!level.isLoaded(p)) continue;
            shield *= factor(level.getBlockState(p));
        }
        return shield;
    }
    public static double factor(BlockState s) {
        if (!s.getFluidState().isEmpty() && s.getFluidState().is(FluidTags.WATER)) return 0.5;
        if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.SNOW_BLOCK)) return 0.5;
        if (s.is(com.frozendawn.init.ModBlocks.LEAD_BLOCK.get())) return 0.1;
        if (s.getBlock() instanceof ReactorCasingBlock || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)
                || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_concrete")) return 0.4;
        if (s.isAir()) return 1;
        if (s.canOcclude()) return 0.5;
        return 0.9;
    }

    private static final Map<ServerPlayer, double[]> ORE = new WeakHashMap<>();
    /** Uranium ore near the player: sampled every five seconds within 6 blocks (Geiger clicks underground). */
    private static double oreRate(ServerPlayer player) {
        var cached = ORE.get(player); long now = player.serverLevel().getGameTime();
        if (cached != null && now - (long) cached[1] < 100) return cached[0];
        var level = player.serverLevel(); var at = player.blockPosition(); double r = 0;
        var ore = ModBlocks.DEEPSLATE_URANIUM_ORE.get();
        for (var p : BlockPos.betweenClosed(at.offset(-6, -6, -6), at.offset(6, 6, 6)))
            if (level.isLoaded(p) && level.getBlockState(p).is(ore)) r += RadiationMath.rate(RadiationMath.ORE, Math.sqrt(p.distSqr(at)), 0.5);
        ORE.put(player, new double[] {r, now});
        return r;
    }
}
