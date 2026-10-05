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

/** Cosmetic outer-visor repair and full-visor moisture, drawn beneath projected instruments. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EmergencyEvaVisor {
    private static final ResourceLocation TAPE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/emergency_visor_tape.png");
    private static final ResourceLocation MIST = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/gui/emergency_visor_mist.png");
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
            var state = mc.player.getData(com.frozendawn.init.ModAttachments.EMERGENCY_EVA);
            float exertion = state.exertionIntensity();
            float age = Mth.clamp((1.0F - reserve - 0.10F) / 0.90F, 0.0F, 1.0F);
            float ageCurve = age * age * (3.0F - 2.0F * age);
            target = Math.min(1.0F, 0.04F + 0.66F * ageCurve + 0.78F * exertion
                    + 0.45F * state.thermalIntensity());
        }
        condensation = Mth.lerp(target > condensation ? 0.010F : 0.002F, condensation, target);
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
        RenderSystem.disableBlend();
    }

    private static void renderCondensation(GuiGraphics graphics) {
        if (condensation < 0.01F || !EmergencyEvaHandler.hasLifeSupport(Minecraft.getInstance().player)) return;
        // One cached texture/quad at every GUI scale. No per-frame pixel loops,
        // framebuffer blur, random noise or rectangular holes around the HUD.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, condensation);
        try {
            graphics.blit(MIST, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    0.0F, 0.0F, 512, 256, 512, 256);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }
}
