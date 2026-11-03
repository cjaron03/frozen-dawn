package com.frozendawn.defense;

import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.network.LashBeamPayload;
import com.frozendawn.radiation.ReactorAdvancements;
import com.frozendawn.world.CombustionAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The LC-4's firing tick, server only. Each tick it cuts everything between last tick's aim line and
 * this one, so the beam draws a line through the room instead of a dot.
 */
public final class SeverLash {
    public static final int DRAW_WATTS = 25_000;
    public static final int JOULES_PER_TICK = DRAW_WATTS / 20;
    public static final double REACH = 12;
    /** Twelve damage a second: double the spec's 6, by the owner's playtest call. */
    public static final float DAMAGE_PER_TICK = 0.6F;
    /** Hostiles cut in one continuous beam for the Hellwhip advancement. */
    public static final int HELLWHIP_HOSTILES = 3;
    /** A held cut sizzles every quarter second. */
    public static final int SIZZLE_TICKS = 5;
    /** One continuous beam: its last aim, the hostiles it has cut so far and when it last sizzled. */
    private record Aim(Vec3 direction, long tick, Set<UUID> hostiles, long sizzled) {}
    private static final Map<UUID, Aim> AIMS = new HashMap<>();
    private SeverLash() {}

    public static boolean fire(ServerPlayer player, ItemStack lash, long now) {
        if (!player.isAlive() || player.isSpectator() || !lash.is(ModItems.SEVER_LASH)) return false;
        var level = player.serverLevel();
        var eye = player.getEyePosition();
        boolean vacuum = !CombustionAtmosphere.canBurnAt(level, BlockPos.containing(eye));
        var shot = lash.getOrDefault(ModDataComponents.LASH_HEAT.get(), LashHeat.COOL).fire(now, vacuum);
        if (!shot.fired() || !PowerCellItem.pay(player, JOULES_PER_TICK)) { AIMS.remove(player.getUUID()); return false; }
        lash.set(ModDataComponents.LASH_HEAT.get(), shot.heat());
        player.getInventory().setChanged();

        var look = player.getLookAngle();
        var last = AIMS.get(player.getUUID());
        boolean continuing = last != null && last.tick() == now - 1;
        var from = continuing ? last.direction() : null;
        var hostiles = continuing ? last.hostiles() : new HashSet<UUID>();
        long sizzled = continuing ? last.sizzled() : now - SIZZLE_TICKS;
        var candidates = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable());
        var cut = new LinkedHashSet<LivingEntity>();
        Vec3 stop = eye;
        BlockHitResult block = null;
        var landed = new LinkedHashMap<BlockPos, net.minecraft.core.Direction>();
        for (var ray : BeamSweep.rays(from, look, REACH)) {
            var end = eye.add(ray.scale(REACH));
            block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            if (block.getType() != HitResult.Type.MISS) landed.putIfAbsent(block.getBlockPos(), block.getDirection());
            for (var e : candidates) {
                var box = e.getBoundingBox().inflate(e.getPickRadius());
                if (box.contains(eye) || box.clip(eye, stop).isPresent()) cut.add(e);
            }
        }

        var source = level.damageSources().source(ModDamageTypes.SEVER_LASH, player);
        for (var e : cut) if (e.hurt(source, DAMAGE_PER_TICK)) {
            sear(e);
            if (e instanceof Enemy) hostiles.add(e.getUUID());
        }
        if (hostiles.size() >= HELLWHIP_HOSTILES) ReactorAdvancements.grant(player, "hellwhip");
        LashWorldEffects.land(level, player, landed, now);
        boolean lands = block != null && block.getType() != HitResult.Type.MISS;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, LashBeamPayload.of(player.getId(), look.toVector3f(),
                (float) stop.distanceTo(eye), shot.heat().glow(now), lands, vacuum));
        if (!continuing) level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SEVER_LASH_START.get(), SoundSource.PLAYERS, .9F, 1);
        if ((lands || !cut.isEmpty()) && now - sizzled >= SIZZLE_TICKS) {
            var at = cut.isEmpty() ? stop : cut.iterator().next().getBoundingBox().getCenter();
            level.playSound(null, at.x, at.y, at.z, ModSounds.SEVER_LASH_SIZZLE.get(), SoundSource.PLAYERS, .7F, .9F + level.random.nextFloat() * .2F);
            sizzled = now;
        }
        AIMS.put(player.getUUID(), new Aim(look, now, hostiles, sizzled));
        if (shot.heat().lockedOut(now + 1)) {
            AIMS.remove(player.getUUID());
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SEVER_LASH_VENT.get(), SoundSource.PLAYERS, 1, 1);
        }
        return true;
    }

    private static void sear(LivingEntity target) {
        if (!target.isAlive()) return;
        var held = target.getEffect(ModEffects.SEARED);
        int duration = Sear.duration(held == null ? 0 : held.getDuration());
        target.addEffect(new MobEffectInstance(ModEffects.SEARED, duration, Sear.amplifier(duration, held == null ? -1 : held.getAmplifier())));
    }

    public static void forget(UUID player) { AIMS.remove(player); }
}
