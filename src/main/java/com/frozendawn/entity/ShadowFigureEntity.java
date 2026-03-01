package com.frozendawn.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Client-only shadow figure entity used for sanity hallucinations.
 * No physics, no collision, no save — purely visual.
 */
public class ShadowFigureEntity extends Entity {

    private static final EntityDataAccessor<Boolean> DATA_IS_WATCHER =
            SynchedEntityData.defineId(ShadowFigureEntity.class, EntityDataSerializers.BOOLEAN);

    private int ticksAlive = 0;
    private boolean fading = false;
    private int fadeOutTicks = 0;
    private int maxFadeOutTicks = 20;

    public ShadowFigureEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_IS_WATCHER, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick(); // needed for bounding box + entity lifecycle
        ticksAlive++;

        if (fading) {
            fadeOutTicks++;
            if (fadeOutTicks >= maxFadeOutTicks) {
                discard();
            }
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    // -- Watcher flag --

    public boolean isWatcher() {
        return entityData.get(DATA_IS_WATCHER);
    }

    public void setWatcher(boolean watcher) {
        entityData.set(DATA_IS_WATCHER, watcher);
    }

    // -- Fade logic --

    public void startFading() {
        if (!fading) {
            fading = true;
            fadeOutTicks = 0;
        }
    }

    public void startFading(int duration) {
        maxFadeOutTicks = duration;
        startFading();
    }

    public boolean isFading() { return fading; }

    public int getTicksAlive() { return ticksAlive; }

    public int getFadeOutTicks() { return fadeOutTicks; }

    public int getMaxFadeOutTicks() { return maxFadeOutTicks; }

    /**
     * Compute alpha for rendering: fade in over 20 ticks, fade out when fading, max 70%.
     */
    public float computeAlpha(float partialTick) {
        float age = ticksAlive + partialTick;
        float fadeIn = Math.min(1.0f, age / 20.0f);
        float fadeOut = fading ? Math.max(0.0f, 1.0f - (fadeOutTicks + partialTick) / maxFadeOutTicks) : 1.0f;
        return fadeIn * fadeOut * 0.7f;
    }
}
