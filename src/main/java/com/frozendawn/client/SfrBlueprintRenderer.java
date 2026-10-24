package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.SfrControllerBlock;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.network.SfrBlueprintPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** SFR-1 build guide: a translucent ghost of the expected block in every empty torus cell, and a red box on every cell
 * holding the wrong block or filling the plasma channel. Cells are read from the client's own world, so placing a block
 * updates the guide without a packet; it hides itself once the torus has nothing left to show. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SfrBlueprintRenderer {
    private static final Map<BlockPos, List<SfrMultiblock.Wrong>> SHOWN = new HashMap<>();
    private static String dimension = "";
    private static long scannedAt = Long.MIN_VALUE;
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    private static final double RANGE = 64, SCALE = 0.875;
    private static final int RESCAN_TICKS = 10;
    private SfrBlueprintRenderer() {}

    public static void receive(SfrBlueprintPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var here = level.dimension().location().toString();
        if (!here.equals(dimension)) { SHOWN.clear(); dimension = here; }
        if (payload.show()) { SHOWN.put(payload.controller().immutable(), List.of()); scannedAt = Long.MIN_VALUE; }
        else SHOWN.remove(payload.controller());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SHOWN.clear(); dimension = ""; }

    private static void rescan() {
        var level = Minecraft.getInstance().level;
        for (var it = SHOWN.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next(); var pos = e.getKey();
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SfrControllerBlock)) { it.remove(); continue; }
            var cells = SfrMultiblock.wrongCells(level, pos, state.getValue(SfrControllerBlock.FACING), Integer.MAX_VALUE);
            if (cells.isEmpty()) it.remove(); else e.setValue(cells);
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level == null || mc.player == null || SHOWN.isEmpty()) return;
        if (!dimension.equals(mc.level.dimension().location().toString())) { SHOWN.clear(); return; }
        long now = mc.level.getGameTime();
        if (now - scannedAt >= RESCAN_TICKS || now < scannedAt) { rescan(); scannedAt = now; }
        var camera = event.getCamera().getPosition(); var poses = event.getPoseStack();
        var ghosts = new ArrayList<SfrMultiblock.Wrong>(); var boxes = new ArrayList<BlockPos>();
        for (var e : SHOWN.entrySet()) {
            if (e.getKey().distToCenterSqr(mc.player.position()) > RANGE * RANGE) continue;
            for (var w : e.getValue()) {
                if (w.expected() != null && mc.level.getBlockState(w.pos()).isAir()) ghosts.add(w); else boxes.add(w.pos());
            }
        }
        if (ghosts.isEmpty() && boxes.isEmpty()) return;
        // Far to near, so nearer ghosts blend over farther ones instead of hiding them through the depth buffer.
        ghosts.sort(Comparator.comparingDouble((SfrMultiblock.Wrong w) -> w.pos().distToCenterSqr(camera)).reversed());
        float time = (now % 40 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 40F;
        float alpha = 0.4F + 0.15F * (float) Math.sin(time * Math.PI * 2);
        var blocks = mc.getBlockRenderer();
        var buffer = new Faded(BUFFERS.getBuffer(RenderType.translucent()), alpha);
        for (var w : ghosts) {
            var state = w.expected().defaultBlockState();
            poses.pushPose();
            poses.translate(w.pos().getX() - camera.x + (1 - SCALE) / 2, w.pos().getY() - camera.y + (1 - SCALE) / 2, w.pos().getZ() - camera.z + (1 - SCALE) / 2);
            poses.scale((float) SCALE, (float) SCALE, (float) SCALE);
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
