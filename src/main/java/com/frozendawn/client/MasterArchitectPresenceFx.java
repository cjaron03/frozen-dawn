package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-local ambient and combat presence for the Hearth Master Architect. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class MasterArchitectPresenceFx {
    private static final double SEARCH_RADIUS = 56.0D;
    private static final float HOSTILE_THRESHOLD = 0.05F;

    private MasterArchitectPresenceFx() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }

        AABB searchArea = minecraft.player.getBoundingBox().inflate(SEARCH_RADIUS);
        for (ArchitectEntity master : level.getEntitiesOfClass(
                ArchitectEntity.class,
                searchArea,
                ArchitectEntity::isHearthMasterArchitect)) {
            if (!master.isAlive() || master.isInvisible() || master.getDeathTicks() > 0) {
                continue;
            }
            tickPresence(level, master);
        }
    }

    private static void tickPresence(ClientLevel level, ArchitectEntity master) {
        int tick = master.tickCount;
        int action = master.getMasterCombatAction();
        float hostility = Mth.clamp(
                Math.max(
                        MasterArchitectWeather.getStrength(),
                        action == MasterArchitectCombatAction.IDLE ? 0.0F : 1.0F),
                0.0F,
                1.0F);
        boolean hostile = hostility > HOSTILE_THRESHOLD;

        int orbitInterval = hostile ? 3 : 9;
        if (Math.floorMod(tick + master.getId(), orbitInterval) == 0) {
            spawnOrbitMote(level, master, hostile, tick);
        }

        if (hostile && Math.floorMod(tick + master.getId(), 3) == 0) {
            Vec3 center = master.position().add(0.0D, 1.05D, 0.0D);
            spawnConvergingParticle(
                    level,
                    master,
                    ParticleTypes.SNOWFLAKE,
                    center,
                    1.75D + hostility * 0.65D,
                    0.10D,
                    tick * 0.71D + master.getId());
        }

        if (action == MasterArchitectCombatAction.THERMAL_SEVER) {
            tickThermalConvergence(
                    level, master, tick, master.getMasterCombatActionTicks());
        } else if (hostile
                && master.getMasterThermalCharge() >= 0.98F
                && Math.floorMod(tick + master.getId(), 4) == 0) {
            tickLoadedThermalHand(level, master, tick);
        } else if (action == MasterArchitectCombatAction.LAST_WALL_CAST
                || action == MasterArchitectCombatAction.LAST_WALL_HEAL) {
            tickLastWallConvergence(level, master, tick);
        }
    }

    private static void spawnOrbitMote(
            ClientLevel level,
            ArchitectEntity master,
            boolean hostile,
            int tick) {
        double speed = hostile ? 0.18D : 0.075D;
        double angle = tick * speed + master.getId() * 0.73D;
        boolean crownOrbit = ((tick / (hostile ? 3 : 9)) & 1) == 0;
        double radius = crownOrbit ? 0.48D : 0.66D;
        double height = crownOrbit
                ? 1.82D + Math.sin(angle * 0.55D) * 0.12D
                : 1.08D + Math.sin(angle * 0.70D) * 0.22D;
        double x = master.getX() + Math.cos(angle) * radius;
        double z = master.getZ() + Math.sin(angle) * radius;
        double tangentSpeed = hostile ? 0.025D : 0.010D;

        level.addParticle(
                ParticleTypes.SCULK_SOUL,
                x,
                master.getY() + height,
                z,
                -Math.sin(angle) * tangentSpeed,
                hostile ? 0.006D : 0.002D,
                Math.cos(angle) * tangentSpeed);
    }

    private static void tickThermalConvergence(
            ClientLevel level,
            ArchitectEntity master,
            int tick,
            int actionTicks) {
        float charge = com.frozendawn.homo.MasterArchitectCombatPolicy
                .thermalCastCharge(actionTicks);
        if (charge <= 0.0F) {
            return;
        }
        Vec3 focus = thermalHandFocus(master, charge);
        Vec3 shoulder = thermalShoulderFocus(master);
        Vec3 frostFront = focus.lerp(shoulder, charge);
        spawnConvergingParticle(
                level,
                master,
                ParticleTypes.SNOWFLAKE,
                frostFront,
                0.72D + charge * 0.58D,
                0.08D + charge * 0.09D,
                tick * 1.21D);
        if ((tick & 1) == 0) {
            spawnConvergingParticle(
                    level,
                    master,
                    ParticleTypes.END_ROD,
                    focus,
                    0.32D + charge * 0.30D,
                    0.045D + charge * 0.05D,
                    tick * 1.63D + 0.8D);
        }
        if (charge > 0.25F && (tick & 1) == 0) {
            spawnConvergingParticle(
                    level,
                    master,
                    ParticleTypes.SCULK_SOUL,
                    focus,
                    0.45D + charge * 0.52D,
                    0.06D + charge * 0.06D,
                    tick * -0.87D + 1.4D);
        }
        if (charge > 0.82F) {
            level.addParticle(
                    ParticleTypes.END_ROD,
                    focus.x,
                    focus.y,
                    focus.z,
                    0.0D,
                    0.006D,
                    0.0D);
        }
    }

    private static void tickLoadedThermalHand(
            ClientLevel level, ArchitectEntity master, int tick) {
        Vec3 focus = thermalHandFocus(master, 0.0F);
        double angle = tick * 0.46D + master.getId();
        level.addParticle(
                ParticleTypes.END_ROD,
                focus.x + Math.cos(angle) * 0.18D,
                focus.y + Math.sin(angle * 1.4D) * 0.12D,
                focus.z + Math.sin(angle) * 0.18D,
                0.0D,
                0.004D,
                0.0D);
        level.addParticle(
                ParticleTypes.SNOWFLAKE,
                focus.x,
                focus.y,
                focus.z,
                0.0D,
                0.012D,
                0.0D);
    }

    private static void tickLastWallConvergence(
            ClientLevel level,
            ArchitectEntity master,
            int tick) {
        Vec3 center = master.position().add(0.0D, 1.05D, 0.0D);
        spawnConvergingParticle(
                level,
                master,
                ParticleTypes.SNOWFLAKE,
                center,
                2.25D,
                0.13D,
                tick * 0.93D);
        if ((tick & 1) == 0) {
            spawnConvergingParticle(
                    level,
                    master,
                    ParticleTypes.SCULK_SOUL,
                    center,
                    1.55D,
                    0.10D,
                    tick * -0.79D + 2.2D);
        }
    }

    private static void spawnConvergingParticle(
            ClientLevel level,
            ArchitectEntity master,
            ParticleOptions particle,
            Vec3 target,
            double radius,
            double speed,
            double angle) {
        double verticalWave = 0.45D + 0.65D * (0.5D + 0.5D * Math.sin(angle * 1.7D));
        Vec3 spawn = new Vec3(
                target.x + Math.cos(angle) * radius,
                master.getY() + verticalWave,
                target.z + Math.sin(angle) * radius);
        BlockPos spawnPos = BlockPos.containing(spawn);
        if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()) {
            return;
        }

        Vec3 velocity = target.subtract(spawn);
        if (velocity.lengthSqr() < 1.0E-4D) {
            return;
        }
        velocity = velocity.normalize().scale(speed);
        level.addParticle(
                particle,
                spawn.x,
                spawn.y,
                spawn.z,
                velocity.x,
                velocity.y,
                velocity.z);
    }

    private static Vec3 thermalHandFocus(ArchitectEntity master, float charge) {
        Vec3 look = horizontalLook(master);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        return master.position()
                .add(0.0D, 1.05D + charge * 0.23D, 0.0D)
                .add(look.scale(0.12D + charge * 0.60D))
                .add(right.scale(-0.34D));
    }

    private static Vec3 thermalShoulderFocus(ArchitectEntity master) {
        Vec3 look = horizontalLook(master);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        return master.position()
                .add(0.0D, 1.48D, 0.0D)
                .add(right.scale(-0.34D));
    }

    private static Vec3 horizontalLook(ArchitectEntity master) {
        Vec3 look = master.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        return horizontal.lengthSqr() < 1.0E-4D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }
}
