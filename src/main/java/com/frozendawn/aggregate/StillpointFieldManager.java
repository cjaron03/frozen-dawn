package com.frozendawn.aggregate;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import com.frozendawn.network.StillpointFieldPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Loaded-world authority for the Stillpoint charge, purge, and sanctuary shell. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class StillpointFieldManager {
    public static final int CHARGE_TICKS = 80;
    private static final double BOUNDARY_MARGIN = 3.0D;
    private static final String LAST_RIPPLE_TICK = "frozendawnStillpointRippleTick";
    private static final TagKey<EntityType<?>> REPELLED = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "stillpoint_repelled"));
    private static final Map<ResourceKey<Level>, Integer> pulseSequences = new HashMap<>();

    private StillpointFieldManager() {
    }

    public static void announceCharge(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.STILLPOINT_CHARGE.get(),
                SoundSource.BLOCKS, 2.4F, 0.82F);
        syncAll(level.getServer());
    }

    public static void tick(MinecraftServer server) {
        AggregateSavedData data = AggregateSavedData.get(server);
        BlockPos center = data.stillpointPos().orElse(null);
        ResourceLocation dimension = data.stillpointDimension().orElse(null);
        if (center == null || dimension == null) return;
        ServerLevel level = server.getLevel(ResourceKey.create(
                Registries.DIMENSION, dimension));
        if (level == null) return;

        if (level.hasChunkAt(center)
                && !level.getBlockState(center).is(ModBlocks.INERT_CONVERGENCE_CORE.get())) {
            data.clearStillpoint(level, center);
            syncAll(server);
            return;
        }

        long elapsed = Math.max(0L, level.getGameTime() - data.stillpointChargeStart());
        if (!data.stillpointActive()) {
            if (StillpointPolicy.chargeComplete(
                    data.stillpointChargeStart(), level.getGameTime(), CHARGE_TICKS)) {
                data.activateStillpoint();
                activate(level, data, center);
            } else if (level.hasChunkAt(center)) {
                emitChargeParticles(level, center, elapsed);
                emitSkySignal(level, center, elapsed);
            }
            return;
        }

        if (!data.stillpointActivationProcessed()) activate(level, data, center);
        enforceBoundary(level, center, FrozenDawnConfig.STILLPOINT_RADIUS.get());
    }

    private static void activate(ServerLevel level, AggregateSavedData data, BlockPos center) {
        if (data.markStillpointActivationProcessed()) {
            level.playSound(null, center, ModSounds.STILLPOINT_FORM.get(),
                    SoundSource.BLOCKS, 3.2F, 0.76F);
            emitFormationParticles(level, center);
            purge(level, data, center, FrozenDawnConfig.STILLPOINT_RADIUS.get());
            broadcastPulse(level, center.getCenter(), 1.0F);
        }
        syncAll(level.getServer());
    }

    private static void purge(ServerLevel level, AggregateSavedData data,
                              BlockPos center, int radius) {
        AABB area = new AABB(center).inflate(radius + 1.0D);
        ServerPlayer owner = data.stillpointPlacer()
                .map(level.getServer().getPlayerList()::getPlayer).orElse(null);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                candidate -> isRepelled(candidate)
                        && candidate.distanceToSqr(center.getCenter()) <= radius * radius)) {
            DamageSource source = owner == null
                    ? level.damageSources().magic()
                    : level.damageSources().playerAttack(owner);
            mob.hurt(source, Math.max(1000.0F, mob.getMaxHealth() * 4.0F));
            if (mob.isAlive()) {
                mob.setHealth(0.0F);
                mob.die(source);
            }
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    18, 0.35D, 0.55D, 0.35D, 0.045D);
        }
    }

    private static void enforceBoundary(ServerLevel level, BlockPos center, int radius) {
        Vec3 centerPoint = center.getCenter();
        AABB area = new AABB(center).inflate(radius + BOUNDARY_MARGIN);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
                StillpointFieldManager::isRepelled)) {
            Vec3 current = mob.position();
            Vec3 previous = new Vec3(mob.xo, mob.yo, mob.zo);
            double currentDistance = current.distanceTo(centerPoint);
            if (StillpointPolicy.segmentEnters(center, previous, current, radius)) {
                Vec3 clamped = StillpointPolicy.clampOutside(center, previous, radius);
                mob.teleportTo(clamped.x, clamped.y, clamped.z);
                Vec3 outward = clamped.subtract(centerPoint).normalize();
                mob.setDeltaMovement(outward.scale(0.42D).add(0.0D, 0.08D, 0.0D));
                mob.getNavigation().stop();
                mob.setTarget(null);
                mob.hurt(level.damageSources().magic(), 6.0F);
                rippleFor(level, mob, clamped);
            } else if (currentDistance < radius - 0.25D) {
                DamageSource source = level.damageSources().magic();
                mob.hurt(source, Math.max(1000.0F, mob.getMaxHealth() * 4.0F));
                if (mob.isAlive()) {
                    mob.setHealth(0.0F);
                    mob.die(source);
                }
                rippleFor(level, mob, current);
            } else if (currentDistance < radius + BOUNDARY_MARGIN) {
                mob.setTarget(null);
                Vec3 outward = current.subtract(centerPoint).normalize();
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.35D)
                        .add(outward.scale(0.16D)));
                mob.getNavigation().moveTo(
                        current.x + outward.x * 8.0D,
                        current.y, current.z + outward.z * 8.0D, 1.15D);
            }
        }
    }

    private static void rippleFor(ServerLevel level, Entity entity, Vec3 point) {
        long now = level.getGameTime();
        long last = entity.getPersistentData().getLong(LAST_RIPPLE_TICK);
        if (now - last < 10L) return;
        entity.getPersistentData().putLong(LAST_RIPPLE_TICK, now);
        level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 0.4D, point.z,
                7, 0.18D, 0.35D, 0.18D, 0.025D);
        broadcastPulse(level, point, 0.55F);
    }

    private static void emitChargeParticles(ServerLevel level, BlockPos center, long elapsed) {
        if ((elapsed & 1L) != 0L) return;
        double progress = Math.min(1.0D, elapsed / (double) CHARGE_TICKS);
        int count = 2 + (int) Math.floor(progress * 5.0D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.getX() + 0.5D, center.getY() + 0.65D, center.getZ() + 0.5D,
                count, 2.4D, 1.2D, 2.4D, 0.035D);
    }

    private static void emitSkySignal(ServerLevel level, BlockPos center, long elapsed) {
        if (elapsed < CHARGE_TICKS - 12L || (elapsed & 1L) != 0L) return;
        double progress = (elapsed - (CHARGE_TICKS - 12L)) / 12.0D;
        int height = 8 + (int) Math.floor(progress * 40.0D);
        Vec3 origin = center.getCenter().add(0.0D, 0.55D, 0.0D);
        for (int y = 0; y <= height; y += 2) {
            double spread = 0.025D + y * 0.0012D;
            level.sendParticles(y % 4 == 0
                            ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    origin.x, origin.y + y, origin.z,
                    2, spread, 0.18D, spread, 0.012D);
        }
        level.sendParticles(ParticleTypes.FLASH,
                origin.x, origin.y + height, origin.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static void emitFormationParticles(ServerLevel level, BlockPos center) {
        Vec3 origin = center.getCenter();
        int radius = FrozenDawnConfig.STILLPOINT_RADIUS.get();
        for (int i = 0; i < 96; i++) {
            double angle = Math.PI * 2.0D * i / 96.0D;
            double y = origin.y + Math.sin(angle * 3.0D) * 1.4D;
            level.sendParticles(ParticleTypes.END_ROD,
                    origin.x + Math.cos(angle) * radius, y,
                    origin.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static boolean isRepelled(Entity entity) {
        return entity != null && entity.getType().is(REPELLED);
    }

    public static void handleFirstPlacement(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "the_world_held_back");
        var advancement = player.getServer().getAdvancements().get(id);
        if (advancement == null
                || player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        WorldTickHandler.grantAdvancement(player, "the_world_held_back");
        PacketDistributor.sendToPlayer(player,
                HearthBoundaryEffectPayload.stillpointFieldDiagnostic());
    }

    public static void broadcastPulse(ServerLevel level, Vec3 point, float strength) {
        pulseSequences.merge(level.dimension(), 1, Integer::sum);
        int sequence = pulseSequences.get(level.dimension());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(point) <= 256.0D * 256.0D) {
                PacketDistributor.sendToPlayer(player,
                        payload(level.getServer(), sequence, point, strength));
            }
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                payload(player.getServer(), pulseSequences.getOrDefault(
                        player.serverLevel().dimension(), 0), Vec3.ZERO, 0.0F));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    private static StillpointFieldPayload payload(MinecraftServer server, int sequence,
                                                   Vec3 pulse, float strength) {
        AggregateSavedData data = AggregateSavedData.get(server);
        BlockPos center = data.stillpointPos().orElse(BlockPos.ZERO);
        ResourceLocation dimension = data.stillpointDimension()
                .orElse(Level.OVERWORLD.location());
        return new StillpointFieldPayload(data.stillpointPos().isPresent(),
                data.stillpointActive(), dimension, center,
                FrozenDawnConfig.STILLPOINT_RADIUS.get(),
                data.stillpointChargeStart(), sequence,
                pulse.x, pulse.y, pulse.z, strength);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && isRepelled(event.getEntity())
                && StillpointPolicy.isSuppressed(level, event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public static void reset() {
        pulseSequences.clear();
    }
}
