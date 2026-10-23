package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A toggle request only; equipment, ownership and power are resolved on the server. */
public record HeadlampTogglePayload() implements CustomPacketPayload {
    public static final Type<HeadlampTogglePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"headlamp_toggle"));
    public static final StreamCodec<ByteBuf,HeadlampTogglePayload> STREAM_CODEC=StreamCodec.unit(new HeadlampTogglePayload());
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void handle(ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator())return;
        if(HeadlampPrototype.isPrototypeWorld(player)) {
            HeadlampPrototype.setEnabled(player,!HeadlampPrototype.enabled(player));
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Headlamp: "+(HeadlampPrototype.enabled(player)?"ON":"OFF")),true);
        } else SuitHeadlamp.toggle(player);
    }
}
