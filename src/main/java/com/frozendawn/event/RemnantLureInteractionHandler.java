package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.RemnantLureSavedData;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModToolTiers;
import com.frozendawn.world.remnant.RemnantLureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionResult;

import java.util.HashMap;
import java.util.Map;

/** Player trust and explicit escape inputs for authored Remnant shelters. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RemnantLureInteractionHandler {
    private static final Map<Long, HitProgress> SEAM_HITS = new HashMap<>();

    private RemnantLureInteractionHandler() {
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        var record = RemnantLureSavedData.get(level.getServer()).at(event.getPos());
        boolean authoredEntrance = record.isPresent()
                && record.get().membranePositions().contains(event.getPos())
                && level.getBlockState(event.getPos()).getBlock() instanceof DoorBlock;
        if (authoredEntrance) {
            if (record.get().state().locksShelter()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            // Opening the refuge is not trust. Crossing its threshold is.
            return;
        }
        RemnantLureManager.commitAt(level, event.getPos(), player);
    }

    @SubscribeEvent
    public static void onStrike(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        var lure = RemnantLureSavedData.get(level.getServer()).at(event.getPos());
        RemnantLureManager.commitAt(level, event.getPos(), player);
        if (!level.getBlockState(event.getPos()).is(ModBlocks.REMNANT_SEAM.get())) return;
        event.setCanceled(true);
        long now = level.getGameTime();
        HitProgress progress = SEAM_HITS.compute(event.getPos().asLong(), (key, old) ->
                old == null || now - old.lastHit > 80 ? new HitProgress(0, now) : old);
        progress.hits += acheronite(player) ? 3 : 1;
        progress.lastHit = now;
        if (progress.hits >= 6) {
            level.destroyBlock(event.getPos(), false, player);
            lure.ifPresent(record -> RemnantLureManager.interruptWallLatch(level, record.id()));
            SEAM_HITS.remove(event.getPos().asLong());
        }
    }

    @SubscribeEvent
    public static void preventOrdinaryBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        boolean lockedLure = RemnantLureSavedData.get(level.getServer()).at(event.getPos())
                .map(record -> record.state().locksShelter()).orElse(false);
        if (lockedLure || level.getBlockState(event.getPos()).is(ModBlocks.REMNANT_SEAM.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        RemnantLureManager.commitAt(player.serverLevel(), event.getPos(), player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        clearStaleGrab(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearStaleGrab(event.getEntity());
    }

    private static void clearStaleGrab(net.minecraft.world.entity.player.Player player) {
        if (!player.getPersistentData().getBoolean(RemnantEntity.GRAB_MARKER)) return;
        player.getPersistentData().remove(RemnantEntity.GRAB_MARKER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    public static void tickFireEscape(ServerLevel level,
                                      RemnantLureSavedData.LureRecord record) {
        if (!record.state().isCommitted()) return;
        for (BlockPos seam : record.seams()) {
            if (!level.getBlockState(seam).is(ModBlocks.REMNANT_SEAM.get())) continue;
            boolean burning = BlockPos.betweenClosedStream(seam.offset(-1, -1, -1),
                            seam.offset(1, 1, 1))
                    .anyMatch(pos -> level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE));
            if (burning) {
                HitProgress progress = SEAM_HITS.computeIfAbsent(seam.asLong(),
                        key -> new HitProgress(0, level.getGameTime()));
                progress.hits++;
                if (progress.hits >= 40) {
                    level.removeBlock(seam, false);
                    RemnantLureManager.interruptWallLatch(level, record.id());
                    SEAM_HITS.remove(seam.asLong());
                }
            }
        }
    }

    private static boolean acheronite(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.SOUL_HARVEST_BLADE.get())
                || player.getMainHandItem().getItem() instanceof TieredItem tiered
                && tiered.getTier() == ModToolTiers.ACHERONITE;
    }

    private static final class HitProgress {
        private int hits;
        private long lastHit;
        private HitProgress(int hits, long lastHit) { this.hits = hits; this.lastHit = lastHit; }
    }
}
