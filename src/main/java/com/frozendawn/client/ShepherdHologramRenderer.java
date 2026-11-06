package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.shepherd.ShepherdEntity;
import com.frozendawn.shepherd.ShepherdHologram;
import com.frozendawn.shepherd.ShepherdHologram.Shade;
import com.frozendawn.shepherd.ShepherdProjection;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Her projection, in the mockup's own numbers: texels from her middle, x toward the figure's side as you face her, z toward you.
 * The figure is a plain humanoid, never {@code ArchitectModel}, wearing the mob's own skin repainted in her greens each tick,
 * so forming, tears and gaps are texels coming and going.
 */
public final class ShepherdHologramRenderer {
    private static final ResourceLocation PAINTED = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "dynamic/shepherd_hologram");
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation HUNTER = skin("returned_0"), MIMIC = skin("shadow_blank"), ARCHITECT = skin("architect");
    private static final int GLOW = 0x7DF2C0, BODY = 0x3AA984, HOLO = 0x1F5C48, HOLO_DARK = 0x113028;
    private static final int SEE_THROUGH = 0xC8, SOLID = 0xE0;
    private static final int SKIN = 64, FRAME_TICKS = 3;
    private static final int FIGURE_X = -30, FIGURE_Y = 4, FIGURE_Z = 5, FROM_X = -4, FROM_Y = 7, RING_Y = 2, RING_RADIUS = 8, ROWS_HALF = 8, SMALL_YOU_X = -5;
    private static final int[] RAY_TOPS = {1, 8, 15};
    private static final float TEXEL = 1 / 16F, FIGURE_SCALE = 0.5F, MODEL_DROP = -1.501F, HUNTER_DEGREES_A_TICK = 1.5F, MAX_LOOK = 1.2F, HALF = 0.5F;
    private static final float CHIN_ARM_X = -2.2F, CHIN_ARM_Y = -0.6F, CHIN_HEAD_X = 0.25F;
    private static final float DIM_SHADOWS = 0.55F, LIFT_LIGHT = 0.2F, LIGHT_GAIN = 1.6F;

    private final HumanoidModel<LivingEntity> model;
    private final PlayerModel<LivingEntity> wide, slim;
    private DynamicTexture painted;
    private NativeImage source;
    private int sourceFigure = ShepherdProjection.NONE, paintedFigure = ShepherdProjection.NONE, paintedAge = -1;

    public ShepherdHologramRenderer(EntityRendererProvider.Context context) {
        model = new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE));
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // A model is a baby until a mob's renderer says otherwise, and none does here.
        model.young = wide.young = slim.young = false;
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/" + name + ".png");
    }

    /** {@code pose} stands at her feet, facing the world's way. */
    public void draw(ShepherdEntity she, float partial, PoseStack pose, MultiBufferSource buffers) {
        if (!she.hologramShown()) return;
        int age = she.projectionAge(), leaving = she.hologramLeaving();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - she.projectionYaw()));
        var flat = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        stage(flat, pose, age, leaving);
        boolean egg = she.projection() == ShepherdProjection.EGG_FRAME;
        if (egg) hooded(flat, pose, age, she.eggAge());
        if (!egg || ShepherdHologram.eggTearing(she.eggAge())) figure(she, partial, pose, buffers, age, leaving);
        pose.popPose();
    }

    /** Thin rays from her side, reaching out first, then the ring the figure stands in. */
    private static void stage(VertexConsumer flat, PoseStack pose, int age, int leaving) {
        int frame = age / FRAME_TICKS, last = FIGURE_X + 3;
        float beam = Math.min(ShepherdHologram.beam(age), ShepherdHologram.beamLeft(leaving));
        int far = Math.round(FROM_X + (last - FROM_X) * beam);
        for (int ray = 0; ray < RAY_TOPS.length; ray++) {
            for (int x = FROM_X; x > last && x >= far; x--) {
                if ((x + frame + ray) % 3 == 0) continue;
                float along = (FROM_X - x) / (float) (FROM_X - last);
                voxel(flat, pose, x, Math.round(FROM_Y + (FIGURE_Y + RAY_TOPS[ray] - FROM_Y) * along), FIGURE_Z, HOLO, SEE_THROUGH);
            }
        }
        voxel(flat, pose, FROM_X, FROM_Y, FIGURE_Z, GLOW, SOLID);
        if (beam < 1) return;
        for (int step = 0; step < RING_RADIUS * 6; step++) {
            double angle = step * Math.PI * 2 / (RING_RADIUS * 6);
            int x = (int) Math.round(Math.cos(angle) * RING_RADIUS), z = (int) Math.round(Math.sin(angle) * RING_RADIUS);
            voxel(flat, pose, FIGURE_X + x, RING_Y, FIGURE_Z + z, (step + frame) % 6 == 0 ? GLOW : BODY, SEE_THROUGH);
        }
    }

    private void figure(ShepherdEntity she, float partial, PoseStack pose, MultiBufferSource buffers, int age, int leaving) {
        int figure = she.projectionFigure();
        // These two have no skin of ours to take apart, so they go halfway through.
        boolean whole = figure == ShepherdProjection.ITEM || figure == ShepherdProjection.PAIR;
        if (whole && ShepherdHologram.faded(leaving) > HALF) return;
        if (figure == ShepherdProjection.ITEM) {
            item(she, partial, pose, buffers, age);
            return;
        }
        if (figure == ShepherdProjection.PAIR) {
            pair(she, pose, buffers, age);
            return;
        }
        paint(figure, age, leaving);
        if (painted == null) return;
        pose.pushPose();
        pose.translate(-FIGURE_X * TEXEL, FIGURE_Y * TEXEL, -FIGURE_Z * TEXEL);
        if (figure == ShepherdProjection.HUNTER) pose.mulPose(Axis.YP.rotationDegrees((age + partial) * HUNTER_DEGREES_A_TICK));
        pose.scale(-FIGURE_SCALE, -FIGURE_SCALE, FIGURE_SCALE);
        pose.translate(0, MODEL_DROP, 0);
        posed(she, figure, age, partial);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(PAINTED)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
        // Asking for another render type ends the last one, so this asks again.
        if (figure == ShepherdProjection.ARCHITECT && leaving == ShepherdHologram.STAYING) overdraw(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), pose, age);
    }

    /** Something stupid, in her green. */
    private static void item(ShepherdEntity she, float partial, PoseStack pose, MultiBufferSource buffers, int age) {
        pose.pushPose();
        pose.translate(-FIGURE_X * TEXEL, (FIGURE_Y + ROWS_HALF) * TEXEL, -FIGURE_Z * TEXEL);
        pose.mulPose(Axis.YP.rotationDegrees((age + partial) * HUNTER_DEGREES_A_TICK));
        var stack = new ItemStack(ModItems.STUFFED_PENGUIN.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, tinted(buffers), she.level(), 0);
        pose.popPose();
    }

    /** A small you in your own skin, in her green, beside a small her. Whoever is looking sees her follower. */
    private void pair(ShepherdEntity she, PoseStack pose, MultiBufferSource buffers, int age) {
        var skin = skinOf(she.projectedPlayer().orElse(she.getUUID()));
        var body = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        pose.pushPose();
        pose.translate(-(FIGURE_X + SMALL_YOU_X) * TEXEL, FIGURE_Y * TEXEL, -FIGURE_Z * TEXEL);
        pose.scale(-FIGURE_SCALE, -FIGURE_SCALE, FIGURE_SCALE);
        pose.translate(0, MODEL_DROP, 0);
        body.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(skin.texture())), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, SEE_THROUGH << 24 | GLOW);
        pose.popPose();
        var flat = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        for (var cell : ShepherdHologram.smallHer()) voxel(flat, pose, FIGURE_X + cell.x(), FIGURE_Y + cell.y(), FIGURE_Z + cell.z(), shaded(cell.shade(), cell.y(), ShepherdHologram.band(age)), SOLID);
    }

    private static PlayerSkin skinOf(UUID who) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(who);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(who);
    }

    private static MultiBufferSource tinted(MultiBufferSource buffers) {
        return type -> new Tinted(buffers.getBuffer(type));
    }

    /** An item's model knows nothing of her green, so its vertices are told on their way through. */
    private record Tinted(VertexConsumer to) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x, y, z);
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            to.setColor(red * (GLOW >> 16 & 0xFF) / 255, green * (GLOW >> 8 & 0xFF) / 255, blue * (GLOW & 0xFF) / 255, alpha);
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override public VertexConsumer setUv2(int u, int v) {
            to.setUv2(u, v);
            return this;
        }

        @Override public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }

    private void posed(ShepherdEntity she, int figure, int age, float partial) {
        model.head.xRot = model.head.yRot = 0;
        model.rightArm.xRot = model.rightArm.yRot = 0;
        turn(she, figure, age, partial);
        model.hat.copyFrom(model.head);
    }

    private void turn(ShepherdEntity she, int figure, int age, float partial) {
        if (figure == ShepherdProjection.MIMIC) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            // You turn, and it turns the same way: it faces you, so straight at it is straight back.
            model.head.yRot = Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partial) - she.projectionYaw() - 180) * Mth.DEG_TO_RAD, -MAX_LOOK, MAX_LOOK);
            model.head.xRot = player.getViewXRot(partial) * Mth.DEG_TO_RAD;
        } else if (figure == ShepherdProjection.ARCHITECT && ShepherdHologram.chin(age)) {
            model.rightArm.xRot = CHIN_ARM_X;
            model.rightArm.yRot = CHIN_ARM_Y;
            model.head.xRot = CHIN_HEAD_X;
        }
    }

    /** Static and bright streaks, over a figure that is already torn. */
    private static void overdraw(VertexConsumer flat, PoseStack pose, int age) {
        for (var bar : ShepherdHologram.bars(age)) {
            for (int x = bar.left(); x < bar.left() + bar.length(); x++) voxel(flat, pose, FIGURE_X + x, FIGURE_Y + bar.y(), FIGURE_Z + 2, GLOW, SOLID);
        }
        for (var fleck : ShepherdHologram.flecks(age)) voxel(flat, pose, FIGURE_X + fleck.x(), FIGURE_Y + fleck.y(), FIGURE_Z + fleck.z(), fleck.bright() ? BODY : HOLO, SEE_THROUGH);
    }

    private static void hooded(VertexConsumer flat, PoseStack pose, int age, int eggAge) {
        int band = ShepherdHologram.band(age);
        for (var cell : ShepherdHologram.hooded()) {
            if (cell.shade() != Shade.GLOW && ShepherdHologram.eggRowTorn(eggAge, cell.y())) continue;
            voxel(flat, pose, FIGURE_X + cell.x(), FIGURE_Y + cell.y(), FIGURE_Z + cell.z(), shaded(cell.shade(), cell.y(), band), SOLID);
        }
    }

    /** A voxel's green, under the same band and dim rows as the skins. */
    private static int shaded(Shade shade, int row, int band) {
        if (shade == Shade.GLOW || row == band) return GLOW;
        if (ShepherdHologram.dimRow(row)) return HOLO;
        return shade == Shade.HOLO ? HOLO : shade == Shade.HOLO_DARK ? HOLO_DARK : BODY;
    }

    /** One texel of light, at the mockup's coordinates. */
    private static void voxel(VertexConsumer buffer, PoseStack pose, int x, int y, int z, int rgb, int alpha) {
        float x0 = -(x + 1) * TEXEL, x1 = -x * TEXEL, y0 = y * TEXEL, y1 = (y + 1) * TEXEL, z0 = -(z + 1) * TEXEL, z1 = -z * TEXEL;
        int color = alpha << 24 | rgb;
        quad(buffer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, color, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(buffer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, color, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(buffer, pose, color, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(buffer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(VertexConsumer buffer, PoseStack pose, int color, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(buffer, pose, color, ax, ay, az, 0, 1);
        vertex(buffer, pose, color, bx, by, bz, 1, 1);
        vertex(buffer, pose, color, cx, cy, cz, 1, 0);
        vertex(buffer, pose, color, dx, dy, dz, 0, 0);
    }

    private static void vertex(VertexConsumer buffer, PoseStack pose, int color, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose.last(), x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
    }

    /** Once a tick at most: the skin as this age shows it. */
    private void paint(int figure, int age, int leaving) {
        if (figure == paintedFigure && age == paintedAge) return;
        if (!load(figure)) return;
        paintedFigure = figure;
        paintedAge = age;
        int body = figure == ShepherdProjection.HUNTER ? BODY : figure == ShepherdProjection.MIMIC ? HOLO : HOLO_DARK;
        int bright = figure == ShepherdProjection.HUNTER ? GLOW : BODY;
        boolean torn = figure == ShepherdProjection.ARCHITECT;
        int band = ShepherdHologram.band(age), tear = ShepherdHologram.tearRow(age), tearTop = tear + ShepherdHologram.tearWidth(age), gap = ShepherdHologram.gapRow(age);
        var out = painted.getPixels();
        for (int x = 0; x < SKIN; x++) {
            for (int y = 0; y < SKIN; y++) {
                int row = ShepherdHologram.row(x, y);
                boolean eye = ShepherdHologram.eye(x, y);
                if (!ShepherdHologram.formed(age, x, y) || ShepherdHologram.gone(leaving, x, y) || torn && !eye && (row == gap || row == gap + 1)) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                int from = torn && !eye && row >= tear && row < tearTop ? Math.floorMod(x + ShepherdHologram.tearShift(age), SKIN) : x;
                int abgr = source.getPixelRGBA(from % source.getWidth(), y % source.getHeight());
                if (abgr >>> 24 == 0) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                int shade = row == band ? bright : ShepherdHologram.dimRow(row) && !torn ? HOLO : body;
                out.setPixelRGBA(x, y, eye ? abgrOf(GLOW, SOLID) : abgrOf(lit(shade, abgr), SEE_THROUGH));
            }
        }
        painted.upload();
    }

    /** The skin's light and shade, in one of her greens. */
    private static int lit(int rgb, int abgr) {
        float light = (0.3F * (abgr & 0xFF) + 0.59F * (abgr >> 8 & 0xFF) + 0.11F * (abgr >> 16 & 0xFF)) / 255;
        float scale = DIM_SHADOWS + (1 - DIM_SHADOWS) * Math.min(1, light * LIGHT_GAIN + LIFT_LIGHT);
        return (int) ((rgb >> 16 & 0xFF) * scale) << 16 | (int) ((rgb >> 8 & 0xFF) * scale) << 8 | (int) ((rgb & 0xFF) * scale);
    }

    private static int abgrOf(int rgb, int alpha) {
        return alpha << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
    }

    /** False if the skin cannot be read, and then there is no figure to draw. */
    private boolean load(int figure) {
        if (painted == null) {
            painted = new DynamicTexture(SKIN, SKIN, false);
            Minecraft.getInstance().getTextureManager().register(PAINTED, painted);
        }
        if (figure == sourceFigure) return true;
        var skin = figure == ShepherdProjection.HUNTER ? HUNTER : figure == ShepherdProjection.MIMIC ? MIMIC : ARCHITECT;
        try (var in = Minecraft.getInstance().getResourceManager().open(skin)) {
            var image = NativeImage.read(in);
            if (source != null) source.close();
            source = image;
            sourceFigure = figure;
            return true;
        } catch (IOException e) {
            FrozenDawn.LOGGER.warn("No hologram skin at {}", skin, e);
            return false;
        }
    }
}
