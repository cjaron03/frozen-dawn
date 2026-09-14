package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.debug.architect.ArchitectDebugSnapshot;
import com.frozendawn.debug.architect.ArchitectDebugSnapshot.*;
import com.frozendawn.network.ArchitectDebugPayload;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.PathfindingRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mojang's path/box/text rendering helpers, driven entirely by captured server observations. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ArchitectDebugRenderer {
    private static final int CYAN = 0xff40dcff, GOLD = 0xffffcc40, PINK = 0xffff70cc, WHITE = 0xffeeeeee;
    private static ArchitectDebugPayload.View view;
    private static Path actorPath, targetPath;
    private static MultiBufferSource.BufferSource buffers;
    private ArchitectDebugRenderer() { }

    public static void receive(ArchitectDebugPayload.View update) {
        view = update.enabled() ? update : null;
        actorPath = targetPath = null;
        if (view != null && view.snapshot() != null) {
            actorPath = nativePath(view.snapshot().actor().navigation());
            if (view.snapshot().target() != null) targetPath = nativePath(view.snapshot().target().navigation());
        }
    }

    private static Path nativePath(Route route) {
        if (route.nodes().isEmpty() || route.destination() == null) return null;
        List<Node> nodes = new ArrayList<>();
        for (var saved : route.nodes()) {
            Node node = new Node(saved.x(), saved.y(), saved.z());
            try { node.type = PathType.valueOf(saved.type()); } catch (IllegalArgumentException ignored) { node.type = PathType.OPEN; }
            node.costMalus = saved.costMalus();
            nodes.add(node);
        }
        Point goal = route.destination();
        Path path = new Path(nodes, BlockPos.containing(goal.x(), goal.y(), goal.z()), route.reachable());
        path.setNextNodeIndex(Math.max(0, Math.min(nodes.size(), route.nextNode() - route.firstNode())));
        return path;
    }

    private static boolean here() {
        var minecraft = Minecraft.getInstance();
        return view != null && minecraft.level != null && minecraft.player != null
                && (view.snapshot() == null || view.snapshot().dimension().equals(minecraft.level.dimension().location().toString()));
    }
    private static boolean layer(int bit) { return (view.layers() & bit) != 0; }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !here() || view.snapshot() == null) return;
        var snapshot = view.snapshot();
        Vec3 camera = event.getCamera().getPosition();
        if (vec(snapshot.actor().position()).distanceToSqr(camera) > 128 * 128) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        if (layer(ArchitectDebugPayload.ROUTES)) {
            if (actorPath != null) {
                PathfindingRenderer.renderPath(poses, buffers, actorPath, 0.18f, false, layer(ArchitectDebugPayload.LABELS), camera.x, camera.y, camera.z);
                buffers.endBatch(RenderType.debugLineStrip(6.0));
            }
            if (targetPath != null) {
                PathfindingRenderer.renderPath(poses, buffers, targetPath, 0.07f, false, false, camera.x, camera.y, camera.z);
                buffers.endBatch(RenderType.debugLineStrip(6.0));
            }
            polyline(poses, snapshot.planner().corridor().stream().map(p -> new Point(p.x() + 0.5, p.y() + 0.12, p.z() + 0.5)).toList(), CYAN, camera);
            Point waypoint = snapshot.planner().waypoint();
            if (waypoint != null) filled(poses, cube(waypoint).deflate(0.3), CYAN, 0.5f, camera);
            Point goal = snapshot.lab().targetGoal();
            if (goal != null) {
                filled(poses, new AABB(goal.x() - 0.2, goal.y(), goal.z() - 0.2, goal.x() + 0.2, goal.y() + 0.6, goal.z() + 0.2), PINK, 0.55f, camera);
                text(poses, "Target waypoint " + snapshot.lab().waypoint(), goal.x(), goal.y() + 1, goal.z(), PINK);
            }
            if (snapshot.planner().steeringGoal() != null)
                polyline(poses, List.of(snapshot.actor().position(), snapshot.planner().steeringGoal()), 0xff80ff80, camera);
        }
        if (layer(ArchitectDebugPayload.GEOMETRY)) {
            outline(poses, box(snapshot.actor().bounds()), CYAN, 1f, camera);
            if (snapshot.target() != null) outline(poses, box(snapshot.target().bounds()), PINK, 1f, camera);
            for (var shape : snapshot.collisionShapes()) outline(poses, box(shape.bounds()).inflate(0.002), 0xffaabbd0, 0.5f, camera);
            for (var marker : snapshot.markers()) {
                int color = switch (marker.kind()) {
                    case "MINING" -> 0xffff7030;
                    case "SCAFFOLD" -> 0xff60ff90;
                    case "STEP" -> GOLD;
                    case "SUPPORT" -> 0xff70a0ff;
                    case "EXCLUDED" -> 0xffff4040;
                    default -> 0xffcc80ff;
                };
                filled(poses, cube(marker.block()).inflate(0.006), color, marker.kind().equals("CANDIDATE") ? 0.07f : 0.18f, camera);
                outline(poses, cube(marker.block()).inflate(0.008), color, 0.8f, camera);
                if (layer(ArchitectDebugPayload.LABELS) || marker.kind().equals("MINING") || marker.kind().equals("STEP"))
                    text(poses, marker.kind() + " " + marker.reason(), marker.block().x() + 0.5, marker.block().y() + 1.08, marker.block().z() + 0.5, color);
            }
            if (snapshot.target() != null) {
                int color = snapshot.combat().hitEligible() ? 0xff70ff90 : 0xffffaa50;
                sphere(poses, snapshot.actor().position(), snapshot.combat().attackRange(), color, camera);
                polyline(poses, List.of(snapshot.actor().position(), snapshot.target().position()), color, camera);
            }
            Body actor = snapshot.actor();
            Point head = new Point(actor.position().x(), actor.bounds().maxY() - 0.2, actor.position().z());
            heading(poses, head, actor.headYaw(), WHITE, camera);
            heading(poses, head, actor.bodyYaw(), PINK, camera);
        }
        if (layer(ArchitectDebugPayload.TRAILS)) {
            polyline(poses, snapshot.trails().stream().map(Trail::actor).map(p -> new Point(p.x(), p.y() + 0.07, p.z())).toList(), CYAN, camera);
            List<Point> targetTrail = new ArrayList<>();
            for (var trail : snapshot.trails()) {
                if (trail.target() == null) { polyline(poses, targetTrail, PINK, camera); targetTrail.clear(); }
                else targetTrail.add(new Point(trail.target().x(), trail.target().y() + 0.08, trail.target().z()));
            }
            polyline(poses, targetTrail, PINK, camera);
        }
        if (layer(ArchitectDebugPayload.ROUTES) || layer(ArchitectDebugPayload.GEOMETRY) || layer(ArchitectDebugPayload.LABELS)) {
            Point actor = snapshot.actor().position();
            text(poses, "Architect #" + snapshot.actor().entityId() + " | " + snapshot.recovery().action(), actor.x(), snapshot.actor().bounds().maxY() + 0.45, actor.z(), CYAN);
            if (snapshot.target() != null) {
                Point target = snapshot.target().position();
                text(poses, "Target #" + snapshot.target().entityId(), target.x(), snapshot.target().bounds().maxY() + 0.4, target.z(), PINK);
            }
        }
        buffers.endBatch();
        poses.popPose();
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!here() || !layer(ArchitectDebugPayload.HUD) || Minecraft.getInstance().options.hideGui) return;
        var minecraft = Minecraft.getInstance();
        var gui = event.getGuiGraphics();
        List<String> lines = new ArrayList<>();
        var s = view.snapshot();
        if (s == null) {
            lines.add("Architect debugger"); lines.add(view.notice());
        } else {
            lines.add("Architect #" + s.actor().entityId() + " | " + s.lab().scenario() + " | " + s.lab().status());
            lines.add((view.historical() ? "HISTORY (current terrain)" : view.serverFrozen() ? "FROZEN" : "LIVE")
                    + " | run tick " + s.runTick() + " | game tick " + s.gameTick());
            lines.add(s.recovery().action() + " | D* " + s.planner().search() + " | cells " + s.planner().cells());
            lines.add(s.target() == null ? "Target: none" : String.format(Locale.ROOT, "Target #%d | distance %.2f / hit < %.1f | LOS %s",
                    s.target().entityId(), s.combat().distance(), s.combat().attackRange(), s.combat().lineOfSight() ? "yes" : "no"));
            lines.add("Attack: " + s.combat().blockedBy() + " | backoff " + s.combat().backoffTicks());
            Route nav = s.actor().navigation();
            lines.add("Navigation " + nav.nextNode() + "/" + nav.totalNodes() + " | " + (nav.done() ? "done" : "active")
                    + " | reach " + nav.reachable() + " | stuck " + nav.stuck() + (nav.truncated() ? " | clipped" : ""));
            lines.add("Committed walk " + s.planner().corridorIndex() + "/" + s.planner().corridorTotal() + " | step " + s.planner().stepType()
                    + (s.planner().corridor().size() < s.planner().corridorTotal() ? " | clipped" : ""));
            lines.add("Stall " + s.recovery().noProgressTicks() + " | walk " + s.recovery().walkStuckTicks()
                    + " | reinits " + s.recovery().reinits() + " | excluded " + s.recovery().exclusions());
            lines.add("Mine " + s.mining().reason() + " " + s.mining().progressTicks() + "/" + s.mining().requiredTicks()
                    + " | captured breaks " + s.recovery().destroyedBlocks() + " | placed " + s.recovery().scaffoldPlacements());
            if (s.target() != null) lines.add(String.format(Locale.ROOT, "Health %.1f / target %.1f | target nav %s",
                    s.actor().health(), s.target().health(), s.target().navigation().totalNodes() == 0 ? "none" : s.target().navigation().reachable() ? "reachable" : "partial"));
            if (s.lab().waypoint() >= 0) lines.add("Target waypoint " + s.lab().waypoint() + " | lap " + s.lab().lap()
                    + " | hits " + s.lab().hits() + " | target still " + s.lab().targetStill());
            lines.add("Cyan: corridor / actor trail   Pink: target trail");
            lines.add("Gold: D* step   Orange: mining   Red/blue: native path nodes");
            if (s.shapesTruncated()) lines.add("Collision display capped at " + ArchitectDebugSnapshot.MAX_SHAPES + " shapes");
            List<Event> recent = s.events();
            for (int i = Math.max(0, recent.size() - 3); i < recent.size(); i++) {
                var e = recent.get(i);
                lines.add("t" + e.runTick() + " " + e.kind() + ": " + e.detail());
            }
        }
        int width = Math.max(80, Math.min(440, gui.guiWidth() - 16));
        int limit = Math.min(lines.size(), Math.max(1, (gui.guiHeight() - 28) / 10));
        gui.fill(6, 6, 10 + width, 14 + limit * 10, 0xd0182029);
        gui.fill(6, 6, 8, 14 + limit * 10, view.historical() ? GOLD : CYAN);
        for (int i = 0; i < limit; i++) gui.drawString(minecraft.font,
                minecraft.font.plainSubstrByWidth(lines.get(i), width - 10), 12, 11 + i * 10, i == 0 ? CYAN : WHITE, false);
    }

    private static void polyline(PoseStack poses, List<Point> points, int color, Vec3 camera) {
        if (points.size() < 2) return;
        var type = RenderType.debugLineStrip(2.0);
        var vertices = buffers.getBuffer(type);
        for (Point point : points) vertices.addVertex(poses.last(), (float) (point.x() - camera.x),
                (float) (point.y() - camera.y), (float) (point.z() - camera.z)).setColor(color);
        buffers.endBatch(type);
    }

    private static void sphere(PoseStack poses, Point center, double radius, int color, Vec3 camera) {
        for (int plane = 0; plane < 3; plane++) {
            List<Point> circle = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                double angle = i * Math.PI * 2 / 48, a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
                circle.add(new Point(center.x() + (plane == 2 ? 0 : a), center.y() + (plane == 0 ? 0 : plane == 1 ? b : a), center.z() + (plane == 1 ? 0 : b)));
            }
            polyline(poses, circle, color, camera);
        }
    }

    private static void heading(PoseStack poses, Point start, float yaw, int color, Vec3 camera) {
        double radians = Math.toRadians(yaw);
        polyline(poses, List.of(start, new Point(start.x() - Math.sin(radians) * 1.5, start.y(), start.z() + Math.cos(radians) * 1.5)), color, camera);
    }
    private static void outline(PoseStack poses, AABB box, int color, float alpha, Vec3 camera) {
        LevelRenderer.renderLineBox(poses, buffers.getBuffer(RenderType.lines()), box.move(-camera.x, -camera.y, -camera.z),
                (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha);
    }
    private static void filled(PoseStack poses, AABB box, int color, float alpha, Vec3 camera) {
        DebugRenderer.renderFilledBox(poses, buffers, box.move(-camera.x, -camera.y, -camera.z),
                (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha);
    }
    private static void text(PoseStack poses, String text, double x, double y, double z, int color) {
        DebugRenderer.renderFloatingText(poses, buffers, text, x, y, z, color, 0.02f, true, 0, true);
    }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static AABB cube(Point p) { return new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1); }
    private static AABB box(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { view = null; actorPath = targetPath = null; }
}
