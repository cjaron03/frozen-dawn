package com.frozendawn.defense;

import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.radiation.ReactorAdvancements;
import com.frozendawn.world.CombustionAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/**
 * The LC-4's firing tick, server only. Each tick it cuts everything between last tick's aim line and
 * this one, so the beam draws a line through the room instead of a dot.
 */
public final class SeverLash {
    public static final int DRAW_WATTS = 25_000;
    public static final int JOULES_PER_TICK = DRAW_WATTS / 20;
    public static final double REACH = 12;
    /** Six damage a second. */
    public static final float DAMAGE_PER_TICK = 0.3F;
    /** Hostiles cut in one sweep for the Hellwhip advancement. */
    public static final int HELLWHIP_HOSTILES = 3;
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1F, .22F, .12F), .6F);
    private record Aim(Vec3 direction, long tick) {}
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
        var last = AIMS.put(player.getUUID(), new Aim(look, now));
        var from = last != null && last.tick() == now - 1 ? last.direction() : null;
        var candidates = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable());
        var cut = new LinkedHashSet<LivingEntity>();
        Vec3 stop = eye;
        BlockHitResult block = null;
        for (var ray : BeamSweep.rays(from, look, REACH)) {
            var end = eye.add(ray.scale(REACH));
            block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            for (var e : candidates) {
                var box = e.getBoundingBox().inflate(e.getPickRadius());
                if (box.contains(eye) || box.clip(eye, stop).isPresent()) cut.add(e);
            }
        }

        var source = level.damageSources().source(ModDamageTypes.SEVER_LASH, player);
        int hostiles = 0;
        for (var e : cut) if (e.hurt(source, DAMAGE_PER_TICK) && e instanceof Enemy) hostiles++;
        if (hostiles >= HELLWHIP_HOSTILES) ReactorAdvancements.grant(player, "hellwhip");
        draw(level, eye, stop, block != null && block.getType() != HitResult.Type.MISS, vacuum);
        if (shot.heat().lockedOut(now + 1)) {
            AIMS.remove(player.getUUID());
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .8F, .7F);
        }
        return true;
    }

    /** In vacuum nothing scatters the beam: it shows only where it lands. */
    private static void draw(ServerLevel level, Vec3 eye, Vec3 stop, boolean landed, boolean vacuum) {
        var path = stop.subtract(eye);
        double length = path.length();
        if (!vacuum) for (double d = 1; d < length; d += .5) {
            var at = eye.add(path.scale(d / length));
            level.sendParticles(BEAM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (landed) level.sendParticles(BEAM, stop.x, stop.y, stop.z, 3, .05, .05, .05, 0);
    }

    public static void forget(UUID player) { AIMS.remove(player); }
}
