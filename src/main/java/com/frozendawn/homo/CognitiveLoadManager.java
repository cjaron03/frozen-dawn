package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.CognitiveLoadState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.CognitiveLoadPayload;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server authority for the Heart's passive, per-player Cognitive Load. */
public final class CognitiveLoadManager {
    private static final TagKey<Item> COMFORT_ITEMS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "comfort_items"));
    private static final Map<UUID, CognitiveLoadPolicy.Relief> RELIEF_CACHE =
            new HashMap<>();
    private static final Map<UUID, Float> LAST_SYNCED_LOAD = new HashMap<>();
    private static final Map<UUID, Float> LAST_SYNCED_BREAKOUT = new HashMap<>();

    private CognitiveLoadManager() {
    }

    public static void tick(ServerLevel level, ApocalypseState apocalypse) {
        ReturnedHearthSavedData.HearthRecord heart = ReturnedHearthSavedData
                .get(level.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive)
                .orElse(null);
        BlockPos anchor = heart == null ? BlockPos.ZERO
                : heart.heartAnchor().orElse(heart.center());
        float fieldStrength = heart == null ? 0.0F : heart.heartFieldStrength();
        boolean heartLive = heart != null;

        for (ServerPlayer player : level.players()) {
            HeartEchoManager.tick(level, player,
                    player.getData(ModAttachments.COGNITIVE_LOAD), heart, anchor);
            tickPlayer(level, player, apocalypse, anchor, fieldStrength, heartLive);
        }
    }

    public static float getLoad(ServerPlayer player) {
        return player.getData(ModAttachments.COGNITIVE_LOAD).load();
    }

    public static void setLoadForDebug(ServerPlayer player, float load) {
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        state.setLoad(load);
        if (load < CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD) {
            state.setRememberedHotbarSlot(-1);
        }
        if (load < CognitiveLoadPolicy.TAKEOVER_THRESHOLD) {
            state.setTerminalTakeover(false);
            state.setTakeoverTicks(0);
            state.setBreakoutTicks(0.0F);
            state.setResistanceInput(0.0F, 0);
        }
        HeartContext heart = heartContext(player.serverLevel());
        sync(player, state.load(), heart.anchor(), heart.live(),
                CognitiveLoadPayload.EVENT_NONE, true);
    }

    public static float relieveFromHeartNode(ServerPlayer player, float amount) {
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        boolean endedTakeover = state.terminalTakeover();
        state.setLoad(state.load() - Math.max(0.0F, amount));
        if (state.load() < CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD) {
            state.setRememberedHotbarSlot(-1);
        }
        if (endedTakeover || state.load() < CognitiveLoadPolicy.TAKEOVER_THRESHOLD) {
            state.setTerminalTakeover(false);
            state.setTakeoverTicks(0);
            state.setBreakoutTicks(0.0F);
            state.setResistanceInput(0.0F, 0);
        }
        state.setLapseCooldownTicks(Math.max(state.lapseCooldownTicks(), 80));
        HeartContext heart = heartContext(player.serverLevel());
        sync(player, state.load(), heart.anchor(), heart.live(),
                endedTakeover ? CognitiveLoadPayload.EVENT_TAKEOVER_END
                        : CognitiveLoadPayload.EVENT_NONE,
                true);
        return state.load();
    }

    public static void clearForHeartErasure(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
            state.setLoad(0.0F);
            state.clearTransientEffects();
            state.setRememberedHotbarSlot(-1);
            state.setLapseCooldownTicks(0);
            sync(player, 0.0F, BlockPos.ZERO, false,
                    CognitiveLoadPayload.EVENT_TAKEOVER_END, true);
        }
    }

    public static String describe(ServerPlayer player) {
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        HeartContext heart = heartContext(player.serverLevel());
        float proximity = heart.live()
                ? proximity(player, heart.anchor(), state.load()) : 0.0F;
        CognitiveLoadPolicy.Relief relief = detectRelief(
                player.serverLevel(), player, ApocalypseState.get(player.getServer()));
        return "load=" + String.format(Locale.ROOT, "%.2f", state.load())
                + " stage=" + CognitiveLoadPolicy.stage(state.load())
                .name().toLowerCase(Locale.ROOT)
                + " proximity=" + String.format(Locale.ROOT, "%.3f", proximity)
                + " los=" + yesNo(heart.live()
                && hasHeartLineOfSight(player.serverLevel(), player, heart.anchor(), state.load()))
                + " relief=" + relief.name().toLowerCase(Locale.ROOT)
                + " takeover=" + yesNo(state.terminalTakeover())
                + " takeoverTicks=" + state.takeoverTicks()
                + " breakout=" + String.format(Locale.ROOT, "%.2f",
                CognitiveLoadPolicy.breakoutProgress(state.breakoutTicks()))
                + " heart=" + yesNo(heart.live());
    }

    public static void handleResistance(ServerPlayer player, float resistance) {
        if (!Float.isFinite(resistance)) {
            return;
        }
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        if (state.terminalTakeover()) {
            state.setResistanceInput(resistance, 6);
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        UUID id = player.getUUID();
        RELIEF_CACHE.remove(id);
        LAST_SYNCED_LOAD.remove(id);
        LAST_SYNCED_BREAKOUT.remove(id);
        HeartEchoManager.onPlayerLogout(player);
    }

    public static void reset() {
        RELIEF_CACHE.clear();
        LAST_SYNCED_LOAD.clear();
        LAST_SYNCED_BREAKOUT.clear();
        HeartEchoManager.reset();
    }

    private static void tickPlayer(
            ServerLevel level,
            ServerPlayer player,
            ApocalypseState apocalypse,
            BlockPos anchor,
            float fieldStrength,
            boolean heartLive) {
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        if (player.isSpectator()) {
            state.setLoad(0.0F);
            state.clearTransientEffects();
            sync(player, 0.0F, anchor, heartLive,
                    CognitiveLoadPayload.EVENT_NONE, false);
            return;
        }

        if (state.terminalTakeover()) {
            if (!heartLive) {
                state.clearTransientEffects();
                sync(player, state.load(), anchor, false,
                        CognitiveLoadPayload.EVENT_TAKEOVER_END, true);
                return;
            }
            int eventId = tickTakeover(level, player, state, anchor);
            sync(player, state.load(), anchor, heartLive,
                    eventId, eventId != CognitiveLoadPayload.EVENT_NONE);
            return;
        }

        float previous = state.load();
        CognitiveLoadPolicy.Relief relief = cachedRelief(level, player, apocalypse);
        float proximity = heartLive ? proximity(player, anchor, previous) : 0.0F;
        boolean lineOfSight = heartLive && proximity > 0.0F
                && hasHeartLineOfSight(level, player, anchor, previous);
        state.setLoad(CognitiveLoadPolicy.nextLoad(
                previous, proximity, lineOfSight, relief, fieldStrength));
        state.setLoad(Math.max(state.load(), HeartEchoManager.loadFloor(player)));

        tickHotbarFailure(player, state, previous);
        int eventId = tickMicroLapse(level, player, state);
        if (state.load() >= CognitiveLoadPolicy.TAKEOVER_THRESHOLD) {
            state.setLoad(CognitiveLoadPolicy.TAKEOVER_THRESHOLD);
            state.setTerminalTakeover(true);
            state.setTakeoverTicks(0);
            state.setBreakoutTicks(0.0F);
            state.setResistanceInput(0.0F, 0);
            eventId = CognitiveLoadPayload.EVENT_TAKEOVER_START;
        }
        sync(player, state.load(), anchor, heartLive, eventId, eventId != 0);
    }

    private static int tickTakeover(
            ServerLevel level,
            ServerPlayer player,
            CognitiveLoadState state,
            BlockPos anchor) {
        state.setLoad(CognitiveLoadPolicy.TAKEOVER_THRESHOLD);
        state.setTakeoverTicks(state.takeoverTicks() + 1);
        state.tickResistanceInput();
        state.setBreakoutTicks(CognitiveLoadPolicy.nextBreakoutTicks(
                state.breakoutTicks(), state.resistanceInput()));
        Vec3 toward = new Vec3(
                anchor.getX() + 0.5D - player.getX(),
                0.0D,
                anchor.getZ() + 0.5D - player.getZ());

        if (state.breakoutTicks() >= CognitiveLoadPolicy.BREAKOUT_REQUIRED_TICKS) {
            state.setTerminalTakeover(false);
            state.setTakeoverTicks(0);
            state.setBreakoutTicks(0.0F);
            state.setResistanceInput(0.0F, 0);
            state.setLoad(CognitiveLoadPolicy.BREAKOUT_RELEASE_LOAD);
            state.setLapseCooldownTicks(180);
            if (toward.lengthSqr() > 0.01D) {
                Vec3 release = toward.normalize().scale(-0.58D);
                player.setDeltaMovement(
                        release.x,
                        Math.max(0.18D, player.getDeltaMovement().y),
                        release.z);
                player.hurtMarked = true;
            }
            return CognitiveLoadPayload.EVENT_TAKEOVER_END;
        }

        if (toward.lengthSqr() > 1.0D) {
            Vec3 step = toward.normalize().scale(
                    CognitiveLoadPolicy.TERMINAL_PULL_ACCELERATION);
            BlockPos next = BlockPos.containing(
                    player.getX() + step.x,
                    player.getY(),
                    player.getZ() + step.z);
            if (isSafePlayerSpace(player.serverLevel(), next)) {
                Vec3 movement = player.getDeltaMovement().add(step.x, 0.0D, step.z);
                double horizontalSpeed = Math.sqrt(
                        movement.x * movement.x + movement.z * movement.z);
                if (horizontalSpeed > CognitiveLoadPolicy.TERMINAL_MAX_PULL_SPEED) {
                    double scale = CognitiveLoadPolicy.TERMINAL_MAX_PULL_SPEED
                            / horizontalSpeed;
                    movement = new Vec3(
                            movement.x * scale, movement.y, movement.z * scale);
                }
                player.setDeltaMovement(movement);
                player.hurtMarked = true;
            }
        }
        if (state.takeoverTicks() % CognitiveLoadPolicy.TERMINAL_DAMAGE_INTERVAL_TICKS == 0) {
            double horizontalDistance = Math.sqrt(toward.x * toward.x
                    + toward.z * toward.z);
            float damage = CognitiveLoadPolicy.terminalDamage(horizontalDistance);
            if (damage > 0.0F) {
                player.hurt(createHeartDamageSource(level), damage);
            }
        }
        return CognitiveLoadPayload.EVENT_NONE;
    }

    private static void tickHotbarFailure(
            ServerPlayer player, CognitiveLoadState state, float previousLoad) {
        if (state.load() < CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD) {
            state.setRememberedHotbarSlot(-1);
            state.setHotbarResetCooldownTicks(0);
            return;
        }
        if (previousLoad < CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD
                || state.rememberedHotbarSlot() < 0) {
            state.setRememberedHotbarSlot(player.getInventory().selected);
            state.setHotbarResetCooldownTicks(100 + player.getRandom().nextInt(81));
            return;
        }
        if (state.hotbarResetCooldownTicks() > 0) {
            state.setHotbarResetCooldownTicks(state.hotbarResetCooldownTicks() - 1);
            return;
        }
        int slot = state.rememberedHotbarSlot();
        if (player.getInventory().selected != slot) {
            player.getInventory().selected = slot;
            player.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
        int base = state.load() >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD ? 80 : 130;
        state.setHotbarResetCooldownTicks(base + player.getRandom().nextInt(81));
    }

    private static int tickMicroLapse(
            ServerLevel level, ServerPlayer player, CognitiveLoadState state) {
        if (HeartEchoManager.hasClarity(player)) {
            state.setLapseCooldownTicks(Math.max(
                    state.lapseCooldownTicks(), HeartEchoPolicy.CLARITY_TICKS));
            return CognitiveLoadPayload.EVENT_NONE;
        }
        if (state.load() < CognitiveLoadPolicy.MICRO_LAPSE_THRESHOLD) {
            state.setLapseCooldownTicks(0);
            return CognitiveLoadPayload.EVENT_NONE;
        }
        if (state.lapseCooldownTicks() == 0) {
            state.setLapseCooldownTicks(150 + player.getRandom().nextInt(151));
            return CognitiveLoadPayload.EVENT_NONE;
        }
        state.setLapseCooldownTicks(state.lapseCooldownTicks() - 1);
        if (state.lapseCooldownTicks() > 0) {
            return CognitiveLoadPayload.EVENT_NONE;
        }
        displaceSafely(level, player);
        state.setLapseCooldownTicks(180 + player.getRandom().nextInt(161));
        return CognitiveLoadPayload.EVENT_MICRO_LAPSE;
    }

    private static void displaceSafely(ServerLevel level, ServerPlayer player) {
        int start = player.getRandom().nextInt(8);
        for (int index = 0; index < 8; index++) {
            double angle = (start + index) * Math.PI / 4.0D;
            double distance = 1.8D + player.getRandom().nextDouble() * 1.4D;
            BlockPos candidate = BlockPos.containing(
                    player.getX() + Math.cos(angle) * distance,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * distance);
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos adjusted = candidate.offset(0, dy, 0);
                if (!isSafePlayerSpace(level, adjusted)) {
                    continue;
                }
                player.teleportTo(adjusted.getX() + 0.5D, adjusted.getY(),
                        adjusted.getZ() + 0.5D);
                return;
            }
        }
    }

    private static boolean isSafePlayerSpace(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) {
            return false;
        }
        return level.getBlockState(feet.below()).isSolidRender(level, feet.below())
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static CognitiveLoadPolicy.Relief cachedRelief(
            ServerLevel level, ServerPlayer player, ApocalypseState apocalypse) {
        UUID id = player.getUUID();
        if (Math.floorMod(level.getGameTime() + id.hashCode(), 10L) == 0L
                || !RELIEF_CACHE.containsKey(id)) {
            RELIEF_CACHE.put(id, detectRelief(level, player, apocalypse));
        }
        return RELIEF_CACHE.getOrDefault(id, CognitiveLoadPolicy.Relief.NONE);
    }

    private static CognitiveLoadPolicy.Relief detectRelief(
            ServerLevel level, ServerPlayer player, ApocalypseState apocalypse) {
        if (hasComfortItem(player) || hasActiveRemnantEmber(player)) {
            return CognitiveLoadPolicy.Relief.COMFORT;
        }
        if (TemperatureManager.getHeatSourceModifier(
                level, player.blockPosition(), apocalypse.getCurrentDay(),
                apocalypse.getTotalDays(), false) > 0.0F) {
            return CognitiveLoadPolicy.Relief.HEAT;
        }
        if (TemperatureManager.isEnclosed(level, player.blockPosition())) {
            return CognitiveLoadPolicy.Relief.SHELTER;
        }
        return CognitiveLoadPolicy.Relief.NONE;
    }

    private static boolean hasComfortItem(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(COMFORT_ITEMS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveRemnantEmber(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        return offhand.is(ModItems.REMNANT_EMBER.get())
                && offhand.getOrDefault(
                ModDataComponents.WARMTH_REMAINING.get(), 0) > 0;
    }

    private static float proximity(
            ServerPlayer player, BlockPos anchor, float load) {
        Vec3 heartPosition = virtualHeartPosition(anchor, load);
        return CognitiveLoadPolicy.proximity(player.getEyePosition().distanceTo(heartPosition));
    }

    private static boolean hasHeartLineOfSight(
            ServerLevel level, ServerPlayer player, BlockPos anchor, float load) {
        Vec3 origin = player.getEyePosition();
        Vec3 heart = virtualHeartPosition(anchor, load);
        return clearRay(level, player, origin, heart)
                || clearRay(level, player, origin, heart.add(0.0D, 7.0D, 0.0D))
                || clearRay(level, player, origin, heart.add(0.0D, -7.0D, 0.0D));
    }

    private static boolean clearRay(
            ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        if (!level.hasChunkAt(BlockPos.containing(to))) {
            return false;
        }
        HitResult result = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.MISS;
    }

    private static Vec3 virtualHeartPosition(BlockPos anchor, float load) {
        return Vec3.atCenterOf(anchor).add(
                0.0D,
                30.0D - CognitiveLoadPolicy.heartDescentBlocks(load),
                0.0D);
    }

    private static void sync(
            ServerPlayer player,
            float load,
            BlockPos anchor,
            boolean heartLive,
            int eventId,
            boolean force) {
        UUID id = player.getUUID();
        float previous = LAST_SYNCED_LOAD.getOrDefault(id, Float.NaN);
        CognitiveLoadState state = player.getData(ModAttachments.COGNITIVE_LOAD);
        float breakout = state.terminalTakeover()
                ? CognitiveLoadPolicy.breakoutProgress(state.breakoutTicks()) : 0.0F;
        float previousBreakout = LAST_SYNCED_BREAKOUT.getOrDefault(id, Float.NaN);
        boolean heartbeat = (player.tickCount % 20) == 0;
        if (!force && !heartbeat && !Float.isNaN(previous)
                && !Float.isNaN(previousBreakout)
                && Math.abs(previous - load) < 0.20F
                && Math.abs(previousBreakout - breakout) < 0.035F) {
            return;
        }
        LAST_SYNCED_LOAD.put(id, load);
        LAST_SYNCED_BREAKOUT.put(id, breakout);
        PacketDistributor.sendToPlayer(player, new CognitiveLoadPayload(
                load, anchor.asLong(), heartLive,
                state.terminalTakeover(), breakout,
                eventId));
    }

    private static DamageSource createHeartDamageSource(ServerLevel level) {
        return new DamageSource(
                level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.THAE_IVEN));
    }

    private static HeartContext heartContext(ServerLevel level) {
        ReturnedHearthSavedData.HearthRecord heart = ReturnedHearthSavedData
                .get(level.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive)
                .orElse(null);
        return heart == null
                ? new HeartContext(BlockPos.ZERO, false)
                : new HeartContext(heart.heartAnchor().orElse(heart.center()), true);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record HeartContext(BlockPos anchor, boolean live) {
    }
}
