package com.frozendawn.client;

import com.frozendawn.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

final class ClientStormVisibility {

    private static final double WINDOW_SCAN_RANGE = 18.0D;
    private static final double WINDOW_SCAN_STEP = 0.25D;

    private static final Set<Block> GLASS_BLOCKS = Set.of(
            Blocks.GLASS,
            Blocks.GLASS_PANE,
            Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS,
            Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS,
            Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS,
            Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS,
            Blocks.WHITE_STAINED_GLASS_PANE,
            Blocks.ORANGE_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS_PANE,
            Blocks.LIME_STAINED_GLASS_PANE,
            Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS_PANE,
            Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
            Blocks.CYAN_STAINED_GLASS_PANE,
            Blocks.PURPLE_STAINED_GLASS_PANE,
            Blocks.BLUE_STAINED_GLASS_PANE,
            Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS_PANE,
            Blocks.RED_STAINED_GLASS_PANE,
            Blocks.BLACK_STAINED_GLASS_PANE
    );

    private ClientStormVisibility() {}

    static boolean isStormExposed(Minecraft mc) {
        return mc.level != null && mc.player != null
                && mc.level.canSeeSky(mc.player.blockPosition().above());
    }

    static boolean isUndergroundOrCovered(Minecraft mc) {
        return mc.player != null
                && mc.level != null
                && (mc.player.blockPosition().getY() < 50 || !isStormExposed(mc));
    }

    static WindowView findWindowView(Minecraft mc) {
        if (mc.level == null || mc.player == null || isStormExposed(mc)) {
            return null;
        }

        Level level = mc.level;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F).normalize();
        boolean sawGlass = false;
        Vec3 lastGlassCenter = null;

        for (double distance = WINDOW_SCAN_STEP; distance <= WINDOW_SCAN_RANGE; distance += WINDOW_SCAN_STEP) {
            Vec3 sample = eye.add(look.scale(distance));
            BlockPos pos = BlockPos.containing(sample);
            if (!level.isLoaded(pos)) {
                return null;
            }

            BlockState state = level.getBlockState(pos);
            if (isWindowGlass(state)) {
                sawGlass = true;
                lastGlassCenter = Vec3.atCenterOf(pos);
                continue;
            }

            if (isOpenStormView(level, pos, state)) {
                if (sawGlass) {
                    return new WindowView(sample, lastGlassCenter == null ? sample : lastGlassCenter);
                }
                continue;
            }

            return null;
        }

        return null;
    }

    private static boolean isOpenStormView(Level level, BlockPos pos, BlockState state) {
        return (state.isAir()
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW))
                && level.canSeeSky(pos);
    }

    private static boolean isWindowGlass(BlockState state) {
        return state.is(ModBlocks.INSULATED_GLASS.get()) || GLASS_BLOCKS.contains(state.getBlock());
    }

    record WindowView(Vec3 outsidePoint, Vec3 glassCenter) {}
}
