package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HeartMemoryNodeEventPayload;
import com.frozendawn.network.HeartMemoryNodeStrikePayload;
import com.frozendawn.network.HeartMaeveErasePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client selection, impact particles, and non-locking inner-memory presentation. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class HeartMemoryNodeClient {
    private static final int MEMORY_DURATION_TICKS = 220;
    private static int memoryTicks;
    private static int memoryNode = -1;
    private static int memoryVariant;
    private static int memoryVisits;
    private static int memoryCasualties;
    private static int maeveChannelTicks;
    private static UUID maeveChannelHeartId;

    private HeartMemoryNodeClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteractionInput(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isCanceled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null) {
            return;
        }
        ThaeIvenHeartEntity selected = selectedHeart(minecraft);
        if (event.isUseItem() && selected != null
                && maeveTargeted(minecraft, selected)) {
            event.setCanceled(true);
            return;
        }
        if (!event.isAttack()) {
            return;
        }
        if (selected == null) {
            return;
        }
        int nodeIndex = HeartLattice.nextNode(selected.destroyedNodeMask());
        boolean echoExposed = HeartEchoClient.isNodeExposed(nodeIndex);
        if (!HeartLattice.isNodeHittable(
                nodeIndex, CognitiveLoadClientState.loadPercent(), echoExposed)) {
            return;
        }
        Vec3 nodePosition = HeartLattice.nodePosition(
                BlockPos.of(selected.anchor()), selected.layoutSeed(),
                CognitiveLoadClientState.loadPercent(), nodeIndex);
        if (!HeartLattice.raySelectsNode(
                minecraft.player.getEyePosition(),
                minecraft.player.getViewVector(1.0F),
                nodePosition)) {
            return;
        }
        PacketDistributor.sendToServer(
                new HeartMemoryNodeStrikePayload(
                        nodeIndex, CognitiveLoadClientState.loadPercent()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (memoryTicks > 0) {
            memoryTicks--;
        }
        tickMaeveChannel();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void handleEvent(HeartMemoryNodeEventPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ThaeIvenHeartEntity heart = minecraft.level.getEntity(payload.entityId())
                instanceof ThaeIvenHeartEntity found ? found : selectedHeart(minecraft);
        if (heart != null) {
            Vec3 node = HeartLattice.nodePosition(
                    BlockPos.of(heart.anchor()), heart.layoutSeed(),
                    CognitiveLoadClientState.loadPercent(), payload.nodeIndex());
            spawnImpact(minecraft, node,
                    payload.hitProgress() >= HeartLattice.HITS_PER_NODE);
            minecraft.level.playLocalSound(
                    node.x, node.y, node.z,
                    payload.hitProgress() >= HeartLattice.HITS_PER_NODE
                            ? ModSounds.THAEVEN_RESOLVE.get()
                            : ModSounds.THAEVEN_INTERRUPT.get(),
                    SoundSource.AMBIENT,
                    payload.hitProgress() >= HeartLattice.HITS_PER_NODE
                            ? 1.35F : 0.92F,
                    payload.hitProgress() >= HeartLattice.HITS_PER_NODE
                            ? 0.54F : 0.88F + payload.hitProgress() * 0.07F,
                    false);
        }
        if (payload.showMemory()) {
            memoryNode = payload.nodeIndex();
            memoryVariant = payload.memoryVariant();
            memoryVisits = payload.visits();
            memoryCasualties = payload.casualties();
            memoryTicks = MEMORY_DURATION_TICKS;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (memoryTicks <= 0 || memoryNode < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float age = MEMORY_DURATION_TICKS - memoryTicks
                + deltaTracker.getGameTimeDeltaPartialTick(false);
        float fadeIn = Mth.clamp(age / 18.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(memoryTicks / 35.0F, 0.0F, 1.0F);
        float alpha = fadeIn * fadeOut;
        int centerX = graphics.guiWidth() / 2;
        int startY = Math.max(44, graphics.guiHeight() / 2 - 56);
        List<Component> lines = memoryLines();

        RenderSystem.enableBlend();
        int backingAlpha = Math.round(alpha * 122.0F);
        graphics.fill(centerX - 164, startY - 13,
                centerX + 164, startY + 18 + lines.size() * 13,
                backingAlpha << 24 | 0x00030A11);
        int titleColor = withAlpha(0x7DEBFF, alpha);
        Component title = Component.translatable(
                "overlay.frozendawn.heart_memory.node_" + (memoryNode + 1) + ".title");
        graphics.drawCenteredString(minecraft.font, title, centerX, startY,
                titleColor);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawCenteredString(
                    minecraft.font,
                    lines.get(index),
                    centerX,
                    startY + 17 + index * 13,
                    withAlpha(index == lines.size() - 1
                            ? 0x9CCEDC : 0xC8EAF3, alpha));
        }
        RenderSystem.disableBlend();
    }

    public static void reset() {
        memoryTicks = 0;
        memoryNode = -1;
        memoryVariant = 0;
        memoryVisits = 0;
        memoryCasualties = 0;
        maeveChannelTicks = 0;
        maeveChannelHeartId = null;
    }

    public static float maeveChannelProgress(ThaeIvenHeartEntity heart) {
        return maeveChannelHeartId != null
                && maeveChannelHeartId.equals(heart.getUUID())
                ? Mth.clamp(maeveChannelTicks
                / (float) com.frozendawn.homo.HeartMaeveErasurePolicy.CHANNEL_TICKS,
                0.0F, 1.0F)
                : 0.0F;
    }

    private static void tickMaeveChannel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null
                || !minecraft.options.keyUse.isDown()) {
            clearMaeveChannel();
            return;
        }
        ThaeIvenHeartEntity heart = selectedHeart(minecraft);
        if (heart == null || !maeveTargeted(minecraft, heart)) {
            clearMaeveChannel();
            return;
        }
        if (!heart.getUUID().equals(maeveChannelHeartId)) {
            maeveChannelHeartId = heart.getUUID();
            maeveChannelTicks = 0;
        }
        maeveChannelTicks++;
        PacketDistributor.sendToServer(new HeartMaeveErasePayload());
        Vec3 maeve = HeartLattice.maevePosition(BlockPos.of(heart.anchor()));
        if ((maeveChannelTicks & 1) == 0) {
            double angle = minecraft.level.random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.2D + minecraft.level.random.nextDouble() * 1.4D;
            minecraft.level.addParticle(
                    maeveChannelTicks % 6 == 0
                            ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                    maeve.x + Math.cos(angle) * radius,
                    maeve.y + (minecraft.level.random.nextDouble() - 0.5D) * 2.4D,
                    maeve.z + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.08D, 0.0D,
                    -Math.sin(angle) * 0.08D);
        }
        if (maeveChannelTicks % 20 == 1) {
            minecraft.level.playLocalSound(
                    maeve.x, maeve.y, maeve.z,
                    ModSounds.THAEVEN_INTERRUPT.get(),
                    SoundSource.AMBIENT, 0.75F,
                    0.52F + maeveChannelProgress(heart) * 0.42F, false);
        }
    }

    private static boolean maeveTargeted(
            Minecraft minecraft, ThaeIvenHeartEntity heart) {
        if (!heart.maeveExposed() || heart.maeveErasureProgress() > 0.0F
                || minecraft.player == null) {
            return false;
        }
        Vec3 maeve = HeartLattice.maevePosition(BlockPos.of(heart.anchor()));
        Vec3 eye = minecraft.player.getEyePosition();
        return eye.distanceToSqr(maeve)
                <= HeartLattice.MAX_MAEVE_INTERACTION_DISTANCE
                * HeartLattice.MAX_MAEVE_INTERACTION_DISTANCE
                && HeartLattice.raySelectsMaeve(
                eye, minecraft.player.getViewVector(1.0F), maeve);
    }

    private static void clearMaeveChannel() {
        maeveChannelTicks = 0;
        maeveChannelHeartId = null;
    }

    private static ThaeIvenHeartEntity selectedHeart(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        ThaeIvenHeartEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ThaeIvenHeartEntity heart)
                    || heart.formationStage() != HeartFormationStage.LIVE) {
                continue;
            }
            double distance = minecraft.player.distanceToSqr(heart);
            if (distance < bestDistance) {
                best = heart;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void spawnImpact(
            Minecraft minecraft, Vec3 node, boolean destroyed) {
        int count = destroyed ? 72 : 24;
        for (int index = 0; index < count; index++) {
            double angle = minecraft.level.random.nextDouble() * Math.PI * 2.0D;
            double speed = (destroyed ? 0.06D : 0.025D)
                    + minecraft.level.random.nextDouble() * (destroyed ? 0.16D : 0.07D);
            double y = (minecraft.level.random.nextDouble() - 0.5D)
                    * (destroyed ? 0.22D : 0.09D);
            minecraft.level.addParticle(
                    index % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME
                            : index % 3 == 1 ? ParticleTypes.REVERSE_PORTAL
                            : ParticleTypes.END_ROD,
                    node.x, node.y, node.z,
                    Math.cos(angle) * speed, y, Math.sin(angle) * speed);
        }
    }

    private static List<Component> memoryLines() {
        List<Component> lines = new ArrayList<>();
        if (memoryNode < HeartLattice.NODE_COUNT - 1) {
            for (int index = 1; index <= 3; index++) {
                lines.add(Component.translatable(
                        "overlay.frozendawn.heart_memory.node_"
                                + (memoryNode + 1) + ".line_" + index));
            }
            return lines;
        }
        lines.add(Component.translatable(
                "overlay.frozendawn.heart_memory.node_5.variant_"
                        + Mth.clamp(memoryVariant, 0, 3)));
        lines.add(Component.translatable(
                "overlay.frozendawn.heart_memory.node_5.visits", memoryVisits));
        lines.add(Component.translatable(
                "overlay.frozendawn.heart_memory.node_5.casualties",
                memoryCasualties));
        lines.add(Component.translatable(
                "overlay.frozendawn.heart_memory.node_5.erased"));
        return lines;
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24
                | color & 0x00FFFFFF;
    }
}
