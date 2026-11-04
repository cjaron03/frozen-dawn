package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Terminus;
import com.frozendawn.init.ModParticles;
import com.frozendawn.mixin.CompositeRenderTypeAccessor;
import com.frozendawn.mixin.CompositeStateAccessor;
import com.frozendawn.mixin.RenderStateShardAccessor;
import com.frozendawn.mixin.TextureStateShardInvoker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;

/**
 * A mind the Terminus stops does not fall. It stands still and greys for half a second, then crumbles from the head
 * down into grey flakes that drift up and off to the operator's right, paling as they go (owner's mockup,
 * docs/art/terminus/stop-mockup.png). The server holds the body for {@link Terminus#DEATH_TICKS}.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class TerminusCrumble {
    public static final int GREY_TICKS = 10, CRUMBLE_START = 10, CRUMBLE_TICKS = 20;
    /** Each texel goes when the crumble passes its height plus up to a quarter of the body; see terminus_crumble.fsh. */
    public static final float RAGGED = .25f;

    private static final class Body {
        int age;
        final Vec3 drift;
        Body(Vec3 drift) { this.drift = drift; }
    }
    private static final Map<LivingEntity, Body> BODIES = new WeakHashMap<>();
    private static ShaderInstance shader;
    private static boolean rendering;

    public static void setShader(ShaderInstance instance) { shader = instance; }

    /** How grey the body is, 0 to 1, {@code age} ticks after the discharge. */
    public static float grey(float age) { return Mth.clamp(age / GREY_TICKS, 0, 1); }

    /** How far the crumble has come, 0 to 1 + RAGGED: a texel at height {@code h} from the top, with noise n, goes once h + n * RAGGED is below it. */
    public static float crumbled(float age) {
        // Capped: it goes to the shader as a byte, and past its top it would wrap round and bring the body back.
        return Mth.clamp((age - CRUMBLE_START) / CRUMBLE_TICKS, 0, 1) * (1 + RAGGED);
    }

    public static void start(LivingEntity body, Entity operator) {
        float yaw = operator == null ? 0 : operator.getYRot() * Mth.DEG_TO_RAD;
        BODIES.put(body, new Body(new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw))));
        Terminus.crumbling(body, true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            BODIES.keySet().forEach(body -> Terminus.crumbling(body, false));
            BODIES.clear();
            return;
        }
        if (mc.isPaused()) return;
        var it = BODIES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var body = entry.getKey();
            var state = entry.getValue();
            // Held until the server takes the body: let go any sooner and vanilla draws its fall and red flush, crumbled
            // or not. The limit is only for a body the server never removes.
            if (body.isRemoved() || body.level() != mc.level || state.age > Terminus.DEATH_TICKS + 40) {
                Terminus.crumbling(body, false);
                it.remove();
                continue;
            }
            // It stands: with no death clock the renderer neither tips it over nor flushes it red.
            body.deathTime = 0;
            float from = crumbled(state.age), to = crumbled(++state.age);
            if (to > from) flakes(mc.level, body, state, from, to);
        }
    }

    /** About one flake for every texel that went this tick, from the band the crumble just crossed. */
    private static void flakes(ClientLevel level, LivingEntity body, Body state, float from, float to) {
        var r = body.getRandom();
        float width = body.getBbWidth(), height = body.getBbHeight();
        // A husk lets go of about 140 over its crumble, the mockup's density of flakes in the air.
        int count = Math.min(24, Mth.ceil(width * height * 120 * (to - from) / (1 + RAGGED)));
        double top = body.getY() + height;
        for (int i = 0; i < count; i++) {
            float at = Mth.clamp(Mth.lerp(r.nextFloat(), from, to) - r.nextFloat() * RAGGED, 0, 1);
            double x = body.getX() + (r.nextDouble() - .5) * width, z = body.getZ() + (r.nextDouble() - .5) * width;
            double drift = .025 + r.nextDouble() * .03;
            level.addParticle(ModParticles.TERMINUS_FLAKE.get(), x, top - at * height, z,
                    state.drift.x * drift, .02 + r.nextDouble() * .025, state.drift.z * drift);
        }
    }

    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?, ?> event) {
        if (rendering || shader == null) return;
        var body = event.getEntity();
        var state = BODIES.get(body);
        if (state == null) return;
        event.setCanceled(true);
        float age = state.age + event.getPartialTick(), height = body.getBbHeight();
        var feet = event.getPoseStack().last().pose().transformPosition(new Vector3f());
        var source = new Crumbling(event.getMultiBufferSource(), feet.y + height, height, grey(age), crumbled(age));
        rendering = true;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            var renderer = (LivingEntityRenderer) event.getRenderer();
            renderer.render(body, Mth.lerp(event.getPartialTick(), body.yRotO, body.getYRot()), event.getPartialTick(),
                    event.getPoseStack(), source, event.getPackedLight());
        } finally {
            rendering = false;
        }
    }

    /**
     * Sends a stopped body's model, armor and held items to the crumble shader on their own textures. Its vertex
     * color carries the crumble (see terminus_crumble.vsh); a glint has nothing to crumble and is left off.
     */
    private record Crumbling(MultiBufferSource to, float top, float height, float grey, float crumbled) implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType type) {
            var name = ((RenderStateShardAccessor) type).frozendawn$name();
            if (name.contains("glint")) return Nothing.INSTANCE;
            if (type instanceof CompositeRenderTypeAccessor composite
                    && (name.startsWith("entity") || name.startsWith("armor") || name.startsWith("item_entity") || name.equals("eyes"))) {
                var textureState = ((CompositeStateAccessor) (Object) composite.frozendawn$state()).frozendawn$textureState();
                var texture = ((TextureStateShardInvoker) textureState).frozendawn$cutoutTexture();
                if (texture.isPresent())
                    return new Vertices(to.getBuffer(Type.of(texture.get())), top, height,
                            (int) (crumbled / (1 + RAGGED) * 255), (int) (grey * 255));
            }
            return to.getBuffer(type);
        }
    }

    private static final class Vertices implements VertexConsumer {
        private final VertexConsumer to;
        private final float top, height;
        private final int crumbled, grey;
        private float y;

        Vertices(VertexConsumer to, float top, float height, int crumbled, int grey) {
            this.to = to; this.top = top; this.height = height; this.crumbled = crumbled; this.grey = grey;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { this.y = y; to.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            to.setColor((int) (Mth.clamp((top - y) / height, 0, 1) * 255), crumbled, grey, alpha);
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { to.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { to.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { to.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { to.setNormal(x, y, z); return this; }
    }

    private enum Nothing implements VertexConsumer {
        INSTANCE;
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static final class Type extends RenderType {
        private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture -> create(
                "frozendawn_terminus_crumble", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));

        static RenderType of(ResourceLocation texture) { return TYPES.apply(texture); }

        private Type(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                     Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    private TerminusCrumble() { }
}
