package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.homo.MasterArchitectFloodPolicy;
import com.frozendawn.homo.MasterArchitectPhasePolicy;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.MasterArchitectFloodMotePayload;
import com.frozendawn.network.MasterArchitectFloodProgressPayload;
import com.frozendawn.network.MasterArchitectFloodStatePayload;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import com.frozendawn.world.ThaeIvenMindDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server authority for the Master Architect's Thae Iven finale. */
final class MasterArchitectFloodController {
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_flood_slow");
    private static final double MOTE_COLLECT_RANGE_SQUARED = 1.5D * 1.5D;
    private static final int FOLD_CAST_TICKS = 60;
    private static final int FOLD_PORTAL_SOUND_TICK = 42;
    private static final int FOLD_WAIL_INTERVAL_TICKS = 10;
    private static final int MEMORY_LOCK_FEEDBACK_INTERVAL_TICKS = 8;
    private static final int MIND_DEATH_EJECTION_TICKS =
            MasterArchitectFloodPolicy.MIND_DEATH_DISINTEGRATION_TICKS;
    private static final int MIND_DEATH_PORTAL_SOUND_TICK = 74;
    private static final int MIND_DEATH_PUSH_TICK = 88;
    private static final int IMMERSION_RAMP_TICKS = 60;
    private static final int RETURN_STAGGER_TICKS = 20;
    private static final int RETURN_DEATH_TICKS = 40;
    private static final int SESSION_RADIUS = 18;
    private static final double SURGE_HALF_WIDTH = 2.4D;
    private static final double SURGE_LENGTH = 18.0D;

    private final ArchitectEntity architect;
    private final MasterArchitectCombatController combatController;
    private final Map<UUID, PlayerFloodState> playerStates = new HashMap<>();
    private final Set<UUID> participantIds = new LinkedHashSet<>();

    private boolean active;
    private boolean preparing;
    private boolean folded;
    private boolean returning;
    private boolean restartRecoveryPending;
    private float floodStrength;
    private float immersion;
    private float copyHealthSnapshot;
    private int survivingResidents;
    private int maximumResidents;
    private int noPlayerTicks;
    private int foldCastTicks;
    private int mindDeathReturnTicks;
    private int retreatTicks;
    private int recoveryTicks;
    private int returnTicks;
    private int copyHealTicks;
    private int healingPressureTicks;
    private int healingTier = 1;
    private int exposureCycle;
    private int exposureTicks;
    private int coreRevealTicks;
    private int surgeTicks;
    private int surgeTelegraphTicks;
    private int ambientWailTicks;
    private boolean coreExposed;
    private boolean congregationApplied;
    private UUID mindCopyId;
    private UUID foldTargetId;
    private UUID mindDeathKillerId;
    private UUID returnKillerId;
    private UUID exposureKillerId;
    private Vec3 surgeOrigin = Vec3.ZERO;
    private Vec3 surgeDirection = Vec3.ZERO;
    private int forcedChunkX;
    private int forcedChunkZ;
    private boolean originChunkWasForced;
    private boolean originChunkForcedBySession;

    MasterArchitectFloodController(
            ArchitectEntity architect,
            MasterArchitectCombatController combatController) {
        this.architect = architect;
        this.combatController = combatController;
    }

    /** @return true while the Flood owns all Master behavior. */
    boolean tick(ServerLevel level, @Nullable ServerPlayer target) {
        if (restartRecoveryPending) {
            recoverAfterRestart(level);
        }
        if (returning) {
            tickReturnDeath(level);
            return true;
        }
        if (preparing) {
            tickFoldCast(level);
            return true;
        }
        if (retreatTicks > 0) {
            return false;
        }
        if (!active) {
            if (!MasterArchitectPhasePolicy.isAtFloodEntry(
                    architect.getHealth(), architect.getMaxHealth())) {
                return false;
            }
            if (target == null || !beginFoldCast(level, target)) {
                return false;
            }
            return true;
        }
        tickFolded(level);
        return true;
    }

