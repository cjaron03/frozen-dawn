package com.frozendawn.client.renderer;

import com.frozendawn.block.HyperionControllerBlock;
import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.HyperionMultiblock;
import com.frozendawn.block.SuperconductingCellBlock;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.power.HyperionPhysics.Stage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** The puck behind the Controller's window, over three magnets. It rides from 1/16 to 7/16 of a block above them with
 * the charge, bobs at rest, wobbles in a warning and shudders in the final seconds. A quench drops it onto the magnets
 * in 4 ticks; it stays down while the battery is empty, warm or not formed. Model space faces north, as the block model. */
public final class HyperionControllerRenderer implements BlockEntityRenderer<HyperionControllerBlockEntity> {
    private static final float PX = 1 / 16f, MAGNET_TOP = 3, FALL_TICKS = 4;
    private static final float[] MAGNETS_X = {4.5f, 7.25f, 10f};
    private final BlockRenderDispatcher blocks;
    private final Map<HyperionControllerBlockEntity, Puck> pucks = new WeakHashMap<>();

    private static final class Puck {
        float height = -1, fallFrom, lastTime;
        long seenQuench;
        float fallStart = Float.NEGATIVE_INFINITY;
    }

    public HyperionControllerRenderer(BlockEntityRendererProvider.Context context) { blocks = context.getBlockRenderDispatcher(); }

    @Override public void render(HyperionControllerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null) return;
        var s = be.snapshot();
        var facing = be.getBlockState().getValue(HyperionControllerBlock.FACING);
        // The block itself is dark inside; light the puck from the open side of the window.
        int lit = LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing));
        float time = level.getGameTime() + partial;
        boolean down = s.problem() != HyperionMultiblock.Problem.NONE || s.stage() == Stage.WARM
                || s.stored() == 0 && s.lastQuenchTick() != Long.MIN_VALUE;
        float charge = s.capacity() <= 0 ? 0 : Math.min(1, s.stored() / (float) s.capacity());
        float target = down ? 0 : 1 + 6 * charge;

        var puck = pucks.computeIfAbsent(be, k -> new Puck());
        if (puck.height < 0) { puck.height = target; puck.seenQuench = s.lastQuenchTick(); puck.lastTime = time; }
        float dt = Math.max(0, Math.min(20, time - puck.lastTime)); puck.lastTime = time;
        if (s.lastQuenchTick() != puck.seenQuench) { puck.seenQuench = s.lastQuenchTick(); puck.fallStart = time; puck.fallFrom = puck.height; }
        float falling = (time - puck.fallStart) / FALL_TICKS;
        if (falling < 1) puck.height = puck.fallFrom * (1 - falling * falling);
        else puck.height += (target - puck.height) * Math.min(1, 0.06f * dt);

        float dx = 0, dy = 0, tilt = 0;
        if (!down) {
            dy = 0.2f * (float) Math.sin(time * 0.1);
            if (s.stage() == Stage.CHIRP || s.stage() == Stage.WARNING) { dx = 0.5f * (float) Math.sin(time * 0.9); dy += 0.5f * (float) Math.sin(time * 1.3); }
            if (s.stage() == Stage.FINAL) { dx = 1.5f * (float) Math.sin(time * 4.7); dy += 0.6f * (float) Math.sin(time * 6.1); tilt = 6 * (float) Math.sin(time * 3.1); }
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            default -> {}
        }
        pose.translate(-0.5, -0.5, -0.5);
        BlockState magnet = ModBlocks.HYPERION_FRAME.get().defaultBlockState();
        for (float x : MAGNETS_X) box(pose, buffers, magnet, x, 2, 1.5f, 1.5f, 1, 3, lit, overlay);
        BlockState ceramic = ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState().setValue(SuperconductingCellBlock.LOOK, SuperconductingCellBlock.Look.PLAIN);
        pose.pushPose();
        pose.translate(8 * PX + dx * PX, (MAGNET_TOP + puck.height + 0.75f + dy) * PX, 3 * PX);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        box(pose, buffers, ceramic, -4, -0.75f, -2, 8, 1.5f, 4, lit, overlay);
        pose.popPose();
        pose.popPose();
    }

    /** A block model squeezed into a box given in pixels. */
    private void box(PoseStack pose, MultiBufferSource buffers, BlockState state, float x, float y, float z, float w, float h, float d, int light, int overlay) {
        pose.pushPose();
        pose.translate(x * PX, y * PX, z * PX);
        pose.scale(w * PX, h * PX, d * PX);
        blocks.renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
