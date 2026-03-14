package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

/**
 * Handles all easter egg logic for Frozen Dawn.
 * Most eggs are one-time-per-player, stored in player persistent data.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class EasterEggHandler {

    private EasterEggHandler() {}

    // --- Per-player tick tracking (in-memory, resets on restart which is fine) ---
    private static final Map<UUID, Integer> crouchTicks = new HashMap<>();
    private static final Map<UUID, Integer> lookUpTicks = new HashMap<>();

    // --- Wilson lava tracking ---
    private static final Map<UUID, WilsonDrop> pendingWilsonDrops = new HashMap<>();

    // --- Delayed whisper scheduling ---
    private static final List<DelayedSound> delayedSounds = new ArrayList<>();

    // --- ORSA Fire Safety per-chunk tracking (in-memory, one-time per chunk) ---
    private static final Set<Long> fireSafetyChunks = new HashSet<>();

    // --- Eyes Up: tracks players who died to freeze damage (bridges death→respawn entity swap) ---
    private static final Set<UUID> pendingFreezeDeaths = new HashSet<>();

    private static final String EE_PREFIX = "frozendawn:ee_";

    static void reset() {
        crouchTicks.clear();
        lookUpTicks.clear();
        pendingWilsonDrops.clear();
        delayedSounds.clear();
        fireSafetyChunks.clear();
        pendingFreezeDeaths.clear();
    }

    // ========================================================================
    // TICK-BASED EASTER EGGS (called from PlayerTickHandler)
    // ========================================================================

    /**
     * Called every server tick from PlayerTickHandler for each overworld player.
     * Individual checks are staggered via gameTick modulo to avoid per-tick overhead.
     */
    static void tickPerPlayer(ServerPlayer player, int phase, float progress) {
        long gameTick = player.level().getGameTime();

        // "This is Fine" — crouch in 1x1 underground for 60+ seconds
        if (gameTick % 20 == 0) {
            tickThisIsFine(player);
        }

        // "Ph'nglui mglw'nafh" — phase 6, look straight up for 5+ seconds
        if (phase >= 6 && gameTick % 20 == 0) {
            tickLovecraft(player);
        }

        // "The Karman Line" — Y=0-1, phase 6, atmosphere gone
        if (phase >= 6 && progress >= 0.85f && gameTick % 40 == 0) {
            tickKarmanLine(player);
        }

        // Survival streak — every 200 ticks
        if (gameTick % 200 == 0) {
            tickSurvivalStreak(player);
        }
    }

    /**
     * Called every server tick from WorldTickHandler to process delayed sounds.
     */
    static void tickDelayedSounds(MinecraftServer server) {
        if (delayedSounds.isEmpty()) return;

        Iterator<DelayedSound> it = delayedSounds.iterator();
        while (it.hasNext()) {
            DelayedSound ds = it.next();
            ds.ticksRemaining--;
            if (ds.ticksRemaining <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(ds.playerId);
                if (player != null) {
                    player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.SANITY_WHISPER.get(), SoundSource.AMBIENT, 0.6f, 0.8f);
                }
                it.remove();
            }
        }
    }

    /**
     * Called every 20 ticks to check Wilson item entities that were dropped.
     */
    static void tickWilsonDrops(MinecraftServer server) {
        if (pendingWilsonDrops.isEmpty()) return;

        Iterator<Map.Entry<UUID, WilsonDrop>> it = pendingWilsonDrops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WilsonDrop> entry = it.next();
            WilsonDrop drop = entry.getValue();
            drop.ticksElapsed += 20;

            ServerLevel level = server.getLevel(drop.dimension);
            if (level == null) {
                // Dimension unloaded — grant normal advancement as fallback
                grantWilsonAdvancement(server, drop.playerId);
                it.remove();
                continue;
            }

            // Check if the item entity still exists
            if (level.getEntity(drop.itemEntityId) instanceof ItemEntity itemEntity) {
                if (itemEntity.isInLava() || itemEntity.isOnFire()) {
                    // Wilson entered lava — schedule delayed whisper, no advancement
                    scheduleWilsonWhisper(drop.playerId);
                    it.remove();
                    continue;
                }
            } else {
                // Item entity gone — check if it was destroyed quickly (likely lava/fire)
                if (drop.ticksElapsed <= 100) { // within 5 seconds
                    scheduleWilsonWhisper(drop.playerId);
                } else {
                    grantWilsonAdvancement(server, drop.playerId);
                }
                it.remove();
                continue;
            }

            // Timeout — if item survived 5 seconds, it's a normal drop
            if (drop.ticksElapsed >= 100) {
                grantWilsonAdvancement(server, drop.playerId);
                it.remove();
            }
        }
    }

    private static void grantWilsonAdvancement(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            WorldTickHandler.grantAdvancement(player, "wilson_dropped");
        }
    }

    private static void scheduleWilsonWhisper(UUID playerId) {
        // 30 seconds = 600 ticks
        delayedSounds.add(new DelayedSound(playerId, 600));
    }

    /**
     * Begins tracking a dropped Wilson item entity to detect lava destruction.
     * If the item enters lava within 5 seconds, the wilson_dropped advancement
     * is suppressed and a delayed sanity whisper is scheduled instead.
     */
    public static void trackWilsonDrop(ServerPlayer player, ItemEntity itemEntity) {
        pendingWilsonDrops.put(itemEntity.getUUID(),
                new WilsonDrop(player.getUUID(), itemEntity.getId(), player.level().dimension()));
    }

    // ========================================================================
    // EVENT-BASED EASTER EGGS
    // ========================================================================

    /**
     * "Snowman Betrayal" — snow golem created in phase 4+ attacks player once.
     * Also handles Wilson item entity tracking for the lava easter egg.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Wilson item entity tracking — only in the dimension where the player dropped it
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            if (itemEntity.getItem().is(ModItems.WILSON.get())) {
                // Find the player who just dropped Wilson (same dimension)
                for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
                    if (player.getPersistentData().getBoolean("frozendawn:wilson_just_dropped")) {
                        player.getPersistentData().remove("frozendawn:wilson_just_dropped");
                        trackWilsonDrop(player, itemEntity);
                        break;
                    }
                }
            }
            return;
        }

        // Snowman Betrayal
        if (!(event.getEntity() instanceof SnowGolem golem)) return;

        ApocalypseState state = ApocalypseState.get(serverLevel.getServer());
        if (state.getPhase() < 4) return;

        // Find nearest player within 8 blocks (the one who built it)
        ServerPlayer builder = null;
        double closest = 64.0; // 8 blocks squared
        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            double dist = player.distanceToSqr(golem);
            if (dist < closest) {
                closest = dist;
                builder = player;
            }
        }

        if (builder != null) {
            final ServerPlayer target = builder;
            // Schedule the betrayal for 1 tick later so the golem is fully spawned
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    serverLevel.getServer().getTickCount() + 1, () -> {
                if (golem.isAlive() && target.isAlive()) {
                    target.hurt(golem.damageSources().mobAttack(golem), 0.5f);
                    WorldTickHandler.grantAdvancement(target, "et_tu_frosty");
                }
            }));
        }
    }

    /**
     * "That's Not Going to Help" — placing torch on surface phase 5+.
     * "ORSA Fire Safety" — 10+ heaters in same chunk.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Level.OVERWORLD) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        BlockState placed = event.getPlacedBlock();
        BlockPos pos = event.getPos();

        ApocalypseState state = ApocalypseState.get(serverLevel.getServer());
        int phase = state.getPhase();

        // "That's Not Going to Help" — torch on surface during phase 5+
        if (phase >= 5 && (placed.getBlock() instanceof TorchBlock || placed.getBlock() instanceof WallTorchBlock)) {
            if (pos.getY() > 60 && serverLevel.canSeeSky(pos)) {
                if (!hasFlag(player, "torch")) {
                    setFlag(player, "torch");
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.ee.torch"), false);
                }
            }
        }

        // "ORSA Fire Safety" — 10+ heaters in same chunk
        if (placed.is(ModBlocks.THERMAL_HEATER.get())
                || placed.is(ModBlocks.IRON_THERMAL_HEATER.get())
                || placed.is(ModBlocks.GOLD_THERMAL_HEATER.get())
                || placed.is(ModBlocks.DIAMOND_THERMAL_HEATER.get())) {
            ChunkPos cp = new ChunkPos(pos);
            long chunkKey = cp.toLong();
            if (!fireSafetyChunks.contains(chunkKey)) {
                int heaterCount = 0;
                for (BlockPos heater : HeaterRegistry.getHeaters(player.level())) {
                    if (new ChunkPos(heater).toLong() == chunkKey) {
                        heaterCount++;
                    }
                }
                if (heaterCount >= 10) {
                    fireSafetyChunks.add(chunkKey);
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.ee.fire_safety"), false);
                }
            }
        }
    }

    /**
     * "The Fridge" — opening chest at exactly -40C.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Level.OVERWORLD) return;

        BlockState blockState = event.getLevel().getBlockState(event.getPos());
        if (!(blockState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return;

        if (hasFlag(player, "fridge")) return;

        ApocalypseState state = ApocalypseState.get(player.getServer());
        float temp = TemperatureManager.getTemperatureAt(
                player.level(), player.blockPosition(),
                state.getCurrentDay(), state.getTotalDays());

        // Check for exactly -40 (within ±0.5 tolerance)
        if (Math.abs(temp - (-40f)) < 0.5f) {
            setFlag(player, "fridge");
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.ee.fridge"), false);
        }
    }

    /**
     * "It Didn't Get Warmer" — waking from bed during phase 4+. Every time.
     */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Level.OVERWORLD) return;

        ApocalypseState state = ApocalypseState.get(player.getServer());
        if (state.getPhase() >= 4) {
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.ee.bed_wake"), false);
        }
    }

    /**
     * "Eyes Up" — respawning after dying to freeze damage.
     * Uses a static UUID set instead of persistent data because the player
     * entity is replaced on respawn and persistent data may not transfer.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check if death was from freeze damage
        if (event.getSource().is(DamageTypes.FREEZE)) {
            pendingFreezeDeaths.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (pendingFreezeDeaths.remove(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.ee.eyes_up"), false);
        }
    }

    // ========================================================================
    // TICK-BASED HELPERS
    // ========================================================================

    private static void tickThisIsFine(ServerPlayer player) {
        if (hasFlag(player, "this_is_fine")) return;

        BlockPos pos = player.blockPosition();
        if (pos.getY() >= 50) {
            crouchTicks.remove(player.getUUID());
            return;
        }

        if (!player.isCrouching()) {
            crouchTicks.remove(player.getUUID());
            return;
        }

        // Check 1x1 enclosure: solid on all 4 sides + above
        Level level = player.level();
        BlockPos above = pos.above();
        BlockPos north = pos.north();
        BlockPos south = pos.south();
        BlockPos east = pos.east();
        BlockPos west = pos.west();
        boolean enclosed = level.getBlockState(above).isSolidRender(level, above)
                && level.getBlockState(north).isSolidRender(level, north)
                && level.getBlockState(south).isSolidRender(level, south)
                && level.getBlockState(east).isSolidRender(level, east)
                && level.getBlockState(west).isSolidRender(level, west);

        if (!enclosed) {
            crouchTicks.remove(player.getUUID());
            return;
        }

        int ticks = crouchTicks.getOrDefault(player.getUUID(), 0) + 20;
        crouchTicks.put(player.getUUID(), ticks);

        if (ticks >= 1200) { // 60 seconds
            setFlag(player, "this_is_fine");
            crouchTicks.remove(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.ee.this_is_fine"), false);
        }
    }

    private static void tickLovecraft(ServerPlayer player) {
        if (hasFlag(player, "lovecraft")) return;

        if (player.getXRot() < -80f) {
            int ticks = lookUpTicks.getOrDefault(player.getUUID(), 0) + 20;
            lookUpTicks.put(player.getUUID(), ticks);

            if (ticks >= 100) { // 5 seconds
                setFlag(player, "lovecraft");
                lookUpTicks.remove(player.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.ee.lovecraft"), false);
            }
        } else {
            lookUpTicks.remove(player.getUUID());
        }
    }

    private static void tickKarmanLine(ServerPlayer player) {
        if (hasFlag(player, "karman")) return;

        int y = player.blockPosition().getY();
        if (y >= 0 && y <= 1) {
            setFlag(player, "karman");
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.ee.karman"), false);
            WorldTickHandler.grantAdvancement(player, "edge_of_space");
        }
    }

    private static void tickSurvivalStreak(ServerPlayer player) {
        ApocalypseState state = ApocalypseState.get(player.getServer());
        int currentDay = state.getCurrentDay();

        if (currentDay < 100) return;

        ConfigPresets preset = ConfigPresets.detectCurrentPreset();
        boolean isBrutal = preset == ConfigPresets.BRUTAL;

        if (isBrutal) {
            if (!hasFlag(player, "how")) {
                setFlag(player, "how");
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.ee.how"), false);
                WorldTickHandler.grantAdvancement(player, "how");
            }
        } else {
            if (!hasFlag(player, "stubborn")) {
                setFlag(player, "stubborn");
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.ee.stubborn"), false);
                WorldTickHandler.grantAdvancement(player, "stubborn");
            }
        }
    }

    // ========================================================================
    // PERSISTENT FLAGS
    // ========================================================================

    private static boolean hasFlag(ServerPlayer player, String flag) {
        return player.getPersistentData().getBoolean(EE_PREFIX + flag);
    }

    private static void setFlag(ServerPlayer player, String flag) {
        player.getPersistentData().putBoolean(EE_PREFIX + flag, true);
    }

    private static void clearFlag(ServerPlayer player, String flag) {
        player.getPersistentData().remove(EE_PREFIX + flag);
    }

    // ========================================================================
    // INTERNAL RECORDS
    // ========================================================================

    private static class WilsonDrop {
        final UUID playerId;
        final int itemEntityId;
        final ResourceKey<Level> dimension;
        int ticksElapsed;

        WilsonDrop(UUID playerId, int itemEntityId, ResourceKey<Level> dimension) {
            this.playerId = playerId;
            this.itemEntityId = itemEntityId;
            this.dimension = dimension;
            this.ticksElapsed = 0;
        }
    }

    private static class DelayedSound {
        final UUID playerId;
        int ticksRemaining;

        DelayedSound(UUID playerId, int ticksRemaining) {
            this.playerId = playerId;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
