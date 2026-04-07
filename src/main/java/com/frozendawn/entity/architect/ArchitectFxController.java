package com.frozendawn.entity.architect;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class ArchitectFxController {

    private final ArchitectEntity entity;
    private final ArchitectBlockBreaker blockBreaker;

    public ArchitectFxController(ArchitectEntity entity, ArchitectBlockBreaker blockBreaker) {
        this.entity = entity;
        this.blockBreaker = blockBreaker;
    }

    public int buildRenderFlags(int action, boolean isDrinkingPotion, int retreatPhase,
                                @Nullable BlockPos scaffoldTarget, int scaffoldDelay) {
        int flags = 0;
        flags = ArchitectRenderFlags.set(flags, ArchitectRenderFlags.MINING, blockBreaker.isMining());
        flags = ArchitectRenderFlags.set(flags, ArchitectRenderFlags.QUEUED_SCAFFOLD,
                scaffoldTarget != null || scaffoldDelay > 0);
        flags = ArchitectRenderFlags.set(flags, ArchitectRenderFlags.RETREAT_RECOVERING,
                action == ArchitectEntity.ACTION_RETREAT && (retreatPhase >= 1 || isDrinkingPotion));
        return flags;
    }

    public void emitActionTelegraphParticles(@Nullable LivingEntity target, int action,
                                             boolean isDrinkingPotion, int retreatPhase,
                                             @Nullable BlockPos scaffoldTarget) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        switch (action) {
            case ArchitectEntity.ACTION_APPROACH -> {
                if (blockBreaker.isMining() && entity.tickCount % 4 == 0) {
                    BlockPos targetPos = blockBreaker.getTarget();
                    if (targetPos != null) {
                        BlockState state = entity.level().getBlockState(targetPos);
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                                5, 0.25, 0.25, 0.25, 0.02);
                        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                                targetPos.getX() + 0.5, targetPos.getY() + 0.6, targetPos.getZ() + 0.5,
                                2, 0.18, 0.12, 0.18, 0.01);
                    }
                } else if (scaffoldTarget != null && entity.tickCount % 4 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                            entity.getX(), entity.getY() + 0.2, entity.getZ(),
                            3, 0.2, 0.05, 0.2, 0.01);
                }
            }
            case ArchitectEntity.ACTION_ATTACK_MELEE -> {
                if (target != null
                        && entity.hasLineOfSight(target)
                        && entity.distanceTo(target) < 5.0f
                        && entity.tickCount % 8 == 0) {
                    Vec3 toward = target.position().subtract(entity.position());
                    Vec3 forward = toward.lengthSqr() > 1.0e-4 ? toward.normalize() : Vec3.ZERO;
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            entity.getX() + forward.x * 0.55, entity.getY() + 1.15, entity.getZ() + forward.z * 0.55,
                            3, 0.15, 0.2, 0.15, 0.01);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            entity.getX() + forward.x * 0.3, entity.getY() + 1.0, entity.getZ() + forward.z * 0.3,
                            2, 0.1, 0.1, 0.1, 0.005);
                }
            }
            case ArchitectEntity.ACTION_RETREAT -> {
                if ((retreatPhase == 1 || isDrinkingPotion) && entity.tickCount % 12 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                            entity.getX(), entity.getY() + 1.0, entity.getZ(),
                            4, 0.25, 0.3, 0.25, 0.01);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            entity.getX(), entity.getY() + 0.9, entity.getZ(),
                            2, 0.18, 0.12, 0.18, 0.003);
                }
            }
            default -> {
            }
        }
    }

    public void updateHeldItem(int action, boolean isDrinkingPotion, boolean building, int retreatPhase) {
        if (isDrinkingPotion) {
            return;
        }

        switch (action) {
            case ArchitectEntity.ACTION_ATTACK_MELEE ->
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            case ArchitectEntity.ACTION_APPROACH -> {
                if (blockBreaker.isMining()) {
                    // Tool is set by block breaker.
                } else if (building) {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
                } else {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            case ArchitectEntity.ACTION_FORTIFY, ArchitectEntity.ACTION_TRAP_SET ->
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
            case ArchitectEntity.ACTION_RETREAT -> {
                if (retreatPhase == 1) {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
                } else {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            default -> entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
