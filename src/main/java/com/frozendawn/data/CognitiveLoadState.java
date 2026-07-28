package com.frozendawn.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Persistent, per-player Thae Iven cognitive-load state. */
public final class CognitiveLoadState implements INBTSerializable<CompoundTag> {
    private float load;
    private boolean terminalTakeover;
    private int takeoverTicks;
    private float breakoutTicks;
    private float resistanceInput;
    private int resistanceInputTtl;
    private int lapseCooldownTicks;
    private int hotbarResetCooldownTicks;
    private int rememberedHotbarSlot = -1;

    public float load() {
        return load;
    }

    public void setLoad(float load) {
        this.load = Mth.clamp(load, 0.0F, 100.0F);
    }

    public int takeoverTicks() {
        return takeoverTicks;
    }

    public boolean terminalTakeover() {
        return terminalTakeover;
    }

    public void setTerminalTakeover(boolean terminalTakeover) {
        this.terminalTakeover = terminalTakeover;
    }

    public void setTakeoverTicks(int takeoverTicks) {
        this.takeoverTicks = Math.max(0, takeoverTicks);
    }

    public float breakoutTicks() {
        return breakoutTicks;
    }

    public void setBreakoutTicks(float breakoutTicks) {
        this.breakoutTicks = Math.max(0.0F, breakoutTicks);
    }

    public float resistanceInput() {
        return resistanceInputTtl > 0 ? resistanceInput : 0.0F;
    }

    public void setResistanceInput(float resistanceInput, int ttl) {
        this.resistanceInput = Mth.clamp(resistanceInput, 0.0F, 1.0F);
        this.resistanceInputTtl = Math.max(0, ttl);
    }

    public void tickResistanceInput() {
        resistanceInputTtl = Math.max(0, resistanceInputTtl - 1);
        if (resistanceInputTtl == 0) {
            resistanceInput = 0.0F;
        }
    }

    public int lapseCooldownTicks() {
        return lapseCooldownTicks;
    }

    public void setLapseCooldownTicks(int lapseCooldownTicks) {
        this.lapseCooldownTicks = Math.max(0, lapseCooldownTicks);
    }

    public int hotbarResetCooldownTicks() {
        return hotbarResetCooldownTicks;
    }

    public void setHotbarResetCooldownTicks(int hotbarResetCooldownTicks) {
        this.hotbarResetCooldownTicks = Math.max(0, hotbarResetCooldownTicks);
    }

    public int rememberedHotbarSlot() {
        return rememberedHotbarSlot;
    }

    public void setRememberedHotbarSlot(int rememberedHotbarSlot) {
        this.rememberedHotbarSlot = rememberedHotbarSlot >= 0
                && rememberedHotbarSlot < 9 ? rememberedHotbarSlot : -1;
    }

    public void clearTransientEffects() {
        terminalTakeover = false;
        takeoverTicks = 0;
        breakoutTicks = 0.0F;
        resistanceInput = 0.0F;
        resistanceInputTtl = 0;
        lapseCooldownTicks = 0;
        hotbarResetCooldownTicks = 0;
        rememberedHotbarSlot = -1;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Load", load);
        tag.putBoolean("TerminalTakeover", terminalTakeover);
        tag.putInt("TakeoverTicks", takeoverTicks);
        tag.putFloat("BreakoutTicks", breakoutTicks);
        tag.putInt("LapseCooldownTicks", lapseCooldownTicks);
        tag.putInt("HotbarResetCooldownTicks", hotbarResetCooldownTicks);
        tag.putInt("RememberedHotbarSlot", rememberedHotbarSlot);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        setLoad(tag.getFloat("Load"));
        setTerminalTakeover(tag.getBoolean("TerminalTakeover"));
        setTakeoverTicks(tag.getInt("TakeoverTicks"));
        setBreakoutTicks(tag.getFloat("BreakoutTicks"));
        resistanceInput = 0.0F;
        resistanceInputTtl = 0;
        setLapseCooldownTicks(tag.getInt("LapseCooldownTicks"));
        setHotbarResetCooldownTicks(tag.getInt("HotbarResetCooldownTicks"));
        setRememberedHotbarSlot(tag.contains("RememberedHotbarSlot")
                ? tag.getInt("RememberedHotbarSlot") : -1);
    }
}
