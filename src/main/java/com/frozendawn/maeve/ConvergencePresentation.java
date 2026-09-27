package com.frozendawn.maeve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** A wide ground gathering, never a body or the scout's extraction column. */
final class ConvergencePresentation {
    static void begin(MinecraftServer server, ConvergenceMemory memory, ConvergenceGroup group) {
        long now = server.overworld().getGameTime();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().location().toString().equals(group.dimension) || !player.isAlive() || player.isSpectator()
                    || player.blockPosition().distSqr(group.destination) > ConvergencePolicy.MESSAGE_RADIUS * ConvergencePolicy.MESSAGE_RADIUS) continue;
            if (memory.notice(player.getUUID(), now)) player.sendSystemMessage(Component.translatable("message.frozendawn.pawn_presentiment"));
        }
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(group.dimension)));
        cloud(level, group, now);
    }
    static void cloud(ServerLevel level, ConvergenceGroup group, long now) {
        if (level == null || !level.hasChunkAt(group.destination)) return;
        double grow = Math.min(1, Math.max(.15, (now - group.cloudAt) / (double) ConvergencePolicy.WARNING));
        double radius = 3 + 9 * grow;
        // At most 48 particles per second, in a broad disk. Never samples unloaded terrain.
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24 + now * .013;
            double r = radius * (i % 2 == 0 ? 1 : .55);
            BlockPos pos = BlockPos.containing(group.destination.getX() + .5 + Math.cos(angle) * r,
                    group.destination.getY(), group.destination.getZ() + .5 + Math.sin(angle) * r);
            if (!level.hasChunkAt(pos)) continue;
            double y = pos.getY() + .25;
            for (int dy = 2; dy >= -2; dy--) {
                var floor = pos.offset(0, dy - 1, 0); var state = level.getBlockState(floor);
                if (!state.getCollisionShape(level, floor).isEmpty() && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()) {
                    y = floor.getY() + 1.15; break;
                }
            }
            level.sendParticles(ParticleTypes.SOUL, pos.getX() + .5, y, pos.getZ() + .5, 1, .25, .12, .25, .015);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + .5, y + .25, pos.getZ() + .5, 1, .15, .1, .15, .005);
        }
    }
    private ConvergencePresentation() { }
}
