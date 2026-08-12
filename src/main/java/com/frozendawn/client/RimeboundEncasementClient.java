package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Client input lock and two-block ice-prison render for full encasement. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RimeboundEncasementClient {
    private RimeboundEncasementClient() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isSolid(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !isSolid(minecraft.player)) {
            return;
        }
        if (event.isAttack() || event.isUseItem() || event.isPickBlock()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!isSolid(event.getEntity())) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Blocks.ICE.defaultBlockState(), poseStack,
                event.getMultiBufferSource(), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY);
        poseStack.translate(0.0D, 1.0D, 0.0D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Blocks.ICE.defaultBlockState(), poseStack,
                event.getMultiBufferSource(), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        event.setCanceled(true);
    }

    private static boolean isSolid(net.minecraft.world.entity.LivingEntity entity) {
        var effect = entity.getEffect(ModEffects.RIMEBOUND_ENCASEMENT);
        return effect != null && effect.getAmplifier() >= 3;
    }
}
