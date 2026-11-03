package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Remembers when a placed pot goes cold, so each bowl inherits the pot's heat. */
public class SeafoodBoilBlockEntity extends BlockEntity {

    private long steamingUntil;

    public SeafoodBoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEAFOOD_BOIL.get(), pos, state);
    }

    public long getSteamingUntil() {
        return steamingUntil;
    }

    public void setSteamingUntil(long steamingUntil) {
        this.steamingUntil = steamingUntil;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("SteamingUntil", steamingUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steamingUntil = tag.getLong("SteamingUntil");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder out) {
        super.collectImplicitComponents(out);
        if (level != null && level.getGameTime() < steamingUntil) out.set(ModDataComponents.STEAMING_UNTIL.get(), steamingUntil);
    }
}
