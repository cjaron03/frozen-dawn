package com.frozendawn.maeve;

import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/** Local sensing only. No inventory, player-health, target-memory, or geometry inference. */
final class ObservationCollector {
    static final double RANGE = 48.0D;
    static final int MAX_LOCAL_CANDIDATES = 16;

    private ObservationCollector() { }

    static boolean canObserve(ArchitectEntity observer, ServerPlayer player, boolean fatalHit) {
        return canObserve(observer, player, fatalHit, false);
    }

    static boolean canObserve(ArchitectEntity observer, ServerPlayer player, boolean fatalHit, boolean fatalPlayer) {
        if (!(observer.level() instanceof ServerLevel level) || player.level() != level
                || (!fatalPlayer && !player.isAlive()) || player.isCreative() || player.isSpectator()
                || observer.isRemoved() || observer.isNoAi() || (!fatalHit && !observer.isAlive())
                || observer.isMasterArchitectVisual() || AggregateReinforcementManager.isChild(observer)
                || observer.distanceToSqr(player) > RANGE * RANGE) return false;
        // A ray crossing an unloaded chunk must not turn observation into chunk loading.
        int minX = Math.min(observer.blockPosition().getX(), player.blockPosition().getX()) >> 4;
        int maxX = Math.max(observer.blockPosition().getX(), player.blockPosition().getX()) >> 4;
        int minZ = Math.min(observer.blockPosition().getZ(), player.blockPosition().getZ()) >> 4;
        int maxZ = Math.max(observer.blockPosition().getZ(), player.blockPosition().getZ()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return observer.hasLineOfSight(player);
    }

    static void damage(BeliefStore store, MissionPlanner missions, ArchitectEntity observer, DamageSource source, float damage, long now) {
        if (!(damage > 0.0F) || !Float.isFinite(damage)
                || !(source.getEntity() instanceof ServerPlayer player)
                || !canObserve(observer, player, true)) return;
        boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE);
        boolean melee = source.getDirectEntity() == player && source.getMsgId().equals("player");
        if (!ranged && !melee) return;
        record(store, missions, observer, player, player.blockPosition(), now, BeliefStore.RANGED, ranged,
                ranged ? "WITNESSED_PROJECTILE_DAMAGE" : "WITNESSED_MELEE_DAMAGE");
        sword(store, missions, observer, player, source, now, false);
    }

    static void sword(BeliefStore store, MissionPlanner missions, ArchitectEntity observer, ServerPlayer player,
                      DamageSource source, long now, boolean blocked) {
        boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
        boolean melee = source.getDirectEntity() == player && source.getMsgId().equals("player");
        if ((!projectile && !melee) || !canObserve(observer, player, true)) return;
        boolean sword = melee && player.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS);
        // Absence of sword evidence stays unknown; do not create a negative-only profile for every arrow.
        if (!sword && store.snapshot(player.getUUID(), now).stream().noneMatch(b -> b.pattern().equals(BeliefStore.SWORD))) return;
        String action = blocked ? "WITNESSED_SHIELD_BLOCK_" : "WITNESSED_DAMAGE_";
        action += sword ? "SWORD" : projectile ? "PROJECTILE" : "NON_SWORD_MELEE";
        if (melee) action += " weapon=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        record(store, missions, observer, player, player.blockPosition(), now, BeliefStore.SWORD, sword, action);
    }

    static void record(BeliefStore store, MissionPlanner missions, ArchitectEntity observer, ServerPlayer player,
                       BlockPos position, long now, String pattern, boolean supporting, String action) {
        var mission = missions.observing(observer, player, now);
        double weight = supporting ? mission == null ? BeliefPolicy.SUPPORT : BeliefPolicy.RECON_SUPPORT
                : -BeliefPolicy.CONTRADICTION;
        store.record(player.getUUID(), observer.getUUID(), player.level().dimension().location().toString(),
                position, now, pattern, supporting, action + (mission == null ? "" : " reconMission=" + mission.id()), weight);
    }

    static boolean restorative(ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (!stack.is(Items.POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (var effect : contents.getAllEffects()) {
            if (effect.is(MobEffects.HEAL) || effect.is(MobEffects.REGENERATION)) return true;
        }
        return false;
    }

    static void recovery(BeliefStore store, MissionPlanner missions, ServerPlayer player, ItemStack consumed, long now) {
        if (!restorative(consumed) || player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        ArrayList<ArchitectEntity> candidates = new ArrayList<>();
        player.serverLevel().getEntities(EntityTypeTest.forClass(ArchitectEntity.class),
                player.getBoundingBox().inflate(RANGE), candidate -> !candidate.isMasterArchitectVisual(), candidates, MAX_LOCAL_CANDIDATES);
        // Bound expensive LOS checks. Shared encounter deduplication gives one contribution,
        // while retaining other witnesses for contact continuity if the first one dies.
        candidates.sort(Comparator.comparing((ArchitectEntity actor) -> missions.observing(actor, player, now) == null)
                .thenComparing(Entity::getUUID));
        for (ArchitectEntity observer : candidates) {
            if (!canObserve(observer, player, false)) continue;
            boolean covered = !player.serverLevel().canSeeSky(player.blockPosition());
            record(store, missions, observer, player, player.blockPosition(), now, BeliefStore.RECOVERY, covered,
                    covered ? "RECOVERY_ITEM_FINISHED_UNDER_COVER" : "RECOVERY_ITEM_FINISHED_OPEN_SKY");
        }
    }

    static boolean refreshContacts(MinecraftServer server, BeliefStore store, long now) {
        boolean changed = false;
        // At most 128 profiles x 8 UUID lookups and LOS checks, once per second.
        for (BeliefStore.Contact contact : store.contacts()) {
            ServerPlayer player = server.getPlayerList().getPlayer(contact.player());
            if (player == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(contact.dimension())));
            if (level != null && level.getEntity(contact.observer()) instanceof ArchitectEntity observer
                    && canObserve(observer, player, false)) changed |= store.contact(contact.player(), now);
        }
        return changed;
    }
}
