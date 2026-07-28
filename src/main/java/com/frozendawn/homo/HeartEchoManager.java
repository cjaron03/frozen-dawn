package com.frozendawn.homo;

import com.frozendawn.data.CognitiveLoadState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HeartEchoActionPayload;
import com.frozendawn.network.HeartEchoStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server authority for private, non-hostile Echo manifestations. */
public final class HeartEchoManager {
    private static final double ACTIVE_RADIUS_SQR = 112.0D * 112.0D;
    private static final Map<UUID, EchoSession> SESSIONS = new HashMap<>();
    private static int nextGeneration = 1;

    private HeartEchoManager() {
    }

    public static void tick(
            ServerLevel level,
            ServerPlayer player,
            CognitiveLoadState loadState,
            ReturnedHearthSavedData.HearthRecord heart,
            BlockPos anchor) {
        if (heart == null || player.isSpectator()
                || player.distanceToSqr(Vec3.atCenterOf(anchor)) > ACTIVE_RADIUS_SQR) {
            clear(player, true);
            return;
        }

        EchoSession session = SESSIONS.computeIfAbsent(
                player.getUUID(), ignored -> new EchoSession());
        session.clarityTicks = Math.max(0, session.clarityTicks - 1);
        session.exposureTicks = Math.max(0, session.exposureTicks - 1);
        session.cooldownTicks = Math.max(0, session.cooldownTicks - 1);
        loadState.setLoad(Math.max(loadState.load(), session.loadFloor));

        int nextNode = HeartLattice.nextNode(heart.heartDestroyedNodeMask());
        if (session.exposedNode != nextNode || nextNode < 0) {
            session.exposureTicks = 0;
            session.exposedNode = -1;
        }

        if (session.active) {
            session.remainingTicks--;
            if (session.remainingTicks <= 0) {
                scream(player, loadState, heart, session);
            }
            return;
        }

        if (session.exposureTicks > 0 || session.cooldownTicks > 0
                || !HeartEchoPolicy.canSpawn(loadState.load(), true, nextNode)) {
            return;
        }
        spawn(level, player, anchor, session, nextNode);
    }

    public static void handleAction(ServerPlayer player, HeartEchoActionPayload payload) {
        EchoSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.active
                || session.generation != payload.generation()) {
            return;
        }
        ReturnedHearthSavedData.HearthRecord heart = ReturnedHearthSavedData
                .get(player.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive)
                .orElse(null);
        if (heart == null || session.nodeIndex != HeartLattice.nextNode(
                heart.heartDestroyedNodeMask())) {
            clear(player, true);
            return;
        }

        CognitiveLoadState loadState = player.getData(ModAttachments.COGNITIVE_LOAD);
        session.active = false;
        if (payload.violent()) {
            session.loadFloor = HeartEchoPolicy.nextViolenceFloor(session.loadFloor);
            loadState.setLoad(Math.max(session.loadFloor,
                    loadState.load() + HeartEchoPolicy.VIOLENCE_LOAD));
            session.cooldownTicks = Math.max(60,
                    HeartEchoPolicy.respawnCooldownTicks(
                            heart.heartFieldStrength(), HeartLattice.destroyedCount(
                                    heart.heartDestroyedNodeMask())) / 2);
            player.playNotifySound(ModSounds.THAE_IVEN_HEART_ECHO_BREAK.get(),
                    SoundSource.MASTER, 1.2F, 1.45F);
            send(player, session, HeartEchoStatePayload.VIOLENTLY_DISMISSED);
            return;
        }

