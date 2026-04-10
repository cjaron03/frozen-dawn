package com.frozendawn.client.compat.curios;

import com.frozendawn.client.renderer.BlizzardGogglesCurioModel;
import com.frozendawn.client.renderer.BlizzardGogglesCurioRenderer;
import com.frozendawn.client.renderer.SnowshoesCurioModel;
import com.frozendawn.client.renderer.SnowshoesCurioRenderer;
import com.frozendawn.init.ModItems;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

final class CuriosLoadedClientAccess implements CuriosClientAccess {

    @Override
    public void registerRenderers() {
        CuriosRendererRegistry.register(
                ModItems.BLIZZARD_GOGGLES.get(),
                () -> new BlizzardGogglesCurioRenderer(Minecraft.getInstance().getEntityModels()));
        CuriosRendererRegistry.register(
                ModItems.SNOWSHOES.get(),
                () -> new SnowshoesCurioRenderer(Minecraft.getInstance().getEntityModels()));
    }

    @Override
    public void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BlizzardGogglesCurioModel.LAYER_LOCATION, BlizzardGogglesCurioModel::createBodyLayer);
        event.registerLayerDefinition(SnowshoesCurioModel.LAYER_LOCATION, SnowshoesCurioModel::createBodyLayer);
    }
}
