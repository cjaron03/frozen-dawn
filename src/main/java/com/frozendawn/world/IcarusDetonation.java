package com.frozendawn.world;

import com.frozendawn.block.IcarusControllerBlock;
import com.frozendawn.block.IcarusControllerBlockEntity;
import com.frozendawn.block.IcarusStructure;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.power.IcarusBlast;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Decides what removing an AMR-1 Icarus part does, and applies a detonation's immediate effects. */
public final class IcarusDetonation {
    /** How far a part can sit from its Controller on each axis; the scan covers every chunk this reaches. */
    private static final int REACH = 2 * IcarusStructure.HALF + 1;

    private IcarusDetonation() {}

    /** Called from IcarusPartBlock.onRemove when the block actually changes. The owning Controller is found from the
     * block entities loaded around pos, not a static map, so this works straight after a reload. */
    public static void partRemoved(ServerLevel l, BlockPos pos) {
        var owners = new ArrayList<IcarusControllerBlockEntity>();
        for (int cx = (pos.getX() - REACH) >> 4; cx <= (pos.getX() + REACH) >> 4; cx++)
            for (int cz = (pos.getZ() - REACH) >> 4; cz <= (pos.getZ() + REACH) >> 4; cz++) {
                var chunk = l.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values())
                    if (be instanceof IcarusControllerBlockEntity c && !c.isRemoved()
                            && IcarusStructure.contains(c.getBlockPos(), c.getBlockState().getValue(IcarusControllerBlock.FACING), pos)) owners.add(c);
            }
        // Collected first: a detonation must not run while the chunk's block entity map is being walked.
        for (var c : owners) c.partRemoved(l);
    }

    /** Everything inside radius dies, items and experience vanish, and out to twice the radius damage and knockback
     * fade with distance; creative and spectator players are untouched. Then the crater is queued. */
    public static void detonate(ServerLevel l, BlockPos core, double radius, Set<UUID> crew) {
        var center = core.getCenter();
        var source = new DamageSource(l.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.ICARUS_DETONATION), center);
        double reach = 2 * radius;
        for (var e : l.getEntities((Entity) null, new AABB(center, center).inflate(reach),
                e -> e.isAlive() && e.position().distanceToSqr(center) < reach * reach)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double distance = e.position().distanceTo(center);
            if (distance <= radius && (e instanceof ItemEntity || e instanceof ExperienceOrb)) { e.discard(); continue; }
            e.hurt(source, IcarusBlast.damage(distance, radius));
            double push = IcarusBlast.knockback(distance, radius);
            if (push > 0 && e.isAlive()) {
                var away = e.position().subtract(center);
                var direction = away.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : away.normalize();
                e.push(direction.x * push, direction.y * push + 0.2 * push, direction.z * push);
                e.hurtMarked = true;
            }
        }
        if (FrozenDawnConfig.ICARUS_TERRAIN_DESTRUCTION.get()) IcarusCraterData.get(l).queue(core, radius, l.getSeed() ^ core.asLong() ^ l.getGameTime());
    }
}
