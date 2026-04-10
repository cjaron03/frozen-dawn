package com.frozendawn.client.compat.curios;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

interface CuriosClientAccess {

    void registerRenderers();

    void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event);
}
