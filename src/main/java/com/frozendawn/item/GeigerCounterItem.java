package com.frozendawn.item;

import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** ORSA Geiger Counter. Without one in your inventory, radiation is silent. Held, it shows counts per minute,
 * dose rate and accumulated dose; the needle on its face follows the reading and pegs off scale in extreme fields. */
public final class GeigerCounterItem extends Item {
    public GeigerCounterItem(Properties p) { super(p); }
    public static boolean carried(Player p) { return p.getInventory().contains(new ItemStack(ModItems.GEIGER_COUNTER.get())); }
    public static boolean held(Player p) { return p.getMainHandItem().is(ModItems.GEIGER_COUNTER.get()) || p.getOffhandItem().is(ModItems.GEIGER_COUNTER.get()); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.geiger_counter.hint").withStyle(ChatFormatting.GRAY));
    }
}
