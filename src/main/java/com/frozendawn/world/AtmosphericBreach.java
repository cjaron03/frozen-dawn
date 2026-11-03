package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.AtmosphericBreachPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Short, local airflow. No instant damage, teleport, camera rotation, or permanent pin. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class AtmosphericBreach {
    public static final int DURATION_TICKS = 40;
    private record Burst(Set<BlockPos> cells, BlockPos hole, long started) {}
    private static final Map<ServerLevel, List<Burst>> BURSTS = new WeakHashMap<>();
    private AtmosphericBreach() {}

    public static void start(ServerLevel level, Set<BlockPos> cells, BlockPos hole) {
        var bursts = BURSTS.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (bursts.size() >= 32) return;
        bursts.add(new Burst(Set.copyOf(cells), hole.immutable(), level.getGameTime()));
        for (var player : level.players()) {
            if (contains(cells, player) && !player.isSpectator())
                PacketDistributor.sendToPlayer(player, new AtmosphericBreachPayload(hole));
        }
    }

    public static boolean contains(Set<BlockPos> cells, Entity entity) {
        return cells.contains(entity.blockPosition()) || cells.contains(BlockPos.containing(entity.getEyePosition()));
    }

    public static Vec3 impulse(Vec3 velocity, Vec3 toward, boolean braced, boolean item, float remaining) {
        if (toward.lengthSqr() < 0.04 || remaining <= 0) return Vec3.ZERO;
        var direction = toward.normalize();
        double cap = item ? 0.55 : 0.28;
        double force = (item ? 0.055 : 0.035) * remaining * (braced ? 0.25 : 1);
        force = Math.min(force, Math.max(0, cap - velocity.dot(direction)));
        var result = direction.scale(force);
        return new Vec3(result.x, Math.max(-0.025, Math.min(0.025, result.y)), result.z);
    }

    public static void tickLevel(ServerLevel level) {
        var bursts = BURSTS.get(level); if (bursts == null) return;
        for (var iterator = bursts.iterator(); iterator.hasNext();) {
            var burst = iterator.next();
            long age = level.getGameTime()-burst.started;
            if (age >= DURATION_TICKS || !CombustionAtmosphere.isVacuum(level)
                    || !level.isLoaded(burst.hole) || !RoomAtmosphere.isPassage(level, burst.hole, level.getBlockState(burst.hole))) {
                iterator.remove(); continue;
            }
            float remaining = 1 - age/(float)DURATION_TICKS;
            var target = burst.hole.getCenter();
            // A 12-block neighborhood is intentionally smaller than the room search bound.
            for (var entity : level.getEntities((Entity)null, new AABB(burst.hole).inflate(12),
                    e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
                if (!contains(burst.cells, entity) || entity.isSpectator()
                        || entity instanceof ServerPlayer player && player.isCreative()) continue;
                var from = entity.getEyePosition();
                if (level.clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
                        .getType() != HitResult.Type.MISS) continue;
                Vec3 push = impulse(entity.getDeltaMovement(), target.subtract(from), entity.isShiftKeyDown() && entity.onGround(),
                        entity instanceof ItemEntity, remaining);
                entity.setDeltaMovement(entity.getDeltaMovement().add(push));
                entity.hurtMarked = true;
                if (entity instanceof ServerPlayer player)
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                if (age % 4 == 0 && push.lengthSqr() > 0)
                    level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 0,
                            push.x*8, push.y*8, push.z*8, 1);
            }
            if (age % 4 == 0) {
                var outward = Vec3.ZERO;
                for (var direction : net.minecraft.core.Direction.values())
                    if (burst.cells.contains(burst.hole.relative(direction)))
                        outward = outward.subtract(Vec3.atLowerCornerOf(direction.getNormal()));
                if (outward.lengthSqr() > 0) outward = outward.normalize();
                for (int i = 0; i < 3; i++) level.sendParticles(ParticleTypes.CLOUD,
                        target.x + (level.random.nextDouble()-0.5)*0.4,
                        target.y + (level.random.nextDouble()-0.5)*0.4,
                        target.z + (level.random.nextDouble()-0.5)*0.4,
                        0, outward.x, outward.y, outward.z, 0.18*remaining);
            }
        }
        if (bursts.isEmpty()) BURSTS.remove(level);
    }
    public static int activeBursts(ServerLevel level) { return BURSTS.getOrDefault(level, List.of()).size(); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { tickLevel(event.getServer().overworld()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { reset(); }
    public static void reset() { BURSTS.clear(); }
}
