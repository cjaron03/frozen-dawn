package com.frozendawn.item;

import com.frozendawn.maeve.MaeveDirector;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * §9.4b marked map: a locked vanilla map centered on her estimate of the shelter, painted once from loaded
 * chunks only (no chunk is loaded for it), marked with openings, heat and losses from the world model.
 * Locked, so neither the terrain nor the marks update after the drop.
 */
final class ScribeMap {
    static final byte SCALE = 1;
    private static final int COLOR = 0x8C9BA5;

    private ScribeMap() { }

    static ItemStack create(ServerLevel level, MaeveDirector.ScribeNotes notes) {
        BlockPos center = notes.center();
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", level.dimension().location().toString());
        tag.putInt("xCenter", center.getX()); tag.putInt("zCenter", center.getZ()); tag.putByte("scale", SCALE);
        tag.putBoolean("trackingPosition", false); tag.putBoolean("unlimitedTracking", false); tag.putBoolean("locked", true);
        tag.putByteArray("colors", paint(level, center));
        var id = level.getFreeMapId();
        level.setMapData(id, MapItemSavedData.load(tag, level.registryAccess()));
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        var marks = new LinkedHashMap<String, MapDecorations.Entry>();
        for (var mark : notes.marks())
            marks.put("frozendawn_scribe_" + marks.size(), new MapDecorations.Entry(type(mark.label()),
                    mark.position().getX() + .5, mark.position().getZ() + .5, mark.rotation()));
        stack.set(DataComponents.MAP_DECORATIONS, new MapDecorations(marks));
        stack.set(DataComponents.MAP_COLOR, new MapItemColor(COLOR));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.frozendawn.scribe_map"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(legend("openings"), legend("heat"), legend("losses"))));
        return stack;
    }

    static Holder<MapDecorationType> type(String label) {
        return switch (label) {
            case "ACCESS_POINT" -> MapDecorationTypes.BLUE_MARKER;
            case "HEAT_SOURCE" -> MapDecorationTypes.TARGET_POINT;
            default -> MapDecorationTypes.RED_X;
        };
    }

    private static Component legend(String key) {
        return Component.translatable("item.frozendawn.scribe_map.legend." + key).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    /** Vanilla's surface shading, one sample per pixel. Unloaded chunks stay blank. */
    static byte[] paint(ServerLevel level, BlockPos center) {
        byte[] colors = new byte[128 * 128];
        if (level.dimensionType().hasCeiling()) return colors;
        int step = 1 << SCALE;
        var pos = new BlockPos.MutableBlockPos();
        for (int px = 0; px < 128; px++) {
            double previous = 0;
            for (int pz = -1; pz < 128; pz++) {
                int x = center.getX() + (px - 64) * step, z = center.getZ() + (pz - 64) * step;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1, depth = 0;
                BlockState state;
                if (y <= level.getMinBuildHeight() + 1) state = Blocks.BEDROCK.defaultBlockState();
                else {
                    do { pos.set(x, --y, z); state = chunk.getBlockState(pos); }
                    while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight());
                    if (y > level.getMinBuildHeight() && !state.getFluidState().isEmpty()) {
                        var below = pos.mutable();
                        for (int fluid = y - 1; fluid > level.getMinBuildHeight(); fluid--) {
                            depth++; below.setY(fluid);
                            if (chunk.getBlockState(below).getFluidState().isEmpty()) break;
                        }
                        if (!state.isFaceSturdy(level, pos, Direction.UP)) state = state.getFluidState().createLegacyBlock();
                    }
                }
                MapColor color = state.getMapColor(level, pos);
                MapColor.Brightness brightness;
                int parity = (px + pz) & 1;
                if (color == MapColor.WATER) {
                    double shade = depth * .1 + parity * .2;
                    brightness = shade < .5 ? MapColor.Brightness.HIGH : shade > .9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double shade = (y - previous) * 4.0 / (step + 4) + (parity - .5) * .4;
                    brightness = shade > .6 ? MapColor.Brightness.HIGH : shade < -.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previous = y;
                if (pz >= 0) colors[px + pz * 128] = color.getPackedId(brightness);
            }
        }
        return colors;
    }
}
