package com.frozendawn.client;

import com.frozendawn.init.ModBlocks;
import net.minecraft.client.Camera;
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
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.isInitialized() ? camera.getPosition() : mc.player.getEyePosition();
        Vec3 look = new Vec3(camera.getLookVector()).normalize();
        Vec3 left = new Vec3(camera.getLeftVector()).normalize();
        Vec3 up = new Vec3(camera.getUpVector()).normalize();

        WindowView centerView = scanWindowRay(level, eye, look);
        if (centerView != null) {
            return centerView;
        }

        double[] offsets = {-0.34D, 0.0D, 0.34D};
        for (double yOffset : offsets) {
            for (double xOffset : offsets) {
                if (xOffset == 0.0D && yOffset == 0.0D) {
                    continue;
                }
                Vec3 sampleLook = look.add(left.scale(xOffset)).add(up.scale(yOffset)).normalize();
                WindowView view = scanWindowRay(level, eye, sampleLook);
                if (view != null) {
                    return view;
                }
            }
        }

        return null;
    }

    private static WindowView scanWindowRay(Level level, Vec3 eye, Vec3 look) {
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

            if (sawGlass) {
                if (isExteriorBeyondWindow(level, pos, state)) {
                    return new WindowView(sample, lastGlassCenter == null ? sample : lastGlassCenter);
                }
                if (canKeepScanningBeyondWindow(level, pos, state)) {
                    continue;
                }
                return null;
            }

            if (isOpenStormView(level, pos, state)) {
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

    private static boolean isExteriorBeyondWindow(Level level, BlockPos pos, BlockState state) {
        if (isOpenStormView(level, pos, state)) {
            return true;
        }
        if (level.canSeeSky(pos) || level.canSeeSky(pos.above())) {
            return true;
        }
        return hasNearbySky(level, pos);
    }

    private static boolean canKeepScanningBeyondWindow(Level level, BlockPos pos, BlockState state) {
        return state.isAir()
                || state.is(BlockTags.REPLACEABLE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(BlockTags.LEAVES)
                || !state.blocksMotion()
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean hasNearbySky(Level level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(cursor) && level.canSeeSky(cursor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isWindowGlass(BlockState state) {
        return state.is(ModBlocks.INSULATED_GLASS.get()) || GLASS_BLOCKS.contains(state.getBlock());
    }

    record WindowView(Vec3 outsidePoint, Vec3 glassCenter) {}
}
