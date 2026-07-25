package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.event.MasterArchitectThermalSever;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.homo.HearthEncounterRole;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.homo.MasterArchitectConstructionPolicy;
import com.frozendawn.homo.MasterArchitectFightMusicManager;
import com.frozendawn.homo.MasterArchitectMusicStage;
import com.frozendawn.homo.MasterArchitectPhasePolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import com.frozendawn.network.ContinuityFracturePayload;
import com.frozendawn.network.MasterArchitectTetherHitPayload;
import com.frozendawn.network.MasterArchitectSeverTelegraphPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final double JUKE_SMASH_RANGE = 3.25D;
    private static final int JUKE_SMASH_TRACK_TICKS = 50;
    private static final int JUKE_SMASH_RELEASE_TICK = 8;
    private static final int JUKE_SMASH_ACTION_TICKS = 14;
    private static final int JUKE_SMASH_COOLDOWN_TICKS = 90;

    private final ArchitectEntity architect;
    private final MasterArchitectConstructionController constructionController;
    private final MasterArchitectFloodController floodController;
    private final List<PlannedWallColumn> wallPlan = new ArrayList<>();
    private final List<BlockPos> placedWallBlocks = new ArrayList<>();
    private final List<BlockPos> placedWallSeams = new ArrayList<>();
    private final Map<UUID, Float> tetherCharges = new LinkedHashMap<>();
    private final List<PendingTetherPulse> pendingTetherPulses = new ArrayList<>();

    private int activeAction = MasterArchitectCombatAction.IDLE;
    private int actionTicks;
    private int staffCooldown;
    private int sharedSpellCooldown = 40;
    private int continuityCooldown = 40;
    private int thermalCooldown = 80;
    private int thermalCooldownDuration = 80;
    private int stormMaintenanceCooldown = 120;
    private int constructionShelterTicks;
    private int constructionHealingTicks;
    private int constructionHealCooldown;
    private int jukeSmashCooldown;
    private int jukeObstructionTicks;
    private boolean tetherUsed;
    private boolean tetherActive;
    private int breakthroughDimTicks;
    private boolean lastWallUsed;
    private boolean healingInterrupted;
    private boolean constructionHealing;
    private float healTarget;
    private long wallExpiresAt = -1L;
    private UUID severTargetId;
    private BlockPos jukeObstruction;
    private BlockPos pendingSmashBlock;
    private int continuityStrafeDirection = 1;
    private MasterArchitectCombatPhase combatPhase = MasterArchitectCombatPhase.KIT;

    MasterArchitectCombatController(ArchitectEntity architect) {
        this.architect = architect;
        this.constructionController =
                new MasterArchitectConstructionController(architect);
        this.floodController = new MasterArchitectFloodController(architect, this);
    }

    void tick(ServerLevel level, ServerPlayer target) {
        refreshPhaseFromHealth(true);
        if (combatPhase == MasterArchitectCombatPhase.FLOOD) {
            prepareFloodOwnership(level);
            if (floodController.tick(level, target)) {
                return;
            }
        }
        tickCooldowns();
        syncThermalCharge();
        cleanupExpiredWall(level);
        constructionController.tick(level, target, combatPhase);
        tickTether(level);
        architect.prepareHearthAssessmentMode();
        architect.setTarget(target);
        architect.equipMasterArchitectStaff();
        architect.getLookControl().setLookAt(target, 40.0F, 35.0F);

        if (MasterArchitectCombatPolicy.shouldUseLastWall(
                        architect.getHealth(), architect.getMaxHealth(), lastWallUsed)
                && (activeAction == MasterArchitectCombatAction.IDLE
                        || activeAction
                                == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST)) {
            if (activeAction == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST) {
                constructionController.cancelCast(level);
                finishAction();
            }
            if (beginLastWall(level, target)) {
                return;
            }
        }

        if (constructionController.isStaggered()) {
            holdConstructionStagger(level);
            return;
        }

        if (activeAction != MasterArchitectCombatAction.IDLE) {
            tickActiveAction(level, target);
            return;
        }

        if (tryBeginObstructionSmash(level, target)) {
            return;
        }

        if (tickConstructionShelterHeal(level, target)) {
            return;
        }

        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
        if (MasterArchitectCombatPolicy.shouldUseTether(
                architect.getHealth(), architect.getMaxHealth(), tetherUsed)) {
            beginTether(level, target);
            return;
        }
        if (constructionController.tryBeginConstruction(level, target, combatPhase)) {
            startAction(MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST);
            architect.getNavigation().stop();
            architect.playSound(
                    ModSounds.MASTER_ARCHITECT_CONSTRUCTION.get(), 1.45F, 0.66F);
            return;
        }

        if (combatPhase == MasterArchitectCombatPhase.CONSTRUCTION
                && constructionController.seekVantage(level)) {
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
            beginThermalSever(level, target);
            return;
        }
        if (sharedSpellCooldown <= 0
                && MasterArchitectCombatPolicy.canCast(
                        distanceSquared,
                        MasterArchitectCombatPolicy.CONTINUITY_RANGE,
                        hasLineOfSight,
                        continuityCooldown)) {
            startAction(MasterArchitectCombatAction.CONTINUITY_FRACTURE);
            continuityStrafeDirection = architect.nextRandomInt(2) == 0 ? -1 : 1;
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
        floodController.onCombatLost(level);
        refreshPhaseFromHealth(true);
        tickCooldowns();
        syncThermalCharge();
        cleanupExpiredWall(level);
        constructionController.tick(level, null, combatPhase);
        if (activeAction == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST) {
            constructionController.cancelCast(level);
        }
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        healingInterrupted = false;
        resetConstructionHealing(false);
        wallPlan.clear();
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
        severTargetId = null;
        jukeObstruction = null;
        pendingSmashBlock = null;
        jukeObstructionTicks = 0;
    }

    void onHurt(ServerLevel level, @Nullable ServerPlayer target) {
        refreshPhaseFromHealth(true);
        if (activeAction == MasterArchitectCombatAction.LAST_WALL_HEAL) {
            healingInterrupted = true;
        }
        if (constructionShelterTicks > 0 || constructionHealing) {
            resetConstructionHealing(true);
        }
        // The threshold hit owns the phase transition. Waiting for the next AI
        // target tick leaves the clamped Master apparently immune at 10%.
        if (combatPhase == MasterArchitectCombatPhase.FLOOD && target != null) {
            prepareFloodOwnership(level);
            floodController.tick(level, target);
        }
    }

    private void prepareFloodOwnership(ServerLevel level) {
        if (floodController.isActive()
                || floodController.isMindSessionActive()
                || floodController.isRetreating()
                || !MasterArchitectPhasePolicy.isAtFloodEntry(
                        architect.getHealth(), architect.getMaxHealth())) {
            return;
        }
        if (tetherActive) {
            endTether(level);
        }
        if (activeAction == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST) {
            constructionController.cancelCast(level);
        }
        finishAction();
    }

    float prepareIncomingDamage(float incomingDamage, boolean bypassesInvulnerability) {
        return MasterArchitectPhasePolicy.clampFloodEntryDamage(
                combatPhase,
                architect.getHealth(),
                architect.getMaxHealth(),
                incomingDamage,
                bypassesInvulnerability);
    }

    void onDeath(ServerLevel level, ServerPlayer killer) {
        floodController.onDeath(level, killer);
        endTether(level);
        architect.getHearthMasterArchitectId().ifPresent(
                hearthId -> HearthCombatRosterManager.onMasterDefeated(level, hearthId));
        constructionController.onDeath(level);
        removeWall(level);
        leaveCombat(level);
    }

    boolean isMindSessionActive() {
        return floodController.isMindSessionActive();
    }

    void tickPersistentState(ServerLevel level) {
        floodController.tickRearmCountdown(level);
    }

    void tickFolded(ServerLevel level) {
        floodController.tickFolded(level);
    }

    void onMindCopyHurt(
            ServerLevel level,
            ArchitectEntity copy,
            net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        floodController.onMindCopyHurt(level, copy, source, amount);
    }

    float prepareMindCopyDamage(
            ServerLevel level,
            ArchitectEntity copy,
            net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        return floodController.prepareMindCopyDamage(level, copy, source, amount);
    }

    void onMindCopyDefeated(
            ServerLevel level,
            ArchitectEntity copy,
            ServerPlayer killer) {
        floodController.onMindCopyDefeated(level, copy, killer);
    }

    void onMindParticipantFailed(
            ServerLevel level, ServerPlayer player, String reason) {
        floodController.participantFailed(level, player, reason);
    }

    MasterArchitectMusicStage musicStage() {
        if (floodController.isMindSessionActive()) {
            return MasterArchitectMusicStage.FLOOD;
        }
        return MasterArchitectMusicStage.forCombatState(tetherUsed, lastWallUsed);
    }

    TetherDamageResult redistributeIncomingDamage(
            ServerLevel level, float incomingDamage) {
        if (!tetherActive || incomingDamage <= 0.0F) {
            return TetherDamageResult.passthrough(incomingDamage);
        }
        UUID hearthId = architect.getHearthMasterArchitectId().orElse(null);
        if (hearthId == null) {
            return TetherDamageResult.passthrough(incomingDamage);
        }

        List<Map.Entry<UUID, Float>> activeNodes = tetherCharges.entrySet().stream()
                .filter(entry -> {
                    Entity entity = level.getEntity(entry.getKey());
                    return entity instanceof LivingEntity living && living.isAlive()
                            && HearthCombatRosterManager.role(
                                    level, hearthId, entry.getKey())
                            == HearthEncounterRole.TETHERED;
                })
                .toList();
        if (activeNodes.isEmpty()) {
            finishTetherIfEmpty(level, hearthId);
            return TetherDamageResult.passthrough(incomingDamage);
        }

        float desiredRedirect = MasterArchitectCombatPolicy
                .desiredTetherRedirect(incomingDamage);
        float redirectRemaining = desiredRedirect;
        float redirected = 0.0F;
        int remainingNodes = activeNodes.size();
        for (Map.Entry<UUID, Float> node : activeNodes) {
            Entity entity = level.getEntity(node.getKey());
            if (!(entity instanceof LivingEntity resident)) {
                remainingNodes--;
                continue;
            }
            float share = remainingNodes > 0
                    ? redirectRemaining / remainingNodes
                    : 0.0F;
            float request = MasterArchitectCombatPolicy.safeTransferRequest(
                    share, node.getValue(), resident.getHealth());
            remainingNodes--;
            if (request <= 0.0F) {
                continue;
            }

            float before = resident.getHealth();
            resident.hurt(architect.damageSources().mobAttack(architect), request);
            float transferred = Math.max(0.0F, before - resident.getHealth());
            if (transferred <= 0.0F) {
                continue;
            }
            node.setValue(Math.max(0.0F, node.getValue() - transferred));
            redirectRemaining = Math.max(0.0F, redirectRemaining - transferred);
            redirected += transferred;
            queueTetherPulse(level, resident);

            if (resident.getHealth()
                    <= MasterArchitectCombatPolicy.TETHER_SURVIVAL_FLOOR + 0.001F) {
                overloadTether(level, hearthId, resident);
            }
        }
        float chargeCapacity = activeNodes.size()
                * MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER;
        float remainingCharge = (float) activeNodes.stream()
                .mapToDouble(entry -> Math.max(0.0F, entry.getValue()))
                .sum();
        float chargeFraction = chargeCapacity > 0.0F
                ? remainingCharge / chargeCapacity
                : 0.0F;
        MasterArchitectCombatPolicy.TetherFeedbackState feedback =
                MasterArchitectCombatPolicy.tetherFeedbackState(
                        desiredRedirect, redirected, chargeFraction);
        playTetherFeedback(level, feedback);
        finishTetherIfEmpty(level, hearthId);
        return new TetherDamageResult(
                Math.max(0.0F, incomingDamage - redirected),
                feedback != MasterArchitectCombatPolicy.TetherFeedbackState.BREAKTHROUGH
                        && redirected > 0.0F);
    }

    void addSaveData(CompoundTag tag) {
        tag.putString("MasterCombatPhase", combatPhase.serializedName());
        tag.putBoolean("MasterTetherUsed", tetherUsed);
        tag.putBoolean("MasterTetherActive", tetherActive);
        ListTag tethers = new ListTag();
        tetherCharges.forEach((entityId, charge) -> {
            CompoundTag tether = new CompoundTag();
            tether.putUUID("EntityId", entityId);
            tether.putFloat("Charge", charge);
            tethers.add(tether);
        });
        tag.put("MasterTethers", tethers);
        tag.putBoolean("MasterLastWallUsed", lastWallUsed);
        tag.putInt("MasterContinuityCooldown", continuityCooldown);
        tag.putInt("MasterThermalCooldown", thermalCooldown);
        tag.putInt("MasterThermalCooldownDuration", thermalCooldownDuration);
        tag.putInt("MasterStormMaintenanceCooldown", stormMaintenanceCooldown);
        tag.putInt("MasterConstructionHealCooldown", constructionHealCooldown);
        tag.putInt("MasterJukeSmashCooldown", jukeSmashCooldown);
        tag.putLong("MasterWallExpiresAt", wallExpiresAt);
        tag.putLongArray("MasterWallBlocks",
                placedWallBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("MasterWallSeams",
                placedWallSeams.stream().mapToLong(BlockPos::asLong).toArray());
        constructionController.addSaveData(tag);
        floodController.addSaveData(tag);
    }

    void readSaveData(CompoundTag tag) {
        tetherUsed = tag.getBoolean("MasterTetherUsed");
        tetherActive = tag.getBoolean("MasterTetherActive");
        tetherCharges.clear();
        ListTag tethers = tag.getList("MasterTethers", Tag.TAG_COMPOUND);
        for (Tag entry : tethers) {
            if (entry instanceof CompoundTag tether && tether.hasUUID("EntityId")) {
                tetherCharges.put(tether.getUUID("EntityId"), Math.max(0.0F,
                        Math.min(MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER,
                                tether.getFloat("Charge"))));
            }
        }
        tetherActive &= !tetherCharges.isEmpty();
        pendingTetherPulses.clear();
        breakthroughDimTicks = 0;
        lastWallUsed = tag.getBoolean("MasterLastWallUsed");
        MasterArchitectCombatPhase loadedPhase = tag.contains(
                "MasterCombatPhase", Tag.TAG_STRING)
                ? MasterArchitectCombatPhase.fromSerializedName(
                        tag.getString("MasterCombatPhase"))
                : MasterArchitectPhasePolicy.migrateLegacyState(
                        architect.getHealth(), architect.getMaxHealth(),
                        tetherUsed, lastWallUsed);
        combatPhase = MasterArchitectPhasePolicy.advance(
                loadedPhase, architect.getHealth(), architect.getMaxHealth());
        architect.setMasterCombatPhase(combatPhase);
        continuityCooldown = Math.max(0, tag.getInt("MasterContinuityCooldown"));
        thermalCooldown = Math.max(0, tag.getInt("MasterThermalCooldown"));
        thermalCooldownDuration = tag.contains("MasterThermalCooldownDuration")
                ? Math.max(1, tag.getInt("MasterThermalCooldownDuration"))
                : Math.max(1, thermalCooldown);
        stormMaintenanceCooldown = tag.contains("MasterStormMaintenanceCooldown")
                ? Math.max(0, tag.getInt("MasterStormMaintenanceCooldown"))
                : 120;
        constructionHealCooldown = Math.max(
                0, tag.getInt("MasterConstructionHealCooldown"));
        jukeSmashCooldown = Math.max(0, tag.getInt("MasterJukeSmashCooldown"));
        jukeObstructionTicks = 0;
        jukeObstruction = null;
        pendingSmashBlock = null;
        constructionShelterTicks = 0;
        constructionHealingTicks = 0;
        constructionHealing = false;
        wallExpiresAt = tag.getLong("MasterWallExpiresAt");
        placedWallBlocks.clear();
        for (long packed : tag.getLongArray("MasterWallBlocks")) {
            placedWallBlocks.add(BlockPos.of(packed));
        }
        placedWallSeams.clear();
        for (long packed : tag.getLongArray("MasterWallSeams")) {
            BlockPos seam = BlockPos.of(packed);
            if (placedWallBlocks.contains(seam)) {
                placedWallSeams.add(seam);
            }
        }
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        healingInterrupted = false;
        wallPlan.clear();
        severTargetId = null;
        constructionController.readSaveData(tag, combatPhase);
        floodController.readSaveData(tag);
        syncThermalCharge();
    }

    private void refreshPhaseFromHealth(boolean logTransition) {
        MasterArchitectCombatPhase next = MasterArchitectPhasePolicy.advance(
                combatPhase, architect.getHealth(), architect.getMaxHealth());
        if (next != combatPhase) {
            MasterArchitectCombatPhase previous = combatPhase;
            combatPhase = next;
            if (logTransition && !architect.level().isClientSide()) {
                FrozenDawn.LOGGER.info(
                        "Master Architect {} advanced {} -> {} at {}/{} health",
                        shortId(architect.getUUID()),
                        previous.serializedName(), next.serializedName(),
                        String.format("%.1f", architect.getHealth()),
                        String.format("%.1f", architect.getMaxHealth()));
            }
        }
        architect.setMasterCombatPhase(combatPhase);
    }

    void resumeAfterFailedMind(ServerLevel level) {
        endTether(level);
        removeWall(level);
        finishAction();
        combatPhase = MasterArchitectPhasePolicy.phaseForHealth(
                architect.getHealth(), architect.getMaxHealth());
        if (combatPhase == MasterArchitectCombatPhase.FLOOD) {
            combatPhase = MasterArchitectCombatPhase.ASCENT;
        }
        tetherUsed = false;
        lastWallUsed = false;
        architect.setMasterCombatPhase(combatPhase);
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
        FrozenDawn.LOGGER.info(
                "Master Architect {} resumed at {} after Thae Iven restored it to {}/{} health",
                shortId(architect.getUUID()), combatPhase.serializedName(),
                String.format("%.1f", architect.getHealth()),
                String.format("%.1f", architect.getMaxHealth()));
    }

    private void beginTether(ServerLevel level, ServerPlayer target) {
        tetherUsed = true;
        MasterArchitectFightMusicManager.pushStage(
                level, architect, MasterArchitectMusicStage.TETHER);
        UUID hearthId = architect.getHearthMasterArchitectId().orElse(null);
        if (hearthId == null) {
            return;
        }
        List<LivingEntity> selected = HearthCombatRosterManager.beginTether(
                level, hearthId, architect,
                MasterArchitectCombatPolicy.TETHER_MAX_MEMBERS);
        tetherCharges.clear();
        selected.forEach(resident -> tetherCharges.put(
                resident.getUUID(),
                MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER));
        tetherActive = !tetherCharges.isEmpty();
        if (tetherActive) {
            HearthCombatRosterManager.signalMasterCast(
                    level, hearthId, architect, target, 20);
        }

        architect.playSound(
                ModSounds.MASTER_ARCHITECT_TETHER_DEPLOY.get(), 1.9F, 0.82F);
        architect.playSound(
                ModSounds.MASTER_ARCHITECT_TETHER_WAIL.get(), 3.6F, 0.56F);
        HearthMasterArchitectWeatherManager.broadcastAuraEvent(
                level,
                MasterArchitectAuraEventPayload.TETHER_SHUDDER,
                architect.blockPosition().above(72),
                architect.blockPosition(),
                1.35F);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                architect.getX(), architect.getY() + 1.45D, architect.getZ(),
                tetherActive ? 28 : 10, 0.55D, 0.8D, 0.55D, 0.06D);
        if (tetherActive) {
            emitRing(level,
                    architect.position().add(0.0D, 0.12D, 0.0D),
                    1.6D, ParticleTypes.END_ROD, 28);
            selected.forEach(resident -> emitTetherDeployment(level, resident));
        }
        FrozenDawn.LOGGER.info(
                "Master Architect {} invoked Thae Iven with {} living nodes",
                shortId(architect.getUUID()), tetherCharges.size());
    }

    private void tickTether(ServerLevel level) {
        tickTetherPulses(level);
        if (breakthroughDimTicks > 0) {
            breakthroughDimTicks--;
        }
        if (!tetherActive) {
            return;
        }
        UUID hearthId = architect.getHearthMasterArchitectId().orElse(null);
        if (hearthId == null) {
            tetherActive = false;
            tetherCharges.clear();
            return;
        }

        List<LivingEntity> exhausted = tetherCharges.keySet().stream()
                .map(level::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(LivingEntity::isAlive)
                .filter(resident -> resident.getHealth()
                        <= MasterArchitectCombatPolicy.TETHER_SURVIVAL_FLOOR + 0.001F)
                .toList();
        exhausted.forEach(resident -> overloadTether(level, hearthId, resident));
        tetherCharges.replaceAll((entityId, charge) ->
                MasterArchitectCombatPolicy.rechargeTether(charge));
        tetherCharges.entrySet().removeIf(entry -> {
            if (HearthCombatRosterManager.role(level, hearthId, entry.getKey())
                    != HearthEncounterRole.TETHERED) {
                return true;
            }
            Entity entity = level.getEntity(entry.getKey());
            return entity != null
                    && (!(entity instanceof LivingEntity living) || !living.isAlive());
        });
        if (architect.tickCount % 3 == 0) {
            for (UUID entityId : tetherCharges.keySet()) {
                Entity entity = level.getEntity(entityId);
                if (entity instanceof LivingEntity resident && resident.isAlive()) {
                    float chargeFraction = tetherCharges.getOrDefault(entityId, 0.0F)
                            / MasterArchitectCombatPolicy.TETHER_MAX_CHARGE_PER_MEMBER;
                    emitTetherBeam(level, resident, chargeFraction);
                }
            }
        }
        finishTetherIfEmpty(level, hearthId);
    }

    private void endTether(ServerLevel level) {
        UUID hearthId = architect.getHearthMasterArchitectId().orElse(null);
        if (hearthId != null) {
            HearthCombatRosterManager.releaseTethers(level, hearthId);
        }
        tetherActive = false;
        tetherCharges.clear();
    }

    private void finishTetherIfEmpty(ServerLevel level, UUID hearthId) {
        tetherCharges.entrySet().removeIf(entry -> {
            if (HearthCombatRosterManager.role(level, hearthId, entry.getKey())
                    != HearthEncounterRole.TETHERED) {
                return true;
            }
            Entity entity = level.getEntity(entry.getKey());
            return entity != null
                    && (!(entity instanceof LivingEntity living) || !living.isAlive());
        });
        if (tetherCharges.isEmpty()) {
            tetherActive = false;
            HearthCombatRosterManager.releaseTethers(level, hearthId);
        }
    }

    private void overloadTether(
            ServerLevel level, UUID hearthId, LivingEntity resident) {
        resident.setHealth(Math.max(
                MasterArchitectCombatPolicy.TETHER_SURVIVAL_FLOOR,
                resident.getHealth()));
        HearthCombatRosterManager.markSpent(level, hearthId, resident.getUUID());
        tetherCharges.remove(resident.getUUID());
        if (resident instanceof Mob mob) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            mob.getNavigation().stop();
        }

        Vec3 away = resident.position().subtract(architect.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 stagger = away.normalize().scale(0.62D).add(0.0D, 0.24D, 0.0D);
        resident.setDeltaMovement(stagger);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                resident.getX(), resident.getY() + resident.getBbHeight() * 0.55D,
                resident.getZ(), 18, 0.38D, 0.55D, 0.38D, 0.09D);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                resident.getX(), resident.getY() + resident.getBbHeight() * 0.55D,
                resident.getZ(), 22, 0.45D, 0.65D, 0.45D, 0.16D);
        level.playSound(null, resident.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_BREAK, architect.getSoundSource(),
                1.35F, 0.55F);
    }

    private void emitTetherBeam(
            ServerLevel level, LivingEntity resident, float chargeFraction) {
        Vec3 from = architect.position().add(0.0D, architect.getBbHeight() * 0.72D, 0.0D);
        Vec3 to = resident.position().add(0.0D, resident.getBbHeight() * 0.58D, 0.0D);
        Vec3 delta = to.subtract(from);
        boolean breakthrough = breakthroughDimTicks > 0;
        int steps = breakthrough ? 6 : chargeFraction <= 0.50F ? 10 : 14;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.add(delta.scale(step / (double) steps));
            ParticleOptions particle = !breakthrough && step % 3 == 0
                    ? ParticleTypes.SNOWFLAKE
                    : ParticleTypes.SCULK_SOUL;
            level.sendParticles(particle,
                    point.x, point.y, point.z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
    }

    private void emitTetherDeployment(ServerLevel level, LivingEntity resident) {
        Vec3 from = architect.position().add(
                0.0D, architect.getBbHeight() * 0.72D, 0.0D);
        Vec3 to = resident.position().add(
                0.0D, resident.getBbHeight() * 0.58D, 0.0D);
        Vec3 delta = to.subtract(from);
        for (int step = 1; step <= 20; step++) {
            Vec3 point = from.add(delta.scale(step / 20.0D));
            level.sendParticles(step % 4 == 0
                            ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
                    point.x, point.y, point.z,
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        level.sendParticles(ParticleTypes.END_ROD,
                to.x, to.y, to.z,
                12, 0.22D, 0.38D, 0.22D, 0.035D);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                to.x, to.y, to.z,
                18, 0.32D, 0.52D, 0.32D, 0.08D);
    }

    private void queueTetherPulse(ServerLevel level, LivingEntity resident) {
        pendingTetherPulses.add(new PendingTetherPulse(resident.getUUID()));
        emitTetherPulsePoint(level, resident, 0.18D);
    }

    private void tickTetherPulses(ServerLevel level) {
        Iterator<PendingTetherPulse> pulses = pendingTetherPulses.iterator();
        while (pulses.hasNext()) {
            PendingTetherPulse pulse = pulses.next();
            Entity entity = level.getEntity(pulse.residentId());
            if (!(entity instanceof LivingEntity resident) || !resident.isAlive()) {
                pulses.remove();
                continue;
            }
            pulse.advance();
            double progress = pulse.ageTicks() >= 2 ? 1.0D : 0.58D;
            emitTetherPulsePoint(level, resident, progress);
            if (pulse.ageTicks() >= 2) {
                flinchOnPulseArrival(level, resident);
                pulses.remove();
            }
        }
    }

    private void emitTetherPulsePoint(
            ServerLevel level, LivingEntity resident, double progress) {
        Vec3 from = architect.position().add(0.0D, architect.getBbHeight() * 0.72D, 0.0D);
        Vec3 to = resident.position().add(0.0D, resident.getBbHeight() * 0.58D, 0.0D);
        Vec3 point = from.lerp(to, progress);
        level.sendParticles(ParticleTypes.END_ROD,
                point.x, point.y, point.z,
                progress >= 1.0D ? 5 : 3,
                0.045D, 0.045D, 0.045D, 0.01D);
    }

    private void flinchOnPulseArrival(ServerLevel level, LivingEntity resident) {
        Vec3 away = resident.position().subtract(architect.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        resident.setDeltaMovement(resident.getDeltaMovement().add(
                away.normalize().scale(0.18D).add(0.0D, 0.08D, 0.0D)));
        resident.hurtMarked = true;
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                resident.getX(), resident.getY() + resident.getBbHeight() * 0.58D,
                resident.getZ(), 7, 0.20D, 0.32D, 0.20D, 0.035D);
    }

    private void playTetherFeedback(
            ServerLevel level,
            MasterArchitectCombatPolicy.TetherFeedbackState feedback) {
        PacketDistributor.sendToPlayersTrackingEntity(architect,
                new MasterArchitectTetherHitPayload(architect.getId(), feedback.id()));
        switch (feedback) {
            case HEALTHY -> level.playSound(null, architect.blockPosition(),
                    ModSounds.MASTER_ARCHITECT_TETHER_DEFLECT.get(),
                    architect.getSoundSource(), 1.45F, 1.0F);
            case STRAINED -> level.playSound(null, architect.blockPosition(),
                    ModSounds.MASTER_ARCHITECT_TETHER_STRAIN.get(),
                    architect.getSoundSource(), 1.55F, 1.0F);
            case BREAKTHROUGH -> breakthroughDimTicks = 12;
        }
    }

    private boolean tickConstructionShelterHeal(
            ServerLevel level,
            ServerPlayer target) {
        if (combatPhase != MasterArchitectCombatPhase.CONSTRUCTION
                || constructionHealCooldown > 0) {
            resetConstructionHealing(false);
            return false;
        }
        boolean sheltered = constructionController.hasIntactShelterBetween(
                level, target);
        if (!sheltered) {
            resetConstructionHealing(constructionHealing);
            return false;
        }
        float ceiling = MasterArchitectConstructionPolicy.shelterHealCeiling(
                combatPhase, architect.getMaxHealth());
        if (architect.getHealth() >= ceiling) {
            resetConstructionHealing(false);
            return false;
        }

        architect.getNavigation().stop();
        Vec3 velocity = architect.getDeltaMovement();
        architect.setDeltaMovement(0.0D, velocity.y, 0.0D);
        constructionShelterTicks++;
        String preset = ApocalypseState.get(level.getServer()).getPresetName();
        int graceTicks = MasterArchitectConstructionPolicy.shelterHealGraceTicks(
                preset);
        if (constructionShelterTicks < graceTicks) {
            return true;
        }

        if (!constructionHealing) {
            constructionHealing = true;
            FrozenDawn.LOGGER.info(
                    "Master Architect {} began channeling Construction War shelter",
                    shortId(architect.getUUID()));
        }
        constructionHealingTicks++;
        architect.heal(Math.min(
                MasterArchitectConstructionPolicy.shelterHealPerTick(
                        architect.getMaxHealth(), preset),
                ceiling - architect.getHealth()));
        if (constructionHealingTicks % 2 == 0) {
            constructionController.emitShelterHealingParticles(level);
        }
        if (constructionHealingTicks
                        >= MasterArchitectConstructionPolicy.SHELTER_HEAL_MAX_TICKS
                || architect.getHealth() >= ceiling) {
            resetConstructionHealing(true);
        }
        return true;
    }

    private void resetConstructionHealing(boolean startCooldown) {
        constructionShelterTicks = 0;
        constructionHealingTicks = 0;
        constructionHealing = false;
        if (startCooldown) {
            constructionHealCooldown = Math.max(
                    constructionHealCooldown,
                    MasterArchitectConstructionPolicy.SHELTER_HEAL_COOLDOWN_TICKS);
        }
    }

    private void tickActiveAction(ServerLevel level, ServerPlayer target) {
        actionTicks++;
        architect.setMasterCombatVisual(activeAction, actionTicks);
        architect.getLookControl().setLookAt(target, 40.0F, 35.0F);
        signalCastSpacing(level, target);

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
            case MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST ->
                    tickConstructionWall(level);
            case MasterArchitectCombatAction.OBSTRUCTION_SMASH ->
                    tickObstructionSmash(level);
            default -> finishAction();
        }
    }

    private boolean tryBeginObstructionSmash(
            ServerLevel level, ServerPlayer target) {
        if (jukeSmashCooldown > 0
                || architect.distanceToSqr(target)
                        > JUKE_SMASH_RANGE * JUKE_SMASH_RANGE
                || architect.hasLineOfSight(target)) {
            resetJukeTracking();
            return false;
        }

        BlockPos obstruction = singlePlayerObstruction(level, target);
        if (obstruction == null) {
            resetJukeTracking();
            return false;
        }
        if (!obstruction.equals(jukeObstruction)) {
            jukeObstruction = obstruction;
            jukeObstructionTicks = 1;
            return false;
        }
        if (++jukeObstructionTicks < JUKE_SMASH_TRACK_TICKS) {
            return false;
        }

        pendingSmashBlock = obstruction.immutable();
        resetJukeTracking();
        startAction(MasterArchitectCombatAction.OBSTRUCTION_SMASH);
        architect.getNavigation().stop();
        architect.getLookControl().setLookAt(
                Vec3.atCenterOf(pendingSmashBlock));
        architect.playSound(
                ModSounds.MASTER_ARCHITECT_OBSTRUCTION_SMASH.get(), 1.8F, 0.72F);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                pendingSmashBlock.getX() + 0.5D,
                pendingSmashBlock.getY() + 0.5D,
                pendingSmashBlock.getZ() + 0.5D,
                12, 0.25D, 0.25D, 0.25D, 0.035D);
        FrozenDawn.LOGGER.info(
                "Master Architect {} marked player obstruction {} for a juke break",
                shortId(architect.getUUID()), pendingSmashBlock);
        return true;
    }

    private void tickObstructionSmash(ServerLevel level) {
        architect.getNavigation().stop();
        if (pendingSmashBlock == null) {
            finishAction();
            return;
        }
        architect.getLookControl().setLookAt(Vec3.atCenterOf(pendingSmashBlock));
        if (actionTicks == JUKE_SMASH_RELEASE_TICK
                && isValidJukeObstruction(level, pendingSmashBlock)) {
            BlockState state = level.getBlockState(pendingSmashBlock);
            level.levelEvent(2001, pendingSmashBlock, Block.getId(state));
            level.destroyBlock(pendingSmashBlock, true, architect);
            PlayerPlacedBlockTracker.get(level.getServer())
                    .markRemoved(pendingSmashBlock);
            level.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    pendingSmashBlock.getX() + 0.5D,
                    pendingSmashBlock.getY() + 0.5D,
                    pendingSmashBlock.getZ() + 0.5D,
                    18, 0.35D, 0.35D, 0.35D, 0.08D);
        }
        if (actionTicks >= JUKE_SMASH_ACTION_TICKS) {
            pendingSmashBlock = null;
            jukeSmashCooldown = JUKE_SMASH_COOLDOWN_TICKS;
            finishAction();
        }
    }

    private BlockPos singlePlayerObstruction(
            ServerLevel level, ServerPlayer target) {
        Vec3 start = architect.getEyePosition();
        Vec3 end = target.getEyePosition();
        Vec3 direction = end.subtract(start).normalize();
        BlockHitResult first = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, architect));
        if (first.getType() != HitResult.Type.BLOCK
                || !isValidJukeObstruction(level, first.getBlockPos())) {
            return null;
        }

        Vec3 beyond = Vec3.atCenterOf(first.getBlockPos())
                .add(direction.scale(0.90D));
        BlockHitResult second = level.clip(new ClipContext(
                beyond, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, architect));
        return second.getType() == HitResult.Type.MISS
                ? first.getBlockPos().immutable()
                : null;
    }

    private boolean isValidJukeObstruction(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)
                || architect.distanceToSqr(Vec3.atCenterOf(pos))
                        > JUKE_SMASH_RANGE * JUKE_SMASH_RANGE
                || constructionController.isTrackedConstructionBlock(pos)
                || level.getBlockEntity(pos) != null
                || !PlayerPlacedBlockTracker.get(level.getServer()).isPlayerPlaced(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !ArchitectBreakPolicy.isProtectedBlock(state);
    }

    private void resetJukeTracking() {
        jukeObstruction = null;
        jukeObstructionTicks = 0;
    }

    private void tickConstructionWall(ServerLevel level) {
        architect.getNavigation().stop();
        if (constructionController.placeNextStep(level)) {
            constructionController.finishConstruction(level);
            sharedSpellCooldown = Math.max(sharedSpellCooldown, 30);
            finishAction();
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
        architect.getMoveControl().strafe(
                0.08F, continuityStrafeDirection * 0.52F);
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
        ServerPlayer severTarget = resolveSeverTarget(level, target);
        if (severTarget == null) {
            finishAction();
            return;
        }
        plantForThermalSever();
        architect.getLookControl().setLookAt(severTarget, 45.0F, 40.0F);
        if (actionTicks >= 6
                && actionTicks % 2 == 0
                && architect.hasLineOfSight(severTarget)) {
            emitBeam(level,
                    thermalHandFocus(),
                    severTarget.getBoundingBox().getCenter(),
                    actionTicks < MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK
                            ? 0.55D : 0.22D);
        }
        if (actionTicks == MasterArchitectCombatPolicy.THERMAL_COMMIT_TICK) {
            architect.playSound(
                    ModSounds.MASTER_ARCHITECT_THERMAL_COMMIT.get(), 1.9F, 0.92F);
            Vec3 flash = thermalHandFocus();
            level.sendParticles(ParticleTypes.END_ROD,
                    flash.x, flash.y, flash.z,
                    42, 0.34D, 0.34D, 0.34D, 0.11D);
            level.sendParticles(ParticleTypes.SCULK_SOUL,
                    flash.x, flash.y, flash.z,
                    26, 0.38D, 0.38D, 0.38D, 0.07D);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    flash.x, flash.y, flash.z,
                    45, 0.42D, 0.48D, 0.42D, 0.13D);
        }
        if (actionTicks == MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK) {
            if (architect.hasLineOfSight(severTarget)
                    && architect.distanceToSqr(severTarget)
                    <= MasterArchitectCombatPolicy.THERMAL_RANGE
                    * MasterArchitectCombatPolicy.THERMAL_RANGE) {
                MasterArchitectThermalSever.apply(
                        severTarget, architect.getUUID());
                architect.playSound(
                        ModSounds.MASTER_ARCHITECT_THERMAL_SEVER.get(), 1.7F, 0.70F);
                Vec3 center = severTarget.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        center.x, center.y, center.z,
                        35, 0.45D, 0.8D, 0.45D, 0.14D);
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        center.x, center.y, center.z,
                        8, 0.25D, 0.5D, 0.25D, 0.04D);
                FrozenDawn.LOGGER.info(
                        "Master Architect {} cast Thermal Sever on {}",
                        shortId(architect.getUUID()),
                        severTarget.getGameProfile().getName());
            }
        }
        if (actionTicks >= MasterArchitectCombatPolicy.THERMAL_ACTION_TICKS) {
            thermalCooldown = MasterArchitectCombatPolicy.THERMAL_COOLDOWN_MIN
                    + architect.nextRandomInt(
                            MasterArchitectCombatPolicy.THERMAL_COOLDOWN_VARIANCE + 1);
            thermalCooldownDuration = thermalCooldown;
            syncThermalCharge();
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
            if (placedWallBlocks.isEmpty() || placedWallSeams.isEmpty()) {
                lastWallUsed = false;
                removeWall(level);
                FrozenDawn.LOGGER.info(
                        "Master Architect {} deferred Last Wall after terrain blocked placement",
                        shortId(architect.getUUID()));
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
        if (actionTicks % MasterArchitectCombatPolicy.LAST_WALL_HEAL_PULSE_TICKS == 0
                && architect.getHealth() < healTarget) {
            architect.heal(Math.min(
                    MasterArchitectCombatPolicy.lastWallHealPerPulse(
                            architect.getMaxHealth()),
                    healTarget - architect.getHealth()));
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
        if (tetherActive) {
            endTether(level);
        }
        constructionController.clearLastWallFootprint(level);
        wallPlan.clear();
        placedWallSeams.clear();
        buildWallPlan(level, target);
        if (wallPlan.size() < 3
                || wallPlan.stream().noneMatch(PlannedWallColumn::weakCenter)) {
            wallPlan.clear();
            return false;
        }
        lastWallUsed = true;
        MasterArchitectFightMusicManager.pushStage(
                level, architect, MasterArchitectMusicStage.LAST_WALL);
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
        MasterArchitectConstructionPolicy.WallAxes primary =
                MasterArchitectConstructionPolicy.wallAxes(toward.x, toward.z);
        List<MasterArchitectConstructionPolicy.WallAxes> orientations = List.of(
                primary,
                new MasterArchitectConstructionPolicy.WallAxes(
                        primary.tangentX(), primary.tangentZ(),
                        -primary.normalX(), -primary.normalZ()),
                new MasterArchitectConstructionPolicy.WallAxes(
                        -primary.tangentX(), -primary.tangentZ(),
                        primary.normalX(), primary.normalZ()),
                new MasterArchitectConstructionPolicy.WallAxes(
                        -primary.normalX(), -primary.normalZ(),
                        primary.tangentX(), primary.tangentZ()));
        List<PlannedWallColumn> best = List.of();
        int[] offsets = {0, -1, 1, -2, 2};
        for (MasterArchitectConstructionPolicy.WallAxes axes : orientations) {
            for (int shift = -2; shift <= 2; shift++) {
                BlockPos center = architect.blockPosition().offset(
                        axes.normalX() * 2 + axes.tangentX() * shift,
                        0,
                        axes.normalZ() * 2 + axes.tangentZ() * shift);
                List<WallCandidate> candidates = new ArrayList<>();
                for (int offset : offsets) {
                    BlockPos column = center.offset(
                            axes.tangentX() * offset,
                            0,
                            axes.tangentZ() * offset);
                    BlockPos base = findWallBase(level, column);
                    if (base != null) {
                        candidates.add(new WallCandidate(base, offset));
                    }
                }
                if (candidates.size() < 3 || candidates.size() <= best.size()) {
                    continue;
                }
                WallCandidate seam = candidates.stream()
                        .min(Comparator.comparingInt(
                                candidate -> Math.abs(candidate.offset)))
                        .orElseThrow();
                best = candidates.stream()
                        .map(candidate -> new PlannedWallColumn(
                                candidate.base, candidate == seam))
                        .toList();
            }
        }
        wallPlan.addAll(best);
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
                BlockPos position = base.above(height);
                BlockState state = level.getBlockState(position);
                if ((!state.isAir() && !state.canBeReplaced())
                        || !level.getEntities(null, new AABB(position)).isEmpty()) {
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
                ? Blocks.ICE.defaultBlockState()
                : Blocks.PACKED_ICE.defaultBlockState();
        for (int height = 0; height < WALL_HEIGHT; height++) {
            BlockPos pos = column.base.above(height);
            BlockState existing = level.getBlockState(pos);
            if (!level.hasChunkAt(pos)
                    || (!existing.isAir() && !existing.canBeReplaced())
                    || !level.getEntities(null, new AABB(pos)).isEmpty()) {
                continue;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            placedWallBlocks.add(pos.immutable());
            if (column.weakCenter) {
                placedWallSeams.add(pos.immutable());
            }
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
        if (constructionHealCooldown > 0) {
            constructionHealCooldown--;
        }
        if (jukeSmashCooldown > 0) {
            jukeSmashCooldown--;
        }
    }

    private void startAction(int action) {
        activeAction = action;
        actionTicks = 0;
        architect.setMasterCombatVisual(action, 0);
    }

    private void finishAction() {
        if (activeAction == MasterArchitectCombatAction.THERMAL_SEVER) {
            severTargetId = null;
        }
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
    }

    private void beginThermalSever(ServerLevel level, ServerPlayer target) {
        severTargetId = target.getUUID();
        startAction(MasterArchitectCombatAction.THERMAL_SEVER);
        plantForThermalSever();
        PacketDistributor.sendToPlayer(target,
                new MasterArchitectSeverTelegraphPayload(
                        architect.getId(),
                        MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK));
    }

    private ServerPlayer resolveSeverTarget(
            ServerLevel level, ServerPlayer fallback) {
        if (severTargetId != null) {
            ServerPlayer locked = level.getServer().getPlayerList()
                    .getPlayer(severTargetId);
            if (locked != null && locked.isAlive()) {
                return locked;
            }
            return null;
        }
        return fallback;
    }

    private void plantForThermalSever() {
        architect.getNavigation().stop();
        Vec3 velocity = architect.getDeltaMovement();
        architect.setDeltaMovement(0.0D, velocity.y, 0.0D);
        architect.setSprinting(false);
    }

    private void signalCastSpacing(ServerLevel level, ServerPlayer target) {
        if (!isSpacingCast(activeAction)) {
            return;
        }
        architect.getHearthMasterArchitectId().ifPresent(hearthId ->
                HearthCombatRosterManager.signalMasterCast(
                        level, hearthId, architect, target, 2));
    }

    private static boolean isSpacingCast(int action) {
        return action == MasterArchitectCombatAction.CONTINUITY_FRACTURE
                || action == MasterArchitectCombatAction.THERMAL_SEVER
                || action == MasterArchitectCombatAction.LAST_WALL_CAST
                || action == MasterArchitectCombatAction.LAST_WALL_HEAL
                || action == MasterArchitectCombatAction.STORM_MAINTENANCE
                || action == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST;
    }

    private void syncThermalCharge() {
        architect.setMasterThermalCharge(
                MasterArchitectCombatPolicy.thermalCooldownCharge(
                        thermalCooldown, thermalCooldownDuration));
    }

    private Vec3 thermalHandFocus() {
        Vec3 look = architect.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        return architect.position()
                .add(0.0D, 1.28D, 0.0D)
                .add(horizontal.scale(0.72D))
                .add(right.scale(-0.34D));
    }

    private void cleanupExpiredWall(ServerLevel level) {
        boolean seamMissing = placedWallSeams.stream().anyMatch(pos ->
                level.hasChunkAt(pos) && !level.getBlockState(pos).is(Blocks.ICE));
        if (seamMissing) {
            healingInterrupted = true;
            level.playSound(
                    null,
                    architect.blockPosition(),
                    SoundEvents.GLASS_BREAK,
                    architect.getSoundSource(),
                    1.7F,
                    0.52F);
            removeWall(level);
            return;
        }
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
            if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE)) {
                level.removeBlock(pos, false);
            }
        }
        placedWallBlocks.clear();
        placedWallBlocks.addAll(unloaded);
        placedWallSeams.removeIf(pos -> !unloaded.contains(pos));
        wallPlan.clear();
        if (placedWallBlocks.isEmpty()) {
            wallExpiresAt = -1L;
        }
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    private void holdConstructionStagger(ServerLevel level) {
        if (activeAction == MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST) {
            constructionController.cancelCast(level);
        }
        activeAction = MasterArchitectCombatAction.IDLE;
        actionTicks = 0;
        severTargetId = null;
        architect.getNavigation().stop();
        Vec3 velocity = architect.getDeltaMovement();
        architect.setDeltaMovement(0.0D, velocity.y, 0.0D);
        architect.setMasterCombatVisual(
                MasterArchitectCombatAction.CONSTRUCTION_STAGGER,
                constructionController.staggerTicks());
    }

    private record PlannedWallColumn(BlockPos base, boolean weakCenter) {
    }

    private record WallCandidate(BlockPos base, int offset) {
    }

    record TetherDamageResult(float masterDamage, boolean suppressNormalHitSound) {
        private static TetherDamageResult passthrough(float damage) {
            return new TetherDamageResult(damage, false);
        }
    }

    private static final class PendingTetherPulse {
        private final UUID residentId;
        private int ageTicks;

        private PendingTetherPulse(UUID residentId) {
            this.residentId = residentId;
        }

        private UUID residentId() {
            return residentId;
        }

        private int ageTicks() {
            return ageTicks;
        }

        private void advance() {
            ageTicks++;
        }
    }
}
