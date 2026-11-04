package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.VaultRecordingEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The pale figure: the player model in a see-through skin, with no shadow and no name. */
public final class VaultRecordingRenderer extends LivingEntityRenderer<VaultRecordingEntity, PlayerModel<VaultRecordingEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/vault_recording.png");

    public VaultRecordingRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0);
    }
    @Override public ResourceLocation getTextureLocation(VaultRecordingEntity entity) { return TEXTURE; }
    @Override protected @Nullable RenderType getRenderType(VaultRecordingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }
    @Override protected boolean shouldShowName(VaultRecordingEntity entity) { return false; }
    @Override protected int getBlockLightLevel(VaultRecordingEntity entity, net.minecraft.core.BlockPos pos) { return 12; }
}
