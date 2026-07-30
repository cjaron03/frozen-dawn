package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.LastWitnessItem;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import com.frozendawn.world.ThaeIvenMindDimension;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Spends one of The Last Witness's three memories to deny a death. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class LastWitnessHandler {
    private LastWitnessHandler() {
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || isFoldDeath(player)
                || !hasMemoryInOffhand(player)) {
            return;
        }

        event.setCanceled(true);
        ServerLevel originLevel = player.serverLevel();
        Vec3 origin = player.position();
        boolean hasAssignedSpawn = player.getRespawnPosition() != null;
        // In 1.21.1 this argument is keepInventory. Respawn anchors consume a charge
        // only when it is false, so a denied death deliberately passes true.
        boolean keepInventoryForRespawnLookup = true;
        DimensionTransition spawn = player.findRespawnPositionAndUseSpawnBlock(
                keepInventoryForRespawnLookup, DimensionTransition.DO_NOTHING);
        boolean worldSpawnFallback = !hasAssignedSpawn || spawn.missingRespawnBlock();
        ServerLevel arrivalLevel = spawn.newLevel();
        Vec3 arrival = spawn.pos();
        ItemStack relic = player.getOffhandItem();
        player.setItemSlot(EquipmentSlot.OFFHAND,
                LastWitnessItem.consumeMemory(relic));
        player.setHealth(Math.max(8.0F, player.getMaxHealth() * 0.4F));
        player.clearFire();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        originLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                origin.x, origin.y + 1.0D, origin.z,
                72, 0.65D, 1.0D, 0.65D, 0.08D);
        player.teleportTo(arrivalLevel,
                arrival.x, arrival.y, arrival.z,
                spawn.yRot(), spawn.xRot());
        SuitIntegrityHandler.stabilizeAfterRescue(
                player, worldSpawnFallback ? 1.0F : 0.35F);
        PlayerTickHandler.stabilizeAfterRescue(player);
        arrivalLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                arrival.x, arrival.y + 1.0D, arrival.z,
                88, 0.8D, 1.2D, 0.8D, 0.10D);
        arrivalLevel.sendParticles(ParticleTypes.END_ROD,
                arrival.x, arrival.y + 1.0D, arrival.z,
                42, 0.55D, 0.9D, 0.55D, 0.05D);
        PacketDistributor.sendToPlayer(
                player, HearthBoundaryEffectPayload.lastWitnessRescue());
        WorldTickHandler.grantAdvancement(player, "you_are_remembered");
    }

    private static boolean isFoldDeath(ServerPlayer player) {
        return ThaeIvenMindDimension.isMindLevel(player.level())
                || ThaeIvenMindDimension.hasStoredOrigin(player);
    }

    private static boolean hasMemoryInOffhand(ServerPlayer player) {
        return player.getOffhandItem().is(ModItems.THE_LAST_WITNESS.get())
                && LastWitnessItem.hasMemory(player.getOffhandItem());
    }
}
