package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.compat.curios.CuriosCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SnowshoesHandler {

    private static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "snowshoes_snow_speed");

    private SnowshoesHandler() {
    }

    static void tick(ServerPlayer player) {
        if (!CuriosCompat.hasSnowshoesEquipped(player)
                || player.level().dimension() != Level.OVERWORLD
                || player.isCreative()
                || player.isSpectator()) {
            clearBoost(player);
            return;
        }

        WorldTickHandler.grantAdvancement(player, "walks_on_snow");
        applyBoost(player, getSurfaceSpeedBonus(player.getBlockStateOn()));
    }

    static void clearBoost(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    public static double getSurfaceSpeedBonus(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return SnowshoesTuning.getSpeedBonusForSnowBlock();
        }
        if (state.is(Blocks.SNOW) && state.hasProperty(SnowLayerBlock.LAYERS)) {
            return SnowshoesTuning.getSpeedBonusForLayers(state.getValue(SnowLayerBlock.LAYERS));
        }
        return 0.0D;
    }

    private static void applyBoost(ServerPlayer player, double bonusAmount) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        if (bonusAmount <= 0.0D || !player.onGround()) {
            movementSpeed.removeModifier(SPEED_MODIFIER_ID);
            return;
        }

        AttributeModifier existing = movementSpeed.getModifier(SPEED_MODIFIER_ID);
        if (existing != null
                && existing.amount() == bonusAmount
                && existing.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            return;
        }

        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                SPEED_MODIFIER_ID,
                bonusAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
