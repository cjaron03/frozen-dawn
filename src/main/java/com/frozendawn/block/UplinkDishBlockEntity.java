package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Render-only: holds the client's reflector yaw so it eases between sweep, spin and rest. Nothing is saved. */
public final class UplinkDishBlockEntity extends BlockEntity {
    /** Degrees, relative to the dish's facing; and degrees per tick. Client-side animation state only. */
    public float yaw, speed;
    public double lastTime = Double.NaN;
    public UplinkDishBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.UPLINK_DISH.get(),p,s);}
}
