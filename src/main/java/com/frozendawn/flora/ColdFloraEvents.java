package com.frozendawn.flora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Frostcap thawing in warm inventories, once a second, and natural Cold Flora near players from Phase 3 (see
 * {@link ColdFloraSpawner}). Chests are not ticked, so a cap kept in any chest stays as it was.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ColdFloraEvents {
    private ColdFloraEvents() { }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int t = server.getTickCount();
        if (t % 20 == 0) for (var player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (WorldTickHandler.getLastTemperature(player.getUUID()) > ColdFloraPolicy.FROSTCAP_DIES_ABOVE) warmInventory(player);
        }
        boolean groves = t % ColdFloraSpawner.GROVE_INTERVAL_TICKS == 0, caps = t % ColdFloraSpawner.CAP_INTERVAL_TICKS == 0,
                sightings = t % RimewoodSighting.INTERVAL_TICKS == 0;
        if ((groves || caps || sightings) && ApocalypseState.get(server).getPhase() >= 3) {
            var level = server.getLevel(Level.OVERWORLD);
            if (level != null) for (var player : level.players()) {
                if (player.isSpectator()) continue;
                if (sightings) RimewoodSighting.check(level, player);
                if (groves && level.random.nextInt(ColdFloraSpawner.GROVE_ONE_IN) == 0) ColdFloraSpawner.tryGrove(level, player.blockPosition());
                if (caps && level.random.nextInt(ColdFloraSpawner.CAP_ONE_IN) == 0) ColdFloraSpawner.tryFrostcap(level, player.blockPosition());
            }
        }
    }

    /** One warm second for every Frostcap stack the player carries. */
    public static void warmInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var next = warm(inv.getItem(i));
            if (next != null) inv.setItem(i, next);
        }
    }

    /** Adds a warm second; returns the replacement stack once it spoils a step, or null if unchanged in kind. */
    public static ItemStack warm(ItemStack stack) {
        boolean frozen = stack.is(ModItems.FROZEN_FROSTCAP.get());
        if (!frozen && !stack.is(ModItems.THAWED_FROSTCAP.get())) return null;
        int warm = stack.getOrDefault(ModDataComponents.FROSTCAP_WARMTH.get(), 0) + 1;
        if (warm < ColdFloraPolicy.THAW_SECONDS) { stack.set(ModDataComponents.FROSTCAP_WARMTH.get(), warm); return null; }
        return new ItemStack(frozen ? ModItems.THAWED_FROSTCAP.get() : ModItems.FROSTCAP_MUSH.get(), stack.getCount());
    }

    /** Places up to {@code count} caps at varied ages around {@code center} where they can stand. */
    public static int plant(ServerLevel level, BlockPos center, int count) {
        int placed = 0;
        var cap = ModBlocks.FROSTCAP.get().defaultBlockState();
        for (int attempt = 0; attempt < count * 6 && placed < count; attempt++) {
            var base = center.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, base);
            if (Math.abs(top.getY() - center.getY()) > 2 || !level.isEmptyBlock(top) || !FrostcapBlock.supported(level, top, Direction.UP)) continue;
            level.setBlock(top, cap.setValue(FrostcapBlock.AGE, level.random.nextInt(3)), 3);
            placed++;
        }
        return placed;
    }
}
