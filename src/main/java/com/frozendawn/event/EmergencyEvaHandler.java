package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.EmergencyEvaState;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModArmorMaterials;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.EmergencyEvaArmorItem;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.network.EmergencyEvaPayload;
import com.frozendawn.phase.PhaseManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class EmergencyEvaHandler {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private EmergencyEvaHandler() {}

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        removeCarriedEmergencyGear(player);
        var apocalypse = ApocalypseState.get(player.getServer());
        if (!PhaseManager.isVacuumActive(apocalypse.getPhase(), apocalypse.getProgress())
                || player.isCreative() || player.isSpectator()) {
            sync(player);
            return;
        }
        // A complete retained ordinary rig with air takes priority (keepInventory).
        if (MobFreezeHandler.getFullSetTier(player) == 3 && hasOrdinaryOxygen(player)) {
            sync(player);
            return;
        }
        issueKit(player);
        PlayerTickHandler.stabilizeAfterRescue(player);
    }

    /** Assign a fresh lease before equipping. All older stored or transferred pieces are inert. */
    public static void issueKit(ServerPlayer player) {
        var state = new EmergencyEvaState(UUID.randomUUID(), EmergencyEvaState.SERVICE_TICKS);
        player.setData(ModAttachments.EMERGENCY_EVA, state);
        for (var slot : SLOTS) {
            ItemStack retained = player.getItemBySlot(slot);
            if (isOrdinaryEva(retained) || (slot == EquipmentSlot.HEAD
                    && retained.is(ModItems.ORSA_THERMAL_VISOR.get()))) continue;
            equip(player, slot, state);
        }
        // A complete regular rig without air needs the emergency chest's internal reserve.
        if (!isWearingIssuedPiece(player)) equip(player, EquipmentSlot.CHEST, state);
        sync(player);
        FrozenDawn.LOGGER.info("[EmergencyEVA] Issued ten-minute recovery kit to {}", player.getGameProfile().getName());
    }

    private static void equip(ServerPlayer player, EquipmentSlot slot, EmergencyEvaState state) {
        var previous = player.getItemBySlot(slot);
        player.setItemSlot(slot, ItemStack.EMPTY);
        if (!previous.isEmpty() && !(previous.getItem() instanceof EmergencyEvaArmorItem)
                && !player.getInventory().add(previous)) player.drop(previous, false);
        var item = switch (slot) {
            case HEAD -> ModItems.EMERGENCY_EVA_HELMET.get();
            case CHEST -> ModItems.EMERGENCY_EVA_CHESTPLATE.get();
            case LEGS -> ModItems.EMERGENCY_EVA_LEGGINGS.get();
            case FEET -> ModItems.EMERGENCY_EVA_BOOTS.get();
            default -> throw new IllegalArgumentException("Not an armor slot");
        };
        var piece = new ItemStack(item);
        piece.set(ModDataComponents.EMERGENCY_EVA_ISSUE, state.issue());
        player.setItemSlot(slot, piece);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().removeData(ModAttachments.EMERGENCY_EVA);
        } else if (event.getEntity() instanceof ServerPlayer player) {
            var previous = event.getOriginal().getData(ModAttachments.EMERGENCY_EVA);
            player.setData(ModAttachments.EMERGENCY_EVA,
                    new EmergencyEvaState(previous.issue(), previous.remainingTicks()));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player) {
            event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof EmergencyEvaArmorItem);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player);
    }

    public static void tick(ServerPlayer player) {
        var state = player.getData(ModAttachments.EMERGENCY_EVA);
        int mask = wornMask(player);
        int previousTicks = state.remainingTicks();
        if (mask != 0 && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            state.tickWorn();
        }
        if (state.equipmentChanged(mask) || (mask != 0
                && (player.tickCount % 20 == 0 || previousTicks > 0 && state.remainingTicks() == 0))) sync(player);
    }

    public static boolean matchesIssue(Player player, ItemStack piece) {
        var state = player.getData(ModAttachments.EMERGENCY_EVA);
        return piece.getItem() instanceof EmergencyEvaArmorItem
                && !state.issue().equals(EmergencyEvaState.NO_ISSUE)
                && state.issue().equals(piece.get(ModDataComponents.EMERGENCY_EVA_ISSUE));
    }

    public static boolean isActivePiece(Player player, ItemStack piece) {
        return matchesIssue(player, piece) && player.getData(ModAttachments.EMERGENCY_EVA).remainingTicks() > 0;
    }

    private static int wornMask(Player player) {
        int mask = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (matchesIssue(player, player.getItemBySlot(SLOTS[i]))) mask |= 1 << i;
        }
        return mask;
    }

    public static boolean isWearingIssuedPiece(Player player) { return wornMask(player) != 0; }
    public static int remainingTicks(Player player) { return player.getData(ModAttachments.EMERGENCY_EVA).remainingTicks(); }
    public static boolean hasLifeSupport(Player player) {
        return isWearingIssuedPiece(player) && remainingTicks(player) > 0
                && MobFreezeHandler.getFullSetTier(player) == 3;
    }
    public static boolean isOrdinaryEva(ItemStack piece) {
        return piece.getItem() instanceof ArmorItem armor && armor.getMaterial() == ModArmorMaterials.EVA;
    }
    private static boolean hasOrdinaryOxygen(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof O2TankItem
                    && stack.getOrDefault(ModDataComponents.O2_LEVEL, 0) > 0) return true;
        }
        return false;
    }
    private static void removeCarriedEmergencyGear(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof EmergencyEvaArmorItem)
                player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }
    private static void sync(ServerPlayer player) {
        var state = player.getData(ModAttachments.EMERGENCY_EVA);
        PacketDistributor.sendToPlayer(player, new EmergencyEvaPayload(state.issue(), state.remainingTicks()));
    }
}
