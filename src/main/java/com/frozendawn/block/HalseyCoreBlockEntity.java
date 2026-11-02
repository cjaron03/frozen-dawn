package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What the client draws at the Halsey Core: the sphere, shield and alarm glow. Visual state only; the controller owns the
 * simulation and pushes this once a second when it changes. Never saved: the controller republishes it after a load. */
public final class HalseyCoreBlockEntity extends BlockEntity {
    /** coreC in whole degrees and shield and gross in hundredths, to bound packets; stepLeft is the seconds left in a timed step. */
    public record Visual(IcarusPhysics.State state, int coreC, double shieldPct, double grossMw, IcarusPhysics.Alarm alarm, int stepLeft) {
        public static final Visual COLD = new Visual(IcarusPhysics.State.COLD, (int) IcarusPhysics.AMBIENT_C, 0, 0, IcarusPhysics.Alarm.NONE, 0);
    }
    private Visual visual = Visual.COLD;

    public HalseyCoreBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.HALSEY_CORE.get(), p, s); }
    public Visual visual() { return visual; }
    public void publish(ServerLevel l, Visual next) {
        if (next.equals(visual)) return;
        visual = next; l.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        boolean hidden = IcarusLook.coreHidden(next.state());
        // Same block, new state: the block entity stays and IcarusPartBlock does not count it as a removed part.
        if (getBlockState().hasProperty(HalseyCoreBlock.HIDDEN) && getBlockState().getValue(HalseyCoreBlock.HIDDEN) != hidden)
            l.setBlock(worldPosition, getBlockState().setValue(HalseyCoreBlock.HIDDEN, hidden), Block.UPDATE_ALL);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        var t = new CompoundTag();
        t.putInt("State", visual.state().ordinal()); t.putInt("CoreC", visual.coreC()); t.putDouble("Shield", visual.shieldPct());
        t.putDouble("Gross", visual.grossMw()); t.putInt("Alarm", visual.alarm().ordinal()); t.putInt("StepLeft", visual.stepLeft());
        return t;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag t, HolderLookup.Provider r) {
        var states = IcarusPhysics.State.values(); var alarms = IcarusPhysics.Alarm.values();
        visual = new Visual(states[Math.clamp(t.getInt("State"), 0, states.length - 1)], t.getInt("CoreC"), t.getDouble("Shield"), t.getDouble("Gross"),
                alarms[Math.clamp(t.getInt("Alarm"), 0, alarms.length - 1)], t.getInt("StepLeft"));
    }
    @Override public void onDataPacket(Connection c, ClientboundBlockEntityDataPacket p, HolderLookup.Provider r) { handleUpdateTag(p.getTag(), r); }
}
