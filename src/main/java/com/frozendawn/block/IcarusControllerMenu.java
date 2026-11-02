package com.frozendawn.block;

import com.frozendawn.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The AMR-1 Icarus Controller screen's data. No slots: capsules and matter go in their own ports. Buttons are the
 * controller's BUTTON_ ids; the settings sliders travel in IcarusSettingsPayload. */
public final class IcarusControllerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256, HEIGHT = 222;
    private final ContainerData data;
    private final @Nullable IcarusControllerBlockEntity entity;
    private final @Nullable BlockPos pos;

    public IcarusControllerMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, new SimpleContainerData(IcarusControllerBlockEntity.DATA), null, buf == null ? null : buf.readBlockPos());
    }
    public IcarusControllerMenu(int id, Inventory inv, IcarusControllerBlockEntity entity) { this(id, entity.data(), entity, entity.getBlockPos()); }
    private IcarusControllerMenu(int id, ContainerData data, @Nullable IcarusControllerBlockEntity entity, @Nullable BlockPos pos) {
        super(ModMenuTypes.ICARUS_CONTROLLER.get(), id);
        this.data = data; this.entity = entity; this.pos = pos;
        addDataSlots(data);
    }
    public ContainerData data() { return data; }
    public @Nullable BlockPos pos() { return pos; }
    public @Nullable IcarusControllerBlockEntity entity() { return entity; }
    public boolean flag(int bit) { return (data.get(IcarusControllerBlockEntity.FLAGS) & bit) != 0; }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (entity == null || player.isSpectator() || !stillValid(player)) return false;
        if (id == IcarusControllerBlockEntity.BUTTON_BLUEPRINT) {
            if (player instanceof ServerPlayer server) IcarusControllerBlock.blueprint(server, entity, entity.toggleBlueprint(server.getUUID()));
            return true;
        }
        return entity.press(id);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return entity == null || entity.stillValid(player); }
}
