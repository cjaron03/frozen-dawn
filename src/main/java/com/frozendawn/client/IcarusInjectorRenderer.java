package com.frozendawn.client;

import com.frozendawn.block.IcarusInjectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** The injector's nozzle iris on its top face: shut and still while idle, opening and turning while the matter stream
 * runs, with the nozzle glowing in the stream's colour. */
public final class IcarusInjectorRenderer implements BlockEntityRenderer<IcarusInjectorBlockEntity> {
    private static final int GRAPHITE = 0x2A2630, BLADE_EDGE = 0x5A4C3A, STREAM = 0xFFB040;
    private static final int BLADES = 6, SEGMENTS = 36;

    public IcarusInjectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(IcarusInjectorBlockEntity injector, float pt, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var core = injector.core();
        var anim = core == null ? null : IcarusCoreAnim.of(core);
        // Open while the stream is reaching up and not yet cut.
        float open = anim == null ? 0 : anim.streamFront(pt) * (1 - anim.streamTail(pt));
        float time = injector.getLevel() == null ? 0 : injector.getLevel().getGameTime() + pt;

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        // Local Z up, so the iris lies flat on the top face.
        poses.mulPose(Axis.XP.rotationDegrees(-90));
        poses.pushPose();
        poses.mulPose(Axis.ZP.rotationDegrees(open * 60 + time * 3 * open));
        float inner = 0.02f + 0.2f * open;
        IcarusDraw.annulus(buffers.getBuffer(IcarusDraw.body()), poses.last(), inner, 0.4f, 0.505f, SEGMENTS,
                j -> IcarusDraw.argb(j % (SEGMENTS / BLADES) == 0 ? BLADE_EDGE : GRAPHITE, 1));
        poses.popPose();
        if (open > 0.01f) {
            var glow = buffers.getBuffer(IcarusDraw.light());
            poses.translate(0, 0, 0.5);
            IcarusDraw.sphere(glow, poses.last(), false, inner * 0.9f, 6, 10, (lat, lon) -> IcarusDraw.argb(STREAM, 0.75 * open));
            IcarusDraw.sphere(glow, poses.last(), false, inner * 1.8f, 6, 10, (lat, lon) -> IcarusDraw.argb(STREAM, 0.2 * open));
        }
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(IcarusInjectorBlockEntity injector) { return new AABB(injector.getBlockPos()).inflate(1); }
}
