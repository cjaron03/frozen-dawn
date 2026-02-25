package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.frozendawn.network.ApocalypseDataPayload;
import com.frozendawn.network.TemperaturePayload;
import com.frozendawn.world.TemperatureManager;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.VegetationDecay;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
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
    /** Cached habitable zone status per player UUID, updated every 20 ticks. */
    private static final java.util.Map<java.util.UUID, Boolean> habitableCache = new java.util.HashMap<>();
    /** Suffocation timer per player (ticks without air). Resets when in habitable zone. */
    private static final java.util.Map<java.util.UUID, Integer> suffocationTimer = new java.util.HashMap<>();
    /** Cached per-player temperature for use by FoodFrostHandler. */
    private static final java.util.Map<java.util.UUID, Float> playerTemperatures = new java.util.HashMap<>();
    /** Ticks to full suffocation death. ~10 seconds of escalating symptoms. */
    private static final int SUFFOCATION_DURATION = 200;

    private static final String[] PHASE_ADVANCEMENTS = {
            "root", "phase2", "phase3", "phase4", "phase5", "phase6"
    };
    private static final String[] ARMOR_ADVANCEMENTS = {
            null, "insulated_clothing", "heavy_insulation", "eva_suit"
    };

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        lastLoggedPhase = -1;
        lastLoggedDay = -1;
        habitableCache.clear();
        suffocationTimer.clear();
        playerTemperatures.clear();
        WeatherHandler.reset();
        NetherSeveranceHandler.reset();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.tick(server);

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

        // Send per-player temperature every 40 ticks (~2 seconds) + check armor advancements + heat damage
        if (state.getApocalypseTicks() % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    float temp = TemperatureManager.getTemperatureAt(
                            player.level(), player.blockPosition(), currentDay, state.getTotalDays());

                    // Armor heat trapping: insulated armor amplifies heat above 20C
                    float armorHeatMult = MobFreezeHandler.getArmorHeatMultiplier(player);
                    if (temp > 20f && armorHeatMult > 0f) {
                        temp += (temp - 20f) * armorHeatMult;
                    }

                    playerTemperatures.put(player.getUUID(), temp);
                    PacketDistributor.sendToPlayer(player, new TemperaturePayload(temp));

                    // Heat penalty — the ironic counterpart to freezing
                    if (!player.isCreative() && !player.isSpectator() && temp > 60f) {
                        if (temp >= 120f) {
                            // Extreme: 2 hearts damage, nausea, heavy slowness
                            DamageSource heatSource = new DamageSource(
                                    player.serverLevel().registryAccess()
                                            .lookupOrThrow(Registries.DAMAGE_TYPE)
                                            .getOrThrow(ModDamageTypes.HYPERTHERMIA));
                            net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
                            player.hurt(heatSource, 4.0f);
                            player.setDeltaMovement(motion);
                            player.addEffect(new MobEffectInstance(
                                    MobEffects.CONFUSION, 80, 0, false, false, false));
                            player.addEffect(new MobEffectInstance(
                                    MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
                            player.displayClientMessage(
                                    Component.translatable("message.frozendawn.heat.cooking"), true);
                            grantAdvancement(player, "too_hot_to_handle");
                            int armorTier = MobFreezeHandler.getFullSetTier(player);
                            if (armorTier >= 1 && armorTier <= 2) {
                                grantAdvancement(player, "insulation_both_ways");
                            }
                        } else if (temp >= 90f) {
                            // Severe: 1 heart damage, weakness
                            DamageSource heatSource = new DamageSource(
                                    player.serverLevel().registryAccess()
                                            .lookupOrThrow(Registries.DAMAGE_TYPE)
                                            .getOrThrow(ModDamageTypes.HYPERTHERMIA));
                            net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
                            player.hurt(heatSource, 2.0f);
                            player.setDeltaMovement(motion);
                            player.addEffect(new MobEffectInstance(
                                    MobEffects.WEAKNESS, 60, 1, false, false, false));
                            player.displayClientMessage(
                                    Component.translatable("message.frozendawn.heat.unbearable"), true);
                            grantAdvancement(player, "too_hot_to_handle");
                            int armorTier = MobFreezeHandler.getFullSetTier(player);
                            if (armorTier >= 1 && armorTier <= 2) {
                                grantAdvancement(player, "insulation_both_ways");
                            }
                        } else if (temp >= 60f) {
                            // Warning: sweating, mild slowness
                            player.addEffect(new MobEffectInstance(
                                    MobEffects.WEAKNESS, 60, 0, false, false, false));
                            player.displayClientMessage(
                                    Component.translatable("message.frozendawn.heat.sweating"), true);
                        }
                    }
                }
                // Grant armor tier advancements
                int armorTier = MobFreezeHandler.getFullSetTier(player);
                for (int i = 1; i <= armorTier && i < ARMOR_ADVANCEMENTS.length; i++) {
                    if (ARMOR_ADVANCEMENTS[i] != null) {
                        grantAdvancement(player, ARMOR_ADVANCEMENTS[i]);
                    }
                }
            }
        }

        float progress = state.getProgress();

        // Wind chill exhaustion in phase 5+ (every 20 ticks = 1 second)
        if (currentPhase >= 5 && state.getApocalypseTicks() % 20 == 0) {
            // Phase 6 multiplier: wind chill is even harsher
            float phaseMult = currentPhase >= 6 ? 1.5f : 1.0f;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative() || player.isSpectator()) continue;
                if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
                // Only outdoors: above Y=50 and can see sky
                if (player.blockPosition().getY() < 50) continue;
                if (!player.level().canSeeSky(player.blockPosition().above())) continue;

                // Tier 2+ armor reduces wind chill; tier 3 negates it
                int armorTier = MobFreezeHandler.getFullSetTier(player);
                if (armorTier >= 3) continue; // EVA suit blocks wind chill
                float armorReduction = armorTier >= 2 ? 0.5f : (armorTier >= 1 ? 0.75f : 1.0f);

                // Sprinting = heavy drain, moving = moderate, standing still = light
                float exhaustion;
                if (player.isSprinting()) {
                    exhaustion = 1.0f * phaseMult * armorReduction;
                } else if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                    exhaustion = 0.4f * phaseMult * armorReduction;
                } else {
                    exhaustion = 0.2f * phaseMult * armorReduction;
                }
                player.getFoodData().addExhaustion(exhaustion);
            }
        }

        // Phase 6 late: atmospheric suffocation (every tick for responsiveness)
        // Uses own timer — no vanilla air supply (no bubble HUD)
        if (currentPhase >= 6 && progress >= 0.85f) {
            // Refresh habitable zone cache every 20 ticks (expensive block scan)
            boolean refreshCache = state.getApocalypseTicks() % 20 == 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative() || player.isSpectator()) continue;
                if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;

                java.util.UUID id = player.getUUID();
                if (refreshCache) {
                    habitableCache.put(id, isInHabitableZone(player));
                }
                if (Boolean.TRUE.equals(habitableCache.get(id))) {
                    // Safe zone: reset suffocation timer
                    suffocationTimer.put(id, 0);
                    continue;
                }

                // Full EVA suit protects from suffocation (but not in very late phase 6)
                if (MobFreezeHandler.getFullSetTier(player) >= 3 && progress < 0.95f) {
                    suffocationTimer.put(id, 0);
                    continue;
                }

                // Advance suffocation timer
                int ticks = suffocationTimer.getOrDefault(id, 0) + 1;
                suffocationTimer.put(id, ticks);
                float suffProgress = Math.min(1.0f, (float) ticks / SUFFOCATION_DURATION);

                // Escalating symptoms
                if (suffProgress >= 0.15f) {
                    // Vision tunneling
                    player.addEffect(new MobEffectInstance(
                            MobEffects.DARKNESS, 60, 0, false, false, false));
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.suffocate.lightheaded"), true);
                }
                if (suffProgress >= 0.40f) {
                    // Nausea + moderate slowness
                    player.addEffect(new MobEffectInstance(
                            MobEffects.CONFUSION, 100, 0, false, false, false));
                    player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false, false));
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.suffocate.nausea"), true);
                }
                if (suffProgress >= 0.70f) {
                    // Severe slowness — can barely move
                    player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false, false));
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.suffocate.fading"), true);
                }

                // Damage phase: every 20 ticks once timer is past the threshold
                if (suffProgress >= 1.0f && ticks % 20 == 0) {
                    player.displayClientMessage(
                            Component.translatable("message.frozendawn.suffocate.dying"), true);
                    DamageSource source = new DamageSource(
                            player.serverLevel().registryAccess()
                                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                                    .getOrThrow(ModDamageTypes.ATMOSPHERIC_SUFFOCATION));
                    net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
                    player.hurt(source, 2.0f);
                    player.setDeltaMovement(motion);
                }
            }
        }

        // Drive world systems in the overworld
        ServerLevel overworld = server.overworld();
        WeatherHandler.tick(overworld, currentPhase, progress);
        NetherSeveranceHandler.tick(overworld, currentPhase);
        BlockFreezer.tick(overworld, currentPhase, progress);
        VegetationDecay.tick(overworld, currentPhase);
        SnowAccumulator.tick(overworld, currentPhase, progress);
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
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ApocalypseState state = ApocalypseState.get(player.getServer());
            PacketDistributor.sendToPlayer(player, createPayload(state));

            // Grant any missed phase advancements
            grantPhaseAdvancements(player, state.getPhase());

            // Give Patchouli guide book on first join
            net.minecraft.nbt.CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.getBoolean("frozendawn:received_books")) {
                persistentData.putBoolean("frozendawn:received_books", true);
                net.minecraft.world.item.ItemStack guide = StarterBooks.createGuideBook();
                if (guide != null) player.getInventory().add(guide);
            }
        }
    }

    /**
     * Checks if a player is in a habitable zone.
     * Any enclosed space (can't see sky) has trapped air — safe from suffocation.
     * On the exposed surface, only areas near a Geothermal Core with O2 production are habitable.
     */
    private static boolean isInHabitableZone(ServerPlayer player) {
        // Under a roof / underground = trapped air = safe
        if (!player.level().canSeeSky(player.blockPosition().above())) return true;

        // Exposed to sky: check registered geothermal cores for O2 range
        net.minecraft.core.BlockPos playerPos = player.blockPosition();
        for (net.minecraft.core.BlockPos corePos : com.frozendawn.world.GeothermalCoreRegistry.getCores(player.level())) {
            int o2Range;
            net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(corePos);
            if (be instanceof com.frozendawn.block.GeothermalCoreBlockEntity core) {
                o2Range = core.getEffectiveO2Range();
            } else {
                o2Range = com.frozendawn.block.GeothermalCoreBlockEntity.BASE_O2_RANGE;
            }
            if (playerPos.distSqr(corePos) <= (long) o2Range * o2Range) {
                return true;
            }
        }
        return false;
    }

    /** Returns the last-calculated temperature for a player (updated every 40 ticks). */
    public static float getLastTemperature(java.util.UUID playerId) {
        return playerTemperatures.getOrDefault(playerId, 20f);
    }

    static void grantAdvancement(ServerPlayer player, String name) {
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
