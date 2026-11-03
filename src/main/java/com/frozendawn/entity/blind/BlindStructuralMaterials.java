package com.frozendawn.entity.blind;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Authored low-impact-band surrogate. These are not measured elastic or airborne coefficients. */
public final class BlindStructuralMaterials {
    public record Profile(String name, double damping, double footCushion, double receiverCoupling) { }
    public static final double MIN_DAMPING = .07;
    private static final Profile DENSE = new Profile("dense", .07, 1, 1);
    private static final Profile STONE = new Profile("stone", .10, 1, 1);
    private static final Profile WOOD = new Profile("wood", .16, .85, .90);
    private static final Profile LOOSE = new Profile("loose", .38, .55, .65);
    private static final Profile WOOL = new Profile("wool", 1.10, .22, .35);
    private static final Profile CARPET = new Profile("carpet", 1.10, .18, .35);
    private BlindStructuralMaterials() { }

    public static Profile of(BlockState state) {
        if (state.is(BlockTags.WOOL_CARPETS)) return CARPET;
        if (state.is(BlockTags.WOOL)) return WOOL;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)) return WOOD;
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(BlockTags.DIRT) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) return LOOSE;
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.NETHERITE_BLOCK)) return DENSE;
        return STONE;
    }

    public static double interfaceTransfer(Profile a, Profile b, double coverage) {
        double joint = a.name.equals(b.name) ? 1 : .80;
        // Fraction of the adjoining cross sections, not absolute area: uniform slabs
        // must not acquire an extra loss at every artificial block boundary.
        return joint * Math.sqrt(Math.min(1, coverage));
    }
}
