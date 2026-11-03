package com.frozendawn.item;

import com.frozendawn.init.*;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.ItemContainerContents;

public final class BatteryLanternItem extends BlockItem {
    public BatteryLanternItem() {
        super(ModBlocks.BATTERY_LANTERN.get(),new Item.Properties().stacksTo(16)
                .component(DataComponents.CONTAINER,ItemContainerContents.EMPTY)
                .component(ModDataComponents.LANTERN_ENABLED.get(),true).component(ModDataComponents.LANTERN_CREDIT.get(),0));
    }
    public static ItemStack cell(ItemStack stack) {return stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyOne();}
    @Override public boolean isBarVisible(ItemStack stack) {return !cell(stack).isEmpty();}
    @Override public int getBarWidth(ItemStack stack) {return Math.round(13f*PowerCellItem.energy(cell(stack))/PowerCellItem.CAPACITY_JOULES);}
    @Override public int getBarColor(ItemStack stack) {return 0x64C9CC;}
    /** The same prepaid half-joule used by the placed block travels with the item. */
    public static boolean payHeldTick(ItemStack stack) {
        if(!stack.is(ModItems.BATTERY_LANTERN)||stack.getCount()!=1||!stack.getOrDefault(ModDataComponents.LANTERN_ENABLED.get(),true))return false;
        var cell=cell(stack);int energy=PowerCellItem.energy(cell);
        int credit=stack.getOrDefault(ModDataComponents.LANTERN_CREDIT.get(),0);
        if(energy*2+credit<5)return false;
        int debit=(5-credit+1)/2;
        PowerCellItem.setEnergy(cell,energy-debit);
        stack.set(ModDataComponents.LANTERN_CREDIT.get(),credit+debit*2-5);
        stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(cell)));
        return true;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(!player.isShiftKeyDown())return InteractionResultHolder.pass(stack);
        if(!level.isClientSide()){
            boolean enabled=!stack.getOrDefault(ModDataComponents.LANTERN_ENABLED.get(),true);
            stack.set(ModDataComponents.LANTERN_ENABLED.get(),enabled);
            player.displayClientMessage(Component.translatable(enabled?"tooltip.frozendawn.battery_lantern.on":"tooltip.frozendawn.battery_lantern.off"),true);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> text,TooltipFlag flag) {
        super.appendHoverText(stack,context,text,flag);
        var cell=cell(stack);
        var state=Component.translatable(stack.getOrDefault(ModDataComponents.LANTERN_ENABLED.get(),true)
                ?"tooltip.frozendawn.battery_lantern.on":"tooltip.frozendawn.battery_lantern.off");
        text.add((cell.isEmpty()?Component.translatable("tooltip.frozendawn.battery_lantern.empty",state)
                :Component.translatable("tooltip.frozendawn.battery_lantern.charge",String.format(Locale.ROOT,"%.3f",PowerCellItem.energy(cell)/1000.0),state)).withStyle(ChatFormatting.AQUA));
        text.add(Component.translatable("tooltip.frozendawn.battery_lantern.portable").withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("tooltip.frozendawn.battery_lantern.controls").withStyle(ChatFormatting.GRAY));
    }
}
