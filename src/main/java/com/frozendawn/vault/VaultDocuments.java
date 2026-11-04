package com.frozendawn.vault;

import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The vault's paper: the one clue left in the hall above, the desk's two sheets, and the locker's tale logs. */
public final class VaultDocuments {
    public static final String DRAIN_SCHEDULE = "terminus_drain_schedule", MANIFEST = "terminus_manifest", NAME_REVIEW = "terminus_name_review";
    public static final List<String> TALE_LOGS = List.of("terminus_log_maintenance", "terminus_log_records", "terminus_log_medical",
            "terminus_log_security", "terminus_log_last");
    private VaultDocuments() { }

    public static ItemStack drainSchedule() { return document(DRAIN_SCHEDULE, "Drain Schedule"); }
    public static List<ItemStack> onTheDesk() { return List.of(document(MANIFEST, "Vault Manifest"), document(NAME_REVIEW, "Name Review")); }
    public static List<ItemStack> inTheLocker() {
        return TALE_LOGS.stream().map(type -> document(type, "Duty Log")).toList();
    }
    private static ItemStack document(String type, String name) {
        var paper = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        var tag = new CompoundTag();
        tag.putString("doc_type", type);
        paper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return paper;
    }
}
