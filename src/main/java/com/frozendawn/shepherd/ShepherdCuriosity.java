package com.frozendawn.shepherd;

import com.frozendawn.block.MonitoringStationTerminalBlockEntity;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.TowerAntennaConsoleBlockEntity;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What she finds worth a look: only near the player, so she cannot wander off, and only what she can see from where she is. */
final class ShepherdCuriosity {
    private ShepherdCuriosity() {}

    enum Kind {
        STORAGE,
        HEATER,
        TERMINAL,
        ANIMAL
    }

    /** {@code animal} is null for a block. */
    record Target(Kind kind, Vec3 at, Animal animal) {}

    private static Kind kind(BlockEntity entity) {
        if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) return Kind.STORAGE;
        if (entity instanceof ThermalHeaterBlockEntity) return Kind.HEATER;
        return entity instanceof MonitoringStationTerminalBlockEntity || entity instanceof TowerAntennaConsoleBlockEntity ? Kind.TERMINAL : null;
    }

    static Target find(ServerLevel level, ServerPlayer player, ShepherdEntity she) {
        var found = new ArrayList<Target>();
        var at = player.blockPosition();
        int reach = ShepherdPolicy.CURIOUS_BLOCKS;
        for (int x = SectionPos.blockToSectionCoord(at.getX() - reach); x <= SectionPos.blockToSectionCoord(at.getX() + reach); x++) {
            for (int z = SectionPos.blockToSectionCoord(at.getZ() - reach); z <= SectionPos.blockToSectionCoord(at.getZ() + reach); z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    var kind = kind(entity);
                    var pos = entity.getBlockPos();
                    if (kind != null && pos.getCenter().distanceTo(player.position()) <= reach && clear(level, she, pos)) found.add(new Target(kind, pos.getCenter(), null));
                }
            }
        }
        for (var animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(reach), Animal::isAlive)) {
            if (animal.distanceTo(player) <= reach && she.hasLineOfSight(animal)) found.add(new Target(Kind.ANIMAL, animal.position(), animal));
        }
        return found.isEmpty() ? null : found.get(she.getRandom().nextInt(found.size()));
    }

    /** A clear line ends on the block itself. */
    private static boolean clear(ServerLevel level, ShepherdEntity she, BlockPos pos) {
        var hit = level.clip(new ClipContext(she.getEyePosition(), pos.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, she));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /** Whether it is still there to look at: broken blocks and dead animals end the look. */
    static boolean stillThere(ServerLevel level, Target target) {
        if (target.animal() != null) return target.animal().isAlive();
        var entity = level.getBlockEntity(BlockPos.containing(target.at()));
        return entity != null && kind(entity) == target.kind();
    }
}
