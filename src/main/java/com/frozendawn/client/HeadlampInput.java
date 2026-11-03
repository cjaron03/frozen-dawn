package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Rebindable player input; the authenticated server owns the switch and power rules. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HeadlampInput {
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.frozendawn.toggle_headlamp", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.frozendawn");

    private HeadlampInput() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.player != null
                    && mc.level != null && mc.screen == null && !mc.isPaused()
                    && mc.isWindowActive()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.frozendawn.network.HeadlampTogglePayload());
            }
        }
    }

    @EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }
}
