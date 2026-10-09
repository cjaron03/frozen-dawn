package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PacifistSavedData;
import com.frozendawn.event.WorldTickHandler;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;

/** MACS §5: successful escape without a player-attributed Returned-lineage kill. */
public final class PacifistAdvancement {
    public static final String ID = "pacifist_launch";
    public static final TagKey<EntityType<?>> RETURNED_LINEAGE = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "returned_lineage"));

    private PacifistAdvancement() { }

    public static void recordDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide() || victim.getServer() == null
                || !victim.getType().is(RETURNED_LINEAGE)) return;
        UUID credited = playerId(victim.getKillCredit());
        if (credited == null) credited = playerId(source.getEntity());
        if (credited == null) credited = playerId(source.getDirectEntity());
        if (credited != null) PacifistSavedData.get(victim.getServer()).recordKill(credited);
    }

    private static UUID playerId(Entity actor) {
        if (actor instanceof ServerPlayer player) return player.getUUID();
        if (actor instanceof TamableAnimal pet) return pet.getOwnerUUID();
        if (actor instanceof Projectile projectile) return playerId(projectile.getOwner());
        if (actor instanceof PrimedTnt tnt) return playerId(tnt.getOwner());
        return null;
    }

    public static boolean eligible(ServerPlayer player) {
        var data = PacifistSavedData.get(player.server);
        if (data.disqualified(player.getUUID())) return false;
        // Existing per-type vanilla statistics seed old saves; no new counter starts them at zero.
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.is(RETURNED_LINEAGE)
                    && player.getStats().getValue(Stats.ENTITY_KILLED.get(type)) > 0) {
                data.recordKill(player.getUUID());
                return false;
            }
        }
        return true;
    }

    /** Only called for actual passengers at the authoritative successful-launch milestone. */
    public static void onSuccessfulLaunch(ServerPlayer player) {
        if (eligible(player)) WorldTickHandler.grantAdvancement(player, ID);
    }
}
