package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import com.frozendawn.network.SfrBlueprintPayload;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** SFR-1 Controller. FACING points away from the torus center, four cells behind it. */
public final class SfrControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public SfrControllerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof SfrControllerBlockEntity c) {
            var level = server.serverLevel();
            // A broken torus toggles its ghost-block blueprint instead of the screen; sneaking opens the screen anyway.
            if (!player.isShiftKeyDown() && !SfrMultiblock.validate(level, p, s.getValue(FACING)).valid()) blueprint(server, c, c.toggleBlueprint(server.getUUID()));
            else server.openMenu(c, buf -> buf.writeBlockPos(p));
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    /** A Controller placed where no torus stands yet shows its blueprint at once. */
    @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(l, p, s, placer, stack);
        if (placer instanceof ServerPlayer server && l.getBlockEntity(p) instanceof SfrControllerBlockEntity c
                && !SfrMultiblock.validate(server.serverLevel(), p, s.getValue(FACING)).valid() && !c.showsBlueprintTo(server.getUUID()))
            blueprint(server, c, c.toggleBlueprint(server.getUUID()));
    }
    /** Sends the blueprint state and names what the torus still needs, by block, on the action bar. */
    private static void blueprint(ServerPlayer player, SfrControllerBlockEntity c, boolean show) {
        var cells = c.blueprint(player.serverLevel());
        // Every cell right but a port missing: nothing to draw, so the blueprint stays hidden.
        if (cells.isEmpty()) { if (show) c.toggleBlueprint(player.getUUID()); show = false; }
        PacketDistributor.sendToPlayer(player, new SfrBlueprintPayload(c.getBlockPos(), show));
        if (cells.isEmpty()) { player.displayClientMessage(Component.translatable("message.frozendawn.sfr.missing_ports"), true); return; }
        if (!show) { player.displayClientMessage(Component.translatable("message.frozendawn.sfr.blueprint_hidden"), true); return; }
        var counts = new java.util.LinkedHashMap<Block, Integer>(); int clear = 0;
        for (var w : cells) if (w.expected() == null) clear++; else counts.merge(w.expected(), 1, Integer::sum);
        var parts = Component.empty();
        for (var e : counts.entrySet()) {
            if (!parts.getSiblings().isEmpty()) parts.append(", ");
            parts.append(Component.translatable("message.frozendawn.sfr.blueprint_part", e.getValue(), e.getKey().getName()));
        }
        if (clear > 0) { if (!parts.getSiblings().isEmpty()) parts.append(", "); parts.append(Component.translatable("message.frozendawn.sfr.blueprint_clear", clear)); }
        player.displayClientMessage(Component.translatable("message.frozendawn.sfr.blueprint", parts), true);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof SfrControllerBlockEntity c) {
            Containers.dropContents(l, p, c.items());
            // The tank empties into canisters, whole units only; a bred fraction is lost with the vessel.
            int units = (int) Math.floor(c.physics().tritium());
            if (units > 0) Containers.dropItemStack(l, p.getX(), p.getY(), p.getZ(), new ItemStack(ModItems.TRITIUM_CANISTER.get(), units));
        }
        super.onRemove(s, l, p, next, moving);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState s) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) { return l.getBlockEntity(p) instanceof SfrControllerBlockEntity c ? c.comparator() : 0; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new SfrControllerBlockEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (t != ModBlockEntities.SFR_CONTROLLER.get()) return null;
        return l.isClientSide() ? (w, pos, state, be) -> com.frozendawn.client.SfrLoopSound.tick((SfrControllerBlockEntity) be)
                : (w, pos, state, be) -> ((SfrControllerBlockEntity) be).serverTick();
    }
}
