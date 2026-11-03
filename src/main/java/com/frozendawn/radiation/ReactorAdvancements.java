package com.frozendawn.radiation;

import com.frozendawn.FrozenDawn;
import com.frozendawn.item.GeigerCounterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Hidden reactor and radiation easter eggs from the Notion advancements page, granted from code. */
public final class ReactorAdvancements {
    private ReactorAdvancements() {}
    public static void grant(ServerPlayer player, String name) {
        var server = player.getServer(); if (server == null) return;
        var holder = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name));
        if (holder == null) return;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) for (var criterion : progress.getRemainingCriteria()) player.getAdvancements().award(holder, criterion);
    }
    public static void grantNear(ServerLevel level, BlockPos pos, double radius, String name) {
        for (var p : level.players()) if (p.blockPosition().closerThan(pos, radius)) grant(p, name);
    }
    /** Was That the Geiger Counter?: a Blind Architect clicks near a player whose counter is clicking. */
    public static void blindArchitectClicked(ServerLevel level, Vec3 at) {
        for (var p : level.players())
            if (p.position().distanceToSqr(at) <= 24 * 24 && GeigerCounterItem.carried(p)
                    && RadiationMath.clicksPerSecond(p.getData(com.frozendawn.init.ModAttachments.RADIATION.get()).rate) > 0)
                grant(p, "was_that_the_geiger_counter");
    }
}
