package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.List;

public final class ArchitectPersistence {

    private ArchitectPersistence() {
    }

    public static void putBlockPosList(CompoundTag tag, String key, List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put(key, list);
    }

    public static void readBlockPosList(CompoundTag tag, String key, List<BlockPos> positions) {
        positions.clear();
        ListTag list = tag.getList(key, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            positions.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
    }

    public static void putOptionalBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    @Nullable
    public static BlockPos getOptionalBlockPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
