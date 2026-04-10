package com.frozendawn.client.compat.curios;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class NoOpCuriosClientAccess implements CuriosClientAccess {

    @Override
    public void registerRenderers() {
        // Intentionally empty when Curios is unavailable.
    }

    @Override
    public void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Intentionally empty when Curios is unavailable.
    }
}
