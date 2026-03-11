package com.frozendawn.entity.ai;

import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Helper for the Architect's BREAK_THROUGH action.
 * Manages progressive block mining with visible cracks, sounds, and tool selection.
 * Not a Goal — called directly from ArchitectEntity's utility AI.
 */
public class ArchitectBlockBreaker {

    private final Monster mob;

    @Nullable
    private BlockPos targetPos;
    private int breakProgress; // 0 to breakTime
    private int breakTime;     // Total ticks to break
    private int lastDestroyStage = -1;
    private int soundCooldown;

    private static final int MAX_BREAK_TICKS = 300; // 15 seconds hard cap
    private static final int IMMUNE_TEST_TICKS = 40; // 2 seconds before giving up on immune blocks
    private static final double REACH = 4.5;

    public ArchitectBlockBreaker(Monster mob) {
        this.mob = mob;
    }

    /**
     * Set the block to mine. Resets progress if target changed.
     */
    public void setTarget(@Nullable BlockPos pos) {
        if (pos == null || !pos.equals(targetPos)) {
            clearDestroyOverlay();
            breakProgress = 0;
            lastDestroyStage = -1;
            soundCooldown = 0;
        }
        targetPos = pos;
        if (pos != null) {
            breakTime = computeBreakTime(pos);
        }
    }

    @Nullable
    public BlockPos getTarget() {
        return targetPos;
    }

    public boolean hasTarget() {
        return targetPos != null;
    }

    /**
     * Tick the mining operation. Returns true when the block breaks.
     */
    public boolean tick() {
        if (targetPos == null) return false;

        Level level = mob.level();
        BlockState state = level.getBlockState(targetPos);

        // Target is already gone
        if (state.isAir()) {
            clearTarget();
            return true;
        }

        // Check reach and LOS
        double distSq = mob.position().distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > REACH * REACH) {
            return false; // Too far — entity should path closer
        }

        // Look at the block
        mob.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        // Update held tool based on block type
        updateToolForBlock(state);

        breakProgress++;

        // Check for immune blocks (acheronite that wasn't caught by cost function)
        if (breakProgress >= IMMUNE_TEST_TICKS && isImmuneBlock(state)) {
            // Mark as immune in the D* Lite pathfinder
            if (mob instanceof com.frozendawn.entity.ArchitectEntity architect) {
                architect.getDStarPathfinder().addImmuneBlock(targetPos);
                architect.getDStarPathfinder().onLocalBlockChanged(targetPos, level);
            }
            clearTarget();
            return false;
        }

        // Play hit sound + swing arm every 4 ticks
        if (soundCooldown <= 0) {
            level.playSound(null, targetPos, ModSounds.ARCHITECT_MINE.get(),
                    SoundSource.HOSTILE, 0.55f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
            mob.swing(InteractionHand.MAIN_HAND);
            soundCooldown = 4;
        } else {
            soundCooldown--;
        }

        // Update destroy overlay (stages 0-9)
        int stage = (int) ((float) breakProgress / breakTime * 10.0f);
        stage = Math.min(stage, 9);
        if (stage != lastDestroyStage) {
            level.destroyBlockProgress(mob.getId(), targetPos, stage);
            lastDestroyStage = stage;
        }

        // Block breaks
        if (breakProgress >= breakTime) {
            level.destroyBlockProgress(mob.getId(), targetPos, -1);
            level.playSound(null, targetPos, ModSounds.ARCHITECT_MINE.get(),
                    SoundSource.HOSTILE, 0.8f, 0.75f + mob.getRandom().nextFloat() * 0.15f);
            level.destroyBlock(targetPos, true);

            // Remove from player-placed tracker
            if (level instanceof ServerLevel serverLevel) {
                MinecraftServer server = serverLevel.getServer();
                PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(server);
                tracker.markRemoved(targetPos);
                grantNearbyBreakAdvancement(serverLevel, targetPos);
            }

            clearTarget();
            return true;
        }

        return false;
    }

