package com.frozendawn.block;

import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.world.RocketLaunchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RocketEngineBlockEntity extends BlockEntity {
    private boolean structureValid;
    private boolean alignedToBlastPit;
    private boolean blueprintUnlocked;
    private boolean initialized;
    private boolean lastStructureValid;
    private int loadedFuelCells;

    public RocketEngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_ENGINE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RocketLaunchStructure.Diagnostic diagnostic = RocketLaunchStructure.diagnose(serverLevel, worldPosition);
        structureValid = diagnostic.valid();
        alignedToBlastPit = diagnostic.atBlastPit();
        blueprintUnlocked = WinConditionState.get(serverLevel.getServer()).isRocketBlueprintUnlocked();

        if (!initialized) {
            initialized = true;
            lastStructureValid = structureValid;
        } else if (structureValid && !lastStructureValid) {
            spawnStructureLockedParticles(serverLevel);
        }
        lastStructureValid = structureValid;

        if (structureValid && alignedToBlastPit && blueprintUnlocked) {
            RocketLaunchManager.tryAssembleFromBlocks(serverLevel, worldPosition, loadedFuelCells);
        }
    }

    public void inspect(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        RocketLaunchStructure.Diagnostic diagnostic = RocketLaunchStructure.diagnose(serverLevel, worldPosition);
        boolean unlocked = WinConditionState.get(serverLevel.getServer()).isRocketBlueprintUnlocked();
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Launch Assembly ---"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Launch Package: "
                + (unlocked ? "\u00A7aUnlocked" : "\u00A7cLocked")));
        player.sendSystemMessage(Component.literal(p + "\u00A77Pad Alignment: "
                + (diagnostic.atBlastPit() ? "\u00A7aCentered" : "\u00A7cOff Pad")));
        player.sendSystemMessage(Component.literal(p + "\u00A77Launch Pad: "
                + (diagnostic.padValid() ? "\u00A7aLocked" : "\u00A7cIncomplete")));
        player.sendSystemMessage(Component.literal(p + "\u00A77Structure: "
                + (diagnostic.valid() ? "\u00A7aLocked" : "\u00A7cInvalid")));
        player.sendSystemMessage(Component.literal(p + "\u00A77Fuel Cells: \u00A7f" + loadedFuelCells + "/6"));
        if (!diagnostic.valid()) {
            player.sendSystemMessage(Component.literal(p + "\u00A77Diag: \u00A7c" + diagnostic.message()));
        }
    }

    private void spawnStructureLockedParticles(ServerLevel serverLevel) {
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 0.6D;
        double z = worldPosition.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 12, 0.25D, 0.2D, 0.25D, 0.02D);
        serverLevel.sendParticles(ParticleTypes.END_ROD, x, y + 0.3D, z, 8, 0.18D, 0.2D, 0.18D, 0.0D);
    }

    public boolean isStructureValid() {
        return structureValid;
    }

    public boolean isAlignedToBlastPit() {
        return alignedToBlastPit;
    }

    public boolean isBlueprintUnlocked() {
        return blueprintUnlocked;
    }

    public int getLoadedFuelCells() {
        return loadedFuelCells;
    }

    public void setLoadedFuelCells(int loadedFuelCells) {
        this.loadedFuelCells = loadedFuelCells;
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadedFuelCells = tag.getInt("loadedFuelCells");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("loadedFuelCells", loadedFuelCells);
    }
}
