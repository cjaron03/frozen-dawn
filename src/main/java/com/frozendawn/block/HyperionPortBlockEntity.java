package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.PowerNetworks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Hyperion's network node. It stores nothing: it reaches its formed controller, checked on every call, so a port
 * whose battery broke or that was never formed reaches no store. The controller sets the link again after each load. */
public final class HyperionPortBlockEntity extends BlockEntity {
    private BlockPos controller;
    public HyperionPortBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HYPERION_PORT.get(), p, s); }

    void setController(BlockPos pos) { controller = pos.immutable(); }
    public Optional<HyperionControllerBlockEntity> controller() {
        if (controller == null || level == null || !level.isLoaded(controller)) return Optional.empty();
        return level.getBlockEntity(controller) instanceof HyperionControllerBlockEntity c && c.shell().valid() && c.shell().ports().contains(worldPosition)
                ? Optional.of(c) : Optional.empty();
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel l) PowerNetworks.storageLoaded(l, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel l) PowerNetworks.storageRemoved(l, worldPosition); super.setRemoved(); }
}
