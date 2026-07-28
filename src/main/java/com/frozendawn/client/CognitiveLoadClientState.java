package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Prepared HUD art and inactive state for the later Cognitive Load slice. */
public final class CognitiveLoadClientState {
    private static final ResourceLocation[] STAGES = {
            texture("cognitive_load_1"),
            texture("cognitive_load_2"),
            texture("cognitive_load_3"),
            texture("cognitive_load_4")
    };
    private static float load;

    private CognitiveLoadClientState() {
    }

    public static void setLoadForFutureSlice(float value) {
        load = Mth.clamp(value, 0.0F, 1.0F);
    }

    public static float load() {
        return load;
    }

    public static void reset() {
        load = 0.0F;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (load <= 0.0F) {
            return;
        }
        int index = Math.min(STAGES.length - 1, Mth.floor(load * STAGES.length));
        int size = Math.min(graphics.guiWidth(), graphics.guiHeight());
        int x = (graphics.guiWidth() - size) / 2;
        int y = (graphics.guiHeight() - size) / 2;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(load, 0.0F, 0.78F));
        graphics.blit(STAGES[index], x, y, size, size, 0.0F, 0.0F,
                256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "textures/gui/" + name + ".png");
    }
}
