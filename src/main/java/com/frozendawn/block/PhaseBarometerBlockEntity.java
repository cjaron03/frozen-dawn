package com.frozendawn.block;

import com.frozendawn.barometer.ForecastBand;
import com.frozendawn.barometer.PhaseBarometerForecasts;
import com.frozendawn.barometer.PhaseBarometerSnapshot;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PhaseBarometerBlockEntity extends BlockEntity implements MenuProvider {

    private static final long ANNOUNCEMENT_TICK_INTERVAL = 20L;

    private boolean announcementsInitialized;
    private int lastAnnouncedPhase = -1;
    private int lastAnnouncedPhase6Stage = -1;
    private int lastAnnouncedBand = -1;

    public PhaseBarometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHASE_BAROMETER.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % ANNOUNCEMENT_TICK_INTERVAL != 0L) {
            return;
        }

        ApocalypseState apocalypseState = ApocalypseState.get(serverLevel.getServer());
        int phase = apocalypseState.getPhase();
        float progress = apocalypseState.getPreciseProgress();
        PhaseBarometerSnapshot snapshot = PhaseBarometerForecasts.evaluate(phase, progress);
        int phase6Stage = PhaseManager.getPhase6Stage(phase, progress).ordinal();
        int band = snapshot.forecastBand().ordinal();

        if (!announcementsInitialized) {
            announcementsInitialized = true;
            lastAnnouncedPhase = phase;
            lastAnnouncedPhase6Stage = phase6Stage;
            lastAnnouncedBand = band;
            setChanged();
            return;
        }

        boolean strongAlert = phase != lastAnnouncedPhase
                || (phase == 6 && phase6Stage != lastAnnouncedPhase6Stage
                && phase6Stage >= PhaseManager.Phase6Stage.MID.ordinal());

        if (strongAlert) {
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.85f, 0.8f);
        } else if (snapshot.forecastBand() == ForecastBand.IMMINENT && lastAnnouncedBand != ForecastBand.IMMINENT.ordinal()) {
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.65f, 1.35f);
        }

        if (phase != lastAnnouncedPhase || phase6Stage != lastAnnouncedPhase6Stage || band != lastAnnouncedBand) {
            lastAnnouncedPhase = phase;
            lastAnnouncedPhase6Stage = phase6Stage;
            lastAnnouncedBand = band;
            setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.frozendawn.phase_barometer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new PhaseBarometerMenu(containerId, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("AnnouncementsInitialized", announcementsInitialized);
        tag.putInt("LastAnnouncedPhase", lastAnnouncedPhase);
        tag.putInt("LastAnnouncedPhase6Stage", lastAnnouncedPhase6Stage);
        tag.putInt("LastAnnouncedBand", lastAnnouncedBand);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        announcementsInitialized = tag.getBoolean("AnnouncementsInitialized");
        lastAnnouncedPhase = tag.getInt("LastAnnouncedPhase");
        lastAnnouncedPhase6Stage = tag.getInt("LastAnnouncedPhase6Stage");
        lastAnnouncedBand = tag.getInt("LastAnnouncedBand");
    }
}
