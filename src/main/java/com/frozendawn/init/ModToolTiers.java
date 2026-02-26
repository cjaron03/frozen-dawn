package com.frozendawn.init;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public class ModToolTiers {

    public static final Tier ACHERONITE = new Tier() {
        @Override public int getUses() { return 2200; }
        @Override public float getSpeed() { return 9.0F; }
        @Override public float getAttackDamageBonus() { return 4.0F; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_NETHERITE_TOOL; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(ModItems.REFINED_ACHERONITE.get()); }
    };
}
