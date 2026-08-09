package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.hearthrot.HearthrotManager;
import com.frozendawn.hearthrot.HearthrotPolicy;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/** Replaces the infected player's hurt vocal without masking combat impacts. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HearthrotHurtSoundHandler {
    private static final double SOUND_ORIGIN_EPSILON_SQUARED = 1.0E-4D;

    private HearthrotHurtSoundHandler() {
    }

    @SubscribeEvent
    public static void onLevelSound(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel().isClientSide
                || event.getSource() != SoundSource.PLAYERS
                || !isPlayerHurtSound(event.getSound())) {
            return;
        }

        Vec3 soundPosition = event.getPosition();
        Player infectedPlayer = event.getLevel().players().stream()
                .filter(player -> player.distanceToSqr(soundPosition)
                        <= SOUND_ORIGIN_EPSILON_SQUARED)
                .filter(player -> HearthrotPolicy.usesCrystallineHurtSounds(
                        HearthrotManager.stage(player)))
                .findFirst()
                .orElse(null);
        if (infectedPlayer == null) {
            return;
        }

        int variant = infectedPlayer.getRandom().nextInt(3);
        event.setSound(switch (variant) {
            case 1 -> ModSounds.HEARTHROT_HURT_CRACK_TWO;
            case 2 -> ModSounds.HEARTHROT_HURT_CRACK_THREE;
            default -> ModSounds.HEARTHROT_HURT_CRACK_ONE;
        });
        event.setNewVolume(Math.max(1.0F, event.getNewVolume()));
        event.setNewPitch(0.94F + infectedPlayer.getRandom().nextFloat() * 0.12F);
    }

    private static boolean isPlayerHurtSound(Holder<SoundEvent> sound) {
        if (sound == null) {
            return false;
        }
        ResourceLocation location = sound.value().getLocation();
        if (!location.getNamespace().equals("minecraft")) {
            return false;
        }
        String path = location.getPath();
        return path.equals("entity.player.hurt")
                || path.equals("entity.player.hurt_drown")
                || path.equals("entity.player.hurt_freeze")
                || path.equals("entity.player.hurt_on_fire")
                || path.equals("entity.player.hurt_sweet_berry_bush");
    }
}
