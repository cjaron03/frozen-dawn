package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Disposable lab villager. Guided mode replaces only the brain's destination selection. */
final class ArchitectWildernessTarget extends Villager {
    private Vec3 destination;
    private boolean autonomous;
    private int repath;
    private double travelSpeed = 0.45;
    private boolean lastPathReachable;

    ArchitectWildernessTarget(ServerLevel level) {
        super(EntityType.VILLAGER, level);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        setHealth(100);
        setPersistenceRequired();
        addTag("fd_lab");
        addTag("fd_wilderness");
        setCustomName(net.minecraft.network.chat.Component.literal("Wilderness pursuit target"));
        setCustomNameVisible(true);
    }

    void guideTo(Vec3 destination) {
        if (destination.equals(this.destination)) return;
        this.destination = destination;
        repath = 0;
    }

    void setAutonomous(boolean autonomous) { this.autonomous = autonomous; }
    void travelSpeed(double speed) { travelSpeed = speed; }
    boolean pathReachable() { return lastPathReachable; }

    @Override
    protected void customServerAiStep() {
        if (autonomous) {
            super.customServerAiStep();
            return;
        }
        // Mob's normal navigation, move/jump controls, collisions, gravity and damage still tick.
        // No teleports, velocity injection, or invulnerability during a run.
        if (destination == null || distanceToSqr(destination) < 1.0) {
            getNavigation().stop();
            return;
        }
        if (--repath <= 0) {
            var path = getNavigation().createPath(BlockPos.containing(destination), 0);
            lastPathReachable = path != null && path.canReach();
            if (path != null) getNavigation().moveTo(path, travelSpeed);
            repath = 15;
        }
    }
}
