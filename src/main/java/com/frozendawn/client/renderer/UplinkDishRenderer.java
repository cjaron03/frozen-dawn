package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.UplinkDishBlock;
import com.frozendawn.block.UplinkDishBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the Uplink reflector at twice block scale on top of the mast and animates it from the dish's MODE:
 * a slow back-and-forth sweep while listening, a steady spin while transmitting, a faster one on Boost,
 * easing back to its facing at rest. Purely visual; the console owns every gameplay check.
 */
public final class UplinkDishRenderer implements BlockEntityRenderer<UplinkDishBlockEntity> {
    public static final ModelResourceLocation REFLECTOR =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/uplink_dish_reflector"));
    /** Degrees per tick: 45 and 90 degrees per second. */
    private static final float SPIN = 2.25f, BOOST_SPIN = 4.5f;
    /** Listening sweep: +/- 70 degrees over a 30-second period. */
    private static final float SWEEP = 70f, SWEEP_TICKS = 600f;
    private static final float SCALE = 2f, PIVOT_Y = 1.375f;
    private final BlockRenderDispatcher blocks;

    public UplinkDishRenderer(BlockEntityRendererProvider.Context context) { blocks = context.getBlockRenderDispatcher(); }

    @Override public void render(UplinkDishBlockEntity dish, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = dish.getLevel();
        if (level == null) return;
        var state = dish.getBlockState();
        animate(dish, state.getValue(UplinkDishBlock.MODE), level.getGameTime() + partial);
        float facing = state.getValue(UplinkDishBlock.FACING).toYRot();
        pose.pushPose();
        pose.translate(0.5, PIVOT_Y, 0.5);
        // Blockstate y rotations turn clockwise seen from above; the pose stack turns counter-clockwise.
        pose.mulPose(Axis.YP.rotationDegrees(-(facing + 180f) - dish.yaw));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5, -0.5, -0.5);
        var model = Minecraft.getInstance().getModelManager().getModel(REFLECTOR);
        int lit = LevelRenderer.getLightColor(level, dish.getBlockPos().above());
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.solidBlockSheet()), state, model,
                1f, 1f, 1f, lit, overlay, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** Integrates the client yaw; tolerant of skipped frames and of the block entity being newly loaded. */
    static void animate(UplinkDishBlockEntity dish, int mode, double now) {
        float dt = Double.isNaN(dish.lastTime) ? 0 : (float) Mth.clamp(now - dish.lastTime, 0, 10);
        dish.lastTime = now;
        if (mode >= 2) {
            float target = mode == 3 ? BOOST_SPIN : SPIN;
            dish.speed += (target - dish.speed) * Math.min(1f, 0.04f * dt);
            dish.yaw = Mth.wrapDegrees(dish.yaw + dish.speed * dt);
            return;
        }
        // Spin down first, then settle onto the sweep (listening) or the rest heading (idle).
        dish.speed += (0 - dish.speed) * Math.min(1f, 0.05f * dt);
        if (Math.abs(dish.speed) > 0.3f) { dish.yaw = Mth.wrapDegrees(dish.yaw + dish.speed * dt); return; }
        float goal = mode == 1 ? SWEEP * Mth.sin((float) (now * 2 * Math.PI / SWEEP_TICKS)) : 0f;
        float delta = Mth.wrapDegrees(goal - dish.yaw);
        dish.yaw = Mth.wrapDegrees(dish.yaw + Mth.clamp(delta * 0.08f * dt, -1.5f * dt, 1.5f * dt));
    }

    /** The reflector is drawn about two blocks wide above the mast. */
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(UplinkDishBlockEntity dish) {
        return new net.minecraft.world.phys.AABB(dish.getBlockPos()).inflate(1.25, 0, 1.25).expandTowards(0, 1.75, 0);
    }
    @Override public int getViewDistance() { return 96; }
}
