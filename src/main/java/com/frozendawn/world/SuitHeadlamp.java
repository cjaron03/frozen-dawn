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
        if(!PowerCellItem.pay(player,needed))return false;
        module.set(ModDataComponents.HEADLAMP_CREDIT.get(),credit+needed*2-5);
        player.getInventory().setChanged();return true;
    }
    static void collect(ServerLevel level,List<ServerPlayer> players,Map<BlockPos,Integer> desired) {
        for(var player:players)if(player.level()==level&&!HeadlampPrototype.enabled(player)&&payTick(player))
            HeadlampBeam.project(level,player.getEyePosition(),player.getLookAngle()).forEach((pos,light)->desired.merge(pos,light,Math::max));
    }
}
