package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.world.ResonanceEventManager;
import com.frozendawn.entity.ResonantPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Converts physical actions into short-lived structural signals. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ResonanceEventHooks {
    private static final String ITEM_GROUNDED_TAG = "frozendawnResonanceGrounded";
    private static final Map<UUID, Boolean> WAS_GROUNDED = new HashMap<>();
    private static final Map<UUID, Float> AIRBORNE_FALL_DISTANCE = new HashMap<>();

    private ResonanceEventHooks() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !enabled(player.serverLevel())) return;
        boolean grounded = player.onGround();
        boolean wasGrounded = WAS_GROUNDED.getOrDefault(player.getUUID(), grounded);
        float airborneFall = AIRBORNE_FALL_DISTANCE.getOrDefault(player.getUUID(), 0.0F);
        if (grounded && !wasGrounded && airborneFall > 0.8F) {
            emit(player.serverLevel(), player, 4.0F, ResonanceEventManager.Type.LAND);
        }
        WAS_GROUNDED.put(player.getUUID(), grounded);
        if (grounded) {
            AIRBORNE_FALL_DISTANCE.put(player.getUUID(), 0.0F);
        } else {
            AIRBORNE_FALL_DISTANCE.put(player.getUUID(),
                    Math.max(airborneFall, player.fallDistance));
        }
        if (!grounded || player.getDeltaMovement().horizontalDistanceSqr() < 0.0012D) return;
        int interval = player.isSprinting() ? 6 : 10;
        if (player.tickCount % interval != 0) return;
        float strength = ResonantPolicy.movementStrength(
                player.isSprinting(), player.isCrouching());
        emit(player.serverLevel(), player, strength,
                player.isSprinting() ? ResonanceEventManager.Type.SPRINT
                        : ResonanceEventManager.Type.WALK);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled(level)) return;
        SoundType sound = event.getState().getSoundType(level, event.getPos(), event.getPlayer());
        boolean metal = sound == SoundType.METAL || sound == SoundType.ANVIL
                || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK;
        ResonanceEventManager.emit(level, event.getPos().getCenter(), metal ? 7.0F : 5.0F,
                metal ? ResonanceEventManager.Type.METAL_MINE
                        : ResonanceEventManager.Type.STONE_MINE,
                event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled(level)) return;
        Entity entity = event.getEntity();
        ResonanceEventManager.emit(level, event.getPos().getCenter(), 4.0F,
                ResonanceEventManager.Type.PLACE, entity == null ? null : entity.getUUID());
    }

    @SubscribeEvent
    public static void onDoor(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !enabled(level)) return;
        var block = level.getBlockState(event.getPos()).getBlock();
        if (!(block instanceof DoorBlock) && !(block instanceof TrapDoorBlock)) return;
        ResonanceEventManager.emit(level, event.getPos().getCenter(), 3.0F,
                ResonanceEventManager.Type.DOOR, event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && enabled(level)) {
            ResonanceEventManager.emit(level, event.getPos().getCenter(), 8.0F,
                    ResonanceEventManager.Type.PISTON, null);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && enabled(level)) {
            Entity source = event.getExplosion().getDirectSourceEntity();
            ResonanceEventManager.emit(level, event.getExplosion().center(), 15.0F,
                    ResonanceEventManager.Type.EXPLOSION,
                    source == null ? null : source.getUUID());
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level)
                || !enabled(level)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        Projectile projectile = event.getProjectile();
        Entity owner = projectile.getOwner();
        ResonanceEventManager.emit(level, hit.getLocation(), 5.0F,
                ResonanceEventManager.Type.PROJECTILE_IMPACT,
                owner == null ? projectile.getUUID() : owner.getUUID());
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item)
                || !(item.level() instanceof ServerLevel level) || !enabled(level)) return;
        boolean grounded = item.onGround();
        boolean wasGrounded = item.getPersistentData().getBoolean(ITEM_GROUNDED_TAG);
        if (grounded && !wasGrounded && item.tickCount > 2) {
            ResonanceEventManager.emit(level, item.position(), 3.0F,
                    ResonanceEventManager.Type.ITEM_IMPACT,
                    item.getOwner() == null ? item.getUUID() : item.getOwner().getUUID());
        }
        if (grounded != wasGrounded) {
            item.getPersistentData().putBoolean(ITEM_GROUNDED_TAG, grounded);
        }
    }

    public static void emitMachinery(ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (enabled(level)) {
            ResonanceEventManager.emit(level, pos.getCenter(), 4.0F,
                    ResonanceEventManager.Type.MACHINERY, null);
        }
    }

    private static void emit(ServerLevel level, ServerPlayer player, float strength,
                             ResonanceEventManager.Type type) {
        ResonanceEventManager.emit(level, player.position(), strength, type, player.getUUID());
    }

    private static boolean enabled(ServerLevel level) {
        return PostMaeveWorldState.isErased(level);
    }
}
