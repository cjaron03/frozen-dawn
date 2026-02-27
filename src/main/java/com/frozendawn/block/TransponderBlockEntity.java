package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.world.GeothermalCoreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the ORSA Transponder.
 * State machine: IDLE → BROADCASTING → COMPLETE.
 * Broadcast requires a nearby Geothermal Core; pauses if core is removed.
 */
public class TransponderBlockEntity extends BlockEntity implements MenuProvider {

    public static final int STATE_IDLE = 0;
    public static final int STATE_BROADCASTING = 1;
    public static final int STATE_COMPLETE = 2;
    public static final int STATE_PAUSED = 3;

    private static final int CORE_CHECK_RADIUS_SQ = 144; // 12 blocks
    private static final int SHAFT_CHECK_INTERVAL = 100; // ticks between shaft revalidation

    private int broadcastTicksRemaining = 0;
    private int totalBroadcastTicks = 0;
    private boolean shaftClear = false;
    private int shaftCheckCooldown = 0;
    private boolean manuallyPaused = false;

    public TransponderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPONDER.get(), pos, state);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;

        int state = getBlockState().getValue(TransponderBlock.STATE);
        if (state != STATE_BROADCASTING && state != STATE_PAUSED) return;

        // Revalidate conditions every 100 ticks (core + shaft)
        if (shaftCheckCooldown <= 0) {
            clearSnowInShaft();
            shaftClear = checkShaftClear();
            shaftCheckCooldown = SHAFT_CHECK_INTERVAL;
        }
        shaftCheckCooldown--;

        boolean conditionsMet = hasNearbyGeothermalCore() && shaftClear;

        // Auto-pause when conditions fail (overrides manual pause state)
        if (!conditionsMet && state == STATE_BROADCASTING) {
            level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_PAUSED), 3);
            manuallyPaused = false; // this was a condition failure, not manual
            // Beacon deactivate sound
            if (level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return;
        }
        // Auto-resume only if NOT manually paused
        if (conditionsMet && state == STATE_PAUSED && !manuallyPaused) {
            level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_BROADCASTING), 3);
        }
        if (!conditionsMet || manuallyPaused) return;

        broadcastTicksRemaining--;
        if (broadcastTicksRemaining <= 0) {
            // Broadcast complete
            level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_COMPLETE), 3);
            FrozenDawn.LOGGER.info("Transponder broadcast complete at ({}, {}, {})",
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

            // Grant advancement to all online players
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.BLOCKS, 1.0f, 1.0f);
                MinecraftServer server = serverLevel.getServer();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    grantAdvancement(p, "broadcast_complete");
                }
            }
            setChanged();
        } else if (broadcastTicksRemaining % 6000 == 0) {
            // Log progress every 5 minutes
            int minutesLeft = broadcastTicksRemaining / 1200;
            FrozenDawn.LOGGER.debug("Transponder broadcasting: {} minutes remaining", minutesLeft);
        }

        // --- Particle beam + ambient sound while broadcasting ---
        if (state == STATE_BROADCASTING && level instanceof ServerLevel serverLevel) {
            long tick = serverLevel.getGameTime();

            // Particle beam: pulse every 8 ticks
            if (tick % 8 == 0) {
                spawnBeamParticles(serverLevel);
            }

            // Ambient hum: loop every 80 ticks (~4 seconds)
            if (tick % 80 == 0) {
                serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT,
                        SoundSource.BLOCKS, 0.6f, 1.4f);
            }
        }
    }

    /**
     * Spawn a column of particles shooting upward from the transponder.
     * Uses END_ROD for a bright ascending beam visible from far away.
     */
    private void spawnBeamParticles(ServerLevel serverLevel) {
        double cx = worldPosition.getX() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        double baseY = worldPosition.getY() + 1.0;
        int maxY = serverLevel.getMaxBuildHeight();

        // Beam goes all the way to world height (like a beacon)
        for (int y = (int) baseY; y < maxY; y += 3) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    cx, y, cz,
                    1, 0.05, 0.2, 0.05, 0.01);
        }

        // Dense cluster at the base for a glow effect
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                cx, baseY, cz,
                3, 0.15, 0.1, 0.15, 0.02);
    }

    /**
     * Toggle broadcasting on/off (pause/resume). Called from the UI.
     */
    public void togglePause() {
        if (level == null || level.isClientSide()) return;
        int state = getBlockState().getValue(TransponderBlock.STATE);
        if (state == STATE_BROADCASTING) {
            manuallyPaused = true;
            level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_PAUSED), 3);
            if (level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        } else if (state == STATE_PAUSED) {
            manuallyPaused = false;
            if (hasNearbyGeothermalCore() && shaftClear) {
                level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_BROADCASTING), 3);
                if (level instanceof ServerLevel sl) {
                    sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE,
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
        }
    }

    public void tryActivate(ServerPlayer player) {
        if (level == null || level.isClientSide()) return;

        // Block activation entirely when win condition is disabled
        if (!FrozenDawnConfig.ENABLE_WIN_CONDITION.get()) {
            player.sendSystemMessage(Component.translatable("message.frozendawn.transponder.disabled"));
            return;
        }

        int state = getBlockState().getValue(TransponderBlock.STATE);

        // Any non-IDLE state: open the status UI
        if (state != STATE_IDLE) {
            player.openMenu(this, worldPosition);
            return;
        }

        // IDLE — check conditions, activate if all met, otherwise open UI showing requirements
        boolean depthOk = worldPosition.getY() < 0;
        boolean schematicOk = WinConditionState.get(player.getServer()).isSchematicUnlocked();
        boolean coreOk = hasNearbyGeothermalCore();
        boolean shaftOk = checkShaftClear();

        if (depthOk && schematicOk && coreOk && shaftOk) {
            // All conditions met — start broadcasting
            shaftClear = true;
            shaftCheckCooldown = SHAFT_CHECK_INTERVAL;
            totalBroadcastTicks = FrozenDawnConfig.BROADCAST_TICKS.get();
            broadcastTicksRemaining = totalBroadcastTicks;
            level.setBlock(worldPosition, getBlockState().setValue(TransponderBlock.STATE, STATE_BROADCASTING), 3);
            player.sendSystemMessage(Component.translatable("message.frozendawn.transponder.activated",
                    totalBroadcastTicks / 1200));
            FrozenDawn.LOGGER.info("Transponder activated at ({}, {}, {}) — broadcasting for {} ticks",
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), totalBroadcastTicks);
            setChanged();
        } else {
            // Open UI to show what's missing
            player.openMenu(this, worldPosition);
        }
    }

    /**
     * Scan the column above the transponder for any opaque blocks.
     * canSeeSky() uses light levels which spread laterally, so we must
     * walk the column block-by-block instead.
     * Snow layers and snow blocks are ignored (cleared separately by the signal).
     */
    private boolean checkShaftClear() {
        if (level == null) return false;
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos(
                worldPosition.getX(), 0, worldPosition.getZ());
        for (int y = worldPosition.getY() + 1; y < maxY; y++) {
            check.setY(y);
            var state = level.getBlockState(check);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                continue; // snow is cleared by the signal beam
            }
            if (state.canOcclude()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clear snow in the column above the transponder while broadcasting.
     * The signal energy melts any snow accumulation in the shaft.
     */
    private void clearSnowInShaft() {
        if (level == null) return;
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos(
                worldPosition.getX(), 0, worldPosition.getZ());
        for (int y = worldPosition.getY() + 1; y < maxY; y++) {
            check.setY(y);
            var state = level.getBlockState(check);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                level.removeBlock(check, false);
            } else if (!state.isAir()) {
                break; // stop at first non-air, non-snow block
            }
        }
    }

    private boolean hasNearbyGeothermalCore() {
        if (level == null) return false;
        for (BlockPos corePos : GeothermalCoreRegistry.getCores(level)) {
            if (worldPosition.distSqr(corePos) <= CORE_CHECK_RADIUS_SQ) return true;
        }
        return false;
    }

    public int getBroadcastTicksRemaining() { return broadcastTicksRemaining; }
    public int getTotalBroadcastTicks() { return totalBroadcastTicks; }

    /** ContainerData for syncing transponder status to the client UI. */
    public ContainerData getMenuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> getBlockState().getValue(TransponderBlock.STATE);
                    case 1 -> totalBroadcastTicks > 0
                            ? (int) (100f * (totalBroadcastTicks - broadcastTicksRemaining) / totalBroadcastTicks)
                            : 0;
                    case 2 -> broadcastTicksRemaining / 1200;
                    case 3 -> shaftClear ? 1 : 0;
                    case 4 -> hasNearbyGeothermalCore() ? 1 : 0;
                    case 5 -> worldPosition.getY() < 0 ? 1 : 0;
                    case 6 -> {
                        if (level == null || level.getServer() == null) yield 0;
                        yield WinConditionState.get(level.getServer()).isSchematicUnlocked() ? 1 : 0;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return 7; }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.frozendawn.transponder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new TransponderMenu(containerId, this);
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BroadcastRemaining", broadcastTicksRemaining);
        tag.putInt("BroadcastTotal", totalBroadcastTicks);
        tag.putBoolean("ManuallyPaused", manuallyPaused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        broadcastTicksRemaining = tag.getInt("BroadcastRemaining");
        totalBroadcastTicks = tag.getInt("BroadcastTotal");
        manuallyPaused = tag.getBoolean("ManuallyPaused");
    }
}
