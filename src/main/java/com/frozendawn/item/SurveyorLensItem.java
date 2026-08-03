package com.frozendawn.item;

import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.HearthSurveyPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

public class SurveyorLensItem extends Item {

    private static final int COOLDOWN_TICKS = 20;
    private final SurveyorLensScanner.LensProfile lensProfile;

    public SurveyorLensItem(Properties properties, SurveyorLensScanner.LensProfile lensProfile) {
        super(properties);
        this.lensProfile = lensProfile;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            if (PostMaeveWorldState.isErased(serverLevel)) {
                displayPostMaeveReading(serverPlayer);
                serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            List<SurveyorLensScanner.HeatSignature> signatures = SurveyorLensScanner.collectHeatSignatures(
                    serverLevel,
                    serverPlayer.position(),
                    serverPlayer.blockPosition(),
                    lensProfile
            );

            Optional<HearthSurveyScanner.HearthSignal> hearthSignal =
                    HearthSurveyScanner.scan(serverPlayer, lensProfile);

            if (hearthSignal.isPresent()) {
                displayHearthSignal(serverPlayer, hearthSignal.orElseThrow());
            } else if (signatures.isEmpty()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.frozendawn.surveyor_lens.none")
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
            } else {
                SurveyorLensScanner.HeatSignature primary = signatures.getFirst();
                MutableComponent message = signatures.size() == 1
                        ? Component.translatable(
                                "message.frozendawn.surveyor_lens.detected",
                                primary.displayName(),
                                primary.distanceBlocks(),
                                primary.direction()
                        )
                        : Component.translatable(
                                "message.frozendawn.surveyor_lens.detected_many",
                                signatures.size(),
                                primary.displayName(),
                                primary.distanceBlocks(),
                                primary.direction()
                        );

                serverPlayer.displayClientMessage(message.withStyle(ChatFormatting.AQUA), true);
            }

            if (!signatures.isEmpty()) {
                markHeatSources(serverLevel, serverPlayer, signatures);
            }

            serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void displayPostMaeveReading(ServerPlayer player) {
        String[] directions = {
                "north", "northeast", "east", "southeast",
                "south", "southwest", "west", "northwest"
        };
        long bucket = player.serverLevel().getGameTime() / 40L;
        long mixed = player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits()
                ^ bucket * 0x9E3779B97F4A7C15L;
        String direction = directions[Math.floorMod((int) (mixed ^ mixed >>> 32),
                directions.length)];
        player.displayClientMessage(Component.translatable(
                "message.frozendawn.surveyor_lens.post_maeve", direction)
                .withStyle(ChatFormatting.DARK_GRAY), true);
        player.playNotifySound(ModSounds.RADIO_STATIC_AMBIENT.get(),
                SoundSource.MASTER, 0.24F, 0.62F);
    }

    private void displayHearthSignal(ServerPlayer player, HearthSurveyScanner.HearthSignal signal) {
        MutableComponent message = switch (signal.band()) {
            case STATIC -> Component.translatable(
                    signal.hostile()
                            ? "message.frozendawn.surveyor_lens.hearth.static_hostile"
                            : "message.frozendawn.surveyor_lens.hearth.static");
            case CARRIER -> Component.translatable(
                    signal.hostile()
                            ? "message.frozendawn.surveyor_lens.hearth.carrier_hostile"
                            : "message.frozendawn.surveyor_lens.hearth.carrier",
                    signal.direction());
            case FRAGMENT -> Component.translatable(
                    signal.hostile()
                            ? "message.frozendawn.surveyor_lens.hearth.fragment_hostile"
                            : "message.frozendawn.surveyor_lens.hearth.fragment",
                    signal.direction());
            case LOCK -> Component.translatable(
                    "message.frozendawn.surveyor_lens.hearth.lock",
                    hearthTypeName(signal.hearthType()),
                    signal.distanceBlocks(),
                    signal.direction());
            case CATALOGUED -> Component.translatable(
                    signal.hostile()
                            ? "message.frozendawn.surveyor_lens.hearth.catalogued_hostile"
                            : "message.frozendawn.surveyor_lens.hearth.catalogued",
                    hearthTypeName(signal.hearthType()),
                    signal.distanceBlocks(),
                    signal.direction());
            case NONE -> Component.translatable("message.frozendawn.surveyor_lens.none");
        };

        ChatFormatting color = signal.hostile()
                ? ChatFormatting.DARK_RED
                : signal.suspicious() ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        player.displayClientMessage(message.withStyle(color), true);

        if (signal.newlyDiscovered()) {
            player.sendSystemMessage(Component.translatable(
                    "message.frozendawn.surveyor_lens.hearth.catalogued_chat",
                    hearthTypeName(signal.hearthType()),
                    signal.distanceBlocks(),
                    signal.direction()).withStyle(ChatFormatting.GOLD));
        }

        playHearthSignal(player, signal);
    }

    private void playHearthSignal(ServerPlayer player, HearthSurveyScanner.HearthSignal signal) {
        SoundEvent sound;
        if (signal.hostile()
                && signal.band() != HearthSurveyPolicy.SignalBand.LOCK
                && signal.band() != HearthSurveyPolicy.SignalBand.CATALOGUED) {
            sound = ModSounds.RADIO_STATIC_HEAVY.get();
        } else {
            sound = switch (signal.band()) {
                case STATIC -> ModSounds.RADIO_STATIC_BURST.get();
                case CARRIER -> ModSounds.RADIO_STATIC_MEDIUM.get();
                case FRAGMENT -> ModSounds.THAEVEN_CONTACT.get();
                case LOCK, CATALOGUED -> ModSounds.RADIO_SIGNAL_LOCK.get();
                case NONE -> ModSounds.RADIO_STATIC_AMBIENT.get();
            };
        }

        float volume = 0.35F + signal.observedStrength() * 0.5F;
        float pitch = signal.hostile()
                ? 0.68F + signal.observedStrength() * 0.08F
                : 0.80F + signal.observedStrength() * 0.18F;
        // Hearth scans are suit-internal instrument feedback and remain audible in vacuum.
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    private static Component hearthTypeName(HearthSelectionPolicy.HearthType type) {
        String key = type == HearthSelectionPolicy.HearthType.MAJOR
                ? "message.frozendawn.surveyor_lens.hearth.type.major"
                : "message.frozendawn.surveyor_lens.hearth.type.minor";
        return Component.translatable(key);
    }

    private void markHeatSources(ServerLevel level, ServerPlayer player, List<SurveyorLensScanner.HeatSignature> signatures) {
        int markers = Math.min(lensProfile.maxMarkers(), signatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = signatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            level.sendParticles(player, signature.sourceType().markerParticle(), true, x, y, z, 10, 0.18D, 0.18D, 0.18D, 0.003D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(lensProfile.tooltipKey())
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(lensProfile.tooltipUseKey())
                .withStyle(ChatFormatting.AQUA));
    }

    public SurveyorLensScanner.LensProfile lensProfile() {
        return lensProfile;
    }
}
