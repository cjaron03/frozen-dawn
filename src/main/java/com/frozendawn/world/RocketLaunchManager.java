package com.frozendawn.world;

import com.frozendawn.block.RocketLaunchStructure;
import com.frozendawn.block.LaunchPadBlock;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.LaunchSequencePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class RocketLaunchManager {
    private RocketLaunchManager() {
    }

    private static boolean allowRepeatLaunchesForTesting() {
        return FrozenDawnConfig.TEST_ALLOW_REPEAT_LAUNCHES.get();
    }

    public static void tick(ServerLevel level) {
        WinConditionState state = WinConditionState.get(level.getServer());
        refreshPadStates(level);
        if (state.isLaunchCompleted() && !allowRepeatLaunchesForTesting()) {
            return;
        }
        ensureRocketPresent(level, state);
        removeDuplicateRockets(level, state.getRocketPadCenter());
        if (state.isLaunchInProgress()) {
            for (ServerPlayer player : level.players()) {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
        }
    }

    public static boolean tryAssembleFromBlocks(ServerLevel level, BlockPos enginePos, int preloadedFuel) {
        WinConditionState state = WinConditionState.get(level.getServer());
        if ((state.isLaunchCompleted() && !allowRepeatLaunchesForTesting())
                || state.isRocketAssembled()
                || !state.isRocketBlueprintUnlocked()) {
            return false;
        }

        RocketLaunchStructure.Diagnostic diagnostic = RocketLaunchStructure.diagnose(level, enginePos);
        if (!diagnostic.valid()) {
            return false;
        }

        BlockPos padCenter = enginePos.below();
        removeDuplicateRockets(level, padCenter);
        for (BlockPos pos : RocketLaunchStructure.rocketBlockPositions(enginePos)) {
            level.removeBlock(pos, false);
        }

        RocketLaunchEntity rocket = new RocketLaunchEntity(level, padCenter);
        rocket.setFuelCells(preloadedFuel);
        level.addFreshEntity(rocket);
        spawnAssemblyBurst(level, padCenter);

        state.setRocketPadCenter(padCenter);
        state.setRocketAssembled(true);
        state.setRocketFuelCellsLoaded(rocket.getFuelCells());
        state.setLaunchInProgress(false);
        state.setLaunchSequenceStartTick(0L);
        return true;
    }

    public static InteractionResult handlePadEmptyUse(ServerPlayer player, BlockPos clickedPos) {
        ServerLevel level = player.serverLevel();
        RocketLaunchStructure.PadDiagnostic padDiagnostic = RocketLaunchStructure.diagnosePad(level, clickedPos);
        if (!padDiagnostic.valid()) {
            player.sendSystemMessage(orsa(padDiagnostic.message(), 'c'));
            return InteractionResult.CONSUME;
        }

        BlockPos padCenter = RocketLaunchStructure.getExpectedPadCenter(level);
        RocketLaunchEntity rocket = findRocket(level, padCenter);
        if (rocket != null) {
            if (player.isShiftKeyDown()) {
                return disassemble(level, rocket, player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
            }
            if (rocket.isIdle()) {
                player.sendSystemMessage(orsa("Board the rocket and press Jump to arm launch.", 'e'));
                return InteractionResult.CONSUME;
            }
            rocket.showStatus(player);
            return InteractionResult.CONSUME;
        }

        player.sendSystemMessage(orsa("Launch pad locked. Build the rocket above the center plate.", 'a'));
        return InteractionResult.CONSUME;
    }

    public static ItemInteractionResult handlePadItemUse(ServerPlayer player, BlockPos clickedPos, ItemStack stack, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        RocketLaunchStructure.PadDiagnostic padDiagnostic = RocketLaunchStructure.diagnosePad(level, clickedPos);
        if (!padDiagnostic.valid()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        RocketLaunchEntity rocket = findRocket(level, RocketLaunchStructure.getExpectedPadCenter(level));
        if (rocket == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult result = handleRocketInteraction(player, rocket, stack, hand);
        return result.consumesAction() ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.FAIL;
    }

    public static InteractionResult handleRocketInteraction(ServerPlayer player, RocketLaunchEntity rocket, ItemStack stack, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        WinConditionState state = WinConditionState.get(level.getServer());

        if (rocket.isLaunching()) {
            rocket.showStatus(player);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (player.getVehicle() == rocket) {
                player.stopRiding();
                rocket.clearLaunchConfirmation();
                player.sendSystemMessage(orsa("Disembarked from the launch vehicle.", 'e'));
                return InteractionResult.CONSUME;
            }
            return disassemble(level, rocket, player) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }

        if (stack.is(ModItems.ROCKET_FUEL_CELL.get())) {
            if (rocket.getFuelCells() >= 6) {
                player.sendSystemMessage(orsa("Rocket fuel cells loaded: 6/6.", 'a'));
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            rocket.setFuelCells(rocket.getFuelCells() + 1);
            level.playSound(null, rocket.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.8F);
            player.sendSystemMessage(orsa("Rocket fuel cells loaded: " + rocket.getFuelCells() + "/6.", 'e'));
            return InteractionResult.CONSUME;
        }

        if (!state.isRocketBlueprintUnlocked()) {
            player.sendSystemMessage(orsa("Martian Command has not cleared the launch package yet.", 'c'));
            return InteractionResult.FAIL;
        }

        if (stack.isEmpty()) {
            if (player.getVehicle() == rocket) {
                player.sendSystemMessage(orsa("Press Jump to arm launch. Hold sneak to disembark.", 'e'));
                return InteractionResult.CONSUME;
            }
            if (!rocket.getPassengers().isEmpty()) {
                player.sendSystemMessage(orsa("Cockpit occupied.", 'c'));
                return InteractionResult.CONSUME;
            }
            if (player.startRiding(rocket, false)) {
                rocket.clearLaunchConfirmation();
                level.playSound(null, rocket.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.9F);
                player.sendSystemMessage(orsa("Boarded launch vehicle. Press Jump to arm launch.", 'e'));
                return InteractionResult.CONSUME;
            }
            player.sendSystemMessage(orsa("Unable to board launch vehicle.", 'c'));
            return InteractionResult.FAIL;
        }

        rocket.showStatus(player);
        return InteractionResult.CONSUME;
    }

    public static void handleLaunchJump(ServerPlayer player) {
        if (!(player.getVehicle() instanceof RocketLaunchEntity rocket)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        WinConditionState state = WinConditionState.get(level.getServer());

        if (rocket.isLaunching()) {
            return;
        }
        if (!state.isRocketBlueprintUnlocked()) {
            player.sendSystemMessage(orsa("Launch package still locked.", 'c'));
            return;
        }
        if (state.isLaunchCompleted() && !allowRepeatLaunchesForTesting()) {
            player.sendSystemMessage(orsa("This world's launch package has already been used.", 'c'));
            return;
        }
        if (rocket.getFuelCells() < 6) {
            player.sendSystemMessage(orsa("Load 6 Rocket Fuel Cells before launch.", 'c'));
            return;
        }

        long gameTime = level.getGameTime();
        if (!rocket.isLaunchConfirmationArmed(player, gameTime)) {
            rocket.armLaunchConfirmation(player, gameTime + 80L);
            level.playSound(null, rocket.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
            player.sendSystemMessage(orsa("After this, there is no going back. Press Jump again to launch.", '6'));
            return;
        }

        tryStartLaunch(level, rocket, player);
    }

    public static boolean tryStartLaunch(ServerLevel level, RocketLaunchEntity rocket, ServerPlayer initiator) {
        WinConditionState state = WinConditionState.get(level.getServer());
        BlockPos padCenter = rocket.getPadCenter();
        RocketLaunchStructure.PadDiagnostic padDiagnostic = RocketLaunchStructure.diagnosePad(level, padCenter);
        if (!padDiagnostic.valid()) {
            initiator.sendSystemMessage(orsa(padDiagnostic.message(), 'c'));
            return false;
        }
        if (!state.isRocketBlueprintUnlocked()) {
            initiator.sendSystemMessage(orsa("Launch package still locked.", 'c'));
            return false;
        }
        if (state.isLaunchCompleted() && !allowRepeatLaunchesForTesting()) {
            initiator.sendSystemMessage(orsa("This world's launch package has already been used.", 'c'));
            return false;
        }
        if (rocket.getFuelCells() < 6) {
            initiator.sendSystemMessage(orsa("Load 6 Rocket Fuel Cells before launch.", 'c'));
            return false;
        }

        rocket.clearLaunchConfirmation();
        rocket.setFuelCells(0);
        rocket.beginLaunch(level.getGameTime());
        state.setRocketAssembled(true);
        state.setRocketPadCenter(padCenter);
        state.setRocketFuelCellsLoaded(0);
        level.playSound(null, padCenter, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.4F, 0.55F);
        LaunchSequencePayload payload = new LaunchSequencePayload(
                rocket.getId(),
                padCenter,
                RocketLaunchEntity.COUNTDOWN_TICKS,
                RocketLaunchEntity.LIFTOFF_TRACK_TICKS,
                RocketLaunchEntity.ASCENT_TICKS,
                RocketLaunchEntity.ATMOSPHERE_EXIT_TICKS,
                RocketLaunchEntity.FADE_TICKS);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        return true;
    }

    public static boolean disassemble(ServerLevel level, RocketLaunchEntity rocket, ServerPlayer player) {
        if (rocket.isLaunching()) {
            player.sendSystemMessage(orsa("Launch sequence already active.", 'c'));
            return false;
        }
        if (!rocket.getPassengers().isEmpty()) {
            player.sendSystemMessage(orsa("Passenger aboard. Clear the cockpit before disassembly.", 'c'));
            return false;
        }

        giveOrDrop(player, new ItemStack(ModItems.ROCKET_ENGINE.get()));
        giveOrDrop(player, new ItemStack(ModItems.ROCKET_FIN.get(), 4));
        giveOrDrop(player, new ItemStack(ModItems.ROCKET_HULL.get(), 4));
        giveOrDrop(player, new ItemStack(ModItems.ROCKET_NOSE_CONE.get()));
        if (rocket.getFuelCells() > 0) {
            giveOrDrop(player, new ItemStack(ModItems.ROCKET_FUEL_CELL.get(), rocket.getFuelCells()));
        }
        level.playSound(null, rocket.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.75F);
        rocket.discard();
        WinConditionState.get(level.getServer()).clearRocketAssembly();
        player.sendSystemMessage(orsa("Launch vehicle disassembled.", 'e'));
        return true;
    }

    public static void finishLaunch(ServerLevel level, RocketLaunchEntity rocket) {
        WinConditionState state = WinConditionState.get(level.getServer());
        BlockPos padCenter = rocket.getPadCenter();
        for (var passenger : List.copyOf(rocket.getPassengers())) {
            passenger.stopRiding();
            if (padCenter != null && !padCenter.equals(BlockPos.ZERO)) {
                passenger.teleportTo(padCenter.getX() + 0.5D, padCenter.getY() + 1.05D, padCenter.getZ() + 0.5D);
            }
            passenger.setDeltaMovement(Vec3.ZERO);
            passenger.hurtMarked = true;
        }
        state.setLaunchCompleted(!allowRepeatLaunchesForTesting());
        state.setLaunchInProgress(false);
        state.setRocketAssembled(false);
        state.setRocketFuelCellsLoaded(0);
        state.setLaunchSequenceStartTick(0L);
        level.playSound(null, rocket.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
        rocket.setLaunchState(RocketLaunchEntity.STATE_FINISHED);
        rocket.discard();
    }

    public static void spawnLaunchParticles(ServerLevel level, RocketLaunchEntity rocket) {
        BlockPos padCenter = rocket.getPadCenter();
        if (padCenter == null) {
            return;
        }
        double x = padCenter.getX() + 0.5D;
        double y = padCenter.getY() + 1.05D + rocket.getScriptedYOffset(0.0F);
        double z = padCenter.getZ() + 0.5D;
        int ticks = rocket.getSequenceTicks();

        if (ticks < RocketLaunchEntity.COUNTDOWN_TICKS) {
            int dense = ticks >= 100 ? 12 : 6;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, dense, 0.45D, 0.18D, 0.45D, 0.04D + ticks * 0.00025D);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 0.2D, z, ticks >= 100 ? 6 : 2, 0.25D, 0.12D, 0.25D, 0.02D);
            if (ticks >= 100) {
                level.sendParticles(ParticleTypes.FLAME, x, y - 0.55D, z, 8, 0.35D, 0.08D, 0.35D, 0.01D);
            }
            return;
        }

        int postCountdown = ticks - RocketLaunchEntity.COUNTDOWN_TICKS;
        if (postCountdown < RocketLaunchEntity.LIFTOFF_TRACK_TICKS + 50) {
            level.sendParticles(ParticleTypes.FLAME, x, y - 0.7D, z, 28, 0.46D, 0.15D, 0.46D, 0.055D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y - 0.1D, z, 20, 0.6D, 0.24D, 0.6D, 0.08D);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 10, 0.4D, 0.2D, 0.4D, 0.05D);
            return;
        }

        if (postCountdown < RocketLaunchEntity.LIFTOFF_TRACK_TICKS + RocketLaunchEntity.ASCENT_TICKS) {
            level.sendParticles(ParticleTypes.FLAME, x, y - 0.65D, z, 18, 0.34D, 0.1D, 0.34D, 0.04D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y - 0.05D, z, 6, 0.32D, 0.12D, 0.32D, 0.03D);
            return;
        }

        level.sendParticles(ParticleTypes.FLAME, x, y - 0.55D, z, 10, 0.22D, 0.08D, 0.22D, 0.025D);
    }

    private static void ensureRocketPresent(ServerLevel level, WinConditionState state) {
        if (!state.isRocketAssembled()) {
            return;
        }
        BlockPos padCenter = state.getRocketPadCenter();
        if (padCenter == null) {
            padCenter = RocketLaunchStructure.getExpectedPadCenter(level);
            if (padCenter == null) {
                return;
            }
            state.setRocketPadCenter(padCenter);
        }
        if (findRocket(level, padCenter) != null) {
            return;
        }

        RocketLaunchEntity rocket = new RocketLaunchEntity(level, padCenter);
        rocket.setFuelCells(state.getRocketFuelCellsLoaded());
        if (state.isLaunchInProgress()) {
            rocket.setLaunchState(RocketLaunchEntity.STATE_LAUNCHING);
            int elapsed = (int) Math.max(0L, level.getGameTime() - state.getLaunchSequenceStartTick());
            rocket.setSequenceTicks(Math.min(RocketLaunchEntity.getTotalSequenceTicks(), elapsed));
        }
        level.addFreshEntity(rocket);
    }

    private static void removeDuplicateRockets(ServerLevel level, BlockPos padCenter) {
        if (padCenter == null) {
            return;
        }
        AABB box = new AABB(padCenter).inflate(2.5D, 10.0D, 2.5D);
        List<RocketLaunchEntity> rockets = level.getEntitiesOfClass(RocketLaunchEntity.class, box,
                rocket -> rocket.getPadCenter().equals(padCenter));
        if (rockets.size() <= 1) {
            return;
        }

        RocketLaunchEntity keeper = rockets.stream()
                .filter(RocketLaunchEntity::isLaunching)
                .findFirst()
                .orElseGet(() -> rockets.stream()
                        .filter(rocket -> !rocket.getPassengers().isEmpty())
                        .findFirst()
                        .orElse(rockets.getFirst()));
        int fuelCells = rockets.stream()
                .mapToInt(RocketLaunchEntity::getFuelCells)
                .max()
                .orElse(keeper.getFuelCells());
        keeper.setFuelCells(Math.max(keeper.getFuelCells(), fuelCells));

        for (RocketLaunchEntity rocket : rockets) {
            if (rocket == keeper) {
                continue;
            }
            for (var passenger : List.copyOf(rocket.getPassengers())) {
                passenger.stopRiding();
            }
            rocket.discard();
        }
    }

    public static RocketLaunchEntity findRocket(ServerLevel level, BlockPos padCenter) {
        if (padCenter == null) {
            return null;
        }
        AABB box = new AABB(padCenter).inflate(2.5D, 10.0D, 2.5D);
        List<RocketLaunchEntity> rockets = level.getEntitiesOfClass(RocketLaunchEntity.class, box,
                rocket -> rocket.getPadCenter().equals(padCenter));
        return rockets.isEmpty() ? null : rockets.getFirst();
    }

    private static void refreshPadStates(ServerLevel level) {
        BlockPos center = RocketLaunchStructure.getExpectedPadCenter(level);
        if (center == null) {
            return;
        }
        RocketLaunchStructure.PadDiagnostic diagnostic = RocketLaunchStructure.diagnosePad(level, center);
        boolean formed = diagnostic.valid();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (!level.getBlockState(pos).is(ModBlocks.LAUNCH_PAD.get())) {
                    continue;
                }
                LaunchPadBlock.PadSection xSection = dx < 0 ? LaunchPadBlock.PadSection.WEST
                        : dx > 0 ? LaunchPadBlock.PadSection.EAST
                        : LaunchPadBlock.PadSection.CENTER;
                LaunchPadBlock.PadSection zSection = dz < 0 ? LaunchPadBlock.PadSection.WEST
                        : dz > 0 ? LaunchPadBlock.PadSection.EAST
                        : LaunchPadBlock.PadSection.CENTER;
                var current = level.getBlockState(pos);
                var updated = current
                        .setValue(LaunchPadBlock.FORMED, formed)
                        .setValue(LaunchPadBlock.PAD_X, xSection)
                        .setValue(LaunchPadBlock.PAD_Z, zSection);
                if (!updated.equals(current)) {
                    level.setBlock(pos, updated, 18);
                }
            }
        }
    }

    private static void spawnAssemblyBurst(ServerLevel level, BlockPos padCenter) {
        double x = padCenter.getX() + 0.5D;
        double y = padCenter.getY() + 1.1D;
        double z = padCenter.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 24, 0.65D, 0.5D, 0.65D, 0.05D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.25D, z, 14, 0.45D, 0.45D, 0.45D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3D, z, 6, 0.25D, 0.15D, 0.25D, 0.01D);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static Component orsa(String message, char colorCode) {
        return Component.literal("\u00A77[\u00A76ORSA\u00A77] \u00A7" + colorCode + message);
    }
}