    /**
     * Select the appropriate tool based on block tags.
     */
    private void updateToolForBlock(BlockState state) {
        ItemStack tool;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            tool = new ItemStack(Items.WOODEN_AXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            tool = new ItemStack(Items.WOODEN_SHOVEL);
        } else {
            // Default to pickaxe for stone, metal, etc.
            tool = new ItemStack(Items.WOODEN_PICKAXE);
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, tool);
    }

    /**
     * Get the appropriate tool ItemStack for a block without equipping it.
     */
    public static ItemStack getToolForBlock(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return new ItemStack(Items.WOODEN_AXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return new ItemStack(Items.WOODEN_SHOVEL);
        } else {
            return new ItemStack(Items.WOODEN_PICKAXE);
        }
    }

    private int computeBreakTime(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        float hardness = state.getDestroySpeed(mob.level(), pos);
        if (hardness < 0) return MAX_BREAK_TICKS; // Unbreakable — will be caught by immune check
        return Math.min(MAX_BREAK_TICKS, Math.max(40, (int) (hardness * 40)));
    }

    /**
     * Calculate effective break time in seconds for a block, using the
     * Architect's best available tool. Used by A* to set breach cost
     * proportional to actual mining difficulty.
     *
     * Tool selection:
     *   Shovel → dirt, sand, gravel, clay, soul sand, etc.
     *   Pickaxe → stone, cobblestone, bricks, ore, etc.
     *   Axe → wood planks, logs, fences, doors, etc.
     *   Bare hand → glass, glowstone, anything else
     */
    public static float getEffectiveBreakTime(BlockState state, BlockPos pos, net.minecraft.world.level.BlockGetter level) {
        ItemStack shovel = new ItemStack(Items.WOODEN_SHOVEL);
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack axe = new ItemStack(Items.WOODEN_AXE);

        float shovelSpeed = shovel.getDestroySpeed(state);
        float pickaxeSpeed = pickaxe.getDestroySpeed(state);
        float axeSpeed = axe.getDestroySpeed(state);
        float handSpeed = 1.0F; // Bare hand base speed

        float bestSpeed = Math.max(Math.max(shovelSpeed, pickaxeSpeed),
                                   Math.max(axeSpeed, handSpeed));

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0) return 0.1F; // Instant-break blocks

        // getDestroySpeed returns 1.0 for wrong tool, >1.0 for correct
        boolean correctTool = bestSpeed > 1.0F;

        float baseTime;
        if (correctTool) {
            baseTime = (hardness * 1.5F) / bestSpeed;
        } else {
            baseTime = (hardness * 5.0F) / bestSpeed;
        }

        return Math.max(baseTime, 0.05F);
    }

    private boolean isImmuneBlock(BlockState state) {
        return state.is(ModBlocks.ACHERONITE_BLOCK.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                || state.is(ModBlocks.TRANSPONDER.get())
                || state.getDestroySpeed(mob.level(), targetPos) < 0;
    }

    public void clearTarget() {
        clearDestroyOverlay();
        targetPos = null;
        breakProgress = 0;
        lastDestroyStage = -1;
        soundCooldown = 0;
    }

    private void clearDestroyOverlay() {
        if (targetPos != null && lastDestroyStage >= 0) {
            mob.level().destroyBlockProgress(mob.getId(), targetPos, -1);
        }
    }

    /**
     * Call on entity death to clean up destroy overlays.
     */
    public void onDeath() {
        clearDestroyOverlay();
    }

    public boolean isMining() {
        return targetPos != null && breakProgress > 0;
    }

    public float getMiningProgress() {
        if (targetPos == null || breakTime <= 0) return 0f;
        return (float) breakProgress / breakTime;
    }

    private void grantNearbyBreakAdvancement(ServerLevel level, BlockPos brokenPos) {
        if (!(mob instanceof ArchitectEntity)) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()
                    && player.distanceToSqr(
                    brokenPos.getX() + 0.5,
                    brokenPos.getY() + 0.5,
                    brokenPos.getZ() + 0.5) <= 16.0) {
                WorldTickHandler.grantAdvancement(player, "architect_singleplayer");
            }
        }
    }
}
