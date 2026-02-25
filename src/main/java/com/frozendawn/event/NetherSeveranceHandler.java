package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * At phase 5+, dimensional lines are severed — the Nether becomes unreachable.
 * Existing portals break and new ones cannot be created.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class NetherSeveranceHandler {

    /**
     * Prevent dimension travel to the Nether at phase 5+.
     */
    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (FrozenDawnPhaseTracker.getPhase() < 5) return;
        ResourceKey<Level> dest = event.getDimension();
        if (dest == Level.NETHER) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.nether_severed"), true);
            }
        }
    }

    /**
     * Prevent flint and steel / fire charge usage near obsidian at phase 5+.
     * This blocks portal creation at the source — no purple blocks ever appear.
     * Grants a hidden advancement on the first attempt.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (FrozenDawnPhaseTracker.getPhase() < 5) return;
        if (event.getLevel().dimension() != Level.OVERWORLD) return;

        var held = event.getItemStack();
        if (!held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE)) return;

        // Fire would be placed on the clicked face — check if that spot is next to obsidian
        BlockPos firePos = event.getPos().relative(event.getFace());
        for (Direction dir : Direction.values()) {
            if (event.getLevel().getBlockState(firePos.relative(dir)).is(Blocks.OBSIDIAN)) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.nether_severed"), true);
                    grantAdvancement(player, "nether_severed");
                }
                return;
            }
        }
    }

    private static boolean initialSweepDone = false;

    /**
     * Called each server tick from WorldTickHandler.
     * Removes existing portal blocks when phase 5 begins, then relies on
     * creation prevention (onRightClick) with a small safety scan as backup.
     */
    public static void tick(ServerLevel overworld, int phase) {
        if (phase < 5) {
            initialSweepDone = false;
            return;
        }

        // One-time sweep: remove all portals near players when entering phase 5
        if (!initialSweepDone) {
            for (ServerPlayer player : overworld.players()) {
                removePortalBlocks(overworld, player.blockPosition(), 48);
            }
            initialSweepDone = true;
            return;
        }

        // Safety net: small scan near players every 200 ticks (10 seconds)
        if (overworld.getGameTime() % 200 == 0) {
            for (ServerPlayer player : overworld.players()) {
                removePortalBlocks(overworld, player.blockPosition(), 8);
            }
        }
    }

    private static void removePortalBlocks(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 64);
        int maxY = Math.min(level.getMaxBuildHeight(), center.getY() + 64);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    public static void reset() {
        initialSweepDone = false;
    }
}
