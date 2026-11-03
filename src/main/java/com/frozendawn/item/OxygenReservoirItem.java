package com.frozendawn.item;
import com.frozendawn.init.*;
import com.frozendawn.oxygen.OxygenAllocation;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class OxygenReservoirItem extends BlockItem {
    public OxygenReservoirItem(){super(ModBlocks.OXYGEN_RESERVOIR.get(),new Item.Properties().stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> text,TooltipFlag flag){super.appendHoverText(stack,context,text,flag);text.add(Component.translatable("tooltip.frozendawn.oxygen_reservoir",String.format(java.util.Locale.ROOT,"%.1f",(double)stack.getOrDefault(ModDataComponents.RESERVOIR_OXYGEN.get(),0L)/OxygenAllocation.MICRO_PER_SECOND)));}
}
