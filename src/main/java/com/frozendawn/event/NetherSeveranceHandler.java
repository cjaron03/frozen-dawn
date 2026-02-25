package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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

    private static boolean announced = false;

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
                }
                return;
            }
        }
    }

    /**
     * Called each server tick from WorldTickHandler.
     * Announces dimensional severance once and removes any existing portal blocks.
     */
    public static void tick(ServerLevel overworld, int phase) {
        if (phase < 5) {
            announced = false;
            return;
        }

        // Announce once when phase 5 begins
        if (!announced) {
            announced = true;
            for (ServerPlayer player : overworld.players()) {
                player.sendSystemMessage(
                        Component.translatable("message.frozendawn.nether_severed.announce"));
            }
        }

        // Remove portal blocks near players every 10 ticks (half second)
        if (overworld.getGameTime() % 10 == 0) {
            for (ServerPlayer player : overworld.players()) {
                removePortalBlocks(overworld, player.blockPosition());
            }
        }
    }

    private static void removePortalBlocks(ServerLevel level, BlockPos center) {
        int radius = 48;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void reset() {
        announced = false;
    }
}
