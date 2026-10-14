package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.PowerLinksPayload;
import com.frozendawn.power.PowerLink;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Native beams and tool targeting. Original rendering; no reference-mod assets or code. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PowerLinkRenderer {
    private static List<PowerLink> links = List.of();
    private static String dimension = "";
    private static long receivedAt;
    private static final MultiBufferSource.BufferSource OUTLINES = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private PowerLinkRenderer() {}
    public static void receive(PowerLinksPayload payload) {
        dimension = payload.dimension(); links = payload.links(); receivedAt = System.nanoTime();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { links = List.of(); dimension = ""; }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level == null) return;
        var camera = event.getCamera().getPosition(); var matrix = event.getPoseStack().last().pose();
        var editing = !mc.options.hideGui && mc.screen == null ? PowerBinderHud.state() : null;
        boolean showLinks = !links.isEmpty() && dimension.equals(mc.level.dimension().location().toString())
                && System.nanoTime() - receivedAt < 3_000_000_000L;
        boolean preview = editing != null && editing.source() != null && editing.target() != null
                && !editing.source().equals(editing.target()) && !mc.player.isShiftKeyDown()
                && editing.source().distSqr(editing.target()) <= 64 * 64;
        if (showLinks || preview) {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
            RenderSystem.depthMask(false); RenderSystem.setShader(GameRenderer::getPositionColorShader);
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            if (showLinks) for (var link : links) {
                boolean highlighted = editing != null && editing.target() != null && link.touches(editing.target());
                ribbon(buffer, matrix, camera, link.a(), link.b(), PowerBinderHud.CYAN, highlighted ? 0.05 : 0.025, highlighted ? 230 : 140);
            }
            if (preview) ribbon(buffer, matrix, camera, editing.source(), editing.target(), PowerBinderHud.color(editing), 0.04, 200);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
        }
        if (editing != null) {
            if (editing.source() != null && editing.source().distSqr(mc.player.blockPosition()) <= 64 * 64)
                outline(event, editing.source(), PowerBinderHud.CYAN, camera);
            if (editing.target() != null) outline(event, editing.target(), PowerBinderHud.color(editing), camera);
            OUTLINES.endBatch(RenderType.lines());
        }
    }
    private static void outline(RenderLevelStageEvent event, BlockPos pos, int color, Vec3 camera) {
        LevelRenderer.renderLineBox(event.getPoseStack(), OUTLINES.getBuffer(RenderType.lines()),
                new AABB(pos).inflate(0.035).move(-camera.x, -camera.y, -camera.z),
                (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
    }
    private static void ribbon(BufferBuilder buffer, org.joml.Matrix4f matrix, Vec3 camera,
            BlockPos from, BlockPos to, int color, double width, int alpha) {
        var a = Vec3.atCenterOf(from).subtract(camera); var b = Vec3.atCenterOf(to).subtract(camera);
        var axis = b.subtract(a).normalize(); var side = axis.cross(a.add(b).scale(-0.5)).normalize();
        if (side.lengthSqr() < 0.001) side = axis.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        side = side.scale(width);
        for (var point : List.of(a.add(side), a.subtract(side), b.subtract(side), b.add(side)))
            buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                    .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha);
    }
}
