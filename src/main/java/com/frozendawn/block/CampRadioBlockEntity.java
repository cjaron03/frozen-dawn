package com.frozendawn.block;

import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.RadioTransmissionPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the ORSA camp radio. Handles:
 * - Ambient static sounds when players are nearby
 * - Interaction cooldown
 * - TTS transmission sequencing via {@link RadioTransmissionPlayer}
 * - Functional/broken determination (30% functional, deterministic per position)
 */
public class CampRadioBlockEntity extends BlockEntity {

    private static final int INTERACTION_COOLDOWN_TICKS = 20 * 30;
    private static final int AMBIENT_INTERVAL_MIN = 200;
    private static final int AMBIENT_INTERVAL_RANGE = 400;
    private static final double AMBIENT_RANGE_SQ = 24.0 * 24.0;
    private static final double INTERACT_RANGE_SQ = 3.5 * 3.5;

    private long lastInteractionTick;
    private int ambientCountdown;
    private Boolean functional;
    private RadioTransmissionPlayer activeTransmission;

    public CampRadioBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMP_RADIO.get(), pos, state);
        ambientCountdown = 100;
    }

    public boolean isFunctional() {
        if (functional == null) {
            long hash = worldPosition.asLong() * 6364136223846793005L + 1442695040888963407L;
            functional = Math.floorMod(hash >> 16, 100) < 30;
        }
        return functional;
    }

    public void interact(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double dx = player.getX() - (worldPosition.getX() + 0.5);
        double dz = player.getZ() - (worldPosition.getZ() + 0.5);
        if (dx * dx + dz * dz > INTERACT_RANGE_SQ) {
            player.displayClientMessage(Component.literal("Move closer to the radio."), true);
            return;
        }

        long currentTick = serverLevel.getGameTime();
        if (currentTick - lastInteractionTick < INTERACTION_COOLDOWN_TICKS) {
            int remaining = (int) ((INTERACTION_COOLDOWN_TICKS - (currentTick - lastInteractionTick)) / 20);
            player.displayClientMessage(Component.literal("Radio cycling... " + remaining + "s"), true);
            return;
        }

        if (activeTransmission != null && !activeTransmission.isDone()) {
            player.displayClientMessage(Component.literal("Transmission in progress..."), true);
            return;
        }

        lastInteractionTick = currentTick;

        BlockPos towerPos = null;
        if (isFunctional()) {
            OrsaStructureState state = OrsaStructureState.get(serverLevel.getServer());
            OrsaStructureState.TowerRecord tower = state.getNearestTower(worldPosition);
            if (tower != null) {
                towerPos = tower.anchorPos();
            }
        }

        activeTransmission = new RadioTransmissionPlayer(
                serverLevel, worldPosition, player, isFunctional(), towerPos);

        serverLevel.playSound(null, worldPosition, ModSounds.RADIO_STATIC_BURST.get(),
                SoundSource.BLOCKS, 0.6f, 0.8f);

        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Drive active transmission
        if (activeTransmission != null) {
            activeTransmission.tick();
            if (activeTransmission.isDone()) {
                if (activeTransmission.wasSuccessful()) {
                    ServerPlayer player = activeTransmission.getPlayer(serverLevel);
                    if (player != null) {
                        WorldTickHandler.grantAdvancement(player, "found_camp_radio");
                    }
                }
                activeTransmission = null;
            }
        }

        // Ambient static
        if (--ambientCountdown <= 0) {
            ambientCountdown = AMBIENT_INTERVAL_MIN
                    + serverLevel.getRandom().nextInt(AMBIENT_INTERVAL_RANGE);

            boolean hasNearbyPlayer = false;
            for (ServerPlayer player : serverLevel.players()) {
                if (player.blockPosition().distSqr(worldPosition) <= AMBIENT_RANGE_SQ) {
                    hasNearbyPlayer = true;
                    break;
                }
            }

            if (hasNearbyPlayer) {
                serverLevel.playSound(null, worldPosition, ModSounds.RADIO_STATIC_AMBIENT.get(),
                        SoundSource.BLOCKS, 0.2f,
                        0.8f + serverLevel.getRandom().nextFloat() * 0.4f);

                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                        worldPosition.getZ() + 0.5,
                        1, 0.1, 0.05, 0.1, 0.01);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("lastInteraction", lastInteractionTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastInteractionTick = tag.getLong("lastInteraction");
        functional = null;
        activeTransmission = null;
    }
}
