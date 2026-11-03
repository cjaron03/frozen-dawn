package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gpr.PlumbLineOres;
import com.frozendawn.gpr.PlumbLinePolicy;
import com.frozendawn.gpr.PlumbLineSurvey;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import com.frozendawn.network.PlumbLineReturnsPayload;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * Plumb Line returns, drawn through terrain for 30 s: ore estimates as corner brackets in the ore's colour (a full box
 * once triangulated), caves as faint blue brackets, metal as white boxes, ORSA structures as cyan boxes. The nearest
 * few and the one under the crosshair are labelled with where to dig.
 * Fades in briefly and out over the last five seconds. Nothing here touches the world.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PlumbLineOverlay {
    /** A return on screen: from a pulse just received (fading after 30 s) or from a held Survey Sheet. */
    private record Shown(Return r, String dimension, long received, boolean held) { }
    private static final List<Shown> SHOWN = new ArrayList<>();
    /** Each scanner's last pulse, kept after the overlay fades so its screen can list the finds. */
    private static final java.util.Map<net.minecraft.core.BlockPos, PlumbLineReturnsPayload> LAST = new java.util.HashMap<>();
    private static final int MAX_SHOWN = 256, NEAREST_LABELS = 4, LABEL_RANGE = 96;
    /** A return within about 5 degrees of the crosshair is labelled and drawn at full strength. */
    private static final double AIM_COS = Math.cos(Math.toRadians(5));
    private static MultiBufferSource.BufferSource buffers;
    private PlumbLineOverlay() { }

    private static boolean drawnSinceReceive = true;

    public static void receive(PlumbLineReturnsPayload payload) {
        long now = System.currentTimeMillis();
        for (var r : payload.returns()) SHOWN.add(new Shown(r, payload.dimension(), now, false));
        LAST.put(payload.scanner(), payload);
        while (SHOWN.size() > MAX_SHOWN) SHOWN.removeFirst();
        drawnSinceReceive = false;
        FrozenDawn.LOGGER.info("[PlumbLine] Overlay received {} returns from {} in {}", payload.returns().size(),
                payload.scanner().toShortString(), payload.dimension());
    }

    /** Development check: test returns around the player, so drawing can be verified without a scan. */
    @SubscribeEvent public static void commands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
        if (net.neoforged.fml.loading.FMLEnvironment.production) return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("fdpreview").then(net.minecraft.commands.Commands.literal("gpr").executes(c -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return 0;
            var p = mc.player.blockPosition();
            receive(new PlumbLineReturnsPayload(mc.level.dimension().location().toString(), p, java.util.List.of(
                    new Return(Kind.ORE, "minecraft:coal_ore", p.getX() + 3, p.getY() - 6, p.getZ() - 2, p.getX() + 7, p.getY() - 3, p.getZ() + 2, false),
                    new Return(Kind.ORE, "minecraft:iron_ore", p.getX() - 6, p.getY() - 9, p.getZ() + 3, p.getX() - 5, p.getY() - 8, p.getZ() + 4, true),
                    new Return(Kind.VOID, "void", p.getX() - 8, p.getY() - 14, p.getZ() - 8, p.getX() + 2, p.getY() - 10, p.getZ() - 4, false),
                    new Return(Kind.METAL, "minecraft:iron_block", p.getX() + 1, p.getY() - 2, p.getZ() + 6, p.getX() + 1, p.getY() - 2, p.getZ() + 6, false))));
            return 1;
        })));
    }

    /** The Survey Sheet in either hand, if its ink is fresh and the player is within its pulse's range. */
    private static PlumbLineSurvey heldSurvey(Minecraft mc) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var survey = mc.player.getItemInHand(hand).get(ModDataComponents.PLUMB_LINE_SURVEY.get());
            if (survey != null && !survey.faded(mc.level.getGameTime())
                    && survey.inRange(mc.level.dimension().location().toString(), mc.player.getX(), mc.player.getZ())) return survey;
        }
        return null;
    }

    /** Survey Sheet tooltip: where and how far the pulse reached, how long the ink lasts, and the ore it found. */
    @SubscribeEvent public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var survey = event.getItemStack().get(ModDataComponents.PLUMB_LINE_SURVEY.get());
        var mc = Minecraft.getInstance();
        if (survey == null || mc.level == null || mc.player == null) return;
        var lines = event.getToolTip();
        var gray = net.minecraft.ChatFormatting.GRAY;
        lines.add(Component.translatable("item.frozendawn.plumb_line_survey.taken", survey.scanner().getX(), survey.scanner().getZ(), survey.radius()).withStyle(gray));
        long left = PlumbLineSurvey.INK_TICKS - (mc.level.getGameTime() - survey.printedAt());
        if (survey.faded(mc.level.getGameTime())) { lines.add(Component.translatable("item.frozendawn.plumb_line_survey.faded").withStyle(net.minecraft.ChatFormatting.RED)); return; }
        lines.add(Component.translatable("item.frozendawn.plumb_line_survey.ink", (left + 1199) / 1200).withStyle(gray));
        boolean near = survey.inRange(mc.level.dimension().location().toString(), mc.player.getX(), mc.player.getZ());
        lines.add(Component.translatable(near ? "item.frozendawn.plumb_line_survey.hold" : "item.frozendawn.plumb_line_survey.far").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        var counts = new java.util.LinkedHashMap<String, int[]>();
        var first = new java.util.HashMap<String, Return>();
        for (var r : survey.returns()) if (r.kind() == Kind.ORE) {
            String key = PlumbLineOres.of(r.label()).key();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            first.putIfAbsent(key, r);
        }
        var keys = new ArrayList<>(counts.keySet());
        keys.sort(Comparator.comparingInt((String k) -> -PlumbLineOres.of(first.get(k).label()).tier()).thenComparingInt(k -> -counts.get(k)[0]));
        for (int i = 0; i < Math.min(5, keys.size()); i++) {
            var r = first.get(keys.get(i));
            int n = counts.get(keys.get(i))[0];
            lines.add(Component.literal(" " + name(r) + (n > 1 ? " ×" + n : "")).withStyle(st -> st.withColor(color(r))));
        }
        if (keys.size() > 5) lines.add(Component.translatable("item.frozendawn.plumb_line_survey.more", keys.size() - 5).withStyle(gray));
        if (keys.isEmpty()) lines.add(Component.translatable("screen.frozendawn.plumb_line.none").withStyle(gray));
    }

    public static PlumbLineReturnsPayload last(net.minecraft.core.BlockPos scanner) { return LAST.get(scanner); }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SHOWN.clear(); LAST.clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var sheet = heldSurvey(mc);
        if (SHOWN.isEmpty() && sheet == null) return;
        if (!drawnSinceReceive && !SHOWN.isEmpty()) {
            drawnSinceReceive = true;
            FrozenDawn.LOGGER.info("[PlumbLine] Overlay drawing {} returns", SHOWN.size());
        }
        long now = System.currentTimeMillis(), life = PlumbLinePolicy.OVERLAY_TICKS * 50L;
        SHOWN.removeIf(s -> now - s.received >= life);
        String dimension = mc.level.dimension().location().toString();
        var shown = new ArrayList<>(SHOWN);
        if (sheet != null) for (var r : sheet.returns()) shown.add(new Shown(r, sheet.dimension(), now, true));
        Vec3 camera = event.getCamera().getPosition();
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
        PoseStack poses = event.getPoseStack();
        var look = new Vec3(event.getCamera().getLookVector());
        Shown aimed = null;
        double aimedCos = AIM_COS;
        for (var s : shown) {
            if (!s.dimension.equals(dimension) || s.r.kind() == Kind.VOID) continue;
            double cos = center(s.r).subtract(camera).normalize().dot(look);
            if (cos > aimedCos) { aimedCos = cos; aimed = s; }
        }
        var labels = new ArrayList<Shown>();
        // NO_DEPTH_TEST leaves the current depth state alone, and it is on here; switch it off so buried returns show.
        RenderSystem.disableDepthTest();
        var lines = buffers.getBuffer(PlumbLineRenderTypes.LINES);
        double fadeRange = PlumbLinePolicy.Setting.DEEP.radius;
        for (var s : shown) {
            if (!s.dimension.equals(dimension)) continue;
            long age = now - s.received;
            var r = s.r;
            // Far returns recede so the nearest few stay readable; the one under the crosshair stands out.
            double distance = Math.sqrt(center(r).distanceToSqr(camera));
            float fade = s.held ? 0.85f : Math.min(1f, age / 500f) * Math.min(1f, (life - age) / 5000f);
            float alpha = fade * (s == aimed ? 1f : (float) Math.max(0.25, 1 - distance / fadeRange));
            var box = new AABB(r.minX(), r.minY(), r.minZ(), r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1).move(-camera.x, -camera.y, -camera.z);
            int color = color(r);
            float cr = (color >> 16 & 255) / 255f, cg = (color >> 8 & 255) / 255f, cb = (color & 255) / 255f;
            boolean estimate = r.kind() == Kind.VOID || r.kind() == Kind.ORE && !r.precise();
            if (estimate) corners(poses.last(), lines, box, cr, cg, cb, (r.kind() == Kind.VOID ? 0.4f : 0.9f) * alpha);
            else LevelRenderer.renderLineBox(poses, lines, box, cr, cg, cb, 0.95f * alpha);
            if (r.kind() != Kind.VOID && s != aimed) labels.add(s);
        }
        buffers.endBatch(PlumbLineRenderTypes.LINES);
        labels.sort(Comparator.comparingDouble(s -> center(s.r).distanceToSqr(camera)));
        var labelled = new ArrayList<Shown>();
        if (aimed != null) labelled.add(aimed);
        for (var s : labels) {
            if (labelled.size() >= NEAREST_LABELS + (aimed != null ? 1 : 0) || center(s.r).distanceToSqr(camera) > LABEL_RANGE * LABEL_RANGE) break;
            labelled.add(s);
        }
        // Directions are from the player's feet, the way they would dig.
        var feet = mc.player != null ? mc.player.position() : camera;
        for (var s : labelled) {
            var r = s.r;
            var c = center(r);
            DebugRenderer.renderFloatingText(poses, buffers, label(r, feet), c.x, r.maxY() + 1.4, c.z, 0xFF000000 | color(r), s == aimed ? 0.035f : 0.026f, true, 0, true);
        }
        buffers.endBatch();
        RenderSystem.enableDepthTest();
    }

    public static Vec3 center(Return r) { return new Vec3((r.minX() + r.maxX() + 1) / 2.0, (r.minY() + r.maxY() + 1) / 2.0, (r.minZ() + r.maxZ() + 1) / 2.0); }

    public static int color(Return r) {
        return switch (r.kind()) {
            case ORE -> PlumbLineOres.of(r.label()).color();
            case VOID -> 0x72A8E0;
            case METAL -> 0xFFFFFF;
            case STRUCTURE -> 0x2DD4D2;
        };
    }

    /** Short name: the ore's type, the metal block's name, or the ORSA site. */
    public static String name(Return r) {
        if (r.kind() == Kind.STRUCTURE) return Component.translatable("gpr.frozendawn." + r.label()).getString();
        if (r.kind() == Kind.ORE) return oreName(PlumbLineOres.of(r.label()), r.label());
        var id = ResourceLocation.tryParse(r.label());
        return id == null ? r.label() : BuiltInRegistries.BLOCK.getOptional(id).map(b -> b.getName().getString()).orElse(r.label());
    }

    public static String oreName(PlumbLineOres.Ore ore, String blockId) {
        if (ore.known()) return Component.translatable("gpr.frozendawn.ore." + ore.key()).getString();
        var id = ResourceLocation.tryParse(blockId);
        return id == null ? ore.key() : BuiltInRegistries.BLOCK.getOptional(id).map(b -> b.getName().getString()).orElse(ore.key());
    }

    /** Where to dig from {@code from}: "~ Galena · 24 down · 15 m NE"; "~" marks an untriangulated estimate. */
    public static String where(Return r, Vec3 from) {
        var c = center(r);
        int dy = (int) Math.round(c.y - from.y), flat = (int) Math.round(Math.hypot(c.x - from.x, c.z - from.z));
        var depth = dy <= -2 ? Component.translatable("gpr.frozendawn.depth.down", -dy) : dy >= 2 ? Component.translatable("gpr.frozendawn.depth.up", dy)
                : Component.translatable("gpr.frozendawn.depth.level");
        var compass = Component.translatable("gpr.frozendawn.compass." + PlumbLinePolicy.compass(c.x - from.x, c.z - from.z));
        return Component.translatable("gpr.frozendawn.where", depth, flat, compass).getString();
    }

    private static String label(Return r, Vec3 from) {
        String name = r.kind() == Kind.ORE && !r.precise() ? "~ " + name(r) : name(r);
        return name + " · " + where(r, from);
    }

    /** Corner brackets: an estimated area, without the wall of lines a full box makes when many overlap. */
    private static void corners(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer v, AABB b, float r, float g, float bl, float a) {
        double lx = Math.min(1.5, b.getXsize() / 4), ly = Math.min(1.5, b.getYsize() / 4), lz = Math.min(1.5, b.getZsize() / 4);
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX, y = (i & 2) == 0 ? b.minY : b.maxY, z = (i & 4) == 0 ? b.minZ : b.maxZ;
            double sx = (i & 1) == 0 ? 1 : -1, sy = (i & 2) == 0 ? 1 : -1, sz = (i & 4) == 0 ? 1 : -1;
            line(pose, v, x, y, z, x + sx * lx, y, z, 1, 0, 0, r, g, bl, a);
            line(pose, v, x, y, z, x, y + sy * ly, z, 0, 1, 0, r, g, bl, a);
            line(pose, v, x, y, z, x, y, z + sz * lz, 0, 0, 1, r, g, bl, a);
        }
    }

    private static void line(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer v, double x0, double y0, double z0,
                             double x1, double y1, double z1, float nx, float ny, float nz, float r, float g, float b, float a) {
        v.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        v.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
