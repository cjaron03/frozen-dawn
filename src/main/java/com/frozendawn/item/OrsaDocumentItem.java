package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.WinConditionState;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Custom item that represents an ORSA lore document found in structure chests.
 * Right-click to archive: consumes the item, grants the discovery advancement,
 * and unlocks the corresponding Patchouli entry in the ORSA Intel section.
 */
public class OrsaDocumentItem extends Item {

    public OrsaDocumentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            String docType = getDocType(stack);

            if (!docType.isEmpty()) {
                // Grant specific discovery advancement
                switch (docType) {
                    case "villager_journal" -> grantAdvancement(serverPlayer, "found_villager_journal");
                    case "orsa_bulletin" -> grantAdvancement(serverPlayer, "found_orsa_bulletin");
                    case "vasik_log" -> grantAdvancement(serverPlayer, "found_vasik_log");
                    case "incident_report" -> grantAdvancement(serverPlayer, "found_incident_report");
                    case "satellite_log" -> grantAdvancement(serverPlayer, "found_satellite_log");
                    case "transponder_schematic" -> {
                        grantAdvancement(serverPlayer, "found_transponder_schematic");
                        // Unlock transponder recipe in Acheron Forge (world-level)
                        WinConditionState winState = WinConditionState.get(serverPlayer.getServer());
                        if (!winState.isSchematicUnlocked()) {
                            winState.setSchematicUnlocked(true);
                            serverPlayer.displayClientMessage(
                                    Component.literal("\u00A77[\u00A76Frozen Dawn\u00A77] \u00A7dTransponder schematics unlocked. Check the Acheron Forge."),
                                    false);
                        }
                    }
                }

                // Grant general classified_information for ORSA-authored docs
                if (!docType.equals("villager_journal")) {
                    grantAdvancement(serverPlayer, "classified_information");
                }

                // Notify player
                serverPlayer.displayClientMessage(
                        Component.literal("\u00A77[\u00A76Frozen Dawn\u00A77] \u00A7fDocument archived in your \u00A76ORSA Field Survival Manual\u00A7f."),
                        false);

                // Consume the document
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to read & archive").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static String getDocType(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            return tag.getString("doc_type");
        }
        return "";
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }
}
