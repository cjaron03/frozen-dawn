package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;

/** Rare, independent cave encounter. Spawn selection never seeds an observation. */
public final class BlindArchitectSpawner {
    static final int MAX_SPAWN_Y = 0;
    private BlindArchitectSpawner() { }
    public static boolean enabled(ServerLevel level) {
        var phase = ApocalypseState.get(level.getServer());
        return FrozenDawnConfig.ENABLE_ARCHITECT.get() && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING) && level.getDifficulty() != Difficulty.PEACEFUL
                && PhaseManager.isVacuumActive(phase.getPhase(), phase.getProgress()) && !PostMaeveWorldState.isErased(level);
    }
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 200 != 0 || !enabled(level)) return;
        var memory = BlindSpawnMemory.get(level);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.getY() > MAX_SPAWN_Y
                    || !memory.ready(player.blockPosition(), level.getGameTime()) || level.random.nextFloat() > .02 * FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get()) continue;
            if (!level.getEntitiesOfClass(ArchitectEntity.class, player.getBoundingBox().inflate(96), ArchitectEntity::isBlindArchitect).isEmpty()) continue;
            BlockPos pos = position(level, player);
            if (pos == null || !memory.ready(pos, level.getGameTime()) || !MaeveDirector.allowNaturalPawn(level, pos)) continue;
            var actor = ModEntities.ARCHITECT.get().create(level, null, pos, MobSpawnType.NATURAL, true, false);
            if (actor == null) continue;
            actor.setBlindArchitect(true);
            if (!level.noCollision(actor) || !level.addFreshEntity(actor)) continue;
            memory.spawned(player.blockPosition(), level.getGameTime()); memory.spawned(pos, level.getGameTime());
            MaeveDirector.observePawn(actor);
        }
    }
    static BlockPos position(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 24; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2, radius = 24 + level.random.nextDouble() * 40;
            var pos = BlockPos.containing(player.getX() + Math.cos(angle) * radius,
                    Math.min(MAX_SPAWN_Y, player.getY() + level.random.nextInt(25) - 12), player.getZ() + Math.sin(angle) * radius);
            if (player.position().distanceToSqr(pos.getCenter()) < 24 * 24 || level.getNearestPlayer(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 24, false) != null
                    || !level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) continue;
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    && level.getMaxLocalRawBrightness(pos) <= 0
                    && Monster.checkMonsterSpawnRules(ModEntities.ARCHITECT.get(), level, MobSpawnType.NATURAL, pos, level.random)) return pos;
        }
        return null;
    }
}