    boolean tickFolded(ServerLevel level) {
        if (restartRecoveryPending) {
            recoverAfterRestart(level);
        }
        if (returning) {
            tickReturnDeath(level);
            return true;
        }
        if (preparing) {
            tickFoldCast(level);
            return true;
        }
        if (!active) {
            return false;
        }
        if (mindDeathReturnTicks > 0) {
            tickMindDeathEjection(level);
            return true;
        }
        if (!congregationApplied) {
            setCongregationState(level, true);
        }

        lockRealMaster();
        if (architect.tickCount % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    architect.getX(), architect.getY() + 1.05D, architect.getZ(),
                    8, 0.65D, 1.0D, 0.65D, 0.035D);
        }
        ServerLevel mindLevel = level.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY);
        ArchitectEntity copy = findMindCopy(mindLevel);
        if (mindLevel == null || copy == null) {
            FrozenDawn.LOGGER.warn(
                    "Master Architect {} lost its Thae Iven copy; recovering encounter",
                    shortId(architect.getUUID()));
            beginRetreat(level, "mind-copy-missing");
            return false;
        }

        tickThroneState(level, mindLevel, copy);
        if (mindDeathReturnTicks > 0 || !active) {
            return true;
        }

        immersion = Mth.clamp(
                immersion + 1.0F / IMMERSION_RAMP_TICKS, 0.0F, 1.0F);
        int presentPlayers = 0;
        for (UUID participantId : List.copyOf(participantIds)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(participantId);
            if (player == null || !player.isAlive()) {
                continue;
            }
            if (!ThaeIvenMindDimension.isMindLevel(player.level())) {
                participantFailed(level, player, "left-mind-dimension");
                if (!active) {
                    return false;
                }
                continue;
            }
            if (player.getY() < ThaeIvenMindDimension.FAILURE_Y
                    || player.distanceToSqr(copy) > 28.0D * 28.0D) {
                participantFailed(level, player, "fled-the-flood");
                if (!active) {
                    return false;
                }
                continue;
            }
            presentPlayers++;
            tickPlayer(mindLevel, player, copy);
        }

        if (presentPlayers == 0) {
            if (++noPlayerTicks >= MasterArchitectFloodPolicy.RETREAT_GRACE_TICKS) {
                beginRetreat(level, "no-participants");
                return false;
            }
        } else {
            noPlayerTicks = 0;
        }

        if (copy.tickCount % 4 == 0) {
            mindLevel.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    copy.getX(), copy.getY() + 1.1D, copy.getZ(),
                    5, 2.2D, 1.35D, 2.2D, 0.035D);
            mindLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    copy.getX(), copy.getY() + 1.0D, copy.getZ(),
                    2, 1.55D, 1.0D, 1.55D, 0.018D);
        }
        return true;
    }

    void tickRearmCountdown(ServerLevel level) {
        if (retreatTicks > 0 && !active && !preparing && !folded && !returning) {
            tickRetreat(level);
        }
    }

    float prepareMindCopyDamage(
            ServerLevel originLevel,
            ArchitectEntity copy,
            DamageSource source,
            float incomingDamage) {
        if (!active || mindCopyId == null || !mindCopyId.equals(copy.getUUID())
                || incomingDamage <= 0.0F) {
            return Math.max(0.0F, incomingDamage);
        }
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player
                ? player : null;
        PlayerFloodState state = attacker == null
                ? null : playerStates.get(attacker.getUUID());
        int stacks = state == null ? 0 : state.stacks;
        if (!coreExposed && stacks >= MasterArchitectFloodPolicy.IVEN_STACK_CAP) {
            beginCoreExposure((ServerLevel) copy.level(), copy, attacker);
        }

        float multiplier = MasterArchitectFloodPolicy.stackDamageMultiplier(stacks);
        if (coreExposed) {
            multiplier *= 2.0F;
        }
        if (state != null && state.stacks > 0) {
            state.stacks--;
            state.decayTicks = state.stacks > 0 ? stackDecayTicks(originLevel) : 0;
            sendProgress(attacker, state);
        }

        float adjusted = incomingDamage * multiplier;
        // Never leave a hidden final hit point behind an empty boss bar. A
        // lethal hit in Thae Iven commits the same canonical death ritual as
        // the third severance and shuts the throne's healing down immediately.
        return Math.min(adjusted, Math.max(0.0F, copy.getHealth()));
    }

    private void tickThroneState(
            ServerLevel originLevel, ServerLevel mindLevel, ArchitectEntity copy) {
        copyHealthSnapshot = copy.getHealth();
        if (copy.getHealth() >= copy.getMaxHealth()
                * MasterArchitectFloodPolicy.THRONE_EJECTION_HEALTH_FRACTION
                - 0.001F) {
            ejectHealedThrone(originLevel, mindLevel, copy);
            return;
        }
        if (coreExposed) {
            copy.setMasterCombatVisual(
                    MasterArchitectCombatAction.MIND_CORE_EXPOSED,
                    MasterArchitectFloodPolicy.CORE_EXPOSURE_TICKS - exposureTicks);
            if (copy.tickCount % 2 == 0) {
                mindLevel.sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        copy.getX(), copy.getY() + 1.15D, copy.getZ(),
                        14, 0.48D, 0.75D, 0.48D, 0.08D);
                mindLevel.sendParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        copy.getX(), copy.getY() + 1.0D, copy.getZ(),
                        8, 0.36D, 0.62D, 0.36D, 0.045D);
            }
            if (--exposureTicks <= 0) {
                finishCoreExposure(originLevel, mindLevel, copy);
                if (!active || mindDeathReturnTicks > 0) {
                    return;
                }
            }
        } else {
            healingPressureTicks++;
            updateHealingTier(originLevel, mindLevel, copy);
        }

        if (!coreExposed && --copyHealTicks <= 0) {
            copyHealTicks = MasterArchitectFloodPolicy.COPY_HEAL_INTERVAL_TICKS;
            String preset = presetName(originLevel);
            float rate = MasterArchitectFloodPolicy.copyHealRate(preset);
            float tierMultiplier = MasterArchitectFloodPolicy.healingTierMultiplier(
                    preset,
                    healingTier,
                    FrozenDawnConfig.MIND_HEAL_TIER_TWO_MULTIPLIER.get().floatValue(),
                    FrozenDawnConfig.MIND_HEAL_TIER_THREE_MULTIPLIER.get().floatValue(),
                    FrozenDawnConfig.BRUTAL_MIND_HEAL_TIER_THREE_MULTIPLIER.get()
                            .floatValue());
            float amount = copy.getMaxHealth() * rate * tierMultiplier * floodStrength;
            float ejectionHealth = copy.getMaxHealth()
                    * MasterArchitectFloodPolicy.THRONE_EJECTION_HEALTH_FRACTION;
            amount = Math.min(amount, Math.max(0.0F, ejectionHealth - copy.getHealth()));
            if (amount > 0.0F && copy.getHealth() < copy.getMaxHealth()) {
                copy.heal(amount);
                copyHealthSnapshot = copy.getHealth();
                emitThroneHealing(mindLevel, copy, amount);
            }
            if (copy.getHealth() >= ejectionHealth - 0.001F) {
                ejectHealedThrone(originLevel, mindLevel, copy);
                return;
            }
        }

        if (!coreExposed) {
            boolean coreReady = playerStates.values().stream()
                    .anyMatch(state -> state.stacks
                            >= MasterArchitectFloodPolicy.IVEN_STACK_CAP);
            copy.setMasterCombatVisual(
                    coreReady && coreRevealTicks > 0
                            ? MasterArchitectCombatAction.MIND_CORE_REVEAL
                            : coreReady
                            ? MasterArchitectCombatAction.MIND_CORE_READY
                            : MasterArchitectCombatAction.FLOOD_CHANNEL,
                    coreRevealTicks);
            if (coreReady && coreRevealTicks > 0) {
                coreRevealTicks--;
            }
            if (coreReady && copy.tickCount % 3 == 0) {
                mindLevel.sendParticles(
                        ParticleTypes.END_ROD,
                        copy.getX(), copy.getY() + 1.15D, copy.getZ(),
                        2, 0.19D, 0.24D, 0.19D, 0.025D);
                mindLevel.sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        copy.getX(), copy.getY() + 1.15D, copy.getZ(),
                        3, 0.22D, 0.28D, 0.22D, 0.055D);
            }
        }

        if (--ambientWailTicks <= 0) {
            ambientWailTicks = 140 + architect.nextRandomInt(141);
            copy.playSound(
                    ModSounds.MASTER_ARCHITECT_TETHER_WAIL.get(),
                    3.4F + floodStrength * 1.4F,
                    0.42F + architect.nextRandomFloat() * 0.12F);
        }

        tickSurge(mindLevel, copy);
    }

    private void beginCoreExposure(
            ServerLevel mindLevel,
            ArchitectEntity copy,
            @Nullable ServerPlayer attacker) {
        coreExposed = true;
        coreRevealTicks = 0;
        exposureTicks = MasterArchitectFloodPolicy.CORE_EXPOSURE_TICKS;
        exposureKillerId = attacker == null ? null : attacker.getUUID();
        copy.setMasterCombatVisual(MasterArchitectCombatAction.MIND_CORE_EXPOSED, 0);
        copy.playSound(ModSounds.MASTER_ARCHITECT_MIND_DEATH_WAIL.get(), 4.6F, 0.72F);
        mindLevel.sendParticles(
                ParticleTypes.FLASH,
                copy.getX(), copy.getY() + 1.1D, copy.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        mindLevel.sendParticles(
                ParticleTypes.SCULK_SOUL,
                copy.getX(), copy.getY() + 1.1D, copy.getZ(),
                85, 1.0D, 1.25D, 1.0D, 0.16D);
        if (architect.level() instanceof ServerLevel originLevel) {
            HearthMasterArchitectWeatherManager.broadcastAuraEvent(
                    originLevel,
                    MasterArchitectAuraEventPayload.EXPOSURE_STUTTER,
                    architect.blockPosition().above(72),
                    architect.blockPosition(),
                    1.15F);
        }
        broadcastProgress();
        FrozenDawn.LOGGER.info(
                "Master Architect {} exposed its Thae Iven core ({}/{})",
                shortId(architect.getUUID()), exposureCycle + 1,
                MasterArchitectFloodPolicy.REQUIRED_EXPOSURES);
    }

    private void finishCoreExposure(
            ServerLevel originLevel, ServerLevel mindLevel, ArchitectEntity copy) {
        coreExposed = false;
        exposureTicks = 0;
        coreRevealTicks = 0;
        exposureCycle++;
        reduceHealingTierAfterExposure(originLevel);
        copy.setMasterCombatVisual(MasterArchitectCombatAction.FLOOD_CHANNEL, 0);
        if (exposureCycle >= MasterArchitectFloodPolicy.REQUIRED_EXPOSURES) {
            ServerPlayer killer = exposureKillerId == null
                    ? null : originLevel.getServer().getPlayerList()
                            .getPlayer(exposureKillerId);
            onMindCopyDefeated(originLevel, copy, killer);
            return;
        }

        for (UUID participantId : participantIds) {
            ServerPlayer player = originLevel.getServer().getPlayerList()
                    .getPlayer(participantId);
            PlayerFloodState state = playerStates.get(participantId);
            if (player == null || state == null) {
                continue;
            }
            state.stacks = 0;
            state.decayTicks = 0;
            resetMotes(mindLevel, player, copy, state);
            sendProgress(player, state);
        }
        surgeTicks = nextSurgeDelay();
        mindLevel.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                copy.getX(), copy.getY() + 1.0D, copy.getZ(),
                65, 1.5D, 1.15D, 1.5D, 0.12D);
        FrozenDawn.LOGGER.info(
                "Master Architect {} survived throne severance {}; flood intensity now {}, healing tier {}",
                shortId(architect.getUUID()), exposureCycle,
                String.format("%.2f", MasterArchitectFloodPolicy
                        .exposureIntensity(exposureCycle)), healingTier);
    }

    private void updateHealingTier(
            ServerLevel originLevel, ServerLevel mindLevel, ArchitectEntity copy) {
        int nextTier = healingTierFor(originLevel, healingPressureTicks);
        if (nextTier <= healingTier) {
            return;
        }
        healingTier = nextTier;
        broadcastProgress();
        copy.playSound(
                ModSounds.MASTER_ARCHITECT_MIND_HEAL_ESCALATE.get(),
                healingTier >= 3 ? 7.5F : 6.2F,
                healingTier >= 3 ? 0.56F : 0.72F);
        mindLevel.playSound(
                null,
                copy.blockPosition(),
                SoundEvents.WARDEN_ROAR,
                SoundSource.MASTER,
                healingTier >= 3 ? 5.5F : 4.0F,
                healingTier >= 3 ? 0.52F : 0.68F);
        mindLevel.sendParticles(
                ParticleTypes.SCULK_SOUL,
                copy.getX(), copy.getY() + 1.05D, copy.getZ(),
                healingTier >= 3 ? 160 : 95,
                healingTier >= 3 ? 4.4D : 3.2D,
                1.55D,
                healingTier >= 3 ? 4.4D : 3.2D,
                healingTier >= 3 ? 0.19D : 0.12D);
        FrozenDawn.LOGGER.info(
                "Master Architect {} Thae Iven healing escalated to tier {} after {} ticks",
                shortId(architect.getUUID()), healingTier, healingPressureTicks);
    }

    private void reduceHealingTierAfterExposure(ServerLevel level) {
        if (healingTier >= 3) {
            healingPressureTicks = healingTierTwoTicks(level);
        } else if (healingTier == 2) {
            healingPressureTicks = 0;
        }
        healingTier = healingTierFor(level, healingPressureTicks);
        broadcastProgress();
    }

    private int healingTierFor(ServerLevel level, int pressureTicks) {
        return MasterArchitectFloodPolicy.healingTier(
                presetName(level),
                pressureTicks,
                FrozenDawnConfig.MIND_HEAL_TIER_TWO_SECONDS.get() * 20,
                FrozenDawnConfig.MIND_HEAL_TIER_THREE_SECONDS.get() * 20,
                FrozenDawnConfig.BRUTAL_MIND_HEAL_TIER_TWO_SECONDS.get() * 20,
                FrozenDawnConfig.BRUTAL_MIND_HEAL_TIER_THREE_SECONDS.get() * 20);
    }

    private int healingTierTwoTicks(ServerLevel level) {
        return ("brutal".equalsIgnoreCase(presetName(level))
                ? FrozenDawnConfig.BRUTAL_MIND_HEAL_TIER_TWO_SECONDS.get()
                : FrozenDawnConfig.MIND_HEAL_TIER_TWO_SECONDS.get()) * 20;
    }

    private void ejectHealedThrone(
            ServerLevel originLevel, ServerLevel mindLevel, ArchitectEntity copy) {
        copyHealthSnapshot = copy.getHealth();
        mindLevel.playSound(
                null,
                copy.blockPosition(),
                SoundEvents.PORTAL_TRAVEL,
                SoundSource.MASTER,
                3.3F,
                0.62F);
        FrozenDawn.LOGGER.info(
                "Master Architect {} completed Thae Iven restoration at {}/{} health; ejecting participants from healing tier {}",
                shortId(architect.getUUID()),
                String.format("%.1f", copy.getHealth()),
                String.format("%.1f", copy.getMaxHealth()),
                healingTier);
        beginRetreat(originLevel, "throne-restored-to-half");
    }

    private void emitThroneHealing(
            ServerLevel mindLevel, ArchitectEntity copy, float amount) {
        Vec3 center = copy.position().add(0.0D, 1.05D, 0.0D);
        for (int index = 0; index < 16; index++) {
            double angle = index * Math.PI * 2.0D / 16.0D
                    + copy.tickCount * 0.08D;
            Vec3 origin = center.add(
                    Math.cos(angle) * 3.2D,
                    -0.5D + (index % 5) * 0.35D,
                    Math.sin(angle) * 3.2D);
            Vec3 velocity = center.subtract(origin).normalize().scale(0.11D);
            mindLevel.sendParticles(
                    ParticleTypes.SOUL,
                    origin.x, origin.y, origin.z,
                    0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        copy.playSound(ModSounds.MASTER_ARCHITECT_FLOOD_MOTE.get(), 1.7F, 0.54F);
        FrozenDawn.LOGGER.debug(
                "Thae Iven healed copy {} by {} to {}/{}",
                shortId(copy.getUUID()), String.format("%.1f", amount),
                String.format("%.1f", copy.getHealth()),
                String.format("%.1f", copy.getMaxHealth()));
    }

    private void tickSurge(ServerLevel mindLevel, ArchitectEntity copy) {
        if (coreExposed || mindDeathReturnTicks > 0) {
            return;
        }
        if (surgeTelegraphTicks > 0) {
            emitSurgeTelegraph(mindLevel);
            if (--surgeTelegraphTicks == 0) {
                resolveSurge(mindLevel, copy);
                surgeTicks = nextSurgeDelay();
            }
            return;
        }
        if (--surgeTicks > 0) {
            return;
        }

        ServerPlayer target = participantIds.stream()
                .map(id -> mindLevel.getServer().getPlayerList().getPlayer(id))
                .filter(player -> player != null && player.level() == mindLevel
                        && player.isAlive())
                .findFirst().orElse(null);
        if (target == null) {
            surgeTicks = 40;
            return;
        }
        surgeOrigin = copy.position().add(0.0D, 0.6D, 0.0D);
        Vec3 towardTarget = target.position().subtract(copy.position())
                .multiply(1.0D, 0.0D, 1.0D);
        surgeDirection = towardTarget.horizontalDistanceSqr() < 0.001D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : towardTarget.normalize();
        surgeTelegraphTicks = MasterArchitectFloodPolicy.SURGE_TELEGRAPH_TICKS;
        mindLevel.playSound(
                null,
                BlockPos.containing(surgeOrigin),
                SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.HOSTILE,
                2.8F,
                0.64F);
    }

    private void emitSurgeTelegraph(ServerLevel level) {
        double progress = 1.0D - surgeTelegraphTicks
                / (double) MasterArchitectFloodPolicy.SURGE_TELEGRAPH_TICKS;
        for (int step = 0; step < 18; step++) {
            double distance = 1.5D + step * (SURGE_LENGTH - 1.5D) / 17.0D;
            Vec3 point = surgeOrigin.add(surgeDirection.scale(distance));
            level.sendParticles(
                    step % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL,
                    point.x, point.y + Math.sin(progress * Math.PI + step) * 0.35D,
                    point.z,
                    1, 0.22D, 0.32D, 0.22D, 0.018D + progress * 0.035D);
        }
    }

    private void resolveSurge(ServerLevel level, ArchitectEntity copy) {
        level.playSound(
                null,
                BlockPos.containing(surgeOrigin),
                SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE,
                3.2F,
                0.78F);
        for (UUID participantId : participantIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(participantId);
            if (player == null || player.level() != level || !player.isAlive()
                    || ThaeIvenMindDimension.isInsideSanctuary(
                            player, architect.getUUID())
                    || !isInSurgeLane(player.position()) || hasSurgeCover(level, player)) {
                continue;
            }
            player.hurt(createFloodDamageSource(level), 1.5F + exposureCycle * 0.6F);
            player.setDeltaMovement(player.getDeltaMovement().add(
                    surgeDirection.x * 0.9D, 0.18D, surgeDirection.z * 0.9D));
            player.hurtMarked = true;
            PlayerFloodState state = playerStates.get(participantId);
            if (state != null && state.stacks > 0) {
                state.stacks--;
                state.decayTicks = state.stacks > 0 ? stackDecayTicks(level) : 0;
                sendProgress(player, state);
            }
        }
    }

    private boolean isInSurgeLane(Vec3 position) {
        Vec3 relative = position.subtract(surgeOrigin).multiply(1.0D, 0.0D, 1.0D);
        double forward = relative.dot(surgeDirection);
        if (forward < 0.0D || forward > SURGE_LENGTH) {
            return false;
        }
        Vec3 lateral = relative.subtract(surgeDirection.scale(forward));
        return lateral.horizontalDistance() <= SURGE_HALF_WIDTH;
    }

    private boolean hasSurgeCover(ServerLevel level, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = surgeOrigin.add(0.0D, 0.8D, 0.0D);
        return level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player)).getType() == HitResult.Type.BLOCK;
    }

    private int nextSurgeDelay() {
        return MasterArchitectFloodPolicy.surgeDelayTicks(
                exposureCycle, architect.nextRandomInt(101));
    }

    private int stackDecayTicks(ServerLevel level) {
        return MasterArchitectFloodPolicy.stackDecayTicks(
                "brutal".equalsIgnoreCase(presetName(level)));
    }

    private static String presetName(ServerLevel level) {
        return ApocalypseState.get(level.getServer()).getPresetName();
    }

    boolean isMindSessionActive() {
        return preparing || active || folded || returning
                || mindDeathReturnTicks > 0;
    }

    boolean isActive() {
        return active;
    }

    boolean isRetreating() {
        return retreatTicks > 0;
    }

    void onCombatLost(ServerLevel level) {
        if (!isMindSessionActive()) {
            clearAllPlayers(level, MasterArchitectFloodStatePayload.CLEAR, false);
        }
    }

    void onMindCopyHurt(
            ServerLevel originLevel,
            ArchitectEntity copy,
            DamageSource source,
            float amount) {
        if (!active || mindCopyId == null || !mindCopyId.equals(copy.getUUID())) {
            return;
        }
        copy.setMasterCombatVisual(MasterArchitectCombatAction.MIND_HIT_STAGGER, 7);
        copy.playSound(ModSounds.MASTER_ARCHITECT_FLOOD_HIT.get(), 3.1F,
                0.74F + copy.getRandom().nextFloat() * 0.08F);

        Vec3 sourcePosition = source.getSourcePosition();
        if (sourcePosition == null && source.getEntity() != null) {
            sourcePosition = source.getEntity().position().add(0.0D, 1.0D, 0.0D);
        }
        if (sourcePosition == null) {
            sourcePosition = copy.position().add(0.0D, 1.0D, 3.0D);
        }
        Vec3 destination = copy.position().add(0.0D, 1.1D, 0.0D);
        ServerLevel copyLevel = (ServerLevel) copy.level();
        for (int step = 0; step <= 14; step++) {
            double progress = step / 14.0D;
            Vec3 point = sourcePosition.lerp(destination, progress);
            copyLevel.sendParticles(
                    step % 4 == 0 ? ParticleTypes.OMINOUS_SPAWNING : ParticleTypes.SOUL,
                    point.x, point.y + Math.sin(progress * Math.PI) * 0.55D, point.z,
                    2, 0.08D, 0.08D, 0.08D, 0.01D);
        }
        copyLevel.sendParticles(
                ParticleTypes.FLASH,
                destination.x, destination.y, destination.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    void onMindCopyDefeated(
            ServerLevel originLevel,
            ArchitectEntity copy,
            @Nullable ServerPlayer killer) {
        if (!active || mindDeathReturnTicks > 0
                || mindCopyId == null || !mindCopyId.equals(copy.getUUID())) {
            return;
        }
        mindDeathKillerId = killer == null ? null : killer.getUUID();
        if (killer != null) {
            architect.getHearthMasterArchitectId().ifPresent(hearthId -> {
                WorldTickHandler.grantAdvancement(killer, "decoherence");
                ReturnedHearthSavedData.get(originLevel.getServer())
                        .markDecoherenceGranted(hearthId);
            });
        }
        mindDeathReturnTicks = MIND_DEATH_EJECTION_TICKS;
        immersion = 1.0F;
        copyHealthSnapshot = 0.0F;
        copyHealTicks = Integer.MAX_VALUE;
        healingPressureTicks = 0;
        coreExposed = false;
        exposureTicks = 0;
        surgeTicks = Integer.MAX_VALUE;
        surgeTelegraphTicks = 0;
        copy.setInvulnerable(true);
        copy.setNoAi(true);
        copy.getNavigation().stop();
        copy.setTarget(null);
        copy.setDeltaMovement(Vec3.ZERO);
        copy.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        copy.setMasterBossBarEmptyOverride(true);
        copy.setMasterCombatVisual(MasterArchitectCombatAction.MIND_RETURN_STAGGER, 0);
        copy.playSound(ModSounds.MASTER_ARCHITECT_MIND_DEATH_WAIL.get(), 6.5F, 0.52F);
        for (UUID participantId : participantIds) {
            ServerPlayer player = originLevel.getServer().getPlayerList()
                    .getPlayer(participantId);
            PlayerFloodState state = playerStates.get(participantId);
            if (player == null || state == null) {
                continue;
            }
            clearPlayerEffects(player);
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodMotePayload(
                            MasterArchitectFloodMotePayload.CLEAR,
                            -1, -1, 0.0D, 0.0D, 0.0D));
        }
        broadcastProgress();
        FrozenDawn.LOGGER.info(
                "Master Architect {} mind-copy entered the ejection death stagger",
                shortId(architect.getUUID()));
    }

    void participantFailed(ServerLevel originLevel, ServerPlayer player, String reason) {
        if (!participantIds.contains(player.getUUID())) {
            return;
        }
        clearPlayerEffects(player);
        PacketDistributor.sendToPlayer(player,
                new MasterArchitectFloodStatePayload(
                        -1, MasterArchitectFloodStatePayload.CLEAR,
                        0.0F, 0.0F, 0.0F));
        ThaeIvenMindDimension.returnToOrigin(player, architect.getUUID());
        participantIds.remove(player.getUUID());
        playerStates.remove(player.getUUID());
        FrozenDawn.LOGGER.info(
                "Master Architect {} Flood participant {} withdrew ({})",
                shortId(architect.getUUID()), player.getGameProfile().getName(), reason);
        if (participantIds.isEmpty()) {
            beginRetreat(originLevel, reason);
        }
    }

    void onDeath(ServerLevel level, @Nullable ServerPlayer killer) {
        clearAllPlayers(level, MasterArchitectFloodStatePayload.CLEAR, true);
        discardMindCopy(level);
        setCongregationState(level, false);
        releaseOriginChunk(level);
        architect.setInvulnerable(false);
        architect.setNoAi(false);
        active = false;
        preparing = false;
        folded = false;
        returning = false;
        immersion = 0.0F;
        retreatTicks = 0;
        recoveryTicks = 0;
        returnTicks = 0;
        foldCastTicks = 0;
        mindDeathReturnTicks = 0;
        foldTargetId = null;
        mindDeathKillerId = null;
        exposureKillerId = null;
        copyHealTicks = 0;
        healingPressureTicks = 0;
        healingTier = 1;
        exposureCycle = 0;
        exposureTicks = 0;
        coreRevealTicks = 0;
        surgeTicks = 0;
        surgeTelegraphTicks = 0;
        ambientWailTicks = 0;
        coreExposed = false;
    }

    void addSaveData(CompoundTag tag) {
        tag.putBoolean("MasterFloodActive", active);
        tag.putBoolean("MasterFloodPreparing", preparing);
        tag.putBoolean("MasterFloodFolded", folded);
        tag.putBoolean("MasterFloodReturning", returning);
        tag.putFloat("MasterFloodStrength", floodStrength);
        tag.putFloat("MasterFloodImmersion", immersion);
        tag.putFloat("MasterFloodCopyHealth", copyHealthSnapshot);
        tag.putInt("MasterFloodSurvivingResidents", survivingResidents);
        tag.putInt("MasterFloodMaximumResidents", maximumResidents);
        tag.putInt("MasterFloodRetreatTicks", retreatTicks);
        tag.putInt("MasterFloodRecoveryTicks", recoveryTicks);
        tag.putInt("MasterFloodReturnTicks", returnTicks);
        tag.putInt("MasterFloodFoldCastTicks", foldCastTicks);
        tag.putInt("MasterFloodMindDeathReturnTicks", mindDeathReturnTicks);
        tag.putInt("MasterFloodCopyHealTicks", copyHealTicks);
        tag.putInt("MasterFloodHealingPressureTicks", healingPressureTicks);
        tag.putInt("MasterFloodHealingTier", healingTier);
        tag.putInt("MasterFloodExposureCycle", exposureCycle);
        tag.putInt("MasterFloodExposureTicks", exposureTicks);
        tag.putInt("MasterFloodCoreRevealTicks", coreRevealTicks);
        tag.putInt("MasterFloodSurgeTicks", surgeTicks);
        tag.putInt("MasterFloodSurgeTelegraphTicks", surgeTelegraphTicks);
        tag.putInt("MasterFloodAmbientWailTicks", ambientWailTicks);
        tag.putBoolean("MasterFloodCoreExposed", coreExposed);
        tag.putInt("MasterFloodForcedChunkX", forcedChunkX);
        tag.putInt("MasterFloodForcedChunkZ", forcedChunkZ);
        tag.putBoolean("MasterFloodOriginChunkWasForced", originChunkWasForced);
        tag.putBoolean("MasterFloodOriginChunkForcedBySession", originChunkForcedBySession);
        if (mindCopyId != null) {
            tag.putUUID("MasterFloodMindCopyId", mindCopyId);
        }
        if (returnKillerId != null) {
            tag.putUUID("MasterFloodReturnKillerId", returnKillerId);
        }
        if (foldTargetId != null) {
            tag.putUUID("MasterFloodFoldTargetId", foldTargetId);
        }
        if (mindDeathKillerId != null) {
            tag.putUUID("MasterFloodMindDeathKillerId", mindDeathKillerId);
        }
        if (exposureKillerId != null) {
            tag.putUUID("MasterFloodExposureKillerId", exposureKillerId);
        }
        ListTag participants = new ListTag();
        for (UUID participantId : participantIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", participantId);
            participants.add(entry);
        }
        tag.put("MasterFloodParticipants", participants);
    }

    void readSaveData(CompoundTag tag) {
        active = tag.getBoolean("MasterFloodActive");
        preparing = tag.getBoolean("MasterFloodPreparing");
        folded = tag.getBoolean("MasterFloodFolded");
        returning = tag.getBoolean("MasterFloodReturning");
        floodStrength = Mth.clamp(tag.getFloat("MasterFloodStrength"), 0.0F, 1.0F);
        immersion = Mth.clamp(tag.getFloat("MasterFloodImmersion"), 0.0F, 1.0F);
        copyHealthSnapshot = Math.max(0.0F, tag.getFloat("MasterFloodCopyHealth"));
        survivingResidents = Math.max(0, tag.getInt("MasterFloodSurvivingResidents"));
        maximumResidents = Math.max(0, tag.getInt("MasterFloodMaximumResidents"));
        retreatTicks = Math.max(0, tag.getInt("MasterFloodRetreatTicks"));
        recoveryTicks = Math.max(0, tag.getInt("MasterFloodRecoveryTicks"));
        returnTicks = Math.max(0, tag.getInt("MasterFloodReturnTicks"));
        foldCastTicks = Math.max(0, tag.getInt("MasterFloodFoldCastTicks"));
        mindDeathReturnTicks = Math.max(
                0, tag.getInt("MasterFloodMindDeathReturnTicks"));
        copyHealTicks = Math.max(0, tag.getInt("MasterFloodCopyHealTicks"));
        healingPressureTicks = Math.max(
                0, tag.getInt("MasterFloodHealingPressureTicks"));
        healingTier = Mth.clamp(tag.contains("MasterFloodHealingTier")
                ? tag.getInt("MasterFloodHealingTier") : 1, 1, 3);
        exposureCycle = Mth.clamp(
                tag.getInt("MasterFloodExposureCycle"),
                0,
                MasterArchitectFloodPolicy.REQUIRED_EXPOSURES);
        exposureTicks = Math.max(0, tag.getInt("MasterFloodExposureTicks"));
        coreRevealTicks = Mth.clamp(
                tag.getInt("MasterFloodCoreRevealTicks"),
                0,
                MasterArchitectFloodPolicy.CORE_REVEAL_TICKS);
        surgeTicks = Math.max(0, tag.getInt("MasterFloodSurgeTicks"));
        surgeTelegraphTicks = Math.max(
                0, tag.getInt("MasterFloodSurgeTelegraphTicks"));
        ambientWailTicks = Math.max(0, tag.getInt("MasterFloodAmbientWailTicks"));
        coreExposed = tag.getBoolean("MasterFloodCoreExposed");
        forcedChunkX = tag.getInt("MasterFloodForcedChunkX");
        forcedChunkZ = tag.getInt("MasterFloodForcedChunkZ");
        originChunkWasForced = tag.getBoolean("MasterFloodOriginChunkWasForced");
        originChunkForcedBySession = tag.getBoolean("MasterFloodOriginChunkForcedBySession");
        mindCopyId = tag.hasUUID("MasterFloodMindCopyId")
                ? tag.getUUID("MasterFloodMindCopyId") : null;
        returnKillerId = tag.hasUUID("MasterFloodReturnKillerId")
                ? tag.getUUID("MasterFloodReturnKillerId") : null;
        foldTargetId = tag.hasUUID("MasterFloodFoldTargetId")
                ? tag.getUUID("MasterFloodFoldTargetId") : null;
        mindDeathKillerId = tag.hasUUID("MasterFloodMindDeathKillerId")
                ? tag.getUUID("MasterFloodMindDeathKillerId") : null;
        exposureKillerId = tag.hasUUID("MasterFloodExposureKillerId")
                ? tag.getUUID("MasterFloodExposureKillerId") : null;
        participantIds.clear();
        ListTag participants = tag.getList("MasterFloodParticipants", Tag.TAG_COMPOUND);
        for (int index = 0; index < participants.size(); index++) {
            CompoundTag entry = participants.getCompound(index);
            if (entry.hasUUID("Player")) {
                participantIds.add(entry.getUUID("Player"));
            }
        }
        restartRecoveryPending = preparing || active || folded || returning
                || mindDeathReturnTicks > 0;
        congregationApplied = false;
        noPlayerTicks = 0;
        playerStates.clear();
    }

    private boolean beginFoldCast(ServerLevel level, ServerPlayer primaryTarget) {
        if (level.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY) == null) {
            FrozenDawn.LOGGER.error(
                    "Cannot open Master Architect Flood: frozendawn:thae_iven is unavailable");
            return false;
        }
        preparing = true;
        active = false;
        folded = false;
        foldCastTicks = 0;
        foldTargetId = primaryTarget.getUUID();
        mindDeathReturnTicks = 0;
        mindDeathKillerId = null;
        forceOriginChunk(level);
        lockRealMaster();
        architect.setMasterCombatVisual(MasterArchitectCombatAction.FLOOD_FOLD_CAST, 0);
        architect.playSound(ModSounds.MASTER_ARCHITECT_FLOOD_BEGIN.get(), 3.2F, 0.58F);
        HearthMasterArchitectWeatherManager.broadcastAuraEvent(
                level,
                MasterArchitectAuraEventPayload.FOLD_CONTRACTION,
                architect.blockPosition().above(88),
                architect.blockPosition(),
                1.5F);
        emitFoldEntryBurst(level);
        FrozenDawn.LOGGER.info(
                "Master Architect {} began the Thae Iven fold cast at {}/{} health",
                shortId(architect.getUUID()),
                String.format("%.1f", architect.getHealth()),
                String.format("%.1f", architect.getMaxHealth()));
        return true;
    }

    private void tickFoldCast(ServerLevel level) {
        ServerPlayer primaryTarget = foldTargetId == null
                ? null : level.getServer().getPlayerList().getPlayer(foldTargetId);
        if (primaryTarget == null || !primaryTarget.isAlive()
                || primaryTarget.level() != level) {
            beginRetreat(level, "fold-target-lost");
            return;
        }

        lockRealMaster();
        architect.setMasterCombatVisual(
                MasterArchitectCombatAction.FLOOD_FOLD_CAST, foldCastTicks);
        emitFoldDrawParticles(level);
        if (foldCastTicks % FOLD_WAIL_INTERVAL_TICKS == 0) {
            architect.playSound(
                    ModSounds.MASTER_ARCHITECT_TETHER_WAIL.get(),
                    3.6F,
                    0.48F + foldCastTicks / (float) FOLD_CAST_TICKS * 0.12F);
        }
        if (foldCastTicks == FOLD_PORTAL_SOUND_TICK) {
            level.playSound(
                    null,
                    architect.blockPosition(),
                    SoundEvents.PORTAL_TRAVEL,
                    SoundSource.MASTER,
                    2.7F,
                    0.78F);
        }
        if (foldCastTicks >= FOLD_CAST_TICKS - 10) {
            emitFoldStrike(level, primaryTarget);
        }

        foldCastTicks++;
        if (foldCastTicks < FOLD_CAST_TICKS) {
            return;
        }
        if (!openMindSession(level, primaryTarget)) {
            beginRetreat(level, "mind-session-open-failed");
        }
    }

    private boolean openMindSession(ServerLevel level, ServerPlayer primaryTarget) {
        ServerLevel mindLevel = level.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY);
        if (mindLevel == null) {
            FrozenDawn.LOGGER.error(
                    "Cannot open Master Architect Flood: frozendawn:thae_iven is unavailable");
            return false;
        }

        UUID hearthId = architect.getHearthMasterArchitectId().orElse(null);
        HearthCombatRosterManager.FloodPopulation population = hearthId == null
                ? new HearthCombatRosterManager.FloodPopulation(0, 0, 0.0F)
                : HearthCombatRosterManager.floodPopulation(level, hearthId);
        survivingResidents = population.survivingResidents();
        maximumResidents = population.maximumResidents();
        floodStrength = population.strength();
        immersion = 0.0F;
        copyHealTicks = MasterArchitectFloodPolicy.COPY_HEAL_INTERVAL_TICKS;
        healingPressureTicks = 0;
        healingTier = 1;
        exposureCycle = 0;
        exposureTicks = 0;
        coreRevealTicks = 0;
        coreExposed = false;
        exposureKillerId = null;
        surgeTicks = nextSurgeDelay();
        surgeTelegraphTicks = 0;
        ambientWailTicks = 100 + architect.nextRandomInt(121);
        surgeOrigin = Vec3.ZERO;
        surgeDirection = Vec3.ZERO;
        noPlayerTicks = 0;
        returnTicks = 0;
        returnKillerId = null;
        participantIds.clear();
        playerStates.clear();

        ThaeIvenMindDimension.ensureArena(mindLevel, architect.getUUID());
        BlockPos arenaCenter = ThaeIvenMindDimension.arenaCenter(architect.getUUID());
        ArchitectEntity copy = ModEntities.ARCHITECT.get().create(mindLevel);
        if (copy == null) {
            return false;
        }
        Vec3 masterPosition = ThaeIvenMindDimension.masterPosition(arenaCenter);
        copy.moveTo(masterPosition.x, masterPosition.y, masterPosition.z,
                architect.getYRot(), architect.getXRot());
        copy.initializeMasterMindCopy(
                architect.getUUID(), architect.getMaxHealth(), architect.getHealth(),
                architect.getTextureVariant());
        if (!mindLevel.addFreshEntity(copy)) {
            copy.discard();
            return false;
        }

        List<ServerPlayer> participants = level.players().stream()
                .filter(this::eligibleAtHearth)
                .filter(player -> player.distanceToSqr(architect)
                        <= SESSION_RADIUS * SESSION_RADIUS)
                .toList();
        if (!participants.contains(primaryTarget)) {
            participants = new ArrayList<>(participants);
            participants.add(primaryTarget);
        }
        for (int index = 0; index < participants.size(); index++) {
            ServerPlayer player = participants.get(index);
            participantIds.add(player.getUUID());
            ThaeIvenMindDimension.storeOrigin(player, architect.getUUID());
            Vec3 entry = ThaeIvenMindDimension.playerEntry(
                    arenaCenter, index, participants.size());
            Vec3 look = masterPosition.subtract(entry);
            float yaw = (float) (Mth.atan2(look.z, look.x) * 180.0D / Math.PI) - 90.0F;
            float pitch = (float) (-(Mth.atan2(look.y, look.horizontalDistance())
                    * 180.0D / Math.PI));
            player.teleportTo(mindLevel, entry.x, entry.y, entry.z, yaw, pitch);
            playerStates.put(player.getUUID(), createPlayerState(mindLevel, player, copy));
        }

        active = true;
        preparing = false;
        folded = true;
        returning = false;
        mindCopyId = copy.getUUID();
        foldTargetId = null;
        foldCastTicks = 0;
        setCongregationState(level, true);
        lockRealMaster();
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                architect.getX(), architect.getY() + 1.1D, architect.getZ(),
                90, 1.5D, 1.5D, 1.5D, 0.12D);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                architect.getX(), architect.getY() + 1.1D, architect.getZ(),
                65, 1.8D, 1.4D, 1.8D, 0.08D);
        FrozenDawn.LOGGER.info(
                "Master Architect {} folded into Thae Iven with {} participant(s), copy={}, residents={}/{}",
                shortId(architect.getUUID()), participantIds.size(), shortId(copy.getUUID()),
                survivingResidents, maximumResidents);
        return true;
    }

    private void tickPlayer(
            ServerLevel mindLevel, ServerPlayer player, ArchitectEntity copy) {
        PlayerFloodState state = playerStates.computeIfAbsent(
                player.getUUID(), ignored -> createPlayerState(mindLevel, player, copy));
        tickStackDecay(mindLevel, player, state);
        tickMoteRespawns(mindLevel, player, copy, state);
        tickStackTransfer(mindLevel, player, state);
        boolean inSanctuary = ThaeIvenMindDimension.isInsideSanctuary(
                player, architect.getUUID());
        double horizontalDistance = player.position().subtract(copy.position()).horizontalDistance();
        float proximity = MasterArchitectFloodPolicy.proximity(horizontalDistance);
        float cycleIntensity = MasterArchitectFloodPolicy.exposureIntensity(exposureCycle);
        double movementModifier = inSanctuary
                ? 0.0D
                : state.staggerTicks > 0
                ? MasterArchitectFloodPolicy.staggerMovementModifier(floodStrength)
                : Mth.clamp(
                        MasterArchitectFloodPolicy.movementModifier(proximity, floodStrength)
                                * cycleIntensity,
                        -0.98D,
                        0.0D);
        applyMovementModifier(player, movementModifier);
        player.setSprinting(false);
        if (state.staggerTicks > 0) {
            state.staggerTicks--;
        }

        boolean collected = collectNearbyMote(mindLevel, player, copy, state);
        enforceMemoryLock(mindLevel, player, copy, state);
        if (inSanctuary) {
            state.ticksSinceLastMote = 0;
            player.removeEffect(MobEffects.CONFUSION);
        } else if (!collected
                && state.stacks < MasterArchitectFloodPolicy.IVEN_STACK_CAP) {
            state.ticksSinceLastMote++;
            if (state.ticksSinceLastMote > MasterArchitectFloodPolicy.RUSH_GRACE_TICKS
                    && state.ticksSinceLastMote
                            % MasterArchitectFloodPolicy.RUSH_DAMAGE_INTERVAL_TICKS == 0) {
                player.hurt(createFloodDamageSource(mindLevel),
                        MasterArchitectFloodPolicy.rushDamage(
                                state.ticksSinceLastMote, floodStrength, proximity));
            }
        }

        if (copy.tickCount % 4 == 0) {
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodStatePayload(
                            copy.getId(), MasterArchitectFloodStatePayload.ACTIVE,
                            floodStrength, proximity, immersion));
            sendProgress(player, state);
        }
    }

    private PlayerFloodState createPlayerState(
            ServerLevel level, ServerPlayer player, ArchitectEntity copy) {
        PlayerFloodState state = new PlayerFloodState(new ArrayList<>());
        spawnMotes(level, player, copy, state);
        sendProgress(player, state);
        return state;
    }

    private void spawnMotes(
            ServerLevel level,
            ServerPlayer player,
            ArchitectEntity copy,
            PlayerFloodState state) {
        int count = MasterArchitectFloodPolicy.moteCount(floodStrength);
        for (int index = 0; index < count; index++) {
            Vec3 position = motePosition(copy, player, index, count, exposureCycle);
            int memoryType = index == count - 1
                    ? MemoryType.MAEVE.id
                    : MemoryType.values()[index % (MemoryType.values().length - 1)].id;
            FloodMote mote = new FloodMote(index, memoryType, position);
            state.motes.add(mote);
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodMotePayload(
                            MasterArchitectFloodMotePayload.SPAWN,
                            mote.id, mote.memoryType,
                            position.x, position.y, position.z));
        }
    }

    private Vec3 motePosition(
            ArchitectEntity copy,
            ServerPlayer player,
            int index,
            int count,
            int cycle) {
        double progress = (index + 1.0D) / (count + 1.0D);
        double outerRadius = Math.max(8.0D, 12.2D - cycle * 1.35D);
        double innerRadius = Math.max(4.8D, 6.2D - cycle * 0.55D);
        double radius = Mth.lerp(progress, outerRadius, innerRadius);
        Vec3 fromMaster = player.position().subtract(copy.position())
                .multiply(1.0D, 0.0D, 1.0D);
        double baseAngle = fromMaster.horizontalDistanceSqr() < 0.001D
                ? 0.0D : Math.atan2(fromMaster.z, fromMaster.x);
        double spread = (index - (count - 1) * 0.5D) * 0.28D;
        double angle = baseAngle + spread + cycle * 0.34D;
        return copy.position().add(
                Math.cos(angle) * radius,
                0.95D + (index % 3) * 0.18D,
                Math.sin(angle) * radius);
    }

    private boolean collectNearbyMote(
            ServerLevel level,
            ServerPlayer player,
            ArchitectEntity copy,
            PlayerFloodState state) {
        for (FloodMote mote : state.motes) {
            if (mote.collected
                    || player.distanceToSqr(mote.position) > MOTE_COLLECT_RANGE_SQUARED) {
                continue;
            }
            mote.collected = true;
            mote.respawnTicks = MasterArchitectFloodPolicy.MOTE_RESPAWN_TICKS;
            state.ticksSinceLastMote = 0;
            state.staggerTicks = MasterArchitectFloodPolicy.MOTE_STAGGER_TICKS;
            boolean coreWasReady = playerStates.values().stream()
                    .anyMatch(candidate -> candidate.stacks
                            >= MasterArchitectFloodPolicy.IVEN_STACK_CAP);
            state.stacks = Math.min(
                    MasterArchitectFloodPolicy.IVEN_STACK_CAP, state.stacks + 1);
            state.decayTicks = stackDecayTicks(level);
            if (mote.memoryType == MemoryType.MAEVE.id) {
                state.finalMemoryReceived = true;
            }
            Vec3 pull = copy.position().subtract(player.position());
            if (pull.horizontalDistanceSqr() > 0.001D) {
                Vec3 impulse = pull.normalize().scale(0.62D + 0.20D * floodStrength);
                player.setDeltaMovement(player.getDeltaMovement().add(
                        impulse.x, Math.max(0.08D, impulse.y * 0.15D), impulse.z));
                player.hurtMarked = true;
            }
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodMotePayload(
                            MasterArchitectFloodMotePayload.COLLECT,
                            mote.id, mote.memoryType,
                            mote.position.x, mote.position.y, mote.position.z));
            level.sendParticles(
                    ParticleTypes.SOUL,
                    mote.position.x, mote.position.y, mote.position.z,
                    28, 0.4D, 0.55D, 0.4D, 0.08D);
            copy.playSound(ModSounds.MASTER_ARCHITECT_FLOOD_MOTE.get(),
                    1.6F, 0.78F + mote.memoryType * 0.035F);
            if (!coreWasReady
                    && state.stacks >= MasterArchitectFloodPolicy.IVEN_STACK_CAP) {
                beginCoreReveal(level, copy);
            }
            sendProgress(player, state);
            return true;
        }
        return false;
    }

    private void beginCoreReveal(ServerLevel level, ArchitectEntity copy) {
        coreRevealTicks = MasterArchitectFloodPolicy.CORE_REVEAL_TICKS;
        copy.setMasterCombatVisual(
                MasterArchitectCombatAction.MIND_CORE_REVEAL,
                coreRevealTicks);
        copy.playSound(ModSounds.MASTER_ARCHITECT_CORE_REVEAL.get(), 3.6F, 1.0F);
        Vec3 core = copy.position().add(0.0D, 1.15D, 0.0D);
        level.sendParticles(
                ParticleTypes.SCULK_CHARGE_POP,
                core.x, core.y, core.z,
                18, 0.34D, 0.52D, 0.34D, 0.045D);
        level.sendParticles(
                ParticleTypes.FLASH,
                core.x, core.y, core.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void tickStackDecay(
            ServerLevel level, ServerPlayer player, PlayerFloodState state) {
        if (state.stacks <= 0 || coreExposed) {
            return;
        }
        if (--state.decayTicks > 0) {
            return;
        }
        state.stacks--;
        state.decayTicks = state.stacks > 0 ? stackDecayTicks(level) : 0;
        sendProgress(player, state);
    }

    private void tickStackTransfer(
            ServerLevel level, ServerPlayer giver, PlayerFloodState giverState) {
        if (giverState.transferCooldown > 0) {
            giverState.transferCooldown--;
        }
        if (!giver.isCrouching() || giverState.stacks <= 0
                || giverState.transferCooldown > 0) {
            giverState.transferTarget = null;
            giverState.transferTicks = 0;
            return;
        }
        ServerPlayer receiver = participantIds.stream()
                .filter(id -> !id.equals(giver.getUUID()))
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(player -> player != null && player.level() == level
                        && player.isAlive() && player.distanceToSqr(giver) <= 4.0D)
                .filter(player -> {
                    PlayerFloodState state = playerStates.get(player.getUUID());
                    return state != null
                            && state.stacks < MasterArchitectFloodPolicy.IVEN_STACK_CAP;
                })
                .findFirst().orElse(null);
        if (receiver == null) {
            giverState.transferTarget = null;
            giverState.transferTicks = 0;
            return;
        }
        if (!receiver.getUUID().equals(giverState.transferTarget)) {
            giverState.transferTarget = receiver.getUUID();
            giverState.transferTicks = 1;
            return;
        }
        if (++giverState.transferTicks < 20) {
            return;
        }

        PlayerFloodState receiverState = playerStates.get(receiver.getUUID());
        int moved = Math.min(
                giverState.stacks,
                MasterArchitectFloodPolicy.IVEN_STACK_CAP - receiverState.stacks);
        if (moved <= 0) {
            return;
        }
        giverState.stacks -= moved;
        receiverState.stacks += moved;
        giverState.decayTicks = giverState.stacks > 0 ? stackDecayTicks(level) : 0;
        receiverState.decayTicks = stackDecayTicks(level);
        giverState.transferCooldown = 40;
        receiverState.transferCooldown = 40;
        giverState.transferTarget = null;
        giverState.transferTicks = 0;
        sendProgress(giver, giverState);
        sendProgress(receiver, receiverState);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                (giver.getX() + receiver.getX()) * 0.5D,
                (giver.getEyeY() + receiver.getEyeY()) * 0.5D,
                (giver.getZ() + receiver.getZ()) * 0.5D,
                24, 0.35D, 0.45D, 0.35D, 0.06D);
    }

    private void tickMoteRespawns(
            ServerLevel level,
            ServerPlayer player,
            ArchitectEntity copy,
            PlayerFloodState state) {
        int count = state.motes.size();
        for (int index = 0; index < count; index++) {
            FloodMote mote = state.motes.get(index);
            if (!mote.collected || --mote.respawnTicks > 0) {
                continue;
            }
            mote.collected = false;
            mote.position = motePosition(copy, player, index, count, exposureCycle);
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodMotePayload(
                            MasterArchitectFloodMotePayload.SPAWN,
                            mote.id, mote.memoryType,
                            mote.position.x, mote.position.y, mote.position.z));
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    mote.position.x, mote.position.y, mote.position.z,
                    18, 0.35D, 0.45D, 0.35D, 0.055D);
        }
    }

    private void resetMotes(
            ServerLevel level,
            ServerPlayer player,
            ArchitectEntity copy,
            PlayerFloodState state) {
        PacketDistributor.sendToPlayer(player,
                new MasterArchitectFloodMotePayload(
                        MasterArchitectFloodMotePayload.CLEAR,
                        -1, -1, 0.0D, 0.0D, 0.0D));
        state.motes.clear();
        spawnMotes(level, player, copy, state);
    }

    private void sendProgress(ServerPlayer player, PlayerFloodState state) {
        PacketDistributor.sendToPlayer(player,
                new MasterArchitectFloodProgressPayload(
                        state.stacks,
                        exposureCycle,
                        coreExposed,
                        mindDeathReturnTicks > 0,
                        healingTier));
    }

    private void broadcastProgress() {
        if (architect.getServer() == null) {
            return;
        }
        for (UUID participantId : participantIds) {
            ServerPlayer player = architect.getServer().getPlayerList().getPlayer(participantId);
            PlayerFloodState state = playerStates.get(participantId);
            if (player != null && state != null) {
                sendProgress(player, state);
            }
        }
    }

    private void enforceMemoryLock(
            ServerLevel level,
            ServerPlayer player,
            ArchitectEntity copy,
            PlayerFloodState state) {
        if (coreExposed
                || state.stacks >= MasterArchitectFloodPolicy.IVEN_STACK_CAP) {
            return;
        }

        Vec3 masterPosition = copy.position();
        Vec3 playerOffset = player.position().subtract(masterPosition);
        double playerDistance = playerOffset.horizontalDistance();
        if (!MasterArchitectFloodPolicy.isInsideMemoryLock(playerDistance)) {
            return;
        }

        Vec3 outward = playerDistance > 0.001D
                ? new Vec3(playerOffset.x / playerDistance, 0.0D, playerOffset.z / playerDistance)
                : new Vec3(0.0D, 0.0D, 1.0D);
        if (outward.horizontalDistanceSqr() < 0.001D) {
            outward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 corrected = masterPosition.add(
                outward.scale(MasterArchitectFloodPolicy.MEMORY_LOCK_DISTANCE));
        player.teleportTo(
                level,
                corrected.x,
                player.getY(),
                corrected.z,
                player.getYRot(),
                player.getXRot());

        Vec3 velocity = player.getDeltaMovement();
        double radialVelocity = velocity.x * outward.x + velocity.z * outward.z;
        if (radialVelocity < 0.0D) {
            velocity = velocity.subtract(outward.scale(radialVelocity));
        }
        player.setDeltaMovement(velocity.x, Math.min(velocity.y, 0.0D), velocity.z);
        player.hurtMarked = true;
        if (state.lockFeedbackTicks-- <= 0) {
            state.lockFeedbackTicks = MEMORY_LOCK_FEEDBACK_INTERVAL_TICKS;
            level.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    player.getX(), player.getY() + 0.9D, player.getZ(),
                    22, 0.55D, 0.75D, 0.55D, 0.045D);
        }
    }

    private void tickReturnDeath(ServerLevel level) {
        lockRealMaster();
        if (returnTicks == 0) {
            Vec3 shove = architect.getLookAngle().multiply(-1.25D, 0.0D, -1.25D);
            architect.setDeltaMovement(shove.x, 0.18D, shove.z);
            level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    architect.getX(), architect.getY() + 1.1D, architect.getZ(),
                    120, 1.3D, 1.2D, 1.3D, 0.22D);
            architect.playSound(ModSounds.MASTER_ARCHITECT_FLOOD_RETURN.get(), 2.5F, 0.62F);
        }
        if (returnTicks < RETURN_STAGGER_TICKS) {
            architect.setMasterCombatVisual(
                    MasterArchitectCombatAction.MIND_RETURN_STAGGER, returnTicks);
        } else {
            architect.setMasterCombatVisual(
                    MasterArchitectCombatAction.MIND_RETURN_CHARGE,
                    returnTicks - RETURN_STAGGER_TICKS);
            level.sendParticles(
                    returnTicks % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                    architect.getX(), architect.getY() + 1.0D, architect.getZ(),
                    10, 0.8D, 1.0D, 0.8D, 0.035D);
        }
        returnTicks++;
        if (returnTicks < RETURN_DEATH_TICKS) {
            return;
        }

        returning = false;
        architect.setInvulnerable(false);
        architect.setNoAi(false);
        releaseOriginChunk(level);
        ServerPlayer killer = returnKillerId == null
                ? null : level.getServer().getPlayerList().getPlayer(returnKillerId);
        DamageSource source = killer == null
                ? level.damageSources().generic()
                : level.damageSources().playerAttack(killer);
        architect.executeMindReturnDeath(source);
    }

    private void tickMindDeathEjection(ServerLevel level) {
        ServerLevel mindLevel = level.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY);
        ArchitectEntity copy = findMindCopy(mindLevel);
        int elapsed = MIND_DEATH_EJECTION_TICKS - mindDeathReturnTicks;
        if (copy != null && mindLevel != null) {
            copy.setInvulnerable(true);
            copy.setNoAi(true);
            copy.getNavigation().stop();
            copy.setTarget(null);
            copy.setDeltaMovement(Vec3.ZERO);
            copy.setMasterCombatVisual(
                    MasterArchitectCombatAction.MIND_RETURN_STAGGER, elapsed);
            emitMindDeathStagger(mindLevel, copy, elapsed);
            if (elapsed % 10 == 0) {
                copy.playSound(
                        ModSounds.MASTER_ARCHITECT_MIND_DEATH_WAIL.get(),
                        6.8F,
                        0.42F + copy.getRandom().nextFloat() * 0.14F);
            }
            if (elapsed == MIND_DEATH_PORTAL_SOUND_TICK) {
                mindLevel.playSound(
                        null,
                        copy.blockPosition(),
                        SoundEvents.PORTAL_TRAVEL,
                        SoundSource.MASTER,
                        3.2F,
                        0.68F);
            }
            if (elapsed == MIND_DEATH_PUSH_TICK) {
                pushParticipantsFromMindCopy(copy);
            }
            if (elapsed % 4 == 0) {
                for (UUID participantId : participantIds) {
                    ServerPlayer player = level.getServer().getPlayerList()
                            .getPlayer(participantId);
                    PlayerFloodState state = playerStates.get(participantId);
                    if (player == null || state == null
                            || player.level() != mindLevel) {
                        continue;
                    }
                    float proximity = MasterArchitectFloodPolicy.proximity(
                            player.position().subtract(copy.position())
                                    .horizontalDistance());
                    PacketDistributor.sendToPlayer(player,
                            new MasterArchitectFloodStatePayload(
                                    copy.getId(),
                                    MasterArchitectFloodStatePayload.ACTIVE,
                                    floodStrength,
                                    proximity,
                                    immersion));
                    sendProgress(player, state);
                }
            }
        }
        if (--mindDeathReturnTicks <= 0) {
            completeMindDeathEjection(level);
        }
    }

    private void completeMindDeathEjection(ServerLevel level) {
        ServerPlayer killer = mindDeathKillerId == null
                ? null : level.getServer().getPlayerList().getPlayer(mindDeathKillerId);
        int operation = killer != null
                && playerStates.getOrDefault(killer.getUUID(), PlayerFloodState.EMPTY)
                        .finalMemoryReceived
                ? MasterArchitectFloodStatePayload.COMPLETE_RECEIVED
                : MasterArchitectFloodStatePayload.COMPLETE_REFUSED;
        clearAllPlayers(level, operation, true);
        discardMindCopy(level);
        active = false;
        preparing = false;
        folded = false;
        returning = true;
        immersion = 0.0F;
        mindDeathReturnTicks = 0;
        returnTicks = Math.max(1, RETURN_DEATH_TICKS - 2);
        returnKillerId = mindDeathKillerId;
        setCongregationState(level, false);
        lockRealMaster();
        FrozenDawn.LOGGER.info(
                "Master Architect {} returned its participants; canonical death queued",
                shortId(architect.getUUID()));
    }

    private void emitMindDeathStagger(
            ServerLevel mindLevel, ArchitectEntity copy, int elapsed) {
        float progress = Mth.clamp(
                elapsed
                        / (float) MasterArchitectFloodPolicy.MIND_DEATH_DISINTEGRATION_TICKS,
                0.0F,
                1.0F);
        Vec3 center = copy.position().add(0.0D, 0.12D + progress * 1.72D, 0.0D);
        int count = 5 + Mth.floor(progress * 8.0F);
        mindLevel.sendParticles(
                elapsed % 2 == 0 ? ParticleTypes.ASH : ParticleTypes.SCULK_SOUL,
                center.x, center.y, center.z,
                count, 0.42D, 0.16D, 0.42D, 0.035D + progress * 0.04D);
        if (elapsed % 4 == 0) {
            mindLevel.sendParticles(
                    ParticleTypes.SCULK_CHARGE_POP,
                    center.x, center.y, center.z,
                    3 + Mth.floor(progress * 4.0F),
                    0.34D, 0.18D, 0.34D, 0.025D);
        }
        if (elapsed % 6 == 0) {
            mindLevel.sendParticles(
                    ParticleTypes.SOUL,
                    center.x, center.y, center.z,
                    2, 0.28D, 0.12D, 0.28D, 0.018D);
        }
    }

    private void pushParticipantsFromMindCopy(ArchitectEntity copy) {
        for (UUID participantId : participantIds) {
            ServerPlayer player = copy.getServer() == null
                    ? null : copy.getServer().getPlayerList().getPlayer(participantId);
            if (player == null || player.level() != copy.level()) {
                continue;
            }
            Vec3 away = player.position().subtract(copy.position());
            if (away.horizontalDistanceSqr() < 0.001D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 impulse = away.normalize().scale(1.15D);
            player.setDeltaMovement(impulse.x, 0.32D, impulse.z);
            player.hurtMarked = true;
        }
    }

    private void beginRetreat(ServerLevel level, String reason) {
        ServerLevel mindLevel = level.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY);
        ArchitectEntity copy = findMindCopy(mindLevel);
        float healedHealth = copy == null ? copyHealthSnapshot : copy.getHealth();
        float cap = architect.getMaxHealth()
                * MasterArchitectFloodPolicy.failedFoldHealthCap(presetName(level));
        float restoredHealth = Mth.clamp(
                healedHealth <= 0.0F ? architect.getHealth() : healedHealth,
                architect.getMaxHealth() * MasterArchitectFloodPolicy.RETREAT_HEALTH_FRACTION,
                cap);
        clearAllPlayers(level, MasterArchitectFloodStatePayload.CLEAR, true);
        discardMindCopy(level);
        architect.setHealth(restoredHealth);
        active = false;
        preparing = false;
        folded = false;
        returning = false;
        immersion = 0.0F;
        noPlayerTicks = 0;
        retreatTicks = "throne-restored-to-half".equals(reason)
                ? 0
                : MasterArchitectFloodPolicy.RETREAT_REARM_TICKS;
        recoveryTicks = 0;
        foldCastTicks = 0;
        mindDeathReturnTicks = 0;
        foldTargetId = null;
        mindDeathKillerId = null;
        exposureKillerId = null;
        coreExposed = false;
        exposureTicks = 0;
        coreRevealTicks = 0;
        healingPressureTicks = 0;
        healingTier = 1;
        surgeTelegraphTicks = 0;
        setCongregationState(level, false);
        architect.setInvulnerable(false);
        architect.setNoAi(false);
        architect.setMasterCombatVisual(MasterArchitectCombatAction.IDLE, 0);
        combatController.resumeAfterFailedMind(level);
        releaseOriginChunk(level);
        FrozenDawn.LOGGER.info(
                "Master Architect {} closed Thae Iven ({}); returned at {}/{} health, resumed its health-matched phase, and re-arms the Flood in {} ticks",
                shortId(architect.getUUID()), reason,
                String.format("%.1f", architect.getHealth()),
                String.format("%.1f", architect.getMaxHealth()), retreatTicks);
    }

    private void tickRetreat(ServerLevel level) {
        if (recoveryTicks > 0) {
            recoveryTicks--;
            float targetHealth = architect.getMaxHealth()
                    * MasterArchitectFloodPolicy.RETREAT_HEALTH_FRACTION;
            if (architect.getHealth() < targetHealth) {
                architect.heal(Math.min(
                        targetHealth - architect.getHealth(),
                        targetHealth / MasterArchitectFloodPolicy.RETREAT_RECOVERY_TICKS));
            }
        }
        if (--retreatTicks == 0) {
            FrozenDawn.LOGGER.info(
                    "Master Architect {} re-armed the Flood at {}/{} health",
                    shortId(architect.getUUID()),
                    String.format("%.1f", architect.getHealth()),
                    String.format("%.1f", architect.getMaxHealth()));
        }
    }

    private void recoverAfterRestart(ServerLevel level) {
        restartRecoveryPending = false;
        FrozenDawn.LOGGER.warn(
                "Master Architect {} recovered a persisted Thae Iven session after restart",
                shortId(architect.getUUID()));
        beginRetreat(level, "server-restart");
    }

    private void lockRealMaster() {
        architect.getNavigation().stop();
        architect.setTarget(null);
        architect.setSprinting(false);
        if (!returning || returnTicks > 0) {
            architect.setDeltaMovement(0.0D, architect.getDeltaMovement().y, 0.0D);
        }
        architect.setInvulnerable(true);
        architect.setNoAi(true);
        if (!returning) {
            architect.setMasterCombatVisual(MasterArchitectCombatAction.FLOOD_CHANNEL, 0);
        }
    }

    private void emitFoldDrawParticles(ServerLevel level) {
        Vec3 center = architect.position().add(0.0D, 1.05D, 0.0D);
        for (int index = 0; index < 18; index++) {
            double angle = index * (Math.PI * 2.0D / 18.0D)
                    + foldCastTicks * 0.23D;
            double radius = 2.4D + (index % 3) * 0.55D;
            Vec3 origin = center.add(
                    Math.cos(angle) * radius,
                    -0.65D + (index % 6) * 0.27D,
                    Math.sin(angle) * radius);
            Vec3 velocity = center.subtract(origin).normalize()
                    .scale(0.10D + foldCastTicks * 0.003D);
            level.sendParticles(
                    index % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL,
                    origin.x, origin.y, origin.z,
                    0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        if (foldCastTicks % 2 == 0) {
            level.sendParticles(
                    ParticleTypes.SCULK_CHARGE_POP,
                    center.x, center.y, center.z,
                    5 + foldCastTicks / 3,
                    0.55D, 0.85D, 0.55D, 0.04D);
        }
    }

    private void emitFoldEntryBurst(ServerLevel level) {
        Vec3 center = architect.position().add(0.0D, 1.05D, 0.0D);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                center.x, center.y, center.z,
                90, 2.1D, 1.4D, 2.1D, 0.16D);
        level.sendParticles(
                ParticleTypes.SCULK_CHARGE_POP,
                center.x, center.y, center.z,
                45, 1.15D, 1.2D, 1.15D, 0.10D);
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z,
                70, 1.55D, 1.3D, 1.55D, 0.12D);
    }

    private void emitFoldStrike(ServerLevel level, ServerPlayer target) {
        Vec3 start = architect.position().add(0.0D, 1.15D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getEyeHeight() * 0.72D, 0.0D);
        for (int step = 0; step <= 18; step++) {
            double progress = step / 18.0D;
            Vec3 point = start.lerp(end, progress);
            level.sendParticles(
                    step % 4 == 0 ? ParticleTypes.SOUL : ParticleTypes.REVERSE_PORTAL,
                    point.x, point.y, point.z,
                    2, 0.09D, 0.09D, 0.09D, 0.018D);
        }
    }

    @Nullable
    private ArchitectEntity findMindCopy(@Nullable ServerLevel mindLevel) {
        if (mindLevel == null || mindCopyId == null) {
            return null;
        }
        Entity entity = mindLevel.getEntity(mindCopyId);
        return entity instanceof ArchitectEntity candidate && candidate.isMasterMindCopy()
                ? candidate : null;
    }

    private void discardMindCopy(ServerLevel originLevel) {
        ServerLevel mindLevel = originLevel.getServer().getLevel(ThaeIvenMindDimension.LEVEL_KEY);
        ArchitectEntity copy = findMindCopy(mindLevel);
        if (copy != null) {
            copy.discard();
        }
        mindCopyId = null;
    }

    private void forceOriginChunk(ServerLevel level) {
        ChunkPos chunk = architect.chunkPosition();
        forcedChunkX = chunk.x;
        forcedChunkZ = chunk.z;
        originChunkWasForced = level.getForcedChunks().contains(chunk.toLong());
        originChunkForcedBySession = !originChunkWasForced
                && level.setChunkForced(forcedChunkX, forcedChunkZ, true);
    }

    private void releaseOriginChunk(ServerLevel level) {
        if (originChunkForcedBySession && !originChunkWasForced) {
            level.setChunkForced(forcedChunkX, forcedChunkZ, false);
        }
        originChunkForcedBySession = false;
    }

    private boolean eligibleAtHearth(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private void clearAllPlayers(ServerLevel level, int operation, boolean returnToOrigin) {
        for (UUID participantId : List.copyOf(participantIds)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(participantId);
            if (player == null) {
                continue;
            }
            clearPlayerEffects(player);
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodStatePayload(
                            -1, operation, floodStrength, 0.0F, 0.0F));
            PacketDistributor.sendToPlayer(player,
                    new MasterArchitectFloodMotePayload(
                            MasterArchitectFloodMotePayload.CLEAR,
                            -1, -1, 0.0D, 0.0D, 0.0D));
            if (returnToOrigin) {
                ThaeIvenMindDimension.returnToOrigin(player, architect.getUUID());
            }
        }
        playerStates.clear();
        if (returnToOrigin) {
            participantIds.clear();
        }
    }

    private void clearPlayerEffects(ServerPlayer player) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(MOVEMENT_MODIFIER_ID);
        }
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private static void applyMovementModifier(ServerPlayer player, double amount) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        AttributeModifier existing = movement.getModifier(MOVEMENT_MODIFIER_ID);
        if (existing != null && Math.abs(existing.amount() - amount) < 0.002D) {
            return;
        }
        movement.addOrUpdateTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private DamageSource createFloodDamageSource(ServerLevel level) {
        return new DamageSource(
                level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.THAE_IVEN));
    }

    private void setCongregationState(ServerLevel level, boolean kneeling) {
        architect.getHearthMasterArchitectId().ifPresent(
                hearthId -> HearthCombatRosterManager.setFloodKneeling(
                        level, hearthId, kneeling));
        congregationApplied = kneeling;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private enum MemoryType {
        DOOR(0),
        STARS(1),
        VIGIL(2),
        ETHVEN(3),
        MAEVE(4);

        private final int id;

        MemoryType(int id) {
            this.id = id;
        }
    }

    private static final class FloodMote {
        private final int id;
        private final int memoryType;
        private Vec3 position;
        private boolean collected;
        private int respawnTicks;

        private FloodMote(int id, int memoryType, Vec3 position) {
            this.id = id;
            this.memoryType = memoryType;
            this.position = position;
        }
    }

    private static final class PlayerFloodState {
        private static final PlayerFloodState EMPTY = new PlayerFloodState(List.of());

        private final List<FloodMote> motes;
        private int ticksSinceLastMote;
        private int staggerTicks;
        private int lockFeedbackTicks;
        private int stacks;
        private int decayTicks;
        private int transferTicks;
        private int transferCooldown;
        private UUID transferTarget;
        private boolean finalMemoryReceived;

        private PlayerFloodState(List<FloodMote> motes) {
            this.motes = motes;
        }

        private boolean hasUncollectedMotes() {
            return motes.stream().anyMatch(mote -> !mote.collected);
        }
    }
}
