package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Cosmetic outer-visor repair and slow peripheral moisture; never obscures the center. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EmergencyEvaVisor {
    private static final ResourceLocation TAPE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/emergency_visor_tape.png");
    private static float condensation;

    private EmergencyEvaVisor() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        condensation = 0.0F;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            condensation = 0.0F;
            return;
        }
        if (mc.isPaused()) return;
        boolean sealed = mc.player.isAlive() && !mc.player.isCreative() && !mc.player.isSpectator()
                && matchingHelmet() && EmergencyEvaHandler.hasLifeSupport(mc.player);
        float target = 0.0F;
        if (sealed) {
            float reserve = EmergencyEvaHandler.remainingTicks(mc.player)
                    / (float) com.frozendawn.data.EmergencyEvaState.SERVICE_TICKS;
            boolean exertion = mc.player.isSprinting()
                    && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.001;
            float age = Mth.clamp((1.0F - reserve - 0.10F) / 0.90F, 0.0F, 1.0F);
            float ageCurve = age * age * (3.0F - 2.0F * age);
            target = 0.04F + 0.66F * ageCurve + (exertion ? 0.18F : 0.0F);
        }
        condensation = Mth.lerp(target > condensation ? 0.006F : 0.004F, condensation, target);
    }

    static boolean hasVisibleCondensation() {
        var mc = Minecraft.getInstance();
        return condensation >= 0.18F && matchingHelmet()
                && mc.options.getCameraType().isFirstPerson()
                && com.frozendawn.config.FrozenDawnConfig.ENABLE_SUIT_PUNCTURE_OVERLAY.get()
                && EmergencyEvaHandler.hasLifeSupport(mc.player);
    }

    private static boolean matchingHelmet() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.EMERGENCY_EVA_HELMET.get())
                && EmergencyEvaHandler.matchesIssue(player, player.getItemBySlot(EquipmentSlot.HEAD));
    }

    public static void render(GuiGraphics graphics) {
        // The caller applies first-person, overlay-option and intro/HUD visibility guards.
        if (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.isAlive()) return;
        renderCondensation(graphics);
        int width = Math.max(48, Math.round(graphics.guiWidth() * 0.33F));
        int height = Math.max(10, Math.round(width / 4.0F));
        int x = graphics.guiWidth() - width - Math.max(6, graphics.guiWidth() / 45);
        int y = Math.max(6, graphics.guiHeight() / 12);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TAPE, x, y, width, height, 48.0F, 102.0F,
                2090, 526, 2172, 724);
    }

    private static void renderCondensation(GuiGraphics graphics) {
        if (condensation < 0.01F || !EmergencyEvaHandler.hasLifeSupport(Minecraft.getInstance().player)) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int pixel = Math.max(2, Math.min(width, height) / 90);
        float sideDepth = width * (0.05F + 0.09F * condensation);
        float bottomDepth = height * (0.08F + 0.10F * condensation);
        // Age gradually spreads moisture inward at the perimeter; the center and telemetry stay clear.
        for (int y = height / 3; y < height; y += pixel) {
            for (int x = 0; x < width; x += pixel) {
                float side = 1.0F - Math.min(x, width - 1 - x) / sideDepth;
                float bottom = 1.0F - (height - 1 - y) / bottomDepth;
                float edge = Mth.clamp(Math.max(side, bottom), 0.0F, 1.0F);
                if (edge <= 0.0F) continue;
                int pattern = Math.floorMod(x / pixel * 17 + y / pixel * 31, 11);
                int alpha = Math.round(54.0F * condensation * edge * (0.55F + pattern / 22.0F));
                if (alpha > 0) graphics.fill(x, y, Math.min(width, x + pixel), Math.min(height, y + pixel),
                        alpha << 24 | 0xA9C2C9);
                // Sparse fixed droplets, rather than animated noise or a screen-wide blur.
                if (condensation >= 0.25F && edge > 0.30F && pattern == 0) {
                    int dropletAlpha = Math.round(42.0F * condensation * edge);
                    graphics.fill(x, y, Math.min(width, x + pixel), Math.min(height, y + pixel / 2 + 1),
                            dropletAlpha << 24 | 0xC8DADE);
                }
            }
        }
    }
}
