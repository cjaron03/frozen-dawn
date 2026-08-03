package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.network.UndoneStrugglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reports movement intent only while the local player is held by an Undone. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class UndoneGraspClient {
    private static int sendCooldown;

    private UndoneGraspClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            sendCooldown = 0;
            return;
        }
        AABB search = minecraft.player.getBoundingBox().inflate(16.0D);
        boolean held = minecraft.level.getEntitiesOfClass(
                UndoneEntity.class, search,
                undone -> undone.getGraspTargetId() == minecraft.player.getId()
                        && undone.isGrasping()).stream().findAny().isPresent();
        if (!held) {
            sendCooldown = 0;
            return;
        }
        if (--sendCooldown > 0) {
            return;
        }
        float forward = minecraft.player.input.forwardImpulse;
        float left = minecraft.player.input.leftImpulse;
        float movement = Mth.clamp((float) Math.sqrt(
                forward * forward + left * left), 0.0F, 1.0F);
        if (minecraft.options.keyJump.isDown()) {
            movement = 1.0F;
        }
        PacketDistributor.sendToServer(new UndoneStrugglePayload(movement));
        sendCooldown = 3;
    }
}
