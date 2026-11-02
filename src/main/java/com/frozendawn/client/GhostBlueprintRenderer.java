package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GhostCell;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Multiblock build guide shared by the SFR-1 and the AMR-1 Icarus: a translucent ghost of the expected block in every empty
 * cell, and a red box on every cell holding the wrong block or one that must be clear, wrapped in a ghost of what belongs
 * there. Each shown controller brings the function that lists its cells; they are read from the client's own world, so
 * placing a block updates the guide without a packet, and a controller hides itself once it has nothing left to show. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class GhostBlueprintRenderer {
    /** Cells read per controller per scan; every cell, as the guide has always drawn. */
    public static final int LIMIT = Integer.MAX_VALUE;
    private record Shown(Function<Level, List<GhostCell>> source, List<GhostCell> cells) {}
    private static final Map<BlockPos, Shown> SHOWN = new HashMap<>();
    private static String dimension = "";
    private static long scannedAt;
    /** Set when a controller is added, so the next frame reads its cells instead of waiting out RESCAN_TICKS. */
    private static boolean stale;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static final double RANGE = 64, SCALE = 0.875, OVER = 1.02;
    private static final int RESCAN_TICKS = 10;
    private GhostBlueprintRenderer() {}

    /** Shows a controller's guide; cells returns nothing once the controller is gone or the structure is finished. */
    public static void show(BlockPos controller, Function<Level, List<GhostCell>> cells) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var here = level.dimension().location().toString();
        if (!here.equals(dimension)) { SHOWN.clear(); dimension = here; }
        SHOWN.put(controller.immutable(), new Shown(cells, List.of())); stale = true;
    }
    public static void hide(BlockPos controller) { SHOWN.remove(controller); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SHOWN.clear(); dimension = ""; }

    private static void rescan() {
        var level = Minecraft.getInstance().level;
        for (var it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!level.hasChunkAt(e.getKey())) continue;
            var cells = e.getValue().source().apply(level);
            if (cells.isEmpty()) it.remove(); else e.setValue(new Shown(e.getValue().source(), cells));
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level == null || mc.player == null || SHOWN.isEmpty()) return;
        if (!dimension.equals(mc.level.dimension().location().toString())) { SHOWN.clear(); return; }
        long now = mc.level.getGameTime();
        if (stale || now - scannedAt >= RESCAN_TICKS || now < scannedAt) { rescan(); scannedAt = now; stale = false; }
        var camera = event.getCamera().getPosition(); var poses = event.getPoseStack();
        var ghosts = new ArrayList<GhostCell>(); var boxes = new ArrayList<BlockPos>();
        for (var e : SHOWN.entrySet()) {
            if (e.getKey().distToCenterSqr(mc.player.position()) > RANGE * RANGE) continue;
            for (var w : e.getValue().cells()) {
                if (w.ghost() != null) ghosts.add(w);
                if (w.ghost() == null || !mc.level.getBlockState(w.pos()).isAir()) boxes.add(w.pos());
            }
        }
        if (ghosts.isEmpty() && boxes.isEmpty()) return;
        // Far to near, so nearer ghosts blend over farther ones instead of hiding them through the depth buffer.
        ghosts.sort(Comparator.comparingDouble((GhostCell w) -> w.pos().distToCenterSqr(camera)).reversed());
        float time = (now % 40 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 40F;
        float alpha = 0.4F + 0.15F * (float) Math.sin(time * Math.PI * 2);
        var blocks = mc.getBlockRenderer();
        var buffer = new Faded(BUFFERS.getBuffer(RenderType.translucent()), alpha);
        for (var w : ghosts) {
            var state = w.ghost();
            // Inside an empty cell the ghost sits a little small; over a wrong block it wraps it, showing what goes there instead.
            double scale = mc.level.getBlockState(w.pos()).isAir() ? SCALE : OVER;
            poses.pushPose();
            poses.translate(w.pos().getX() - camera.x + (1 - scale) / 2, w.pos().getY() - camera.y + (1 - scale) / 2, w.pos().getZ() - camera.z + (1 - scale) / 2);
            poses.scale((float) scale, (float) scale, (float) scale);
            blocks.getModelRenderer().renderModel(poses.last(), buffer, state, blocks.getBlockModel(state), 1, 1, 1,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
            poses.popPose();
        }
        BUFFERS.endBatch(RenderType.translucent());
        var lines = BUFFERS.getBuffer(RenderType.lines());
        for (var pos : boxes)
            LevelRenderer.renderLineBox(poses, lines, new AABB(pos).inflate(0.02).move(-camera.x, -camera.y, -camera.z), 1F, 0.25F, 0.2F, 1F);
        BUFFERS.endBatch(RenderType.lines());
    }

    /** Scales every vertex's alpha, turning an opaque block model into a ghost. */
    private record Faded(VertexConsumer inner, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { inner.setColor(r, g, b, Math.round(a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { inner.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
    }
}
