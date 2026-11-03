package com.frozendawn.flora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.SaplingRimewoodState;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Life Finds a Way: a natural Rimewood trunk within 16 blocks and in clear sight. Sapling-grown and placed logs do not count. */
public final class RimewoodSighting {
    private RimewoodSighting() { }

    public static final String ADVANCEMENT = "life_finds_a_way";
    public static final int INTERVAL_TICKS = 100, RADIUS = 16, VERTICAL = 10;

    public static void check(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator() || done(player)) return;
        var center = player.blockPosition();
        var saplings = SaplingRimewoodState.get(level);
        var placed = PlayerPlacedBlockTracker.get(level.getServer());
        var cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            for (int dy = VERTICAL; dy >= -VERTICAL; dy--) {
                cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                if (!level.isLoaded(cursor) || !level.getBlockState(cursor).is(ColdFlora.RIMEWOOD_LOGS)) continue;
                if (level.getBlockState(cursor.below()).is(ColdFlora.RIMEWOOD_LOGS)) continue;
                if (!ColdFlora.rimewoodSoil(level.getBlockState(cursor.below()))) break;
                var base = cursor.immutable();
                if (base.distSqr(center) <= RADIUS * RADIUS && !saplings.grewFromSapling(base) && !placed.isPlayerPlaced(base)
                        && visible(level, player, base)) {
                    WorldTickHandler.grantAdvancement(player, ADVANCEMENT);
                    return;
                }
                break;
            }
        }
    }

    /** The first block on the sight line is Rimewood, or nothing at all. */
    static boolean visible(ServerLevel level, ServerPlayer player, BlockPos base) {
        var target = Vec3.atCenterOf(base.above());
        var hit = level.clip(new ClipContext(player.getEyePosition(), target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || level.getBlockState(hit.getBlockPos()).is(ColdFlora.COLD_FLORA);
    }

    private static boolean done(ServerPlayer player) {
        var holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, ADVANCEMENT));
        return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
