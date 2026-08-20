package com.frozendawn.aggregate;

import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cheap threshold-driven growth authority. It never force-loads or scans globally. */
public final class AggregateGrowthManager {
    private AggregateGrowthManager() {
    }

    public static void tick(ServerLevel overworld) {
        if ((overworld.getGameTime() % 20L) != 0L
                || !FrozenDawnConfig.ENABLE_AGGREGATE.get()
                || !PostMaeveWorldState.isErased(overworld.getServer())) return;
        AggregateSavedData data = AggregateSavedData.get(overworld.getServer());
        if (data.resolved()) return;

        if (!data.fightStarted()) advanceGrowth(overworld, data);
        if (data.stage().ordinal() >= AggregateStage.OSSUARY.ordinal()) {
            grantDiscovery(overworld, data);
        }
        tickGestationPresentation(overworld, data);
        if (data.awakeningEligible()) {
            data.ossuaryPos().ifPresent(anchor -> overworld.getPlayers(player ->
                    !player.isSpectator() && player.distanceToSqr(
                            anchor.getX() + 0.5D, anchor.getY() + 0.5D,
                            anchor.getZ() + 0.5D) <= 48.0D * 48.0D)
                    .stream().findFirst().ifPresent(player ->
                            AggregateEncounterManager.awaken(overworld, data, player)));
        }
        AggregateEncounterManager.reconcile(overworld, data);
        AggregateReinforcementManager.tick(overworld, data);
    }

    private static void advanceGrowth(ServerLevel level, AggregateSavedData data) {
        AggregateStage target = AggregatePressurePolicy.configuredStageFor(data.pressure());
        long day = Math.floorDiv(level.getDayTime(), 24_000L);
        AggregateStage next = AggregatePressurePolicy.nextGrowthStage(
                data.stage(), target, day, data.lastStageAdvanceDay());
        if (next == data.stage()) return;
        if (next.ordinal() >= AggregateStage.DEPOSIT.ordinal()
                && data.ossuaryPos().isEmpty() && !chooseAnchor(level, data)) {
            return;
        }
        data.advanceStage(next, day);
        AggregateOssuaryBuilder.buildStage(level, data, next);
        playStageDiagnostic(level, data, next);
    }

    public static void playStageDiagnostic(
            ServerLevel level, AggregateSavedData data, AggregateStage stage) {
        int line = switch (stage) {
            case DEPOSIT -> 0;
            case OSSUARY -> 1;
            case GESTATION -> 2;
            default -> -1;
        };
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (line < 0 || anchor == null) return;
        for (ServerPlayer player : level.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.distanceToSqr(anchor.getCenter()) <= 128.0D * 128.0D)) {
            PacketDistributor.sendToPlayer(
                    player, com.frozendawn.network.HearthBoundaryEffectPayload
                            .aggregateDiagnostic(line));
        }
    }

    private static boolean chooseAnchor(ServerLevel level, AggregateSavedData data) {
        long seed = level.getSeed() ^ Double.doubleToLongBits(data.pressure())
                ^ 0x4147475245474154L;
        RandomSource random = RandomSource.create(seed);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int radius = 80 + random.nextInt(65);
                int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
                BlockPos horizontal = new BlockPos(x, player.blockPosition().getY(), z);
                if (!level.hasChunkAt(horizontal)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (AggregateOssuaryBuilder.validAnchor(level, candidate)) {
                    data.setOssuary(candidate, seed ^ candidate.asLong());
                    return true;
                }
            }
        }
        return false;
    }

    public static void leaveResidue(
            ServerLevel level, BlockPos deathPos, AggregateSavedData data) {
        if (data.stage().ordinal() < AggregateStage.RESIDUE.ordinal()
                || data.stage().ordinal() >= AggregateStage.DEPOSIT.ordinal()
                || level.random.nextFloat() > 0.18F || !level.isLoaded(deathPos)) return;
        for (int i = 0; i < 4; i++) {
            BlockPos pos = deathPos.offset(level.random.nextInt(5) - 2,
                    level.random.nextInt(2), level.random.nextInt(5) - 2);
            if (level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, ModBlocks.AGGREGATE_RESIDUE.get().defaultBlockState(), 2);
            }
        }
        for (FrostmiteEntity mite : level.getEntitiesOfClass(FrostmiteEntity.class,
                new net.minecraft.world.phys.AABB(deathPos).inflate(12.0D),
                entity -> entity.isAlive())) {
            mite.getNavigation().moveTo(deathPos.getX() + 0.5D,
                    deathPos.getY(), deathPos.getZ() + 0.5D, 1.0D);
        }
    }

    public static double evolvedWeightMultiplier(ServerLevel level, BlockPos pos) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        if (!FrozenDawnConfig.ENABLE_AGGREGATE.get() || data.resolved()
                || data.stage().ordinal() < AggregateStage.OSSUARY.ordinal()
                || data.fightStarted()) return 1.0D;
        return data.ossuaryPos().filter(anchor -> anchor.distSqr(pos) <= 96.0D * 96.0D)
                .isPresent() ? 1.2D : 1.0D;
    }

    private static void tickGestationPresentation(
            ServerLevel level, AggregateSavedData data) {
        if (data.stage() != AggregateStage.GESTATION
                && data.stage() != AggregateStage.AWAKENING_ELIGIBLE) return;
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (anchor == null || !level.isLoaded(anchor) || level.random.nextInt(60) != 0) return;
        level.playSound(null, anchor, ModSounds.AGGREGATE_AMBIENT.get(),
                SoundSource.HOSTILE, 1.6F, 0.52F + level.random.nextFloat() * 0.12F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                anchor.getX() + 0.5D, anchor.getY() + 2.0D, anchor.getZ() + 0.5D,
                14, 2.4D, 1.0D, 2.4D, 0.025D);
    }

    private static void grantDiscovery(ServerLevel level, AggregateSavedData data) {
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (anchor == null) return;
        for (ServerPlayer player : level.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.distanceToSqr(anchor.getCenter()) <= 48.0D * 48.0D)) {
            WorldTickHandler.grantAdvancement(player, "selection_pressure");
        }
    }
}
