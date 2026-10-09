package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Local execution cue only: mining a visible owned screen does not create a belief. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ArchitectMantletEvents {
    private ArchitectMantletEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMiningStarted(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        var level = player.serverLevel(); var pos = event.getPos();
        // The event precedes vanilla's reach/protection checks. Reject attempts
        // vanilla cannot accept before notifying any nearby executor.
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)
                || !player.canInteractWithBlock(pos, 1.0) || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return;
        var state = level.getBlockState(pos);
        if (!state.is(Blocks.PACKED_ICE) || state.getDestroyProgress(player, level, pos) <= 0) return;
        var nearby = new ArrayList<ArchitectEntity>();
        level.getEntities(EntityTypeTest.forClass(ArchitectEntity.class), new AABB(pos).inflate(12),
                actor -> actor.isAlive() && !actor.isNoAi() && !actor.isMasterArchitectVisual(), nearby, 16);
        for (var actor : nearby) actor.onMantletMiningStarted(pos);
    }
}
