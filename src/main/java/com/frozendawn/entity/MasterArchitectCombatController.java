package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.MasterArchitectThermalSever;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.ContinuityFracturePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete hostile combat lane for the Hearth-bound Master Architect.
 * Ordinary Architects never instantiate or enter this controller.
 */
final class MasterArchitectCombatController {
    private static final double APPROACH_SPEED = 0.82D;
    private static final double RETREAT_SPEED = 0.68D;
    private static final double SPELL_HOLD_MIN_DISTANCE = 6.0D;
    private static final double SPELL_HOLD_MAX_DISTANCE = 10.0D;
    private static final int WALL_HEIGHT = 3;

    private final ArchitectEntity architect;
    private final List<PlannedWallColumn> wallPlan = new ArrayList<>();
    private final List<BlockPos> placedWallBlocks = new ArrayList<>();

    private int activeAction = MasterArchitectCombatAction.IDLE;
    private int actionTicks;
    private int staffCooldown;
    private int sharedSpellCooldown = 40;
    private int continuityCooldown = 40;
    private int thermalCooldown = 80;
    private int stormMaintenanceCooldown = 120;
    private boolean lastWallUsed;
    private boolean healingInterrupted;
    private float healTarget;
    private long wallExpiresAt = -1L;

    MasterArchitectCombatController(ArchitectEntity architect) {
        this.architect = architect;
    }

    void tick(ServerLevel level, ServerPlayer target) {
        tickCooldowns();
        cleanupExpiredWall(level);
        architect.prepareHearthAssessmentMode();
        architect.setTarget(target);
        architect.equipMasterArchitectStaff();
        architect.getLookControl().setLookAt(target, 40.0F, 35.0F);

        if (activeAction != MasterArchitectCombatAction.IDLE) {
            tickActiveAction(level, target);
            return;
        }

        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
        if (MasterArchitectCombatPolicy.shouldUseLastWall(
                architect.getHealth(), architect.getMaxHealth(), lastWallUsed)
                && beginLastWall(level, target)) {
            return;
        }

        double distanceSquared = architect.distanceToSqr(target);
        boolean hasLineOfSight = architect.hasLineOfSight(target);
        if (sharedSpellCooldown <= 0
                && MasterArchitectCombatPolicy.canCast(
                        distanceSquared,
                        MasterArchitectCombatPolicy.THERMAL_RANGE,
                        hasLineOfSight,
                        thermalCooldown)
                && (continuityCooldown > 0 || architect.nextRandomFloat() < 0.55F)) {
            startAction(MasterArchitectCombatAction.THERMAL_SEVER);
            architect.getNavigation().stop();
            architect.playSound(ModSounds.MASTER_ARCHITECT_CAST.get(), 1.4F, 0.72F);
            return;
        }
        if (sharedSpellCooldown <= 0
                && MasterArchitectCombatPolicy.canCast(
                        distanceSquared,
                        MasterArchitectCombatPolicy.CONTINUITY_RANGE,
                        hasLineOfSight,
                        continuityCooldown)) {
            startAction(MasterArchitectCombatAction.CONTINUITY_FRACTURE);
            architect.getNavigation().stop();
            architect.playSound(ModSounds.MASTER_ARCHITECT_CAST.get(), 1.4F, 0.90F);
            return;
        }

        if (staffCooldown <= 0
                && distanceSquared
                <= MasterArchitectCombatPolicy.STAFF_RANGE
                * MasterArchitectCombatPolicy.STAFF_RANGE
                && hasLineOfSight) {
            startAction(MasterArchitectCombatAction.STAFF_STRIKE);
            architect.getNavigation().stop();
            return;
        }

        if (MasterArchitectCombatPolicy.shouldMaintainStorm(
                distanceSquared, sharedSpellCooldown, stormMaintenanceCooldown)) {
            startAction(MasterArchitectCombatAction.STORM_MAINTENANCE);
            architect.getNavigation().stop();
            return;
        }

        maneuver(target, distanceSquared);
    }

    void leaveCombat(ServerLevel level) {
        cleanupExpiredWall(level);
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        healingInterrupted = false;
        wallPlan.clear();
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
    }

    void onHurt() {
        if (activeAction == MasterArchitectCombatAction.LAST_WALL_HEAL) {
            healingInterrupted = true;
        }
    }

