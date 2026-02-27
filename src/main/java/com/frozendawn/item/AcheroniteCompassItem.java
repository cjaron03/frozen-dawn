package com.frozendawn.item;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.WinConditionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Acheronite Compass: points toward the crashed ORSA satellite.
 * Uses the LodestoneTracker data component so vanilla compass rendering handles the needle.
 * Server-side inventoryTick writes the satellite position into the item's data;
 * if win condition is disabled or position not yet chosen, the tracker is cleared
 * so the compass spins aimlessly (same behavior as compass in the Nether).
 */
public class AcheroniteCompassItem extends Item {

    public AcheroniteCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Only update every 20 ticks to avoid per-tick overhead
        if (level.getGameTime() % 20 != 0) return;

        ServerLevel overworld = serverLevel.getServer().overworld();
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);

        if (!FrozenDawnConfig.ENABLE_WIN_CONDITION.get()) {
            // Win condition disabled — clear tracker so compass spins
            if (current != null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }

        WinConditionState winState = WinConditionState.get(serverLevel.getServer());
        BlockPos satellitePos = winState.getSatellitePos();

        if (satellitePos == null) {
            // No satellite yet — spin
            if (current != null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }

        // Set lodestone tracker pointing to satellite in overworld
        // tracked=false so it doesn't validate a lodestone block at the position
        GlobalPos target = GlobalPos.of(Level.OVERWORLD, satellitePos);
        if (current == null || current.target().isEmpty()
                || !current.target().get().equals(target)) {
            stack.set(DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(Optional.of(target), false));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint when tracking a target
        return stack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(stack);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return super.getDescriptionId(stack);
    }
}
