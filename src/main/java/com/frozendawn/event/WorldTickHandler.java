package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.frozendawn.network.ApocalypseDataPayload;
import com.frozendawn.network.TemperaturePayload;
import com.frozendawn.world.TemperatureManager;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.VegetationDecay;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives the apocalypse forward each server tick.
 * Dispatches to WeatherHandler, BlockFreezer, VegetationDecay, SnowAccumulator,
 * syncs state to clients, and grants phase advancements.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class WorldTickHandler {

    private static int lastLoggedPhase = -1;
    private static int lastLoggedDay = -1;

    private static final String[] PHASE_ADVANCEMENTS = {
            "root", "phase2", "phase3", "phase4", "phase5"
    };

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.tick();

        int currentPhase = state.getPhase();
        int currentDay = state.getCurrentDay();
        FrozenDawnPhaseTracker.setPhase(currentPhase);

        // Log phase transitions and grant advancements
        if (currentPhase != lastLoggedPhase) {
            FrozenDawn.LOGGER.info("Apocalypse phase transition: Phase {} -> Phase {} (Day {})",
                    lastLoggedPhase == -1 ? "START" : lastLoggedPhase, currentPhase, currentDay);
            lastLoggedPhase = currentPhase;

            // Grant phase advancements to all online players
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                grantPhaseAdvancements(player, currentPhase);
            }
        }

        // Log day changes (every 10 days to avoid spam)
        if (currentDay != lastLoggedDay && currentDay % 10 == 0) {
            FrozenDawn.LOGGER.info("Apocalypse Day {}/{} | Phase {} | Temp offset: {}C | Sun scale: {}",
                    currentDay, state.getTotalDays(), currentPhase,
                    String.format("%.1f", state.getTemperatureOffset()),
                    String.format("%.2f", state.getSunScale()));
            lastLoggedDay = currentDay;
        }

        // Sync apocalypse data to all clients every 100 ticks (~5 seconds)
        if (state.getApocalypseTicks() % 100 == 0) {
            PacketDistributor.sendToAllPlayers(createPayload(state));
        }

        // Send per-player temperature every 40 ticks (~2 seconds)
        if (state.getApocalypseTicks() % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    float temp = TemperatureManager.getTemperatureAt(
                            player.level(), player.blockPosition(), currentDay, state.getTotalDays());
                    PacketDistributor.sendToPlayer(player, new TemperaturePayload(temp));
                }
            }
        }

        // Drive world systems in the overworld
        ServerLevel overworld = server.overworld();
        WeatherHandler.tick(overworld, currentPhase);
        BlockFreezer.tick(overworld, currentPhase);
        VegetationDecay.tick(overworld, currentPhase);
        SnowAccumulator.tick(overworld, currentPhase);
    }

    /**
     * Suppress all mob spawning in the Overworld at phase 4+.
     * Both hostile and passive mobs stop appearing.
     */
    @SubscribeEvent
    public static void onMobSpawn(FinalizeSpawnEvent event) {
        if (FrozenDawnPhaseTracker.getPhase() < 4) return;
        if (event.getEntity().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        event.setSpawnCancelled(true);
    }

    /**
     * Prevent crop growth when temperature is below 0C.
     * Crops near heat sources (thermal heaters, lava, geothermal cores) can still grow.
     */
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (FrozenDawnPhaseTracker.getPhase() < 3) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        float temp = TemperatureManager.getTemperatureAt(
                serverLevel, event.getPos(), state.getCurrentDay(), state.getTotalDays());
        if (temp < 0f) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(ModBlocks.GEOTHERMAL_CORE.get())) return;
        if (event.getPos().getY() >= 0) return;

        grantAdvancement(player, "last_light");
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        var stack = event.getOriginalStack();
        if (!stack.is(Items.WRITTEN_BOOK)) return;

        // Check if this is an ORSA book by checking the author field
        var bookContent = stack.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        if (bookContent != null && "ORSA".equals(bookContent.author())) {
            grantAdvancement(player, "classified_information");
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ApocalypseState state = ApocalypseState.get(player.getServer());
            PacketDistributor.sendToPlayer(player, createPayload(state));

            // Grant any missed phase advancements
            grantPhaseAdvancements(player, state.getPhase());
        }
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    private static void grantPhaseAdvancements(ServerPlayer player, int currentPhase) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Grant advancements for all phases up to and including current
        for (int i = 0; i < currentPhase && i < PHASE_ADVANCEMENTS.length; i++) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, PHASE_ADVANCEMENTS[i]);
            AdvancementHolder holder = server.getAdvancements().get(loc);
            if (holder == null) continue;

            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) {
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(holder, criterion);
                }
            }
        }
    }

    private static ApocalypseDataPayload createPayload(ApocalypseState state) {
        return new ApocalypseDataPayload(
                state.getPhase(),
                state.getProgress(),
                state.getTemperatureOffset(),
                state.getSunScale(),
                state.getSunBrightness(),
                state.getSkyLight()
        );
    }
}
