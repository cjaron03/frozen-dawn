package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Power limit is local to this heater; the room thermostat still owns the target. */
public final class ResistiveHeaterMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6, LIMIT_BUTTON_BASE = 100;
    private final ResistiveHeaterBlockEntity heater;
    private final BlockPos pos;
    private final ContainerData data = new SimpleContainerData(DATA_COUNT);
    public ResistiveHeaterMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(ModMenuTypes.RESISTIVE_HEATER.get(), id);
        heater = null; pos = buf.readBlockPos();
        data.set(5, ResistiveHeaterBlockEntity.DEFAULT_LIMIT_WATTS / 1000);
        addDataSlots(data);
    }
    public ResistiveHeaterMenu(int id, Inventory inv, ResistiveHeaterBlockEntity h) {
        super(ModMenuTypes.RESISTIVE_HEATER.get(), id);
        heater = h; pos = h.getBlockPos(); addDataSlots(data); refresh();
    }
    public ContainerData data() { return data; }
    /** Menu data travels as signed shorts; 0..50,000 W fits an unsigned short. */
    public int drawWatts() { return data.get(3) & 0xffff; }
    public int powerLimitKw() { return data.get(5); }
    private void refresh() {
        if (heater == null) return;
        data.set(0, heater.status());
        data.set(1, Double.isFinite(heater.temperature())
                ? (int)Math.clamp(Math.round(heater.temperature()*10), -32767, 32767) : -32768);
        data.set(2, (int)Math.round(heater.target()*10));
        data.set(3, heater.drawWatts()); data.set(4, heater.airPresent() ? 1 : 0);
        data.set(5, heater.powerLimitWatts() / 1000);
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player p) {
        return heater != null ? heater.stillValid(p) : !p.isSpectator() && p.level().isLoaded(pos)
                && p.distanceToSqr(pos.getCenter()) <= 64
                && p.level().getBlockState(pos).getBlock() instanceof ResistiveHeaterBlock;
    }
    @Override public boolean clickMenuButton(Player p, int id) {
        if (heater == null || !stillValid(p) || id < LIMIT_BUTTON_BASE
                || id > LIMIT_BUTTON_BASE + ResistiveHeaterBlockEntity.MAX_WATTS / 1000) return false;
        heater.setPowerLimitWatts((id - LIMIT_BUTTON_BASE) * 1000);
        refresh();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
}
