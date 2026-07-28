package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ShadowFigureEntity;
import com.frozendawn.homo.CognitiveLoadPolicy;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.CognitiveLoadPayload;
import com.frozendawn.network.CognitiveResistancePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Client presentation and input consequences for server-owned Cognitive Load. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class CognitiveLoadClientState {
    private static final ResourceLocation[] STAGES = {
            texture("cognitive_load_1"),
            texture("cognitive_load_2"),
            texture("cognitive_load_3"),
            texture("cognitive_load_4")
    };
    private static final ResourceLocation[] HALLUCINATIONS = {
            texture("cognitive_hallucination_door"),
            texture("cognitive_hallucination_witness"),
            texture("cognitive_hallucination_identity"),
            texture("cognitive_hallucination_hearth")
    };
    private static final List<ShadowFigureEntity> WATCHERS = new ArrayList<>();
    private static final Map<KeyMapping, PendingInput> PENDING_INPUTS = new HashMap<>();
    private static final Map<KeyMapping, Integer> BYPASS_INPUTS = new HashMap<>();

    private static float targetLoad;
    private static float displayedLoad;
    private static long heartAnchor;
    private static boolean heartLive;
    private static boolean terminalTakeover;
    private static float targetBreakoutProgress;
    private static float displayedBreakoutProgress;
    private static float lastResistanceSent = -1.0F;
    private static int resistanceSendCooldown;
    private static float frozenOxygenRatio = -1.0F;
    private static int blackoutTicks;
    private static int hallucinationTicks;
    private static int hallucinationDuration;
    private static int hallucinationCooldown = 80;
    private static int hallucinationIndex;
    private static int hallucinationSeed;
    private static float hallucinationX;
    private static float hallucinationY;
    private static float hallucinationScale = 1.0F;
    private static int highToneTicks = 700;
    private static int choirDelayTicks = 240;
    private static int choirBurstTicks;
    private static int audioDuckDelayTicks = 260;
    private static int audioDuckTicks;
    private static int groanTicks = 520;
    private static CognitiveLoop infrasound;
    private static CognitiveLoop choir;

    private CognitiveLoadClientState() {
    }

    public static void update(CognitiveLoadPayload payload) {
        float previous = targetLoad;
        targetLoad = Mth.clamp(payload.load(), 0.0F, 100.0F);
        heartAnchor = payload.heartAnchor();
        heartLive = payload.heartLive();
        terminalTakeover = payload.terminalTakeover();
        targetBreakoutProgress = terminalTakeover
                ? Mth.clamp(payload.breakoutProgress(), 0.0F, 1.0F) : 0.0F;
        if (previous < CognitiveLoadPolicy.O2_FREEZE_THRESHOLD
                && targetLoad >= CognitiveLoadPolicy.O2_FREEZE_THRESHOLD) {
            snapshotOxygen();
        } else if (targetLoad < CognitiveLoadPolicy.O2_FREEZE_THRESHOLD) {
            frozenOxygenRatio = -1.0F;
        }
        if (payload.eventId() == CognitiveLoadPayload.EVENT_MICRO_LAPSE) {
            blackoutTicks = 6;
        } else if (payload.eventId() == CognitiveLoadPayload.EVENT_TAKEOVER_START) {
            blackoutTicks = Math.max(blackoutTicks, 4);
        } else if (payload.eventId() == CognitiveLoadPayload.EVENT_TAKEOVER_END) {
            terminalTakeover = false;
            targetBreakoutProgress = 0.0F;
            blackoutTicks = 0;
        }
    }

    /** Normalized load for renderers that need a 0-1 client-local value. */
    public static float load() {
        return displayedLoad / CognitiveLoadPolicy.MAX_LOAD;
    }

    public static float loadPercent() {
        return displayedLoad;
    }

    public static float heartDescentBlocks() {
        return heartLive ? CognitiveLoadPolicy.heartDescentBlocks(displayedLoad) : 0.0F;
    }

    public static boolean shouldFreezeOxygenTelemetry() {
        return FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && targetLoad >= CognitiveLoadPolicy.O2_FREEZE_THRESHOLD
                && frozenOxygenRatio >= 0.0F;
    }

    public static float frozenOxygenRatio() {
        return Mth.clamp(frozenOxygenRatio, 0.0F, 1.0F);
    }

    public static boolean shouldMuffleWorldSound(SoundInstance sound) {
        if (!FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                || !heartLive || isCognitiveSound(sound.getLocation())) {
            return false;
        }
        if (FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && blackoutTicks > 0) {
            return true;
        }
        if (audioDuckTicks > 0) {
            return sound.getSource() != SoundSource.MASTER
                    && sound.getSource() != SoundSource.MUSIC;
        }
        return targetLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD
                && (sound.getSource() == SoundSource.PLAYERS
                || sound.getSource() == SoundSource.BLOCKS);
    }

    public static float worldSoundVolumeFactor() {
        if (blackoutTicks > 0) {
            return 0.0F;
        }
        if (audioDuckTicks > 0) {
            return 0.18F;
        }
        return targetLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD ? 0.58F : 1.0F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        displayedLoad = Mth.approach(displayedLoad, targetLoad, 0.65F);
        displayedBreakoutProgress = Mth.approach(
                displayedBreakoutProgress, targetBreakoutProgress, 0.055F);
        if (blackoutTicks > 0) {
            blackoutTicks--;
        }
        if (terminalTakeover) {
            tickTakeover(minecraft);
        }
        if (shouldFreezeOxygenTelemetry()
                && minecraft.player.getXRot() > 35.0F
                && minecraft.player.tickCount % 10 == 0) {
            snapshotOxygen();
        }
        tickDelayedInputs();
        tickHallucination(minecraft);
        tickWatchers(minecraft);
        tickAudio(minecraft);
    }

    @SubscribeEvent
    public static void onInteractionInput(InputEvent.InteractionKeyMappingTriggered event) {
        KeyMapping mapping = event.getKeyMapping();
        int bypass = BYPASS_INPUTS.getOrDefault(mapping, 0);
        if (bypass > 0) {
            if (bypass == 1) {
                BYPASS_INPUTS.remove(mapping);
            } else {
                BYPASS_INPUTS.put(mapping, bypass - 1);
            }
            return;
        }
        int delay = CognitiveLoadPolicy.inputDelayTicks(targetLoad);
        if (!terminalTakeover && delay <= 0) {
            return;
        }
        if (!terminalTakeover && !PENDING_INPUTS.containsKey(mapping)) {
            PENDING_INPUTS.put(mapping, new PendingInput(
                    mapping, delay, event.isUseItem() ? 2 : 1));
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        int delay = CognitiveLoadPolicy.inputDelayTicks(targetLoad);
        if (!terminalTakeover && delay <= 0) {
            return;
        }
        InputConstants.Key pressed = InputConstants.getKey(
                event.getKey(), event.getScanCode());
        for (KeyMapping mapping : delayedGameplayMappings(minecraft)) {
            if (!mapping.getKey().equals(pressed) || !mapping.consumeClick()) {
                continue;
            }
            if (!terminalTakeover) {
                PENDING_INPUTS.putIfAbsent(
                        mapping, new PendingInput(mapping, delay, 1));
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && targetLoad >= CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD
                && event.getName().equals(VanillaGuiLayers.SCOREBOARD_SIDEBAR)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (displayedLoad <= 0.01F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int stageIndex = Math.max(0, Math.min(STAGES.length - 1,
                CognitiveLoadPolicy.stage(displayedLoad).ordinal() - 1));
        int barWidth = 112;
        int barHeight = 6;
        int x = (graphics.guiWidth() - barWidth) / 2;
        int y = 18;
        int iconSize = 22;
        int iconX = x - iconSize - 7;
        int iconY = y - 8;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.90F);
        graphics.blit(STAGES[stageIndex], iconX, iconY, iconSize, iconSize,
                0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        graphics.drawString(minecraft.font, "COGNITIVE LOAD", x, y - 10,
                0xFF76A8C5, false);
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1,
                0xD9070B12);
        int fill = Math.round(barWidth * displayedLoad / CognitiveLoadPolicy.MAX_LOAD);
        graphics.fill(x, y, x + fill, y + barHeight, 0xE31A3854);
        if (fill > 2) {
            graphics.fill(x, y, x + fill, y + 1, 0xE353CDE3);
            graphics.fill(x + fill - 1, y, x + fill, y + barHeight, 0xFF79EDFF);
        }
        graphics.drawString(minecraft.font, Math.round(displayedLoad) + "%",
                x + barWidth + 6, y - 1, 0xFF9BB7C8, false);

        if (terminalTakeover || displayedBreakoutProgress > 0.01F) {
            int resistanceY = y + 17;
            int resistanceFill = Math.round(barWidth * displayedBreakoutProgress);
            graphics.drawString(minecraft.font, "RESISTANCE", x, resistanceY - 9,
                    0xFFC5DDE8, false);
            graphics.fill(x - 1, resistanceY - 1,
                    x + barWidth + 1, resistanceY + 4, 0xD9070B12);
            graphics.fill(x, resistanceY,
                    x + resistanceFill, resistanceY + 3, 0xE345A7BA);
            if (resistanceFill > 2) {
                graphics.fill(x, resistanceY,
                        x + resistanceFill, resistanceY + 1, 0xF09CF5FF);
            }
        }

        if (FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && displayedLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD) {
            renderPeripheralFailure(graphics);
        }
        if (FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && hallucinationTicks > 0) {
            renderHallucination(graphics, minecraft);
        }
        if (FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && blackoutTicks > 0) {
            float pulse = 1.0F - Math.abs(blackoutTicks - 3.0F) / 3.0F;
            int alpha = Math.round(Mth.clamp(pulse, 0.30F, 1.0F) * 242.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    alpha << 24);
        }
        RenderSystem.disableBlend();
    }

    public static void reset() {
        targetLoad = 0.0F;
        displayedLoad = 0.0F;
        heartAnchor = 0L;
        heartLive = false;
        terminalTakeover = false;
        targetBreakoutProgress = 0.0F;
        displayedBreakoutProgress = 0.0F;
        lastResistanceSent = -1.0F;
        resistanceSendCooldown = 0;
        frozenOxygenRatio = -1.0F;
        blackoutTicks = 0;
        hallucinationTicks = 0;
        hallucinationDuration = 0;
        hallucinationCooldown = 80;
        PENDING_INPUTS.clear();
        BYPASS_INPUTS.clear();
        discardWatchers();
        stopAudio();
        highToneTicks = 700;
        choirDelayTicks = 240;
        choirBurstTicks = 0;
        audioDuckDelayTicks = 260;
        audioDuckTicks = 0;
        groanTicks = 520;
    }

    private static void tickDelayedInputs() {
        if (targetLoad < CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD) {
            PENDING_INPUTS.clear();
            BYPASS_INPUTS.clear();
            return;
        }
        Iterator<Map.Entry<KeyMapping, PendingInput>> iterator =
                PENDING_INPUTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<KeyMapping, PendingInput> entry = iterator.next();
            PendingInput pending = entry.getValue();
            if (pending.remainingTicks() > 1) {
                entry.setValue(pending.tick());
                continue;
            }
            BYPASS_INPUTS.merge(pending.mapping(), pending.replayEvents(), Integer::sum);
            InputConstants.Key key = pending.mapping().getKey();
            KeyMapping.click(key);
            iterator.remove();
        }
    }

    private static List<KeyMapping> delayedGameplayMappings(Minecraft minecraft) {
        List<KeyMapping> mappings = new ArrayList<>(
                List.of(
                        minecraft.options.keyInventory,
                        minecraft.options.keyDrop,
                        minecraft.options.keySwapOffhand,
                        minecraft.options.keyAdvancements,
                        minecraft.options.keyLoadHotbarActivator,
                        minecraft.options.keySaveHotbarActivator));
        mappings.addAll(List.of(minecraft.options.keyHotbarSlots));
        return mappings;
    }

    private static void tickTakeover(Minecraft minecraft) {
        if (minecraft.player == null || !heartLive) {
            return;
        }
        BlockPos anchor = BlockPos.of(heartAnchor);
        Vec3 toward = new Vec3(
                anchor.getX() + 0.5D - minecraft.player.getX(),
                0.0D,
                anchor.getZ() + 0.5D - minecraft.player.getZ());
        if (toward.lengthSqr() > 1.0D) {
            Vec3 motion = minecraft.player.getDeltaMovement().add(
                    toward.normalize().scale(
                            CognitiveLoadPolicy.TERMINAL_PULL_ACCELERATION));
            double horizontalSpeed = Math.sqrt(
                    motion.x * motion.x + motion.z * motion.z);
            if (horizontalSpeed > CognitiveLoadPolicy.TERMINAL_MAX_PULL_SPEED) {
                double scale = CognitiveLoadPolicy.TERMINAL_MAX_PULL_SPEED
                        / horizontalSpeed;
                motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
            }
            minecraft.player.setDeltaMovement(motion);
        }
        float resistance = resistanceIntent(minecraft, toward);
        if (--resistanceSendCooldown <= 0
                || Math.abs(resistance - lastResistanceSent) >= 0.08F) {
            PacketDistributor.sendToServer(new CognitiveResistancePayload(resistance));
            lastResistanceSent = resistance;
            resistanceSendCooldown = 3;
        }
    }

    private static float resistanceIntent(Minecraft minecraft, Vec3 towardHeart) {
        if (minecraft.player == null || minecraft.screen != null
                || towardHeart.lengthSqr() < 0.01D) {
            return 0.0F;
        }
        float forwardImpulse = minecraft.player.input.forwardImpulse;
        float leftImpulse = minecraft.player.input.leftImpulse;
        double yaw = Math.toRadians(minecraft.player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 intent = forward.scale(forwardImpulse).add(left.scale(leftImpulse));
        double intentLength = intent.length();
        if (intentLength < 0.01D) {
            return 0.0F;
        }
        Vec3 away = towardHeart.normalize().scale(-1.0D);
        double alignment = intent.normalize().dot(away);
        return Mth.clamp((float) (Math.max(0.0D, alignment)
                * Math.min(1.0D, intentLength)), 0.0F, 1.0F);
    }

    private static void tickHallucination(Minecraft minecraft) {
        if (!FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                || !heartLive
                || displayedLoad < CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD) {
            hallucinationTicks = 0;
            hallucinationCooldown = 80;
            return;
        }
        if (hallucinationTicks > 0) {
            hallucinationTicks--;
            return;
        }
        if (terminalTakeover) {
            hallucinationCooldown = Math.min(hallucinationCooldown, 8);
        }
        if (--hallucinationCooldown > 0) {
            return;
        }
        float intensity = Mth.clamp(
                (displayedLoad - CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD)
                        / (CognitiveLoadPolicy.MAX_LOAD
                        - CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD),
                0.0F, 1.0F);
        hallucinationDuration = 22 + minecraft.level.random.nextInt(25)
                + Math.round(intensity * 18.0F);
        hallucinationTicks = hallucinationDuration;
        int availableImages = Mth.clamp(
                2 + Mth.floor(intensity * 3.0F), 2, HALLUCINATIONS.length);
        hallucinationIndex = minecraft.level.random.nextInt(availableImages);
        hallucinationSeed = minecraft.level.random.nextInt();
        hallucinationX = minecraft.level.random.nextFloat();
        hallucinationY = minecraft.level.random.nextFloat();
        hallucinationScale = 0.78F + minecraft.level.random.nextFloat() * 0.58F;
        int minimumDelay = terminalTakeover ? 6 : Math.round(Mth.lerp(intensity, 105.0F, 28.0F));
        int randomDelay = terminalTakeover ? 18 : Math.round(Mth.lerp(intensity, 155.0F, 55.0F));
        hallucinationCooldown = minimumDelay
                + minecraft.level.random.nextInt(Math.max(1, randomDelay));
    }

    private static void snapshotOxygen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        AirStatusTelemetry.TankTelemetry telemetry =
                AirStatusTelemetry.getTankTelemetry(minecraft.player);
        if (!telemetry.hasAnyTank() || telemetry.maxO2() <= 0) {
            frozenOxygenRatio = -1.0F;
            return;
        }
        frozenOxygenRatio = Mth.clamp(
                telemetry.totalO2() / (float) telemetry.maxO2(), 0.0F, 1.0F);
    }

    private static void tickWatchers(Minecraft minecraft) {
        int desired = FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                && heartLive ? CognitiveLoadPolicy.watcherCount(displayedLoad) : 0;
        if (desired == 0 || minecraft.player == null
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(BlockPos.of(heartAnchor)))
                > 150.0D * 150.0D) {
            discardWatchers();
            return;
        }
        WATCHERS.removeIf(ShadowFigureEntity::isRemoved);
        while (WATCHERS.size() < desired) {
            ShadowFigureEntity watcher = ModEntities.SHADOW_FIGURE.get()
                    .create(minecraft.level);
            if (watcher == null) {
                break;
            }
            watcher.setWatcher(true);
            minecraft.level.addEntity(watcher);
            WATCHERS.add(watcher);
        }
        while (WATCHERS.size() > desired) {
            ShadowFigureEntity watcher = WATCHERS.removeLast();
            watcher.startFading(20);
        }

        BlockPos anchor = BlockPos.of(heartAnchor);
        double time = minecraft.level.getGameTime() * 0.0018D;
        for (int index = 0; index < WATCHERS.size(); index++) {
            ShadowFigureEntity watcher = WATCHERS.get(index);
            double angle = index * Math.PI * 2.0D / Math.max(1, WATCHERS.size())
                    + time + (index % 3) * 0.12D;
            double radius = 47.0D + (index % 4) * 2.15D;
            int x = Mth.floor(anchor.getX() + 0.5D + Math.cos(angle) * radius);
            int z = Mth.floor(anchor.getZ() + 0.5D + Math.sin(angle) * radius);
            BlockPos column = new BlockPos(x, anchor.getY(), z);
            int y = minecraft.level.hasChunkAt(column)
                    ? minecraft.level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    : anchor.getY();
            watcher.setPos(x + 0.5D, y, z + 0.5D);
        }
    }

    private static void discardWatchers() {
        for (ShadowFigureEntity watcher : WATCHERS) {
            if (!watcher.isRemoved()) {
                watcher.startFading(12);
            }
        }
        WATCHERS.clear();
    }

    private static void tickAudio(Minecraft minecraft) {
        if (!FrozenDawnConfig.ENABLE_COGNITIVE_LOAD_EFFECTS.get()
                || !heartLive || MasterArchitectFloodClient.isActive()) {
            fadeAudio(0.0F, 0.0F);
            audioDuckTicks = 0;
            return;
        }
        float stageTwo = Mth.clamp(
                (displayedLoad - CognitiveLoadPolicy.O2_FREEZE_THRESHOLD) / 25.0F,
                0.0F, 1.0F);
        float stageFour = Mth.clamp(
                (displayedLoad - CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD) / 25.0F,
                0.0F, 1.0F);
        float pulse = 0.78F + 0.22F
                * Mth.sin(minecraft.player.tickCount * 0.045F);
        float infraTarget = (0.10F + stageTwo * 0.09F + stageFour * 0.10F)
                * (stageFour > 0.0F ? pulse : 1.0F);

        if (displayedLoad >= CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD) {
            if (choirBurstTicks > 0) {
                choirBurstTicks--;
            } else if (--choirDelayTicks <= 0) {
                choirBurstTicks = displayedLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD
                        ? 100 : 40 + minecraft.level.random.nextInt(41);
                choirDelayTicks = displayedLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD
                        ? 40 + minecraft.level.random.nextInt(51)
                        : 180 + minecraft.level.random.nextInt(241);
            }
            if (audioDuckTicks > 0) {
                audioDuckTicks--;
            } else if (--audioDuckDelayTicks <= 0) {
                audioDuckTicks = 20 + minecraft.level.random.nextInt(21);
                audioDuckDelayTicks = 180 + minecraft.level.random.nextInt(261);
            }
            if (--groanTicks <= 0) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.PLAYER_HURT_FREEZE,
                        0.58F + minecraft.level.random.nextFloat() * 0.10F,
                        displayedLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD
                                ? 0.19F : 0.11F));
                groanTicks = displayedLoad >= CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD
                        ? 260 + minecraft.level.random.nextInt(281)
                        : 400 + minecraft.level.random.nextInt(401);
            }
        } else {
            choirBurstTicks = 0;
            audioDuckTicks = 0;
        }

        if (displayedLoad >= CognitiveLoadPolicy.O2_FREEZE_THRESHOLD
                && --highToneTicks <= 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSounds.THAEVEN_CONTACT.get(),
                    1.32F + minecraft.level.random.nextFloat() * 0.12F,
                    0.13F));
            highToneTicks = 600 + minecraft.level.random.nextInt(601);
        }
        float choirTarget = choirBurstTicks > 0
                ? 0.08F + stageFour * 0.14F : 0.0F;
        fadeAudio(displayedLoad >= CognitiveLoadPolicy.O2_FREEZE_THRESHOLD
                ? infraTarget : 0.0F, choirTarget);
    }

    private static void fadeAudio(float infrasoundTarget, float choirTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (infrasoundTarget > 0.001F && infrasound == null) {
            infrasound = new CognitiveLoop(ModSounds.MASTER_ARCHITECT_INFRASOUND.get());
            minecraft.getSoundManager().play(infrasound);
        }
        if (choirTarget > 0.001F && choir == null) {
            choir = new CognitiveLoop(ModSounds.THAE_IVEN_HEART_FORMATION.get());
            minecraft.getSoundManager().play(choir);
        }
        if (infrasound != null) {
            infrasound.setTarget(infrasoundTarget);
            if (infrasound.isStopped()) {
                infrasound = null;
            }
        }
        if (choir != null) {
            choir.setTarget(choirTarget);
            if (choir.isStopped()) {
                choir = null;
            }
        }
    }

    private static void stopAudio() {
        if (infrasound != null) {
            infrasound.stopNow();
            infrasound = null;
        }
        if (choir != null) {
            choir.stopNow();
            choir = null;
        }
    }

    private static boolean isCognitiveSound(ResourceLocation location) {
        if (!location.getNamespace().equals(FrozenDawn.MOD_ID)) {
            return false;
        }
        String path = location.getPath();
        return path.equals("entity.master_architect.infrasound")
                || path.equals("entity.thae_iven_heart.formation")
                || path.equals("ui.thaeven_contact");
    }

    private static void renderPeripheralFailure(GuiGraphics graphics) {
        float strength = Mth.clamp(
                (displayedLoad - CognitiveLoadPolicy.INPUT_DELAY_THRESHOLD) / 25.0F,
                0.0F, 1.0F);
        int tintAlpha = Math.round(strength * 20.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                (tintAlpha << 24) | 0x0013212B);
        int band = Math.max(12, Math.min(graphics.guiWidth(), graphics.guiHeight()) / 9);
        for (int layer = 0; layer < 5; layer++) {
            float layerStrength = strength * (1.0F - layer * 0.17F);
            int alpha = Math.round(layerStrength * 46.0F);
            int inset = layer * Math.max(3, band / 8);
            int width = Math.max(3, band / 5);
            int color = alpha << 24;
            graphics.fill(inset, inset, inset + width,
                    graphics.guiHeight() - inset, color);
            graphics.fill(graphics.guiWidth() - inset - width, inset,
                    graphics.guiWidth() - inset, graphics.guiHeight() - inset, color);
            graphics.fill(inset, inset, graphics.guiWidth() - inset,
                    inset + width, color);
            graphics.fill(inset, graphics.guiHeight() - inset - width,
                    graphics.guiWidth() - inset, graphics.guiHeight() - inset, color);
        }
    }

    private static void renderHallucination(
            GuiGraphics graphics, Minecraft minecraft) {
        float intensity = Mth.clamp(
                (displayedLoad - CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD)
                        / (CognitiveLoadPolicy.MAX_LOAD
                        - CognitiveLoadPolicy.MEMORY_FAILURE_THRESHOLD),
                0.0F, 1.0F);
        float age = hallucinationDuration - hallucinationTicks;
        float fadeIn = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(hallucinationTicks / 11.0F, 0.0F, 1.0F);
        float pulse = 0.74F + 0.26F * Mth.sin(age * 0.83F + hallucinationSeed);
        float alpha = (0.15F + 0.31F * intensity) * fadeIn * fadeOut * pulse;
        if (alpha <= 0.005F) {
            return;
        }
        int minimumDimension = Math.min(graphics.guiWidth(), graphics.guiHeight());
        int size = Math.round(minimumDimension
                * Mth.lerp(intensity, 0.34F, 0.58F) * hallucinationScale);
        size = Mth.clamp(size, 82, Math.max(82, minimumDimension - 18));
        float driftX = Mth.sin(age * 0.19F + hallucinationSeed * 0.01F) * 7.0F;
        float driftY = -age * (0.06F + intensity * 0.05F);
        int xRange = Math.max(1, graphics.guiWidth() - size);
        int yRange = Math.max(1, graphics.guiHeight() - size);
        int x = Math.round(hallucinationX * xRange + driftX);
        int y = Math.round(hallucinationY * yRange + driftY);
        ResourceLocation texture = HALLUCINATIONS[hallucinationIndex];

        RenderSystem.setShaderColor(0.08F, 0.20F, 0.48F, alpha * 0.44F);
        graphics.blit(texture, x - 7, y + 2, size, size,
                0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(0.16F, 0.92F, 1.0F, alpha * 0.35F);
        graphics.blit(texture, x + 6, y - 2, size, size,
                0.0F, 0.0F, 256, 256, 256, 256);
        renderTornHallucination(graphics, texture, x, y, size, alpha, age);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderTornHallucination(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int size,
            float alpha,
            float age) {
        int slices = 14;
        RenderSystem.setShaderColor(0.62F, 0.90F, 1.0F, alpha);
        for (int slice = 0; slice < slices; slice++) {
            int sourceTop = slice * 256 / slices;
            int sourceBottom = (slice + 1) * 256 / slices;
            int sourceHeight = sourceBottom - sourceTop;
            int destinationTop = y + slice * size / slices;
            int destinationBottom = y + (slice + 1) * size / slices;
            int destinationHeight = Math.max(1, destinationBottom - destinationTop);
            float tearWave = Mth.sin(
                    hallucinationSeed * 0.013F + slice * 1.91F + age * 0.31F);
            int tear = Math.round(tearWave * (3.0F + alpha * 18.0F));
            if ((slice + hallucinationSeed) % 5 == 0) {
                tear += Mth.floor(Mth.sin(age * 0.77F + slice) * 9.0F);
            }
            graphics.blit(texture, x + tear, destinationTop, size, destinationHeight,
                    0.0F, sourceTop, 256, sourceHeight, 256, 256);
        }
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "textures/gui/" + name + ".png");
    }

    private record PendingInput(
            KeyMapping mapping, int remainingTicks, int replayEvents) {
        private PendingInput tick() {
            return new PendingInput(mapping, remainingTicks - 1, replayEvents);
        }
    }

    private static final class CognitiveLoop extends AbstractTickableSoundInstance {
        private float target;

        private CognitiveLoop(SoundEvent sound) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            relative = true;
            looping = true;
            attenuation = Attenuation.NONE;
            volume = 0.0F;
            pitch = 1.0F;
        }

        private void setTarget(float target) {
            this.target = Mth.clamp(target, 0.0F, 1.0F);
        }

        private void stopNow() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = Mth.approach(volume, target, target > volume ? 0.012F : 0.025F);
            if (target <= 0.0F && volume <= 0.001F) {
                stop();
            }
        }
    }
}
