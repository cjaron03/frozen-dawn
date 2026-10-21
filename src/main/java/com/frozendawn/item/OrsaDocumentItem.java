package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.PlayerEndStats;
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
import java.util.Map;

/**
 * Custom item that represents an ORSA lore document found in structure chests.
 * Right-click to archive: consumes the item, grants the discovery advancement,
 * unlocks the matching ORSA Field Manual entry and opens the manual on it.
 */
public class OrsaDocumentItem extends Item {

    public OrsaDocumentItem(Properties properties) {
        super(properties);
    }

    /** Discovery advancement and the manual entry each document is filed under. */
    private record Doc(String advancement, String entryPath, String entryName) {
        static Doc intel(String docType, String entryName) {
            return new Doc("found_" + docType, "orsa_intel/" + docType, entryName);
        }

        /** ORSA Uplink transcripts: invisible unlocks for the Relay Archive manual pages. */
        static Doc relay(int item) {
            return new Doc("relay_archive/" + item, "orsa_intel/relay_archive", "Relay Archive");
        }
    }

    private static final Map<String, Doc> DOCS = Map.ofEntries(
            Map.entry("villager_journal", Doc.intel("villager_journal", "A Villager's Journal")),
            Map.entry("orsa_bulletin", Doc.intel("orsa_bulletin", "Safety Bulletin")),
            Map.entry("vasik_log", Doc.intel("vasik_log", "Dr. Vasik's Log")),
            Map.entry("incident_report", Doc.intel("incident_report", "Incident TIR-1171")),
            Map.entry("satellite_log", Doc.intel("satellite_log", "Satellite System Log")),
            Map.entry("launch_manifest", Doc.intel("launch_manifest", "Launch Manifest")),
            Map.entry("transponder_schematic", new Doc("found_transponder_schematic", "advanced/transponder", "Transponder")),
            Map.entry("camp_field_report", Doc.intel("camp_field_report", "ORSA Field Report")),
            Map.entry("camp_personal_note", Doc.intel("camp_personal_note", "Crumpled Note")),
            Map.entry("camp_transfer_log", Doc.intel("camp_transfer_log", "Camp Transfer Log")),
            Map.entry("cargo_drop_manifest", Doc.intel("cargo_drop_manifest", "Cargo Manifest")),
            Map.entry("station_upload_log", Doc.intel("station_upload_log", "Weather Upload Log")),
            Map.entry("station_requisition", Doc.intel("station_requisition", "Parts Requisition")),
            Map.entry("station_maintenance_bulletin", Doc.intel("station_maintenance_bulletin", "Ingest Node Bulletin")),
            Map.entry("station_relay_diagnostics", Doc.intel("station_relay_diagnostics", "Relay Diagnostics")),
            Map.entry("vehicle_transcript_abandoned", Doc.intel("vehicle_transcript_abandoned", "Audio: Cabin Mic")),
            Map.entry("vehicle_transcript_breakdown", Doc.intel("vehicle_transcript_breakdown", "Audio: Breakdown")),
            Map.entry("vehicle_transcript_failed", Doc.intel("vehicle_transcript_failed", "Audio: Whiteout")),
            // News bulletins — civilian journalism, no classified_information
            Map.entry("newspaper_cannibal", Doc.intel("newspaper_cannibal", "Grandview Gazette")),
            Map.entry("newspaper_conspiracy", Doc.intel("newspaper_conspiracy", "The Northern Star")),
            Map.entry("newspaper_missing", Doc.intel("newspaper_missing", "Missing Persons")),
            Map.entry("newspaper_weather", Doc.intel("newspaper_weather", "Channel 9 Weather")),
            Map.entry("relay_archive_1", Doc.relay(1)),
            Map.entry("relay_archive_2", Doc.relay(2)),
            Map.entry("relay_archive_3", Doc.relay(3))
    );

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            String docType = getDocType(stack);

            Doc doc = DOCS.get(docType);

            if (doc != null) {
                grantAdvancement(serverPlayer, doc.advancement());

                if (docType.equals("transponder_schematic")) {
                    // Unlock transponder recipe in Acheron Forge (world-level)
                    WinConditionState winState = WinConditionState.get(serverPlayer.getServer());
                    if (!winState.isSchematicUnlocked()) {
                        winState.setSchematicUnlocked(true);
                        serverPlayer.displayClientMessage(
                                Component.literal("\u00A77[\u00A76Frozen Dawn\u00A77] \u00A7dTransponder schematics unlocked. Check the Acheron Forge."),
                                false);
                    }
                }

                // Grant general classified_information for ORSA-authored docs only
                if (!docType.equals("villager_journal") && !docType.startsWith("newspaper_")) {
                    grantAdvancement(serverPlayer, "classified_information");
                    PlayerEndStats.incrementOrsaDocumentsRead(serverPlayer);
                }

                if (docType.equals("satellite_log")) {
                    PlayerEndStats.markSatelliteFound(
                            serverPlayer,
                            ApocalypseState.get(serverPlayer.getServer()).getCurrentDay());
                }

                FieldManual.announceArchived(serverPlayer, doc.entryName());

                // Consume the document
                stack.shrink(1);

                FieldManual.openEntry(serverPlayer, doc.entryPath());
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
