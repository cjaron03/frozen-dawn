package com.frozendawn.compat.curios;

import com.frozendawn.init.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.function.Predicate;

final class CuriosLoadedAccess implements CuriosAccess {

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new SnowshoesCurio(stack), ModItems.SNOWSHOES.get());
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new BasicCurio(stack), ModItems.BLIZZARD_GOGGLES.get());
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new BasicCurio(stack), ModItems.ICE_CLAWS.get());
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new HelmetMountCurio(stack), ModItems.SUIT_HEADLAMP.get());
    }

    @Override
    public boolean isItemEquipped(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.isEquipped(item))
                .orElse(false);
    }

    @Override
    public boolean isItemEquipped(Player player, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.isEquipped(filter))
                .orElse(false);
    }

    @Override
    public ItemStack helmetMount(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.getStacksHandler("helmet_mount"))
                .filter(handler -> handler.getStacks().getSlots() > 0
                        && !handler.getActiveStates().isEmpty() && handler.getActiveStates().getFirst())
                .map(handler -> handler.getStacks().getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
    }

    private record HelmetMountCurio(ItemStack stack) implements ICurio {
        @Override public ItemStack getStack() { return stack; }
        @Override public boolean canEquip(SlotContext context) {
            return context.identifier().equals("helmet_mount") && context.index() == 0 && !context.cosmetic();
        }
        @Override public boolean canEquipFromUse(SlotContext context) { return true; }
    }

    private record SnowshoesCurio(ItemStack stack) implements ICurio {

        @Override
        public ItemStack getStack() {
            return this.stack;
        }

        @Override
        public boolean canEquipFromUse(SlotContext slotContext) {
            return true;
        }

        @Override
        public boolean canWalkOnPowderedSnow(SlotContext slotContext) {
            return true;
        }
    }

    private record BasicCurio(ItemStack stack) implements ICurio {

        @Override
        public ItemStack getStack() {
            return this.stack;
        }

        @Override
        public boolean canEquipFromUse(SlotContext slotContext) {
            return true;
        }
    }
}
