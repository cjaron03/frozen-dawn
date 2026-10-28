package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** ORSA Data Slate: one Fabricator blueprint. Insert it into a Fabricator to learn the part; a slate for a part the
 * world already knows stays readable. */
public final class DataSlateItem extends Item {
    public DataSlateItem(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var id = stack.get(ModDataComponents.BLUEPRINT.get());
        // Only known slate ids reach the registry lookup; a hand-edited component reads as blank instead of throwing.
        if (id == null || !FabricatorBlueprints.SLATES.contains(id)) { tooltip.add(Component.translatable("item.frozendawn.data_slate.blank").withStyle(ChatFormatting.GRAY)); return; }
        var part = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, id));
        tooltip.add(Component.translatable("item.frozendawn.data_slate.blueprint", part.getDescription()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.frozendawn.data_slate.hint").withStyle(ChatFormatting.GRAY));
    }
}
