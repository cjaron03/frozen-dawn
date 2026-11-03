package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.IcarusPartBlock;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.IcarusDetonation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** A warning, not a reprieve: starting to mine any block of a reactor whose core holds antimatter flashes a red line over
 * the hotbar and sounds the warning tone to that player. Finishing the break still detonates at once (no countdown). */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class IcarusPartWarning {
    private IcarusPartWarning() {}

    @SubscribeEvent
    public static void onMiningStarted(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return;
        var level = player.serverLevel(); var pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof IcarusPartBlock) || !IcarusDetonation.armed(level, pos)) return;
        player.displayClientMessage(Component.translatable("message.frozendawn.icarus.armed_part").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        player.playNotifySound(ModSounds.ICARUS_ALARM_WARNING.get(), SoundSource.BLOCKS, 1.0f, 1.2f);
    }
}