    void onDeath(ServerLevel level) {
        removeWall(level);
        leaveCombat(level);
    }

    void addSaveData(CompoundTag tag) {
        tag.putBoolean("MasterLastWallUsed", lastWallUsed);
        tag.putInt("MasterContinuityCooldown", continuityCooldown);
        tag.putInt("MasterThermalCooldown", thermalCooldown);
        tag.putInt("MasterStormMaintenanceCooldown", stormMaintenanceCooldown);
        tag.putLong("MasterWallExpiresAt", wallExpiresAt);
        tag.putLongArray("MasterWallBlocks",
                placedWallBlocks.stream().mapToLong(BlockPos::asLong).toArray());
    }

    void readSaveData(CompoundTag tag) {
        lastWallUsed = tag.getBoolean("MasterLastWallUsed");
        continuityCooldown = Math.max(0, tag.getInt("MasterContinuityCooldown"));
        thermalCooldown = Math.max(0, tag.getInt("MasterThermalCooldown"));
        stormMaintenanceCooldown = tag.contains("MasterStormMaintenanceCooldown")
                ? Math.max(0, tag.getInt("MasterStormMaintenanceCooldown"))
                : 120;
        wallExpiresAt = tag.getLong("MasterWallExpiresAt");
        placedWallBlocks.clear();
        for (long packed : tag.getLongArray("MasterWallBlocks")) {
            placedWallBlocks.add(BlockPos.of(packed));
        }
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        healingInterrupted = false;
        wallPlan.clear();
    }

    private void tickActiveAction(ServerLevel level, ServerPlayer target) {
        actionTicks++;
        architect.setMasterCombatVisual(activeAction, actionTicks);
        architect.getLookControl().setLookAt(target, 40.0F, 35.0F);

        switch (activeAction) {
            case MasterArchitectCombatAction.STAFF_STRIKE ->
                    tickStaffStrike(level, target);
            case MasterArchitectCombatAction.CONTINUITY_FRACTURE ->
                    tickContinuityFracture(level, target);
            case MasterArchitectCombatAction.THERMAL_SEVER ->
                    tickThermalSever(level, target);
            case MasterArchitectCombatAction.LAST_WALL_CAST ->
                    tickLastWallCast(level);
            case MasterArchitectCombatAction.LAST_WALL_HEAL ->
                    tickLastWallHeal(level);
            case MasterArchitectCombatAction.STORM_MAINTENANCE ->
                    tickStormMaintenance(level, target);
            default -> finishAction();
        }
    }

