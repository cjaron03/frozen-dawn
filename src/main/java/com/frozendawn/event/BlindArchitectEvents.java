package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.frozendawn.entity.blind.BlindVibrationPropagation.Kind;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-local event delivery also covers sources beyond vanilla's smaller event radii. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class BlindArchitectEvents {
    private record Step(Vec3 position, double accumulated) { }
    private static final WeakHashMap<ServerPlayer, Step> STEPS = new WeakHashMap<>();
    private BlindArchitectEvents() { }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ArchitectEntity actor) com.frozendawn.entity.blind.BlindArchitectRegistry.loaded(actor);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var type = event.getVanillaEvent();
        if (type != GameEvent.EAT && type != GameEvent.DRINK && type != GameEvent.STEP && type != GameEvent.HIT_GROUND
                && type != GameEvent.BLOCK_OPEN && type != GameEvent.BLOCK_CLOSE && type != GameEvent.BLOCK_PLACE
                && type != GameEvent.BLOCK_DESTROY && type != GameEvent.BLOCK_ACTIVATE && type != GameEvent.BLOCK_DEACTIVATE
                && type != GameEvent.NOTE_BLOCK_PLAY && type != GameEvent.PROJECTILE_LAND && type != GameEvent.BLOCK_CHANGE && type != GameEvent.EXPLODE) return;
        for (var actor : nearby(level, event.getEventPosition())) actor.blindController().hear(type, event.getContext(), event.getEventPosition());
    }
    private static java.util.List<ArchitectEntity> nearby(ServerLevel level, Vec3 position) {
        var actors = new java.util.ArrayList<ArchitectEntity>();
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ArchitectEntity.class),
                new AABB(position, position).inflate(BlindVibrationPropagation.RANGE), a -> a.isBlindArchitect() && a.isAlive() && !a.isNoAi(), actors, 32);
        return java.util.List.copyOf(actors);
    }
    public static void mining(ServerLevel level, ServerPlayer player, BlockPos block) {
        if (level.getGameTime() % 10 != 0 || player.isCreative() || player.isSpectator() || !player.isAlive()
                || !level.hasChunkAt(block) || !player.canInteractWithBlock(block, 1.0) || level.getBlockState(block).isAir()) return;
        vibrate(level, BlindVibrationPropagation.block(Kind.TOOL, block, level.getBlockState(block), player.getEyePosition(), 24, level.getGameTime()));
        // The accepted tool stroke is audible from the worker's exposed air,
        // while its material vibration starts inside the block being mined.
        emit(level, player.getEyePosition(), player, 24, "TOOL");
    }
    public static void machine(ServerLevel level, BlockPos block) {
        if ((level.getGameTime() + Math.floorMod(block.asLong(), 100)) % 100 != 0) return;
        // Put air hearing just above the machine; vibration starts at its actual block.
        vibrate(level, BlindVibrationPropagation.block(Kind.MACHINE, block, level.getBlockState(block), block.getCenter(), 10, level.getGameTime()));
        emit(level, block.above().getCenter(), null, 24, "MACHINE");
    }
    public static void emit(ServerLevel level, Vec3 position, net.minecraft.world.entity.Entity source, int range) {
        emit(level,position,source,range,"ACTIVITY");
    }
    public static void emit(ServerLevel level, Vec3 position, net.minecraft.world.entity.Entity source, int range, String kind) {
        for (var actor : nearby(level, position)) actor.blindController().emit(position, source, range, kind);
    }
    public static void vibrate(ServerLevel level, BlindVibrationPropagation.Impulse impulse) {
        Vec3 origin = impulse.block() == null ? impulse.position() : impulse.block().getCenter();
        for (var actor : nearby(level, origin)) actor.blindController().vibrate(impulse);
    }
    @SubscribeEvent
    public static void sneakSteps(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Step previous = STEPS.put(player, new Step(player.position(), 0));
        if (previous == null || !player.isShiftKeyDown() || !player.onGround() || player.isCreative() || player.isSpectator()) return;
        double distance = player.position().subtract(previous.position).horizontalDistance();
        if (distance > 1.2) return;
        if (distance < .001) {
            STEPS.put(player, new Step(player.position(), previous.accumulated));
            return;
        }
        double sum = previous.accumulated + distance;
        STEPS.put(player, new Step(player.position(), sum >= .65 ? 0 : sum));
        // Vanilla suppresses sneak footsteps. Here the quiet movement still has an air channel.
        if (sum >= .65) {
            vibrate(player.serverLevel(), BlindVibrationPropagation.foot(player, Kind.FOOTSTEP, 6, player.level().getGameTime()));
            emit(player.serverLevel(), player.position().add(0, .15, 0), player, 12, "QUIET_STEP");
        }
    }
}
