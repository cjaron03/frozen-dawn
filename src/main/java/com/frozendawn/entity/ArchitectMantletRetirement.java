package com.frozendawn.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Only arrows already lodged in a retiring screen become recoverable items. */
final class ArchitectMantletRetirement {
    private static final int MAX_ARROWS = 32;
    private ArchitectMantletRetirement() { }

    static boolean releaseStoppedArrows(ArchitectEntity actor, List<BlockPos> cells) {
        if (cells.isEmpty()) return true;
        AABB bounds = new AABB(cells.getFirst());
        for (BlockPos cell : cells) bounds = bounds.minmax(new AABB(cell));
        var arrows = new ArrayList<AbstractArrow>();
        ((ServerLevel) actor.level()).getEntities(EntityTypeTest.forClass(AbstractArrow.class), bounds.inflate(.1),
                a -> !a.isRemoved() && (a instanceof Arrow || a instanceof SpectralArrow), arrows, MAX_ARROWS + 1);
        // Leave the old screen standing rather than doing unbounded work or releasing unprocessed arrows.
        if (arrows.size() > MAX_ARROWS) return false;
        int released = 0;
        for (var arrow : arrows) {
            var state = arrow.saveWithoutId(new CompoundTag());
            if (!state.getBoolean("inGround")
                    || !state.getCompound("inBlockState").getString("Name").equals("minecraft:packed_ice")
                    || cells.stream().noneMatch(p -> new AABB(p).inflate(.06).contains(arrow.position()))) continue;
            if (arrow.pickup == AbstractArrow.Pickup.ALLOWED)
                arrow.spawnAtLocation(arrow.getPickupItemStackOrigin().copy(), .1F);
            arrow.discard();
            released++;
        }
        if (released > 0) actor.recordDecision("MANTLET_ARROW_RECOVERY", null, "lodged=" + released);
        return true;
    }
}
