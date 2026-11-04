package com.frozendawn.client.renderer;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.vault.VaultBridge;
import com.frozendawn.vault.VaultDoorCellBlock;
import com.frozendawn.vault.VaultMechanismBlock.Kind;
import com.frozendawn.vault.VaultMechanismBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws what a vault mechanism moves: a door's whole panel on its slide, and the bridge's next cell on its way out. */
public final class VaultMechanismRenderer implements BlockEntityRenderer<VaultMechanismBlockEntity> {
    private final BlockRenderDispatcher blocks;

    public VaultMechanismRenderer(BlockEntityRendererProvider.Context context) { blocks = context.getBlockRenderDispatcher(); }

    @Override public void render(VaultMechanismBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        float shown = be.shown(partial);
        if (be.kind() == Kind.BRIDGE) {
            float cells = shown / VaultMechanismBlockEntity.CELL_TICKS;
            int whole = (int) cells;
            if (cells - whole > 0 && whole < VaultBridge.SPAN)
                draw(be, ModBlocks.VAULT_WALKWAY.get().defaultBlockState(), whole + (cells - whole), 1, 0, be.getBlockPos().offset(whole + 1, 2, 0), pose, buffers);
            return;
        }
        boolean last = be.kind() == Kind.LAST_DOOR;
        float slide = last ? 0 : shown / VaultMechanismBlockEntity.DOOR_TICKS * 3;
        if (slide >= 3 || last && be.finished()) return;
        var panel = ModBlocks.VAULT_DOOR_CELL.get().defaultBlockState().setValue(VaultDoorCellBlock.LAST, last);
        draw(be, panel, 0, -3, slide, be.getBlockPos().offset(-1, -3, 0), pose, buffers);
    }

    private void draw(VaultMechanismBlockEntity be, BlockState state, float x, float y, float z, BlockPos lit, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(x, y, z);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.translucentCullBlockSheet()), state, blocks.getBlockModel(state), 1, 1, 1,
                LevelRenderer.getLightColor(be.getLevel(), lit), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(VaultMechanismBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(VaultMechanismBlockEntity be) { return new AABB(be.getBlockPos()).inflate(12); }
}
