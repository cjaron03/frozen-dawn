package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.IcarusCraterSitesPayload;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.radiation.RadiationHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** What a fresh Icarus crater does to whoever goes down into it: the activated ground adds to the radiation dose rate,
 * and for its first minutes the floor burns living things and items on it. Both fade with the ticks since the blast. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class IcarusCraterHazards {
    /** The bowl: inside the crater's radius and no higher than this above the core, roughly the old ground. */
    public static final double BOWL_ABOVE = 4;
    /** How far from a player in the bowl the floor's heat reaches things, so the scan stays near players. */
    private static final double BURN_REACH = 24;

    private IcarusCraterHazards() {}

    /** The crater's share of a player's dose rate, RU/s, before any lead lining. */
    public static double radiation(ServerPlayer p) {
        var l = p.serverLevel(); double rate = 0;
        for (var site : IcarusCraterData.get(l).sites()) {
            double d = distance(site, p.position());
            if (d >= 0) rate += IcarusBlast.craterRate(l.getGameTime() - site.blastTick(), d, site.radius());
        }
        return rate;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel l && l.getGameTime() % 20 == 0) second(l);
    }

    /** One second of the floor's heat. Public for GameTests. */
    public static void second(ServerLevel l) {
        var data = IcarusCraterData.get(l);
        if (data.sites().isEmpty()) return;
        if (data.prune(l.getGameTime())) sync(l);
        for (var site : data.sites()) {
            double heat = IcarusBlast.floorHeat(l.getGameTime() - site.blastTick());
            if (heat < IcarusBlast.FLOOR_BURNS_ABOVE) continue;
            var burned = new java.util.HashSet<Entity>();
            for (var p : l.players()) {
                if (distance(site, p.position()) < 0) continue;
                for (var e : l.getEntities(p, p.getBoundingBox().inflate(BURN_REACH), e -> e instanceof LivingEntity || e instanceof ItemEntity)) burned.add(e);
                burned.add(p);
            }
            for (var e : burned) if (e.onGround() && distance(site, e.position()) >= 0) burn(l, e, heat);
        }
    }

    private static void burn(ServerLevel l, Entity e, double heat) {
        if (e instanceof ServerPlayer p && RadiationHandler.immune(p)) return;
        if (e instanceof LivingEntity living) living.hurt(l.damageSources().hotFloor(), (float) (1 + 3 * heat));
        e.igniteForSeconds(e instanceof ItemEntity ? 3 : 2);
    }

    /** Horizontal distance from the site's centre for a point in its bowl, or -1 outside it. */
    private static double distance(IcarusCraterData.Site site, Vec3 pos) {
        var c = site.center();
        if (pos.y > c.getY() + BOWL_ABOVE) return -1;
        double d = Math.hypot(pos.x - (c.getX() + 0.5), pos.z - (c.getZ() + 0.5));
        return d <= site.radius() ? d : -1;
    }

    /** Tells everyone in the level which craters are still hot. */
    public static void sync(ServerLevel l) {
        var payload = new IcarusCraterSitesPayload(IcarusCraterData.get(l).sites());
        for (var p : l.players()) PacketDistributor.sendToPlayer(p, payload);
    }

    private static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new IcarusCraterSitesPayload(IcarusCraterData.get(p.serverLevel()).sites()));
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }
}
