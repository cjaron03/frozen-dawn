package com.frozendawn.item;
import com.frozendawn.init.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class PixieItem extends BlockItem {
    public PixieItem(){super(ModBlocks.PIXIE.get(),new Item.Properties().stacksTo(1));}
    @Override public void appendHoverText(ItemStack s,Item.TooltipContext c,List<Component> lines,TooltipFlag flag){super.appendHoverText(s,c,lines,flag);lines.add(Component.translatable("screen.frozendawn.pixie.water",String.format(Locale.ROOT,"%.1f",s.getOrDefault(ModDataComponents.PIXIE_WATER.get(),0L)/12000000.0)));}
}
