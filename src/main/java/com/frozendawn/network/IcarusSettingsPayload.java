package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.IcarusControllerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** The Icarus screen's three sliders: gross target in kW, shield drive in permille and matter feed per minute. The server
 * applies them only to the Controller the player has open, and the physics clamps them to its ranges. */
public record IcarusSettingsPayload(BlockPos controller, int outputKw, int shieldPermille, int feedPerMinute) implements CustomPacketPayload {
    public static final Type<IcarusSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_settings"));
    public static final StreamCodec<ByteBuf, IcarusSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, IcarusSettingsPayload::controller, ByteBufCodecs.VAR_INT, IcarusSettingsPayload::outputKw,
            ByteBufCodecs.VAR_INT, IcarusSettingsPayload::shieldPermille, ByteBufCodecs.VAR_INT, IcarusSettingsPayload::feedPerMinute,
            IcarusSettingsPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(ServerPlayer player, IcarusSettingsPayload p) {
        if (player.isSpectator() || !(player.containerMenu instanceof IcarusControllerMenu menu) || menu.entity() == null
                || !p.controller().equals(menu.pos()) || !menu.stillValid(player)) return;
        menu.entity().settings(p.outputKw(), p.shieldPermille(), p.feedPerMinute());
    }
}
