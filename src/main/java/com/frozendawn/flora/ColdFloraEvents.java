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
 * Frostcap thawing in warm inventories, once a second, and wild Frostcap patches appearing on cold snow and ice near
 * players from Phase 3. Chests are not ticked, so a cap kept in any chest stays as it was.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ColdFloraEvents {
    private ColdFloraEvents() { }

    /** Every 10 s, each player has a 1 in 30 chance (about once in five minutes) to seed one patch 24-64 blocks away. */
    static final int SEED_INTERVAL_TICKS = 200, SEED_ONE_IN = 30, SEED_MIN = 24, SEED_MAX = 64, SEED_CLEAR_RADIUS = 16;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int t = server.getTickCount();
        if (t % 20 == 0) for (var player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (WorldTickHandler.getLastTemperature(player.getUUID()) > ColdFloraPolicy.FROSTCAP_DIES_ABOVE) warmInventory(player);
        }
        if (t % SEED_INTERVAL_TICKS == 0 && ApocalypseState.get(server).getPhase() >= 3) {
            var level = server.getLevel(Level.OVERWORLD);
            if (level != null) for (var player : level.players())
                if (!player.isSpectator() && level.random.nextInt(SEED_ONE_IN) == 0) seedNear(level, player.blockPosition());
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

    /** A wild patch of three to six caps on open snow or ice that is cold enough, if none grow within 16 blocks. */
    static boolean seedNear(ServerLevel level, BlockPos around) {
        var random = level.random;
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = SEED_MIN + random.nextInt(SEED_MAX - SEED_MIN + 1);
        int x = around.getX() + (int) Math.round(Math.cos(angle) * distance), z = around.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) return false;
        var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (!FrostcapBlock.supported(level, top, Direction.UP) || !level.isEmptyBlock(top)) return false;
        if (ColdFlora.temperature(level, top) > ColdFloraPolicy.GROW_AT_OR_BELOW) return false;
        for (var p : BlockPos.betweenClosed(top.offset(-SEED_CLEAR_RADIUS, -4, -SEED_CLEAR_RADIUS), top.offset(SEED_CLEAR_RADIUS, 4, SEED_CLEAR_RADIUS)))
            if (level.getBlockState(p).is(ModBlocks.FROSTCAP.get())) return false;
        return plant(level, top, 3 + random.nextInt(4)) > 0;
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
