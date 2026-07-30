package com.frozendawn.client;

import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.CognitiveLoadPolicy;
import com.frozendawn.homo.HeartCollapseStage;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Client-local atmosphere drain derived from the synchronized Heart entity. */
public final class HeartQuietClient {
    private static long sampledGameTime = Long.MIN_VALUE;
    private static int destroyedNodes;
    private static float environmentMultiplier = 1.0F;

    private HeartQuietClient() {
    }

    public static int destroyedNodes() {
        refresh();
        return destroyedNodes;
    }

    public static float environmentMultiplier() {
        refresh();
        return environmentMultiplier;
    }

    public static void reset() {
        sampledGameTime = Long.MIN_VALUE;
        destroyedNodes = 0;
        environmentMultiplier = 1.0F;
    }

    private static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (sampledGameTime == gameTime) {
            return;
        }
        sampledGameTime = gameTime;

        AABB search = minecraft.player.getBoundingBox().inflate(
                CognitiveLoadPolicy.EFFECT_RADIUS + 16.0D,
                96.0D,
                CognitiveLoadPolicy.EFFECT_RADIUS + 16.0D);
        ThaeIvenHeartEntity heart = minecraft.level.getEntitiesOfClass(
                        ThaeIvenHeartEntity.class,
                        search,
                        entity -> entity.formationStage() == HeartFormationStage.LIVE)
                .stream()
                .min(Comparator.comparingDouble(
                        entity -> entity.distanceToSqr(minecraft.player)))
                .orElse(null);
        if (heart == null) {
            destroyedNodes = 0;
            environmentMultiplier = 1.0F;
            return;
        }

        destroyedNodes = HeartLattice.destroyedCount(heart.destroyedNodeMask());
        float nodeMultiplier = switch (destroyedNodes) {
            case 0 -> 1.0F;
            case 1 -> 0.78F;
            case 2 -> 0.52F;
            case 3 -> 0.28F;
            case 4 -> 0.07F;
            default -> 0.0F;
        };
        if (heart.collapseStage() != HeartCollapseStage.NONE) {
            nodeMultiplier = 0.0F;
        }

        Vec3 anchor = Vec3.atCenterOf(net.minecraft.core.BlockPos.of(heart.anchor()));
        double horizontalDistance = Math.sqrt(Math.max(
                0.0D,
                minecraft.player.distanceToSqr(anchor)
                        - Mth.square(minecraft.player.getY() - anchor.y)));
        float influence = 1.0F - (float) Mth.clamp(
                (horizontalDistance
                        - (CognitiveLoadPolicy.EFFECT_RADIUS - 16.0F)) / 16.0D,
                0.0D,
                1.0D);
        environmentMultiplier = Mth.lerp(influence, 1.0F, nodeMultiplier);
    }
}
