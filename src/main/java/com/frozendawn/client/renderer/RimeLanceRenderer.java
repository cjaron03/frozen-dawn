package com.frozendawn.client.renderer;

import com.frozendawn.entity.RimeLanceEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public final class RimeLanceRenderer extends ThrownItemRenderer<RimeLanceEntity> {
    public RimeLanceRenderer(EntityRendererProvider.Context context) {
        super(context, 1.7F, true);
    }
}
