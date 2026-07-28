package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.network.HeartMemoryNodeEventPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-validated interaction authority for the Heart's five ordered memories. */
public final class HeartMemoryNodeManager {
    private static final int STRIKE_COOLDOWN_TICKS = 6;
    private static final float MAX_RENDERED_LOAD_DRIFT = 32.0F;
    private static final double CLOSE_STRIKE_DISTANCE_SQR = 7.0D * 7.0D;
    private static final float IMPACT_RELIEF = 2.5F;
    private static final float DESTRUCTION_RELIEF = 24.0F;
    private static final Map<UUID, Long> LAST_STRIKE_TICK = new HashMap<>();

    private HeartMemoryNodeManager() {
    }

    public static void handleStrike(
            ServerPlayer player, int requestedNode, float renderedLoad) {
        ServerLevel level = player.serverLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive)
                .orElse(null);
        if (hearth == null || requestedNode < 0
                || requestedNode >= HeartLattice.NODE_COUNT
                || requestedNode != HeartLattice.nextNode(
                hearth.heartDestroyedNodeMask())) {
            return;
        }
        long now = level.getGameTime();
        Long previousStrike = LAST_STRIKE_TICK.get(player.getUUID());
        if (previousStrike != null
                && now - previousStrike < STRIKE_COOLDOWN_TICKS) {
            return;
        }
        float load = CognitiveLoadManager.getLoad(player);
        if (load + 0.001F < HeartLattice.requiredLoad(requestedNode)
                && !HeartEchoManager.isNodeExposed(player, requestedNode)) {
            return;
        }
        if (!Float.isFinite(renderedLoad)
                || renderedLoad < 0.0F
                || renderedLoad > CognitiveLoadPolicy.MAX_LOAD
                || Math.abs(renderedLoad - load) > MAX_RENDERED_LOAD_DRIFT) {
            return;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        Vec3 nodePosition = HeartLattice.nodePosition(
                anchor, heartSeed(hearth, anchor), renderedLoad, requestedNode);
        Vec3 eye = player.getEyePosition();
        if (!HeartLattice.raySelectsNode(
                eye, player.getViewVector(1.0F), nodePosition)
                || (eye.distanceToSqr(nodePosition) > CLOSE_STRIKE_DISTANCE_SQR
                && !hasLineOfSight(level, player, eye, nodePosition))) {
            return;
        }
        LAST_STRIKE_TICK.put(player.getUUID(), now);
        applyStrike(level, player, data, hearth, requestedNode, nodePosition, true);
    }

    public static boolean damageNodeForDebug(
            ServerLevel level, int requestedNode) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive)
                .orElse(null);
        if (hearth == null || requestedNode != HeartLattice.nextNode(
                hearth.heartDestroyedNodeMask())) {
            return false;
        }
        for (int hit = hearth.heartActiveNodeDamage();
             hit < HeartLattice.HITS_PER_NODE; hit++) {
            data.damageHeartMemoryNode(hearth.id(), requestedNode);
        }
        return true;
    }

    public static void reset() {
        LAST_STRIKE_TICK.clear();
    }

    private static void applyStrike(
            ServerLevel level,
            ServerPlayer player,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            int nodeIndex,
            Vec3 nodePosition,
            boolean applyPlayerConsequences) {
        MemorySnapshot memory = snapshotMemory(data, hearth, player);
        ReturnedHearthSavedData.HeartNodeDamageResult result =
                data.damageHeartMemoryNode(hearth.id(), nodeIndex);
        if (!result.accepted()) {
            return;
        }
        if (applyPlayerConsequences) {
            HeartEchoManager.grantNodeClarity(player, nodeIndex);
            CognitiveLoadManager.relieveFromHeartNode(
                    player, result.destroyed() ? DESTRUCTION_RELIEF : IMPACT_RELIEF);
            Vec3 away = player.position().subtract(nodePosition);
            if (away.lengthSqr() > 0.01D) {
                Vec3 recoil = away.normalize().scale(result.destroyed() ? 0.34D : 0.12D);
                player.setDeltaMovement(
                        player.getDeltaMovement().add(recoil.x, 0.12D, recoil.z));
                player.hurtMarked = true;
            }
        }
        if (result.destroyed() && nodeIndex == HeartLattice.NODE_COUNT - 1) {
            data.erasePlayerFromHive(player.getUUID());
        }

        ThaeIvenHeartEntity heart = hearth.heartEntityId()
                .map(level::getEntity)
                .filter(ThaeIvenHeartEntity.class::isInstance)
                .map(ThaeIvenHeartEntity.class::cast)
                .orElse(null);
        int entityId = heart == null ? -1 : heart.getId();
        if (heart != null) {
            heart.configure(
                    hearth.id(), heartSeed(hearth, hearth.heartAnchor()
                            .orElse(hearth.center())),
                    hearth.heartAnchor().orElse(hearth.center()).asLong(),
                    hearth.heartFieldStrength(), HeartFormationStage.LIVE, 1.0F,
                    result.destroyedMask(), result.activeDamage());
        }

        for (ServerPlayer witness : level.players()) {
            if (witness.distanceToSqr(Vec3.atCenterOf(
                    hearth.heartAnchor().orElse(hearth.center())))
                    > 160.0D * 160.0D) {
                continue;
            }
            PacketDistributor.sendToPlayer(witness,
                    new HeartMemoryNodeEventPayload(
                            entityId,
                            nodeIndex,
                            result.destroyed()
                                    ? HeartLattice.HITS_PER_NODE
                                    : result.activeDamage(),
                            result.destroyedMask(),
                            memory.variant(),
                            memory.visits(),
                            memory.casualties(),
                            result.destroyed()
                                    && witness.getUUID().equals(player.getUUID())));
        }
    }

    private static MemorySnapshot snapshotMemory(
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord hearth,
            ServerPlayer player) {
        ReturnedHearthSavedData.PlayerHiveMemory global = data
                .playerMemory(player.getUUID()).orElse(null);
        int visits = global == null ? 0 : global.totalVisits();
        int casualties = global == null ? 0 : global.congregationCasualties();
        ReturnedHearthSavedData.HiveRelationship relationship = global == null
                ? ReturnedHearthSavedData.HiveRelationship.NEUTRAL
                : global.relationship();
        int violations = hearth.playerContact(player.getUUID())
                .map(memory -> memory.violationReasons().size()).orElse(0);
        int variant = casualties > 0 ? 3
                : relationship == ReturnedHearthSavedData.HiveRelationship.ORSATHAE
                || violations > 0 ? 2
                : relationship == ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS
                ? 1 : 0;
        return new MemorySnapshot(variant, visits, casualties);
    }

    private static boolean hasLineOfSight(
            ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static long heartSeed(
            ReturnedHearthSavedData.HearthRecord hearth, BlockPos anchor) {
        return hearth.heartLayoutSeed() != 0L
                ? hearth.heartLayoutSeed()
                : hearth.layoutSeed() ^ anchor.asLong() ^ 0x48454152544C4154L;
    }

    private record MemorySnapshot(int variant, int visits, int casualties) {
    }
}
