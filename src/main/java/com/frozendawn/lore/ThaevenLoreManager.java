package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModParticles;
import com.frozendawn.network.OpenThaevenArchivePayload;
import com.frozendawn.network.ThaevenLoreSyncPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-side entrypoint for every archive grant and semantic mutation. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ThaevenLoreManager {
    public static final ResourceLocation TRANSLATOR_RECIPE =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "thaeven_translator");

    private ThaevenLoreManager() {
    }

    public static void examineCarrier(
            ServerPlayer player, ThaevenRecordId record) {
        examineCarrier(player, record, player.position().add(0.0D, 1.0D, 0.0D));
    }

    public static void examineCarrier(
            ServerPlayer player, ThaevenRecordId record, Vec3 carrierPosition) {
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(player.getServer());
        if (!hasTranslator(player)) {
            if (data.discoverRecipe(player.getUUID())) {
                player.awardRecipesByKey(List.of(TRANSLATOR_RECIPE));
                sync(player);
            }
            PacketDistributor.sendToPlayer(player,
                    new OpenThaevenArchivePayload(record.ordinal(), true));
            return;
        }
        boolean translated = data.grantRecord(player.getUUID(), record);
        if (translated) {
            showTranslationComplete(player.serverLevel(), carrierPosition,
                    record);
        }
        sync(player);
        PacketDistributor.sendToPlayer(player,
                new OpenThaevenArchivePayload(record.ordinal(), false));
    }

    private static void showTranslationComplete(
            ServerLevel level, Vec3 position, ThaevenRecordId record) {
        level.sendParticles(ParticleTypes.GLOW,
                position.x, position.y, position.z,
                12, 0.32D, 0.24D, 0.32D, 0.025D);
        level.sendParticles(ParticleTypes.WAX_ON,
                position.x, position.y, position.z,
                8, 0.28D, 0.2D, 0.28D, 0.02D);
        level.playSound(null, BlockPos.containing(position),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                0.45F, 1.35F);
        if (record == ThaevenRecordId.THE_UNTHREADING) {
            level.sendParticles(ModParticles.UNTHREADING_MEMORY.get(),
                    position.x, position.y + 0.6D, position.z,
                    34, 0.9D, 1.25D, 0.9D, 0.045D);
            level.sendParticles(ModParticles.UNTHREADING_RESIDUE.get(),
                    position.x, position.y + 0.8D, position.z,
                    38, 1.5D, 0.95D, 1.5D, 0.075D);
            level.playSound(null, BlockPos.containing(position),
                    SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS,
                    1.0F, 0.58F);
        }
    }

    public static void openArchive(ServerPlayer player) {
        sync(player);
        PacketDistributor.sendToPlayer(player,
                new OpenThaevenArchivePayload(-1, false));
    }

    public static boolean grantRecord(
            ServerPlayer player, ThaevenRecordId record) {
        boolean changed = ThaevenLoreSavedData.get(player.getServer())
                .grantRecord(player.getUUID(), record);
        if (changed) {
            sync(player);
        }
        return changed;
    }

    public static void markViewed(
            ServerPlayer player, ThaevenRecordId record, int revision) {
        if (ThaevenLoreSavedData.get(player.getServer()).markViewed(
                player.getUUID(), record, revision)) {
            sync(player);
        }
    }

    public static boolean hasTranslator(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.THAEVEN_TRANSLATOR.get())) {
                return true;
            }
        }
        return player.getOffhandItem().is(ModItems.THAEVEN_TRANSLATOR.get());
    }

    public static void unlockSemantic(
            MinecraftServer server, ThaevenSemanticKey key) {
        if (!ThaevenLoreSavedData.get(server).unlockSemantic(key)) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        ThaevenLoreSavedData.ArchiveSnapshot snapshot =
                ThaevenLoreSavedData.get(player.getServer())
                        .snapshot(player.getUUID());
        PacketDistributor.sendToPlayer(player, new ThaevenLoreSyncPayload(
                snapshot.discoveredMask(), snapshot.recipeDiscovered(),
                snapshot.seenRevisions(), snapshot.architectLidRevision()));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
