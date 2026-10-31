package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Bank and dock share the same small inventory UI. The Binder always handles linking. */
public final class PowerStorageBlock extends Block implements EntityBlock {
    private final boolean bank;
    public PowerStorageBlock(Properties p, boolean bank) { super(p); this.bank = bank; }
    public boolean isBank() { return bank; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new PowerStorageBlockEntity(p, s); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof PowerStorageBlockEntity storage)
            server.openMenu(storage, buf -> { buf.writeBlockPos(p); buf.writeBoolean(bank); });
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moved) {
        if (s.getBlock() != next.getBlock() && l.getBlockEntity(p) instanceof PowerStorageBlockEntity storage) {
            Containers.dropContents(l, p, storage); l.updateNeighbourForOutputSignal(p, this);
        }
        super.onRemove(s, l, p, next, moved);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState s) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        if (!(l.getBlockEntity(p) instanceof PowerStorageBlockEntity storage)) return 0;
        return storage.storedJoules() == 0 ? 0 : 1 + (int)(14L * storage.storedJoules() / storage.capacityJoules());
    }
}