    private void tickStaffStrike(ServerLevel level, ServerPlayer target) {
        if (actionTicks == MasterArchitectCombatPolicy.STAFF_STRIKE_TICK) {
            architect.swing(InteractionHand.MAIN_HAND);
            architect.playSound(ModSounds.MASTER_ARCHITECT_STAFF.get(), 1.45F, 0.86F);
            if (architect.hasLineOfSight(target)
                    && architect.distanceToSqr(target)
                    <= MasterArchitectCombatPolicy.STAFF_RANGE
                    * MasterArchitectCombatPolicy.STAFF_RANGE) {
                architect.doHurtTarget(target);
                Vec3 center = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.ENCHANT,
                        center.x, center.y, center.z,
                        10, 0.35D, 0.55D, 0.35D, 0.12D);
            }
        }
        if (actionTicks >= MasterArchitectCombatPolicy.STAFF_ACTION_TICKS) {
            staffCooldown = MasterArchitectCombatPolicy.STAFF_COOLDOWN_TICKS;
            finishAction();
        }
    }

    private void tickContinuityFracture(ServerLevel level, ServerPlayer target) {
        architect.getNavigation().stop();
        if (actionTicks % 3 == 0) {
            double radius = 0.8D + actionTicks * 0.055D;
            emitRing(level, architect.position().add(0.0D, 0.15D, 0.0D),
                    radius, ParticleTypes.SCULK_SOUL, 12);
        }
        if (actionTicks == MasterArchitectCombatPolicy.CONTINUITY_RELEASE_TICK) {
            if (architect.hasLineOfSight(target)
                    && architect.distanceToSqr(target)
                    <= MasterArchitectCombatPolicy.CONTINUITY_RANGE
                    * MasterArchitectCombatPolicy.CONTINUITY_RANGE) {
                int direction = architect.nextRandomInt(2) == 0 ? -1 : 1;
                PacketDistributor.sendToPlayer(target, new ContinuityFracturePayload(
                        MasterArchitectCombatPolicy.CONTINUITY_EFFECT_TICKS,
                        direction));
                emitRing(level, target.position().add(0.0D, 0.1D, 0.0D),
                        2.1D, ParticleTypes.REVERSE_PORTAL, 24);
                architect.playSound(
                        ModSounds.MASTER_ARCHITECT_FRACTURE.get(), 1.65F, 0.82F);
                FrozenDawn.LOGGER.info(
                        "Master Architect {} cast Continuity Fracture on {}",
                        shortId(architect.getUUID()), target.getGameProfile().getName());
            }
        }
        if (actionTicks >= MasterArchitectCombatPolicy.CONTINUITY_ACTION_TICKS) {
            continuityCooldown = MasterArchitectCombatPolicy.CONTINUITY_COOLDOWN_MIN
                    + architect.nextRandomInt(
                            MasterArchitectCombatPolicy.CONTINUITY_COOLDOWN_VARIANCE + 1);
            sharedSpellCooldown = MasterArchitectCombatPolicy.SHARED_SPELL_COOLDOWN_TICKS;
            finishAction();
        }
    }

    private void tickThermalSever(ServerLevel level, ServerPlayer target) {
        architect.getNavigation().stop();
        if (actionTicks >= 6
                && actionTicks % 2 == 0
                && architect.hasLineOfSight(target)) {
            emitBeam(level,
                    architect.getEyePosition().add(0.0D, -0.35D, 0.0D),
                    target.getBoundingBox().getCenter(),
                    actionTicks < MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK
                            ? 0.55D : 0.22D);
        }
        if (actionTicks == MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK) {
            if (architect.hasLineOfSight(target)
                    && architect.distanceToSqr(target)
                    <= MasterArchitectCombatPolicy.THERMAL_RANGE
                    * MasterArchitectCombatPolicy.THERMAL_RANGE) {
                MasterArchitectThermalSever.apply(target, architect.getUUID());
                architect.playSound(
                        ModSounds.MASTER_ARCHITECT_THERMAL_SEVER.get(), 1.7F, 0.70F);
                Vec3 center = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        center.x, center.y, center.z,
                        35, 0.45D, 0.8D, 0.45D, 0.14D);
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        center.x, center.y, center.z,
                        8, 0.25D, 0.5D, 0.25D, 0.04D);
                FrozenDawn.LOGGER.info(
                        "Master Architect {} cast Thermal Sever on {}",
                        shortId(architect.getUUID()), target.getGameProfile().getName());
            }
        }
        if (actionTicks >= MasterArchitectCombatPolicy.THERMAL_ACTION_TICKS) {
            thermalCooldown = MasterArchitectCombatPolicy.THERMAL_COOLDOWN_MIN
                    + architect.nextRandomInt(
                            MasterArchitectCombatPolicy.THERMAL_COOLDOWN_VARIANCE + 1);
            sharedSpellCooldown = MasterArchitectCombatPolicy.SHARED_SPELL_COOLDOWN_TICKS;
            finishAction();
        }
    }

    private void tickLastWallCast(ServerLevel level) {
        architect.getNavigation().stop();
        if (actionTicks == 1) {
            architect.playSound(ModSounds.MASTER_ARCHITECT_LAST_WALL.get(), 1.7F, 0.76F);
        }
        if (actionTicks == 1 || actionTicks == 4 || actionTicks == 7
                || actionTicks == 10 || actionTicks == 13) {
            int columnIndex = switch (actionTicks) {
                case 1 -> 0;
                case 4 -> 1;
                case 7 -> 2;
                case 10 -> 3;
                default -> 4;
            };
            placeWallColumn(level, columnIndex);
        }
        if (actionTicks >= MasterArchitectCombatPolicy.LAST_WALL_CAST_TICKS) {
            if (placedWallBlocks.isEmpty()) {
                finishAction();
                return;
            }
            activeAction = MasterArchitectCombatAction.LAST_WALL_HEAL;
            actionTicks = 0;
            healingInterrupted = false;
            healTarget = Math.min(
                    architect.getMaxHealth(),
                    architect.getHealth()
                            + architect.getMaxHealth()
                            * MasterArchitectCombatPolicy.LAST_WALL_MAX_HEAL_FRACTION);
            architect.setMasterCombatVisual(activeAction, actionTicks);
        }
    }

    private void tickLastWallHeal(ServerLevel level) {
        architect.getNavigation().stop();
        if (healingInterrupted) {
            level.sendParticles(ParticleTypes.SMOKE,
                    architect.getX(), architect.getY() + 1.0D, architect.getZ(),
                    12, 0.28D, 0.5D, 0.28D, 0.02D);
            finishAction();
            return;
        }
        if (actionTicks % 10 == 0 && architect.getHealth() < healTarget) {
            architect.heal(Math.min(1.0F, healTarget - architect.getHealth()));
        }
        if (actionTicks % 5 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    architect.getX(), architect.getY() + 0.8D, architect.getZ(),
                    3, 0.24D, 0.45D, 0.24D, 0.025D);
        }
        if (actionTicks >= MasterArchitectCombatPolicy.LAST_WALL_HEAL_TICKS
                || architect.getHealth() >= healTarget) {
            finishAction();
        }
    }

    private void tickStormMaintenance(ServerLevel level, ServerPlayer target) {
        architect.getNavigation().stop();
        if (actionTicks == 6) {
            architect.playSound(
                    ModSounds.MASTER_ARCHITECT_STORM_MAINTAIN.get(), 1.6F, 0.84F);
        }
        if (actionTicks >= 8 && actionTicks <= 42 && actionTicks % 4 == 0) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    targetCenter.x, targetCenter.y, targetCenter.z,
                    18, 7.0D, 2.4D, 7.0D, 0.28D);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    architect.getX(), architect.getY() + 1.65D, architect.getZ(),
                    4, 0.55D, 0.20D, 0.55D, 0.035D);
        }
        if (actionTicks >= MasterArchitectCombatPolicy.STORM_MAINTENANCE_ACTION_TICKS) {
            stormMaintenanceCooldown =
                    MasterArchitectCombatPolicy.STORM_MAINTENANCE_COOLDOWN_MIN
                            + architect.nextRandomInt(
                                    MasterArchitectCombatPolicy
                                            .STORM_MAINTENANCE_COOLDOWN_VARIANCE + 1);
            finishAction();
        }
    }

    private boolean beginLastWall(ServerLevel level, ServerPlayer target) {
        wallPlan.clear();
        buildWallPlan(level, target);
        if (wallPlan.size() < 3
                || wallPlan.stream().noneMatch(PlannedWallColumn::weakCenter)) {
            wallPlan.clear();
            return false;
        }
        lastWallUsed = true;
        wallExpiresAt = level.getGameTime()
                + MasterArchitectCombatPolicy.LAST_WALL_LIFETIME_TICKS;
        startAction(MasterArchitectCombatAction.LAST_WALL_CAST);
        architect.getNavigation().stop();
        FrozenDawn.LOGGER.info(
                "Master Architect {} began Last Wall with {} viable columns",
                shortId(architect.getUUID()), wallPlan.size());
        return true;
    }

    private void buildWallPlan(ServerLevel level, ServerPlayer target) {
        Vec3 toward = target.position().subtract(architect.position());
        int normalX;
        int normalZ;
        int tangentX;
        int tangentZ;
        if (Math.abs(toward.x) >= Math.abs(toward.z)) {
            normalX = toward.x >= 0.0D ? 1 : -1;
            normalZ = 0;
            tangentX = 0;
            tangentZ = 1;
        } else {
            normalX = 0;
            normalZ = toward.z >= 0.0D ? 1 : -1;
            tangentX = 1;
            tangentZ = 0;
        }

        BlockPos center = architect.blockPosition().offset(normalX * 2, 0, normalZ * 2);
        int[] offsets = {0, -1, 1, -2, 2};
        for (int offset : offsets) {
            BlockPos column = center.offset(tangentX * offset, 0, tangentZ * offset);
            BlockPos base = findWallBase(level, column);
            if (base != null) {
                wallPlan.add(new PlannedWallColumn(base, offset == 0));
            }
        }
    }

    private BlockPos findWallBase(ServerLevel level, BlockPos column) {
        int startY = architect.blockPosition().getY() + 2;
        for (int y = startY; y >= startY - 6; y--) {
            BlockPos floor = new BlockPos(column.getX(), y - 1, column.getZ());
            BlockPos base = floor.above();
            if (!level.hasChunkAt(base)
                    || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                continue;
            }
            boolean clear = true;
            for (int height = 0; height < WALL_HEIGHT; height++) {
                if (!level.getBlockState(base.above(height)).isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return base;
            }
        }
        return null;
    }

    private void placeWallColumn(ServerLevel level, int index) {
        if (index >= wallPlan.size()) {
            return;
        }
        PlannedWallColumn column = wallPlan.get(index);
        BlockState state = column.weakCenter
                ? Blocks.PACKED_ICE.defaultBlockState()
                : Blocks.BLUE_ICE.defaultBlockState();
        for (int height = 0; height < WALL_HEIGHT; height++) {
            BlockPos pos = column.base.above(height);
            if (!level.hasChunkAt(pos)
                    || !level.getBlockState(pos).isAir()
                    || !level.getEntities(null, new AABB(pos)).isEmpty()) {
                continue;
            }
            level.setBlock(pos, state, Block.UPDATE_ALL);
            placedWallBlocks.add(pos.immutable());
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    10, 0.32D, 0.35D, 0.32D, 0.08D);
        }
        level.playSound(null, column.base,
                SoundEvents.GLASS_PLACE, architect.getSoundSource(), 0.9F,
                column.weakCenter ? 0.72F : 0.56F);
    }

    private void maneuver(ServerPlayer target, double distanceSquared) {
        double distance = Math.sqrt(distanceSquared);
        boolean spellReadySoon = sharedSpellCooldown <= 20
                && (continuityCooldown <= 20 || thermalCooldown <= 20);
        if (spellReadySoon
                && distance >= SPELL_HOLD_MIN_DISTANCE
                && distance <= SPELL_HOLD_MAX_DISTANCE) {
            architect.getNavigation().stop();
            return;
        }
        if (distance < 2.8D) {
            Vec3 away = architect.position().subtract(target.position());
            if (away.horizontalDistanceSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 destination = architect.position().add(away.normalize().scale(4.0D));
            architect.getNavigation().moveTo(
                    destination.x, architect.getY(), destination.z, RETREAT_SPEED);
            return;
        }
        architect.getNavigation().moveTo(target, APPROACH_SPEED);
    }

    private void emitBeam(
            ServerLevel level, Vec3 from, Vec3 to, double spread) {
        Vec3 delta = to.subtract(from);
        for (int step = 1; step <= 12; step++) {
            Vec3 point = from.add(delta.scale(step / 12.0D));
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    point.x, point.y, point.z,
                    1, spread * 0.08D, spread * 0.08D, spread * 0.08D, 0.0D);
            if (step % 3 == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        point.x, point.y, point.z,
                        1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    private void emitRing(
            ServerLevel level,
            Vec3 center,
            double radius,
            ParticleOptions particle,
            int points) {
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0D * index / points;
            level.sendParticles(particle,
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void tickCooldowns() {
        if (staffCooldown > 0) {
            staffCooldown--;
        }
        if (sharedSpellCooldown > 0) {
            sharedSpellCooldown--;
        }
        if (continuityCooldown > 0) {
            continuityCooldown--;
        }
        if (thermalCooldown > 0) {
            thermalCooldown--;
        }
        if (stormMaintenanceCooldown > 0) {
            stormMaintenanceCooldown--;
        }
    }

    private void startAction(int action) {
        activeAction = action;
        actionTicks = 0;
        architect.setMasterCombatVisual(action, 0);
    }

    private void finishAction() {
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
    }

    private void cleanupExpiredWall(ServerLevel level) {
        if (wallExpiresAt >= 0L && level.getGameTime() >= wallExpiresAt) {
            removeWall(level);
        }
    }

    private void removeWall(ServerLevel level) {
        List<BlockPos> unloaded = new ArrayList<>();
        for (BlockPos pos : placedWallBlocks) {
            if (!level.hasChunkAt(pos)) {
                unloaded.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
                level.removeBlock(pos, false);
            }
        }
        placedWallBlocks.clear();
        placedWallBlocks.addAll(unloaded);
        wallPlan.clear();
        if (placedWallBlocks.isEmpty()) {
            wallExpiresAt = -1L;
        }
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    private record PlannedWallColumn(BlockPos base, boolean weakCenter) {
    }
}