        relieve(loadState, HeartEchoPolicy.ACKNOWLEDGEMENT_RELIEF);
        session.exposedNode = session.nodeIndex;
        session.exposureTicks = HeartEchoPolicy.NODE_EXPOSURE_TICKS;
        session.clarityTicks = HeartEchoPolicy.CLARITY_TICKS;
        session.cooldownTicks = HeartEchoPolicy.respawnCooldownTicks(
                heart.heartFieldStrength(), HeartLattice.destroyedCount(
                        heart.heartDestroyedNodeMask()));
        player.playNotifySound(ModSounds.THAE_IVEN_HEART_ECHO_ACKNOWLEDGE.get(),
                SoundSource.MASTER, 0.9F, 1.35F);
        send(player, session, HeartEchoStatePayload.ACKNOWLEDGED);
    }

    public static boolean isNodeExposed(ServerPlayer player, int nodeIndex) {
        EchoSession session = SESSIONS.get(player.getUUID());
        return session != null && session.exposureTicks > 0
                && session.exposedNode == nodeIndex;
    }

    public static boolean hasClarity(ServerPlayer player) {
        EchoSession session = SESSIONS.get(player.getUUID());
        return session != null && session.clarityTicks > 0;
    }

    public static void grantNodeClarity(ServerPlayer player, int nodeIndex) {
        EchoSession session = SESSIONS.computeIfAbsent(
                player.getUUID(), ignored -> new EchoSession());
        session.active = false;
        session.nodeIndex = nodeIndex;
        session.clarityTicks = HeartEchoPolicy.CLARITY_TICKS;
        session.cooldownTicks = Math.max(session.cooldownTicks, 60);
        send(player, session, HeartEchoStatePayload.NODE_HIT);
    }

    public static float loadFloor(ServerPlayer player) {
        EchoSession session = SESSIONS.get(player.getUUID());
        return session == null ? 0.0F : session.loadFloor;
    }

    public static void onPlayerLogout(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void reset() {
        SESSIONS.clear();
        nextGeneration = 1;
    }

    private static void spawn(
            ServerLevel level,
            ServerPlayer player,
            BlockPos anchor,
            EchoSession session,
            int nodeIndex) {
        Vec3 position = findPosition(level, player, anchor);
        session.active = true;
        session.generation = nextGeneration++;
        session.position = position;
        session.nodeIndex = nodeIndex;
        session.remainingTicks = HeartEchoPolicy.PATIENCE_TICKS;
        player.playNotifySound(ModSounds.THAE_IVEN_HEART_ECHO_APPEAR.get(),
                SoundSource.MASTER, 1.45F, 0.78F);
        send(player, session, HeartEchoStatePayload.ACTIVE);
    }

    private static void scream(
            ServerPlayer player,
            CognitiveLoadState loadState,
            ReturnedHearthSavedData.HearthRecord heart,
            EchoSession session) {
        session.active = false;
        loadState.setLoad(loadState.load() + HeartEchoPolicy.SCREAM_LOAD);
        session.cooldownTicks = HeartEchoPolicy.respawnCooldownTicks(
                heart.heartFieldStrength(), HeartLattice.destroyedCount(
                        heart.heartDestroyedNodeMask()));
        player.playNotifySound(ModSounds.THAE_IVEN_HEART_ECHO_SCREAM.get(),
                SoundSource.MASTER, 1.65F, 1.28F);
        send(player, session, HeartEchoStatePayload.SCREAMED);
    }

    private static void relieve(CognitiveLoadState state, float amount) {
        state.setLoad(state.load() - Math.max(0.0F, amount));
        if (state.load() < CognitiveLoadPolicy.TAKEOVER_THRESHOLD) {
            state.setTerminalTakeover(false);
            state.setTakeoverTicks(0);
            state.setBreakoutTicks(0.0F);
            state.setResistanceInput(0.0F, 0);
        }
        state.setLapseCooldownTicks(Math.max(
                state.lapseCooldownTicks(), HeartEchoPolicy.CLARITY_TICKS));
    }

    private static Vec3 findPosition(
            ServerLevel level, ServerPlayer player, BlockPos anchor) {
        double yaw = Math.toRadians(player.getYRot());
        double sideSign = player.getRandom().nextBoolean() ? 1.0D : -1.0D;
        double firstOffset = Math.toRadians(
                28.0D + player.getRandom().nextDouble() * 22.0D) * sideSign;
        for (int attempt = 0; attempt < 8; attempt++) {
            double offset = firstOffset + Math.toRadians(attempt * 11.0D)
                    * (attempt % 2 == 0 ? 1.0D : -1.0D);
            double angle = yaw + offset;
            double distance = 7.0D + player.getRandom().nextDouble() * 6.0D;
            int x = Mth.floor(player.getX() - Math.sin(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.cos(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, Mth.floor(player.getY()), z))) {
                continue;
            }
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos feet = new BlockPos(x, Mth.floor(player.getY()) + dy, z);
                if (!safeStandingSpace(level, feet)
                        || feet.distSqr(anchor) > ACTIVE_RADIUS_SQR) {
                    continue;
                }
                Vec3 candidate = Vec3.atBottomCenterOf(feet);
                if (visible(level, player, player.getEyePosition(),
                        candidate.add(0.0D, 1.45D, 0.0D))) {
                    return candidate;
                }
            }
        }
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 0.01D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 lateral = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        return player.position().add(horizontal.scale(8.0D)).add(lateral.scale(4.0D));
    }

    private static boolean safeStandingSpace(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isSolidRender(level, feet.below())
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above())
                .isEmpty();
    }

    private static boolean visible(
            ServerLevel level, ServerPlayer player, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static void send(
            ServerPlayer player, EchoSession session, int state) {
        PacketDistributor.sendToPlayer(player, new HeartEchoStatePayload(
                session.generation,
                state,
                session.position.x,
                session.position.y,
                session.position.z,
                session.nodeIndex,
                session.exposureTicks,
                session.clarityTicks));
    }

    private static void clear(ServerPlayer player, boolean notify) {
        EchoSession session = SESSIONS.remove(player.getUUID());
        if (notify && session != null) {
            send(player, session, HeartEchoStatePayload.CLEAR);
        }
    }

    private static final class EchoSession {
        private int generation;
        private boolean active;
        private Vec3 position = Vec3.ZERO;
        private int nodeIndex = -1;
        private int remainingTicks;
        private int cooldownTicks;
        private int exposedNode = -1;
        private int exposureTicks;
        private int clarityTicks;
        private float loadFloor;
    }
}
