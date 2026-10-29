package com.frozendawn.client;

import com.frozendawn.block.IcarusStabilizerBlock;
import com.frozendawn.block.IcarusStabilizerBlockEntity;
import com.frozendawn.power.IcarusLook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** A stabilizer's three counter-rotating rings on its emitter face and the glow at their centre. The rings spin up with
 * the stabilizer's beam, faster as the core heats, and park square when the field drops; without a core they stand still. */
public final class IcarusStabilizerRenderer implements BlockEntityRenderer<IcarusStabilizerBlockEntity> {
    private static final int GRAPHITE = 0x2A2630, GOLD = 0xC89A3A, LIT = 0x8CF0FF;
    private static final int SEGMENTS = 24;

    public IcarusStabilizerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(IcarusStabilizerBlockEntity stabilizer, float pt, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var core = stabilizer.core();
        var anim = core == null ? null : IcarusCoreAnim.of(core);
        float beam = anim == null ? 0 : anim.beam(stabilizer.index(), pt), ring = anim == null ? 0 : anim.ring(pt);
        // Past running speed the rings blur into bright discs, so a spin-up reads from across the room.
        float whir = anim == null ? 0 : Math.clamp((anim.ringSpeed() - 6) / 14, 0, 1);
        var facing = stabilizer.getBlockState().getValue(IcarusStabilizerBlock.FACING);

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        // The model's emitter faces north (local -Z); turn it to face the core.
        poses.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        var body = buffers.getBuffer(IcarusDraw.body());
        for (int k = 0; k < 3; k++) {
            float outer = 0.46f - 0.12f * k, inner = outer - 0.07f, z = -0.52f - 0.08f * k;
            float angle = (k % 2 == 0 ? ring : -ring) * (1 + 0.35f * k) + 15 * k;
            int accent = IcarusLook.mix(GOLD, LIT, Math.max(beam, whir));
            poses.pushPose();
            poses.mulPose(Axis.ZP.rotationDegrees(angle));
            var pose = poses.last();
            // Every fourth segment is a bright notch, so the spin reads at a glance.
            IcarusDraw.annulus(body, pose, inner, outer, z, SEGMENTS, j -> IcarusDraw.argb(j % 4 == 0 ? accent : GRAPHITE, 1));
            IcarusDraw.band(body, pose, outer, z, z + 0.06f, SEGMENTS, j -> IcarusDraw.argb(j % 4 == 0 ? accent : GRAPHITE, 1));
            if (whir > 0.01f) {
                int blur = IcarusDraw.glow(LIT, whir * (0.35f + 0.15f * k));
                IcarusDraw.annulus(buffers.getBuffer(IcarusDraw.additive(IcarusDraw.WHITE)), pose, inner, outer, z - 0.01f, SEGMENTS, j -> blur);
            }
            body = buffers.getBuffer(IcarusDraw.body());
            poses.popPose();
        }
        if (beam > 0.01f) {
            var glow = buffers.getBuffer(IcarusDraw.light());
            poses.pushPose();
            poses.translate(0, 0, -0.55);
            IcarusDraw.sphere(glow, poses.last(), false, 0.12f + 0.1f * beam, 6, 10, (lat, lon) -> IcarusDraw.argb(LIT, 0.6 * beam));
            IcarusDraw.sphere(glow, poses.last(), false, 0.3f * beam, 6, 10, (lat, lon) -> IcarusDraw.argb(LIT, 0.18 * beam));
            poses.popPose();
        }
        poses.popPose();
    }

    @Override public AABB getRenderBoundingBox(IcarusStabilizerBlockEntity stabilizer) { return new AABB(stabilizer.getBlockPos()).inflate(1); }
}
