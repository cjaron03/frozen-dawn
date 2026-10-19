package com.frozendawn.world;

import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Server-owned equipment and physical-cell accounting; no elapsed-time/offline charge. */
public final class SuitHeadlamp {
    public static final int DRAW_WATTS=50;
    private SuitHeadlamp() {}

    /** Only the functional Helmet Mount slot supplies a module. Ordinary carriage is inactive. */
    public static ItemStack module(ServerPlayer player) {
        var mounted=com.frozendawn.compat.curios.CuriosCompat.helmetMount(player);
        return mounted.is(ModItems.SUIT_HEADLAMP)?mounted:ItemStack.EMPTY;
    }
    public static boolean compatibleHelmet(ServerPlayer player) {
        var helmet=player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(ModItems.EVA_HELMET)||helmet.is(ModItems.ORSA_THERMAL_VISOR);
    }
    public static boolean enabled(ItemStack module) {
        return module.is(ModItems.SUIT_HEADLAMP)&&module.getCount()==1
                &&module.getOrDefault(ModDataComponents.HEADLAMP_ENABLED.get(),false);
    }
    public static void toggle(ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator())return;
        var module=module(player);
        if(module.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.frozendawn.headlamp.missing"),true);return;
        }
        boolean on=!enabled(module);
        module.set(ModDataComponents.HEADLAMP_ENABLED.get(),on);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(on?"tooltip.frozendawn.headlamp.on":"tooltip.frozendawn.headlamp.off"),true);
    }
    /** Pays a complete 2.5 J tick across loose cells atomically; insufficient funds stay untouched. */
    public static boolean payTick(ServerPlayer player) {
        var module=module(player);
        if(!player.isAlive()||player.isSpectator()||!compatibleHelmet(player)||!enabled(module))return false;
        int credit=Math.clamp(module.getOrDefault(ModDataComponents.HEADLAMP_CREDIT.get(),0),0,1);
        int needed=(5-credit+1)/2;
        var cells=new ArrayList<ItemStack>();
        int available=0;
        // Inventory.items is slots 0..35: hotbar first, then main inventory. Do not traverse containers.
        for(var stack:player.getInventory().items) {
            if(stack.is(ModItems.POWER_CELL)&&stack.getCount()==1&&PowerCellItem.energy(stack)>0) {
                cells.add(stack);available+=PowerCellItem.energy(stack);
                if(available>=needed)break;
            }
        }
        if(available<needed) {
            var off=player.getOffhandItem();
            if(off.is(ModItems.POWER_CELL)&&off.getCount()==1) {cells.add(off);available+=PowerCellItem.energy(off);}
        }
        if(available<needed)return false;
        int remaining=needed;
        for(var cell:cells) {
            int debit=Math.min(remaining,PowerCellItem.energy(cell));
            PowerCellItem.setEnergy(cell,PowerCellItem.energy(cell)-debit);remaining-=debit;
            if(remaining==0)break;
        }
        module.set(ModDataComponents.HEADLAMP_CREDIT.get(),credit+needed*2-5);
        player.getInventory().setChanged();return true;
    }
    static void collect(ServerLevel level,List<ServerPlayer> players,Map<BlockPos,Integer> desired) {
        for(var player:players)if(player.level()==level&&!HeadlampPrototype.enabled(player)&&payTick(player))
            HeadlampBeam.project(level,player.getEyePosition(),player.getLookAngle()).forEach((pos,light)->desired.merge(pos,light,Math::max));
    }
}
