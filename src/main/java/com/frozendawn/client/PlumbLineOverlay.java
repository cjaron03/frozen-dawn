package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gpr.PlumbLinePolicy;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import com.frozendawn.network.PlumbLineReturnsPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Plumb Line returns, drawn through terrain for 30 s: ore veins as soft amber blobs labelled by exact type (white and
 * tight once triangulated), voids as faint outlines, metal as bright returns, ORSA structures as cyan markers.
 * Fades in briefly and out over the last five seconds. Nothing here touches the world.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PlumbLineOverlay {
    private record Shown(Return r, String dimension, long received) { }
    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final int MAX_SHOWN = 256, MAX_LABELS = 14, LABEL_RANGE = 96;
    private static MultiBufferSource.BufferSource buffers;
    private PlumbLineOverlay() { }

    public static void receive(PlumbLineReturnsPayload payload) {
        long now = System.currentTimeMillis();
        for (var r : payload.returns()) SHOWN.add(new Shown(r, payload.dimension(), now));
        while (SHOWN.size() > MAX_SHOWN) SHOWN.removeFirst();
    }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SHOWN.clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOWN.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis(), life = PlumbLinePolicy.OVERLAY_TICKS * 50L;
        SHOWN.removeIf(s -> now - s.received >= life);
        String dimension = mc.level.dimension().location().toString();
        Vec3 camera = event.getCamera().getPosition();
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
        PoseStack poses = event.getPoseStack();
        var labels = new ArrayList<Shown>();
        for (var s : SHOWN) {
            if (!s.dimension.equals(dimension)) continue;
            long age = now - s.received;
            float alpha = Math.min(1f, age / 500f) * Math.min(1f, (life - age) / 5000f);
            var r = s.r;
            var box = new AABB(r.minX(), r.minY(), r.minZ(), r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1).move(-camera.x, -camera.y, -camera.z);
            int color = color(r);
            float cr = (color >> 16 & 255) / 255f, cg = (color >> 8 & 255) / 255f, cb = (color & 255) / 255f;
            if (r.kind() == Kind.ORE && !r.precise()) fill(poses, box, cr, cg, cb, 0.12f * alpha); // the soft blob
            LevelRenderer.renderLineBox(poses, buffers.getBuffer(PlumbLineRenderTypes.LINES), box, cr, cg, cb,
                    (r.kind() == Kind.VOID ? 0.45f : 0.9f) * alpha);
            if (r.kind() != Kind.VOID) labels.add(s);
        }
        buffers.endBatch(PlumbLineRenderTypes.FILL);
        buffers.endBatch(PlumbLineRenderTypes.LINES);
        labels.sort(Comparator.comparingDouble(s -> center(s.r).distanceToSqr(camera)));
        for (int i = 0; i < Math.min(MAX_LABELS, labels.size()); i++) {
            var r = labels.get(i).r;
            var c = center(r);
            if (c.distanceToSqr(camera) > LABEL_RANGE * LABEL_RANGE) break;
            DebugRenderer.renderFloatingText(poses, buffers, label(r), c.x, r.maxY() + 1.4, c.z, 0xFF000000 | color(r), 0.03f, true, 0, true);
        }
        buffers.endBatch();
    }

    private static Vec3 center(Return r) { return new Vec3((r.minX() + r.maxX() + 1) / 2.0, (r.minY() + r.maxY() + 1) / 2.0, (r.minZ() + r.maxZ() + 1) / 2.0); }

    private static int color(Return r) {
        return switch (r.kind()) {
            case ORE -> r.precise() ? 0xF2F2F2 : 0xE5A064;
            case VOID -> 0x72A8E0;
            case METAL -> 0xFFFFFF;
            case STRUCTURE -> 0x2DD4D2;
        };
    }

    /** Exact block name for ores and metal; "~" marks an untriangulated estimate. */
    private static String label(Return r) {
        if (r.kind() == Kind.STRUCTURE) return Component.translatable("gpr.frozendawn." + r.label()).getString();
        var id = ResourceLocation.tryParse(r.label());
        String name = id == null ? r.label() : BuiltInRegistries.BLOCK.getOptional(id).map(b -> b.getName().getString()).orElse(r.label());
        return r.kind() == Kind.ORE && !r.precise() ? "~ " + name : name;
    }

    private static void fill(PoseStack poses, AABB b, float r, float g, float bl, float a) {
        var v = buffers.getBuffer(PlumbLineRenderTypes.FILL);
        var m = poses.last().pose();
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (var f : faces) for (int i = 0; i < 12; i += 3) v.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, a);
    }
}
