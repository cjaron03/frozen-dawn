package com.frozendawn.item;

import com.frozendawn.init.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** One serving. Eaten while Steaming it also grants Warm Meal; cold, it is only food. */
public class SeafoodBoilBowlItem extends Item {

    public SeafoodBoilBowlItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) SeafoodBoilHeat.release(stack, level.getGameTime());
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && SeafoodBoilHeat.isSteaming(stack, level.getGameTime())) {
            entity.addEffect(new MobEffectInstance(ModEffects.WARM_MEAL, SeafoodBoilHeat.WARM_MEAL_TICKS));
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SeafoodBoilHeat.appendTooltip(stack, context, lines);
    }
}
