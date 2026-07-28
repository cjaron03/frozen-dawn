package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;

import java.util.List;

public class OrsaIdBadgeItem extends Item {

    private static final String[] NAMES = {
            "Dr. Vasik", "Dr. Chen", "Sgt. Torres", "Dr. Okonkwo", "Pvt. Lindgren",
            "Dr. Nakamura", "Cpl. Reyes", "Dr. Petrov", "Agent Wolfe", "Dr. Agrawal"
    };

    private static final String[] DEPARTMENTS = {
            "Thermodynamics", "Xenobiology", "Engineering", "Security",
            "Finance", "Medical", "Field Ops"
    };

    public OrsaIdBadgeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createNamed(long seed, int ordinal) {
        RandomSource random = RandomSource.create(seed + ordinal * 0x9E3779B97F4A7C15L);
        ItemStack stack = new ItemStack(com.frozendawn.init.ModItems.ORSA_ID_BADGE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("BadgeName", NAMES[random.nextInt(NAMES.length)]);
        tag.putString("BadgeDept", DEPARTMENTS[random.nextInt(DEPARTMENTS.length)]);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null && existing.copyTag().contains("BadgeName")) return;

        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putString("BadgeName", NAMES[level.random.nextInt(NAMES.length)]);
        tag.putString("BadgeDept", DEPARTMENTS[level.random.nextInt(DEPARTMENTS.length)]);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("BadgeName")) {
                tooltipComponents.add(Component.literal(tag.getString("BadgeName"))
                        .withStyle(ChatFormatting.WHITE));
                tooltipComponents.add(Component.literal("Dept: " + tag.getString("BadgeDept"))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        tooltipComponents.add(Component.literal("STATUS: MISSING")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
