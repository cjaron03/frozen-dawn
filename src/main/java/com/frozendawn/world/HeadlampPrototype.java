package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Property-gated lab controls; free power is restricted to the two prototype worlds. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class HeadlampPrototype {
    public static final String WORLD="Headlamp Aim Check";
    public static final String WATER_WORLD="Headlamp Water Check";
    private static final Set<String> WORLDS=Set.of(WORLD,WATER_WORLD);
    private static final Map<ServerLevel,Set<UUID>> ENABLED=new WeakHashMap<>();
    private HeadlampPrototype() {}
    public static boolean isPrototypeWorld(ServerPlayer player) {
        return Boolean.getBoolean("frozendawn.labCommands") && WORLDS.contains(player.getServer().getWorldData().getLevelName());
    }
    public static boolean enabled(ServerPlayer player) {
        return Boolean.getBoolean("frozendawn.labCommands")
                && ENABLED.getOrDefault(player.serverLevel(),Set.of()).contains(player.getUUID());
    }
    public static void setEnabled(ServerPlayer player,boolean enabled) {
        if (!Boolean.getBoolean("frozendawn.labCommands")) return;
        var sources=ENABLED.computeIfAbsent(player.serverLevel(),k->new HashSet<>());
        if (enabled) sources.add(player.getUUID()); else sources.remove(player.getUUID());
    }
    static void collect(ServerLevel level,List<ServerPlayer> players,Map<BlockPos,Integer> desired) {
        var active=ENABLED.get(level);
        if (active==null) return;
        active.retainAll(players.stream().map(ServerPlayer::getUUID).toList());
        for (var player:players) if (player.level()==level&&player.isAlive()&&!player.isSpectator()&&enabled(player))
            HeadlampBeam.project(level,player.getEyePosition(),player.getLookAngle()).forEach((pos,light)->desired.merge(pos,light,Math::max));
    }
    static void clear(ServerLevel level) { ENABLED.remove(level); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("frozendawn.labCommands")) return;
        var root=Commands.literal("headlamp_prototype").requires(s->s.hasPermission(2));
        for (String action:List.of("on","off","toggle","status")) root.then(Commands.literal(action).executes(context->{
            var source=context.getSource();var player=source.getPlayerOrException();
            if (!WORLDS.contains(source.getServer().getWorldData().getLevelName())) {
                source.sendFailure(Component.literal("Headlamp prototype is restricted to its lab worlds"));return 0;
            }
            if (!action.equals("status")) {
                setEnabled(player,action.equals("toggle")?!enabled(player):action.equals("on"));
                player.displayClientMessage(Component.literal("Headlamp: "+(enabled(player)?"ON":"OFF")),true);
            } else player.sendSystemMessage(Component.literal("Headlamp prototype: "+(enabled(player)?"ON":"OFF")+" | light "+HeadlampBeam.MAX_BRIGHTNESS+" / "+HeadlampBeam.RANGE+"-block projection | free lab power; cell drain starts in slice 2"));
            return 1;
        }));
        event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("headlamp_equipment").requires(s->s.hasPermission(2))
                .then(Commands.literal("status").executes(context->{
                    var player=context.getSource().getPlayerOrException();
                    if(!context.getSource().getServer().getWorldData().getLevelName().equals("Headlamp Equipment Check"))return 0;
                    var module=SuitHeadlamp.module(player);
                    player.sendSystemMessage(Component.literal("Headlamp: "+(module.isEmpty()?"NOT EQUIPPED":SuitHeadlamp.enabled(module)?"ON":"OFF")
                            +" | mount "+!module.isEmpty()+" | helmet "+SuitHeadlamp.compatibleHelmet(player)
                            +" | prepaid half-J "+module.getOrDefault(com.frozendawn.init.ModDataComponents.HEADLAMP_CREDIT.get(),0)));
                    for(int i=0;i<36;i++) {
                        var stack=player.getInventory().getItem(i);
                        if(stack.is(com.frozendawn.init.ModItems.POWER_CELL))player.sendSystemMessage(Component.literal("Cell slot "+i+": "+com.frozendawn.item.PowerCellItem.energy(stack)+" J"));
                    }
                    return 1;
                })));
    }
}
