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
import net.minecraft.nbt.ListTag;
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
 * Locked, so neither the terrain nor the marks update after the drop. It zooms to fit its marks: 4, 2 or 1 pixels
 * per block, or vanilla's half when even that would cut one off. Past vanilla's closest scale each block is painted
 * as a square and the marks are placed at their zoomed positions.
 */
final class ScribeMap {
    private static final int COLOR = 0x8C9BA5, FIT = 56;
    /** Vanilla's rotation for a standing banner mark; every Scribe mark stands upright. */
    private static final float UPRIGHT = 180;

    private ScribeMap() { }

    /** Pixels per block as a shift: 2, 1 or 0 zoom in past vanilla's closest scale; -1 is vanilla's scale 1. */
    static int zoom(BlockPos center, List<MaeveDirector.ScribeMark> marks) {
        int reach = 0;
        for (var mark : marks)
            reach = Math.max(reach, Math.max(Math.abs(mark.position().getX() - center.getX()), Math.abs(mark.position().getZ() - center.getZ())) + 1);
        for (int zoom = 2; zoom >= 0; zoom--) if (reach << zoom <= FIT) return zoom;
        return -1;
    }

    /** The block a pixel shows, along one axis. */
    private static int block(int center, int pixel, int zoom) {
        return center + (zoom >= 0 ? (pixel - 64) >> zoom : (pixel - 64) << 1);
    }

    /** A mark's coordinate as vanilla must read it to draw it at the zoomed pixel. */
    private static double markAt(int center, int block, int zoom) {
        return zoom >= 0 ? center + (block + .5 - center) * (1 << zoom) : block + .5;
    }

    static ItemStack create(ServerLevel level, MaeveDirector.ScribeNotes notes) {
        BlockPos center = notes.center();
        int zoom = zoom(center, notes.marks());
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", level.dimension().location().toString());
        tag.putInt("xCenter", center.getX()); tag.putInt("zCenter", center.getZ()); tag.putByte("scale", (byte) (zoom >= 0 ? 0 : 1));
        tag.putBoolean("trackingPosition", false); tag.putBoolean("unlimitedTracking", false); tag.putBoolean("locked", true);
        tag.putByteArray("colors", paint(level, center, zoom));
        tag.put("banners", new ListTag()); // vanilla load parses this unconditionally and warns when it is absent
        var id = level.getFreeMapId();
        level.setMapData(id, MapItemSavedData.load(tag, level.registryAccess()));
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        var marks = new LinkedHashMap<String, MapDecorations.Entry>();
        for (var mark : notes.marks())
            marks.put("frozendawn_scribe_" + marks.size(), new MapDecorations.Entry(type(mark.label()),
                    markAt(center.getX(), mark.position().getX(), zoom), markAt(center.getZ(), mark.position().getZ(), zoom),
                    UPRIGHT));
        stack.set(DataComponents.MAP_DECORATIONS, new MapDecorations(marks));
        stack.set(DataComponents.MAP_COLOR, new MapItemColor(COLOR));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.frozendawn.scribe_map"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(legend("openings"), legend("heat"), legend("losses"))));
        return stack;
    }

    static Holder<MapDecorationType> type(String label) {
        return switch (label) {
            // A banner, not the blue pointer, which reads as a player (owner, 2026-10-09).
            case "ACCESS_POINT" -> MapDecorationTypes.BLUE_BANNER;
            case "HEAT_SOURCE" -> MapDecorationTypes.TARGET_POINT;
            default -> MapDecorationTypes.RED_X;
        };
    }

    private static Component legend(String key) {
        return Component.translatable("item.frozendawn.scribe_map.legend." + key).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    /** Vanilla's surface shading, one sample per pixel; zoomed in, a block repeats across its square. Unloaded chunks stay blank. */
    static byte[] paint(ServerLevel level, BlockPos center, int zoom) {
        byte[] colors = new byte[128 * 128];
        if (level.dimensionType().hasCeiling()) return colors;
        int step = zoom >= 0 ? 1 : 2;
        var pos = new BlockPos.MutableBlockPos();
        for (int px = 0; px < 128; px++) {
            // Shade against the block to the north, so every pixel of a zoomed block shades alike.
            double previous = 0, last = 0;
            int lastZ = Integer.MIN_VALUE;
            for (int pz = -1; pz < 128; pz++) {
                int x = block(center.getX(), px, zoom), z = block(center.getZ(), pz, zoom);
                if (z != lastZ) { previous = last; lastZ = z; }
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
                int parity = zoom >= 0 ? (x + z) & 1 : (px + pz) & 1;
                if (color == MapColor.WATER) {
                    double shade = depth * .1 + parity * .2;
                    brightness = shade < .5 ? MapColor.Brightness.HIGH : shade > .9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double shade = (y - previous) * 4.0 / (step + 4) + (parity - .5) * .4;
                    brightness = shade > .6 ? MapColor.Brightness.HIGH : shade < -.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                last = y;
                if (pz >= 0) colors[px + pz * 128] = color.getPackedId(brightness);
            }
        }
        return colors;
    }
}
