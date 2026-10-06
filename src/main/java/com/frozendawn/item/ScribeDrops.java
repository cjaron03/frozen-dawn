package com.frozendawn.item;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** §9.4b drops: the record and the marked map, both frozen at the moment of death. */
public final class ScribeDrops {
    private ScribeDrops() { }

    public static void drop(ServerLevel level, ArchitectEntity scribe) {
        var notes = MaeveDirector.scribeNotes(scribe);
        if (notes == null) return;
        ItemStack record = new ItemStack(ModItems.SCRIBE_RECORD.get());
        record.set(ModDataComponents.SCRIBE_RECORD.get(), ScribeRecordContents.of(notes));
        scribe.spawnAtLocation(record);
        if (notes.dimension().equals(level.dimension().location().toString())) scribe.spawnAtLocation(ScribeMap.create(level, notes));
    }
}
