package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import com.frozendawn.network.IcarusBlueprintPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** AMR-1 Icarus Controller, on the ring top beside the support blocks. FACING points away from the core. */
public final class IcarusControllerBlock extends IcarusPartBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public IcarusControllerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER) || player.getOffhandItem().is(ModItems.CONDUIT_BINDER)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof IcarusControllerBlockEntity c) {
            // An unfinished reactor toggles its ghost-block blueprint; the screen arrives with the running reactor.
            if (!player.isShiftKeyDown() && !IcarusStructure.validate(server.serverLevel(), p, s.getValue(FACING), true).valid())
                blueprint(server, c, c.toggleBlueprint(server.getUUID()));
        }
        return InteractionResult.sidedSuccess(l.isClientSide());
    }
    /** A Controller placed where no reactor stands yet shows its blueprint at once. */
    @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(l, p, s, placer, stack);
        if (placer instanceof ServerPlayer server && l.getBlockEntity(p) instanceof IcarusControllerBlockEntity c
                && !IcarusStructure.validate(server.serverLevel(), p, s.getValue(FACING), true).valid() && !c.showsBlueprintTo(server.getUUID()))
            blueprint(server, c, c.toggleBlueprint(server.getUUID()));
    }
    /** Sends the blueprint state, then names the current stage and every block still needed, in chat where the list wraps. */
    private static void blueprint(ServerPlayer player, IcarusControllerBlockEntity c, boolean show) {
        var level = player.serverLevel(); var pos = c.getBlockPos(); var facing = c.getBlockState().getValue(FACING);
        var cells = c.blueprint(level);
        if (cells.isEmpty()) { if (show) c.toggleBlueprint(player.getUUID()); show = false; }
        PacketDistributor.sendToPlayer(player, new IcarusBlueprintPayload(pos, show));
        if (!show) { player.displayClientMessage(Component.translatable("message.frozendawn.icarus.blueprint_hidden"), true); return; }
        var stage = IcarusStructure.stage(level, pos, facing);
        var parts = Component.empty();
        for (var e : IcarusStructure.remaining(level, pos, facing).entrySet()) {
            if (!parts.getSiblings().isEmpty()) parts.append(", ");
            parts.append(Component.translatable("message.frozendawn.icarus.blueprint_part", e.getValue(), e.getKey().getName()));
        }
        long clear = cells.stream().filter(w -> w.ghost() == null).count();
        if (clear > 0) { if (!parts.getSiblings().isEmpty()) parts.append(", "); parts.append(Component.translatable("message.frozendawn.icarus.blueprint_clear", clear)); }
        var name = Component.translatable("message.frozendawn.icarus.stage." + stage.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GOLD);
        player.displayClientMessage(Component.translatable("message.frozendawn.icarus.blueprint", name, parts), false);
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IcarusControllerBlockEntity(p, s); }
}
