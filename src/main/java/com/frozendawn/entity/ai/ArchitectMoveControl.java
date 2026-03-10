package com.frozendawn.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ArchitectMoveControl extends MoveControl {
    private final float maxRotate;

    public ArchitectMoveControl(Mob mob, float maxRotate) {
        super(mob);
        this.maxRotate = maxRotate;
    }

    @Override
    public void tick() {
        if (this.operation == Operation.STRAFE) {
            float speed = (float) this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float moveSpeed = (float) this.speedModifier * speed;
            float forwards = this.strafeForwards;
            float sideways = this.strafeRight;
            float magnitude = Mth.sqrt(forwards * forwards + sideways * sideways);
            if (magnitude < 1.0F) {
                magnitude = 1.0F;
            }

            magnitude = moveSpeed / magnitude;
            forwards *= magnitude;
            sideways *= magnitude;
            float sin = Mth.sin(this.mob.getYRot() * (float) (Math.PI / 180.0));
            float cos = Mth.cos(this.mob.getYRot() * (float) (Math.PI / 180.0));
            float relativeX = forwards * cos - sideways * sin;
            float relativeZ = sideways * cos + forwards * sin;
            if (!this.isWalkable(relativeX, relativeZ)) {
                this.strafeForwards = 1.0F;
                this.strafeRight = 0.0F;
            }

            this.mob.setSpeed(moveSpeed);
            this.mob.setZza(this.strafeForwards);
            this.mob.setXxa(this.strafeRight);
            this.operation = Operation.WAIT;
        } else if (this.operation == Operation.MOVE_TO) {
            this.operation = Operation.WAIT;
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            double dy = this.wantedY - this.mob.getY();
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < MIN_SPEED_SQR) {
                this.mob.setZza(0.0F);
                return;
            }

            float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYaw, this.maxRotate));
            this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            BlockPos blockPos = this.mob.blockPosition();
            BlockState state = this.mob.level().getBlockState(blockPos);
            VoxelShape shape = state.getCollisionShape(this.mob.level(), blockPos);
            if (dy > (double) this.mob.maxUpStep() && dx * dx + dz * dz < (double) Math.max(1.0F, this.mob.getBbWidth())
                    || !shape.isEmpty()
                    && this.mob.getY() < shape.max(Direction.Axis.Y) + (double) blockPos.getY()
                    && !state.is(BlockTags.DOORS)
                    && !state.is(BlockTags.FENCES)) {
                this.mob.getJumpControl().jump();
                this.operation = Operation.JUMPING;
            }
        } else if (this.operation == Operation.JUMPING) {
            this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.mob.onGround()) {
                this.operation = Operation.WAIT;
            }
        } else {
            this.mob.setZza(0.0F);
        }
    }

    private boolean isWalkable(float relativeX, float relativeZ) {
        PathNavigation navigation = this.mob.getNavigation();
        if (navigation != null) {
            NodeEvaluator evaluator = navigation.getNodeEvaluator();
            if (evaluator != null
                    && evaluator.getPathType(
                    this.mob,
                    BlockPos.containing(
                            this.mob.getX() + (double) relativeX,
                            (double) this.mob.getBlockY(),
                            this.mob.getZ() + (double) relativeZ))
                    != PathType.WALKABLE) {
                return false;
            }
        }

        return true;
    }
}
