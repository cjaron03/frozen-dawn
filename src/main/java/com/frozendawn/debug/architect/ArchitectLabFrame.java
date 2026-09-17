package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Origin is the template's floor corner, not the GameTest structure block below it. */
public record ArchitectLabFrame(BlockPos origin, Rotation rotation) {
    public ArchitectLabFrame { origin = origin.immutable(); }
    public BlockPos block(BlockPos local) { return local.rotate(rotation).offset(origin); }
    public net.minecraft.world.phys.AABB bounds() {
        BlockPos a = block(BlockPos.ZERO), b = block(new BlockPos(20, 15, 20));
        return new net.minecraft.world.phys.AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }
    public Vec3 position(Vec3 local) {
        return StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO).add(Vec3.atLowerCornerOf(origin));
    }
    public BlockPos localBlock(BlockPos world) {
        return world.subtract(origin).rotate(inverse());
    }
    private Rotation inverse() {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }
    public Vec3 local(Vec3 world) {
        Rotation inverse = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
        return StructureTemplate.transform(world.subtract(Vec3.atLowerCornerOf(origin)), Mirror.NONE, inverse, BlockPos.ZERO);
    }
}
